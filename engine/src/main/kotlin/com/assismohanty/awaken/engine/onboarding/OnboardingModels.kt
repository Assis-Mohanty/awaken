package com.assismohanty.awaken.engine.onboarding

import kotlinx.serialization.Serializable

/** Which set of illustrations the flow shows. Never stored on the profile (docs/onboarding/PLAN.md Q1). */
@Serializable
enum class IllustrationSet { MALE, FEMALE, NEUTRAL }

/** Display units. The stored value is always centimetres and kilograms (GD §17 OB11). */
@Serializable
enum class LengthUnit { CM, FEET_INCHES }

@Serializable
enum class MassUnit { KG, LB }

/** Step 8, "What do you want to improve?". */
@Serializable
enum class ImproveGoal { STRENGTH, FOCUS, SLEEP, LESS_SCREEN_TIME, DISCIPLINE, FITNESS, LEARNING }

/** Step 9, "Things that get in your way". [NONE] is exclusive. */
@Serializable
enum class Obstacle { TOO_MUCH_SCROLLING, POOR_SLEEP, SITTING_ALL_DAY, PROCRASTINATION, NONE }

/** Step 10, daily screen time today. */
@Serializable
enum class ScreenTimeBand { UNDER_2H, H2_TO_4, H4_TO_6, OVER_6H }

@Serializable
enum class FitnessLevel { BEGINNER, INTERMEDIATE, ADVANCED }

@Serializable
enum class EquipmentChoice { BODYWEIGHT, DUMBBELLS, GYM }

/** Step 14. [NO_INJURIES] is exclusive. */
@Serializable
enum class InjuryRegion { NO_INJURIES, SHOULDERS, BACK, WRIST, KNEE, HIP }

@Serializable
enum class Pace { STEADY, BALANCED, PUSH_HARD }

/** An optional target on the plan screen (docs/onboarding/PLAN.md Q3). The date is ISO-8601 (yyyy-MM-dd). */
@Serializable
data class WeightTarget(val weightKg: Double, val date: String)

/**
 * Everything the flow has asked so far. Null or empty means "not answered" or skipped. Serializable because the draft is
 * saved after every step and the flow resumes from it. Days are ISO numbers (1 = Monday ... 7 = Sunday).
 */
@Serializable
data class OnboardingAnswers(
    /** Generated once, so the plan preview and the real first plan use the same id. */
    val hunterId: String = "",
    val name: String = "",
    val illustrationSet: IllustrationSet? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val heightUnit: LengthUnit = LengthUnit.CM,
    val weightUnit: MassUnit = MassUnit.KG,
    val bodyStage: Int? = null,
    val improve: List<ImproveGoal> = emptyList(),
    val obstacles: Set<Obstacle> = emptySet(),
    val screenTime: ScreenTimeBand? = null,
    val bedtimeMinute: Int? = null,
    val wakeMinute: Int? = null,
    val fitness: FitnessLevel? = null,
    val equipment: EquipmentChoice? = null,
    val injuries: Set<InjuryRegion> = emptySet(),
    val trainingDays: Set<Int> = emptySet(),
    val trainingReminder: Boolean = false,
    val pace: Pace? = null,
    val target: WeightTarget? = null,
    /** Changes the person made on the plan screen. They win over what the answers would have set. */
    val edits: PlanEdits = PlanEdits(),
    /** Health Connect was allowed on the permission screen (re-checked on resume, so never stale). */
    val healthConnected: Boolean = false,
)

/** What the plan screen's Edit buttons change. Null leaves the value the answers set (GD §17). */
@Serializable
data class PlanEdits(
    val focusStats: List<com.assismohanty.awaken.engine.model.Stat>? = null,
    val stepGoal: Int? = null,
    val focusGoalMinutes: Int? = null,
    val appLimitMinutes: Int? = null,
    val trainingDays: Set<Int>? = null,
)
