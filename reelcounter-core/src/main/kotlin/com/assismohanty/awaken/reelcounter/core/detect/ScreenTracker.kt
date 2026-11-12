package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.count.CounterEvent
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import java.time.Duration
import java.time.Instant

data class TrackerConfig(
    val contentThrottleMillis: Long = 300,
    val childSwapWindowMillis: Long = 1_000,
    /** A look that misses the reel while its app stays in front (a swipe in flight) ends the visit only after this long. */
    val missLeaveMillis: Long = 1_000,
)

data class TrackerOutput(val events: List<CounterEvent>, val recheckAt: Instant? = null)

/** Maps raw accessibility-level signals onto [CounterEvent]s. Pure; see PLAN.md section 5.5. */
class ScreenTracker(
    private var detector: ScreenDetector,
    private val config: TrackerConfig = TrackerConfig(),
) {
    var activeApp: ShortVideoApp? = null
        private set

    /**
     * The short-video app in front, even while its screen is not recognised right now (a swipe in flight, a comment sheet).
     * The runner keeps probing while this is set, so a single missed or mid-swipe look can never end the counting.
     */
    var foregroundApp: ShortVideoApp? = null
        private set

    /** The reel screen is active and was matched by the latest look (not inside a tolerated miss). */
    val reelOnScreen: ShortVideoApp? get() = activeApp.takeIf { missSince == null }

    private var lastEvalAt: Instant? = null
    private var missSince: Instant? = null
    private val trackers = mutableMapOf<ShortVideoApp, ItemPositionTracker>()

    fun replaceDetector(detector: ScreenDetector) {
        this.detector = detector
    }

    fun onSignal(signal: RawSignal): TrackerOutput {
        val events = mutableListOf<CounterEvent>()
        when (signal) {
            is RawSignal.WindowStateChanged -> foregroundApp = ShortVideoApp.fromPackage(signal.packageName) ?: foregroundApp
            is RawSignal.WindowContentChanged -> foregroundApp = ShortVideoApp.fromPackage(signal.packageName) ?: foregroundApp
            is RawSignal.ViewScrolled -> foregroundApp = ShortVideoApp.fromPackage(signal.packageName) ?: foregroundApp
            is RawSignal.Probe -> foregroundApp = ShortVideoApp.fromPackage(signal.foregroundPackage)
            is RawSignal.ScreenOff -> foregroundApp = null
            is RawSignal.ScreenOn -> foregroundApp = ShortVideoApp.fromPackage(signal.foregroundPackage)
        }
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
                    val t = tracker(app)
                    // Scrolls arrive while the swipe is moving, with both pages on screen: the surest moment to see which way
                    // it goes. A look on a timer can miss that moment entirely, and then a swipe back would read as forward.
                    if (t.childSwap) (detector.detect(signal.packageName, signal.nodes) as? Detection.ShortVideo)?.let { t.onPages(it.pages, signal.at) }
                    val adopting = t.adopting
                    t.onScroll(signal.scroll, signal.at)?.let {
                        events += if (adopting) CounterEvent.ItemAdopted(app, it, signal.at) else CounterEvent.ItemChanged(app, it, signal.at)
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
        missSince = null
    }

    private fun evaluate(pkg: String?, nodes: NodeSource?, at: Instant, events: MutableList<CounterEvent>) {
        lastEvalAt = at
        when (val d = detector.detect(pkg, nodes)) {
            is Detection.ShortVideo -> {
                missSince = null
                val t = tracker(d.app)
                t.setRule(d.rule)
                if (activeApp != d.app) {
                    activeApp = d.app
                    t.startVisit()
                    if (d.rule.signal == ItemSignal.CHILD_SWAP) {
                        t.resetSignature()
                        t.onChildSignature(d.childSignature, at)
                    }
                    events += CounterEvent.ScreenEntered(d.app, at, t.index)
                } else if (d.rule.signal == ItemSignal.CHILD_SWAP) {
                    t.onPages(d.pages, at)
                    t.onChildSignature(d.childSignature, at)?.let { events += CounterEvent.ItemChanged(d.app, it, at) }
                }
            }
            Detection.NotShortVideo -> {
                val app = activeApp
                if (app != null && ShortVideoApp.fromPackage(pkg) == app) {
                    // Keep the visit (and the pre-swipe reel's identity) through a short miss; leave once it lasts.
                    val since = missSince ?: at.also { missSince = it }
                    if (Duration.between(since, at).toMillis() >= config.missLeaveMillis) leave(at, events)
                } else {
                    leave(at, events)
                }
            }
        }
    }
}
