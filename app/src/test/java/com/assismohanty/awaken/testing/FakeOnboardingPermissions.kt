package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.feature.onboarding.OnboardingPermissions

/** What the phone reports to the onboarding permission screens; a test sets it directly. */
class FakeOnboardingPermissions(
    var status: HealthStatus = HealthStatus.AVAILABLE,
    var health: Boolean = false,
    var usage: Boolean = false,
    var notifications: Boolean = false,
    var askable: Boolean = true,
) : OnboardingPermissions {
    override val healthRequired: Set<String> = setOf("read.steps", "read.exercise", "read.sleep")
    override val healthPermissions: Set<String> = healthRequired + setOf("read.distance", "read.calories")

    override suspend fun healthStatus(): HealthStatus = status

    override suspend fun healthGranted(): Boolean = health

    override fun usageGranted(): Boolean = usage

    override fun notificationsGranted(): Boolean = notifications

    override fun notificationsAskable(): Boolean = askable
}
