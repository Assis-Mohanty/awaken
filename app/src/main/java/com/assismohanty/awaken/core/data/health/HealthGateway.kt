package com.assismohanty.awaken.core.data.health

import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthWindow

/** Whether the Health Connect app/module can be used on this device. */
enum class HealthStatus { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

/** A read that couldn't happen: permission revoked, app not in the foreground, provider gone. Never a crash. */
class HealthReadException(cause: Throwable) : Exception(cause)

/**
 * Read-only access to Health Connect (ARCH §3.4): Steps, ExerciseSession, SleepSession and nothing else. All network-free;
 * Health Connect is an on-device provider.
 */
interface HealthGateway {
    suspend fun status(): HealthStatus

    /** The permissions the hunter must grant, as Health Connect permission strings. */
    val requiredPermissions: Set<String>

    suspend fun grantedPermissions(): Set<String>

    /** Best effort: Health Connect may refuse when unavailable. */
    suspend fun revokeAll()

    @Throws(HealthReadException::class)
    suspend fun readDay(window: HealthWindow): HealthDay

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        const val PLAY_STORE_URI = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
        const val PLAY_STORE_WEB_URI = "https://play.google.com/store/apps/details?id=$PROVIDER_PACKAGE"
    }
}
