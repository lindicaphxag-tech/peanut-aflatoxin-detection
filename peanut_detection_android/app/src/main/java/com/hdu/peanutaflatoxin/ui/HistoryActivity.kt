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
