package com.assismohanty.awaken.reelcounter.core.limits

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DailyLimitTest {
    @Test
    fun `daily limit is the sum of the per-app limits, or null when none is set`() {
        assertThat(combinedDailyLimit(emptyList())).isNull()
        assertThat(combinedDailyLimit(listOf(30))).isEqualTo(30)
        assertThat(combinedDailyLimit(listOf(30, 20, 50))).isEqualTo(100)
        assertThat(combinedDailyLimit(listOf(0, 40))).isEqualTo(40)
        assertThat(combinedDailyLimit(listOf(Int.MAX_VALUE, Int.MAX_VALUE))).isEqualTo(Int.MAX_VALUE)
    }
}
