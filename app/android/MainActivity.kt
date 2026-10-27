package com.hdu.peanutaflatoxin.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hdu.peanutaflatoxin.R
import com.hdu.peanutaflatoxin.ml.LgbmInference
import com.hdu.peanutaflatoxin.util.FeatureExtractor
import com.hdu.peanutaflatoxin.util.HistoryDbHelper

class MainActivity : AppCompatActivity() {
    private lateinit var inference: LgbmInference
    private lateinit var tvResult: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var dbHelper: HistoryDbHelper

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                analyzeImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inference = LgbmInference(this)
        dbHelper = HistoryDbHelper(this)
        tvResult = findViewById(R.id.tv_result)
        tvConfidence = findViewById(R.id.tv_confidence)

        findViewById<Button>(R.id.btn_detect).setOnClickListener {
            // 打开相册选择图片，后续接入CameraX实现自动三波段拍摄
            val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(pickIntent)
        }

        findViewById<Button>(R.id.btn_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun analyzeImage(uri: Uri) {
        Thread {
            try {
                // 1. 提取16维特征
                val features = FeatureExtractor.extractFromUri(this, uri)
                // 2. 本地推理
                val res = inference.classify(features)
                runOnUiThread {
                    tvResult.text = if (res.isMoldy) "检测结果：存在黄曲霉污染风险" else "检测结果：花生状态正常"
                    tvResult.setTextColor(if (res.isMoldy) getColor(R.color.red) else getColor(R.color.green))
                    tvConfidence.text = "置信度：${"%.1f".format(res.confidence * 100)}%"
                }
                // 3. 保存到SQLite历史记录
                dbHelper.insertRecord(uri.toString(), res.isMoldy, res.moldyProb)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "检测失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
