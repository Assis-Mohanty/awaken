package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthReadException
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthWindow

/** In-memory Health Connect: tests set what it reports and see what was asked. */
class FakeHealthGateway(
    var status: HealthStatus = HealthStatus.AVAILABLE,
    var day: HealthDay = HealthDay(),
    var failReads: Boolean = false,
    /** Mirrors the debug "simulate steps" switch: the data is fixture data. */
    var simulated: Boolean = false,
    /** False = no app has written any (non-manual) steps to Health Connect. */
    var stepData: Boolean = true,
) : HealthGateway {
    override val isSimulated: Boolean get() = simulated

    override suspend fun hasStepData(window: HealthWindow): Boolean = stepData

    override val requiredPermissions: Set<String> = setOf("read.steps", "read.exercise", "read.sleep")
    override val distancePermission: String = "read.distance"
    override val caloriesPermission: String = "read.calories"
    override val heartRatePermission: String = "read.heart_rate"
    var granted: Set<String> = allPermissions
    var reads = 0
        private set
    var revoked = false
        private set
    var lastWindow: HealthWindow? = null
        private set

    override suspend fun status(): HealthStatus = status

    override suspend fun grantedPermissions(): Set<String> = if (status == HealthStatus.AVAILABLE) granted else emptySet()

    override suspend fun revokeAll() {
        revoked = true
        granted = emptySet()
    }

    override suspend fun readDay(window: HealthWindow): HealthDay {
        reads++
        lastWindow = window
        if (failReads) throw HealthReadException(SecurityException("not in foreground"))
        return day
    }
}
