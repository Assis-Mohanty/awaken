package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WeightUnitTest {
    @Test
    fun kilogramsAreUnchangedAndPoundsUseTheExactDefinition() {
        assertThat(WeightUnit.KG.fromKg(80.0)).isEqualTo(80.0)
        assertThat(WeightUnit.KG.toKg(80.0)).isEqualTo(80.0)
        assertThat(WeightUnit.LB.fromKg(0.45359237)).isWithin(1e-12).of(1.0)
        assertThat(WeightUnit.LB.fromKg(80.0)).isWithin(1e-3).of(176.37)
    }

    @Test
    fun aRoundTripKeepsTheWeight() {
        listOf(30.0, 64.2, 80.0, 199.9).forEach { kg ->
            assertThat(WeightUnit.LB.toKg(WeightUnit.LB.fromKg(kg))).isWithin(1e-9).of(kg)
        }
    }
}
