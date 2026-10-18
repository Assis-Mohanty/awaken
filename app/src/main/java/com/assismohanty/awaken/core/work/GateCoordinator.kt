package com.assismohanty.awaken.core.work

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkManager
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.GateCheck
import com.assismohanty.awaken.core.domain.GateRunController
import com.assismohanty.awaken.core.domain.StartGateResult
import com.assismohanty.awaken.core.domain.StartGateUseCase
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Ties a Focus Gate run to the process lifecycle, notifications and the end-of-gate job (ARCH §3.3). Registered on
 * `ProcessLifecycleOwner`, so background time is measured for the whole app, not per screen. Clears and collapses go
 * to [CompletionCenter], so the reward sheet or the ember window shows on whichever screen is open.
 */
@Singleton
class GateCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val startGate: StartGateUseCase,
    private val controller: GateRunController,
    private val completionCenter: CompletionCenter,
    private val notifications: GateNotifications,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    // Screen ticks must not account background time: only the process lifecycle does, on return.
    @Volatile private var inForeground = false

    // Lifecycle callbacks are handled strictly in order, one at a time. Launching each one separately on a
    // multi-threaded scope could run a quick return before the stop it follows, leaving a stale stop on the run
    // that would later count foreground time as background.
    private val lifecycleEvents = Channel<Boolean>(Channel.UNLIMITED).also { events ->
        appScope.launch {
            for (started in events) {
                try {
                    if (started) handleStart() else handleStop()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Gate lifecycle event failed")
                }
            }
        }
    }

    suspend fun start(rank: GateRank, stat: Stat, label: String?): StartGateResult {
        val result = startGate(rank, stat, label)
        when (result) {
            is StartGateResult.Started -> scheduleEnd(result.run)
            StartGateResult.TooLate -> completionCenter.message(UserMessage.GateTooLate)
            StartGateResult.AlreadyRunning, StartGateResult.NoHunter -> Unit
        }
        return result
    }

    suspend fun pause() {
        controller.pause()
        afterTimingChange()
    }

    suspend fun resume() {
        controller.resume()
        afterTimingChange()
    }

    suspend fun abandon() {
        controller.abandon()
        GateEndWorker.cancel(workManager)
        notifications.cancelAll()
    }

    /** Re-evaluates the running gate (screen tick or return to foreground). */
    suspend fun check(): GateCheck = controller.check().also(::report)

    /** A screen tick: claims a finished gate while the hunter watches. No-op in the background. */
    suspend fun tick() {
        if (inForeground) check()
    }

    override fun onStart(owner: LifecycleOwner) {
        inForeground = true
        lifecycleEvents.trySend(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        inForeground = false
        lifecycleEvents.trySend(false)
    }

    private suspend fun handleStart() {
        notifications.cancelRunning()
        check()
    }

    private suspend fun handleStop() {
        val run = controller.onAppStopped() ?: return
        val now = clock.now()
        notifications.showRunning(
            gateName = run.gateRank.name,
            endAt = GateRunMath.endAt(run.timing, now),
            breachLeft = GateRunMath.breachLeft(run.backgroundMs),
            paused = GateRunMath.isPaused(run.timing, now),
            pauseEndsAt = GateRunMath.pauseEndsAt(run.timing),
        )
    }

    private fun report(check: GateCheck) {
        when (check) {
            is GateCheck.Running -> Unit
            is GateCheck.Cleared -> {
                GateEndWorker.cancel(workManager)
                notifications.cancelAll()
                completionCenter.completed(check.outcome)
            }
            is GateCheck.Collapsed -> {
                GateEndWorker.cancel(workManager)
                notifications.cancelAll()
                val reason = check.run.collapseReason
                if (check.run.status == GateRunStatus.COLLAPSED && reason != null) {
                    completionCenter.gateCollapsed(reason, check.run.backgroundMs)
                }
            }
            GateCheck.NoRun -> Unit
        }
    }

    // The end moves with a pause, so the end-of-gate job is re-armed whenever the timing changes.
    private suspend fun afterTimingChange() {
        val check = check()
        if (check is GateCheck.Running) scheduleEnd(check.run)
    }

    private fun scheduleEnd(run: GateRunEntity) {
        val now = clock.now()
        GateEndWorker.schedule(workManager, Duration.between(now, GateRunMath.endAt(run.timing, now)))
    }
}
