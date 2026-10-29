package com.hdu.peanutaflatoxin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hdu.peanutaflatoxin.db.HistoryDbHelper
import com.hdu.peanutaflatoxin.db.HistoryRecord
import com.hdu.peanutaflatoxin.db.HistoryExport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryMigrationTest {
    @Test fun exportIncludesProvenanceAndOmitsPrivateUri() {
        val rows = listOf(HistoryRecord(1, "123", "content://private/image", false, 0.3,
            "modelhash", "scalerhash", "pipeline-v1", "inputhash"),
            HistoryRecord(2, "124", "content://old", true, 0.4))
        val json = JSONObject(HistoryExport.serialize(rows))
        assertEquals("peanut-demo-history-v1", json.getString("schema"))
        val records = json.getJSONArray("records")
        assertEquals(2, records.length())
        assertEquals("modelhash", records.getJSONObject(0).getString("model_sha256"))
        assertEquals("inputhash", records.getJSONObject(0).getString("input_sha256"))
        assertTrue(records.getJSONObject(1).isNull("model_sha256"))
        assertTrue(!json.toString().contains("content://"))
    }
    @Test fun versionOneRecordsSurviveMigrationWithUnknownProvenance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-regression.db"
        context.deleteDatabase(name)
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL, image_uri TEXT NOT NULL, is_moldy INTEGER NOT NULL, confidence REAL NOT NULL)")
        db.execSQL("INSERT INTO history VALUES (1, '123', 'content://old', 0, 0.3)")
        db.version = 1
        db.close()
        val helper = HistoryDbHelper(context, name)
        try {
            val record = helper.getAllRecords().single()
            assertEquals(2, helper.readableDatabase.version)
            assertEquals(0.3, record.confidence, 0.0)
            assertEquals(null, record.modelSha256)
            assertEquals(null, record.scalerSha256)
            assertEquals(null, record.pipelineVersion)
            assertEquals(null, record.inputSha256)
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
