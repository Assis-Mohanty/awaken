package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ProgramStatus { ACTIVE, COMPLETED, FINISHED, ABANDONED }

/** What the hunter chooses after a broken day (GD §16.15). */
enum class ProgramBreakChoice { RESTART, CONTINUE }

/** How one finished program day went. */
enum class ProgramDay { KEPT, REST, BROKEN }

enum class ProgramStart { OK, NOT_ENOUGH_SLOTS, PROGRAM_ACTIVE }

/** Words for a rule, by key; the app maps a key to a localised title. */
enum class ProgramRuleKey { WALK, READ, FOCUS_15, PLAN, WORKOUT, FOCUS_25, STEPS_10K }

/** What the rule becomes when Health is off and its verified form cannot be checked. */
data class ProgramFallback(val titleKey: ProgramRuleKey, val target: Int, val unit: String)

/**
 * One daily rule of a program, which becomes a read-only habit. [habitType] is CHECK or TIMED; a TIMED rule is a focus
 * session whose [target] is minutes.
 */
data class ProgramRule(
    val key: ProgramRuleKey,
    val stat: Stat,
    val difficulty: Difficulty,
    val timed: Boolean,
    val target: Int,
    val unit: String,
    val verification: Verification,
    val fallback: ProgramFallback? = null,
)

/** A program: its length and rules (N30). [needsPhoneDownNight] adds the Phone-down night to each day when that feature is on. */
data class ProgramSpec(
    val key: String,
    val days: Int,
    val rules: List<ProgramRule>,
    val needsPhoneDownNight: Boolean,
    val achievementId: String,
)

/**
 * Programs: 30 / 66 / 75 days (GD §16.15, approved 2026-11-02). Pure. Nothing is ever deducted: a broken day only changes
 * how the run ends, and the only reward is a once-only gold achievement for a run with no broken day.
 */
object ProgramRules {
    private const val FOCUS_UNIT = "min"

    private val walk = ProgramRule(
        ProgramRuleKey.WALK, Stat.VIT, Difficulty.NORMAL, timed = false, target = 7_000, unit = "steps", verification = Verification.HEALTH_STEPS,
        fallback = ProgramFallback(ProgramRuleKey.WALK, target = 30, unit = "min"),
    )
    private val read = ProgramRule(ProgramRuleKey.READ, Stat.INT, Difficulty.EASY, timed = false, target = 10, unit = "pages", verification = Verification.SELF)
    private val focus15 = ProgramRule(
        ProgramRuleKey.FOCUS_15, Stat.INT, HabitRules.timedDifficulty(15), timed = true, target = 15, unit = FOCUS_UNIT, verification = Verification.TIMER,
    )
    private val plan = ProgramRule(ProgramRuleKey.PLAN, Stat.WIL, Difficulty.EASY, timed = false, target = 2, unit = "min", verification = Verification.SELF)
    private val workout = ProgramRule(
        ProgramRuleKey.WORKOUT, Stat.STR, Difficulty.NORMAL, timed = false, target = 45, unit = "min", verification = Verification.HEALTH_EXERCISE,
        fallback = ProgramFallback(ProgramRuleKey.WORKOUT, target = 45, unit = "min"),
    )
    private val steps10k = ProgramRule(
        ProgramRuleKey.STEPS_10K, Stat.VIT, Difficulty.NORMAL, timed = false, target = 10_000, unit = "steps", verification = Verification.HEALTH_STEPS,
        fallback = ProgramFallback(ProgramRuleKey.STEPS_10K, target = 10_000, unit = "steps"),
    )
    private val focus25 = ProgramRule(
        ProgramRuleKey.FOCUS_25, Stat.INT, HabitRules.timedDifficulty(25), timed = true, target = 25, unit = FOCUS_UNIT, verification = Verification.TIMER,
    )

    /** N30: the three programs. The names are original. */
    val CATALOG: List<ProgramSpec> = listOf(
        ProgramSpec("steady_30", 30, listOf(walk, read, focus15), needsPhoneDownNight = false, achievementId = "program_steady_30"),
        ProgramSpec("groundwork_66", 66, listOf(walk, read, focus15, plan), needsPhoneDownNight = false, achievementId = "program_groundwork_66"),
        ProgramSpec("ironclad_75", 75, listOf(workout, read, steps10k, focus25), needsPhoneDownNight = true, achievementId = "program_ironclad_75"),
    )

    fun spec(key: String): ProgramSpec? = CATALOG.firstOrNull { it.key == key }

    /** Day k: game-days since the (re)start, plus one. */
    fun dayNumber(runStart: LocalDate, date: LocalDate): Int = (ChronoUnit.DAYS.between(runStart, date) + 1).toInt()

    /** The game-day a run started on [runStart] ends on. */
    fun lastDate(runStart: LocalDate, spec: ProgramSpec): LocalDate = runStart.plusDays(spec.days - 1L)

    fun isLastDay(runStart: LocalDate, spec: ProgramSpec, date: LocalDate): Boolean = dayNumber(runStart, date) == spec.days

    /** Kept when every rule of the day was done; otherwise rest on a finalized rest or recovery day, else broken. */
    fun dayVerdict(allRulesDone: Boolean, restOrRecovery: Boolean): ProgramDay = when {
        allRulesDone -> ProgramDay.KEPT
        restOrRecovery -> ProgramDay.REST
        else -> ProgramDay.BROKEN
    }

    /** The question after a broken day goes unanswered until the next rollover: carry on. */
    val DEFAULT_BREAK = ProgramBreakChoice.CONTINUE

    /** No broken day in the run → COMPLETED; any broken day (continued past) → FINISHED. */
    fun endStatus(brokenDays: Int): ProgramStatus = if (brokenDays == 0) ProgramStatus.COMPLETED else ProgramStatus.FINISHED

    fun canStart(spec: ProgramSpec, freeHabitSlots: Int, programActive: Boolean): ProgramStart = when {
        programActive -> ProgramStart.PROGRAM_ACTIVE
        freeHabitSlots < spec.rules.size -> ProgramStart.NOT_ENOUGH_SLOTS
        else -> ProgramStart.OK
    }

    /**
     * The catalog rule a Health-checked program habit was made from, matched by its check and target (unique within a
     * program); null for any other habit. Used to fall back for a day whose Health check is unavailable (ENGINE_DECISIONS #148).
     */
    fun healthRuleFor(spec: ProgramSpec, verification: Verification, target: Int): ProgramRule? =
        spec.rules.firstOrNull { it.fallback != null && it.verification == verification && it.target == target }

    /** The rule as the habit will be made: its verified form with Health on, its plain form with Health off. */
    fun effective(rule: ProgramRule, healthOn: Boolean): ProgramRule =
        if (healthOn || rule.fallback == null) {
            rule
        } else {
            rule.copy(target = rule.fallback.target, unit = rule.fallback.unit, verification = Verification.SELF)
        }
}
