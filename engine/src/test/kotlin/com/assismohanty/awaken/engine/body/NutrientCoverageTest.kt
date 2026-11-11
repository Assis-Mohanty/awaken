package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NutrientCoverageTest {
    private fun row(kcal: Double, vararg more: Pair<Nutrient, Double>) = Nutrients(mapOf(Nutrient.KCAL to kcal) + more)

    @Test
    fun theShareIsByCaloriesOfRowsThatHaveTheNutrient() {
        val rows = listOf(row(600.0, Nutrient.IRON to 3.0), row(400.0))
        assertThat(NutrientCoverage.share(rows, Nutrient.IRON)).isWithin(1e-9).of(0.6)
        assertThat(NutrientCoverage.isPartial(0.6)).isTrue()
    }

    @Test
    fun aZeroCalorieRowDoesNotCountAsAGapAndNothingLoggedIsUnknown() {
        val rows = listOf(row(500.0, Nutrient.IRON to 2.0), row(0.0))
        assertThat(NutrientCoverage.share(rows, Nutrient.IRON)).isEqualTo(1.0)
        assertThat(NutrientCoverage.share(emptyList(), Nutrient.IRON)).isNull()
        assertThat(NutrientCoverage.isPartial(null)).isFalse()
    }

    @Test
    fun exactlyEightyPercentIsNotPartial() {
        assertThat(NutrientCoverage.isPartial(0.8)).isFalse()
        assertThat(NutrientCoverage.isPartial(0.79)).isTrue()
    }
}
