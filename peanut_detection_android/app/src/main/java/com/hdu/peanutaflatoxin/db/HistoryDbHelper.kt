package com.hdu.peanutaflatoxin.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistoryRecord(
    val id: Long,
    val timestamp: String,
    val imageUri: String,
    val isMoldy: Boolean,
    val confidence: Double,
    val modelSha256: String? = null,
    val scalerSha256: String? = null,
    val pipelineVersion: String? = null,
    val inputSha256: String? = null
)

class HistoryDbHelper(context: Context, databaseName: String = DB_NAME) : SQLiteOpenHelper(context, databaseName, null, DB_VERSION) {
    companion object {
        const val DB_NAME = "detection_history.db"
        const val DB_VERSION = 2
        const val TABLE_NAME = "history"
        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_IMAGE_URI = "image_uri"
        const val COL_IS_MOLDY = "is_moldy"
        const val COL_CONFIDENCE = "confidence"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("""
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_IMAGE_URI TEXT NOT NULL,
                $COL_IS_MOLDY INTEGER NOT NULL,
                $COL_CONFIDENCE REAL NOT NULL,
                model_sha256 TEXT,
                scaler_sha256 TEXT,
                pipeline_version TEXT,
                input_sha256 TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2 && db != null) {
            listOf("model_sha256", "scaler_sha256", "pipeline_version", "input_sha256").forEach {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $it TEXT")
            }
            return
        }
        throw IllegalStateException("数据库版本 $oldVersion → $newVersion 缺少迁移，已拒绝升级以保留历史记录")
    }

    fun insertRecord(imageUri: String, isMoldy: Boolean, confidence: Double, modelSha256: String? = null, scalerSha256: String? = null, pipelineVersion: String? = null, inputSha256: String? = null): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, System.currentTimeMillis().toString())
            put(COL_IMAGE_URI, imageUri)
            put(COL_IS_MOLDY, if (isMoldy) 1 else 0)
            put(COL_CONFIDENCE, confidence)
            put("model_sha256", modelSha256)
            put("scaler_sha256", scalerSha256)
            put("pipeline_version", pipelineVersion)
            put("input_sha256", inputSha256)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun getAllRecords(): List<HistoryRecord> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC"
        )
        val list = mutableListOf<HistoryRecord>()
        cursor.use { rows ->
            while (rows.moveToNext()) {
                list.add(HistoryRecord(
                    id = rows.getLong(rows.getColumnIndexOrThrow(COL_ID)),
                    timestamp = rows.getString(rows.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    imageUri = rows.getString(rows.getColumnIndexOrThrow(COL_IMAGE_URI)),
                    isMoldy = rows.getInt(rows.getColumnIndexOrThrow(COL_IS_MOLDY)) == 1,
                    confidence = rows.getDouble(rows.getColumnIndexOrThrow(COL_CONFIDENCE)),
                    modelSha256 = rows.getString(rows.getColumnIndexOrThrow("model_sha256")),
                    scalerSha256 = rows.getString(rows.getColumnIndexOrThrow("scaler_sha256")),
                    pipelineVersion = rows.getString(rows.getColumnIndexOrThrow("pipeline_version")),
                    inputSha256 = rows.getString(rows.getColumnIndexOrThrow("input_sha256"))
                ))
            }
        }
        return list
    }
}
