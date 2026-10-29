package com.hdu.peanutaflatoxin

import android.widget.LinearLayout
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.ImageView
import android.widget.ScrollView
import com.hdu.peanutaflatoxin.db.HistoryDbHelper
import com.hdu.peanutaflatoxin.ml.LgbmInference
import com.hdu.peanutaflatoxin.ui.HistoryActivity
import com.hdu.peanutaflatoxin.ui.MainActivity
import com.hdu.peanutaflatoxin.util.FeatureExtractor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 34])
class DemoTest {
    private val activityControllers = mutableListOf<ActivityController<out Activity>>()

    private fun <T : Activity> launchActivity(activityClass: Class<T>): T {
        val controller = Robolectric.buildActivity(activityClass).setup()
        activityControllers.add(controller)
        return controller.get()
    }

    @After
    fun destroyActivities() {
        activityControllers.asReversed().forEach { controller ->
            runCatching { controller.pause().stop().destroy() }
        }
        activityControllers.clear()
    }

    @Test
    fun mainScreenStarts() {
        val activity = launchActivity(MainActivity::class.java)
        assertEquals("运行模型演示", activity.findViewById<android.widget.Button>(R.id.btn_detect).text.toString())
        assertEquals("坚果图像分析演示", activity.getString(R.string.app_name))
    }

