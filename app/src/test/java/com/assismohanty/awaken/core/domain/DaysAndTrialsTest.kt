package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.repo.Notice
import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.LevelCurve
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.RankRules.TrialStatus
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 7: rollover notices, rest days, Recovery Mode and Rank Trials, end to end on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class DaysAndTrialsTest {
    private val graph = TestGraph()
    private val questDao = graph.db.questDao()
    private val dayRecordDao = graph.db.dayRecordDao()
    private val trialDao = graph.db.trialDao()

    @After
    fun tearDown() = graph.close()

    private suspend fun nextDay(): RolloverOutcome {
        graph.clock.advance(Duration.ofDays(1))
        return requireNotNull(graph.runRollover())
    }

    private suspend fun questsToday() = questDao.getForDate(graph.today())

    private suspend fun complete(questInstanceId: String): CompletionOutcome {
        val result = graph.completeQuest(questInstanceId)
        assertThat(result).isInstanceOf(CompleteQuestResult.Completed::class.java)
        return (result as CompleteQuestResult.Completed).outcome
    }

    /** Puts the hunter at exactly [level] (debug-style XP grant through the ledger). */
    private suspend fun reachLevel(level: Int) {
        graph.ledger.insertIdempotent(
            listOf(
                LedgerEntryEntity(
                    kind = LedgerKind.XP_TOTAL, delta = LevelCurve.cumulative(level), sourceType = SourceType.QUEST,
                    sourceId = "test:level$level", gameDate = graph.today(), createdAt = graph.clock.now(),
                ),
            ),
        )
    }

    @Test
    fun aFailedDayIssuesOnePenaltyQuestAndOneWarning() = runTest {
        graph.seedHunter()
        graph.runRollover()

        val outcome = nextDay()
        graph.runRollover() // same day again: nothing new

        assertThat(outcome.penaltyIssued).isTrue()
        assertThat(questsToday().count { it.type == QuestType.PENALTY }).isEqualTo(1)
        assertThat(graph.notices.pending.first()).containsExactly(Notice.PenaltyWarning(graph.today().toString()))
    }

    @Test
    fun aShieldedMissLeavesAShieldNotice() = runTest {
        graph.seedHunter()
        graph.runRollover()
        complete(questsToday().first { it.type == QuestType.MAIN }.id)
        graph.grantShield()
        nextDay() // day 1 cleared: streak 1

        nextDay() // day 2 missed: the shield is used

        val shielded = graph.today().minusDays(1)
        assertThat(dayRecordDao.get(shielded)?.outcome).isEqualTo(DayOutcome.SHIELDED)
        assertThat(graph.notices.pending.first()).contains(Notice.ShieldUsed(shielded.toString()))
    }

    @Test
    fun rolloverOutcomesMapToNotices() {
        val today = graph.today()
        val outcome = RolloverOutcome(
            today = today,
            finalized = emptyList(),
            penaltyIssued = false,
            recalibration = mapOf(Stat.STR to DifficultyAdapter.Change.UP),
            trialFailed = Rank.D,
        )
        assertThat(outcome.toNotices()).containsExactly(
            Notice.Recalibrated(mapOf(Stat.STR to DifficultyAdapter.Change.UP)),
            Notice.TrialFailed(Rank.D),
        ).inOrder()
    }

    @Test
    fun restDaysAreSetAheadOncePerIsoWeekAndProtectTheStreak() = runTest {
        graph.seedHunter() // 2026-10-18 is a Sunday
        graph.runRollover()
        val monday = graph.today().plusDays(1)

        assertThat(graph.setRestDay(graph.today(), rest = true)).isEqualTo(DayPlanResult.NotAllowed) // already started
        assertThat(graph.setRestDay(monday, rest = true)).isEqualTo(DayPlanResult.Saved)
        assertThat(graph.setRestDay(monday.plusDays(1), rest = true)).isEqualTo(DayPlanResult.NotAllowed) // same ISO week
        assertThat(graph.setRestDay(graph.today().plusDays(DayPlan.REST_PLAN_DAYS + 1), rest = true)).isEqualTo(DayPlanResult.NotAllowed)

        nextDay()
        nextDay() // finalizes Monday

        assertThat(dayRecordDao.get(monday)?.outcome).isEqualTo(DayOutcome.REST)
    }

    @Test
    fun recoveryCarriesOverDailyUntilTurnedOff() = runTest {
        graph.seedHunter()
        graph.runRollover()
        assertThat(graph.setRecoveryMode(on = true)).isEqualTo(DayPlanResult.Saved)

        nextDay()

        assertThat(dayRecordDao.get(graph.today().minusDays(1))?.outcome).isEqualTo(DayOutcome.RECOVERY)
        assertThat(dayRecordDao.get(graph.today())?.recovery).isTrue()
        val generated = questsToday().filter { it.type == QuestType.MAIN || it.type == QuestType.SIDE }
        assertThat(generated).isNotEmpty()
        assertThat(generated.map { it.stat }.toSet()).containsNoneOf(Stat.STR, Stat.INT, Stat.CHA)

        graph.setRecoveryMode(on = false)
        graph.runRollover() // a same-day rollover must not switch it back on
        assertThat(dayRecordDao.get(graph.today())?.recovery).isFalse()
    }

    @Test
    fun recoveryStopsWhenTheMonthlyBudgetRunsOut() = runTest {
        graph.seedHunter() // October 18
        graph.runRollover()
        graph.setRecoveryMode(on = true)

        repeat(7) { nextDay() } // October 25: 18..24 used all 7 days

        assertThat(dayRecordDao.get(graph.today().minusDays(1))?.recovery).isTrue()
        assertThat(dayRecordDao.get(graph.today())?.recovery ?: false).isFalse()
        assertThat(graph.setRecoveryMode(on = true)).isEqualTo(DayPlanResult.NoRecoveryLeft)
    }

    @Test
    fun aTrialPassesAfterOneStagePerDayAndPromotes() = runTest {
        graph.seedHunter()
        graph.runRollover()
        assertThat(graph.startTrial()).isEqualTo(StartTrialResult.NotAvailable) // level 1: no trial yet
        reachLevel(Rank.D.minLevel)

        assertThat(graph.startTrial()).isInstanceOf(StartTrialResult.Started::class.java)
        val stages = questsToday().filter { it.type == QuestType.TRIAL }
        assertThat(stages).hasSize(RankRules.TRIAL_STAGES)
        assertThat(stages.map { it.stat }.toSet()).hasSize(RankRules.TRIAL_STAGES)

        // Day 1: clearing one stage closes the others until tomorrow.
        assertThat(complete(stages[0].id).promotedTo).isNull()
        assertThat(questsToday().filter { it.type == QuestType.TRIAL && it.id != stages[0].id }.map { it.status })
            .containsExactly(QuestStatus.EXPIRED, QuestStatus.EXPIRED)

        // Day 2: only the two open stages come back.
        nextDay()
        val day2 = questsToday().filter { it.type == QuestType.TRIAL }
        assertThat(day2.map { it.stat }).containsExactlyElementsIn(stages.drop(1).map { it.stat })
        complete(day2[0].id)

        // Day 3: the last stage promotes.
        nextDay()
        val last = questsToday().single { it.type == QuestType.TRIAL }
        val goldBefore = graph.ledger.balances().gold
        val outcome = complete(last.id)

        assertThat(outcome.promotedTo).isEqualTo(Rank.D)
        assertThat(trialDao.passedRanks()).containsExactly(Rank.D)
        assertThat(outcome.unlockedAchievements).contains("rank_d")
        val achievementGold = outcome.unlockedAchievements.sumOf { AchievementEvaluator.byId.getValue(it).gold }
        assertThat(graph.ledger.balances().gold - goldBefore).isEqualTo((RankRules.TRIAL_REWARD_GOLD + outcome.reward.gold + achievementGold).toLong())

        // Promotion is permanent (ENGINE_DECISIONS #32).
        assertThat(graph.undoCompletion(last.id)).isEqualTo(UndoResult.TrialPassed)
    }

    @Test
    fun undoingAStageReopensTheDaysOtherStages() = runTest {
        graph.seedHunter()
        graph.runRollover()
        reachLevel(Rank.D.minLevel)
        graph.startTrial()
        val stages = questsToday().filter { it.type == QuestType.TRIAL }

        complete(stages[0].id)
        assertThat(graph.undoCompletion(stages[0].id)).isInstanceOf(UndoResult.Undone::class.java)

        assertThat(questsToday().filter { it.type == QuestType.TRIAL }.map { it.status }.toSet()).containsExactly(QuestStatus.ACTIVE)
    }

    @Test
    fun anExpiredTrialFailsWithACooldownFromTheNextDayStart() = runTest {
        graph.seedHunter()
        graph.runRollover()
        reachLevel(Rank.D.minLevel)
        graph.startTrial()
        val trial = requireNotNull(trialDao.active())

        repeat(RankRules.TRIAL_WINDOW_DAYS.toInt()) { nextDay() }

        val failed = requireNotNull(trialDao.covering(trial.startedGameDate))
        assertThat(failed.status).isEqualTo(TrialStatus.FAILED)
        assertThat(failed.endedAt).isEqualTo(RankRules.trialFailedAt(trial.startedGameDate, ZoneOffset.UTC, 4))
        assertThat(graph.notices.pending.first()).contains(Notice.TrialFailed(Rank.D))
        assertThat(graph.startTrial()).isEqualTo(StartTrialResult.NotAvailable) // inside the 48 h cooldown

        nextDay()
        nextDay()
        assertThat(graph.startTrial()).isInstanceOf(StartTrialResult.Started::class.java)
    }
}
