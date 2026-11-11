package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.body.LabelPlausibility.Label
import com.assismohanty.awaken.engine.body.LabelPlausibility.Problem
import com.assismohanty.awaken.engine.body.LabelPlausibility.Verdict
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LabelPlausibilityTest {
    private val biscuit = Label(kcal = 480.0, protein = 7.0, carbs = 64.0, fat = 21.0, sugars = 20.0, satFat = 10.0, fiber = 3.0, sodiumMg = 400.0)

    private fun rejected(label: Label) = (LabelPlausibility.check(label) as Verdict.Rejected).problem

    @Test
    fun aConsistentLabelIsAcceptedWithItsExtras() {
        val accepted = LabelPlausibility.check(biscuit) as Verdict.Accepted
        assertThat(accepted.per100[Nutrient.KCAL]).isEqualTo(480.0)
        assertThat(accepted.per100.values.keys).containsAtLeast(Nutrient.SUGARS, Nutrient.SAT_FAT, Nutrient.FIBER, Nutrient.SODIUM)
    }

    @Test
    fun aMissingMacroOrNegativeOrImpossibleValueIsRejected() {
        assertThat(rejected(biscuit.copy(protein = null))).isEqualTo(Problem.MISSING)
        assertThat(rejected(biscuit.copy(fat = -1.0))).isEqualTo(Problem.NEGATIVE)
        assertThat(rejected(biscuit.copy(kcal = 950.0))).isEqualTo(Problem.IMPOSSIBLE)
        assertThat(rejected(biscuit.copy(carbs = 101.0))).isEqualTo(Problem.IMPOSSIBLE)
        assertThat(rejected(Label(480.0, protein = 50.0, carbs = 50.0, fat = 20.0))).isEqualTo(Problem.MACROS_OVER_100G)
    }

    @Test
    fun caloriesThatDisagreeWithTheMacrosAreRejectedButCloseOnesPass() {
        assertThat(rejected(biscuit.copy(kcal = 900.0))).isEqualTo(Problem.CALORIES_DISAGREE)
        // 4*7 + 4*(64-3) + 2*3 + 9*21 = 467; 480 is 13 kcal away, inside max(20 kcal, 20 %).
        assertThat(LabelPlausibility.check(biscuit)).isInstanceOf(Verdict.Accepted::class.java)
    }

    @Test
    fun aBadExtraValueIsLeftOutButTheProductStays() {
        val accepted = LabelPlausibility.check(biscuit.copy(sugars = 90.0, satFat = 40.0, fiber = 80.0, sodiumMg = 90_000.0)) as Verdict.Accepted
        assertThat(accepted.per100.values.keys).containsNoneOf(Nutrient.SUGARS, Nutrient.SAT_FAT, Nutrient.FIBER, Nutrient.SODIUM)
        assertThat(accepted.per100[Nutrient.KCAL]).isEqualTo(480.0)
    }
}
