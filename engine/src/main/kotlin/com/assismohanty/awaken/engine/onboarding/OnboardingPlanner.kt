package com.assismohanty.awaken.engine.onboarding

import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.FocusGoalRules
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.StepGoalRules
import com.assismohanty.awaken.engine.body.WeightGoal
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalDate

/** What Android allows right now. The plan depends on it: app limits and phone-down nights need usage access. */
data class OnboardingGrants(
    val usageAccess: Boolean = false,
    val healthConnected: Boolean = false,
)

/** Screen Discipline on from tomorrow, with this limit on each seeded Distracting app (OB5). */
data class DisciplinePlan(val limitMinutes: Int)

/** Phone-down nights on, with this window (GD §16.12). */
data class PhoneDownPlan(val bedtimeMinute: Int, val wakeMinute: Int)

/**
 * Everything Start will write, worked out from the answers (docs/onboarding/PLAN.md §3.3). Pure: the screen shows it, the
 * quest preview is built from it, and the commit writes it, so what the person sees is what they get.
 */
data class OnboardingPlan(
    val focusStats: List<Stat>,
    val tiers: Map<Stat, Difficulty>,
    val stepGoal: Int,
    val focusGoalMinutes: Int,
    val ownedEquipment: Set<String>,
    val injuryMode: Boolean,
    val injuryTags: Set<String>,
    val repRange: RepBand,
    /** ISO days, 1 = Monday ... 7 = Sunday. */
    val trainingDays: Set<Int>,
    val trainingReminder: Boolean,
    val discipline: DisciplinePlan?,
    val phoneDown: PhoneDownPlan?,
    /** Something the person asked for needs usage access that is not granted: the plan says so and links to the setting. */
    val needsUsageAccess: Boolean,
    /** "Too much scrolling": Reel counter is suggested after Start. It is never switched on here (it needs an accessibility service). */
    val suggestReelCounter: Boolean,
    val healthEnabled: Boolean,
    /** The daily reminder time, minutes since midnight (OB14). */
    val reminderMinute: Int,
    val birthYear: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val dietGoalHint: WeightGoal?,
)

object OnboardingPlanner {

    /** OB14 */
    const val REMINDER_MINUTE = 7 * 60

    /** OB15: the building screen runs this long in total, split evenly across the lines it shows. */
    const val BUILD_MILLIS = 3_000

    fun plan(answers: OnboardingAnswers, grants: OnboardingGrants, today: LocalDate): OnboardingPlan {
        val edits = answers.edits
        val screenTimeAnswered = answers.screenTime != null
        val poorSleep = Obstacle.POOR_SLEEP in answers.obstacles
        val usage = grants.usageAccess

        val discipline = if (screenTimeAnswered && usage) {
            DisciplinePlan(snapLimit(edits.appLimitMinutes) ?: OnboardingMapping.appLimitMinutes(answers.screenTime))
        } else {
            null
        }
        val phoneDown = if (poorSleep && usage) {
            val (bed, wake) = OnboardingRules.sleepShown(answers.bedtimeMinute, answers.wakeMinute, GameDay.DEFAULT_DAY_START_HOUR)
            PhoneDownPlan(bed, wake)
        } else {
            null
        }
        val trainingDays = edits.trainingDays ?: answers.trainingDays
        return OnboardingPlan(
            focusStats = edits.focusStats?.takeIf { it.size == 2 && it[0] != it[1] } ?: OnboardingRules.focusStats(answers.improve),
            tiers = OnboardingMapping.baselineTiers(answers),
            stepGoal = edits.stepGoal?.let(StepGoalRules::clampGoal) ?: OnboardingMapping.stepGoal(answers),
            focusGoalMinutes = edits.focusGoalMinutes?.let(FocusGoalRules::clampGoal) ?: OnboardingMapping.focusGoalMinutes(answers),
            ownedEquipment = OnboardingMapping.ownedEquipment(answers.equipment),
            injuryMode = OnboardingMapping.injuryMode(answers.injuries),
            injuryTags = OnboardingMapping.injuryTags(answers.injuries),
            repRange = OnboardingMapping.repRange(answers.fitness),
            trainingDays = trainingDays,
            trainingReminder = answers.trainingReminder && trainingDays.isNotEmpty(),
            discipline = discipline,
            phoneDown = phoneDown,
            needsUsageAccess = (screenTimeAnswered || poorSleep) && !usage,
            suggestReelCounter = Obstacle.TOO_MUCH_SCROLLING in answers.obstacles,
            healthEnabled = grants.healthConnected,
            reminderMinute = REMINDER_MINUTE,
            birthYear = answers.age?.let { today.year - it },
            heightCm = answers.heightCm,
            weightKg = answers.weightKg,
            dietGoalHint = OnboardingRules.dietGoalHint(answers.bodyStage, answers.weightKg, answers.target),
        )
    }

    /** A limit the person typed in: 5-240 minutes in steps of 5, or null when there is none. */
    private fun snapLimit(minutes: Int?): Int? = minutes?.let {
        ScreenTimeRules.MIN_LIMIT_MINUTES.coerceAtLeast(
            (it.coerceIn(ScreenTimeRules.MIN_LIMIT_MINUTES, ScreenTimeRules.MAX_LIMIT_MINUTES) / ScreenTimeRules.LIMIT_STEP_MINUTES) *
                ScreenTimeRules.LIMIT_STEP_MINUTES,
        )
    }

    /** Whether a window the plan would write is one phone-down nights accept (a guard for the commit). */
    fun isWritable(plan: OnboardingPlan): Boolean =
        plan.phoneDown?.let { BedtimeRules.isValidWindow(it.bedtimeMinute, it.wakeMinute, GameDay.DEFAULT_DAY_START_HOUR) } ?: true
}
