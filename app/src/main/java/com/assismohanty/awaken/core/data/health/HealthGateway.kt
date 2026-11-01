package com.assismohanty.awaken.core.data.health

import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthWindow
import com.assismohanty.awaken.engine.model.Verification

/** Whether the Health Connect app/module can be used on this device. */
enum class HealthStatus { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

/** A read that couldn't happen: permission revoked, app not in the foreground, provider gone. Never a crash. */
class HealthReadException(cause: Throwable) : Exception(cause)

/**
 * Read-only access to Health Connect (ARCH §3.4): Steps, ExerciseSession and SleepSession (core), plus Distance, Active
 * calories and Heart rate (optional, ENGINE_DECISIONS #83). All network-free; Health Connect is an on-device provider.
 */
interface HealthGateway {
    suspend fun status(): HealthStatus

    /** The core permissions the hunter must grant for verified quests, as Health Connect permission strings. */
    val requiredPermissions: Set<String>

    val distancePermission: String
    val caloriesPermission: String
    val heartRatePermission: String

    /** Extra metrics: each one is requested together with the core set but only ever turns off its own feature. */
    val optionalPermissions: Set<String> get() = setOf(distancePermission, caloriesPermission, heartRatePermission)

    /** Everything the permission dialog asks for. */
    val allPermissions: Set<String> get() = requiredPermissions + optionalPermissions

    suspend fun grantedPermissions(): Set<String>

    /** HEALTH verifications that need an optional permission the hunter has not granted (ENGINE_DECISIONS #83). */
    suspend fun unavailableVerifications(): Set<Verification> {
        val granted = grantedPermissions()
        return buildSet {
            if (distancePermission !in granted) add(Verification.HEALTH_DISTANCE)
            if (caloriesPermission !in granted) add(Verification.HEALTH_CALORIES)
        }
    }

    /** Best effort: Health Connect may refuse when unavailable. */
    suspend fun revokeAll()

    @Throws(HealthReadException::class)
    suspend fun readDay(window: HealthWindow): HealthDay

    /** True only for debug fixture data ("Simulate 9,000 steps"); real Health Connect is never labelled fake. */
    val isSimulated: Boolean get() = false

    /** At least one app wrote non-manual steps in [window]. False means "no step source", never "0 steps". */
    suspend fun hasStepData(window: HealthWindow): Boolean

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        const val PLAY_STORE_URI = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
        const val PLAY_STORE_WEB_URI = "https://play.google.com/store/apps/details?id=$PROVIDER_PACKAGE"
    }
}
