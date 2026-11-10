package com.assismohanty.awaken.core.protect

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** App icons for the Lockdown screens (the slots), looked up once per package. On-device only; nothing leaves the phone. */
@Singleton
class AppIcons @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = HashMap<String, ImageBitmap?>()

    @Synchronized
    fun icon(packageName: String): ImageBitmap? = cache.getOrPut(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap() }.getOrNull()
    }
}
