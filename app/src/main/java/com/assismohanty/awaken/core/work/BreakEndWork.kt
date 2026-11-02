package com.assismohanty.awaken.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.domain.BreakAlarm
import com.assismohanty.awaken.core.domain.PomodoroSessions
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/** One-shot at a Pomodoro break's end (GD §16.3). Inexact on purpose, like every reminder in the app. */
@Singleton
class WorkManagerBreakAlarm @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : BreakAlarm {
    override suspend fun schedule(at: Instant) {
        val delay = Duration.between(clock.now(), at).toMillis().coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BreakEndWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        ).await()
    }

    override suspend fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME).await()
        // Nothing left to announce once the session is over.
    }

    companion object {
        const val NAME = "pomodoro_break_end"
    }
}

/**
 * The break is over: say so on the focus channel with a tap-through. Only while the session still waits at that break; a
 * session that was ended, or whose next round was already started, stays quiet. The next round never starts by itself.
 */
@HiltWorker
class BreakEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessions: PomodoroSessions,
    private val notifications: GateNotifications,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            sessions.sync()
            sessions.breakAlert()?.let(notifications::showBreakOver)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Break end failed")
        }
        return Result.success()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BreakAlarmModule {
    @Binds
    abstract fun bindBreakAlarm(impl: WorkManagerBreakAlarm): BreakAlarm

    @Binds
    abstract fun bindFocusRoundPort(impl: GateCoordinator): com.assismohanty.awaken.core.domain.FocusRoundPort
}
