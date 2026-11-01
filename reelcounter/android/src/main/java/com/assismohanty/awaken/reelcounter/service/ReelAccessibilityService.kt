package com.assismohanty.awaken.reelcounter.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.detect.RawSignal
import com.assismohanty.awaken.reelcounter.core.model.Bounds
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.inspector.InspectorHooks
import com.assismohanty.awaken.reelcounter.overlay.OverlayController
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

/**
 * Watches the five supported apps. It reads only view ids, class names, bounds and flags, and hands abstract
 * signals to [CounterRunner]. It never reads or logs node text.
 */
@AndroidEntryPoint
class ReelAccessibilityService : AccessibilityService() {
    @Inject lateinit var runner: CounterRunner
    @Inject lateinit var clock: ReelClock
    @Inject lateinit var status: ServiceStatus
    @Inject lateinit var overlay: OverlayController
    @Inject lateinit var inspector: InspectorHooks

    private var adapter: EventAdapter? = null
    private var receiver: ScreenStateReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay.attach(this)
        adapter = EventAdapter(clock) { pkg -> AccessibilityNodeSource(this, pkg, screenBounds()) }
        runner.start { at -> probe(at) }
        registerScreenReceiver()
        status.setConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val signal = adapter?.toSignal(event) ?: return
        when (signal) {
            is RawSignal.ViewScrolled -> inspector.onScroll(signal.packageName, signal.scroll, signal.at)
            is RawSignal.WindowStateChanged -> inspector.onWindowEvent(signal.packageName, signal.nodes, signal.at)
            is RawSignal.WindowContentChanged -> inspector.onWindowEvent(signal.packageName, signal.nodes, signal.at)
            else -> Unit
        }
        runner.submitSignal(signal)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        status.setConnected(false)
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        runner.stop()
        inspector.onServiceStopped()
        overlay.detach()
        adapter = null
    }

    private fun registerScreenReceiver() {
        val r = ScreenStateReceiver(
            onScreenOff = { runner.submitSignal(RawSignal.ScreenOff(clock.now())) },
            onScreenOn = {
                val fg = foregroundPackage()
                val nodes = fg?.takeIf { it in ShortVideoApp.allPackages }
                    ?.let { AccessibilityNodeSource(this, it, screenBounds()) }
                runner.submitSignal(RawSignal.ScreenOn(fg, nodes, clock.now()))
            },
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    private fun probe(at: Instant): RawSignal.Probe {
        val fg = foregroundPackage()
        val nodes = fg?.takeIf { it in ShortVideoApp.allPackages }?.let { AccessibilityNodeSource(this, it, screenBounds()) }
        return RawSignal.Probe(fg, nodes, at)
    }

    private fun foregroundPackage(): String? = runCatching {
        windows.firstOrNull { it.isActive }?.root?.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun screenBounds(): Bounds {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b: Rect = wm.currentWindowMetrics.bounds
            return Bounds(b.left, b.top, b.right, b.bottom)
        }
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return Bounds(0, 0, dm.widthPixels, dm.heightPixels)
    }
}
