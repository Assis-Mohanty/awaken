package com.assismohanty.awaken.debug

import com.assismohanty.awaken.core.data.ai.KtorPathOracleClient
import com.assismohanty.awaken.core.data.ai.OracleOutcome
import com.assismohanty.awaken.core.data.ai.OracleReply
import com.assismohanty.awaken.core.data.ai.PathOracleClient
import com.assismohanty.awaken.core.data.ai.dto.ReportRequest
import com.assismohanty.awaken.engine.path.PathPlanRequest
import com.assismohanty.awaken.engine.path.PathWeekRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Debug builds: the real client unless "Use FakePathOracle" is on (default when no staging URL is configured). */
@Singleton
class SwitchingPathOracle @Inject constructor(
    private val real: KtorPathOracleClient,
    private val settings: DebugSettings,
) : PathOracleClient {
    private val fake = FakePathOracle(latencyMs = FAKE_LATENCY_MS)
    private val active: PathOracleClient get() = if (settings.useFakeOracle.value) fake else real

    override suspend fun planPath(request: PathPlanRequest): OracleOutcome<OracleReply> = active.planPath(request)

    override suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean): OracleOutcome<OracleReply> =
        active.weekQuests(request, pathId, manualRetry)

    override suspend fun report(report: ReportRequest): OracleOutcome<Unit> = active.report(report)

    private companion object {
        const val FAKE_LATENCY_MS = 800L
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DebugOracleModule {
    @Provides
    @Singleton
    fun providePathOracleClient(impl: SwitchingPathOracle): PathOracleClient = impl
}
