package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.limit.LimitPolicy
import java.time.Instant
import java.time.LocalDate

data class Pending(val index: Int, val since: Instant)

data class Session(
    val id: Long,
    val highestCounted: Int,
    val currentIndex: Int,
    val pending: Pending?,
    val leftAt: Instant?,
)

data class AppDayState(val count: Int = 0, val snoozesUsed: Int = 0, val carryMillis: Long = 0)

data class ActiveScreen(val app: ShortVideoApp, val lastAt: Instant)

data class RestoredSession(val sessionId: Long, val highestCounted: Int, val lastSeenAt: Instant)

data class CounterState(
    val day: LocalDate,
    val apps: Map<ShortVideoApp, AppDayState>,
    val sessions: Map<ShortVideoApp, Session>,
    val active: ActiveScreen?,
    val limits: Map<ShortVideoApp, Int>,
) {
    fun countToday(app: ShortVideoApp): Int = apps[app]?.count ?: 0
    fun totalToday(): Int = apps.values.sumOf { it.count }
    fun snoozesUsed(app: ShortVideoApp): Int = apps[app]?.snoozesUsed ?: 0
    fun allowance(app: ShortVideoApp, snoozeSize: Int): Int? =
        LimitPolicy.allowance(limits[app], snoozesUsed(app), snoozeSize)

    companion object {
        fun initial(
            day: LocalDate,
            todayCounts: Map<ShortVideoApp, Int>,
            todaySnoozes: Map<ShortVideoApp, Int>,
            limits: Map<ShortVideoApp, Int>,
            restored: Map<ShortVideoApp, RestoredSession> = emptyMap(),
        ): CounterState = CounterState(
            day = day,
            apps = ShortVideoApp.entries.associateWith {
                AppDayState(count = todayCounts[it] ?: 0, snoozesUsed = todaySnoozes[it] ?: 0)
            },
            sessions = restored.mapValues { (_, r) ->
                Session(
                    id = r.sessionId,
                    highestCounted = r.highestCounted,
                    currentIndex = r.highestCounted,
                    pending = null,
                    leftAt = r.lastSeenAt,
                )
            },
            active = null,
            limits = limits.filterValues { it >= 1 },
        )
    }
}

data class Transition(val state: CounterState, val effects: List<CounterEffect>)
