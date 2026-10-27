package com.assismohanty.awaken.core.data.repo

import android.content.Context
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.IdentityCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Loads `assets/identities.json` once, off the main thread (GD §14.7). */
@Singleton
class IdentityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: IdentityCatalog? = null

    suspend fun catalog(): IdentityCatalog = cached ?: mutex.withLock {
        cached ?: withContext(ioDispatcher) {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            IdentityCatalog.parse(text).also { catalog ->
                // Content is validated by :engine tests; a problem here means a bad build, so log loudly but keep going.
                catalog.validate().forEach { Timber.e("identities.json: %s", it) }
            }
        }.also { cached = it }
    }

    private companion object {
        const val ASSET_PATH = "identities.json"
    }
}
