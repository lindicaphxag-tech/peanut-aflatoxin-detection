package com.hdu.peanutaflatoxin.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdu.peanutaflatoxin.db.HistoryDbHelper
import com.hdu.peanutaflatoxin.ml.LgbmInference
import com.hdu.peanutaflatoxin.util.FeatureExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class AnalysisUiState(
    val isAnalyzing: Boolean = false,
    val isPickingImage: Boolean = false,
    val score: Double? = null,
    val preview: Bitmap? = null,
    val error: String? = null
)

/** Retains an in-flight analysis across Activity recreation without retaining the Activity. */
class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val inference by lazy { LgbmInference(getApplication()) }
    private fun assetSha256(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        getApplication<Application>().assets.open(name).use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private val modelSha256 by lazy { assetSha256("lgbm_model.json") }
    private val scalerSha256 by lazy { assetSha256("scaler_params.json") }
    private val dbHelperDelegate = lazy { HistoryDbHelper(getApplication()) }
    private val dbHelper by dbHelperDelegate

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState = _uiState.asStateFlow()
    private var analysisJob: Job? = null

    fun beginImagePicking(): Boolean {
        if (_uiState.value.isAnalyzing || _uiState.value.isPickingImage) return false
        _uiState.value = _uiState.value.copy(isPickingImage = true)
        return true
    }

    fun finishImagePicking() {
        if (_uiState.value.isPickingImage) _uiState.value = _uiState.value.copy(isPickingImage = false)
    }

    fun analyze(uri: Uri) {
        if (_uiState.value.isAnalyzing || _uiState.value.isPickingImage) return
        _uiState.value = AnalysisUiState(isAnalyzing = true)
        analysisJob = viewModelScope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    val extracted = FeatureExtractor.extractWithPreview(getApplication(), uri)
                    currentCoroutineContext().ensureActive()
                    val result = inference.classify(extracted.features)
                    currentCoroutineContext().ensureActive()
                    if (dbHelper.insertRecord(uri.toString(), result.isMoldy, result.moldyProb,
                            modelSha256, scalerSha256, FeatureExtractor.PIPELINE_VERSION, extracted.inputSha256) == -1L) {
                        throw IllegalStateException("无法保存历史记录")
                    }
                    extracted.preview to result.moldyProb
                }
                _uiState.value = AnalysisUiState(score = output.second, preview = output.first)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.value = AnalysisUiState(error = e.message ?: "未知错误")
            }
        }
    }

    fun clearError() {
        if (_uiState.value.error != null) _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        if (dbHelperDelegate.isInitialized()) {
            val closeDatabase = { dbHelper.close() }
            val runningAnalysis = analysisJob
            if (runningAnalysis == null || runningAnalysis.isCompleted) {
                closeDatabase()
            } else {
                runningAnalysis.invokeOnCompletion { closeDatabase() }
            }
        }
        super.onCleared()
    }
}
