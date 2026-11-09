package com.assismohanty.awaken.core.work

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.time.Instant
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.assismohanty.awaken.core.audio.ChimePlayer
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.domain.GymTracker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Fires when a gym rest timer runs out, even if the app was killed meanwhile (the end time is stored on the workout).
 * If the open Gym screen already handled the end there is nothing left to clear and this does nothing.
 */
@HiltWorker
class RestEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gym: GymTracker,
    private val notifications: GateNotifications,
    private val chimePlayer: ChimePlayer,
    private val uiPrefs: UiPrefsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workoutId = inputData.getString(KEY_WORKOUT_ID) ?: return Result.success()
        val end = gym.finishRestIfDue(workoutId) ?: return Result.success()
        notifications.showRestOver()
        if (end.chime && gym.restAlerts().sound) chimePlayer.play(uiPrefs.prefs.first().chime)
        return Result.success()
    }

    companion object {
        const val NAME = "gym_rest_end"
        const val KEY_WORKOUT_ID = "workout_id"
    }
}

/** Keeps the end-of-rest job in step with the running workout's stored rest end. */
interface RestScheduler {
    fun sync(workout: WorkoutEntity?)
}

@Singleton
class WorkRestScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val clock: Clock,
) : RestScheduler {
    private var startedFor: Instant? = null

    override fun sync(workout: WorkoutEntity?) {
        val manager = WorkManager.getInstance(context)
        val endsAt = workout?.restEndsAt
        if (workout == null || endsAt == null) {
            manager.cancelUniqueWork(RestEndWorker.NAME)
            if (startedFor != null) {
                startedFor = null
                context.stopService(Intent(context, RestTimerService::class.java))
            }
            return
        }
        // The service keeps the countdown and the chime with the screen off; the worker below is the fallback when it cannot start.
        if (startedFor != endsAt) {
            startedFor = endsAt
            try {
                ContextCompat.startForegroundService(context, RestTimerService.intent(context, RestTimerService.ACTION_START, workout.id, endsAt))
            } catch (e: Exception) {
                Timber.w(e, "The rest timer service could not start; the end-of-rest job will do")
            }
        }
        val delay = Duration.between(clock.now(), endsAt).toMillis().coerceAtLeast(0)
        manager.enqueueUniqueWork(
            RestEndWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RestEndWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(RestEndWorker.KEY_WORKOUT_ID, workout.id).build())
                .build(),
        )
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class RestModule {
    @dagger.Binds
    abstract fun bindRestScheduler(scheduler: WorkRestScheduler): RestScheduler
}
