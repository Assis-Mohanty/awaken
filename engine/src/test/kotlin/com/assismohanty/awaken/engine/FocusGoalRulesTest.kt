package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** GD §16.4 daily focus goal and focus streak (vectors #98-#102 minus the crystal, #101 achievement). */
class FocusGoalRulesTest {
    private val monday = LocalDate.of(2026, 10, 19)
    private val first = LocalDate.of(2026, 10, 1)

    private fun day(offset: Long) = monday.plusDays(offset)

    @Test
    fun `vector 98 - runs count in whole minutes and collapsed ones are not in the list`() {
        // Cleared D preset (25), custom 40, stopwatch 47 min 30 s; the collapsed C gate is not passed in at all.
        val minutes = FocusGoalRules.focusMinutes(listOf(25 * 60, 40 * 60, 47 * 60 + 30))
        assertThat(minutes).isEqualTo(112)
        assertThat(FocusGoalRules.met(minutes, FocusGoalRules.DEFAULT_GOAL)).isFalse()
        // A cleared E gate (15) later in the day tips it over.
        assertThat(FocusGoalRules.met(minutes + 15, FocusGoalRules.DEFAULT_GOAL)).isTrue()
    }

    @Test
    fun `each run is floored on its own, not as a total`() {
        assertThat(FocusGoalRules.focusMinutes(listOf(119, 119))).isEqualTo(2)
    }

    @Test
    fun `the goal is versioned - the latest version on or before the day wins, else 120`() {
        val versions = listOf(FocusGoalVersion(day(3), 60), FocusGoalVersion(day(1), 90))
        assertThat(FocusGoalRules.goalOn(day(0), versions)).isEqualTo(120)
        assertThat(FocusGoalRules.goalOn(day(1), versions)).isEqualTo(90)
        assertThat(FocusGoalRules.goalOn(day(2), versions)).isEqualTo(90)
        assertThat(FocusGoalRules.goalOn(day(3), versions)).isEqualTo(60)
        assertThat(FocusGoalRules.goalOn(day(9), emptyList())).isEqualTo(120)
    }

    @Test
    fun `goal bounds are 15 to 600 in steps of 15`() {
        assertThat(listOf(15, 120, 600).all(FocusGoalRules::isValidGoal)).isTrue()
        assertThat(listOf(0, 10, 125, 615).none(FocusGoalRules::isValidGoal)).isTrue()
        assertThat(FocusGoalRules.clampGoal(1)).isEqualTo(15)
        assertThat(FocusGoalRules.clampGoal(9_999)).isEqualTo(600)
        assertThat(FocusGoalRules.clampGoal(100)).isEqualTo(90)
    }

    @Test
    fun `vector 99 - a finalized rest day is skipped over, so Mon Tue Thu with a rest Wednesday is a streak of 3`() {
        val minutes = mapOf(day(0) to 130, day(1) to 125, day(3) to 120)
        val outcomes = mapOf(day(2) to DayOutcome.REST)

        assertThat(FocusGoalRules.streak(day(3), first, minutes, emptyList(), outcomes)).isEqualTo(3)
    }

    @Test
    fun `a recovery day is skipped too, but an ordinary missed day ends the streak`() {
        val minutes = mapOf(day(0) to 130, day(1) to 125, day(3) to 120)
        assertThat(FocusGoalRules.streak(day(3), first, minutes, emptyList(), mapOf(day(2) to DayOutcome.RECOVERY))).isEqualTo(3)
        assertThat(FocusGoalRules.streak(day(3), first, minutes, emptyList(), mapOf(day(2) to DayOutcome.BROKEN))).isEqualTo(1)
        assertThat(FocusGoalRules.streak(day(3), first, minutes, emptyList(), emptyMap())).isEqualTo(1)
    }

    @Test
    fun `counting back from yesterday when today is not met yet`() {
        val minutes = mapOf(day(0) to 130, day(1) to 125, day(2) to 10)
        assertThat(FocusGoalRules.streak(day(2), first, minutes, emptyList(), emptyMap())).isEqualTo(2)
        // Nothing yesterday either: no streak.
        assertThat(FocusGoalRules.streak(day(4), first, minutes, emptyList(), emptyMap())).isEqualTo(0)
    }

    @Test
    fun `vector 100 - changing the goal today applies from today and earlier days keep theirs`() {
        // Wednesday had 100 minutes against 120 (not met). Thursday: goal lowered to 60 with 70 minutes done.
        val goals = listOf(FocusGoalVersion(day(3), 60))
        val minutes = mapOf(day(2) to 100, day(3) to 70)

        assertThat(FocusGoalRules.met(minutes.getValue(day(3)), FocusGoalRules.goalOn(day(3), goals))).isTrue()
        assertThat(FocusGoalRules.met(minutes.getValue(day(2)), FocusGoalRules.goalOn(day(2), goals))).isFalse()
        assertThat(FocusGoalRules.streak(day(3), first, minutes, goals, emptyMap())).isEqualTo(1)
    }

    @Test
    fun `the streak never reaches back before the first game day`() {
        val minutes = (0L..9L).associate { monday.minusDays(it) to 200 }
        assertThat(FocusGoalRules.streak(monday, monday.minusDays(4), minutes, emptyList(), emptyMap())).isEqualTo(5)
    }

    @Test
    fun `vector 101 - the 7th consecutive met day unlocks focus_streak7 once for 30 gold, with no XP`() {
        val unlocked = AchievementEvaluator.evaluate(AchievementSnapshot(focusStreak = 7))
        assertThat(unlocked).containsExactly("focus_streak7")
        assertThat(AchievementEvaluator.byId.getValue("focus_streak7").gold).isEqualTo(30)
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(focusStreak = 8, alreadyUnlocked = setOf("focus_streak7")))).isEmpty()
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(focusStreak = 6))).isEmpty()
    }

    @Test
    fun `focus streak 30 and 100 pay 100 and 200 gold and 100 grants the Deep Worker title`() {
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(focusStreak = 30))).containsExactly("focus_streak7", "focus_streak30").inOrder()
        assertThat(AchievementEvaluator.byId.getValue("focus_streak30").gold).isEqualTo(100)
        assertThat(AchievementEvaluator.byId.getValue("focus_streak100").gold).isEqualTo(200)
        assertThat(AchievementEvaluator.byId.getValue("focus_streak100").titleId).isEqualTo("deep_worker")
    }
}
