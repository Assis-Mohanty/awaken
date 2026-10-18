package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.repo.Notice
import com.assismohanty.awaken.core.data.repo.NoticeStore
import com.assismohanty.awaken.engine.DayOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one entry point for the day rollover (app start, foreground, day-start tick and DayRolloverWorker). It turns what
 * the run did into notices, so a rollover done by the worker at 04:00 is still shown when the hunter next opens the app.
 * Runs are serialized in-process; the rollover itself is also idempotent across processes.
 */
@Singleton
class RunRolloverUseCase @Inject constructor(
    private val rollover: DayRolloverUseCase,
    private val notices: NoticeStore,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(): RolloverOutcome? = mutex.withLock {
        val outcome = rollover() ?: return@withLock null
        notices.add(outcome.toNotices())
        outcome
    }
}

internal fun RolloverOutcome.toNotices(): List<Notice> = buildList {
    if (penaltyIssued) add(Notice.PenaltyWarning(today.toString()))
    // "A shield absorbed yesterday's miss": one notice for the most recent shielded day.
    finalized.lastOrNull { it.outcome == DayOutcome.SHIELDED }?.let { add(Notice.ShieldUsed(it.gameDate.toString())) }
    if (recalibration.isNotEmpty()) add(Notice.Recalibrated(recalibration))
    trialFailed?.let { add(Notice.TrialFailed(it)) }
}
