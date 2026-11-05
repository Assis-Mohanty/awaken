package com.assismohanty.awaken.engine.onboarding

import com.assismohanty.awaken.engine.body.WeightGoal
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnboardingRulesTest {

    @Test
    fun `vector 143 sleep and focus give VIT and WIL`() {
        assertThat(OnboardingRules.focusStats(listOf(ImproveGoal.SLEEP, ImproveGoal.FOCUS))).containsExactly(Stat.VIT, Stat.WIL)
    }

    @Test
    fun `vector 144 one stat picked is filled in order`() {
        // Strength and fitness are both STR, so INT fills the second slot.
        assertThat(OnboardingRules.focusStats(listOf(ImproveGoal.STRENGTH, ImproveGoal.FITNESS))).containsExactly(Stat.STR, Stat.INT)
        // The fill skips a stat already chosen.
        assertThat(OnboardingRules.focusStats(listOf(ImproveGoal.LEARNING))).containsExactly(Stat.INT, Stat.STR)
    }

    @Test
    fun `vector 145 skipped step gives STR and INT`() {
        assertThat(OnboardingRules.focusStats(emptyList())).containsExactly(Stat.STR, Stat.INT).inOrder()
    }

    @Test
    fun `vector 146 the stat with most picks wins and earliest pick breaks a tie`() {
        assertThat(
            OnboardingRules.focusStats(listOf(ImproveGoal.LEARNING, ImproveGoal.FOCUS, ImproveGoal.DISCIPLINE)),
        ).containsExactly(Stat.WIL, Stat.INT).inOrder()
        // INT, VIT and WIL have one pick each: the first two picked stay.
        assertThat(
            OnboardingRules.focusStats(listOf(ImproveGoal.LEARNING, ImproveGoal.SLEEP, ImproveGoal.FOCUS)),
        ).containsExactly(Stat.INT, Stat.VIT).inOrder()
    }

    @Test
    fun `focus stats are always two different stats`() {
        val all = ImproveGoal.entries
        for (size in 0..all.size) {
            val stats = OnboardingRules.focusStats(all.take(size))
            assertThat(stats).hasSize(2)
            assertThat(stats.toSet()).hasSize(2)
        }
    }

    @Test
    fun `vector 147 body shape stage picks the Diet goal hint`() {
        assertThat(OnboardingRules.dietGoalHint(1, null, null)).isEqualTo(WeightGoal.GAIN)
        assertThat(OnboardingRules.dietGoalHint(2, null, null)).isEqualTo(WeightGoal.GAIN)
        assertThat(OnboardingRules.dietGoalHint(3, null, null)).isEqualTo(WeightGoal.MAINTAIN)
        assertThat(OnboardingRules.dietGoalHint(4, null, null)).isEqualTo(WeightGoal.LOSE)
        assertThat(OnboardingRules.dietGoalHint(5, null, null)).isEqualTo(WeightGoal.LOSE)
        assertThat(OnboardingRules.dietGoalHint(null, null, null)).isNull()
    }

    @Test
    fun `vector 148 a target weight overrides the stage`() {
        assertThat(OnboardingRules.dietGoalHint(1, 70.0, WeightTarget(65.0, "2027-03-01"))).isEqualTo(WeightGoal.LOSE)
        assertThat(OnboardingRules.dietGoalHint(5, 70.0, WeightTarget(75.0, "2027-03-01"))).isEqualTo(WeightGoal.GAIN)
        assertThat(OnboardingRules.dietGoalHint(5, 70.0, WeightTarget(70.0, "2027-03-01"))).isEqualTo(WeightGoal.MAINTAIN)
        // A target with no known current weight can't be compared, so the stage still decides.
        assertThat(OnboardingRules.dietGoalHint(4, null, WeightTarget(65.0, "2027-03-01"))).isEqualTo(WeightGoal.LOSE)
    }

    @Test
    fun `vector 149 None is exclusive`() {
        var picked = OnboardingRules.toggleObstacle(emptySet(), Obstacle.TOO_MUCH_SCROLLING)
        picked = OnboardingRules.toggleObstacle(picked, Obstacle.POOR_SLEEP)
        assertThat(picked).containsExactly(Obstacle.TOO_MUCH_SCROLLING, Obstacle.POOR_SLEEP)
        picked = OnboardingRules.toggleObstacle(picked, Obstacle.NONE)
        assertThat(picked).containsExactly(Obstacle.NONE)
        picked = OnboardingRules.toggleObstacle(picked, Obstacle.PROCRASTINATION)
        assertThat(picked).containsExactly(Obstacle.PROCRASTINATION)
        assertThat(OnboardingRules.toggleObstacle(picked, Obstacle.PROCRASTINATION)).isEmpty()
    }

    @Test
    fun `improve keeps the order of the picks`() {
        var picked = OnboardingRules.toggleImprove(emptyList(), ImproveGoal.SLEEP)
        picked = OnboardingRules.toggleImprove(picked, ImproveGoal.FOCUS)
        assertThat(picked).containsExactly(ImproveGoal.SLEEP, ImproveGoal.FOCUS).inOrder()
        assertThat(OnboardingRules.toggleImprove(picked, ImproveGoal.SLEEP)).containsExactly(ImproveGoal.FOCUS)
    }

    @Test
    fun `vector 150 the progress bar has five segments and only moves forward`() {
        assertThat(OnboardingStep.SEGMENTS).isEqualTo(5)
        val values = OnboardingStep.entries.map { it.progress }
        assertThat(values).isInOrder()
        assertThat(values.first()).isGreaterThan(0f)
        assertThat(values.last()).isEqualTo(5f)
        // The last screen of each group fills its segment exactly.
        assertThat(OnboardingStep.BODY_SHAPE.progress).isEqualTo(1f)
        assertThat(OnboardingStep.SCREEN_TIME.progress).isEqualTo(2f)
        assertThat(OnboardingStep.PACE.progress).isEqualTo(3f)
        assertThat(OnboardingStep.NOTIFICATIONS.progress).isEqualTo(4f)
    }

    @Test
    fun `only the name cannot be skipped among the questions`() {
        val unskippable = OnboardingStep.entries.filter { !it.skippable }
        assertThat(unskippable).containsExactly(
            OnboardingStep.WELCOME, OnboardingStep.NAME, OnboardingStep.BUILDING, OnboardingStep.PLAN,
        )
    }

    @Test
    fun `first and building screens have no back`() {
        assertThat(OnboardingStep.entries.filter { !it.canGoBack }).containsExactly(OnboardingStep.WELCOME, OnboardingStep.BUILDING)
        assertThat(OnboardingStep.WELCOME.previous).isEqualTo(OnboardingStep.WELCOME)
        assertThat(OnboardingStep.PLAN.next).isEqualTo(OnboardingStep.PLAN)
    }

    @Test
    fun `continue is gated by each step's rule`() {
        val blank = OnboardingAnswers()
        assertThat(OnboardingRules.canContinue(OnboardingStep.NAME, blank.copy(name = "Al"))).isFalse()
        assertThat(OnboardingRules.canContinue(OnboardingStep.NAME, blank.copy(name = "  Sam "))).isTrue()
        assertThat(OnboardingRules.canContinue(OnboardingStep.GENDER, blank)).isFalse()
        assertThat(OnboardingRules.canContinue(OnboardingStep.GENDER, blank.copy(illustrationSet = IllustrationSet.NEUTRAL))).isTrue()
        assertThat(OnboardingRules.canContinue(OnboardingStep.IMPROVE, blank)).isFalse()
        assertThat(OnboardingRules.canContinue(OnboardingStep.OBSTACLES, blank)).isFalse()
        assertThat(OnboardingRules.canContinue(OnboardingStep.AGE, blank)).isTrue()
        assertThat(OnboardingRules.canContinue(OnboardingStep.WELCOME, blank)).isTrue()
    }

    @Test
    fun `continuing a picker untouched answers it with the starting value`() {
        val blank = OnboardingAnswers()
        assertThat(OnboardingRules.withShownDefaults(OnboardingStep.AGE, blank).age).isEqualTo(25)
        assertThat(OnboardingRules.withShownDefaults(OnboardingStep.HEIGHT, blank).heightCm).isEqualTo(170.0)
        assertThat(OnboardingRules.withShownDefaults(OnboardingStep.WEIGHT, blank).weightKg).isEqualTo(70.0)
        assertThat(OnboardingRules.withShownDefaults(OnboardingStep.BODY_SHAPE, blank).bodyStage).isEqualTo(3)
        // An answer already given is kept.
        assertThat(OnboardingRules.withShownDefaults(OnboardingStep.AGE, blank.copy(age = 31)).age).isEqualTo(31)
        assertThat(OnboardingRules.withShownDefaults(OnboardingStep.NAME, blank)).isEqualTo(blank)
    }

    @Test
    fun `skipping forgets that step's answer and leaves the rest`() {
        val answers = OnboardingAnswers(
            name = "Sam", age = 30, heightCm = 180.0, improve = listOf(ImproveGoal.SLEEP), obstacles = setOf(Obstacle.POOR_SLEEP),
        )
        assertThat(OnboardingRules.skipped(OnboardingStep.AGE, answers)).isEqualTo(answers.copy(age = null))
        assertThat(OnboardingRules.skipped(OnboardingStep.IMPROVE, answers).improve).isEmpty()
        assertThat(OnboardingRules.skipped(OnboardingStep.OBSTACLES, answers).obstacles).isEmpty()
        assertThat(OnboardingRules.skipped(OnboardingStep.NAME, answers)).isEqualTo(answers)
    }
}
