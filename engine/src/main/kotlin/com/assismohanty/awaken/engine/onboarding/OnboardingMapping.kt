package com.assismohanty.awaken.engine.onboarding

import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.FocusGoalRules
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.StepGoalRules
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat

/** A rep range for progression hints (GD §16.6): lowest and highest reps. */
data class RepBand(val min: Int, val max: Int)

/**
 * What each answer sets (GD §17). Pure functions of the answers: the plan on the last screen, and the settings written at
 * Start, both read from here. Nothing is computed twice.
 */
object OnboardingMapping {

    /** OB2: sleep windows under 6 h are Easy, 6 to under 7 h Normal, 7 h or more Hard. */
    private const val SLEEP_NORMAL_FROM_MINUTES = 6 * 60
    private const val SLEEP_HARD_FROM_MINUTES = 7 * 60

    /** OB2: baseline tier per stat, shifted by pace and clamped to Easy..Hard. */
    fun baselineTiers(answers: OnboardingAnswers): Map<Stat, Difficulty> {
        val shift = when (answers.pace) {
            Pace.STEADY -> -1
            Pace.PUSH_HARD -> 1
            Pace.BALANCED, null -> 0
        }
        val strength = when (answers.fitness) {
            FitnessLevel.BEGINNER -> Difficulty.EASY
            FitnessLevel.INTERMEDIATE -> Difficulty.NORMAL
            FitnessLevel.ADVANCED -> Difficulty.HARD
            null -> Difficulty.NORMAL
        }
        val vitality = sleepMinutes(answers)?.let { minutes ->
            when {
                minutes >= SLEEP_HARD_FROM_MINUTES -> Difficulty.HARD
                minutes >= SLEEP_NORMAL_FROM_MINUTES -> Difficulty.NORMAL
                else -> Difficulty.EASY
            }
        } ?: Difficulty.NORMAL
        return mapOf(
            Stat.STR to strength,
            Stat.INT to Difficulty.NORMAL,
            Stat.WIL to Difficulty.EASY,
            Stat.VIT to vitality,
            Stat.CHA to Difficulty.EASY,
        ).mapValues { (_, tier) -> shifted(tier, shift) }
    }

    private fun sleepMinutes(answers: OnboardingAnswers): Int? {
        val bed = answers.bedtimeMinute ?: return null
        val wake = answers.wakeMinute ?: return null
        return BedtimeRules.windowMinutes(bed, wake)
    }

    private fun shifted(tier: Difficulty, delta: Int): Difficulty =
        Difficulty.entries[(tier.ordinal + delta).coerceIn(Difficulty.EASY.ordinal, Difficulty.HARD.ordinal)]

    /** OB3: Beginner 6,000, Intermediate 8,000 (`N19`), Advanced 10,000; "Sitting all day" takes off 2,000. Skipped: 8,000. */
    fun stepGoal(answers: OnboardingAnswers): Int {
        val base = when (answers.fitness) {
            FitnessLevel.BEGINNER -> 6_000
            FitnessLevel.ADVANCED -> 10_000
            FitnessLevel.INTERMEDIATE, null -> StepGoalRules.DEFAULT_GOAL
        }
        val sitting = if (Obstacle.SITTING_ALL_DAY in answers.obstacles) 2_000 else 0
        return StepGoalRules.clampGoal(base - sitting)
    }

    /** OB4: Steady 60, Balanced 120 (`N11`), Push hard 180 minutes; "Procrastination" halves it. Skipped: 120. */
    fun focusGoalMinutes(answers: OnboardingAnswers): Int {
        val base = when (answers.pace) {
            Pace.STEADY -> 60
            Pace.PUSH_HARD -> 180
            Pace.BALANCED, null -> FocusGoalRules.DEFAULT_GOAL
        }
        return if (Obstacle.PROCRASTINATION in answers.obstacles) base / 2 else base
    }

    /** OB5: the limit for each seeded Distracting app, from the daily screen time band. No answer keeps the app default. */
    fun appLimitMinutes(band: ScreenTimeBand?): Int = when (band) {
        ScreenTimeBand.UNDER_2H, null -> ScreenTimeRules.DEFAULT_LIMIT_MINUTES
        ScreenTimeBand.H2_TO_4 -> 45
        ScreenTimeBand.H4_TO_6 -> 60
        ScreenTimeBand.OVER_6H -> 90
    }

    /** OB6: Beginner 10-15, Intermediate 8-12 (`N14`), Advanced 6-10. */
    fun repRange(level: FitnessLevel?): RepBand = when (level) {
        FitnessLevel.BEGINNER -> RepBand(10, 15)
        FitnessLevel.ADVANCED -> RepBand(6, 10)
        FitnessLevel.INTERMEDIATE, null -> RepBand(8, 12)
    }

    /** OB8: the quest equipment keys the choice owns. */
    fun ownedEquipment(choice: EquipmentChoice?): Set<String> = when (choice) {
        EquipmentChoice.DUMBBELLS -> setOf("dumbbells")
        EquipmentChoice.GYM -> setOf("gym_access", "dumbbells", "kettlebell", "pullup_bar", "resistance_band")
        EquipmentChoice.BODYWEIGHT, null -> emptySet()
    }

    /** OB9: tags the quest generator avoids for these regions. Empty when there is nothing to avoid. */
    fun injuryTags(regions: Set<InjuryRegion>): Set<String> = regions.flatMapTo(mutableSetOf()) { region ->
        when (region) {
            InjuryRegion.SHOULDERS, InjuryRegion.WRIST -> listOf("upper")
            InjuryRegion.BACK -> listOf("impact")
            InjuryRegion.KNEE, InjuryRegion.HIP -> listOf("legs", "impact")
            InjuryRegion.NO_INJURIES -> emptyList()
        }
    }

    /** OB9: any region at all turns Injury Mode on. */
    fun injuryMode(regions: Set<InjuryRegion>): Boolean = regions.any { it != InjuryRegion.NO_INJURIES }

    /** OB12: the muscles to light up on the body map for a region. Display only. */
    fun injuryMuscles(region: InjuryRegion): Set<Muscle> = when (region) {
        InjuryRegion.SHOULDERS -> setOf(Muscle.SHOULDERS)
        InjuryRegion.BACK -> setOf(Muscle.LOWER_BACK, Muscle.MIDDLE_BACK)
        InjuryRegion.WRIST -> setOf(Muscle.FOREARMS)
        InjuryRegion.KNEE -> setOf(Muscle.QUADRICEPS)
        InjuryRegion.HIP -> setOf(Muscle.GLUTES)
        InjuryRegion.NO_INJURIES -> emptySet()
    }
}
