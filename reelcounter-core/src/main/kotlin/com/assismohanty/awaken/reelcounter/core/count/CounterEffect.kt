package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import java.time.Instant
import java.time.LocalDate

sealed interface CounterEffect {
    /** [at] is when the video came into view, not when the settle window closed. */
    data class Counted(
        val app: ShortVideoApp,
        val day: LocalDate,
        val sessionId: Long,
        val index: Int,
        val at: Instant,
        val countToday: Int,
    ) : CounterEffect

    data class TimeAdded(val app: ShortVideoApp, val day: LocalDate, val seconds: Long) : CounterEffect
    data class NudgeRequested(val app: ShortVideoApp, val countToday: Int, val snoozesLeft: Int) : CounterEffect
    data class SnoozeAccepted(val app: ShortVideoApp, val day: LocalDate, val snoozesUsed: Int) : CounterEffect
    data class SnoozeRejected(val app: ShortVideoApp) : CounterEffect

    /** null = hide the bubble. */
    data class BubbleChanged(val app: ShortVideoApp?) : CounterEffect
}
