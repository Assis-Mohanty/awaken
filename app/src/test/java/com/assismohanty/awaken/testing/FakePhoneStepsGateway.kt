package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.data.health.PhoneStepsGateway
import com.assismohanty.awaken.engine.HealthWindow

/** The phone's own step counter (Recording API) as a test sets it: the same count for every day asked about. */
class FakePhoneStepsGateway(
    var supported: Boolean = true,
    var granted: Boolean = false,
    var stepsPerDay: Long = 0,
    var failReads: Boolean = false,
) : PhoneStepsGateway {
    override val permission: String = "android.permission.ACTIVITY_RECOGNITION"
    var subscribed = false
        private set
    var reads = 0
        private set

    override fun isSupported(): Boolean = supported

    override fun isPermissionGranted(): Boolean = granted

    override suspend fun subscribe(): Boolean {
        subscribed = supported && granted
        return subscribed
    }

    override suspend fun dailySteps(windows: List<HealthWindow>): List<Long>? {
        reads++
        return if (failReads) null else windows.map { stepsPerDay }
    }
}
