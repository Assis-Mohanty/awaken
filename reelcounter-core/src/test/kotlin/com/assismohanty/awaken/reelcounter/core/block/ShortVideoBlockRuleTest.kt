package com.assismohanty.awaken.reelcounter.core.block

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.jupiter.api.Test

class ShortVideoBlockRuleTest {
    private val t0 = Instant.parse("2026-11-10T10:00:00Z")
    private val ig = ShortVideoApp.INSTAGRAM
    private val rule = ShortVideoBlockRule(blocked = setOf(ig), verified = setOf(ig))

    private fun ShortVideoBlockRule.look(state: BlockState, at: Long, app: ShortVideoApp? = ig, on: Boolean = true, ruleOn: Boolean = true) =
        step(state, app, on, ruleOn, t0.plusMillis(at))

    @Test
    fun firstLookPressesBack() {
        assertThat(rule.look(BlockState(), 0).action).isEqualTo(BlockAction.BACK)
    }

    @Test
    fun atMostOneBackPerSecond() {
        val first = rule.look(BlockState(), 0)
        assertThat(rule.look(first.state, 999).action).isEqualTo(BlockAction.NONE)
        assertThat(rule.look(first.state, 1_000).action).isEqualTo(BlockAction.BACK)
    }

    @Test
    fun goesHomeWhenTwoBacksDidNotClearTheScreen() {
        val one = rule.look(BlockState(), 0)
        val two = rule.look(one.state, 1_000)
        val third = rule.look(two.state, 2_000)
        assertThat(third.action).isEqualTo(BlockAction.HOME)
        assertThat(third.state.backs).isEqualTo(0)
    }

    @Test
    fun leavingTheScreenForgetsTheBacks() {
        val one = rule.look(BlockState(), 0)
        val gone = rule.look(one.state, 500, on = false)
        assertThat(gone).isEqualTo(BlockStep(BlockState(), BlockAction.NONE))
        assertThat(rule.look(gone.state, 600).action).isEqualTo(BlockAction.BACK)
    }

    @Test
    fun doesNothingWhenTheRuleIsOff() {
        assertThat(rule.look(BlockState(), 0, ruleOn = false).action).isEqualTo(BlockAction.NONE)
    }

    @Test
    fun doesNothingForAnAppThatIsNotChosen() {
        assertThat(rule.look(BlockState(), 0, app = ShortVideoApp.TIKTOK).action).isEqualTo(BlockAction.NONE)
        assertThat(rule.look(BlockState(), 0, app = null).action).isEqualTo(BlockAction.NONE)
    }

    @Test
    fun doesNothingWhileTheDetectorIsUnverified() {
        val unverified = ShortVideoBlockRule(blocked = setOf(ig), verified = emptySet())
        assertThat(unverified.step(BlockState(), ig, true, true, t0).action).isEqualTo(BlockAction.NONE)
    }
}
