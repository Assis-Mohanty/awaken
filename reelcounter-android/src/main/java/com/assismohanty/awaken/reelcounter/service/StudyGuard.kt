package com.assismohanty.awaken.reelcounter.service

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.block.BlockAction
import com.assismohanty.awaken.reelcounter.core.block.BlockState
import com.assismohanty.awaken.reelcounter.core.block.ShortVideoBlockRule
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import com.assismohanty.awaken.reelcounter.core.study.ChannelMatch
import com.assismohanty.awaken.reelcounter.core.study.StudyDecision
import com.assismohanty.awaken.reelcounter.data.DetectorRepository
import com.assismohanty.awaken.reelcounter.data.ReelSessionGate
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Study mode (docs/protect/PLAN.md 4.2). While a focus session or Lockdown runs and the list is on, reads the
 * channel name from the one verified node id, compares it with the list and drops it: the text is never stored or logged.
 * Not on the list means Back; unreadable means the video plays (fail open). Does nothing until the detector entry says
 * `channelVerified`, so an unchecked id can never read or block anything. Called from the counter thread, under its lock.
 */
@Singleton
class StudyGuard @Inject constructor(
    private val settings: SettingsStore,
    private val detectors: DetectorRepository,
    private val gate: ReelSessionGate,
    private val blocker: ShortVideoBlocker,
) {
    private var state = BlockState()
    private var lastLookAt: Instant? = null
    private val rule = ShortVideoBlockRule(blocked = setOf(ShortVideoApp.YOUTUBE), verified = setOf(ShortVideoApp.YOUTUBE))

    suspend fun onLook(app: ShortVideoApp?, nodes: NodeSource?, now: Instant) {
        if (app != ShortVideoApp.YOUTUBE || nodes == null || !blocker.hasPerformer) return reset()
        // Reading is the costly part, so look at most once per interval.
        lastLookAt?.let { if (Duration.between(it, now) < LOOK_GAP) return }
        if (!settings.studyOn.first() || !settings.studyConsent.first()) return reset()
        val youtube = detectors.config.value.config.apps.firstOrNull { it.app == ShortVideoApp.YOUTUBE }
        if (youtube == null || !youtube.channelVerified || youtube.channelNameIds.isEmpty()) return reset()
        if (!gate.sessionRunning.first()) return reset()
        lastLookAt = now
        val shown = youtube.channelNameIds.firstNotNullOfOrNull { nodes.textOf(it) }
        val decision = ChannelMatch.decide(shown, settings.studyChannels.first())
        val step = rule.step(state, ShortVideoApp.YOUTUBE, decision == StudyDecision.BLOCK, ruleOn = true, now = now)
        state = step.state
        if (step.action != BlockAction.NONE) blocker.perform(step.action)
    }

    private fun reset() {
        state = BlockState()
        lastLookAt = null
    }

    private companion object {
        val LOOK_GAP: Duration = Duration.ofMillis(500)
    }
}