    @Test
    fun mainScreenCanScrollAndOpensSystemImagePicker() {
        val activity = launchActivity(MainActivity::class.java)
        val scrollId = activity.resources.getIdentifier("main_scroll", "id", activity.packageName)
        assertTrue(scrollId != 0 && activity.findViewById<ScrollView>(scrollId) is ScrollView)
        activity.findViewById<android.widget.Button>(R.id.btn_detect).performClick()
        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_GET_CONTENT, intent.action)
        assertEquals("image/*", intent.type)
    }

    @Test
    fun rapidPickerTapsLaunchOnlyOnePicker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("privacy_agreed", true).commit()
        val activity = launchActivity(MainActivity::class.java)
        val button = activity.findViewById<Button>(R.id.btn_detect)
        button.performClick()
        button.performClick()
        val shadow = Shadows.shadowOf(activity)
        assertEquals(Intent.ACTION_GET_CONTENT, shadow.nextStartedActivity.action)
        assertEquals(null, shadow.nextStartedActivity)
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
    fun modelRejectsNonFiniteFeaturesAndInvalidThreshold() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = LgbmInference(context)
        assertThrows(IllegalArgumentException::class.java) { model.predictProba(DoubleArray(16) { Double.NaN }) }
        assertThrows(IllegalArgumentException::class.java) { model.classify(DoubleArray(16), Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { model.classify(DoubleArray(16), 1.1) }
    }

    @Test
    fun largeImagesAreDecodedAtABoundedSize() {
        val method = FeatureExtractor::class.java.declaredMethods.firstOrNull { it.name == "sampleSizeFor" }
        assertTrue(method != null)
        val sample = method!!.invoke(FeatureExtractor, 16000, 12000) as Int
        assertTrue(sample >= 16 && 16000 / sample <= 1024 && 12000 / sample <= 1024)
    }

    @Test
    fun selectedImageProducesFiniteFeatureVector() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://demo/test-image")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes.toByteArray()) }
        val features = FeatureExtractor.extractFromUri(context, uri)
        assertEquals(16, features.size)
        assertTrue(features.all { it.isFinite() })
    }

    @Test
    fun selectedImageRunsAndIsSavedBeforeResultIsShown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(HistoryDbHelper.DB_NAME)
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("privacy_agreed", true).commit()
        val uri = Uri.parse("content://demo/full-flow")
        val bytes = ByteArrayOutputStream()
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .compress(Bitmap.CompressFormat.PNG, 100, bytes)
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes.toByteArray()) }
        val activity = launchActivity(MainActivity::class.java)
        val button = activity.findViewById<Button>(R.id.btn_detect)
        button.performClick()
        val shadow = Shadows.shadowOf(activity)
        val launched = shadow.nextStartedActivityForResult.intent
        shadow.receiveResult(launched, Activity.RESULT_OK, Intent().setData(uri))
        assertTrue(!button.isEnabled)
        val result = activity.findViewById<TextView>(R.id.tv_result)
        val deadline = System.nanoTime() + 10_000_000_000L
        while (result.text.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue(result.text.toString().contains("未经验证"))
        assertTrue(button.isEnabled)
        val previewId = activity.resources.getIdentifier("image_preview", "id", activity.packageName)
        assertTrue(previewId != 0 && activity.findViewById<ImageView>(previewId).drawable != null)
        val historyDb = HistoryDbHelper(activity.applicationContext)
        try {
            val records = historyDb.getAllRecords()
            assertEquals(1, records.size)
            val record = records.single()
            val sha256 = Regex("[0-9a-f]{64}")
            assertTrue(record.modelSha256?.matches(sha256) == true)
            assertTrue(record.scalerSha256?.matches(sha256) == true)
            assertTrue(record.inputSha256?.matches(sha256) == true)
            assertEquals(FeatureExtractor.PIPELINE_VERSION, record.pipelineVersion)
            assertEquals(FeatureExtractor.extractWithPreview(context, uri).inputSha256, record.inputSha256)
        } finally {
            historyDb.close()
        }
    }

    @Test
    fun analysisStateSurvivesActivityRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(HistoryDbHelper.DB_NAME)
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("privacy_agreed", true).commit()
        val uri = Uri.parse("content://demo/rotation")
        val bytes = ByteArrayOutputStream()
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .compress(Bitmap.CompressFormat.PNG, 100, bytes)
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes.toByteArray()) }

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activityControllers.add(controller)
        val activity = controller.get()
        activity.findViewById<Button>(R.id.btn_detect).performClick()
        val pickerIntent = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
        Shadows.shadowOf(activity).receiveResult(pickerIntent, Activity.RESULT_OK, Intent().setData(uri))

        val recreatedActivity = controller.recreate().get()
        val result = recreatedActivity.findViewById<TextView>(R.id.tv_result)
        val deadline = System.nanoTime() + 10_000_000_000L
        while (result.text.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.text.toString().contains("未经验证"))
        assertTrue(recreatedActivity.findViewById<Button>(R.id.btn_detect).isEnabled)
    }

    @Test
    fun unreadableImageRestoresControlsWithoutSavingResult() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(HistoryDbHelper.DB_NAME)
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("privacy_agreed", true).commit()
        val uri = Uri.parse("content://demo/bad-image")
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            throw FileNotFoundException("unavailable")
        }
        val activity = launchActivity(MainActivity::class.java)
        val button = activity.findViewById<Button>(R.id.btn_detect)
        button.performClick()
        val shadow = Shadows.shadowOf(activity)
        val launched = shadow.nextStartedActivityForResult.intent
        shadow.receiveResult(launched, Activity.RESULT_OK, Intent().setData(uri))
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!button.isEnabled && System.nanoTime() < deadline) {
            Thread.sleep(20)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue(button.isEnabled)
        assertEquals("", activity.findViewById<TextView>(R.id.tv_result).text.toString())
        val historyDb = HistoryDbHelper(context)
        try {
            assertTrue(historyDb.getAllRecords().isEmpty())
        } finally {
            historyDb.close()
        }
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
        val activity = launchActivity(HistoryActivity::class.java)
        assertEquals(1, activity.findViewById<LinearLayout>(R.id.rv_history).childCount)
    }

    @Test
    fun exportUsesSystemDocumentPicker() {
        val activity = launchActivity(HistoryActivity::class.java)
        activity.findViewById<Button>(R.id.btn_export).performClick()
        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/json", intent.type)
    }

    @Test
    fun emptyHistoryExplainsThereAreNoRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(HistoryDbHelper.DB_NAME)
        val activity = launchActivity(HistoryActivity::class.java)
        val container = activity.findViewById<LinearLayout>(R.id.rv_history)
        assertEquals(1, container.childCount)
        assertEquals("暂无历史记录", (container.getChildAt(0) as TextView).text.toString())
    }

    @Test
    fun unknownDatabaseUpgradeDoesNotDeleteHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(HistoryDbHelper.DB_NAME)
        val helper = HistoryDbHelper(context)
        try {
            helper.insertRecord("content://demo/preserved", false, 0.3)
            assertThrows(IllegalStateException::class.java) { helper.onUpgrade(helper.writableDatabase, 2, 3) }
            assertEquals(1, helper.getAllRecords().size)
        } finally {
            helper.close()
        }
    }
}
