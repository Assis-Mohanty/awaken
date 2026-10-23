package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Verification

/**
 * The set counter for SELF rep quests (GD §6.5; ENGINE_DECISIONS #84, #85): a list of reps per set, kept as text so the
 * app can store it in one column. It only tracks progress. It never completes a quest and never mints anything: the
 * hunter taps Complete, so XP, caps and the V multiplier are untouched.
 */
object SetCounter {
    /** Input sanity (not a game number): the most sets one quest can hold. */
    const val MAX_SETS = 30

    /** Input sanity: the largest single set. Matches the REPS upper clamp for Path quests (GD §6.6.4 V4). */
    const val MAX_REPS_PER_SET = 300

    private const val SEPARATOR = ','
    private const val REPS_UNIT = "reps"

    /** Only honor-system rep quests get a counter: verified quests are completed by their verifier. */
    fun appliesTo(unit: String, verification: Verification): Boolean = unit == REPS_UNIT && verification == Verification.SELF

    /** Reads the stored text; anything malformed is skipped rather than trusted. */
    fun parse(raw: String): List<Int> =
        raw.split(SEPARATOR)
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..MAX_REPS_PER_SET }
            .take(MAX_SETS)

    fun format(sets: List<Int>): String = sets.joinToString(SEPARATOR.toString())

    /** Adds a set of [reps] (clamped to 1..[MAX_REPS_PER_SET]); a full counter is returned unchanged. */
    fun add(sets: List<Int>, reps: Int): List<Int> =
        if (sets.size >= MAX_SETS) sets else sets + reps.coerceIn(1, MAX_REPS_PER_SET)

    fun undoLast(sets: List<Int>): List<Int> = sets.dropLast(1)

    fun total(sets: List<Int>): Int = sets.sum()

    fun remaining(sets: List<Int>, target: Int): Int = (target - total(sets)).coerceAtLeast(0)

    /** What the stepper starts at: the reps still missing, or the last set once the target is reached. */
    fun suggestedReps(sets: List<Int>, target: Int): Int {
        val missing = remaining(sets, target)
        return (if (missing > 0) missing else sets.lastOrNull() ?: 1).coerceIn(1, MAX_REPS_PER_SET)
    }

    fun isTargetReached(sets: List<Int>, target: Int): Boolean = total(sets) >= target
}
