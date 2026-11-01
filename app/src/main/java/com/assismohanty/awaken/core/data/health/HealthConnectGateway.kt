package com.assismohanty.awaken.core.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthSession
import com.assismohanty.awaken.engine.HealthWindow
import com.assismohanty.awaken.engine.SleepStage
import com.assismohanty.awaken.engine.SleepStageType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The real Health Connect client (connect-client 1.1.0). Reads only; never writes (ARCH §3.4).
 *
 * Trust (GD §6.2 "Health data trust"): manual-entry records are flagged on sessions and left out of the totals by
 * aggregating only over the apps that wrote at least one non-manual record ([trustedOrigins]); the aggregate itself
 * still does Health Connect's own cross-source dedupe. The rules that act on the flags live in `:engine`.
 */
@Singleton
class HealthConnectGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HealthGateway {

    override val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    override val distancePermission: String = HealthPermission.getReadPermission(DistanceRecord::class)
    override val caloriesPermission: String = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    override val heartRatePermission: String = HealthPermission.getReadPermission(HeartRateRecord::class)

    override suspend fun status(): HealthStatus = when (HealthConnectClient.getSdkStatus(context, HealthGateway.PROVIDER_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.UPDATE_REQUIRED
        else -> HealthStatus.NOT_INSTALLED
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context, HealthGateway.PROVIDER_PACKAGE)

    override suspend fun grantedPermissions(): Set<String> = withContext(ioDispatcher) {
        if (status() != HealthStatus.AVAILABLE) return@withContext emptySet()
        guarded { client().permissionController.getGrantedPermissions() } ?: emptySet()
    }

    override suspend fun revokeAll() = withContext(ioDispatcher) {
        if (status() != HealthStatus.AVAILABLE) return@withContext
        guarded { client().permissionController.revokeAllPermissions() }
        Unit
    }

    override suspend fun readDay(window: HealthWindow): HealthDay = withContext(ioDispatcher) {
        try {
            val client = client()
            val granted = client.permissionController.getGrantedPermissions()
            val canDistance = distancePermission in granted
            val canCalories = caloriesPermission in granted
            val canHeartRate = heartRatePermission in granted
            val range = TimeRangeFilter.between(window.start, window.end)

            val steps = trustedAggregate(client, StepsRecord::class, range, setOf(StepsRecord.COUNT_TOTAL))
                ?.get(StepsRecord.COUNT_TOTAL) ?: 0L
            val distance = if (canDistance) {
                trustedAggregate(client, DistanceRecord::class, range, setOf(DistanceRecord.DISTANCE_TOTAL))
                    ?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters ?: 0.0
            } else {
                0.0
            }
            val calories = if (canCalories) {
                trustedAggregate(client, ActiveCaloriesBurnedRecord::class, range, setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL))
                    ?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0
            } else {
                0.0
            }
            val heartRate = if (canHeartRate) {
                trustedAggregate(client, HeartRateRecord::class, range, setOf(HeartRateRecord.BPM_AVG))
                    ?.get(HeartRateRecord.BPM_AVG)?.toInt()
            } else {
                null
            }

            val exercise = readAll(client, ExerciseSessionRecord::class, range).map { record ->
                exerciseSession(client, record, canDistance, canCalories, canHeartRate)
            }
            // Sleep that ended in the window may have started the evening before: look back far enough to catch it.
            val sleepRange = TimeRangeFilter.between(window.start.minus(SLEEP_LOOKBACK), window.end)
            val sleep = readAll(client, SleepSessionRecord::class, sleepRange).map(::sleepSession)

            HealthDay(
                steps = steps,
                exercise = exercise,
                sleep = sleep,
                distanceMeters = distance,
                activeKcal = calories,
                avgHeartRate = heartRate,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw HealthReadException(e)
        }
    }

    override suspend fun hasStepData(window: HealthWindow): Boolean = withContext(ioDispatcher) {
        if (status() != HealthStatus.AVAILABLE) return@withContext false
        guarded { trustedOrigins(client(), StepsRecord::class, TimeRangeFilter.between(window.start, window.end)).isNotEmpty() } ?: false
    }

    /** One workout with its own distance, active calories and average heart rate, read from the app that wrote it. */
    private suspend fun exerciseSession(
        client: HealthConnectClient,
        record: ExerciseSessionRecord,
        canDistance: Boolean,
        canCalories: Boolean,
        canHeartRate: Boolean,
    ): HealthSession {
        val metrics = buildSet {
            if (canDistance) add(DistanceRecord.DISTANCE_TOTAL)
            if (canCalories) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (canHeartRate) add(HeartRateRecord.BPM_AVG)
        }
        val totals = if (metrics.isEmpty()) {
            null
        } else {
            client.aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime),
                    dataOriginFilter = setOf(record.metadata.dataOrigin),
                ),
            )
        }
        return HealthSession(
            start = record.startTime,
            end = record.endTime,
            manualEntry = record.metadata.isManual(),
            origin = record.metadata.dataOrigin.packageName,
            label = record.title?.takeIf { it.isNotBlank() } ?: exerciseTypeLabel(record.exerciseType),
            distanceMeters = totals?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            activeKcal = totals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            avgHeartRate = totals?.get(HeartRateRecord.BPM_AVG)?.toInt(),
        )
    }

    private fun sleepSession(record: SleepSessionRecord): HealthSession = HealthSession(
        start = record.startTime,
        end = record.endTime,
        manualEntry = record.metadata.isManual(),
        origin = record.metadata.dataOrigin.packageName,
        stages = record.stages.map { SleepStage(it.startTime, it.endTime, sleepStageType(it.stage)) },
    )

    private fun sleepStageType(stage: Int): SleepStageType = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED ->
            SleepStageType.AWAKE
        SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageType.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
        else -> SleepStageType.UNKNOWN
    }

    /** A readable name for the workout types people actually log; anything else has no label and shows as "Workout". */
    private fun exerciseTypeLabel(type: Int): String? = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "strength training"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "rowing"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "dancing"
        else -> null
    }

    private fun Metadata.isManual(): Boolean = recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY

    /**
     * Health Connect's own cross-source aggregate, restricted to the apps that wrote at least one non-manual record in
     * the range. Null when every record there was typed in by hand (nothing trustworthy to total).
     */
    private suspend fun <T : Record> trustedAggregate(
        client: HealthConnectClient,
        type: KClass<T>,
        range: TimeRangeFilter,
        metrics: Set<androidx.health.connect.client.aggregate.AggregateMetric<*>>,
    ): AggregationResult? {
        val origins = trustedOrigins(client, type, range)
        if (origins.isEmpty()) return null
        return client.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = range, dataOriginFilter = origins))
    }

    private suspend fun <T : Record> trustedOrigins(client: HealthConnectClient, type: KClass<T>, range: TimeRangeFilter): Set<DataOrigin> =
        readAll(client, type, range).filterNot { it.metadata.isManual() }.mapTo(HashSet()) { it.metadata.dataOrigin }

    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, range: TimeRangeFilter): List<T> {
        val records = ArrayList<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, pageToken = pageToken))
            records.addAll(response.records)
            // The token can come back as "" instead of null at the end.
            pageToken = response.pageToken?.takeUnless { it.isEmpty() }
        } while (pageToken != null)
        return records
    }

    /** Health Connect throws for revoked permissions or a backgrounded app; treat those as "nothing". */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        val SLEEP_LOOKBACK: Duration = Duration.ofHours(18)
    }
}
