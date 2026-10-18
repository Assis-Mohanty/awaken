package com.assismohanty.awaken.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.DeviceUptime
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.engine.BackgroundDelta
import com.assismohanty.awaken.engine.GatePhase
import com.assismohanty.awaken.engine.GateRules
import com.assismohanty.awaken.engine.GateRunMath
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * One-shot at a gate's planned end (ARCH §3.3): "Gate cleared — return to claim". It only notifies; the claim happens
 * when the hunter returns, so the reward sheet is seen. If the run was paused since scheduling, it re-arms itself.
 */
@HiltWorker
class GateEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gateRunDao: GateRunDao,
    private val notifications: GateNotifications,
    private val uptime: DeviceUptime,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val run = gateRunDao.running() ?: return Result.success()
        val stopAt = run.lastStopAt ?: return Result.success()
        val stopElapsed = run.lastStopElapsedMs ?: return Result.success()
        val now = clock.now()
        if (GateRunMath.phase(run.timing, now) == GatePhase.Finished) {
            // Only promise a reward if the time away so far is within budget; otherwise it collapses on return.
            val nowElapsed = uptime.elapsedRealtimeMs()
            val sameBoot = uptime.bootCount() == run.bootCount && nowElapsed >= stopElapsed
            val delta = GateRunMath.backgroundOnResume(run.timing, stopAt, stopElapsed, nowElapsed, sameBoot)
            val cleared = delta is BackgroundDelta.Counted && !GateRules.isCollapsed(run.backgroundMs + delta.addedMs)
            if (cleared) notifications.showEnded(run.gateRank.name) else notifications.cancelRunning()
        } else {
            schedule(WorkManager.getInstance(applicationContext), Duration.between(now, GateRunMath.endAt(run.timing, now)))
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "gate_end"

        fun schedule(workManager: WorkManager, delay: Duration) {
            workManager.enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GateEndWorker>()
                    .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(NAME)
        }
    }
}
