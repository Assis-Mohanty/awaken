package com.assismohanty.awaken.core.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.NutritionLabelParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real nutrition-label reader on a real phone: the compressed language data copied out of the installed app, the native
 * Tesseract code, and the parser, end to end. Unit tests cannot cover this (their assets are not compressed and the native code
 * does not run on the computer). Run it with `adb install` and `am instrument`, NOT `connectedDebugAndroidTest`, which uninstalls
 * the app afterwards and so deletes its data.
 */
@RunWith(AndroidJUnit4::class)
class LabelReaderDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun photo(name: String): Bitmap = testAssets.open("ocr/$name").use { requireNotNull(BitmapFactory.decodeStream(it)) }

    private fun read(name: String): Pair<List<com.assismohanty.awaken.engine.body.OcrWord>, Long> {
        val reader = TesseractLabelReader(app, Dispatchers.IO)
        val started = System.currentTimeMillis()
        val words = runBlocking { reader.read(photo(name)) }
        val took = System.currentTimeMillis() - started
        assertNotNull("the reader could not start (language data or native code)", words)
        Log.i("LabelReaderDevice", "$name took ${took} ms, ${words!!.size} words: " + words.joinToString(" ") { it.text })
        return words to took
    }

    @Test
    fun theLanguageDataIsCopiedOutOfTheCompressedAssetAndTheReaderRuns() {
        val (words, _) = read("label-b.png")
        assertTrue("no words were read", words.isNotEmpty())
    }

    @Test
    fun aTiltedLabelIsReadAndParsedIntoTheRightNumbers() {
        val (words, took) = read("label-b.png")
        val label = requireNotNull(NutritionLabelParser.parse(words)) { "no table found in: " + words.joinToString(" ") { it.text } }
        Log.i("LabelReaderDevice", "label-b parsed ${label.per100} unsure=${label.unsure}")
        assertEquals(480.0, label.per100.getValue(Nutrient.KCAL), 0.01)
        assertEquals(7.0, label.per100.getValue(Nutrient.PROTEIN), 0.01)
        assertEquals(64.0, label.per100.getValue(Nutrient.CARBS), 0.01)
        assertEquals(21.0, label.per100.getValue(Nutrient.FAT), 0.01)
        assertTrue("reading took $took ms", took < 30_000)
    }

    @Test
    fun theTwoColumnLabelKeepsTheColumnsApart() {
        val label = requireNotNull(NutritionLabelParser.parse(read("label-c.png").first))
        Log.i("LabelReaderDevice", "label-c parsed ${label.per100} unsure=${label.unsure}")
        assertEquals(520.0, label.per100.getValue(Nutrient.KCAL), 0.01)
        assertEquals(12.0, label.per100.getValue(Nutrient.PROTEIN), 0.01)
        assertEquals(60.0, label.per100.getValue(Nutrient.CARBS), 0.01)
        assertEquals(26.0, label.per100.getValue(Nutrient.FAT), 0.01)
    }

    @Test
    fun aBlurryPhotoOfACurvedJarIsReadWithTheRightPer100GramNumbers() {
        // A crop of a real screenshot of the camera preview of a curved peanut butter jar: tilted, partly out of focus, g read as 9.
        val (words, took) = read("real-jar-table.png")
        val label = requireNotNull(NutritionLabelParser.parse(words)) { "no table found in: " + words.joinToString(" ") { it.text } }
        Log.i("LabelReaderDevice", "real jar parsed ${label.per100} unsure=${label.unsure} serving=${label.servingGrams} in $took ms")
        assertEquals(628.0, label.per100.getValue(Nutrient.KCAL), 0.01)
        assertEquals(28.0, label.per100.getValue(Nutrient.PROTEIN), 0.01)
        assertEquals(17.5, label.per100.getValue(Nutrient.CARBS), 0.01)
        assertEquals(49.6, label.per100.getValue(Nutrient.FAT), 0.01)
        assertEquals(13.7, label.per100.getValue(Nutrient.SAT_FAT), 0.01)
        assertTrue("a per-32 g number was used as per 100 g", label.per100.values.none { it == 9.0 || it == 15.8 })
    }

    @Test
    fun aLabelWithMissingCellsNeverBorrowsTheServingColumn() {
        val label = requireNotNull(NutritionLabelParser.parse(read("label-a.png").first))
        Log.i("LabelReaderDevice", "label-a parsed ${label.per100} unsure=${label.unsure}")
        assertEquals(480.0, label.per100.getValue(Nutrient.KCAL), 0.01)
        assertTrue("a serving-column number was used as per 100 g", label.per100.values.none { it == 6.3 || it == 120.0 })
    }
}
