package com.assismohanty.awaken.core.designsystem.components

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Test

class StopwatchReadingTest {
    @Test
    fun splitsIntoHoursMinutesSecondsAndMillis() {
        val reading = Duration.ofHours(1).plusMinutes(2).plusSeconds(3).plusMillis(45).toStopwatchReading()
        assertThat(reading).isEqualTo(StopwatchReading(1, 2, 3, 45))
        assertThat(reading.spoken).isEqualTo("1:02:03.045")
    }

    @Test
    fun negativeIsZero() {
        assertThat(Duration.ofSeconds(-4).toStopwatchReading()).isEqualTo(StopwatchReading(0, 0, 0, 0))
    }

    @Test
    fun overNinetyMinutesRollsIntoHours() {
        assertThat(Duration.ofMinutes(125).toStopwatchReading().hours).isEqualTo(2)
    }
}
