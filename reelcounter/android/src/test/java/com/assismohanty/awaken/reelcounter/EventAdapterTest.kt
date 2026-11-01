package com.assismohanty.awaken.reelcounter

import android.view.accessibility.AccessibilityEvent
import com.assismohanty.awaken.reelcounter.core.detect.RawSignal
import com.assismohanty.awaken.reelcounter.core.time.FixedReelClock
import com.assismohanty.awaken.reelcounter.service.EventAdapter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventAdapterTest {
    private val adapter = EventAdapter(FixedReelClock(T0)) { null }

    @Suppress("DEPRECATION")
    private fun event(type: Int, pkg: String?, init: AccessibilityEvent.() -> Unit = {}): AccessibilityEvent =
        AccessibilityEvent.obtain(type).apply {
            packageName = pkg
            init()
        }

    @Test
    fun windowStateChangedMapsToSignal() {
        val s = adapter.toSignal(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, IG_PKG))
        assertThat(s).isInstanceOf(RawSignal.WindowStateChanged::class.java)
        assertThat((s as RawSignal.WindowStateChanged).packageName).isEqualTo(IG_PKG)
        assertThat(s.at).isEqualTo(T0)
    }

    @Test
    fun windowContentChangedMapsToSignal() {
        val s = adapter.toSignal(event(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, IG_PKG))
        assertThat(s).isInstanceOf(RawSignal.WindowContentChanged::class.java)
    }

    @Test
    fun scrollFieldsAreCopied() {
        val s = adapter.toSignal(
            event(AccessibilityEvent.TYPE_VIEW_SCROLLED, IG_PKG) {
                fromIndex = 3
                toIndex = 4
                itemCount = 9
                scrollDeltaY = 1200
            },
        ) as RawSignal.ViewScrolled
        assertThat(s.scroll.fromIndex).isEqualTo(3)
        assertThat(s.scroll.toIndex).isEqualTo(4)
        assertThat(s.scroll.itemCount).isEqualTo(9)
        assertThat(s.scroll.scrollDeltaY).isEqualTo(1200)
        assertThat(s.scroll.sourceViewId).isNull()
    }

    @Test
    fun unsupportedPackageIsIgnored() {
        assertThat(adapter.toSignal(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.example.app"))).isNull()
        assertThat(adapter.toSignal(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, null))).isNull()
    }

    @Test
    fun otherEventTypesAreIgnored() {
        assertThat(adapter.toSignal(event(AccessibilityEvent.TYPE_VIEW_CLICKED, IG_PKG))).isNull()
    }
}
