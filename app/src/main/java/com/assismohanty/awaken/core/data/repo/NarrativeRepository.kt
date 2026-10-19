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

/** One bundled story chapter (GD §11), unlocked when the hunter reaches [unlockLevel]. */
@Serializable
data class Chapter(val id: String, val unlockLevel: Int, val title: String, val body: String)

@Serializable
private data class ChaptersFile(val version: Int, val chapters: List<Chapter>)

/** Loads `assets/narrative/` (system lines, chapters) once each, off the main thread (GD §11). */
@Singleton
class NarrativeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var systemLines: Map<String, List<String>>? = null
    private var chapters: List<Chapter>? = null

    suspend fun systemLines(): Map<String, List<String>> = systemLines ?: mutex.withLock {
        systemLines ?: withContext(ioDispatcher) {
            json.decodeFromString(SystemLinesFile.serializer(), readAsset(SYSTEM_LINES)).lines
        }.also { systemLines = it }
    }

    /** Chapters in unlock order. */
    suspend fun chapters(): List<Chapter> = chapters ?: mutex.withLock {
        chapters ?: withContext(ioDispatcher) {
            json.decodeFromString(ChaptersFile.serializer(), readAsset(CHAPTERS)).chapters.sortedBy { it.unlockLevel }
        }.also { chapters = it }
    }

    private fun readAsset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    private companion object {
        const val SYSTEM_LINES = "narrative/system_lines.json"
        const val CHAPTERS = "narrative/chapters.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
