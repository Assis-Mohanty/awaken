package com.assismohanty.awaken.core.work

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.domain.HealthSyncUseCase
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Health Connect sync while the app is in the foreground: once on every start, then every 30 minutes until it stops
 * (ENGINE_DECISIONS #51). Health Connect refuses background reads without an extra permission we don't request, so
 * there is deliberately no background worker.
 */
@Singleton
class HealthSyncCoordinator @Inject constructor(
    private val sync: HealthSyncUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private var job: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                try {
                    sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Health sync failed")
                }
                delay(INTERVAL.toMillis())
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        job?.cancel()
        job = null
    }

    private companion object {
        val INTERVAL: Duration = Duration.ofMinutes(30)
    }
}
