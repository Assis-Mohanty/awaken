package com.assismohanty.awaken.core.data.ai

import com.assismohanty.awaken.core.data.ai.dto.ReportRequest
import com.assismohanty.awaken.engine.path.PathPlanRequest
import com.assismohanty.awaken.engine.path.PathWeekRequest

/**
 * The only way the app reaches an AI (CLAUDE.md invariant 5): the stateless Path Oracle proxy. Implementations return
 * the raw JSON; `PathValidator` in `:engine` decides what is usable before anything touches Room.
 */
interface PathOracleClient {
    suspend fun planPath(request: PathPlanRequest): OracleOutcome<OracleReply>

    /** [pathId] keys the proxy's per-Path weekly limits; [manualRetry] marks a user-triggered retry (GD §6.6.7). */
    suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean): OracleOutcome<OracleReply>

    suspend fun report(report: ReportRequest): OracleOutcome<Unit>
}
