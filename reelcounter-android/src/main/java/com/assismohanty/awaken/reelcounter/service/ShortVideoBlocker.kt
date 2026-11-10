package com.assismohanty.awaken.reelcounter.service

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.block.BlockAction
import com.assismohanty.awaken.reelcounter.core.block.BlockState
import com.assismohanty.awaken.reelcounter.core.block.ShortVideoBlockRule
import com.assismohanty.awaken.reelcounter.data.BlockMode
import com.assismohanty.awaken.reelcounter.data.DetectorRepository
import com.assismohanty.awaken.reelcounter.data.ReelSessionGate
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs [ShortVideoBlockRule] on every look and presses Back or Home through [performer], which the service sets while it
 * runs. Acts only with the hunter's consent, a chosen app and a verified detector. Called from the counter thread, under its lock.
 */
@Singleton
class ShortVideoBlocker @Inject constructor(
    private val settings: SettingsStore,
    private val detectors: DetectorRepository,
    private val gate: ReelSessionGate,
) {
    @Volatile
    var performer: ((BlockAction) -> Unit)? = null

    private var state = BlockState()

    suspend fun onLook(screenApp: ShortVideoApp?, now: Instant) {
        val act = performer ?: return
        val mode = settings.blockMode.first()
        // Cheap exit: this runs on every probe, so do not read the rest while nothing is on.
        if (mode == BlockMode.OFF || screenApp == null || !settings.blockConsent.first()) {
            state = BlockState()
            return
        }
        val ruleOn = mode == BlockMode.ALWAYS || gate.sessionRunning.first()
        val verified = detectors.config.value.config.apps.filter { it.verified }.map { it.app }.toSet()
        val step = ShortVideoBlockRule(settings.blockApps.first(), verified).step(state, screenApp, true, ruleOn, now)
        state = step.state
        if (step.action != BlockAction.NONE) act(step.action)
    }
}
