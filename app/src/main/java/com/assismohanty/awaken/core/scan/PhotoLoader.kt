package com.assismohanty.awaken.core.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.assismohanty.awaken.core.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Opens a photo the hunter picked with the system photo picker (no storage permission), shrunk on decode so a 12 MP
 * picture never sits in memory at full size. Nothing is copied or kept.
 */
class PhotoLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun load(uri: Uri, longestSide: Int = LONGEST_SIDE): Bitmap? = withContext(ioDispatcher) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > longestSide) {
                    decoder.setTargetSize(info.size.width * longestSide / longest, info.size.height * longestSide / longest)
                }
                // Software pixels: the classifier reads them, and hardware bitmaps cannot be read back.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.onFailure { Timber.w(it, "Picked photo could not be opened") }.getOrNull()
    }

    private companion object {
        const val LONGEST_SIDE = 1_024
    }
}
