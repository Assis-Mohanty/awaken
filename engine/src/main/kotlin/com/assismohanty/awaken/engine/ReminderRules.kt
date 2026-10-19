package com.assismohanty.awaken.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** When the two daily notifications fire and when the second one is worth sending (ARCH §3.5; ENGINE_DECISIONS #53-#54). */
object ReminderRules {
    /** The "last call" time, local. Day starts are 00:00-06:00 (#47), so this is always inside the same game-day. */
    val LAST_CALL_TIME: LocalTime = LocalTime.of(21, 0)

    /**
     * The next moment local [time] occurs strictly after [now]. Works on local date-times, so a DST change never makes
     * a reminder fire an hour off; a time that doesn't exist on a spring-forward day lands on the first valid instant.
     */
    fun nextFire(now: Instant, zone: ZoneId, time: LocalTime): Instant {
        val localNow = now.atZone(zone).toLocalDateTime()
        var date = localNow.toLocalDate()
        if (!localNow.toLocalTime().isBefore(time)) date = date.plusDays(1)
        return date.atTime(time).atZone(zone).toInstant()
    }

    /**
     * Whether to send the 21:00 last call: only with a reminder set (reminder off silences everything), only when the
     * reminder isn't later than the last call (they must not arrive out of order), and only on an uncleared day.
     */
    fun shouldLastCall(reminder: LocalTime?, day: DayRecord): Boolean =
        reminder != null && reminder < LAST_CALL_TIME && !day.restDay && !StreakRules.isCleared(day)

    /**
     * How many more completions clear the day (GD §7.1: the main quest alone, or 3 items). With the main quest still
     * open that is at most 1; otherwise it is what is left of the 3 items. Always at least 1 on an uncleared day.
     */
    fun questsToClear(day: DayRecord, mainOpen: Boolean): Int {
        if (StreakRules.isCleared(day)) return 0
        val itemsLeft = StreakRules.CLEAR_ITEM_THRESHOLD - day.itemsCompleted
        return (if (mainOpen) minOf(1, itemsLeft) else itemsLeft).coerceAtLeast(1)
    }
}
