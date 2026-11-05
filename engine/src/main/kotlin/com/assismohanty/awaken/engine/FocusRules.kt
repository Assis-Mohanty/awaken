package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.GateRank

/** How a Focus Gate run is timed (GD §8, §8.1). */
enum class GateMode { PRESET, CUSTOM, STOPWATCH }

/** What a finished custom or stopwatch run is worth (GD §8.1 P2–P5). */
sealed interface FocusRunOutcome {
    /** Under the minimum: logged only, no XP, no collapse (P3). */
    data object ShortRun : FocusRunOutcome

    /** Over the daily cap: logged only (P5). */
    data object OverCap : FocusRunOutcome

    /** Pays quest XP at [rank]'s difficulty. */
    data class Paid(val rank: GateRank) : FocusRunOutcome
}

/** Pure rules for custom and stopwatch gates and subjects (GD §8.1, approved 2026-11-01). */
object FocusRules {
    const val CUSTOM_MIN_MINUTES = 10
    const val CUSTOM_MAX_MINUTES = 180
    const val CUSTOM_STEP_MINUTES = 5
    const val CUSTOM_DEFAULT_MINUTES = 30
    const val STOPWATCH_MIN_MINUTES = 15
    const val DAILY_CAP = 4
    const val SUBJECT_MAX = 12
    const val SUBJECT_NAME_MAX = 24

    /** A running stopwatch has no end; its stored planned length is this stand-in so pause maths keeps working. Replaced by the counted time on stop. */
    const val STOPWATCH_PLACEHOLDER_SEC = 24 * 60 * 60

    fun clampCustomMinutes(minutes: Int): Int {
        val clamped = minutes.coerceIn(CUSTOM_MIN_MINUTES, CUSTOM_MAX_MINUTES)
        return CUSTOM_MIN_MINUTES + (clamped - CUSTOM_MIN_MINUTES) / CUSTOM_STEP_MINUTES * CUSTOM_STEP_MINUTES
    }

    /** Tier by counted minutes (P2): the largest preset gate that fits; null below the E-Gate. */
    fun tierFor(minutes: Int): GateRank? = GateRank.entries.lastOrNull { minutes >= it.durationMinutes }

    /**
     * The result of a run that counted [minutes] of focus, with [paidToday] custom and stopwatch runs already paid on
     * its game-day. Custom runs are measured at their planned length; stopwatch runs at the elapsed time.
     */
    fun outcome(minutes: Int, paidToday: Int): FocusRunOutcome {
        val rank = tierFor(minutes) ?: return FocusRunOutcome.ShortRun
        if (paidToday >= DAILY_CAP) return FocusRunOutcome.OverCap
        return FocusRunOutcome.Paid(rank)
    }

    fun cleanSubjectName(raw: String): String? = raw.trim().take(SUBJECT_NAME_MAX).takeIf { it.isNotEmpty() }

    fun canAddSubject(activeCount: Int): Boolean = activeCount < SUBJECT_MAX
}
