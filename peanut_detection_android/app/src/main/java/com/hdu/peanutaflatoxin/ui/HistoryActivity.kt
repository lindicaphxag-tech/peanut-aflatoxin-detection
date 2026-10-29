package com.hdu.peanutaflatoxin.ui

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hdu.peanutaflatoxin.R
import com.hdu.peanutaflatoxin.db.HistoryDbHelper
import com.hdu.peanutaflatoxin.db.HistoryExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    private lateinit var dbHelper: HistoryDbHelper
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val appContext = applicationContext
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val json = HistoryDbHelper(appContext).use { HistoryExport.serialize(it.getAllRecords()) }
                        val stream = appContext.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IllegalStateException("无法打开目标文件")
                        stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                    }
                    Toast.makeText(appContext, R.string.export_complete, Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Toast.makeText(appContext, getString(R.string.export_failed, e.message ?: "未知错误"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        dbHelper = HistoryDbHelper(this)

        findViewById<Button>(R.id.btn_back).setOnClickListener {
            finish()
        }
        findViewById<Button>(R.id.btn_export).setOnClickListener {
            try {
                exportLauncher.launch("peanut-demo-history.json")
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.export_failed, e.message ?: "无法打开文件选择器"), Toast.LENGTH_SHORT).show()
            }
        }

        val container = findViewById<LinearLayout>(R.id.rv_history)
        val records = dbHelper.getAllRecords()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        if (records.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.empty_history)
                textSize = 16f
            })
        }

        records.forEach { record ->
            val itemView = layoutInflater.inflate(R.layout.item_history, container, false)
            itemView.findViewById<TextView>(R.id.tv_time).text = sdf.format(Date(record.timestamp.toLong()))
            itemView.findViewById<TextView>(R.id.tv_result).text = getString(R.string.demo_result)
            itemView.findViewById<TextView>(R.id.tv_confidence).text = getString(R.string.model_score, record.confidence * 100)
            container.addView(itemView)
        }
    }

    override fun onDestroy() {
        if (::dbHelper.isInitialized) dbHelper.close()
        super.onDestroy()
    }
}
