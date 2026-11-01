package com.assismohanty.awaken.reelcounter.inspector

import com.assismohanty.awaken.reelcounter.IG_PKG
import com.assismohanty.awaken.reelcounter.SCREEN
import com.assismohanty.awaken.reelcounter.T0
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.detect.Detection
import com.assismohanty.awaken.reelcounter.core.detect.ItemSignal
import com.assismohanty.awaken.reelcounter.core.detect.ScreenRule
import com.assismohanty.awaken.reelcounter.core.model.NodeSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NodeTreeDumperTest {
    private val tree = NodeSnapshot(
        key = "r", viewId = null, className = "FrameLayout", bounds = SCREEN, scrollable = false, visibleToUser = true,
        children = listOf(
            NodeSnapshot("p", "com.instagram.android:id/pager", "ViewPager", SCREEN, true, true),
        ),
    )

    @Test
    fun rendersHeaderTreeAndScrollLog() {
        val detection = Detection.ShortVideo(
            ShortVideoApp.INSTAGRAM,
            ScreenRule(listOf("a.b:id/c"), signal = ItemSignal.SCROLL_INDEX),
            viaFallback = true,
            childSignature = null,
        )
        val text = NodeTreeDumper.dump(IG_PKG, T0, SCREEN, detection, tree, listOf("10:00:00.100 src=x from=1 to=1 count=-1 dy=0"))
        val lines = text.lines()
        assertThat(lines[0]).isEqualTo(
            "# pkg=$IG_PKG at=$T0 screen=1080x2400 detection=ShortVideo(INSTAGRAM, viaFallback=true, signal=SCROLL_INDEX)",
        )
        assertThat(lines[1]).isEqualTo("FrameLayout id=- bounds=0,0,1080,2400 scroll=false vis=true children=1")
        assertThat(lines[2]).isEqualTo("  ViewPager id=com.instagram.android:id/pager bounds=0,0,1080,2400 scroll=true vis=true children=0")
        assertThat(lines[3]).isEqualTo("# scroll log (last 50 for this package)")
        assertThat(lines[4]).startsWith("10:00:00.100")
    }

    @Test
    fun noWindowIsReported() {
        val text = NodeTreeDumper.dump(IG_PKG, T0, SCREEN, Detection.NotShortVideo, null, emptyList())
        assertThat(text).contains("detection=NotShortVideo")
        assertThat(text).contains("(no window)")
    }
}
