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
import com.hdu.peanutaflatoxin.R
import com.hdu.peanutaflatoxin.db.HistoryDbHelper
import com.hdu.peanutaflatoxin.ml.LgbmInference
import com.hdu.peanutaflatoxin.util.FeatureExtractor
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var inference: LgbmInference
    private lateinit var dbHelper: HistoryDbHelper
    private lateinit var tvResult: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var progress: ProgressBar
    private lateinit var detectButton: Button
    private lateinit var historyButton: Button
    private lateinit var imagePreview: ImageView

    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                startAnalysis(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化核心组件
        inference = LgbmInference(this)
        dbHelper = HistoryDbHelper(this)
        tvResult = findViewById(R.id.tv_result)
        tvConfidence = findViewById(R.id.tv_confidence)
        progress = findViewById(R.id.progress)
        detectButton = findViewById(R.id.btn_detect)
        historyButton = findViewById(R.id.btn_history)
        imagePreview = findViewById(R.id.image_preview)

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
            .setTitle("隐私政策")
            .setMessage("本应用所有检测数据均仅存储在您的手机本地，不会上传任何图像或个人信息到服务器。使用本应用即代表您同意上述隐私条款。")
            .setCancelable(false)
            .setPositiveButton("同意并继续") { dialog, _ ->
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putBoolean("privacy_agreed", true).apply()
                dialog.dismiss()
            }
            .show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickImageLauncher.launch(intent)
    }

    private fun startAnalysis(uri: Uri) {
        if (!detectButton.isEnabled) return
        detectButton.isEnabled = false
        historyButton.isEnabled = false
        progress.visibility = ProgressBar.VISIBLE
        tvResult.text = ""
        tvConfidence.text = ""
        imagePreview.setImageDrawable(null)
        thread {
            try {
                // 1. 提取特征
                val extracted = FeatureExtractor.extractWithPreview(this, uri)
                // 2. 本地推理
                val res = inference.classify(extracted.features)
                // 历史记录成功保存后才展示结果，避免显示无法查询的结果。
                if (dbHelper.insertRecord(uri.toString(), res.isMoldy, res.moldyProb) == -1L) {
                    throw IllegalStateException("无法保存历史记录")
                }
                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    detectButton.isEnabled = true
                    historyButton.isEnabled = true
                    tvResult.text = "模型演示结果（未经验证）"
                    imagePreview.setImageBitmap(extracted.preview)
                    tvResult.setTextColor(getColor(R.color.black))
                    tvConfidence.text = "模型输出分数：${"%.1f".format(res.moldyProb * 100)}%"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    detectButton.isEnabled = true
                    historyButton.isEnabled = true
                    Toast.makeText(this, "检测失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
