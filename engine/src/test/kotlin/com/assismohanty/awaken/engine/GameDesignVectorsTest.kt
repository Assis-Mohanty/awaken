package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** GAME_DESIGN §12 — one named test per row. These are the cross-implementation contract. */
class GameDesignVectorsTest {

    private val context = HunterContext(hunterId = "hunter-1", focusStats = listOf(Stat.STR, Stat.INT))

    @Test
    fun `vector 01 - NORMAL streak 0 SELF no crit = 60`() {
        assertThat(XpCalculator.questXp(Difficulty.NORMAL, 0, VerificationSource.SELF, crit = false)).isEqualTo(60)
    }

    @Test
    fun `vector 02 - HARD streak 12 HEALTH no crit = 140`() {
        assertThat(XpCalculator.questXp(Difficulty.HARD, 12, VerificationSource.HEALTH, crit = false)).isEqualTo(140)
    }

    @Test
    fun `vector 03 - EASY streak 45 capped to 30 TIMER crit = 138`() {
        assertThat(XpCalculator.questXp(Difficulty.EASY, 45, VerificationSource.TIMER, crit = true)).isEqualTo(138)
    }

    @Test
    fun `vector 04 - EXTREME streak 30 HEALTH crit = 468`() {
        assertThat(XpCalculator.questXp(Difficulty.EXTREME, 30, VerificationSource.HEALTH, crit = true)).isEqualTo(468)
    }

    @Test
    fun `vector 05 - gold for vector 2 = 70`() {
        assertThat(XpCalculator.goldFor(140)).isEqualTo(70)
    }

    @Test
    fun `vector 06 - xpToNext(1) = 112`() {
        assertThat(LevelCurve.xpToNext(1)).isEqualTo(112)
    }

    @Test
    fun `vector 07 - xpToNext(10) = 578`() {
        assertThat(LevelCurve.xpToNext(10)).isEqualTo(578)
    }

    @Test
    fun `vector 08 - level(0, 111, 112, 2505) = 1, 1, 2, 10`() {
        assertThat(LevelCurve.level(0)).isEqualTo(1)
        assertThat(LevelCurve.level(111)).isEqualTo(1)
        assertThat(LevelCurve.level(112)).isEqualTo(2)
        assertThat(LevelCurve.level(2505)).isEqualTo(10)
    }

    @Test
    fun `vector 09 - statLevel(0, 160, 3240) = 1, 3, 10`() {
        assertThat(StatLevel.level(0)).isEqualTo(1)
        assertThat(StatLevel.level(160)).isEqualTo(3)
        assertThat(StatLevel.level(3240)).isEqualTo(10)
    }

    @Test
    fun `vector 10 - self XP 580 plus 60 crosses cap so whole quest reduced to 15 XP and 0 gold`() {
        val quest = nonCritQuest(Difficulty.NORMAL, VerificationSource.SELF)
        val reward = XpCalculator.forCompletion(quest, context.copy(todaySelfXp = 580))

        assertThat(reward.xp).isEqualTo(15)
        assertThat(reward.gold).isEqualTo(0)
        assertThat(reward.cappedSource).isEqualTo(VerificationSource.SELF)
    }

    @Test
    fun `vector 11 - same ledger event applied twice leaves balance unchanged`() {
        val reward = XpCalculator.forCompletion(nonCritQuest(Difficulty.NORMAL, VerificationSource.SELF), context)
        val rows = LedgerMath.rowsFor(reward, SourceType.QUEST, "q-1")

        val (once, insertedFirst) = LedgerMath.append(emptyList(), rows)
        val (twice, insertedSecond) = LedgerMath.append(once, rows)

        assertThat(insertedFirst).hasSize(rows.size)
        assertThat(insertedSecond).isEmpty()
        assertThat(LedgerMath.balances(twice)).isEqualTo(LedgerMath.balances(once))
        assertThat(LedgerMath.balances(once).xpTotal).isEqualTo(60)
    }

    @Test
    fun `vector 12 - completion then undo nets XP gold and stat XP to zero with both rows kept`() {
        val quest = nonCritQuest(Difficulty.NORMAL, VerificationSource.SELF).copy(secondaryStat = Stat.WIL)
        val completionId = LedgerMath.completionId(quest.questInstanceId, attempt = 1)
        val rows = LedgerMath.rowsFor(XpCalculator.forCompletion(quest, context), SourceType.QUEST, completionId)
        val (completed, _) = LedgerMath.append(emptyList(), rows)
        val reversals = completed.map(LedgerMath::reversal)
        val (undone, inserted) = LedgerMath.append(completed, reversals)

        assertThat(inserted).hasSize(rows.size)
        assertThat(undone).hasSize(rows.size * 2)
        assertThat(undone.filter { it.kind == LedgerKind.XP_TOTAL }).hasSize(2)
        val balances = LedgerMath.balances(undone)
        assertThat(balances.xpTotal).isEqualTo(0)
        assertThat(balances.gold).isEqualTo(0)
        Stat.entries.forEach { assertThat(balances.statXp(it)).isEqualTo(0) }
    }

    @Test
    fun `vector 13 - main incomplete but 3 sides done is cleared`() {
        val day = DayRecord(LocalDate.of(2026, 10, 18), mainCompleted = false, itemsCompleted = 3)
        assertThat(StreakRules.isCleared(day)).isTrue()
    }

    @Test
    fun `vector 14 - uncleared day with 1 shield keeps streak, uses shield, no penalty`() {
        val (state, result) = StreakRules.finalizeDay(
            StreakState(streak = 6, bestStreak = 6, shields = 1),
            DayRecord(LocalDate.of(2026, 10, 18)),
        )
        assertThat(state.streak).isEqualTo(6)
        assertThat(state.shields).isEqualTo(0)
        assertThat(result.shieldUsed).isTrue()
        assertThat(result.penaltyTriggered).isFalse()
    }

    @Test
    fun `vector 15 - same hunterId and gameDate generates identical directive without duplicates`() {
        val templates = TemplateFixtures.library()
        val date = LocalDate.of(2026, 10, 18)

        val first = DirectiveGenerator.generate(templates, context, date)
        val second = DirectiveGenerator.generate(templates, context, date)

        assertThat(second).isEqualTo(first)
        assertThat(first.quests.map { it.templateId }).containsNoDuplicates()
        assertThat(first.quests.map { it.questInstanceId }).containsNoDuplicates()
    }

    /** A quest instance id whose fnv1a64 roll is not a crit, so vectors stay about the formula only. */
    private fun nonCritQuest(difficulty: Difficulty, verification: VerificationSource): CompletableQuest {
        val id = generateSequence(0) { it + 1 }.map { "quest-$it" }.first { !CritRoller.isCrit(it) }
        return CompletableQuest(id, QuestType.SIDE, Stat.STR, null, difficulty, verification)
    }
}
