package com.assismohanty.awaken.engine

/** The daily step goal ring (GD §16.11). Display only: it pays nothing and changes no HEALTH_STEPS quest. */
object StepGoalRules {
    /** N19 */
    const val DEFAULT_GOAL = 8_000

    /** N20: 1,000-30,000 in steps of 500. */
    const val MIN_GOAL = 1_000
    const val MAX_GOAL = 30_000
    const val GOAL_STEP = 500

    fun isValidGoal(goal: Int): Boolean = goal in MIN_GOAL..MAX_GOAL && (goal - MIN_GOAL) % GOAL_STEP == 0

    fun clampGoal(goal: Int): Int = MIN_GOAL + (goal.coerceIn(MIN_GOAL, MAX_GOAL) - MIN_GOAL) / GOAL_STEP * GOAL_STEP

    /** Fill of the ring, 0..1. */
    fun progress(steps: Long, goal: Int): Float = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)

    fun reached(steps: Long, goal: Int): Boolean = steps >= goal
}
