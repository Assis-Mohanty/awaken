package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestTemplate
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RulesTest {

    @Nested
    inner class XpCalculatorTest {
        private val context = HunterContext(hunterId = "h", focusStats = listOf(Stat.STR, Stat.INT))
        private val critId = generateSequence(0) { it + 1 }.map { "id-$it" }.first { CritRoller.isCrit(it) }
        private val plainId = generateSequence(0) { it + 1 }.map { "id-$it" }.first { !CritRoller.isCrit(it) }

        private fun quest(id: String, type: QuestType = QuestType.SIDE, verification: VerificationSource = VerificationSource.SELF) =
            CompletableQuest(id, type, Stat.STR, Stat.WIL, Difficulty.NORMAL, verification)

        @Test
        fun `secondary stat gets 30 percent and total XP is counted once`() {
            val reward = XpCalculator.forCompletion(quest(plainId), context)
            assertThat(reward.xp).isEqualTo(60)
            assertThat(reward.statXp).containsExactly(Stat.STR, 60, Stat.WIL, 18)
            assertThat(reward.gold).isEqualTo(30)
            assertThat(reward.fragments).isEqualTo(0)
        }

        @Test
        fun `crit doubles XP and grants a fragment`() {
            val reward = XpCalculator.forCompletion(quest(critId), context)
            assertThat(reward.crit).isTrue()
            assertThat(reward.xp).isEqualTo(120)
            assertThat(reward.fragments).isEqualTo(1)
            assertThat(reward.breakdown.map { it.kind }).contains(com.assismohanty.awaken.engine.model.BreakdownItem.Kind.CRIT)
        }

        @Test
        fun `penalty and trial quests never crit`() {
            assertThat(XpCalculator.forCompletion(quest(critId, QuestType.PENALTY), context).crit).isFalse()
            assertThat(XpCalculator.forCompletion(quest(critId, QuestType.TRIAL), context).crit).isFalse()
        }

        @Test
        fun `gate clear grants a fragment`() {
            val reward = XpCalculator.forCompletion(quest(plainId, QuestType.GATE, VerificationSource.TIMER), context)
            assertThat(reward.fragments).isEqualTo(1)
            assertThat(reward.xp).isEqualTo(69) // 40 × 1.5 × 1.0 × 1.15
        }

        @Test
        fun `timer cap at 900 and health is uncapped`() {
            val timer = quest(plainId, verification = VerificationSource.TIMER)
            assertThat(XpCalculator.forCompletion(timer, context.copy(todayTimerXp = 831)).cappedSource).isNull()
            assertThat(XpCalculator.forCompletion(timer, context.copy(todayTimerXp = 832)).cappedSource).isEqualTo(VerificationSource.TIMER)
            val health = quest(plainId, verification = VerificationSource.HEALTH)
            assertThat(XpCalculator.forCompletion(health, context.copy(todaySelfXp = 10_000)).cappedSource).isNull()
        }

        @Test
        fun `exactly reaching the cap is not over it`() {
            assertThat(XpCalculator.forCompletion(quest(plainId), context.copy(todaySelfXp = 540)).cappedSource).isNull()
        }

        @Test
        fun `rounding is half-up`() {
            assertThat(XpCalculator.roundHalfUp(2.5)).isEqualTo(3)
            assertThat(XpCalculator.goldFor(15)).isEqualTo(8)
        }
    }

    @Nested
    inner class StatLevelTest {
        @Test
        fun `boundaries are exact`() {
            for (level in 1..60) {
                val xp = StatLevel.xpForLevel(level)
                assertThat(StatLevel.level(xp)).isEqualTo(level)
                if (level > 1) assertThat(StatLevel.level(xp - 1)).isEqualTo(level - 1)
            }
            assertThat(StatLevel.level(23_040)).isEqualTo(25)
            assertThat(StatLevel.level(-10)).isEqualTo(1)
        }

        @Test
        fun `radar max is at least 20`() {
            assertThat(StatLevel.radarMax(3)).isEqualTo(20)
            assertThat(StatLevel.radarMax(18)).isEqualTo(23)
        }
    }

    @Nested
    inner class RankRulesTest {
        private val now = Instant.parse("2026-10-18T10:00:00Z")

        @Test
        fun `bands follow GD 5`() {
            assertThat(listOf(1, 9, 10, 19, 20, 34, 35, 49, 50, 69, 70, 100).map { RankRules.bandFor(it) })
                .containsExactly(Rank.E, Rank.E, Rank.D, Rank.D, Rank.C, Rank.C, Rank.B, Rank.B, Rank.A, Rank.A, Rank.S, Rank.S)
                .inOrder()
        }

        @Test
        fun `reaching a band unlocks the next trial but does not promote`() {
            assertThat(RankRules.currentRank(emptySet())).isEqualTo(Rank.E)
            assertThat(RankRules.trialAvailable(9, emptySet(), false, null, now)).isNull()
            assertThat(RankRules.trialAvailable(10, emptySet(), false, null, now)).isEqualTo(Rank.D)
            assertThat(RankRules.trialAvailable(40, emptySet(), false, null, now)).isEqualTo(Rank.D)
            assertThat(RankRules.trialAvailable(40, setOf(Rank.D), false, null, now)).isEqualTo(Rank.C)
            assertThat(RankRules.trialAvailable(10, emptySet(), true, null, now)).isNull()
            assertThat(RankRules.trialAvailable(100, Rank.entries.toSet(), false, null, now)).isNull()
        }

        @Test
        fun `retry waits 48 hours after a failure`() {
            assertThat(RankRules.trialAvailable(10, emptySet(), false, now.minus(Duration.ofHours(47)), now)).isNull()
            assertThat(RankRules.trialAvailable(10, emptySet(), false, now.minus(Duration.ofHours(48)), now)).isEqualTo(Rank.D)
        }

        @Test
        fun `trial passes with 3 stages in 5 days, one per day, otherwise fails`() {
            val start = LocalDate.of(2026, 10, 1)
            val clears = listOf(start, start.plusDays(2), start.plusDays(4))
            assertThat(RankRules.trialStatus(start, start.plusDays(4), clears)).isEqualTo(RankRules.TrialStatus.PASSED)
            assertThat(RankRules.trialStatus(start, start.plusDays(4), clears.take(2))).isEqualTo(RankRules.TrialStatus.ACTIVE)
            assertThat(RankRules.trialStatus(start, start.plusDays(5), clears.take(2))).isEqualTo(RankRules.TrialStatus.FAILED)
            assertThat(RankRules.canClearStage(start, start, listOf(start))).isFalse()
            assertThat(RankRules.canClearStage(start, start.plusDays(5), emptyList())).isFalse()
        }

        @Test
        fun `trial stats are two weakest plus a focus stat`() {
            val xp = mapOf(Stat.STR to 500L, Stat.INT to 900L, Stat.WIL to 10L, Stat.VIT to 20L, Stat.CHA to 300L)
            assertThat(RankRules.trialStats(xp, listOf(Stat.INT, Stat.STR))).containsExactly(Stat.WIL, Stat.VIT, Stat.INT).inOrder()
            assertThat(RankRules.trialStats(xp, listOf(Stat.WIL, Stat.VIT))).containsExactly(Stat.WIL, Stat.VIT, Stat.CHA).inOrder()
        }
    }

    @Nested
    inner class DifficultyAdapterTest {
        private val monday = LocalDate.of(2026, 10, 19)

        private fun outcomes(stat: Stat, completed: Int, total: Int) = (0 until total).map {
            QuestOutcome(monday.minusDays(1L + it % 7), QuestType.SIDE, stat, if (it < completed) QuestStatus.COMPLETED else QuestStatus.EXPIRED)
        }

        @Test
        fun `thresholds and minimum count`() {
            assertThat(DifficultyAdapter.changeFor(4, 4)).isEqualTo(DifficultyAdapter.Change.UP)
            assertThat(DifficultyAdapter.changeFor(3, 3)).isEqualTo(DifficultyAdapter.Change.NONE)
            assertThat(DifficultyAdapter.changeFor(2, 5)).isEqualTo(DifficultyAdapter.Change.DOWN)
            assertThat(DifficultyAdapter.changeFor(3, 5)).isEqualTo(DifficultyAdapter.Change.NONE)
        }

        @Test
        fun `recalibrates per stat within bounds using the previous 7 days`() {
            val all = outcomes(Stat.STR, 6, 6) + outcomes(Stat.INT, 0, 5) + outcomes(Stat.CHA, 6, 6) +
                QuestOutcome(monday.minusDays(8), QuestType.SIDE, Stat.VIT, QuestStatus.EXPIRED) +
                outcomes(Stat.WIL, 0, 5).map { it.copy(type = QuestType.HABIT) }
            val result = DifficultyAdapter.recalibrate(mapOf(Stat.CHA to Difficulty.EXTREME), all, monday.plusDays(2))

            assertThat(result.tiers).containsExactly(
                Stat.STR, Difficulty.HARD, Stat.INT, Difficulty.EASY, Stat.WIL, Difficulty.NORMAL,
                Stat.VIT, Difficulty.NORMAL, Stat.CHA, Difficulty.EXTREME,
            )
            assertThat(result.changes[Stat.CHA]).isEqualTo(DifficultyAdapter.Change.NONE)
        }

        @Test
        fun `due on Monday or first open after`() {
            assertThat(DifficultyAdapter.isDue(monday.plusDays(3), monday.minusDays(7))).isTrue()
            assertThat(DifficultyAdapter.isDue(monday.plusDays(3), monday)).isFalse()
        }
    }

    @Nested
    inner class AchievementEvaluatorTest {
        @Test
        fun `launch set has 32 unique ids`() {
            assertThat(AchievementEvaluator.all.map { it.id }).containsNoDuplicates()
            assertThat(AchievementEvaluator.all).hasSize(32)
        }

        @Test
        fun `returns only newly unlocked ids`() {
            val snapshot = AchievementSnapshot(questsCompleted = 1, onboardingDone = true, level = 10, alreadyUnlocked = setOf("awakened"))
            assertThat(AchievementEvaluator.evaluate(snapshot)).containsExactly("first_blood", "lvl5", "lvl10").inOrder()
            assertThat(AchievementEvaluator.evaluate(AchievementSnapshot())).isEmpty()
        }

        @Test
        fun `stat, balance, gate hours and rank conditions`() {
            val snapshot = AchievementSnapshot(
                statXp = Stat.entries.associateWith { StatLevel.xpForLevel(5) } + (Stat.INT to StatLevel.xpForLevel(20)),
                gateMinutesCleared = 600,
                passedTrials = setOf(Rank.D),
                alreadyUnlocked = emptySet(),
            )
            assertThat(AchievementEvaluator.evaluate(snapshot)).containsExactly("gate_hours10", "balanced", "int20", "rank_d").inOrder()
        }

        @Test
        fun `dawn raid and midnight gate windows respect dayStartHour`() {
            assertThat(AchievementEvaluator.isDawnRaid(LocalTime.of(6, 59))).isTrue()
            assertThat(AchievementEvaluator.isDawnRaid(LocalTime.of(7, 0))).isFalse()
            assertThat(AchievementEvaluator.isDawnRaid(LocalTime.of(2, 0))).isFalse()
            assertThat(AchievementEvaluator.isMidnightGate(LocalTime.of(23, 0))).isTrue()
            assertThat(AchievementEvaluator.isMidnightGate(LocalTime.of(3, 30))).isTrue()
            assertThat(AchievementEvaluator.isMidnightGate(LocalTime.of(4, 0))).isFalse()
        }
    }

    @Nested
    inner class QuestRulesTest {
        private val t0 = Instant.parse("2026-10-18T10:00:00Z")

        @Test
        fun `min duration only applies once begun`() {
            assertThat(QuestRules.canComplete(null, t0, 120)).isTrue()
            assertThat(QuestRules.canComplete(t0, t0.plusSeconds(119), 120)).isFalse()
            assertThat(QuestRules.canComplete(t0, t0.plusSeconds(120), 120)).isTrue()
        }

        @Test
        fun `undo window is 10 minutes`() {
            assertThat(QuestRules.canUndo(t0, t0.plus(Duration.ofMinutes(10)))).isTrue()
            assertThat(QuestRules.canUndo(t0, t0.plus(Duration.ofMinutes(10)).plusSeconds(1))).isFalse()
        }

        @Test
        fun `custom quest limits`() {
            assertThat(QuestRules.maxCustomDifficulty(VerificationSource.SELF)).isEqualTo(Difficulty.NORMAL)
            assertThat(QuestRules.maxCustomDifficulty(VerificationSource.TIMER)).isEqualTo(Difficulty.EXTREME)
            assertThat(QuestRules.canAddCustom(7)).isTrue()
            assertThat(QuestRules.canAddCustom(8)).isFalse()
        }

        @Test
        fun `gate collapses past 90 seconds in background`() {
            assertThat(GateRules.isCollapsed(90_000)).isFalse()
            assertThat(GateRules.isCollapsed(90_001)).isTrue()
            assertThat(GateRules.canPause(1)).isFalse()
        }
    }

    @Test
    fun `template JSON from GD 6_2 deserializes`() {
        val json = """
            {
              "id": "str_pushups", "stat": "STR", "secondaryStat": "WIL",
              "titles": ["Iron Arms", "Ground Assault", "The Hundred"],
              "description": "Complete {target} push-ups. Sets of any size.", "unit": "reps",
              "targets": { "EASY": 20, "NORMAL": 40, "HARD": 80, "EXTREME": 120 },
              "verification": "SELF", "equipment": [], "tags": ["bodyweight", "indoor", "no_impact_false"],
              "minDurationSec": 120
            }
        """.trimIndent()
        val template = Json.decodeFromString<QuestTemplate>(json)
        assertThat(template.targets[Difficulty.HARD]).isEqualTo(80)
        assertThat(template.verification).isEqualTo(Verification.SELF)
        assertThat(template.secondaryStat).isEqualTo(Stat.WIL)
    }
}
