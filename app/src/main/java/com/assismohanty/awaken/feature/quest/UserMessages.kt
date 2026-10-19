package com.assismohanty.awaken.feature.quest

import android.content.res.Resources
import android.text.format.DateUtils
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.export.ImportProblem
import com.assismohanty.awaken.core.designsystem.components.shopItemNameRes
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.GateRules
import java.text.NumberFormat
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

private fun shopItemName(itemId: String, resources: Resources): String =
    shopItemNameRes(itemId)?.let(resources::getString) ?: itemId

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
    UserMessage.UndoHealth -> resources.getString(R.string.msg_undo_health)
    UserMessage.GateTooLate -> resources.getString(R.string.msg_gate_too_late)
    UserMessage.TrialStarted -> resources.getString(R.string.msg_trial_started)
    UserMessage.TrialNoStages -> resources.getString(R.string.msg_trial_no_stages)
    is UserMessage.Rerolled ->
        if (cost == 0) resources.getString(R.string.msg_reroll_free) else resources.getString(R.string.msg_reroll_paid, cost)
    UserMessage.RerollLimit -> resources.getString(R.string.msg_reroll_limit)
    is UserMessage.RerollGold -> resources.getString(R.string.msg_reroll_gold, cost)
    UserMessage.RerollNone -> resources.getString(R.string.msg_reroll_none)
    UserMessage.ShareFailed -> resources.getString(R.string.msg_share_failed)
    is UserMessage.VaultBought -> resources.getString(R.string.msg_vault_bought, shopItemName(itemId, resources))
    is UserMessage.VaultEquipped -> resources.getString(R.string.msg_vault_equipped, shopItemName(itemId, resources))
    UserMessage.VaultDuplicate -> resources.getString(R.string.msg_vault_duplicate)
    is UserMessage.VaultRejected -> when (val reason = check) {
        is BuyCheck.InsufficientGold -> resources.getString(R.string.vault_missing_gold, NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(reason.missing))
        BuyCheck.ShieldLimit -> resources.getString(R.string.vault_shield_limit)
        is BuyCheck.RankRequired -> resources.getString(R.string.vault_requires_rank, reason.rank.name)
        BuyCheck.AlreadyOwned, BuyCheck.Buyable -> resources.getString(R.string.msg_vault_already_owned)
    }
    UserMessage.VaultNoFragments -> resources.getString(R.string.msg_vault_no_fragments)
    UserMessage.VaultPoolEmpty -> resources.getString(R.string.msg_vault_pool_empty)
    UserMessage.DayStartBlocked -> resources.getString(R.string.msg_day_start_blocked)
    UserMessage.ExportDone -> resources.getString(R.string.msg_export_done)
    UserMessage.ExportFailed -> resources.getString(R.string.msg_export_failed)
    UserMessage.ImportUnreadable -> resources.getString(R.string.msg_import_unreadable)
    is UserMessage.ImportRejected -> resources.getString(problem.messageRes())
    UserMessage.SettingsSaved -> resources.getString(R.string.msg_settings_saved)
    UserMessage.HealthConnected -> resources.getString(R.string.msg_health_connected)
    UserMessage.HealthDeclined -> resources.getString(R.string.health_declined)
    UserMessage.HealthNotInstalled -> resources.getString(R.string.health_not_installed)
    UserMessage.Error -> resources.getString(R.string.msg_error)
}

private fun ImportProblem.messageRes(): Int = when (this) {
    ImportProblem.NotAwakenFile, ImportProblem.Malformed -> R.string.msg_import_not_backup
    is ImportProblem.NewerVersion -> R.string.msg_import_newer
    is ImportProblem.OlderVersion -> R.string.msg_import_older
    is ImportProblem.MissingTable, ImportProblem.NoHunter, is ImportProblem.InvalidLedgerRow, is ImportProblem.InvalidRow ->
        R.string.msg_import_damaged
}
