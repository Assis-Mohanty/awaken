package com.assismohanty.awaken.core.data.export

import android.content.Context
import android.net.Uri
import com.assismohanty.awaken.core.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Reads and writes the backup file through the Storage Access Framework uri the hunter picked (no storage permission). */
@Singleton
class BackupFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun write(uri: Uri, text: String) = withContext(ioDispatcher) {
        // "wt" truncates an existing file; some document providers only accept "w".
        val resolver = context.contentResolver
        val out = checkNotNull(runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri, "w")) {
            "No output stream for $uri"
        }
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** The file's text, or null when it can't be opened or is larger than [MAX_BYTES] (not a plausible backup). */
    suspend fun read(uri: Uri): String? = withContext(ioDispatcher) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) return@use null
                out.write(buffer, 0, read)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }
    }

    private companion object {
        const val MAX_BYTES = 64 * 1024 * 1024
        const val BUFFER_BYTES = 64 * 1024
    }
}
