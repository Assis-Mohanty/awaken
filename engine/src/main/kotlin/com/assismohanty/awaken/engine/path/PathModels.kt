package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Lifecycle of a Path (GD §6.6.1). */
enum class PathStatus { PLANNING, ACTIVE, PAUSED, ARCHIVED }

/**
 * Blueprint units (GD §6.6.3) with their V4 target bounds. [templateUnit] is the unit string a quest carries in the app
 * (shared with the template vocabulary where one exists, so plurals resolve the same way).
 */
enum class PathUnit(val min: Int, val max: Int, val templateUnit: String) {
    MINUTES(5, 120, "min"),
    REPS(5, 300, "reps"),
    PAGES(2, 100, "pages"),
    PROBLEMS(1, 10, "problems"),
    ITEMS(1, 20, "items"),
    SESSIONS(1, 3, "sessions"),
    KM(1, 21, "km"),
}

/** How a Path quest is verified (GD §6.6.3): by hand, or by clearing the Focus Gate it maps to. */
enum class PathMode { SELF, GATE }

/** What the engine tells the model about last week (GD §6.6.5). */
enum class ProgressionSignal { UP, HOLD, DOWN }

/** A Path slot's fate (ARCH §2 `path_slot.outcome`; ENGINE_DECISIONS #64 for EXPIRED vs SKIPPED). */
enum class SlotOutcome { PENDING, COMPLETED, EXPIRED, SKIPPED, REROLLED, REPORTED }

/** The three optional chips on a Path quest's reward sheet (GD §6.6.5). */
enum class Feedback { TOO_EASY, RIGHT, TOO_HARD }

/** The closed tag vocabulary a blueprint may use (GD §6.6.3). */
object PathTags {
    val ALL: Set<String> = setOf("impact", "legs", "upper", "outdoor", "indoor", "social", "quiet", "screen")
}

/**
 * A hunter's Path (GD §6.6.1). [createdAt] orders Paths for the daily cap and main promotion ties (older first).
 */
data class Path(
    val id: String,
    val goal: String,
    val currentLevel: String,
    val notes: String,
    val primaryStat: Stat,
    val secondaryStat: Stat?,
    val activeDays: Set<DayOfWeek>,
    val questsPerDay: Int,
    val weeklyMinutes: Int,
    val status: PathStatus,
    val createdAt: Instant,
) {
    /** V9 (GD §6.6.2): `n = activeDays × questsPerDay`. */
    val weeklyQuestLimit: Int get() = activeDays.size * questsPerDay
}

/** What the hunter types when creating a Path, before it exists (GD §6.6.1). */
data class PathInput(
    val goal: String,
    val currentLevel: String,
    val notes: String,
    val primaryStat: Stat,
    val secondaryStat: Stat?,
    val activeDays: Set<DayOfWeek>,
    val questsPerDay: Int,
    val weeklyMinutes: Int,
)

/** Why a Path can't be created (shown inline on the form). */
enum class PathInputProblem { GOAL_LENGTH, LEVEL_LENGTH, NOTES_LENGTH, SAME_STATS, NO_DAYS, QUESTS_PER_DAY, WEEKLY_MINUTES }

/** The limits in GD §6.6.1. */
object PathRules {
    val GOAL_LENGTH = 3..120
    val LEVEL_LENGTH = 0..120
    val NOTES_LENGTH = 0..280
    val QUESTS_PER_DAY = 1..2
    val WEEKLY_MINUTES = 30..900
    const val MAX_ACTIVE = 3
    const val MAX_CREATED_PER_DAY = 1

