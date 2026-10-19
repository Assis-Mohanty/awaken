package com.assismohanty.awaken.debug

import com.assismohanty.awaken.core.data.health.HealthConnectGateway
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthWindow
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug builds: the real gateway, unless "Simulate 9,000 steps" is on. Then Health Connect looks installed and fully
 * granted and reports 9,000 steps for today, so verified quests can be tested on emulators without Health Connect.
 */
@Singleton
class DebugHealthGateway @Inject constructor(
    private val real: HealthConnectGateway,
    private val settings: DebugSettings,
) : HealthGateway {
    private val simulating: Boolean get() = settings.simulateSteps.value

    override val requiredPermissions: Set<String> get() = real.requiredPermissions

    override suspend fun status(): HealthStatus = if (simulating) HealthStatus.AVAILABLE else real.status()

    override suspend fun grantedPermissions(): Set<String> = if (simulating) requiredPermissions else real.grantedPermissions()

    override suspend fun revokeAll() {
        if (!simulating) real.revokeAll()
    }

    override suspend fun readDay(window: HealthWindow): HealthDay =
        if (simulating) HealthDay(steps = SIMULATED_STEPS) else real.readDay(window)

    companion object {
        const val SIMULATED_STEPS = 9_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugHealthModule {
    @Binds
    abstract fun bindHealthGateway(impl: DebugHealthGateway): HealthGateway
}
