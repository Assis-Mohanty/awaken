package com.assismohanty.awaken.core.scan

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.LabelPlausibility
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.NutritionLabelParser
import com.assismohanty.awaken.engine.body.OcrWord
import com.googlecode.leptonica.android.AdaptiveMap
import com.googlecode.leptonica.android.Convert
import com.googlecode.leptonica.android.Enhance
import com.googlecode.leptonica.android.Pix
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Reads the words (and where they sit) from a photo. The photo stays on the phone. */
fun interface LabelReader {
    /** The recognised words, or null when the reader could not run at all. An empty list means it ran and found no text. */
    suspend fun read(photo: Bitmap): List<OcrWord>?
}

/**
 * Tesseract on the phone (docs/diet/PLAN.md section 5; ENGINE_DECISIONS #205): the English "fast" language data ships in the app
 * (assets/tessdata, 4 MB) and is copied to private storage the first time. No network, no Google services. One photo at a time,
 * and each read builds and releases its own native engine so memory is freed straight after.
 *
 * A photo of a real pack is not a clean scan: curved, tilted, uneven light, part of it out of focus. So the photo is read up to four
 * ways (the default page mode and "sparse text", each on the photo as taken and on a cleaned copy with the lighting flattened and the
 * edges sharpened), the words of each are judged by `:engine` [NutritionLabelParser], and the best reading wins. Reading stops early
 * as soon as one gives a complete table whose numbers agree with each other.
 *
 * In a debuggable build only, the photo and each reading are kept in the app's private cache (`label-debug`) so a failed read can
 * be pulled off the phone and studied. A release build never keeps anything.
 */
@Singleton
class TesseractLabelReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LabelReader {
    private val lock = Mutex()
    private val languageData = OcrLanguageData(context)
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private class Pass(val name: String, val image: Pix, val mode: Int)

    private class Reading(val name: String, val words: List<OcrWord>, val score: Int, val goodEnough: Boolean)

    override suspend fun read(photo: Bitmap): List<OcrWord>? = lock.withLock {
        withContext(ioDispatcher) {
            val dataDir = languageData.ensure() ?: return@withContext null
            val engine = TessBaseAPI()
            val prepared = scaledForReading(photo)
            val pixes = mutableListOf<Pix>()
            try {
                if (!engine.init(dataDir.absolutePath, LANGUAGE)) return@withContext null
                // Keeps the gaps between columns, which is how a table's columns stay apart.
                engine.setVariable("preserve_interword_spaces", "1")
                if (debuggable) keepPhoto(photo)
                val passes = passesFor(prepared, pixes)
                var best: Reading? = null
                for (pass in passes) {
                    val started = System.currentTimeMillis()
                    val words = recognise(engine, pass) ?: continue
                    val reading = judge(pass.name, words)
                    Timber.i("Label pass %s: %d words, score %d, %d ms", pass.name, words.size, reading.score, System.currentTimeMillis() - started)
                    if (debuggable) keepReading(pass.name, words)
                    if (best == null || reading.score > best.score || (reading.score == best.score && words.size > best.words.size)) best = reading
                    if (reading.goodEnough) break
                }
                best?.words ?: emptyList()
            } catch (e: RuntimeException) {
                Timber.w(e, "Reading the label failed")
                null
            } catch (e: UnsatisfiedLinkError) {
                // The native reading code could not be loaded on this phone: report it, do not crash.
                Timber.w(e, "The reading engine could not be loaded")
                null
            } finally {
                pixes.forEach { it.recycle() }
                engine.recycle()
                if (prepared !== photo) prepared.recycle()
            }
        }
    }

