package com.assismohanty.awaken.core.scan

import android.content.Context
import android.graphics.Bitmap
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.OcrWord
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
 */
@Singleton
class TesseractLabelReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LabelReader {
    private val lock = Mutex()

    override suspend fun read(photo: Bitmap): List<OcrWord>? = lock.withLock {
        withContext(ioDispatcher) {
            val dataDir = prepareLanguageData() ?: return@withContext null
            val engine = TessBaseAPI()
            val prepared = scaledForReading(photo)
            try {
                if (!engine.init(dataDir.absolutePath, LANGUAGE)) return@withContext null
                engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                // Keeps the gaps between columns, which is how a table's columns stay apart.
                engine.setVariable("preserve_interword_spaces", "1")
                engine.setImage(prepared)
                engine.getUTF8Text() // runs the recognition; the words below come from the same pass
                val iterator = engine.resultIterator ?: return@withContext emptyList()
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
                words
            } catch (e: RuntimeException) {
                Timber.w(e, "Reading the label failed")
                null
            } finally {
                engine.recycle()
                if (prepared !== photo) prepared.recycle()
            }
        }
    }

    /** Copies the language data out of the app on first use (and again if the app ships a different file). Null if it can not be copied. */
    private fun prepareLanguageData(): File? {
        val tessdata = File(File(context.filesDir, "ocr"), "tessdata").apply { mkdirs() }
        val target = File(tessdata, "$LANGUAGE.traineddata")
        return try {
            val shipped = context.assets.openFd("tessdata/$LANGUAGE.traineddata").use { it.length }
            if (!target.exists() || target.length() != shipped) {
                context.assets.open("tessdata/$LANGUAGE.traineddata").use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            tessdata.parentFile
        } catch (e: java.io.IOException) {
            Timber.w(e, "The language data could not be prepared")
            null
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
        const val MAX_SIDE = 2_000
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LabelReaderModule {
    @Binds
    abstract fun bindLabelReader(reader: TesseractLabelReader): LabelReader
}
