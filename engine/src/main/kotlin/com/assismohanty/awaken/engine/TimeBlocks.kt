package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.TimeOfDay
import java.time.LocalTime

/**
 * Time-of-day blocks (GD §14.5). Morning runs from the day start to [Bounds.morningEnd], Day to [Bounds.dayEnd], Evening
 * to the next day start (so 02:00 with a 04:00 day start is still the previous game-day's Evening). Presentation only:
 * no generation rule, reward or clear rule reads a block.
 */
object TimeBlocks {
    const val DEFAULT_MORNING_END = 12
    const val DEFAULT_DAY_END = 17

    /** Settings choices, whole hours (GD §14.5). The ranges don't overlap, so Morning always ends before Day. */
    val MORNING_END_HOURS = 9..14
    val DAY_END_HOURS = 15..21

    data class Bounds(val morningEnd: Int = DEFAULT_MORNING_END, val dayEnd: Int = DEFAULT_DAY_END) {
        init {
            require(morningEnd in MORNING_END_HOURS) { "morningEnd $morningEnd outside $MORNING_END_HOURS" }
            require(dayEnd in DAY_END_HOURS) { "dayEnd $dayEnd outside $DAY_END_HOURS" }
        }

        companion object {
            /** Stored values that are out of range (an older build, a hand-edited file) fall back to the defaults. */
            fun safe(morningEnd: Int, dayEnd: Int): Bounds = Bounds(
                morningEnd = morningEnd.takeIf { it in MORNING_END_HOURS } ?: DEFAULT_MORNING_END,
                dayEnd = dayEnd.takeIf { it in DAY_END_HOURS } ?: DEFAULT_DAY_END,
            )
        }
    }

    /** The block containing [time] for a game-day starting at [dayStartHour] (0–6, ENGINE_DECISIONS #47). */
    fun blockAt(time: LocalTime, dayStartHour: Int, bounds: Bounds = Bounds()): TimeOfDay = when {
        time.hour < dayStartHour -> TimeOfDay.EVENING
        time.hour < bounds.morningEnd -> TimeOfDay.MORNING
        time.hour < bounds.dayEnd -> TimeOfDay.DAY
        else -> TimeOfDay.EVENING
    }

    /** Display order: Morning, Day, Evening, then Anytime (null) last (GD §14.5). */
    fun order(block: TimeOfDay?): Int = block?.ordinal ?: TimeOfDay.entries.size
}
