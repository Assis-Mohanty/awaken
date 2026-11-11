package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LabelMathTest {
    @Test
    fun kilojoulesBecomeKilocalories() {
        assertThat(LabelMath.kcalFromKj(1_000.0)).isWithin(1e-4).of(239.0057)
        assertThat(LabelMath.kcalFromKj(4.184)).isWithin(1e-12).of(1.0)
    }

    @Test
    fun saltBecomesSodiumInMilligrams() {
        // 1 g of salt is 0.4 g of sodium.
        assertThat(LabelMath.sodiumMgFromSalt(1.0)).isWithin(1e-9).of(400.0)
        assertThat(LabelMath.sodiumMgFromSalt(2.5)).isWithin(1e-9).of(1_000.0)
    }

    @Test
    fun perServingValuesScaleToPer100GramsAndABadServingIsRefused() {
        assertThat(LabelMath.per100(6.0, 30.0)).isWithin(1e-9).of(20.0)
        assertThat(LabelMath.per100(6.0, 0.0)).isNull()
        assertThat(LabelMath.per100(6.0, -5.0)).isNull()
    }
}
