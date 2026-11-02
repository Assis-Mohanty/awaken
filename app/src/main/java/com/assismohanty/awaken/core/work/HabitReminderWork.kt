package com.assismohanty.awaken.core.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.repo.LexiconSource
import com.assismohanty.awaken.core.domain.BuildHabitReminderUseCase
import com.assismohanty.awaken.core.domain.HabitReminderContent
import com.assismohanty.awaken.core.domain.HabitReminderScheduling
import com.assismohanty.awaken.engine.ReminderRules
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Per-habit reminders (GD §16.2): at most two habits have one, each fires at most once a day on its own channel, and
 * only while the habit is still open. One-shot WorkManager works with an initial delay (inexact on purpose, like the
 * directive reminder: no exact-alarm permission, and Doze may nudge them a few minutes). Each work re-arms itself, and
 * [rearm] runs when a habit is saved, at app start and after an import.
 */
@Singleton
class HabitReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val habitDao: HabitDao,
    private val clock: Clock,
) : HabitReminderScheduling {

    override suspend fun rearm() {
        val workManager = WorkManager.getInstance(context)
        // Cancel every armed reminder first, so a habit that lost its reminder or was archived stops at once.
        workManager.cancelAllWorkByTag(TAG).await()
        habitDao.withReminder().forEach { habit -> habit.reminderTime?.let { schedule(habit.id, it) } }
    }

    /** Arms [habitId]'s next reminder at [minutes] after local midnight. */
    suspend fun schedule(habitId: String, minutes: Int) {
        val now = clock.now()
        val fireAt = ReminderRules.nextFire(now, clock.zone(), LocalTime.ofSecondOfDay(minutes * SECONDS_PER_MINUTE))
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(habitId),
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<HabitReminderWorker>()
                .setInputData(workDataOf(HABIT_ID to habitId))
                .setInitialDelay(Duration.between(now, fireAt).toMillis(), TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build(),
        ).await()
    }

    companion object {
        const val HABIT_ID = "habit_id"
        private const val TAG = "habit-reminder"
        private const val SECONDS_PER_MINUTE = 60L
        fun workName(habitId: String) = "habit_reminder_$habitId"
    }
}

/** Posts one habit's reminder if it is still worth it, then arms the next day's. */
@HiltWorker
class HabitReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val build: BuildHabitReminderUseCase,
    private val notifications: HabitReminderNotifications,
    private val habitDao: HabitDao,
    private val scheduler: HabitReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getString(HabitReminderScheduler.HABIT_ID) ?: return Result.success()
        try {
            build(habitId)?.let(notifications::show)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Habit reminder %s failed", habitId)
        }
        // Always re-arm while the habit still has a reminder: a broken chain would silence it until the next app start.
        habitDao.get(habitId)?.takeIf { it.active }?.reminderTime?.let { scheduler.schedule(habitId, it) }
        return Result.success()
    }
}

/** The habit reminder notification: its own channel, one per habit per day (a stable id replaces rather than stacks). */
@Singleton
class HabitReminderNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: NotificationAccess,
    private val words: LexiconSource = LexiconSource.Fixed,
) {
    @SuppressLint("MissingPermission") // guarded by NotificationAccess.isGranted() on the first line
    fun show(content: HabitReminderContent): Boolean {
        if (!access.isGranted()) return false
        ensureChannel()
        val id = notificationId(content.habitId)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_HABIT)
            .setSmallIcon(R.drawable.ic_stat_awaken)
            .setContentTitle(content.title)
            .setContentText(words.resources(context).getString(R.string.habit_reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    private fun ensureChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_HABIT, words.resources(context).getString(R.string.habit_reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = words.resources(context).getString(R.string.habit_reminder_channel_description) },
        )
    }

    companion object {
        const val CHANNEL_HABIT = "habit_reminder"

        /** Stable per habit, in a band that cannot meet the directive (811), last call (812) or review (813) ids. */
        fun notificationId(habitId: String): Int = BASE_ID + (habitId.hashCode() and Int.MAX_VALUE) % ID_SPAN

        private const val BASE_ID = 1_000
        private const val ID_SPAN = 100_000
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HabitReminderModule {
    @Binds
    abstract fun bindHabitReminderScheduling(impl: HabitReminderScheduler): HabitReminderScheduling
}
