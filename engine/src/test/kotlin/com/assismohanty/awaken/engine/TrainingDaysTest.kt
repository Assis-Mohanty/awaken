package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class TrainingDaysTest {
    @Test
    fun `days round trip through the mask`() {
        for (mask in 0..127) assertThat(TrainingDays.mask(TrainingDays.days(mask))).isEqualTo(mask)
        assertThat(TrainingDays.mask(setOf(1, 3, 5))).isEqualTo(0b10101)
        assertThat(TrainingDays.mask(setOf(7))).isEqualTo(64)
        assertThat(TrainingDays.days(0)).isEmpty()
    }

    @Test
    fun `days outside Monday to Sunday are ignored`() {
        assertThat(TrainingDays.mask(setOf(0, 8, 2))).isEqualTo(2)
    }

    @Test
    fun `a date is a training day when its weekday is in the mask`() {
        val monday = LocalDate.of(2026, 11, 2)
        val mask = TrainingDays.mask(setOf(1, 3))
        assertThat(TrainingDays.isTrainingDay(mask, monday)).isTrue()
        assertThat(TrainingDays.isTrainingDay(mask, monday.plusDays(1))).isFalse()
        assertThat(TrainingDays.isTrainingDay(mask, monday.plusDays(2))).isTrue()
        assertThat(TrainingDays.isTrainingDay(TrainingDays.NONE, monday)).isFalse()
    }
}
