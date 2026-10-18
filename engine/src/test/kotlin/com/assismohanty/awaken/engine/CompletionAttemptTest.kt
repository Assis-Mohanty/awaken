package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.LedgerEntry
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Undo → re-complete (ENGINE_DECISIONS #17): one completionId per attempt, crit seeded by the questInstanceId. */
class CompletionAttemptTest {

    private val context = HunterContext(hunterId = "hunter-1", focusStats = listOf(Stat.STR, Stat.INT))
    private val critQuestId = firstId { CritRoller.isCrit(it) }
    private val plainQuestId = firstId { !CritRoller.isCrit(it) }

    private fun firstId(predicate: (String) -> Boolean) =
        generateSequence(0) { it + 1 }.map { DirectiveGenerator.instanceId("hunter-1", java.time.LocalDate.of(2026, 10, 18), "side$it") }
            .first(predicate)

    private fun quest(id: String) = CompletableQuest(id, QuestType.SIDE, Stat.STR, Stat.WIL, Difficulty.NORMAL, VerificationSource.SELF)

    /** Today's SELF XP as the app derives it: net XP_TOTAL over the ledger (undo rows subtract). */
    private fun todaySelfXp(ledger: List<LedgerEntry>) = LedgerMath.balances(ledger).xpTotal.toInt()

    private fun complete(ledger: List<LedgerEntry>, questId: String, attempt: Int): List<LedgerEntry> {
        val reward = XpCalculator.forCompletion(quest(questId), context.copy(todaySelfXp = todaySelfXp(ledger)))
        val rows = LedgerMath.rowsFor(reward, SourceType.QUEST, LedgerMath.completionId(questId, attempt))
        return LedgerMath.append(ledger, rows).first
    }

    private fun undo(ledger: List<LedgerEntry>, questId: String, attempt: Int): List<LedgerEntry> {
        val completionId = LedgerMath.completionId(questId, attempt)
        val reversals = ledger.filter { it.sourceType == SourceType.QUEST && it.sourceId == completionId }.map(LedgerMath::reversal)
        return LedgerMath.append(ledger, reversals).first
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `complete then undo then complete gives exactly one net reward`(crit: Boolean) {
        val questId = if (crit) critQuestId else plainQuestId
        val single = LedgerMath.balances(complete(emptyList(), questId, attempt = 1))

        val ledger = complete(undo(complete(emptyList(), questId, attempt = 1), questId, attempt = 1), questId, attempt = 2)

        assertThat(LedgerMath.balances(ledger)).isEqualTo(single)
        assertThat(single.xpTotal).isGreaterThan(0)
        assertThat(ledger.filter { it.kind == LedgerKind.XP_TOTAL }.map { it.sourceId })
            .containsExactly("$questId#1", "$questId#1", "$questId#2")
    }

    @Test
    fun `crit result is identical across attempts`() {
        for (questId in listOf(critQuestId, plainQuestId)) {
            val first = XpCalculator.forCompletion(quest(questId), context)
            val second = XpCalculator.forCompletion(quest(questId), context)
            assertThat(second.crit).isEqualTo(first.crit)
            assertThat(second.xp).isEqualTo(first.xp)
        }
        assertThat(XpCalculator.forCompletion(quest(critQuestId), context).crit).isTrue()
    }

    @Test
    fun `undoing the same completion twice is a no-op`() {
        val once = undo(complete(emptyList(), plainQuestId, 1), plainQuestId, 1)
        val twice = undo(once, plainQuestId, 1)
        assertThat(twice).isEqualTo(once)
        assertThat(LedgerMath.balances(twice).xpTotal).isEqualTo(0)
    }

    @Test
    fun `re-completion still goes through the daily cap`() {
        // 540 SELF XP already earned by other quests; a 60 XP completion exactly reaches the cap.
        val earlier = LedgerEntry(LedgerKind.XP_TOTAL, 540, SourceType.QUEST, "other#1")
        val afterUndo = undo(complete(listOf(earlier), plainQuestId, 1), plainQuestId, 1)
        assertThat(todaySelfXp(afterUndo)).isEqualTo(540)

        val recompleted = complete(afterUndo, plainQuestId, 2)
        assertThat(LedgerMath.balances(recompleted).xpTotal).isEqualTo(600)

        // Had the undone XP not been netted out, the re-completion would have been capped.
        val capped = XpCalculator.forCompletion(quest(plainQuestId), context.copy(todaySelfXp = 600))
        assertThat(capped.cappedSource).isEqualTo(VerificationSource.SELF)
        assertThat(capped.xp).isEqualTo(15)
    }

    @Test
    fun `completion ids are per attempt and start at 1`() {
        assertThat(LedgerMath.completionId("q", 1)).isEqualTo("q#1")
        assertThat(LedgerMath.completionId("q", 2)).isEqualTo("q#2")
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> { LedgerMath.completionId("q", 0) }
    }
}
