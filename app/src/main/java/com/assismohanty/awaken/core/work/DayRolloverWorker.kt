package com.assismohanty.awaken.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.domain.RunRolloverUseCase
import com.assismohanty.awaken.engine.GameDay
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Background day rollover (ARCH §3.2): hourly, plus once just after the next day start, so days finalize and the
 * directive is ready even if the app isn't opened. Each run re-arms the day-start job for the following day.
 */
@HiltWorker
class DayRolloverWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runRollover: RunRolloverUseCase,
    private val hunterDao: HunterDao,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        runRollover()
        scheduleNextDayStart(WorkManager.getInstance(applicationContext), hunterDao, clock)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Background rollover failed")
        Result.retry()
    }

    companion object {
        private const val PERIODIC_NAME = "day_rollover_periodic"
        private const val DAY_START_NAME = "day_rollover_day_start"
        private val PERIOD: Duration = Duration.ofHours(1)

        /** Idempotent: safe to call on every app start. */
        suspend fun schedule(workManager: WorkManager, hunterDao: HunterDao, clock: Clock) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DayRolloverWorker>(PERIOD.toMinutes(), TimeUnit.MINUTES).build(),
            )
            scheduleNextDayStart(workManager, hunterDao, clock)
        }

        private suspend fun scheduleNextDayStart(workManager: WorkManager, hunterDao: HunterDao, clock: Clock) {
            val dayStartHour = hunterDao.getProfile()?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR
            val nextStart = GameDay.endOf(clock.gameDate(dayStartHour), clock.zone(), dayStartHour)
            val delay = Duration.between(clock.now(), nextStart).coerceAtLeast(Duration.ZERO)
            workManager.enqueueUniqueWork(
                DAY_START_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DayRolloverWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build(),
            )
        }
    }
}
