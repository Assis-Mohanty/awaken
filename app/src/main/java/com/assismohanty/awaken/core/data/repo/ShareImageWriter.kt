package com.assismohanty.awaken.core.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.assismohanty.awaken.core.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Writes the Weekly Report card to `cache/share/` and returns a FileProvider uri for ACTION_SEND (PRD F16). */
class ShareImageWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun writePng(bitmap: Bitmap): Uri = withContext(ioDispatcher) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        // One file, overwritten: old report images never pile up in the cache.
        val file = File(dir, FILE_NAME)
        // GraphicsLayer captures can be HARDWARE bitmaps; encode from a software copy.
        val software = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        file.outputStream().use { out -> check(software.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG encode failed" } }
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    }

    private companion object {
        const val SHARE_DIR = "share"
        const val FILE_NAME = "awaken_weekly_report.png"
        const val AUTHORITY_SUFFIX = ".share"
        const val PNG_QUALITY = 100
    }
}
