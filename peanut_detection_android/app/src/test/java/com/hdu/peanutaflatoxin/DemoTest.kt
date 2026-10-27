package com.hdu.peanutaflatoxin

import android.widget.LinearLayout
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.hdu.peanutaflatoxin.db.HistoryDbHelper
import com.hdu.peanutaflatoxin.ml.LgbmInference
import com.hdu.peanutaflatoxin.ui.HistoryActivity
import com.hdu.peanutaflatoxin.ui.MainActivity
import com.hdu.peanutaflatoxin.util.FeatureExtractor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DemoTest {
    @Test
    fun mainScreenStarts() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals("运行模型演示", activity.findViewById<android.widget.Button>(R.id.btn_detect).text.toString())
    }

    @Test
    fun modelMatchesReferenceForScalerMeans() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val params = JSONObject(context.assets.open("scaler_params.json").bufferedReader().use { it.readText() })
        val mean = params.getJSONArray("mean_")
        val features = DoubleArray(mean.length()) { mean.getDouble(it) }
        assertEquals(0.30072822456105475, LgbmInference(context).predictProba(features), 1e-12)
    }

    @Test
    fun selectedImageProducesFiniteFeatureVector() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://demo/test-image")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes.toByteArray()))
        val features = FeatureExtractor.extractFromUri(context, uri)
        assertEquals(16, features.size)
        assertTrue(features.all { it.isFinite() })
    }

    @Test
    fun savedHistoryAppearsInHistoryScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(HistoryDbHelper.DB_NAME)
        val db = HistoryDbHelper(context)
        try {
            db.insertRecord("content://demo/image", false, 0.3)
        } finally {
            db.close()
        }
        val activity = Robolectric.buildActivity(HistoryActivity::class.java).setup().get()
        assertEquals(1, activity.findViewById<LinearLayout>(R.id.rv_history).childCount)
    }
}
