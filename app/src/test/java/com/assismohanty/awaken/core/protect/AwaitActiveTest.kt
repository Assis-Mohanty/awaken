package com.assismohanty.awaken.core.protect

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The service's wait for the screen to come back: a missed broadcast must not leave it waiting for ever (A7). */
@OptIn(ExperimentalCoroutinesApi::class)
class AwaitActiveTest {
    @Test
    fun aMissedScreenOnBroadcastIsCaughtByTheNextRecheck() = runTest {
        val active = MutableStateFlow(false)
        var screenOn = false
        var done = false
        launch {
            awaitActive(active, 5_000) { screenOn }
            done = true
        }
        runCurrent()
        // The screen comes on, but no broadcast says so.
        screenOn = true
        advanceTimeBy(4_000)
        assertThat(done).isFalse()
        advanceTimeBy(1_500)
        assertThat(done).isTrue()
        assertThat(active.value).isTrue()
    }

    @Test
    fun aBroadcastEndsTheWaitAtOnce() = runTest {
        val active = MutableStateFlow(false)
        var done = false
        launch {
            awaitActive(active, 5_000) { false }
            done = true
        }
        runCurrent()
        active.value = true
        runCurrent()
        assertThat(done).isTrue()
    }
}
