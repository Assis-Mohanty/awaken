package com.assismohanty.awaken.core.data.ai

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** Proves to the proxy that a request comes from this app, installed from Play, on a real device (ARCH §3.7). */
interface IntegrityTokenProvider {
    /** A token bound to [requestHash] (SHA-256 of the exact request bytes, standard base64), or null if none could be made. */
    suspend fun token(requestHash: String): String?
}

/**
 * Play Integrity **Standard API**: the token provider is prepared once per process (the slow step, needs the Google
 * Cloud project number), then each request asks it for a token bound to that request's hash.
 */
@Singleton
class PlayIntegrityTokenProvider @Inject constructor(
    @ApplicationContext context: Context,
    private val config: OracleConfig,
) : IntegrityTokenProvider {
    private val manager: StandardIntegrityManager = IntegrityManagerFactory.createStandard(context)
    private val mutex = Mutex()
    private var provider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    override suspend fun token(requestHash: String): String? {
        if (config.cloudProjectNumber == 0L) return null
        return try {
            val request = StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()
            prepared().request(request).await().token()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Play Integrity token failed")
            // A failed preparation is retried next time; a stale provider would keep failing.
            mutex.withLock { provider = null }
            null
        }
    }

    private suspend fun prepared(): StandardIntegrityManager.StandardIntegrityTokenProvider = mutex.withLock {
        provider ?: manager
            .prepareIntegrityToken(
                StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(config.cloudProjectNumber)
                    .build(),
            )
            .await()
            .also { provider = it }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