    /** Problems with the form, in field order; empty when the Path can be created. */
    fun problems(input: PathInput): List<PathInputProblem> = buildList {
        if (input.goal.trim().length !in GOAL_LENGTH) add(PathInputProblem.GOAL_LENGTH)
        if (input.currentLevel.trim().length !in LEVEL_LENGTH) add(PathInputProblem.LEVEL_LENGTH)
        if (input.notes.trim().length !in NOTES_LENGTH) add(PathInputProblem.NOTES_LENGTH)
        if (input.secondaryStat == input.primaryStat) add(PathInputProblem.SAME_STATS)
        if (input.activeDays.isEmpty()) add(PathInputProblem.NO_DAYS)
        if (input.questsPerDay !in QUESTS_PER_DAY) add(PathInputProblem.QUESTS_PER_DAY)
        if (input.weeklyMinutes !in WEEKLY_MINUTES) add(PathInputProblem.WEEKLY_MINUTES)
    }

    /** Max 3 active (or planning) Paths and at most one created per game-day (GD §6.6.1). */
    fun canCreate(activeOrPlanning: Int, createdToday: Int): Boolean =
        activeOrPlanning < MAX_ACTIVE && createdToday < MAX_CREATED_PER_DAY
}

/** One rung of the ladder (GD §6.6.2). Narrative only: milestones carry no rewards. */
data class Milestone(val index: Int, val title: String, val summary: String, val exitCriterion: String)

/**
 * A blueprint that survived V1–V9 (GD §6.6.4), with strings cleaned and numbers clamped. [index] is its position in the
 * model's response (stable across re-validation; part of the slot key).
 */
data class ValidatedBlueprint(
    val index: Int,
    val title: String,
    val description: String,
    val stat: Stat,
    val unit: PathUnit,
    val target: Int,
    val effort: Difficulty,
    val estMinutes: Int,
    val mode: PathMode,
    val tags: Set<String>,
    val rationale: String,
)

/**
 * A validated blueprint with its rewards decided by the engine (GD §6.6.4 pricing). The model's `effort` only ever
 * lowers a SELF quest's difficulty; Hard and Extreme exist only behind a Focus Gate.
 */
data class PricedBlueprint(
    val blueprint: ValidatedBlueprint,
    val difficulty: Difficulty,
    val verification: Verification,
    val gateRank: GateRank?,
    val minDurationSec: Int,
)

/** The rule that discarded a blueprint, for the Debug screen. [index] is −1 when the whole response was unusable. */
data class DropReason(val index: Int, val rule: Rule, val detail: String) {
    enum class Rule { V1, V2, V6, V7, V8, V9 }
}

/** What the validator needs to know about the hunter besides the Path (GD §6.6.4 V6–V7). */
data class ValidationContext(
    val path: Path,
    val ownedEquipment: Set<String>,
    /** The hunter's active Injury Mode tags (empty when off; ENGINE_DECISIONS #46). */
    val injuryTags: Set<String>,
    /** Titles of this Path's quests in the last 14 days (V7). */
    val recentTitles: List<String>,
)

/** A week response after validation: the survivors in order, every drop with its reason, and the advance hint. */
data class WeekValidation(
    val kept: List<ValidatedBlueprint>,
    val drops: List<DropReason>,
    val suggestAdvance: Boolean,
)

/** A plan response after validation (GD §6.6.2). */
sealed interface PlanValidation {
    data class Valid(val milestones: List<Milestone>) : PlanValidation

    /** Fewer than 4 usable milestones (or nothing parseable): the Path stays PLANNING and offers Retry. */
    data class Failed(val usable: Int) : PlanValidation
}

/**
 * A priced blueprint placed in a week (ARCH §2 `path_slot`). [assignedDate] null means unscheduled: it didn't fit the
 * Path's remaining days this week and waits as a reroll replacement (ENGINE_DECISIONS #65).
 */
data class PathSlot(
    val id: String,
    val pathId: String,
    val isoWeek: String,
    val blueprintIndex: Int,
    val assignedDate: LocalDate?,
    val carried: Boolean,
    val priced: PricedBlueprint,
    val outcome: SlotOutcome = SlotOutcome.PENDING,
    val feedback: Feedback? = null,
)

/** One of last week's Path quests, as the progression signal and the next week's prompt see it (GD §6.6.5). */
data class WeekOutcome(val title: String, val outcome: SlotOutcome, val feedback: Feedback?)
