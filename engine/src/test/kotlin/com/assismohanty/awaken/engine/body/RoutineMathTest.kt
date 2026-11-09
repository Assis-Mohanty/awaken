package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RoutineMathTest {
    @Test
    fun `each set takes 40 s plus the rest, rounded to the minute`() {
        // 3 x (40 + 120) = 480 s = 8 min; 3 x (40 + 60) = 300 s = 5 min.
        assertThat(RoutineMath.estimateMinutes(listOf(RoutineLine(3, 120), RoutineLine(3, 60)))).isEqualTo(13)
        // No rest: 3 x 40 = 120 s = 2 min.
        assertThat(RoutineMath.estimateMinutes(listOf(RoutineLine(3, null)))).isEqualTo(2)
    }

    @Test
    fun `a superset partner followed by another does not rest`() {
        val lines = listOf(RoutineLine(3, 90, supersetWithNext = true), RoutineLine(3, 90))
        // 3 x 40 + 3 x (40 + 90) = 510 s -> 9 min (8.5 rounds up).
        assertThat(RoutineMath.estimateMinutes(lines)).isEqualTo(9)
    }

    @Test
    fun `an empty routine takes no time`() {
        assertThat(RoutineMath.estimateMinutes(emptyList())).isEqualTo(0)
    }
}
