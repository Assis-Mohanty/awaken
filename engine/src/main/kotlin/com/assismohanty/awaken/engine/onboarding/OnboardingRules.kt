package com.assismohanty.awaken.engine.onboarding

import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.body.WeightGoal
import com.assismohanty.awaken.engine.model.Stat

/** The five segments of the progress bar. */
enum class OnboardingGroup { YOU, GOALS, DAILY_LIFE, PHONE, PLAN }

/**
 * The screens of the quiz flow, in order (docs/onboarding/PLAN.md §4). [skippable] steps show Skip; [canGoBack] is false
 * where going back makes no sense (the first screen, and the plan being built).
 */
enum class OnboardingStep(
    val group: OnboardingGroup,
    val skippable: Boolean = true,
    val canGoBack: Boolean = true,
) {
    WELCOME(OnboardingGroup.YOU, skippable = false, canGoBack = false),
    NAME(OnboardingGroup.YOU, skippable = false),
    GENDER(OnboardingGroup.YOU),
    AGE(OnboardingGroup.YOU),
    HEIGHT(OnboardingGroup.YOU),
    WEIGHT(OnboardingGroup.YOU),
    BODY_SHAPE(OnboardingGroup.YOU),
    IMPROVE(OnboardingGroup.GOALS),
    OBSTACLES(OnboardingGroup.GOALS),
    SCREEN_TIME(OnboardingGroup.GOALS),
    SLEEP(OnboardingGroup.DAILY_LIFE),
    FITNESS(OnboardingGroup.DAILY_LIFE),
    EQUIPMENT(OnboardingGroup.DAILY_LIFE),
    INJURIES(OnboardingGroup.DAILY_LIFE),
    TRAINING_DAYS(OnboardingGroup.DAILY_LIFE),
    PACE(OnboardingGroup.DAILY_LIFE),
    HEALTH(OnboardingGroup.PHONE),
    USAGE(OnboardingGroup.PHONE),
    NOTIFICATIONS(OnboardingGroup.PHONE),
    BUILDING(OnboardingGroup.PLAN, skippable = false, canGoBack = false),
    PLAN(OnboardingGroup.PLAN, skippable = false),
    ;

    val next: OnboardingStep get() = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
    val previous: OnboardingStep get() = entries[(ordinal - 1).coerceAtLeast(0)]

    /** Progress bar value, 0..[SEGMENTS]: the finished groups plus how far through this one the step is. */
    val progress: Float
        get() {
            val inGroup = entries.filter { it.group == group }
            return group.ordinal + (inGroup.indexOf(this) + 1).toFloat() / inGroup.size
        }

    companion object {
        val SEGMENTS: Int = OnboardingGroup.entries.size
    }
}

/** What each screen may do with the answers so far. Pure: the ViewModel applies the results. */
object OnboardingRules {

    /** OB1 */
    private val STAT_FOR_GOAL = mapOf(
        ImproveGoal.STRENGTH to Stat.STR,
        ImproveGoal.FITNESS to Stat.STR,
        ImproveGoal.FOCUS to Stat.WIL,
        ImproveGoal.DISCIPLINE to Stat.WIL,
        ImproveGoal.LESS_SCREEN_TIME to Stat.WIL,
        ImproveGoal.SLEEP to Stat.VIT,
        ImproveGoal.LEARNING to Stat.INT,
    )

    /** OB1: used to fill when fewer than two stats were picked. */
    private val FILL_ORDER = listOf(Stat.STR, Stat.INT, Stat.WIL, Stat.VIT, Stat.CHA)

    /** OB1: the two focus stats for the improve answers (earliest pick wins a tie; short answers are filled in order). */
    fun focusStats(improve: List<ImproveGoal>): List<Stat> {
        val counts = LinkedHashMap<Stat, Int>()
        improve.forEach { goal -> STAT_FOR_GOAL.getValue(goal).let { counts[it] = (counts[it] ?: 0) + 1 } }
        // sortedByDescending is stable, so equal counts keep the order the stats were first picked in.
        val picked = counts.entries.sortedByDescending { it.value }.map { it.key }.take(2)
        return (picked + FILL_ORDER.filter { it !in picked }).take(2)
    }

    /** OB13: the Diet goal to prefill. A target weight wins over the body shape; neither means no hint. */
    fun dietGoalHint(bodyStage: Int?, currentKg: Double?, target: WeightTarget?): WeightGoal? {
        if (target != null && currentKg != null) {
            return when {
                target.weightKg < currentKg -> WeightGoal.LOSE
                target.weightKg > currentKg -> WeightGoal.GAIN
                else -> WeightGoal.MAINTAIN
            }
        }
        return when (bodyStage) {
            null -> null
            1, 2 -> WeightGoal.GAIN
            3 -> WeightGoal.MAINTAIN
            else -> WeightGoal.LOSE
        }
    }

    /** "None" stands alone: picking it clears the others, and picking anything else clears it. */
    fun toggleObstacle(current: Set<Obstacle>, obstacle: Obstacle): Set<Obstacle> = when {
        obstacle in current -> current - obstacle
        obstacle == Obstacle.NONE -> setOf(Obstacle.NONE)
        else -> current - Obstacle.NONE + obstacle
    }

