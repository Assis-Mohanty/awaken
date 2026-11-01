package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.count.CounterEvent
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import java.time.Duration
import java.time.Instant

data class TrackerConfig(val contentThrottleMillis: Long = 300, val childSwapWindowMillis: Long = 1_000)

data class TrackerOutput(val events: List<CounterEvent>, val recheckAt: Instant? = null)

/** Maps raw accessibility-level signals onto [CounterEvent]s. Pure; see PLAN.md section 5.5. */
class ScreenTracker(
    private var detector: ScreenDetector,
    private val config: TrackerConfig = TrackerConfig(),
) {
    var activeApp: ShortVideoApp? = null
        private set

    private var lastEvalAt: Instant? = null
    private val trackers = mutableMapOf<ShortVideoApp, ItemPositionTracker>()

    fun replaceDetector(detector: ScreenDetector) {
        this.detector = detector
    }

    fun onSignal(signal: RawSignal): TrackerOutput {
        val events = mutableListOf<CounterEvent>()
        when (signal) {
            is RawSignal.WindowStateChanged -> evaluate(signal.packageName, signal.nodes, signal.at, events)
            is RawSignal.WindowContentChanged -> {
                throttleRecheck(signal.at)?.let { return TrackerOutput(emptyList(), it) }
                evaluate(signal.packageName, signal.nodes, signal.at, events)
            }
            is RawSignal.ViewScrolled -> {
                val app = ShortVideoApp.fromPackage(signal.packageName)
                if (app != null && activeApp != app) {
                    throttleRecheck(signal.at)?.let { return TrackerOutput(emptyList(), it) }
                    evaluate(signal.packageName, signal.nodes, signal.at, events)
                }
                if (app != null && activeApp == app) {
                    tracker(app).onScroll(signal.scroll, signal.at)?.let {
                        events += CounterEvent.ItemChanged(app, it, signal.at)
                    }
                }
            }
            is RawSignal.Probe -> {
                val fg = ShortVideoApp.fromPackage(signal.foregroundPackage)
                if (activeApp != null && fg != activeApp) leave(signal.at, events)
                if (fg != null) evaluate(signal.foregroundPackage, signal.nodes, signal.at, events)
                events += CounterEvent.Tick(signal.at)
            }
            is RawSignal.ScreenOff -> if (activeApp != null) leave(signal.at, events)
            is RawSignal.ScreenOn -> {
                if (ShortVideoApp.fromPackage(signal.foregroundPackage) != null) {
                    evaluate(signal.foregroundPackage, signal.nodes, signal.at, events)
                }
            }
        }
        return TrackerOutput(events)
    }

    private fun tracker(app: ShortVideoApp) =
        trackers.getOrPut(app) { ItemPositionTracker(config.childSwapWindowMillis) }

    private fun throttleRecheck(at: Instant): Instant? {
        val last = lastEvalAt ?: return null
        return if (Duration.between(last, at).toMillis() < config.contentThrottleMillis) {
            last.plusMillis(config.contentThrottleMillis)
        } else {
            null
        }
    }

    private fun leave(at: Instant, events: MutableList<CounterEvent>) {
        val app = activeApp ?: return
        events += CounterEvent.ScreenLeft(app, at)
        activeApp = null
    }

    private fun evaluate(pkg: String?, nodes: NodeSource?, at: Instant, events: MutableList<CounterEvent>) {
        lastEvalAt = at
        when (val d = detector.detect(pkg, nodes)) {
            is Detection.ShortVideo -> {
                val t = tracker(d.app)
                t.setRule(d.rule)
                if (activeApp != d.app) {
                    activeApp = d.app
                    if (d.rule.signal == ItemSignal.CHILD_SWAP) {
                        t.resetSignature()
                        t.onChildSignature(d.childSignature, at)
                    }
                    events += CounterEvent.ScreenEntered(d.app, at, t.index)
                } else if (d.rule.signal == ItemSignal.CHILD_SWAP) {
                    t.onChildSignature(d.childSignature, at)?.let { events += CounterEvent.ItemChanged(d.app, it, at) }
                }
            }
            Detection.NotShortVideo -> leave(at, events)
        }
    }
}
