package com.assismohanty.awaken.reelcounter

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.data.BubbleEdge
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.store() =
        SettingsStore(newDataStore(StandardTestDispatcher(testScheduler), folder.newFile("s.preferences_pb").also { it.delete() }))

    @Test
    fun defaults() = runTest {
        val s = store()
        assertThat(s.dayStartMinutes.first()).isEqualTo(240)
        assertThat(s.limits.first()).isEmpty()
        assertThat(s.bubbleEnabled.first()).isTrue()
        assertThat(s.bubbleEdge.first()).isEqualTo(BubbleEdge.END)
        assertThat(s.bubbleYFraction.first()).isEqualTo(0.35f)
        assertThat(s.setupCompleted.first()).isFalse()
        assertThat(s.lastHeartbeatMillis.first()).isNull()
        assertThat(s.inspectorEnabled.first()).isFalse()
    }

    @Test
    fun limitsRoundTripAndRemovingDeletesTheKey() = runTest {
        val s = store()
        s.setLimit(ShortVideoApp.INSTAGRAM, 50)
        s.setLimit(ShortVideoApp.TIKTOK, 20)
        assertThat(s.limits.first()).containsExactly(ShortVideoApp.INSTAGRAM, 50, ShortVideoApp.TIKTOK, 20)
        s.setLimit(ShortVideoApp.INSTAGRAM, null)
        assertThat(s.limits.first()).containsExactly(ShortVideoApp.TIKTOK, 20)
    }

    @Test
    fun bubblePositionIsStoredAndClamped() = runTest {
        val s = store()
        s.setBubblePosition(BubbleEdge.START, 1.7f)
        assertThat(s.bubbleEdge.first()).isEqualTo(BubbleEdge.START)
        assertThat(s.bubbleYFraction.first()).isEqualTo(1f)
    }

    @Test
    fun otherValuesRoundTrip() = runTest {
        val s = store()
        s.setDayStartMinutes(0)
        s.setBubbleEnabled(false)
        s.setSetupCompleted(true)
        s.setLastHeartbeat(1234L)
        s.setInspectorEnabled(true)
        assertThat(s.dayStartMinutes.first()).isEqualTo(0)
        assertThat(s.bubbleEnabled.first()).isFalse()
        assertThat(s.setupCompleted.first()).isTrue()
        assertThat(s.lastHeartbeatMillis.first()).isEqualTo(1234L)
        assertThat(s.inspectorEnabled.first()).isTrue()
    }
}
