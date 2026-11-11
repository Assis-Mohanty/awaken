package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HeightUnitTest {
    @Test
    fun feetAndInchesUseTheExactInch() {
        assertThat(HeightUnit.cmOf(5, 9.0)).isWithin(1e-9).of(175.26)
        assertThat(HeightUnit.cmOf(6, 0.0)).isWithin(1e-9).of(182.88)
        assertThat(HeightUnit.feetInches(175.26)).isEqualTo(5 to 9.0)
        assertThat(HeightUnit.feetInches(182.88)).isEqualTo(6 to 0.0)
    }

    @Test
    fun almostAFootRollsUpAndSmallHeightsStayInInches() {
        // 11.96 inches is 11.95+ when rounded to a tenth, so it is the next foot, not "5 ft 12.0 in".
        assertThat(HeightUnit.feetInches(HeightUnit.cmOf(5, 11.96))).isEqualTo(6 to 0.0)
        assertThat(HeightUnit.feetInches(HeightUnit.cmOf(0, 9.0))).isEqualTo(0 to 9.0)
    }

    @Test
    fun measurementsConvertThroughCentimetres() {
        assertThat(HeightUnit.CM.lengthFromCm(80.0)).isEqualTo(80.0)
        assertThat(HeightUnit.FT_IN.lengthFromCm(2.54)).isWithin(1e-12).of(1.0)
        listOf(10.0, 80.0, 101.6).forEach { cm -> assertThat(HeightUnit.FT_IN.lengthToCm(HeightUnit.FT_IN.lengthFromCm(cm))).isWithin(1e-9).of(cm) }
    }
}
