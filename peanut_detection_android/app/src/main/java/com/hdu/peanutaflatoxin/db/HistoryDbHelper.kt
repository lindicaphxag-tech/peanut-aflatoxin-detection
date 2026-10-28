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
    val confidence: Double
)

class HistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    companion object {
        const val DB_NAME = "detection_history.db"
        const val DB_VERSION = 1
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
                $COL_CONFIDENCE REAL NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Preserve user history until a version-specific migration is implemented.
        throw IllegalStateException("数据库版本 $oldVersion → $newVersion 缺少迁移，已拒绝升级以保留历史记录")
    }

    fun insertRecord(imageUri: String, isMoldy: Boolean, confidence: Double): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, System.currentTimeMillis().toString())
            put(COL_IMAGE_URI, imageUri)
            put(COL_IS_MOLDY, if (isMoldy) 1 else 0)
            put(COL_CONFIDENCE, confidence)
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
                    confidence = rows.getDouble(rows.getColumnIndexOrThrow(COL_CONFIDENCE))
                ))
            }
        }
        return list
    }
}
