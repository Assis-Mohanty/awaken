package com.assismohanty.awaken.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.assismohanty.awaken.core.domain.PathJob
import com.assismohanty.awaken.core.domain.PathRequestMode
import com.assismohanty.awaken.core.domain.PathRequestResult
import com.assismohanty.awaken.core.domain.PathWorkScheduler
import com.assismohanty.awaken.core.domain.RunPathRequestUseCase
import com.assismohanty.awaken.core.domain.SendReportsUseCase
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Asks the Path Oracle for a ladder and/or a week of quests (ARCH §3.7). The `requestId` lives in the work's input data,
 * so every WorkManager retry resends the same id and the proxy never counts it twice.
 */
@HiltWorker
class PlanWeekWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runPathRequest: RunPathRequestUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val job = PathJob(
            pathId = inputData.getString(KEY_PATH_ID) ?: return Result.failure(),
            mode = inputData.getString(KEY_MODE)?.let { runCatching { PathRequestMode.valueOf(it) }.getOrNull() } ?: return Result.failure(),
            requestId = inputData.getString(KEY_REQUEST_ID) ?: return Result.failure(),
            isoWeek = inputData.getString(KEY_ISO_WEEK),
            manual = inputData.getBoolean(KEY_MANUAL, false),
        )
        return try {
            when (runPathRequest(job, runAttemptCount)) {
                PathRequestResult.Done -> Result.success()
                PathRequestResult.Retry -> Result.retry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Path request failed")
            if (runAttemptCount + 1 >= RunPathRequestUseCase.MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val KEY_PATH_ID = "path_id"
        const val KEY_MODE = "mode"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_ISO_WEEK = "iso_week"
        const val KEY_MANUAL = "manual"
    }
}

/** Sends the report outbox (PRD F21). */
@HiltWorker
class ReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sendReports: SendReportsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        when (sendReports()) {
            PathRequestResult.Done -> Result.success()
            PathRequestResult.Retry -> if (runAttemptCount + 1 >= RunPathRequestUseCase.MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Sending reports failed")
        if (runAttemptCount + 1 >= RunPathRequestUseCase.MAX_ATTEMPTS) Result.failure() else Result.retry()
    }
}

/** WorkManager implementation of [PathWorkScheduler]: unique names, CONNECTED, 30 s exponential backoff (ARCH §3.7). */
@Singleton
class WorkManagerPathScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : PathWorkScheduler {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun enqueuePlan(pathId: String, thenWeek: Boolean) {
        val mode = if (thenWeek) PathRequestMode.PLAN_THEN_WEEK else PathRequestMode.PLAN
        enqueue("path-$pathId-plan", planWeekRequest(pathId, mode, isoWeek = null, manual = false))
    }

    override fun enqueueWeek(pathId: String, isoWeek: String, manual: Boolean) {
        enqueue("path-$pathId-$isoWeek", planWeekRequest(pathId, PathRequestMode.WEEK, isoWeek, manual))
    }

    override fun enqueueReports() {
        enqueue(REPORTS_NAME, OneTimeWorkRequestBuilder<ReportWorker>().applyCommon().build())
    }

    override fun cancelAllPathWork() {
        workManager.cancelAllWorkByTag(TAG)
    }

    /** KEEP: queued or running work for the same name stays (and keeps its requestId); finished work is replaced. */
    private fun enqueue(name: String, request: OneTimeWorkRequest) {
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    private fun planWeekRequest(pathId: String, mode: PathRequestMode, isoWeek: String?, manual: Boolean): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<PlanWeekWorker>()
            .setInputData(inputData(pathId, mode, isoWeek, manual))
            .applyCommon()
            .build()

    private fun inputData(pathId: String, mode: PathRequestMode, isoWeek: String?, manual: Boolean): Data = workDataOf(
        PlanWeekWorker.KEY_PATH_ID to pathId,
        PlanWeekWorker.KEY_MODE to mode.name,
        PlanWeekWorker.KEY_REQUEST_ID to UUID.randomUUID().toString(),
        PlanWeekWorker.KEY_ISO_WEEK to isoWeek,
        PlanWeekWorker.KEY_MANUAL to manual,
    )

    private fun <B : androidx.work.WorkRequest.Builder<B, W>, W : androidx.work.WorkRequest> B.applyCommon(): B =
        setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TAG)

    private companion object {
        const val TAG = "path-oracle"
        const val REPORTS_NAME = "path-reports"
        val BACKOFF: Duration = Duration.ofSeconds(30)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PathWorkModule {
    @Binds
    abstract fun bindPathWorkScheduler(impl: WorkManagerPathScheduler): PathWorkScheduler
}
