package com.hdu.peanutaflatoxin.ui

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hdu.peanutaflatoxin.R
import com.hdu.peanutaflatoxin.db.HistoryDbHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    private lateinit var dbHelper: HistoryDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        dbHelper = HistoryDbHelper(this)

        findViewById<Button>(R.id.btn_back).setOnClickListener {
            finish()
        }

        val container = findViewById<LinearLayout>(R.id.rv_history)
        val records = dbHelper.getAllRecords()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        records.forEach { record ->
            val itemView = layoutInflater.inflate(R.layout.item_history, null)
            itemView.findViewById<TextView>(R.id.tv_time).text = sdf.format(Date(record.timestamp.toLong()))
            itemView.findViewById<TextView>(R.id.tv_result).text = "模型演示结果（未经验证）"
            itemView.findViewById<TextView>(R.id.tv_confidence).text = "模型输出分数：${"%.1f".format(record.confidence * 100)}%"
            container.addView(itemView)
        }
    }
}
