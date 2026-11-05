package com.assismohanty.awaken.engine.onboarding

import com.assismohanty.awaken.engine.body.WeightGoal
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class OnboardingPlannerTest {

    private val today = LocalDate.of(2026, 11, 5)
    private val none = OnboardingGrants()
    private val usage = OnboardingGrants(usageAccess = true)
    private val blank = OnboardingAnswers()

    private fun plan(answers: OnboardingAnswers, grants: OnboardingGrants = none) = OnboardingPlanner.plan(answers, grants, today)

    @Test
    fun `vector 163 a person who skipped everything gets the defaults`() {
        val p = plan(blank)
        assertThat(p.focusStats).containsExactly(Stat.STR, Stat.INT).inOrder()
        assertThat(p.stepGoal).isEqualTo(8_000)
        assertThat(p.focusGoalMinutes).isEqualTo(120)
        assertThat(p.ownedEquipment).isEmpty()
        assertThat(p.injuryMode).isFalse()
        assertThat(p.injuryTags).isEmpty()
        assertThat(p.repRange).isEqualTo(RepBand(8, 12))
        assertThat(p.trainingDays).isEmpty()
        assertThat(p.trainingReminder).isFalse()
        assertThat(p.discipline).isNull()
        assertThat(p.phoneDown).isNull()
        assertThat(p.needsUsageAccess).isFalse()
        assertThat(p.suggestReelCounter).isFalse()
        assertThat(p.healthEnabled).isFalse()
        assertThat(p.reminderMinute).isEqualTo(420)
        assertThat(p.birthYear).isNull()
        assertThat(p.dietGoalHint).isNull()
        assertThat(p.tiers.getValue(Stat.STR)).isEqualTo(Difficulty.NORMAL)
    }

    @Test
    fun `vector 164 screen discipline needs both an answer and usage access`() {
        val answered = blank.copy(screenTime = ScreenTimeBand.H4_TO_6)
        assertThat(plan(answered, usage).discipline).isEqualTo(DisciplinePlan(60))
        assertThat(plan(answered, none).discipline).isNull()
        // Granted but never asked: nothing is switched on.
        assertThat(plan(blank, usage).discipline).isNull()
    }

    @Test
    fun `vector 165 asking for something that needs usage access says so when it is missing`() {
        assertThat(plan(blank.copy(screenTime = ScreenTimeBand.UNDER_2H), none).needsUsageAccess).isTrue()
        assertThat(plan(blank.copy(obstacles = setOf(Obstacle.POOR_SLEEP)), none).needsUsageAccess).isTrue()
        assertThat(plan(blank.copy(screenTime = ScreenTimeBand.UNDER_2H), usage).needsUsageAccess).isFalse()
        assertThat(plan(blank, none).needsUsageAccess).isFalse()
    }

    @Test
    fun `vector 166 phone-down nights need poor sleep and usage access, with the chosen or default window`() {
        val poor = blank.copy(obstacles = setOf(Obstacle.POOR_SLEEP))
        assertThat(plan(poor, usage).phoneDown).isEqualTo(PhoneDownPlan(23 * 60, 6 * 60 + 30))
        assertThat(plan(poor.copy(bedtimeMinute = 22 * 60, wakeMinute = 6 * 60), usage).phoneDown).isEqualTo(PhoneDownPlan(22 * 60, 6 * 60))
        assertThat(plan(poor, none).phoneDown).isNull()
        assertThat(plan(blank.copy(obstacles = setOf(Obstacle.PROCRASTINATION)), usage).phoneDown).isNull()
    }

    @Test
    fun `every window the plan writes is one phone-down nights accept`() {
        val poor = blank.copy(obstacles = setOf(Obstacle.POOR_SLEEP))
        // A 02:00 bedtime with a 06:30 wake would be too short; the plan moves the wake time.
        val p = plan(poor.copy(bedtimeMinute = 2 * 60, wakeMinute = 6 * 60 + 30), usage)
        assertThat(OnboardingPlanner.isWritable(p)).isTrue()
        assertThat(p.phoneDown).isEqualTo(PhoneDownPlan(2 * 60, 7 * 60))
    }

    @Test
    fun `vector 167 Too much scrolling suggests the reel counter and never switches it on`() {
        val p = plan(blank.copy(obstacles = setOf(Obstacle.TOO_MUCH_SCROLLING)))
        assertThat(p.suggestReelCounter).isTrue()
    }

    @Test
    fun `vector 168 health follows the permission`() {
        assertThat(plan(blank, OnboardingGrants(healthConnected = true)).healthEnabled).isTrue()
        assertThat(plan(blank, none).healthEnabled).isFalse()
    }

    @Test
    fun `vector 169 age becomes a birth year`() {
        assertThat(plan(blank.copy(age = 30)).birthYear).isEqualTo(1996)
    }

    @Test
    fun `vector 170 a full set of answers feeds every setting`() {
        val answers = blank.copy(
            improve = listOf(ImproveGoal.SLEEP, ImproveGoal.FOCUS),
            obstacles = setOf(Obstacle.SITTING_ALL_DAY, Obstacle.PROCRASTINATION),
            screenTime = ScreenTimeBand.OVER_6H, fitness = FitnessLevel.BEGINNER, equipment = EquipmentChoice.GYM,
            injuries = setOf(InjuryRegion.KNEE), trainingDays = setOf(1, 3, 5), trainingReminder = true, pace = Pace.PUSH_HARD,
            bodyStage = 5, weightKg = 90.0, age = 28, heightCm = 180.0,
        )
        val p = plan(answers, OnboardingGrants(usageAccess = true, healthConnected = true))
        assertThat(p.focusStats).containsExactly(Stat.VIT, Stat.WIL).inOrder()
        assertThat(p.stepGoal).isEqualTo(4_000)
        assertThat(p.focusGoalMinutes).isEqualTo(90)
        assertThat(p.discipline).isEqualTo(DisciplinePlan(90))
        assertThat(p.ownedEquipment).contains("gym_access")
        assertThat(p.injuryMode).isTrue()
        assertThat(p.injuryTags).containsExactly("legs", "impact")
        assertThat(p.repRange).isEqualTo(RepBand(10, 15))
        assertThat(p.trainingDays).containsExactly(1, 3, 5)
        assertThat(p.trainingReminder).isTrue()
        assertThat(p.healthEnabled).isTrue()
        assertThat(p.dietGoalHint).isEqualTo(WeightGoal.LOSE)
        assertThat(p.heightCm).isEqualTo(180.0)
    }

    @Test
    fun `a reminder with no training days is dropped`() {
        assertThat(plan(blank.copy(trainingReminder = true)).trainingReminder).isFalse()
        assertThat(plan(blank.copy(trainingReminder = true, edits = PlanEdits(trainingDays = setOf(2)))).trainingReminder).isTrue()
    }

    @Test
    fun `vector 171 edits win over what the answers set`() {
        val answers = blank.copy(
            improve = listOf(ImproveGoal.SLEEP), screenTime = ScreenTimeBand.UNDER_2H, trainingDays = setOf(1),
            edits = PlanEdits(
                focusStats = listOf(Stat.CHA, Stat.STR), stepGoal = 12_000, focusGoalMinutes = 45, appLimitMinutes = 20,
                trainingDays = setOf(2, 4),
            ),
        )
        val p = plan(answers, usage)
        assertThat(p.focusStats).containsExactly(Stat.CHA, Stat.STR).inOrder()
        assertThat(p.stepGoal).isEqualTo(12_000)
        assertThat(p.focusGoalMinutes).isEqualTo(45)
        assertThat(p.discipline).isEqualTo(DisciplinePlan(20))
        assertThat(p.trainingDays).containsExactly(2, 4)
    }

    @Test
    fun `vector 172 edited numbers snap to valid values and bad focus stats are ignored`() {
        val answers = blank.copy(
            screenTime = ScreenTimeBand.UNDER_2H,
            edits = PlanEdits(focusStats = listOf(Stat.STR, Stat.STR), stepGoal = 12_345, focusGoalMinutes = 47, appLimitMinutes = 3),
        )
        val p = plan(answers, usage)
        // Two identical stats are not a valid pick, so the answers' own result stands.
        assertThat(p.focusStats).containsExactly(Stat.STR, Stat.INT).inOrder()
        assertThat(p.stepGoal).isEqualTo(12_000)
        assertThat(p.focusGoalMinutes).isEqualTo(45)
        assertThat(p.discipline).isEqualTo(DisciplinePlan(5))
    }

    // --- Target weight (OB17, OB18) ---

    private val adult = blank.copy(age = 30, heightCm = 170.0, weightKg = 80.0)

    @Test
    fun `vector 173 the target is not offered under 18 or without a height and weight`() {
        assertThat(WeightTargetRules.isOffered(30, 170.0, 80.0)).isTrue()
        assertThat(WeightTargetRules.isOffered(18, 170.0, 80.0)).isTrue()
        assertThat(WeightTargetRules.isOffered(17, 170.0, 80.0)).isFalse()
        assertThat(WeightTargetRules.isOffered(null, 170.0, 80.0)).isFalse()
        assertThat(WeightTargetRules.isOffered(30, null, 80.0)).isFalse()
        assertThat(WeightTargetRules.isOffered(30, 170.0, null)).isFalse()
        val target = WeightTargetRules.target(70.0, 8, today)
        assertThat(WeightTargetRules.problems(target, blank.copy(age = 16, heightCm = 170.0, weightKg = 60.0), today))
            .containsExactly(TargetProblem.NOT_OFFERED)
    }

    @Test
    fun `vector 174 the date must be at least four weeks away`() {
        assertThat(WeightTargetRules.problems(WeightTargetRules.target(70.0, 4, today), adult, today)).isEmpty()
        assertThat(WeightTargetRules.problems(WeightTarget(70.0, today.plusWeeks(3).toString()), adult, today))
            .containsExactly(TargetProblem.DATE_TOO_SOON)
        assertThat(WeightTargetRules.problems(WeightTarget(70.0, "soon"), adult, today)).containsExactly(TargetProblem.DATE_TOO_SOON)
    }

    @Test
    fun `vector 175 the weight must be in range and not below BMI 18_5 for the height`() {
        // 170 cm: BMI 18.5 is 53.5 kg.
        assertThat(WeightTargetRules.problems(WeightTargetRules.target(53.5, 8, today), adult, today)).isEmpty()
        assertThat(WeightTargetRules.problems(WeightTargetRules.target(53.4, 8, today), adult, today))
            .containsExactly(TargetProblem.BELOW_HEALTHY_FLOOR)
        assertThat(WeightTargetRules.problems(WeightTargetRules.target(20.0, 8, today), adult, today))
            .containsExactly(TargetProblem.WEIGHT_OUT_OF_RANGE)
        assertThat(WeightTargetRules.problems(WeightTargetRules.target(400.0, 8, today), adult, today))
            .containsExactly(TargetProblem.WEIGHT_OUT_OF_RANGE)
        assertThat(WeightTargetRules.allowedRangeKg(170.0).start).isEqualTo(53.5)
    }

    @Test
    fun `vector 176 the estimate is a smooth monotone curve between the two numbers`() {
        for ((start, end) in listOf(80.0 to 70.0, 60.0 to 66.0)) {
            val curve = WeightTargetRules.curve(start, end)
            assertThat(curve).hasSize(WeightTargetRules.CURVE_SAMPLES)
            assertThat(curve.first()).isEqualTo(start)
            assertThat(curve.last()).isWithin(1e-9).of(end)
            val sorted = if (end < start) curve.sortedDescending() else curve.sorted()
            assertThat(curve).isEqualTo(sorted)
            // Never beyond the two numbers.
            assertThat(curve.all { it in minOf(start, end)..maxOf(start, end) }).isTrue()
        }
        assertThat(WeightTargetRules.curve(70.0, 70.0).toSet()).containsExactly(70.0)
    }

    @Test
    fun `the plan picks up a target for the Diet hint`() {
        val target = WeightTargetRules.target(70.0, 8, today)
        assertThat(plan(adult.copy(bodyStage = 1, target = target)).dietGoalHint).isEqualTo(WeightGoal.LOSE)
    }
}
