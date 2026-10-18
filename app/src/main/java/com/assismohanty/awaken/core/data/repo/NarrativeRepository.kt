package com.assismohanty.awaken.core.data.repo

import android.content.Context
import com.assismohanty.awaken.core.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SystemLinesFile(val version: Int, val placeholders: List<String> = emptyList(), val lines: Map<String, List<String>>)

/** Loads `assets/narrative/system_lines.json` once, off the main thread (GD §11). Chapters arrive with Status (Phase 9). */
@Singleton
class NarrativeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var systemLines: Map<String, List<String>>? = null

    suspend fun systemLines(): Map<String, List<String>> = systemLines ?: mutex.withLock {
        systemLines ?: withContext(ioDispatcher) {
            val text = context.assets.open(SYSTEM_LINES).bufferedReader().use { it.readText() }
            json.decodeFromString(SystemLinesFile.serializer(), text).lines
        }.also { systemLines = it }
    }

    private companion object {
        const val SYSTEM_LINES = "narrative/system_lines.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
