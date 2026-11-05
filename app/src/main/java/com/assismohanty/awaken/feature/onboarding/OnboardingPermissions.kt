package com.assismohanty.awaken.feature.onboarding

import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.work.NotificationAccess
import javax.inject.Inject

/**
 * What the permission screens need to know about the phone, in one place so the ViewModel can be tested with a fake. Every
 * read is a check, never a request: the system's own dialogs are launched by the screen.
 */
interface OnboardingPermissions {
    suspend fun healthStatus(): HealthStatus

    /** Everything the Health Connect sheet asks for, and the part without which verified quests stay off. */
    val healthPermissions: Set<String>
    val healthRequired: Set<String>

    suspend fun healthGranted(): Boolean

    fun usageGranted(): Boolean

    fun notificationsGranted(): Boolean

    /** False below Android 13 (nothing to ask) and once granted. */
    fun notificationsAskable(): Boolean
}

class AndroidOnboardingPermissions @Inject constructor(
    private val health: HealthGateway,
    private val usage: UsageGateway,
    private val notifications: NotificationAccess,
) : OnboardingPermissions {
    override suspend fun healthStatus(): HealthStatus = health.status()

    override val healthPermissions: Set<String> get() = health.allPermissions
    override val healthRequired: Set<String> get() = health.requiredPermissions

    override suspend fun healthGranted(): Boolean =
        health.status() == HealthStatus.AVAILABLE && health.grantedPermissions().containsAll(health.requiredPermissions)

    override fun usageGranted(): Boolean = usage.hasAccess()

    override fun notificationsGranted(): Boolean = notifications.isGranted()

    override fun notificationsAskable(): Boolean = notifications.canAsk()
}
