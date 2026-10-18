package com.assismohanty.awaken.core.data.clock

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class SystemClockTest {
    @Test
    fun nowIsMonotonicAcrossCalls() {
        val clock: Clock = SystemClock()
        val first = clock.now()
        val second = clock.now()
        assertThat(second).isAtLeast(first)
        assertThat(first).isGreaterThan(Instant.EPOCH)
    }
}
