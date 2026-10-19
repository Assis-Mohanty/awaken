package com.assismohanty.awaken.core.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthSession
import com.assismohanty.awaken.engine.HealthWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The real Health Connect client (connect-client 1.1.0). Reads only; never writes (ARCH §3.4). */
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
            val range = TimeRangeFilter.between(window.start, window.end)
            val steps = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] ?: 0L
            val exercise = readAll(client, ExerciseSessionRecord::class, range) { HealthSession(it.startTime, it.endTime) }
            // Sleep that ended in the window may have started the evening before: look back far enough to catch it.
            val sleepRange = TimeRangeFilter.between(window.start.minus(SLEEP_LOOKBACK), window.end)
            val sleep = readAll(client, SleepSessionRecord::class, sleepRange) { HealthSession(it.startTime, it.endTime) }
            HealthDay(steps = steps, exercise = exercise, sleep = sleep)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw HealthReadException(e)
        }
    }

    private suspend fun <T : androidx.health.connect.client.records.Record> readAll(
        client: HealthConnectClient,
        type: kotlin.reflect.KClass<T>,
        range: TimeRangeFilter,
        map: (T) -> HealthSession,
    ): List<HealthSession> {
        val sessions = ArrayList<HealthSession>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, pageToken = pageToken))
            response.records.mapTo(sessions, map)
            // The token can come back as "" instead of null at the end.
            pageToken = response.pageToken?.takeUnless { it.isEmpty() }
        } while (pageToken != null)
        return sessions
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
