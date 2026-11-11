package com.assismohanty.awaken.core.scan

import android.content.Context
import java.io.File
import java.io.IOException
import timber.log.Timber

/**
 * The English language data Tesseract needs. Tesseract reads real files, not app assets, so it is copied into private storage the
 * first time. The asset is stored compressed inside the app, so it can only be read as a stream: `AssetManager.openFd` fails for it
 * on a phone (and works in tests, which is how this once shipped broken). A stamp file records which model was copied, so a new
 * model in an app update replaces the old one, and a half-written or empty copy is replaced too.
 */
class OcrLanguageData(private val context: Context) {

    /** The folder to give Tesseract: it holds `tessdata/eng.traineddata`. Null when the data can not be put there. */
    fun ensure(): File? {
        val root = File(context.filesDir, "ocr")
        val tessdata = File(root, "tessdata")
        val target = File(tessdata, FILE)
        val stamp = File(tessdata, "$FILE.id")
        return try {
            tessdata.mkdirs()
            val current = target.exists() && target.length() >= MIN_BYTES && stamp.exists() && stamp.readText() == MODEL_ID
            if (!current) {
                val part = File(tessdata, "$FILE.part")
                context.assets.open(ASSET).use { input -> part.outputStream().use { input.copyTo(it) } }
                if (part.length() < MIN_BYTES) {
                    part.delete()
                    return null
                }
                target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                stamp.writeText(MODEL_ID)
            }
            root
        } catch (e: IOException) {
            Timber.w(e, "The language data could not be prepared")
            null
        }
    }

    companion object {
        const val ASSET = "tessdata/eng.traineddata"
        const val FILE = "eng.traineddata"

        /** Names the shipped file (tessdata_fast, English). Change it when the file in `assets/tessdata` changes. */
        const val MODEL_ID = "tessdata_fast-eng-2026-11"

        /** A model smaller than this is a failed copy: the real one is about 4 MB. */
        const val MIN_BYTES = 1_000_000L
    }
}
