package com.assismohanty.awaken.core.data.ai

import com.assismohanty.awaken.core.data.ai.dto.ReportRequest
import com.assismohanty.awaken.engine.path.PathPlanRequest
import com.assismohanty.awaken.engine.path.PathWeekRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release: AI Paths is compiled out (no network client, no INTERNET permission). Every Path entry point is hidden, so
 * nothing calls this; if something does by mistake, it fails loudly instead of quietly doing nothing.
 */
class DisabledPathOracleClient : PathOracleClient {
    override suspend fun planPath(request: PathPlanRequest): OracleOutcome<OracleReply> = disabled()

    override suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean): OracleOutcome<OracleReply> = disabled()

    override suspend fun report(report: ReportRequest): OracleOutcome<Unit> = disabled()

    private fun disabled(): Nothing = throw IllegalStateException("AI Paths is not part of this build")
}

@Module
@InstallIn(SingletonComponent::class)
object OracleModule {
    @Provides
    @Singleton
    fun providePathOracleClient(): PathOracleClient = DisabledPathOracleClient()
}
