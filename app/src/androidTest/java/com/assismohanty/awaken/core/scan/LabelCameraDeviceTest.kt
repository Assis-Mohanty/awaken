package com.assismohanty.awaken.core.scan

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assismohanty.awaken.core.designsystem.components.LabelCamera
import com.assismohanty.awaken.core.designsystem.components.LabelShutter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The label camera on a real phone: it starts, takes a photo of whatever it points at, hands back an upright bitmap, and the reader
 * can run on that photo. Needs the app's camera permission to be granted already (this test does not change permissions).
 */
@RunWith(AndroidJUnit4::class)
class LabelCameraDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theCameraStartsTakesAPhotoAndTheReaderCanUseIt() {
        val shutter = LabelShutter()
        rule.setContent { LabelCamera(shutter, Modifier.fillMaxSize()) }

        var photo: Bitmap? = null
        val deadline = System.currentTimeMillis() + 20_000
        var attempts = 0
        // The camera starts asynchronously, so a tap in the first moments finds no photo; the app tells the hunter to try again.
        while (photo == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(1_000)
            attempts++
            val latch = CountDownLatch(1)
            rule.runOnUiThread { shutter.capture { photo = it; latch.countDown() } }
            latch.await(8, TimeUnit.SECONDS)
        }
        Log.i("LabelCameraDevice", "photo after $attempts attempt(s): ${photo?.width}x${photo?.height}")
        assertNotNull("the camera never produced a photo", photo)
        assertTrue("the photo is too small to read text from", maxOf(photo!!.width, photo!!.height) >= 1000)

        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val started = System.currentTimeMillis()
        val words = runBlocking { TesseractLabelReader(app, Dispatchers.IO).read(photo!!) }
        Log.i("LabelCameraDevice", "read took ${System.currentTimeMillis() - started} ms, ${words?.size} words")
        assertNotNull("the reader failed on a real camera photo", words)
    }
}
