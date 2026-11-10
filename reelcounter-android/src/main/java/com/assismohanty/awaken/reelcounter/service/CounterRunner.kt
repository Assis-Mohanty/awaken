package com.assismohanty.awaken.reelcounter.service

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.count.CounterEffect
import com.assismohanty.awaken.reelcounter.core.count.CounterEvent
import com.assismohanty.awaken.reelcounter.core.count.CounterMachine
import com.assismohanty.awaken.reelcounter.core.count.CounterState
import com.assismohanty.awaken.reelcounter.core.detect.RawSignal
import com.assismohanty.awaken.reelcounter.core.detect.ScreenDetector
import com.assismohanty.awaken.reelcounter.core.detect.ScreenTracker
import com.assismohanty.awaken.reelcounter.core.time.GameDay
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.DetectorRepository
import com.assismohanty.awaken.reelcounter.data.ReelDayStart
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.assismohanty.awaken.reelcounter.di.CounterDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the [CounterState]. Every signal is handled on the single [CounterDispatcher] thread, under a mutex,
 * so state changes and their effects are applied in order.
 */
@Singleton
class CounterRunner @Inject constructor(
    private val repository: ReelRepository,
    private val settings: SettingsStore,
    private val detectors: DetectorRepository,
    private val applier: EffectApplier,
    private val clock: ReelClock,
    private val dayStart: ReelDayStart,
    @CounterDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val machine = CounterMachine()
    private val lock = Mutex()
    private val _state = MutableStateFlow<CounterState?>(null)
    val state: StateFlow<CounterState?> = _state

    private var scope: CoroutineScope? = null
    private var ready: Job? = null
    private var tracker: ScreenTracker? = null
    private var gameDay: GameDay = GameDay(clock.zone())
    private var probeFactory: ((Instant) -> RawSignal.Probe)? = null
    private var probeJob: Job? = null
    private var recheckJob: Job? = null

    /** Who is covering the app right now (Pause, Focus Lock, ...). Counting resumes only when nobody is. */
    private val holders = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val held get() = holders.isNotEmpty()

    /** Safe to call again after the service reconnects: the previous run is stopped first. */
    fun start(probe: (Instant) -> RawSignal.Probe) {
        stop()
        val s = CoroutineScope(SupervisorJob() + dispatcher)
        scope = s
        probeFactory = probe
        ready = s.launch {
            val hour = dayStart.dayStartHour.first()
            gameDay = GameDay(clock.zone(), LocalTime.of(hour.coerceIn(0, 23), 0))
            val day = gameDay.dayOf(clock.now())
            val restore = repository.loadRestore(day)
            val limits = settings.limits.first()
            _state.value = CounterState.initial(day, restore.counts, restore.snoozes, limits, restore.sessions)
            tracker = ScreenTracker(ScreenDetector(detectors.config.value.config))
        }
        // The long-lived collectors hang off the scope, not off `ready`, so `ready.join()` can return.
        s.launch {
            ready?.join()
            settings.limits.onEach { handleEvent(CounterEvent.LimitsChanged(it, clock.now())) }.launchIn(this)
            dayStart.dayStartHour.onEach { gameDay = GameDay(clock.zone(), LocalTime.of(it.coerceIn(0, 23), 0)) }
                .launchIn(this)
            detectors.config.onEach { tracker?.replaceDetector(ScreenDetector(it.config)) }.launchIn(this)
            while (isActive) {
                settings.setLastHeartbeat(clock.now().toEpochMilli())
                delay(HEARTBEAT_MILLIS)
            }
        }
    }

    fun submitSignal(raw: RawSignal) {
        val s = scope ?: return
        s.launch {
            ready?.join()
            lock.withLock { handle(raw) }
        }
    }

    /**
     * While another screen (the Pause screen) covers the app, time under it must not count. Turning it on ends the current
     * screen at once and drops signals until it is turned off; turning it off looks at the screen again straight away.
     */
    fun setHeld(on: Boolean, owner: String = "pause") {
        val wasHeld = held
        if (on) holders.add(owner) else holders.remove(owner)
        if (held == wasHeld) return
        val s = scope ?: return
        s.launch {
            ready?.join()
            lock.withLock {
                if (on) {
                    handle(RawSignal.ScreenOff(clock.now()))
                } else {
                    probeFactory?.let { handle(it(clock.now())) }
                }
            }
        }
    }

    fun submitSnooze(app: ShortVideoApp) {
        val s = scope ?: return
        s.launch {
            ready?.join()
            lock.withLock { reduceAndApply(listOf(CounterEvent.Snoozed(app, clock.now()))) }
        }
    }

    /** Ends the current screen (flushing time and hiding overlays), then cancels everything. */
    fun stop() {
        val s = scope ?: return
        scope = null
        probeJob?.cancel()
        recheckJob?.cancel()
        val initialised = ready
        s.launch {
            initialised?.join()
            lock.withLock {
                handle(RawSignal.ScreenOff(clock.now()))
                applier.flushTime()
            }
        }.invokeOnCompletion { s.cancel() }
    }

    private suspend fun handleEvent(event: CounterEvent) = lock.withLock {
        if (_state.value != null) reduceAndApply(listOf(event))
    }

    private suspend fun handle(raw: RawSignal) {
        if (held && raw !is RawSignal.ScreenOff) return
        val current = _state.value ?: return
        val tr = tracker ?: return
        val events = mutableListOf<CounterEvent>()
        val day = gameDay.dayOf(raw.at)
        if (day.isAfter(current.day)) events += CounterEvent.DayRolled(day, raw.at)
        val out = tr.onSignal(raw)
        events += out.events
        reduceAndApply(events)
        out.recheckAt?.let(::scheduleRecheck)
        updateProbeLoop(tr)
    }

    private suspend fun reduceAndApply(events: List<CounterEvent>) {
        var state = _state.value ?: return
        val effects = mutableListOf<CounterEffect>()
        for (event in events) {
            val tr = machine.reduce(state, event)
            state = tr.state
            effects += tr.effects
        }
        _state.value = state
        applier.apply(effects, state)
    }

    private fun scheduleRecheck(at: Instant) {
        val s = scope ?: return
        recheckJob?.cancel()
        recheckJob = s.launch {
            val wait = at.toEpochMilli() - clock.now().toEpochMilli()
            if (wait > 0) delay(wait)
            val factory = probeFactory ?: return@launch
            ready?.join()
            lock.withLock { handle(factory(clock.now())) }
        }
    }

    private fun updateProbeLoop(tr: ScreenTracker) {
        val s = scope ?: return
        if (tr.activeApp != null) {
            if (probeJob?.isActive != true) {
                probeJob = s.launch {
                    while (isActive) {
                        delay(PROBE_MILLIS)
                        val factory = probeFactory ?: break
                        lock.withLock { handle(factory(clock.now())) }
                    }
                }
            }
        } else {
            probeJob?.cancel()
        }
    }

    companion object {
        const val PROBE_MILLIS = 1_000L
        const val HEARTBEAT_MILLIS = 5 * 60 * 1_000L
    }
}
