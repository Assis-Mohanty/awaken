package com.assismohanty.awaken.reelcounter.core.block

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import java.time.Duration
import java.time.Instant

/** What the service does about a short-video screen right now. */
enum class BlockAction { NONE, BACK, HOME }

/** What the rule remembers between looks: when it last acted and how many Backs in a row did not clear the screen. */
data class BlockState(val lastActionAt: Instant? = null, val backs: Int = 0)

data class BlockStep(val state: BlockState, val action: BlockAction)

/**
 * Short-video blocking (docs/protect/PLAN.md 4.1). A fixed script, no judgement: when a short-video screen of a chosen
 * app is in front and the rule is on, press Back at most once per [minGap]; if the screen is still there after [maxBacks]
 * Backs, go Home. Acts only on apps whose detector is verified, so an unchecked id can never block anything.
 * The rest of the app is untouched: the rule is told only whether a short-video screen is showing.
 */
class ShortVideoBlockRule(
    private val blocked: Set<ShortVideoApp>,
    private val verified: Set<ShortVideoApp>,
    private val minGap: Duration = MIN_GAP,
    private val maxBacks: Int = MAX_BACKS,
) {
    /** [onShortVideoScreen] is the detector's answer for [app]; [ruleOn] is the "during sessions / always" choice. */
    fun step(state: BlockState, app: ShortVideoApp?, onShortVideoScreen: Boolean, ruleOn: Boolean, now: Instant): BlockStep {
        val applies = ruleOn && onShortVideoScreen && app != null && app in blocked && app in verified
        if (!applies) return BlockStep(BlockState(), BlockAction.NONE)
        val last = state.lastActionAt
        if (last != null && Duration.between(last, now) < minGap) return BlockStep(state, BlockAction.NONE)
        return if (state.backs >= maxBacks) {
            BlockStep(BlockState(lastActionAt = now), BlockAction.HOME)
        } else {
            BlockStep(BlockState(lastActionAt = now, backs = state.backs + 1), BlockAction.BACK)
        }
    }

    companion object {
        val MIN_GAP: Duration = Duration.ofSeconds(1)
        const val MAX_BACKS = 2
    }
}
