package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import java.time.Instant
import java.time.LocalDate

val DAY_1: LocalDate = LocalDate.of(2026, 11, 1)
val DAY_2: LocalDate = LocalDate.of(2026, 11, 2)
val T0: Instant = Instant.parse("2026-11-01T10:00:00Z")

/**
 * Drives a [CounterMachine] with a millisecond clock relative to [T0] and records every effect.
 * Methods taking `ms` move the clock to that absolute offset; the rest use the current clock.
 */
class MachineHarness(
    val config: CounterConfig = CounterConfig(),
    day: LocalDate = DAY_1,
    limits: Map<ShortVideoApp, Int> = emptyMap(),
    todayCounts: Map<ShortVideoApp, Int> = emptyMap(),
    todaySnoozes: Map<ShortVideoApp, Int> = emptyMap(),
) {
    private val machine = CounterMachine(config)
    var state: CounterState = CounterState.initial(day, todayCounts, todaySnoozes, limits)
        private set
    val effects = mutableListOf<CounterEffect>()
    var t: Long = 0
        private set
    private val cursor = mutableMapOf<ShortVideoApp, Int>()

    fun now(): Instant = T0.plusMillis(t)

    fun send(event: CounterEvent): List<CounterEffect> {
        val transition = machine.reduce(state, event)
        state = transition.state
        effects += transition.effects
        return transition.effects
    }

    fun enter(app: ShortVideoApp, ms: Long = t, index: Int = cursor[app] ?: 0): List<CounterEffect> {
        t = ms
        cursor[app] = index
        return send(CounterEvent.ScreenEntered(app, now(), index))
    }

    fun item(app: ShortVideoApp, index: Int, ms: Long = t): List<CounterEffect> {
        t = ms
        cursor[app] = index
        return send(CounterEvent.ItemChanged(app, index, now()))
    }

    fun leave(app: ShortVideoApp, ms: Long = t): List<CounterEffect> {
        t = ms
        return send(CounterEvent.ScreenLeft(app, now()))
    }

    fun tick(ms: Long = t): List<CounterEffect> {
        t = ms
        return send(CounterEvent.Tick(now()))
    }

    fun roll(day: LocalDate, ms: Long = t): List<CounterEffect> {
        t = ms
        return send(CounterEvent.DayRolled(day, now()))
    }

    fun snooze(app: ShortVideoApp, ms: Long = t): List<CounterEffect> {
        t = ms
        return send(CounterEvent.Snoozed(app, now()))
    }

    fun setLimits(limits: Map<ShortVideoApp, Int>, ms: Long = t): List<CounterEffect> {
        t = ms
        return send(CounterEvent.LimitsChanged(limits, now()))
    }

    /** Waits past the settle window and ticks, so any pending item commits. */
    fun settle(): List<CounterEffect> = tick(t + config.settleMillis + 50)

    /** Enters [app] and lets the first video settle. */
    fun open(app: ShortVideoApp, ms: Long = t, index: Int = cursor[app] ?: 0): List<CounterEffect> =
        enter(app, ms, index) + settle()

    /** Swipes forward to [n] new videos, one second apart, letting each settle. */
    fun watchNext(app: ShortVideoApp, n: Int = 1) {
        repeat(n) {
            val next = (cursor[app] ?: 0) + 1
            item(app, next, t + 1_000)
            settle()
        }
    }

    /** Swipes to an explicit index and lets it settle. */
    fun swipeTo(app: ShortVideoApp, index: Int) {
        item(app, index, t + 1_000)
        settle()
    }

    fun counted(app: ShortVideoApp? = null): List<CounterEffect.Counted> =
        effects.filterIsInstance<CounterEffect.Counted>().filter { app == null || it.app == app }

    fun nudges(app: ShortVideoApp? = null): List<CounterEffect.NudgeRequested> =
        effects.filterIsInstance<CounterEffect.NudgeRequested>().filter { app == null || it.app == app }

    fun seconds(app: ShortVideoApp, day: LocalDate = DAY_1): Long =
        effects.filterIsInstance<CounterEffect.TimeAdded>().filter { it.app == app && it.day == day }.sumOf { it.seconds }

    fun bubbles(): List<CounterEffect.BubbleChanged> = effects.filterIsInstance<CounterEffect.BubbleChanged>()
}
