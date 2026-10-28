package com.hdu.peanutaflatoxin.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.hdu.peanutaflatoxin.R
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val analysisViewModel: AnalysisViewModel by viewModels()
    private lateinit var tvResult: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var progress: ProgressBar
    private lateinit var detectButton: Button
    private lateinit var historyButton: Button
    private lateinit var imagePreview: ImageView

    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        analysisViewModel.finishImagePicking()
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                startAnalysis(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvResult = findViewById(R.id.tv_result)
        tvConfidence = findViewById(R.id.tv_confidence)
        progress = findViewById(R.id.progress)
        detectButton = findViewById(R.id.btn_detect)
        historyButton = findViewById(R.id.btn_history)
        imagePreview = findViewById(R.id.image_preview)

        lifecycleScope.launch {
            analysisViewModel.uiState.collect(::renderAnalysisState)
        }

        // 首次启动弹出隐私政策
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("privacy_agreed", false)) {
            showPrivacyDialog()
        }

        detectButton.setOnClickListener {
            openGallery()
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_title)
            .setMessage(R.string.privacy_message)
            .setCancelable(false)
            .setPositiveButton(R.string.privacy_accept) { dialog, _ ->
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putBoolean("privacy_agreed", true).apply()
                dialog.dismiss()
            }
            .show()
    }

    private fun openGallery() {
        if (!analysisViewModel.beginImagePicking()) return
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            pickImageLauncher.launch(intent)
        } catch (e: Exception) {
            analysisViewModel.finishImagePicking()
            Toast.makeText(this, getString(R.string.analysis_failed, e.message ?: "无法打开图片选择器"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAnalysis(uri: Uri) {
        analysisViewModel.analyze(uri)
    }

    private fun renderAnalysisState(state: AnalysisUiState) {
        val buttonsEnabled = !state.isAnalyzing && !state.isPickingImage
        detectButton.isEnabled = buttonsEnabled
        historyButton.isEnabled = buttonsEnabled
        progress.visibility = if (state.isAnalyzing) ProgressBar.VISIBLE else ProgressBar.GONE

        state.preview?.let {
            imagePreview.setImageBitmap(it)
            tvResult.text = getString(R.string.demo_result)
            tvResult.setTextColor(getColor(R.color.black))
            tvConfidence.text = getString(R.string.model_score, (state.score ?: 0.0) * 100)
        } ?: run {
            if (state.isAnalyzing) {
                tvResult.text = ""
                tvConfidence.text = ""
                imagePreview.setImageDrawable(null)
            }
        }

        state.error?.let {
            Toast.makeText(this, getString(R.string.analysis_failed, it), Toast.LENGTH_SHORT).show()
            analysisViewModel.clearError()
        }
    }
}
