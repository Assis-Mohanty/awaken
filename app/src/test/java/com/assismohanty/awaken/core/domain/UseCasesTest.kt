package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.repo.COMPLETION_SOURCE_TYPES
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UseCasesTest {
    private val graph = TestGraph()
    private val questDao = graph.db.questDao()
    private val ledgerDao = graph.db.ledgerDao()

    @After
    fun tearDown() = graph.close()

    private fun CompleteQuestResult.outcome(): CompletionOutcome {
        assertThat(this).isInstanceOf(CompleteQuestResult.Completed::class.java)
        return (this as CompleteQuestResult.Completed).outcome
    }

    private fun CompletionOutcome.achievementGold(): Long =
        unlockedAchievements.sumOf { AchievementEvaluator.byId.getValue(it).gold.toLong() }

    @Test
    fun generatingTwiceOnTheSameDayCreatesNoDuplicates() = runTest {
        graph.seedHunter()
        val first = graph.generateDirective(graph.today())
        val second = graph.generateDirective(graph.today())

        assertThat(first).isNotNull()
        assertThat(second).isEqualTo(first)
        assertThat(first?.count { it.type == QuestType.MAIN }).isEqualTo(1)
        assertThat(first?.count { it.type == QuestType.SIDE }).isEqualTo(3)
        assertThat(questDao.getForDate(graph.today())).hasSize(4)
    }

    @Test
    fun sameCompletionTwiceWritesOneSetOfRows() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("pushups")

        val outcome = graph.completeQuest(quest.id).outcome()
        val rowsAfterFirst = ledgerDao.all()
        val second = graph.completeQuest(quest.id)

        assertThat(second).isEqualTo(CompleteQuestResult.NotCompletable(QuestStatus.COMPLETED))
        assertThat(ledgerDao.all()).isEqualTo(rowsAfterFirst)
        assertThat(ledgerDao.bySource(COMPLETION_SOURCE_TYPES, outcome.completionId).map { it.kind })
            .containsExactly(LedgerKind.XP_TOTAL, LedgerKind.XP_STAT, LedgerKind.GOLD)
        // Re-applying the exact same rows is a no-op too.
        assertThat(graph.ledger.insertIdempotent(rowsAfterFirst.map { it.copy(id = 0) })).isEmpty()
    }

    @Test
    fun undoNetsQuestRewardToZeroButKeepsAchievements() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("pushups")
        val outcome = graph.completeQuest(quest.id).outcome()

        assertThat(graph.undoCompletion(quest.id)).isEqualTo(UndoResult.Undone(outcome.completionId))

        val balances = graph.ledger.balances()
        assertThat(balances.xpTotal).isEqualTo(0)
        assertThat(balances.statXp(quest.stat)).isEqualTo(0)
        assertThat(balances.gold).isEqualTo(outcome.achievementGold()) // GD §6.5: unlocks are permanent
        assertThat(ledgerDao.bySource(listOf(SourceType.UNDO), outcome.completionId)).hasSize(3)
        assertThat(questDao.get(quest.id)?.status).isEqualTo(QuestStatus.ACTIVE)
    }

    @Test
    fun completeUndoCompleteGivesOneNetRewardWithTheSameCrit() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("crit", crit = true)

        val first = graph.completeQuest(quest.id).outcome()
        graph.undoCompletion(quest.id)
        val second = graph.completeQuest(quest.id).outcome()

        assertThat(first.reward.crit).isTrue()
        assertThat(second.reward.crit).isTrue()
        assertThat(second.reward.xp).isEqualTo(first.reward.xp)
        assertThat(second.completionId).isEqualTo("${quest.id}#2")
        val balances = graph.ledger.balances()
        assertThat(balances.xpTotal).isEqualTo(second.reward.xp.toLong())
        assertThat(balances.fragments).isEqualTo(1)
        assertThat(balances.gold).isEqualTo(second.reward.gold + first.achievementGold() + second.achievementGold())
    }

    @Test
    fun healthQuestCannotBeCompletedByHand() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("steps", verification = Verification.HEALTH_STEPS)

        assertThat(graph.completeQuest(quest.id)).isEqualTo(CompleteQuestResult.VerificationRequired)
        assertThat(graph.completeQuest.completeVerified(quest.id, VerificationSource.TIMER))
            .isEqualTo(CompleteQuestResult.VerificationRequired)
        val outcome = graph.completeQuest.completeVerified(quest.id, VerificationSource.HEALTH).outcome()
        assertThat(outcome.reward.xp).isEqualTo(78) // 40 × 1.5 × 1.0 × 1.3
    }

    @Test
    fun levelUpIsDetectedWhenCrossingTheThreshold() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("extreme", difficulty = Difficulty.EXTREME)

        val outcome = graph.completeQuest(quest.id).outcome()

        assertThat(outcome.reward.xp).isEqualTo(120) // 40 × 3.0; level 2 needs 112
        assertThat(outcome.levelBefore).isEqualTo(1)
        assertThat(outcome.levelAfter).isEqualTo(2)
        assertThat(outcome.leveledUp).isTrue()
    }

    @Test
    fun dailyCapCountsUndoneXpAsZero() = runTest {
        graph.seedHunter()
        graph.ledger.insertIdempotent(
            listOf(
                LedgerEntryEntity(
                    kind = LedgerKind.XP_TOTAL, delta = 540, sourceType = SourceType.QUEST, sourceId = "earlier#1",
                    gameDate = graph.today(), xpSource = VerificationSource.SELF.name, createdAt = graph.clock.now(),
                ),
            ),
        )
        val a = graph.fixtureQuest("a")
        val b = graph.fixtureQuest("b")

        assertThat(graph.completeQuest(a.id).outcome().reward.cappedSource).isNull() // 540 + 60 = 600, not over
        graph.undoCompletion(a.id)
        val rewardB = graph.completeQuest(b.id).outcome().reward

        assertThat(rewardB.cappedSource).isNull()
        assertThat(rewardB.xp).isEqualTo(60)
    }

    @Test
    fun rolloverAcrossFiveMissedDaysUsesOneShieldThenBreaks() = runTest {
        graph.seedHunter()
        graph.rollover()
        val day1 = graph.today()
        repeat(3) { graph.completeQuest(graph.fixtureQuest("clear$it").id).outcome() }
        graph.grantShield()

        graph.clock.advance(Duration.ofDays(6)) // days 2–6 never opened
        val outcome = requireNotNull(graph.rollover())

        assertThat(outcome.finalized.map { it.gameDate }).containsExactlyElementsIn((0L..5L).map { day1.plusDays(it) }).inOrder()
        assertThat(outcome.finalized.map { it.outcome }).containsExactly(
            DayOutcome.CLEARED, DayOutcome.SHIELDED,
            DayOutcome.BROKEN, DayOutcome.BROKEN, DayOutcome.BROKEN, DayOutcome.BROKEN,
        ).inOrder()
        assertThat(outcome.penaltyIssued).isTrue()
        val balances = graph.ledger.balances()
        assertThat(balances.item(LedgerRepository.STREAK_SHIELD_ITEM_ID)).isEqualTo(0)
        assertThat(ledgerDao.bySource(listOf(SourceType.DAY_CLEAR), day1.toString())).hasSize(1)
        assertThat(graph.reader.finalizedStreak().streak).isEqualTo(0)
        assertThat(graph.reader.finalizedStreak().bestStreak).isEqualTo(1)

        // Day 1's unfinished generated quests expired; today has a directive plus exactly one penalty quest.
        assertThat(questDao.getForDate(day1).filter { it.type == QuestType.MAIN }.map { it.status }).containsExactly(QuestStatus.EXPIRED)
        val todayQuests = questDao.getForDate(graph.today())
        assertThat(todayQuests.count { it.type == QuestType.PENALTY }).isEqualTo(1)

        // Running again finalizes nothing and adds nothing.
        val again = requireNotNull(graph.rollover())
        assertThat(again.finalized).isEmpty()
        assertThat(questDao.getForDate(graph.today())).hasSize(todayQuests.size)
    }

    @Test
    fun rerollFirstFreeThenCostsGold() = runTest {
        graph.seedHunter()
        graph.rollover()
        val side = questDao.getForDate(graph.today()).first { it.type == QuestType.SIDE }
        val main = questDao.getForDate(graph.today()).first { it.type == QuestType.MAIN }

        val first = graph.rerollQuest(side.id)
        assertThat(first).isInstanceOf(RerollResult.Rerolled::class.java)
        assertThat((first as RerollResult.Rerolled).cost).isEqualTo(0)
        assertThat(questDao.get(side.id)?.status).isEqualTo(QuestStatus.REROLLED)

        assertThat(graph.rerollQuest(first.replacement.id)).isEqualTo(RerollResult.InsufficientGold(cost = 40, gold = 0))
        assertThat(graph.rerollQuest(main.id)).isEqualTo(RerollResult.NotRerollable)
    }

    @Test
    fun undoAfterTheDayEndedIsRefused() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("late")
        graph.clock.instant = Instant.parse("2026-10-19T03:55:00Z") // still 18 Oct's game-day
        graph.completeQuest(quest.id).outcome()
        graph.clock.advance(Duration.ofMinutes(6)) // 04:01 → new game-day, inside the 10-minute window

        assertThat(graph.undoCompletion(quest.id)).isEqualTo(UndoResult.DayClosed)
    }
}