    /** The ways to read this photo, plainest first. Every picture made here is added to [pixes] so it is released afterwards. */
    private fun passesFor(photo: Bitmap, pixes: MutableList<Pix>): List<Pass> {
        val colour = ReadFile.readBitmap(photo)?.also { pixes += it } ?: return emptyList()
        val passes = mutableListOf(
            Pass("plain-auto", colour, TessBaseAPI.PageSegMode.PSM_AUTO),
            Pass("plain-sparse", colour, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT),
        )
        cleaned(colour, pixes)?.let {
            passes += Pass("clean-auto", it, TessBaseAPI.PageSegMode.PSM_AUTO)
            passes += Pass("clean-sparse", it, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
        }
        return passes
    }

    /** Grey, with uneven lighting flattened and the edges sharpened: helps on a curved pack that is lit from one side. */
    private fun cleaned(colour: Pix, pixes: MutableList<Pix>): Pix? = try {
        val grey = Convert.convertTo8(colour)?.also { pixes += it }
        val flat = grey?.let { AdaptiveMap.backgroundNormMorph(it) }?.also { pixes += it }
        flat?.let { Enhance.unsharpMasking(it) }?.also { pixes += it }
    } catch (e: RuntimeException) {
        Timber.w(e, "Cleaning the photo failed; the plain readings are used")
        null
    }

    private fun recognise(engine: TessBaseAPI, pass: Pass): List<OcrWord>? {
        engine.setPageSegMode(pass.mode)
        engine.setImage(pass.image)
        engine.getUTF8Text() // runs the recognition; the words below come from the same pass
        val iterator = engine.resultIterator ?: return null
        val words = mutableListOf<OcrWord>()
        iterator.begin()
        do {
            val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
            if (text.isNotEmpty()) {
                val box = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                words += OcrWord(text, box.left, box.top, box.right, box.bottom, iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD).roundToInt())
            }
        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        iterator.delete()
        return words
    }

    /** How good a reading is: how many nutrients the parser found, and whether the table is complete and its numbers agree. */
    private fun judge(name: String, words: List<OcrWord>): Reading {
        val parsed = NutritionLabelParser.parse(words) ?: return Reading(name, words, 0, false)
        val v = parsed.per100
        val plausible = LabelPlausibility.check(
            LabelPlausibility.Label(
                v[Nutrient.KCAL], v[Nutrient.PROTEIN], v[Nutrient.CARBS], v[Nutrient.FAT],
                v[Nutrient.SUGARS], v[Nutrient.SAT_FAT], v[Nutrient.FIBER], v[Nutrient.SODIUM],
            ),
        ) is LabelPlausibility.Verdict.Accepted
        val score = v.size * POINTS_PER_NUTRIENT + (if (parsed.isComplete) COMPLETE_BONUS else 0) + (if (plausible) PLAUSIBLE_BONUS else 0) - parsed.unsure.size
        return Reading(name, words, score, parsed.isComplete && plausible && parsed.unsure.isEmpty())
    }

    private fun keepPhoto(photo: Bitmap) {
        try {
            val dir = File(context.cacheDir, DEBUG_DIR).apply { mkdirs() }
            File(dir, "photo.jpg").outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } catch (e: java.io.IOException) {
            Timber.w(e, "Could not keep the debug photo")
        }
    }

    private fun keepReading(name: String, words: List<OcrWord>) {
        try {
            val dir = File(context.cacheDir, DEBUG_DIR).apply { mkdirs() }
            File(dir, "$name.tsv").writeText(words.joinToString("\n") { listOf(it.text, it.left, it.top, it.right, it.bottom, it.confidence).joinToString("\t") })
        } catch (e: java.io.IOException) {
            Timber.w(e, "Could not keep the debug reading")
        }
    }

    /** Big photos are reduced: reading time grows with size, and small print is already sharp enough at this width. */
    private fun scaledForReading(photo: Bitmap): Bitmap {
        val longest = max(photo.width, photo.height)
        if (longest <= MAX_SIDE) return photo
        val scale = MAX_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(photo, (photo.width * scale).roundToInt(), (photo.height * scale).roundToInt(), true)
    }

    private companion object {
        const val LANGUAGE = "eng"
        const val MAX_SIDE = 2_400
        const val DEBUG_DIR = "label-debug"
        const val JPEG_QUALITY = 90
        const val POINTS_PER_NUTRIENT = 2
        const val COMPLETE_BONUS = 10
        const val PLAUSIBLE_BONUS = 10
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LabelReaderModule {
    @Binds
    abstract fun bindLabelReader(reader: TesseractLabelReader): LabelReader
}
