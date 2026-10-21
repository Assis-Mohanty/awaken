package com.assismohanty.awaken.feature.paths

import androidx.compose.runtime.Immutable
import com.assismohanty.awaken.core.data.db.entity.PathSlotEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekStatus
import com.assismohanty.awaken.core.designsystem.components.LadderNode
import com.assismohanty.awaken.core.designsystem.components.OracleUiState
import com.assismohanty.awaken.core.domain.PathSummary
import com.assismohanty.awaken.core.domain.RetryPathUseCase
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.ProgressionSignal
import com.assismohanty.awaken.engine.path.SlotOutcome
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One of this week's quests as a Path window lists it (DS §4.8: "this week's quests with outcomes"). */
@Immutable
data class PathQuestLineUi(
    val slotId: String,
    val title: String,
    val outcome: SlotOutcome,
    /** Short weekday of the day it is (or was) offered, e.g. "Wed". */
    val dayLabel: String,
    val gateRank: GateRank?,
    val carried: Boolean,
)

@Immutable
data class PathCardUi(
    val id: String,
    val goal: String,
    val primaryStat: Stat,
    val secondaryStat: Stat?,
    val status: PathStatus,
    val ladder: List<LadderNode>,
    val currentIndex: Int,
    val weekDone: Int,
    val weekTotal: Int,
    val quests: List<PathQuestLineUi>,
    /** Null for a paused Path: the Oracle is not being asked for anything. */
    val oracle: OracleUiState?,
    val canRetry: Boolean,
    /** The retry limit (2 manual per week) is spent. */
    val retryLimitReached: Boolean,
    /** How the engine judged last week; shown once this week's quests are charted. */
    val recalibration: ProgressionSignal?,
    /** Title of the next milestone when the model suggested advancing and the engine agrees. */
    val advanceTo: String?,
)

/** Maps the observed rows to what the Paths tab and Path detail draw. */
fun PathSummary.toCard(consentGiven: Boolean, locale: Locale = Locale.getDefault()): PathCardUi {
    val week = week
    val oracle = oracleState(consentGiven)
    val failed = oracle == OracleUiState.FAILED
    val retriesUsed = week?.manualRetries ?: 0
    val shown = weekSlots.filter { it.assignedGameDate != null && it.outcome != SlotOutcome.REROLLED && it.outcome != SlotOutcome.REPORTED }
    return PathCardUi(
        id = path.id,
        goal = path.goal,
        primaryStat = path.primaryStat,
        secondaryStat = path.secondaryStat,
        status = path.status,
        ladder = milestones.map { LadderNode(it.title, it.summary, it.exitCriterion) },
        currentIndex = path.currentMilestoneIndex.coerceIn(0, (milestones.size - 1).coerceAtLeast(0)),
        weekDone = shown.count { it.outcome == SlotOutcome.COMPLETED },
        weekTotal = shown.size,
        quests = shown.sortedWith(compareBy({ it.assignedGameDate }, { it.blueprintIndex })).map { it.toLine(locale) },
        oracle = oracle,
        canRetry = failed && consentGiven && (path.status == PathStatus.PLANNING || retriesUsed < RetryPathUseCase.MAX_MANUAL_RETRIES),
        retryLimitReached = failed && consentGiven && path.status == PathStatus.ACTIVE && retriesUsed >= RetryPathUseCase.MAX_MANUAL_RETRIES,
        recalibration = week?.takeIf { it.status == PathWeekStatus.READY }?.progressionSignal,
        advanceTo = advance?.next?.title,
    )
}

private fun PathSummary.oracleState(consentGiven: Boolean): OracleUiState? {
    if (path.status == PathStatus.PAUSED || path.status == PathStatus.ARCHIVED) return null
    return when (week?.status) {
        PathWeekStatus.READY -> OracleUiState.CHARTED
        PathWeekStatus.EMPTY, PathWeekStatus.FAILED -> if (consentGiven) OracleUiState.FAILED else OracleUiState.CONSENT_OFF
        PathWeekStatus.REQUESTED -> if (offline) OracleUiState.OFFLINE else OracleUiState.CHARTING
        null -> if (path.status == PathStatus.PLANNING) OracleUiState.CHARTING else null
    }
}

private fun PathSlotEntity.toLine(locale: Locale) = PathQuestLineUi(
    slotId = id,
    title = title,
    outcome = outcome,
    dayLabel = assignedGameDate?.dayLabel(locale).orEmpty(),
    gateRank = gateRank,
    carried = carriedOver,
)

private fun LocalDate.dayLabel(locale: Locale): String = dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
