package com.assismohanty.awaken.feature.quest

import android.content.res.Resources
import android.text.format.DateUtils
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.GateRules
import java.time.Duration

/** Why a Focus Gate collapsed, e.g. "Background time 1:42 exceeded the 1:30 limit." (DESIGN_SYSTEM §4.4). */
fun collapseDetail(reason: GateCollapseReason, backgroundMs: Long, resources: Resources): String = when (reason) {
    GateCollapseReason.BREACH -> resources.getString(
        R.string.gate_collapsed_breach,
        shortClock(backgroundMs),
        shortClock(GateRules.BREACH_BUDGET_MS),
    )
    GateCollapseReason.DEVICE_RESTARTED -> resources.getString(R.string.gate_collapsed_restarted)
    GateCollapseReason.DAY_RESET -> resources.getString(R.string.gate_collapsed_day_reset)
}

/** "m:ss" with no leading zero on the minutes: 1:42, 0:30. */
fun shortClock(ms: Long): String {
    val seconds = Duration.ofMillis(ms).seconds.coerceAtLeast(0)
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private const val SECONDS_PER_MINUTE = 60L

/** System-voice text for a domain message (DESIGN_SYSTEM §5): what happened, and what to do. */
fun UserMessage.text(resources: Resources): String = when (this) {
    is UserMessage.TooSoon -> resources.getString(R.string.msg_too_soon, DateUtils.formatElapsedTime(remainingSeconds))
    UserMessage.VerificationRequired -> resources.getString(R.string.msg_verification_required)
    UserMessage.WrongDay -> resources.getString(R.string.msg_wrong_day)
    UserMessage.AlreadyDone -> resources.getString(R.string.msg_already_done)
    UserMessage.QuestCleared -> resources.getString(R.string.msg_quest_cleared)
    UserMessage.Undone -> resources.getString(R.string.msg_undone)
    UserMessage.UndoExpired -> resources.getString(R.string.msg_undo_expired)
    UserMessage.UndoDayClosed -> resources.getString(R.string.msg_undo_day_closed)
    UserMessage.UndoPromotion -> resources.getString(R.string.msg_undo_promotion)
    UserMessage.UndoGate -> resources.getString(R.string.msg_undo_gate)
    UserMessage.GateTooLate -> resources.getString(R.string.msg_gate_too_late)
    UserMessage.TrialStarted -> resources.getString(R.string.msg_trial_started)
    UserMessage.TrialNoStages -> resources.getString(R.string.msg_trial_no_stages)
    is UserMessage.Rerolled ->
        if (cost == 0) resources.getString(R.string.msg_reroll_free) else resources.getString(R.string.msg_reroll_paid, cost)
    UserMessage.RerollLimit -> resources.getString(R.string.msg_reroll_limit)
    is UserMessage.RerollGold -> resources.getString(R.string.msg_reroll_gold, cost)
    UserMessage.RerollNone -> resources.getString(R.string.msg_reroll_none)
    UserMessage.Error -> resources.getString(R.string.msg_error)
}
