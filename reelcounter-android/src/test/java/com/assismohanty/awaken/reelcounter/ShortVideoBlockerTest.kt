package com.assismohanty.awaken.reelcounter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.assismohanty.awaken.reelcounter.core.block.BlockAction
import com.assismohanty.awaken.reelcounter.data.BlockMode
import com.assismohanty.awaken.reelcounter.data.DetectorRepository
import com.assismohanty.awaken.reelcounter.data.ReelSessionGate
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.assismohanty.awaken.reelcounter.service.ShortVideoBlocker
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Blocking needs consent, a mode, a chosen app and a verified detector. The shipped detectors are unverified. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShortVideoBlockerTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val t0 = Instant.parse("2026-11-10T10:00:00Z")
    private val actions = mutableListOf<BlockAction>()

    private suspend fun TestScope.blocker(): Pair<ShortVideoBlocker, SettingsStore> {
        val settings = SettingsStore(newDataStore(StandardTestDispatcher(testScheduler), File(folder.root, "b.preferences_pb")))
        settings.setBlockConsent(true)
        settings.setBlockMode(BlockMode.ALWAYS)
        settings.setBlockApps(setOf(IG))
        val blocker = ShortVideoBlocker(settings, DetectorRepository(context), object : ReelSessionGate {
            override val sessionRunning = flowOf(false)
        })
        blocker.performer = { actions += it }
        return blocker to settings
    }

    @Test
    fun theShippedUnverifiedDetectorsBlockNothing() = runTest {
        val (blocker, _) = blocker()
        blocker.onLook(IG, t0)
        assertThat(actions).isEmpty()
    }

    @Test
    fun withoutConsentNothingIsBlocked() = runTest {
        val (blocker, settings) = blocker()
        settings.setBlockConsent(false)
        blocker.onLook(IG, t0)
        assertThat(actions).isEmpty()
    }
}
