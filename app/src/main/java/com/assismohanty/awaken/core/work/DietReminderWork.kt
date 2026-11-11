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
import com.assismohanty.awaken.core.data.db.entity.Meal
import com.assismohanty.awaken.core.data.repo.LexiconSource
import com.assismohanty.awaken.core.data.repo.MealReminderStore
import com.assismohanty.awaken.core.domain.BodyLog
import com.assismohanty.awaken.core.domain.DietReminderScheduling
import com.assismohanty.awaken.core.domain.DietTracker
import com.assismohanty.awaken.core.domain.FoodLibrary
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
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Meal reminders and the end-of-fast notice (Diet v2, GD 20.4). One-shot WorkManager works with an initial delay, inexact on
 * purpose like the other reminders. A meal reminder fires once a day and stays quiet when that meal already has food in
 * today's diary; there is never a follow-up for a missed one. Each work re-arms itself.
 */
@Singleton
class DietReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: MealReminderStore,
    private val bodyLog: BodyLog,
    private val clock: Clock,
) : DietReminderScheduling {

    override suspend fun rearmMeals() {
        WorkManager.getInstance(context).cancelAllWorkByTag(MEAL_TAG).await()
        store.reminders.first().forEach { (mealId, minutes) -> scheduleMeal(mealId, minutes) }
    }

    suspend fun scheduleMeal(mealId: String, minutes: Int) {
        val now = clock.now()
        val fireAt = ReminderRules.nextFire(now, clock.zone(), LocalTime.ofSecondOfDay(minutes * SECONDS_PER_MINUTE))
        WorkManager.getInstance(context).enqueueUniqueWork(
            MEAL_WORK + mealId,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MealReminderWorker>()
                .setInputData(workDataOf(MEAL_ID to mealId))
                .setInitialDelay(Duration.between(now, fireAt).toMillis(), TimeUnit.MILLISECONDS)
                .addTag(MEAL_TAG)
                .build(),
        ).await()
    }

    override suspend fun rearmFast() {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(FAST_WORK).await()
        val fast = bodyLog.observeActiveFast().first() ?: return
        val delay = Duration.between(clock.now(), fast.startedAt.plus(Duration.ofMinutes(fast.targetMinutes.toLong())))
        if (delay.isNegative || delay.isZero) return
        manager.enqueueUniqueWork(
            FAST_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FastEndWorker>()
                .setInputData(workDataOf(FAST_ID to fast.id))
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        ).await()
    }

    companion object {
        const val MEAL_ID = "meal_id"
        const val FAST_ID = "fast_id"
        private const val MEAL_TAG = "meal-reminder"
        private const val MEAL_WORK = "meal_reminder_"
        private const val FAST_WORK = "fast_end"
        private const val SECONDS_PER_MINUTE = 60L
    }
}

/** Posts one meal's reminder when that meal is still empty today, then arms tomorrow's. */
@HiltWorker
class MealReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val store: MealReminderStore,
    private val diet: DietTracker,
    private val library: FoodLibrary,
    private val notifications: DietNotifications,
    private val scheduler: DietReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mealId = inputData.getString(DietReminderScheduler.MEAL_ID) ?: return Result.success()
        // Gone from the store means the hunter switched it off since this work was armed: stay silent and do not re-arm.
        val minutes = store.reminders.first()[mealId] ?: return Result.success()
        try {
            if (diet.entriesOn(diet.today(), mealId).isEmpty()) {
                val slot = library.observeMealSlots().first().firstOrNull { it.id == mealId }
                notifications.showMeal(mealId, slot?.name)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Meal reminder %s failed", mealId)
        }
        scheduler.scheduleMeal(mealId, minutes)
        return Result.success()
    }
}

/** Tells the hunter, quietly, that a fast reached its target; only while that same fast still runs and targets are visible. */
@HiltWorker
class FastEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bodyLog: BodyLog,
    private val diet: DietTracker,
    private val notifications: DietNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(DietReminderScheduler.FAST_ID) ?: return Result.success()
        try {
            val running = bodyLog.observeActiveFast().first()?.takeIf { it.id == id }
            val visible = diet.observePlan().first()?.targetsVisible == true
            if (running != null && visible) notifications.showFastReached()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Fast notice failed")
        }
        return Result.success()
    }
}

/** The Diet notifications: a reminder per meal on its own channel, and the end of a fast on a low-importance one. */
@Singleton
class DietNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: NotificationAccess,
    private val words: LexiconSource = LexiconSource.Fixed,
) {
    @SuppressLint("MissingPermission") // guarded by NotificationAccess.isGranted() on the first line
    fun showMeal(mealId: String, customName: String?): Boolean {
        if (!access.isGranted()) return false
        ensureChannels()
        val res = words.resources(context)
        val name = customName ?: Meal.entries.firstOrNull { it.name == mealId }?.let { res.getString(builtInName(it)) } ?: mealId
        post(CHANNEL_MEAL, mealNotificationId(mealId), res.getString(R.string.diet_reminder_text, name.lowercase()), NotificationCompat.PRIORITY_DEFAULT)
        return true
    }

    @SuppressLint("MissingPermission")
    fun showFastReached(): Boolean {
        if (!access.isGranted()) return false
        ensureChannels()
        post(CHANNEL_FAST, FAST_ID, words.resources(context).getString(R.string.diet_fast_notice), NotificationCompat.PRIORITY_LOW)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun post(channel: String, id: Int, text: String, priority: Int) {
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_TODAY).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_awaken)
            .setContentTitle(words.resources(context).getString(R.string.diet_title))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(priority)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val res = words.resources(context)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_MEAL, res.getString(R.string.diet_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_FAST, res.getString(R.string.diet_fast_channel), NotificationManager.IMPORTANCE_LOW))
    }

    private fun builtInName(meal: Meal): Int = when (meal) {
        Meal.BREAKFAST -> R.string.diet_meal_breakfast
        Meal.LUNCH -> R.string.diet_meal_lunch
        Meal.DINNER -> R.string.diet_meal_dinner
        Meal.SNACK -> R.string.diet_meal_snack
    }

    companion object {
        const val CHANNEL_MEAL = "diet_meal_reminder"
        const val CHANNEL_FAST = "diet_fast"
        private const val FAST_ID = 301_000
        private const val MEAL_BASE = 300_000
        private const val MEAL_SPAN = 1_000

        /** Stable per meal, in a band clear of the habit (1,000 to 101,000) and daily (811 to 813) ids. */
        fun mealNotificationId(mealId: String): Int = MEAL_BASE + (mealId.hashCode() and Int.MAX_VALUE) % MEAL_SPAN
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietReminderModule {
    @Binds
    abstract fun bindDietReminderScheduling(impl: DietReminderScheduler): DietReminderScheduling
}