    /** "No injuries" stands alone, like "None" in [toggleObstacle]. */
    fun toggleInjury(current: Set<InjuryRegion>, region: InjuryRegion): Set<InjuryRegion> = when {
        region in current -> current - region
        region == InjuryRegion.NO_INJURIES -> setOf(InjuryRegion.NO_INJURIES)
        else -> current - InjuryRegion.NO_INJURIES + region
    }

    /** ISO day numbers, 1 = Monday ... 7 = Sunday. */
    fun toggleDay(current: Set<Int>, isoDay: Int): Set<Int> {
        require(isoDay in 1..7) { "not an ISO day: $isoDay" }
        return if (isoDay in current) current - isoDay else current + isoDay
    }

    /**
     * The bedtime and wake time to show, and to record when the sleep step is passed untouched (OB10). A wake time that no
     * longer makes a valid window with the bedtime moves to the nearest one that does.
     */
    fun sleepShown(bedtimeMinute: Int?, wakeMinute: Int?, dayStartHour: Int): Pair<Int, Int> {
        val bed = bedtimeMinute ?: BedtimeRules.DEFAULT_BEDTIME
        val wake = wakeMinute ?: BedtimeRules.DEFAULT_WAKE
        return bed to (SleepWheels.fitWake(bed, wake, dayStartHour) ?: wake)
    }

    /** Selection order is kept: it breaks ties in [focusStats]. */
    fun toggleImprove(current: List<ImproveGoal>, goal: ImproveGoal): List<ImproveGoal> =
        if (goal in current) current - goal else current + goal

    /** Whether Continue is enabled on [step] (docs/onboarding/PLAN.md §4). Pickers always hold a value, so they are always valid. */
    fun canContinue(step: OnboardingStep, answers: OnboardingAnswers): Boolean = when (step) {
        OnboardingStep.NAME -> Awakening.isValidName(answers.name)
        OnboardingStep.GENDER -> answers.illustrationSet != null
        OnboardingStep.IMPROVE -> answers.improve.isNotEmpty()
        OnboardingStep.OBSTACLES -> answers.obstacles.isNotEmpty()
        OnboardingStep.SCREEN_TIME -> answers.screenTime != null
        OnboardingStep.FITNESS -> answers.fitness != null
        OnboardingStep.EQUIPMENT -> answers.equipment != null
        OnboardingStep.INJURIES -> answers.injuries.isNotEmpty()
        OnboardingStep.PACE -> answers.pace != null
        OnboardingStep.BUILDING -> false
        else -> true
    }

    /**
     * Continue on a picker step takes the value on screen, so leaving a picker untouched still answers it with the
     * starting value (OB10). Other steps are unchanged.
     */
    fun withShownDefaults(step: OnboardingStep, answers: OnboardingAnswers): OnboardingAnswers = when (step) {
        OnboardingStep.AGE -> answers.copy(age = answers.age ?: BodyMath.DEFAULT_AGE)
        OnboardingStep.HEIGHT -> answers.copy(heightCm = answers.heightCm ?: BodyMath.DEFAULT_HEIGHT_CM)
        OnboardingStep.WEIGHT -> answers.copy(weightKg = answers.weightKg ?: BodyMath.DEFAULT_WEIGHT_KG)
        OnboardingStep.BODY_SHAPE -> answers.copy(bodyStage = answers.bodyStage ?: DEFAULT_BODY_STAGE)
        OnboardingStep.SLEEP -> sleepShown(answers.bedtimeMinute, answers.wakeMinute, GameDay.DEFAULT_DAY_START_HOUR)
            .let { (bed, wake) -> answers.copy(bedtimeMinute = bed, wakeMinute = wake) }
        else -> answers
    }

    /** Skip: forgets whatever [step] had recorded, so a skipped step is the same as one never answered. */
    fun skipped(step: OnboardingStep, answers: OnboardingAnswers): OnboardingAnswers = when (step) {
        OnboardingStep.GENDER -> answers.copy(illustrationSet = null)
        OnboardingStep.AGE -> answers.copy(age = null)
        OnboardingStep.HEIGHT -> answers.copy(heightCm = null)
        OnboardingStep.WEIGHT -> answers.copy(weightKg = null)
        OnboardingStep.BODY_SHAPE -> answers.copy(bodyStage = null)
        OnboardingStep.IMPROVE -> answers.copy(improve = emptyList())
        OnboardingStep.OBSTACLES -> answers.copy(obstacles = emptySet())
        OnboardingStep.SCREEN_TIME -> answers.copy(screenTime = null)
        OnboardingStep.SLEEP -> answers.copy(bedtimeMinute = null, wakeMinute = null)
        OnboardingStep.FITNESS -> answers.copy(fitness = null)
        OnboardingStep.EQUIPMENT -> answers.copy(equipment = null)
        OnboardingStep.INJURIES -> answers.copy(injuries = emptySet())
        OnboardingStep.TRAINING_DAYS -> answers.copy(trainingDays = emptySet(), trainingReminder = false)
        OnboardingStep.PACE -> answers.copy(pace = null)
        else -> answers
    }

    /** The middle of the 1..5 body shape slider. */
    const val DEFAULT_BODY_STAGE = 3
    const val BODY_STAGES = 5
}
