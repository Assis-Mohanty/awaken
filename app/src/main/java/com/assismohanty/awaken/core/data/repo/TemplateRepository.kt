package com.assismohanty.awaken.core.data.repo

import android.content.Context
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.model.TemplateLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Loads `assets/quests/templates.json` once, off the main thread, into engine models (GD §6.2). */
@Singleton
class TemplateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: TemplateLibrary? = null

    suspend fun library(): TemplateLibrary = cached ?: mutex.withLock {
        cached ?: withContext(ioDispatcher) {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            TemplateLibrary.parse(text).also { library ->
                // Content is validated by :engine tests; a problem here means a bad build, so log loudly but keep going.
                library.validate().forEach { Timber.e("templates.json: %s", it) }
            }
        }.also { cached = it }
    }

    private companion object {
        const val ASSET_PATH = "quests/templates.json"
    }
}
