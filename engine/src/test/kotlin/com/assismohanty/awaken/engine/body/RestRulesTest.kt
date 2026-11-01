package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

class RestRulesTest {
    private val t0 = Instant.parse("2026-11-01T10:00:00Z")

    @Test
    fun `stepping up from off starts at the first value and stops at the maximum`() {
        assertThat(RestRules.stepUp(null)).isEqualTo(60)
        assertThat(RestRules.stepUp(60)).isEqualTo(75)
        assertThat(RestRules.stepUp(600)).isEqualTo(600)
        assertThat(RestRules.stepUp(595)).isEqualTo(600)
    }

    @Test
    fun `stepping down from the lowest step turns rest off`() {
        assertThat(RestRules.stepDown(75)).isEqualTo(60)
        assertThat(RestRules.stepDown(15)).isNull()
        assertThat(RestRules.stepDown(null)).isNull()
    }

    @Test
    fun `clamp drops zero and negatives and caps the top`() {
        assertThat(RestRules.clamp(0)).isNull()
        assertThat(RestRules.clamp(-5)).isNull()
        assertThat(RestRules.clamp(900)).isEqualTo(600)
        assertThat(RestRules.clamp(90)).isEqualTo(90)
    }

    @Test
    fun `remaining never goes negative`() {
        assertThat(RestRules.remaining(t0.plusSeconds(30), t0)).isEqualTo(Duration.ofSeconds(30))
        assertThat(RestRules.remaining(t0, t0.plusSeconds(5))).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `a rest that ended long ago does not chime`() {
        assertThat(RestRules.shouldChime(t0, t0.plusSeconds(2))).isTrue()
        assertThat(RestRules.shouldChime(t0, t0.plusSeconds(11))).isFalse()
    }
}
