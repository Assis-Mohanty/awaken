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
) : HealthGateway {
    override val requiredPermissions: Set<String> = setOf("read.steps", "read.exercise", "read.sleep")
    var granted: Set<String> = requiredPermissions
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
