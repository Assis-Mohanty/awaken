package com.assismohanty.awaken.reelcounter.service

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.count.CounterEffect
import com.assismohanty.awaken.reelcounter.core.count.CounterState
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What the counter needs from the overlay windows. Implemented by `OverlayController`. */
interface OverlaySink {
    fun showBubble(app: ShortVideoApp)
    fun hideAll()
    fun showNudge(app: ShortVideoApp, countToday: Int, snoozesLeft: Int)
    fun hideNudge()
}

/** Asks for a widget refresh. Implemented by the widget in Phase 5. */
fun interface WidgetSink {
    fun request()
}

/** Turns [CounterEffect]s into storage writes and overlay calls. */
@Singleton
class EffectApplier @Inject constructor(
    private val repository: ReelRepository,
    private val overlay: OverlaySink,
    private val widget: WidgetSink,
) {
    private data class TimeKey(val day: LocalDate, val app: ShortVideoApp)

    private val buffered = mutableMapOf<TimeKey, Long>()

    suspend fun apply(effects: List<CounterEffect>, state: CounterState) {
        for (effect in effects) {
            when (effect) {
                is CounterEffect.Counted -> {
                    repository.recordReel(effect)
                    widget.request()
                }
                is CounterEffect.TimeAdded -> addTime(effect)
                is CounterEffect.NudgeRequested -> overlay.showNudge(effect.app, effect.countToday, effect.snoozesLeft)
                is CounterEffect.SnoozeAccepted -> {
                    repository.setSnoozes(effect.day, effect.app, effect.snoozesUsed)
                    overlay.hideNudge()
                }
                is CounterEffect.SnoozeRejected -> overlay.showNudge(effect.app, state.countToday(effect.app), 0)
                is CounterEffect.BubbleChanged -> {
                    val app = effect.app
                    if (app != null) {
                        overlay.showBubble(app)
                    } else {
                        flushTime()
                        overlay.hideAll()
                    }
                    widget.request()
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
