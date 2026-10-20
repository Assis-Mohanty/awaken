package com.assismohanty.awaken.engine.path

/**
 * The engine's half of "how much harder next week" (GD §6.6.5). Same thresholds as §6.4, plus the reward-sheet
 * feedback chips. The model only ever sees the resulting [ProgressionSignal].
 */
object PathProgression {
    const val UP_RATE = 0.85
    const val DOWN_RATE = 0.40
    const val MIN_TOTAL = 4
    const val DOWN_BIAS = -0.5
    const val ADVANCE_MIN_COMPLETED = 4
    const val ADVANCE_MIN_RATE = 0.60

    /** Counted outcomes from one Path's previous ISO week (ENGINE_DECISIONS #66). */
    data class WeekStats(val completed: Int, val total: Int, val feedbackBias: Double) {
        /** 0.0 when nothing was counted, so an empty week can never look good. */
        val completionRate: Double get() = if (total == 0) 0.0 else completed.toDouble() / total
    }

    /**
     * Each slot is counted once at its final outcome, so a carried-over quest counts once (GD §6.6.5). Only COMPLETED,
     * EXPIRED and SKIPPED count; REROLLED, REPORTED and still-PENDING (never offered) slots are left out.
     */
    fun stats(lastWeek: List<WeekOutcome>): WeekStats {
        val completed = lastWeek.count { it.outcome == SlotOutcome.COMPLETED }
        val total = lastWeek.count { it.outcome in COUNTED }
        val feedback = lastWeek.mapNotNull { it.feedback }
        val bias = (feedback.count { it == Feedback.TOO_EASY } - feedback.count { it == Feedback.TOO_HARD }).toDouble() /
            maxOf(1, feedback.size)
        return WeekStats(completed, total, bias)
    }

    fun signal(lastWeek: List<WeekOutcome>): ProgressionSignal = signal(stats(lastWeek))

    fun signal(stats: WeekStats): ProgressionSignal = when {
        stats.completionRate >= UP_RATE && stats.total >= MIN_TOTAL && stats.feedbackBias >= 0 -> ProgressionSignal.UP
        (stats.completionRate <= DOWN_RATE && stats.total >= MIN_TOTAL) || stats.feedbackBias <= DOWN_BIAS ->
            ProgressionSignal.DOWN
        else -> ProgressionSignal.HOLD
    }

    /**
     * Milestone advance (GD §6.6.5): offered only when the model suggests it **and** the engine agrees: ≥ 4 Path quests
     * completed since the current milestone began and last week's completion rate ≥ 0.60. Advancing pays nothing.
     */
    fun canOfferAdvance(suggestAdvance: Boolean, completedSinceMilestone: Int, lastWeek: WeekStats): Boolean =
        suggestAdvance &&
            completedSinceMilestone >= ADVANCE_MIN_COMPLETED &&
            lastWeek.completionRate >= ADVANCE_MIN_RATE

    private val COUNTED = setOf(SlotOutcome.COMPLETED, SlotOutcome.EXPIRED, SlotOutcome.SKIPPED)
}
