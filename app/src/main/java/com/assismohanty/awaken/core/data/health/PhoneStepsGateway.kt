package com.assismohanty.awaken.core.data.health

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.HealthWindow
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.LocalRecordingClient
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.data.LocalField
import com.google.android.gms.fitness.request.LocalDataReadRequest
import com.google.android.gms.tasks.Task
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The phone's own step counter, for when no app writes steps to Health Connect (ARCH §3.4). Backed by Google's Recording
 * API on mobile: on-device, no Google account, about 10 days kept by Play services. Display only: it never verifies a
 * quest (ENGINE_DECISIONS #110). Granting [permission] is the hunter's opt-in.
 */
interface PhoneStepsGateway {
    /** The runtime permission that turns step recording on. */
    val permission: String

    /** Play services is new enough and the OS has the runtime permission (Android 10+). */
    fun isSupported(): Boolean

    fun isPermissionGranted(): Boolean

    /** Starts (or renews) step recording. Idempotent; false when it couldn't. */
    suspend fun subscribe(): Boolean

    /** Steps per window, in order; null when the read failed. Steps before the first subscription read as 0. */
    suspend fun dailySteps(windows: List<HealthWindow>): List<Long>?
}

/** [PhoneStepsGateway] over `FitnessLocal.getLocalRecordingClient` (play-services-fitness). No Google Fit API is used. */
@Singleton
class RecordingApiStepsGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PhoneStepsGateway {

    // A compile-time constant string; it is only ever requested or checked on Android 10+ (see isSupported).
    @SuppressLint("InlinedApi")
    override val permission: String = Manifest.permission.ACTIVITY_RECOGNITION

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context, LocalRecordingClient.LOCAL_RECORDING_CLIENT_STEPS_MIN_VERSION_CODE) ==
            ConnectionResult.SUCCESS

    override fun isPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun subscribe(): Boolean = withContext(ioDispatcher) {
        // Checked inline (not via isPermissionGranted) so lint sees the guard before subscribe().
        if (!isSupported() || context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return@withContext false
        guarded {
            FitnessLocal.getLocalRecordingClient(context).subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA).await()
            true
        } ?: false
    }

    override suspend fun dailySteps(windows: List<HealthWindow>): List<Long>? = withContext(ioDispatcher) {
        if (!isSupported() || !isPermissionGranted()) return@withContext null
        val client = FitnessLocal.getLocalRecordingClient(context)
        guarded {
            windows.map { window ->
                val start = window.start.epochSecond
                val end = window.end.epochSecond
                if (end <= start) return@map 0L
                val request = LocalDataReadRequest.Builder()
                    .aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
                    // One bucket covering the whole window, so a game-day that starts at 04:00 isn't cut at midnight.
                    .bucketByTime((end - start).toInt(), TimeUnit.SECONDS)
                    .setTimeRange(start, end, TimeUnit.SECONDS)
                    .build()
                client.readData(request).await().buckets
                    .flatMap { it.dataSets }
                    .flatMap { it.dataPoints }
                    .sumOf { it.getValue(LocalField.FIELD_STEPS).asInt().toLong() }
            }
        }
    }

    /** Play services throws for a revoked permission or an outdated module; treat those as "no reading". */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Recording API call failed")
        null
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PhoneStepsModule {
    @Binds
    abstract fun bindPhoneStepsGateway(impl: RecordingApiStepsGateway): PhoneStepsGateway
}
