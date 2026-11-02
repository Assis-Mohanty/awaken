package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import java.time.Instant
import java.time.LocalDate

sealed interface CounterEvent {
    val at: Instant

    data class ScreenEntered(val app: ShortVideoApp, override val at: Instant, val index: Int) : CounterEvent
    data class ItemChanged(val app: ShortVideoApp, val index: Int, override val at: Instant) : CounterEvent
    data class ScreenLeft(val app: ShortVideoApp, override val at: Instant) : CounterEvent
    data class Tick(override val at: Instant) : CounterEvent
    data class DayRolled(val newDay: LocalDate, override val at: Instant) : CounterEvent
    data class Snoozed(val app: ShortVideoApp, override val at: Instant) : CounterEvent
    data class LimitsChanged(val limits: Map<ShortVideoApp, Int>, override val at: Instant) : CounterEvent
}
