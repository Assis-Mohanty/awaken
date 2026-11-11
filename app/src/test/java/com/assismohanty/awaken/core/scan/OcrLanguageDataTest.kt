package com.assismohanty.awaken.core.scan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The language data is copied out of the app so Tesseract can read it, and recopied when it is missing, empty or from an older model. */
@RunWith(AndroidJUnit4::class)
class OcrLanguageDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val data = OcrLanguageData(context)
    private val model get() = File(File(File(context.filesDir, "ocr"), "tessdata"), OcrLanguageData.FILE)
    private val stamp get() = File(model.parentFile, "${OcrLanguageData.FILE}.id")

    @Before
    fun clean() {
        File(context.filesDir, "ocr").deleteRecursively()
    }

    private fun shippedSize(): Long = context.assets.open(OcrLanguageData.ASSET).use { stream -> stream.readBytes().size.toLong() }

    @Test
    fun theFirstUseCopiesTheWholeModelAndGivesTesseractTheFolderThatHoldsTessdata() {
        val root = data.ensure()

        assertThat(root).isEqualTo(File(context.filesDir, "ocr"))
        assertThat(File(root, "tessdata/eng.traineddata").length()).isEqualTo(shippedSize())
        assertThat(model.length()).isGreaterThan(OcrLanguageData.MIN_BYTES)
        assertThat(stamp.readText()).isEqualTo(OcrLanguageData.MODEL_ID)
    }

    @Test
    fun aCurrentCopyIsLeftAloneOnTheNextUse() {
        data.ensure()
        model.setLastModified(1_000_000_000_000L)

        data.ensure()

        assertThat(model.lastModified()).isEqualTo(1_000_000_000_000L)
    }

    @Test
    fun anEmptyOrCutShortCopyIsReplaced() {
        data.ensure()
        model.writeBytes(ByteArray(10))

        data.ensure()

        assertThat(model.length()).isEqualTo(shippedSize())
    }

    @Test
    fun aCopyFromAnOlderModelIsReplaced() {
        data.ensure()
        stamp.writeText("an-older-model")
        model.writeBytes(ByteArray((OcrLanguageData.MIN_BYTES + 5).toInt()))

        data.ensure()

        assertThat(model.length()).isEqualTo(shippedSize())
        assertThat(stamp.readText()).isEqualTo(OcrLanguageData.MODEL_ID)
    }

    @Test
    fun noHalfWrittenFileIsLeftBehind() {
        data.ensure()
        assertThat(File(model.parentFile, "${OcrLanguageData.FILE}.part").exists()).isFalse()
    }
}
