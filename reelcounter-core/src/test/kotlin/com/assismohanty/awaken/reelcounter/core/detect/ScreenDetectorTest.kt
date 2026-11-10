package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.model.Bounds
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScreenDetectorTest {
    private val pagerRule = rule(IG_PAGER, ItemSignal.PAGER_PAGE)

    private fun verified(vararg rules: ScreenRule) =
        ScreenDetector(detectorConfig(appDetector(ShortVideoApp.INSTAGRAM, true, *rules)))

    private fun unverified(vararg rules: ScreenRule) =
        ScreenDetector(detectorConfig(appDetector(ShortVideoApp.INSTAGRAM, false, *rules)))

    private fun screenWith(container: com.assismohanty.awaken.reelcounter.core.model.NodeSnapshot, vararg extra: com.assismohanty.awaken.reelcounter.core.model.NodeSnapshot) =
        source(node("root", children = listOf(container) + extra), IG_PKG)

    @Test
    fun matchesContainerIdWithCoverage() {
        val d = verified(pagerRule).detect(IG_PKG, screenWith(node("p", id = IG_PAGER)))
        assertThat(d).isInstanceOf(Detection.ShortVideo::class.java)
        assertThat((d as Detection.ShortVideo).viaFallback).isFalse()
        assertThat(d.app).isEqualTo(ShortVideoApp.INSTAGRAM)
    }

    @Test
    fun smallContainerDoesNotMatch() {
        val small = node("p", id = IG_PAGER, bounds = Bounds(0, 0, 1080, 1200))
        assertThat(verified(pagerRule).detect(IG_PKG, screenWith(small))).isEqualTo(Detection.NotShortVideo)
    }

    @Test
    fun excludeIdBlocksMatch() {
        val r = rule(IG_PAGER, ItemSignal.PAGER_PAGE, exclude = listOf("com.instagram.android:id/comments"))
        val d = verified(r).detect(
            IG_PKG,
            screenWith(node("p", id = IG_PAGER), node("x", id = "com.instagram.android:id/comments")),
        )
        assertThat(d).isEqualTo(Detection.NotShortVideo)
    }

    @Test
    fun invisibleContainerDoesNotMatch() {
        val hidden = node("p", id = IG_PAGER, visible = false)
        assertThat(verified(pagerRule).detect(IG_PKG, screenWith(hidden))).isEqualTo(Detection.NotShortVideo)
    }

    @Test
    fun unsupportedPackageNeverMatches() {
        val pkg = "com.example.app"
        assertThat(verified(pagerRule).detect(pkg, source(node("root", id = IG_PAGER), pkg)))
            .isEqualTo(Detection.NotShortVideo)
    }

    @Test
    fun nullNodesIsNotShortVideo() {
        assertThat(verified(pagerRule).detect(IG_PKG, null)).isEqualTo(Detection.NotShortVideo)
    }

    @Test
    fun childSignatureIsFullScreenChildKey() {
        val container = node("p", id = IG_PAGER, children = listOf(node("child-7")))
        val d = verified(pagerRule).detect(IG_PKG, screenWith(container)) as Detection.ShortVideo
        assertThat(d.childSignature).isEqualTo("child-7")
    }

    @Test
    fun itemContainerSignatureIsItsOwnKeyEvenWithNoVisibleChild() {
        val r = rule(IG_PAGER, ItemSignal.CHILD_SWAP).copy(itemIsContainer = true)
        val offscreen = node("c", visible = false)
        val d = verified(r).detect(IG_PKG, screenWith(node("item-3", id = IG_PAGER, children = listOf(offscreen)))) as Detection.ShortVideo
        assertThat(d.childSignature).isEqualTo("item-3")
    }

    @Test
    fun structuralFallbackMatchesFullScreenPager() {
        val d = unverified(rule("com.instagram.android:id/nonexistent", ItemSignal.PAGER_PAGE))
            .detect(IG_PKG, igPagerScreen())
        assertThat(d).isInstanceOf(Detection.ShortVideo::class.java)
        d as Detection.ShortVideo
        assertThat(d.viaFallback).isTrue()
        assertThat(d.rule.signalSourceIds).containsExactly(IG_PAGER)
        assertThat(d.childSignature).isEqualTo("c1")
    }

    @Test
    fun structuralFallbackRejectsFeedWithSmallChildren() {
        val small = node("c", bounds = Bounds(0, 0, 1080, 960))
        val screen = source(node("root", children = listOf(node("p", id = IG_PAGER, scrollable = true, children = listOf(small)))), IG_PKG)
        val d = unverified(rule("com.instagram.android:id/nonexistent", ItemSignal.PAGER_PAGE)).detect(IG_PKG, screen)
        assertThat(d).isEqualTo(Detection.NotShortVideo)
    }

    @Test
    fun structuralFallbackDisabledWhenVerified() {
        val d = verified(rule("com.instagram.android:id/nonexistent", ItemSignal.PAGER_PAGE))
            .detect(IG_PKG, igPagerScreen())
        assertThat(d).isEqualTo(Detection.NotShortVideo)
    }

    @Test
    fun tiktokSecondPackageMatchesSameApp() {
        val id = "com.ss.android.ugc.trill:id/feed_pager"
        val detector = ScreenDetector(
            detectorConfig(appDetector(ShortVideoApp.TIKTOK, true, rule(id, ItemSignal.PAGER_PAGE))),
        )
        val pkg = "com.ss.android.ugc.trill"
        val d = detector.detect(pkg, source(node("root", children = listOf(node("p", id = id))), pkg))
        assertThat((d as Detection.ShortVideo).app).isEqualTo(ShortVideoApp.TIKTOK)
    }
}
