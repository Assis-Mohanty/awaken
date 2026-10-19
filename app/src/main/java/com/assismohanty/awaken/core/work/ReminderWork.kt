package com.assismohanty.awaken.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.domain.BuildReminderUseCase
import com.assismohanty.awaken.engine.ReminderRules
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/** The two daily notifications (ARCH §3.5). */
enum class ReminderKind(val workName: String) {
    DIRECTIVE("reminder_directive"),
    LAST_CALL("reminder_last_call"),
}

/**
 * Arms the two reminders as one-shot works with an initial delay (inexact on purpose: no exact-alarm permission, and
 * Doze may nudge them by a few minutes). Each work re-arms itself when it runs; [reschedule] also runs whenever the
 * reminder time or the day start changes (see [ReminderCoordinator]).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hunterDao: HunterDao,
    private val clock: Clock,
) {
    suspend fun reschedule() = ReminderKind.entries.forEach { schedule(it) }

    suspend fun schedule(kind: ReminderKind) {
        val workManager = WorkManager.getInstance(context)
        val reminder = hunterDao.getProfile()?.reminderTime?.let(LocalTime::parse)
        // Reminder off silences both (ENGINE_DECISIONS #53); a reminder at/after 21:00 has no last call (#54).
        val time = when (kind) {
            ReminderKind.DIRECTIVE -> reminder
            ReminderKind.LAST_CALL -> ReminderRules.LAST_CALL_TIME.takeIf { reminder != null && reminder < it }
        }
        if (time == null) {
            workManager.cancelUniqueWork(kind.workName)
            return
        }
        val now = clock.now()
        val delay = Duration.between(now, ReminderRules.nextFire(now, clock.zone(), time))
        workManager.enqueueUniqueWork(
            kind.workName,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf(KIND to kind.name))
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    companion object {
        const val KIND = "kind"
    }
}

/** Posts one reminder if there is something to say, then arms the next day's. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val build: BuildReminderUseCase,
    private val notifications: ReminderNotifications,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(ReminderScheduler.KIND)?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
            ?: return Result.success()
        try {
            val content = when (kind) {
                ReminderKind.DIRECTIVE -> build.directive()
                ReminderKind.LAST_CALL -> build.lastCall()
            }
            // A missing permission only skips the post; the next run is still armed.
            content?.let(notifications::show)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Reminder %s failed", kind)
        }
        // Always re-arm, even after a failure: a broken chain would silence reminders until the next app start.
        scheduler.schedule(kind)
        return Result.success()
    }
}
