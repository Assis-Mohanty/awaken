package com.assismohanty.awaken.engine.onboarding

import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.FocusGoalRules
import com.assismohanty.awaken.engine.StepGoalRules
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnboardingMappingTest {

    private val blank = OnboardingAnswers()

    /** 23:00 to 07:00 is 8 hours of sleep. */
    private val eightHours = blank.copy(bedtimeMinute = 23 * 60, wakeMinute = 7 * 60)

    @Test
    fun `vector 151 baseline tiers from fitness and sleep at a balanced pace`() {
        val tiers = OnboardingMapping.baselineTiers(eightHours.copy(fitness = FitnessLevel.BEGINNER, pace = Pace.BALANCED))
        assertThat(tiers).containsExactly(
            Stat.STR, Difficulty.EASY, Stat.INT, Difficulty.NORMAL, Stat.WIL, Difficulty.EASY,
            Stat.VIT, Difficulty.HARD, Stat.CHA, Difficulty.EASY,
        )
    }

    @Test
    fun `vector 152 pace shifts every stat and clamps to Easy through Hard`() {
        val answers = eightHours.copy(fitness = FitnessLevel.BEGINNER)
        val push = OnboardingMapping.baselineTiers(answers.copy(pace = Pace.PUSH_HARD))
        assertThat(push).containsExactly(
            Stat.STR, Difficulty.NORMAL, Stat.INT, Difficulty.HARD, Stat.WIL, Difficulty.NORMAL,
            // Already Hard: stays Hard, never Extreme.
            Stat.VIT, Difficulty.HARD, Stat.CHA, Difficulty.NORMAL,
        )
        val steady = OnboardingMapping.baselineTiers(answers.copy(fitness = FitnessLevel.INTERMEDIATE, pace = Pace.STEADY))
        assertThat(steady).containsExactly(
            Stat.STR, Difficulty.EASY, Stat.INT, Difficulty.EASY, Stat.WIL, Difficulty.EASY,
            Stat.VIT, Difficulty.NORMAL, Stat.CHA, Difficulty.EASY,
        )
    }

    @Test
    fun `vector 153 skipped fitness or sleep is Normal`() {
        val tiers = OnboardingMapping.baselineTiers(blank.copy(pace = Pace.BALANCED))
        assertThat(tiers.getValue(Stat.STR)).isEqualTo(Difficulty.NORMAL)
        assertThat(tiers.getValue(Stat.VIT)).isEqualTo(Difficulty.NORMAL)
        assertThat(OnboardingMapping.baselineTiers(blank)).isEqualTo(tiers)
    }

    @Test
    fun `sleep length picks the vitality tier at 6 and 7 hours`() {
        fun vit(hours: Double): Difficulty {
            val bed = 23 * 60
            return OnboardingMapping.baselineTiers(blank.copy(bedtimeMinute = bed, wakeMinute = (bed + (hours * 60).toInt()) % (24 * 60)))
                .getValue(Stat.VIT)
        }
        assertThat(vit(5.75)).isEqualTo(Difficulty.EASY)
        assertThat(vit(6.0)).isEqualTo(Difficulty.NORMAL)
        assertThat(vit(6.75)).isEqualTo(Difficulty.NORMAL)
        assertThat(vit(7.0)).isEqualTo(Difficulty.HARD)
    }

    @Test
    fun `vector 154 step goal from fitness and sitting`() {
        assertThat(OnboardingMapping.stepGoal(blank.copy(fitness = FitnessLevel.BEGINNER))).isEqualTo(6_000)
        assertThat(OnboardingMapping.stepGoal(blank.copy(fitness = FitnessLevel.INTERMEDIATE))).isEqualTo(8_000)
        assertThat(OnboardingMapping.stepGoal(blank.copy(fitness = FitnessLevel.ADVANCED))).isEqualTo(10_000)
        assertThat(OnboardingMapping.stepGoal(blank)).isEqualTo(8_000)
        val sitting = setOf(Obstacle.SITTING_ALL_DAY)
        assertThat(OnboardingMapping.stepGoal(blank.copy(fitness = FitnessLevel.ADVANCED, obstacles = sitting))).isEqualTo(8_000)
        assertThat(OnboardingMapping.stepGoal(blank.copy(fitness = FitnessLevel.BEGINNER, obstacles = sitting))).isEqualTo(4_000)
    }

    @Test
    fun `every step goal is a valid goal`() {
        for (level in FitnessLevel.entries + null) {
            for (obstacles in listOf(emptySet(), setOf(Obstacle.SITTING_ALL_DAY))) {
                assertThat(StepGoalRules.isValidGoal(OnboardingMapping.stepGoal(blank.copy(fitness = level, obstacles = obstacles)))).isTrue()
            }
        }
    }

    @Test
    fun `vector 155 focus goal from pace and procrastination`() {
        assertThat(OnboardingMapping.focusGoalMinutes(blank.copy(pace = Pace.STEADY))).isEqualTo(60)
        assertThat(OnboardingMapping.focusGoalMinutes(blank.copy(pace = Pace.BALANCED))).isEqualTo(120)
        assertThat(OnboardingMapping.focusGoalMinutes(blank.copy(pace = Pace.PUSH_HARD))).isEqualTo(180)
        assertThat(OnboardingMapping.focusGoalMinutes(blank)).isEqualTo(FocusGoalRules.DEFAULT_GOAL)
        val procrastination = setOf(Obstacle.PROCRASTINATION)
        assertThat(OnboardingMapping.focusGoalMinutes(blank.copy(pace = Pace.STEADY, obstacles = procrastination))).isEqualTo(30)
        assertThat(OnboardingMapping.focusGoalMinutes(blank.copy(pace = Pace.BALANCED, obstacles = procrastination))).isEqualTo(60)
        assertThat(OnboardingMapping.focusGoalMinutes(blank.copy(pace = Pace.PUSH_HARD, obstacles = procrastination))).isEqualTo(90)
    }

    @Test
    fun `every focus goal is a valid goal`() {
        for (pace in Pace.entries + null) {
            for (obstacles in listOf(emptySet(), setOf(Obstacle.PROCRASTINATION))) {
                assertThat(FocusGoalRules.isValidGoal(OnboardingMapping.focusGoalMinutes(blank.copy(pace = pace, obstacles = obstacles)))).isTrue()
            }
        }
    }

    @Test
    fun `vector 156 app limit by daily screen time`() {
        assertThat(OnboardingMapping.appLimitMinutes(ScreenTimeBand.UNDER_2H)).isEqualTo(30)
        assertThat(OnboardingMapping.appLimitMinutes(ScreenTimeBand.H2_TO_4)).isEqualTo(45)
        assertThat(OnboardingMapping.appLimitMinutes(ScreenTimeBand.H4_TO_6)).isEqualTo(60)
        assertThat(OnboardingMapping.appLimitMinutes(ScreenTimeBand.OVER_6H)).isEqualTo(90)
        assertThat(OnboardingMapping.appLimitMinutes(null)).isEqualTo(30)
    }

    @Test
    fun `vector 157 rep range by fitness level`() {
        assertThat(OnboardingMapping.repRange(FitnessLevel.BEGINNER)).isEqualTo(RepBand(10, 15))
        assertThat(OnboardingMapping.repRange(FitnessLevel.INTERMEDIATE)).isEqualTo(RepBand(8, 12))
        assertThat(OnboardingMapping.repRange(FitnessLevel.ADVANCED)).isEqualTo(RepBand(6, 10))
        assertThat(OnboardingMapping.repRange(null)).isEqualTo(RepBand(8, 12))
    }

    @Test
    fun `vector 158 equipment keys`() {
        assertThat(OnboardingMapping.ownedEquipment(EquipmentChoice.BODYWEIGHT)).isEmpty()
        assertThat(OnboardingMapping.ownedEquipment(null)).isEmpty()
        assertThat(OnboardingMapping.ownedEquipment(EquipmentChoice.DUMBBELLS)).containsExactly("dumbbells")
        assertThat(OnboardingMapping.ownedEquipment(EquipmentChoice.GYM))
            .containsExactly("gym_access", "dumbbells", "kettlebell", "pullup_bar", "resistance_band")
    }

    @Test
    fun `vector 159 injury tags and mode`() {
        assertThat(OnboardingMapping.injuryTags(setOf(InjuryRegion.SHOULDERS, InjuryRegion.KNEE))).containsExactly("upper", "legs", "impact")
        assertThat(OnboardingMapping.injuryTags(setOf(InjuryRegion.WRIST))).containsExactly("upper")
        assertThat(OnboardingMapping.injuryTags(setOf(InjuryRegion.BACK))).containsExactly("impact")
        assertThat(OnboardingMapping.injuryTags(setOf(InjuryRegion.HIP))).containsExactly("legs", "impact")
        assertThat(OnboardingMapping.injuryTags(setOf(InjuryRegion.NO_INJURIES))).isEmpty()
        assertThat(OnboardingMapping.injuryTags(emptySet())).isEmpty()
        assertThat(OnboardingMapping.injuryMode(setOf(InjuryRegion.KNEE))).isTrue()
        assertThat(OnboardingMapping.injuryMode(setOf(InjuryRegion.NO_INJURIES))).isFalse()
        assertThat(OnboardingMapping.injuryMode(emptySet())).isFalse()
    }

    @Test
    fun `every injury tag is one the quest generator knows`() {
        val known = com.assismohanty.awaken.engine.DirectiveGenerator.INJURY_TAGS.toSet()
        val all = InjuryRegion.entries.toSet()
        assertThat(known).containsAtLeastElementsIn(OnboardingMapping.injuryTags(all))
    }

    @Test
    fun `vector 160 body map regions`() {
        assertThat(OnboardingMapping.injuryMuscles(InjuryRegion.SHOULDERS)).containsExactly(Muscle.SHOULDERS)
        assertThat(OnboardingMapping.injuryMuscles(InjuryRegion.BACK)).containsExactly(Muscle.LOWER_BACK, Muscle.MIDDLE_BACK)
        assertThat(OnboardingMapping.injuryMuscles(InjuryRegion.WRIST)).containsExactly(Muscle.FOREARMS)
        assertThat(OnboardingMapping.injuryMuscles(InjuryRegion.KNEE)).containsExactly(Muscle.QUADRICEPS)
        assertThat(OnboardingMapping.injuryMuscles(InjuryRegion.HIP)).containsExactly(Muscle.GLUTES)
        assertThat(OnboardingMapping.injuryMuscles(InjuryRegion.NO_INJURIES)).isEmpty()
    }

    @Test
    fun `vector 161 No injuries is exclusive`() {
        var picked = OnboardingRules.toggleInjury(emptySet(), InjuryRegion.KNEE)
        picked = OnboardingRules.toggleInjury(picked, InjuryRegion.BACK)
        assertThat(picked).containsExactly(InjuryRegion.KNEE, InjuryRegion.BACK)
        picked = OnboardingRules.toggleInjury(picked, InjuryRegion.NO_INJURIES)
        assertThat(picked).containsExactly(InjuryRegion.NO_INJURIES)
        picked = OnboardingRules.toggleInjury(picked, InjuryRegion.HIP)
        assertThat(picked).containsExactly(InjuryRegion.HIP)
    }

    @Test
    fun `training days toggle by ISO number`() {
        var days = OnboardingRules.toggleDay(emptySet(), 1)
        days = OnboardingRules.toggleDay(days, 3)
        assertThat(days).containsExactly(1, 3)
        assertThat(OnboardingRules.toggleDay(days, 1)).containsExactly(3)
    }

    @Test
    fun `vector 162 sleep defaults and a wake time that stops fitting`() {
        assertThat(OnboardingRules.sleepShown(null, null, 4)).isEqualTo(BedtimeRules.DEFAULT_BEDTIME to BedtimeRules.DEFAULT_WAKE)
        // A 02:00 bedtime with the default 06:30 wake is 4.5 hours, under the 5-hour minimum: the wake moves to 07:00.
        assertThat(OnboardingRules.sleepShown(2 * 60, BedtimeRules.DEFAULT_WAKE, 4)).isEqualTo(2 * 60 to 7 * 60)
        val untouched = OnboardingRules.withShownDefaults(OnboardingStep.SLEEP, blank)
        assertThat(untouched.bedtimeMinute).isEqualTo(BedtimeRules.DEFAULT_BEDTIME)
        assertThat(untouched.wakeMinute).isEqualTo(BedtimeRules.DEFAULT_WAKE)
    }
}
