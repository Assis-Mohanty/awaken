package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.ai.AiPrefsRepository
import com.assismohanty.awaken.core.data.ai.OracleOutcome
import com.assismohanty.awaken.core.data.ai.OracleTrace
import com.assismohanty.awaken.core.data.ai.PathOracleClient
import com.assismohanty.awaken.core.data.ai.dto.ReportRequest
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.entity.PathWeekEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekStatus
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.ProgressionSignal
import javax.inject.Inject

/** What a Path request should do (ARCH §3.7 "PLAN_THEN_WEEK" for a new Path, "WEEK" every Monday). */
enum class PathRequestMode { PLAN_THEN_WEEK, PLAN, WEEK }

/**
 * One logical request. [requestId] is created when the work is first enqueued and reused by every retry, so the proxy
 * never charges quota twice for it (ARCH §3.7).
 */
data class PathJob(
    val pathId: String,
    val mode: PathRequestMode,
    val requestId: String,
    /** The week a [PathRequestMode.WEEK] job is for; null means the current ISO week. */
    val isoWeek: String? = null,
    val manual: Boolean = false,
)

sealed interface PathRequestResult {
    /** Finished — successfully, or given up on (the week is marked FAILED) — nothing more to do. */
    data object Done : PathRequestResult

    /** A transient failure: try again with backoff. */
    data object Retry : PathRequestResult
}

/**
 * Runs one Path request end to end: ask the Oracle, then hand its reply to the accept use cases, which validate, price
 * and schedule it (CLAUDE.md invariant 5). Used by `PlanWeekWorker`; kept free of WorkManager so it is unit-testable.
 */
class RunPathRequestUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val requests: PathRequests,
    private val oracle: PathOracleClient,
    private val acceptPlan: AcceptPlanUseCase,
    private val acceptWeek: AcceptWeekPlanUseCase,
    private val aiPrefs: AiPrefsRepository,
    private val trace: OracleTrace,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(job: PathJob, attempt: Int): PathRequestResult {
        if (!aiPrefs.hasConsent()) return PathRequestResult.Done
        val path = pathDao.get(job.pathId)?.takeIf { it.status != PathStatus.ARCHIVED } ?: return PathRequestResult.Done
        val profile = hunterDao.getProfile() ?: return PathRequestResult.Done
        val week = job.isoWeek ?: PathScheduler.isoWeek(clock.gameDate(profile.dayStartHour))

        if (job.mode != PathRequestMode.WEEK && (job.mode == PathRequestMode.PLAN || path.status == PathStatus.PLANNING)) {
            val request = requests.plan(path, job.requestId) ?: return PathRequestResult.Done
            when (val outcome = oracle.planPath(request)) {
                is OracleOutcome.Success -> {
                    trace.clearOffline(path.id)
                    // Consent revoked while the request was in flight: the reply is discarded unread (ARCH §3.7).
                    if (!aiPrefs.hasConsent()) return PathRequestResult.Done
                    when (acceptPlan(path.id, outcome.value)) {
                        is AcceptPlanResult.Accepted -> Unit
                        AcceptPlanResult.NotFound -> return PathRequestResult.Done
                        AcceptPlanResult.Invalid -> return failed(path.id, week, attempt, retryable = true)
                    }
                }
                else -> return failed(path.id, week, attempt, retryable = outcome.isRetryable(), offline = outcome == OracleOutcome.Offline)
            }
            if (job.mode == PathRequestMode.PLAN) return PathRequestResult.Done
        }
        return weekStep(job, week, attempt)
    }

    private suspend fun weekStep(job: PathJob, isoWeek: String, attempt: Int): PathRequestResult {
        val path = pathDao.get(job.pathId)?.takeIf { it.status == PathStatus.ACTIVE } ?: return PathRequestResult.Done
        val row = ensureWeekRow(path.id, isoWeek)
        if (row.status != PathWeekStatus.REQUESTED) return PathRequestResult.Done
        val request = requests.week(path, isoWeek, job.requestId) ?: return failed(path.id, isoWeek, attempt, retryable = false)
        return when (val outcome = oracle.weekQuests(request, path.id, job.manual)) {
            is OracleOutcome.Success -> {
                trace.clearOffline(path.id)
                if (!aiPrefs.hasConsent()) return PathRequestResult.Done
                acceptWeek(path.id, isoWeek, outcome.value)
                PathRequestResult.Done
            }
            else -> failed(path.id, isoWeek, attempt, retryable = outcome.isRetryable(), offline = outcome == OracleOutcome.Offline)
        }
    }

    private suspend fun ensureWeekRow(pathId: String, isoWeek: String): PathWeekEntity = transactions.run {
        pathDao.week(pathId, isoWeek) ?: PathWeekEntity(
            pathId = pathId,
            isoWeek = isoWeek,
            status = PathWeekStatus.REQUESTED,
            progressionSignal = ProgressionSignal.HOLD,
            promptVersion = "",
            responseSha256 = "",
            requestedAt = clock.now(),
        ).also { pathDao.insertWeekIfAbsent(it) }
    }

    /** Retry with backoff while attempts remain; otherwise record the failure so the Paths tab can offer [Retry]. */
    private suspend fun failed(pathId: String, isoWeek: String, attempt: Int, retryable: Boolean, offline: Boolean = false): PathRequestResult {
        if (offline) trace.markOffline(pathId) else trace.clearOffline(pathId)
        if (retryable && attempt + 1 < MAX_ATTEMPTS) return PathRequestResult.Retry
        transactions.run {
            val row = pathDao.week(pathId, isoWeek)
            if (row != null && row.status == PathWeekStatus.REQUESTED) pathDao.setWeekStatus(pathId, isoWeek, PathWeekStatus.FAILED)
        }
        return PathRequestResult.Done
    }

    private fun OracleOutcome<*>.isRetryable(): Boolean = when (this) {
        OracleOutcome.Offline, is OracleOutcome.RateLimited, OracleOutcome.ModelInvalidOutput, OracleOutcome.ServerError,
        OracleOutcome.IntegrityFailed,
        -> true
        // A spent budget, a rejected request or a missing endpoint will not change by waiting.
        OracleOutcome.BudgetExhausted, OracleOutcome.InvalidRequest, OracleOutcome.Unavailable, is OracleOutcome.Success -> false
    }

    companion object {
        /** ARCH §3.7: exponential backoff, at most 5 attempts. */
        const val MAX_ATTEMPTS = 5
    }
}

/** Sends the report outbox to `/v1/reports` (PRD F21). Reports carry no install ID of their own. */
class SendReportsUseCase @Inject constructor(
    private val pathDao: PathDao,
    private val oracle: PathOracleClient,
    private val clock: Clock,
) {
    suspend operator fun invoke(): PathRequestResult {
        for (report in pathDao.unsentReports()) {
            val outcome = oracle.report(ReportRequest(report.promptVersion, report.reason, report.title, report.description))
            when (outcome) {
                is OracleOutcome.Success -> pathDao.markReportSent(report.id, clock.now())
                OracleOutcome.Offline, is OracleOutcome.RateLimited, OracleOutcome.ServerError,
                OracleOutcome.IntegrityFailed, OracleOutcome.ModelInvalidOutput,
                -> return PathRequestResult.Retry
                // No consent/endpoint, or the proxy refuses it: keep it in the outbox and stop for now.
                OracleOutcome.BudgetExhausted, OracleOutcome.InvalidRequest, OracleOutcome.Unavailable -> return PathRequestResult.Done
            }
        }
        return PathRequestResult.Done
    }
}
