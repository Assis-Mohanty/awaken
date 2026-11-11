package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

class FastingRulesTest {
    private val start = Instant.parse("2026-11-10T20:00:00Z")

    @Test
    fun onlyTwelveFourteenAndSixteenHourFastsExist() {
        assertThat(FastingRules.PRESET_HOURS).containsExactly(12, 14, 16).inOrder()
        assertThat(FastingRules.isPreset(18 * 60)).isFalse()
    }

    @Test
    fun runningThenReachedAndNeverNegative() {
        assertThat(FastingRules.state(null, 720, start)).isEqualTo(FastState.Idle)
        val running = FastingRules.state(start, 720, start.plus(Duration.ofHours(5))) as FastState.Running
        assertThat(running.remaining).isEqualTo(Duration.ofHours(7))
        val reached = FastingRules.state(start, 720, start.plus(Duration.ofHours(13))) as FastState.Reached
        assertThat(reached.elapsed).isEqualTo(Duration.ofHours(13))
        assertThat(FastingRules.state(start, 720, start.minusSeconds(60))).isInstanceOf(FastState.Running::class.java)
    }
}
