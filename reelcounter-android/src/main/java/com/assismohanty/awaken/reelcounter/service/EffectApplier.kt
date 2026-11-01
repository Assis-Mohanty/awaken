package com.assismohanty.awaken.reelcounter.service

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.count.CounterConfig
import com.assismohanty.awaken.reelcounter.core.count.CounterEffect
import com.assismohanty.awaken.reelcounter.core.count.CounterState
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the counter needs from the overlay windows. [allowance] is the limit plus any snoozes, or null when the app
 * has no limit. Implemented by `OverlayController`, which hands it to the host app's `ReelOverlayUi`.
 */
interface OverlaySink {
    fun showBubble(app: ShortVideoApp, sessionId: Long, count: Int, allowance: Int?)
    fun updateBubble(app: ShortVideoApp, count: Int, allowance: Int?)
    fun hideAll()
    fun showNudge(app: ShortVideoApp, countToday: Int, allowance: Int?, snoozesLeft: Int)
    fun hideNudge()
}


/** Turns [CounterEffect]s into storage writes and overlay calls. */
@Singleton
class EffectApplier @Inject constructor(
    private val repository: ReelRepository,
    private val overlay: OverlaySink,
) {
    private data class TimeKey(val day: LocalDate, val app: ShortVideoApp)

    private val buffered = mutableMapOf<TimeKey, Long>()
    private val snoozeSize = CounterConfig().snoozeSize

    suspend fun apply(effects: List<CounterEffect>, state: CounterState) {
        for (effect in effects) {
            when (effect) {
                is CounterEffect.Counted -> {
                    repository.recordReel(effect)
                    overlay.updateBubble(effect.app, effect.countToday, state.allowance(effect.app, snoozeSize))
                }
                is CounterEffect.TimeAdded -> addTime(effect)
                is CounterEffect.NudgeRequested ->
                    overlay.showNudge(effect.app, effect.countToday, state.allowance(effect.app, snoozeSize), effect.snoozesLeft)
                is CounterEffect.SnoozeAccepted -> {
                    repository.setSnoozes(effect.day, effect.app, effect.snoozesUsed)
                    overlay.hideNudge()
                    overlay.updateBubble(effect.app, state.countToday(effect.app), state.allowance(effect.app, snoozeSize))
                }
                is CounterEffect.SnoozeRejected ->
                    overlay.showNudge(effect.app, state.countToday(effect.app), state.allowance(effect.app, snoozeSize), 0)
                is CounterEffect.BubbleChanged -> {
                    val app = effect.app
                    if (app != null) {
                        overlay.showBubble(app, state.sessions[app]?.id ?: 0L, state.countToday(app), state.allowance(app, snoozeSize))
                    } else {
                        flushTime()
                        overlay.hideAll()
                    }
                }
            }
        }
    }

    /** Writes every buffered second. Called when leaving a screen and when the service stops. */
    suspend fun flushTime() {
        val pending = buffered.toMap()
        buffered.clear()
        for ((key, seconds) in pending) repository.addSeconds(key.day, key.app, seconds)
    }

    private suspend fun addTime(effect: CounterEffect.TimeAdded) {
        // A day change flushes whatever is still buffered for the old day.
        if (buffered.keys.any { it.day != effect.day }) flushTime()
        val key = TimeKey(effect.day, effect.app)
        val total = (buffered[key] ?: 0L) + effect.seconds
        if (total >= FLUSH_AFTER_SECONDS) {
            buffered.remove(key)
            repository.addSeconds(key.day, key.app, total)
        } else {
            buffered[key] = total
        }
    }

    companion object {
        const val FLUSH_AFTER_SECONDS = 15L
    }
}
