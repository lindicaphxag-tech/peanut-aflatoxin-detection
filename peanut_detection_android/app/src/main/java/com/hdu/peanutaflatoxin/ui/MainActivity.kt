package com.hdu.peanutaflatoxin.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private val REQUEST_PERMISSION = 100

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

        // 首次启动弹出隐私政策
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("privacy_agreed", false)) {
            showPrivacyDialog()
        }

        // 申请权限
        checkPermissions()

        findViewById<Button>(R.id.btn_detect).setOnClickListener {
            openGallery()
        }

        findViewById<Button>(R.id.btn_history).setOnClickListener {
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

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
        }
        val needRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needRequest.toTypedArray(), REQUEST_PERMISSION)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun startAnalysis(uri: Uri) {
        progress.visibility = ProgressBar.VISIBLE
        tvResult.text = ""
        tvConfidence.text = ""
        thread {
            try {
                // 1. 提取特征
                val features = FeatureExtractor.extractFromUri(this, uri)
                // 2. 本地推理
                val res = inference.classify(features)
                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    tvResult.text = if (res.isMoldy) "检测结果：存在黄曲霉污染风险" else "检测结果：花生状态正常"
                    tvResult.setTextColor(if (res.isMoldy) getColor(R.color.red) else getColor(R.color.green))
                    tvConfidence.text = "置信度：${"%.1f".format(res.confidence * 100)}%"
                }
                // 3. 保存历史
                dbHelper.insertRecord(uri.toString(), res.isMoldy, res.moldyProb)
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    Toast.makeText(this, "检测失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
