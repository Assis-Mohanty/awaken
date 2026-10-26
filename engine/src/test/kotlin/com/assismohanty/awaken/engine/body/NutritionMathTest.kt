package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** GD §13.1 vectors. */
class NutritionMathTest {
    private val man = BodyProfile(Sex.MALE, age = 25, heightCm = 175.0, weightKg = 80.0, ActivityLevel.MODERATE, WeightGoal.MAINTAIN)
    private val woman = BodyProfile(Sex.FEMALE, age = 30, heightCm = 160.0, weightKg = 60.0, ActivityLevel.SEDENTARY, WeightGoal.LOSE)

    @Test
    fun `Mifflin-St Jeor BMR and TDEE`() {
        assertThat(NutritionMath.bmr(man)).isWithin(1e-9).of(1_773.75)
        assertThat(NutritionMath.tdee(man)).isWithin(1e-9).of(2_749.3125)
        assertThat(NutritionMath.bmr(woman)).isWithin(1e-9).of(1_289.0)
    }

    @Test
    fun `goal deltas apply and the floor holds`() {
        assertThat(NutritionMath.calorieTarget(man)).isEqualTo(2_749)
        assertThat(NutritionMath.calorieTarget(man.copy(goal = WeightGoal.GAIN))).isEqualTo(3_049)
        assertThat(NutritionMath.calorieTarget(man.copy(goal = WeightGoal.LOSE))).isEqualTo(2_249)
        // 1,289 × 1.2 = 1,547 − 500 = 1,047 → female floor 1,200.
        assertThat(NutritionMath.calorieTarget(woman)).isEqualTo(1_200)
        assertThat(NutritionMath.calorieTarget(woman.copy(sex = Sex.MALE))).isEqualTo(1_500)
    }

    @Test
    fun `macro targets follow protein per kg, 25 percent fat and carbs as the rest`() {
        val plan = NutritionMath.plan(man)

        assertThat(plan.bmr).isEqualTo(1_774)
        assertThat(plan.tdee).isEqualTo(2_749)
        assertThat(plan.kcal).isEqualTo(2_749)
        assertThat(plan.targets.getValue(Nutrient.PROTEIN).amount).isWithin(1e-9).of(128.0)
        assertThat(plan.targets.getValue(Nutrient.FAT).amount).isWithin(1e-6).of(2_749 * 0.25 / 9)
        assertThat(plan.targets.getValue(Nutrient.CARBS).amount).isWithin(1e-6).of((2_749 - 128 * 4 - 2_749 * 0.25) / 4)
        assertThat(plan.targets.getValue(Nutrient.FIBER).amount).isWithin(1e-6).of(2.749 * 14)
        assertThat(plan.targets.getValue(Nutrient.SAT_FAT)).isEqualTo(NutrientTarget(2_749 * 0.10 / 9, TargetKind.LIMIT))
        assertThat(NutritionMath.plan(man.copy(goal = WeightGoal.LOSE)).targets.getValue(Nutrient.PROTEIN).amount).isWithin(1e-9).of(160.0)
    }

    @Test
    fun `daily values are the default micronutrient targets`() {
        val targets = NutritionMath.plan(man).targets

        assertThat(targets[Nutrient.SODIUM]).isEqualTo(NutrientTarget(2_300.0, TargetKind.LIMIT))
        assertThat(targets[Nutrient.CHOLESTEROL]).isEqualTo(NutrientTarget(300.0, TargetKind.LIMIT))
        assertThat(targets[Nutrient.POTASSIUM]).isEqualTo(NutrientTarget(4_700.0, TargetKind.GOAL))
        assertThat(targets[Nutrient.CALCIUM]).isEqualTo(NutrientTarget(1_300.0, TargetKind.GOAL))
        assertThat(targets[Nutrient.IRON]).isEqualTo(NutrientTarget(18.0, TargetKind.GOAL))
        assertThat(targets[Nutrient.MAGNESIUM]).isEqualTo(NutrientTarget(420.0, TargetKind.GOAL))
        assertThat(targets[Nutrient.VITAMIN_A]).isEqualTo(NutrientTarget(900.0, TargetKind.GOAL))
        assertThat(targets[Nutrient.VITAMIN_C]).isEqualTo(NutrientTarget(90.0, TargetKind.GOAL))
        assertThat(targets[Nutrient.VITAMIN_D]).isEqualTo(NutrientTarget(20.0, TargetKind.GOAL))
        assertThat(targets).doesNotContainKey(Nutrient.SUGARS)
    }

    @Test
    fun `a kcal override rescales derived macros, a macro override wins outright`() {
        val plan = NutritionMath.plan(man, overrides = mapOf(Nutrient.KCAL to 2_000.0, Nutrient.PROTEIN to 150.0, Nutrient.SUGARS to 50.0))

        assertThat(plan.kcal).isEqualTo(2_000)
        assertThat(plan.targets.getValue(Nutrient.PROTEIN).amount).isEqualTo(150.0)
        assertThat(plan.targets.getValue(Nutrient.FAT).amount).isWithin(1e-6).of(2_000 * 0.25 / 9)
        assertThat(plan.targets.getValue(Nutrient.CARBS).amount).isWithin(1e-6).of((2_000 - 600 - 500) / 4.0)
        assertThat(plan.targets[Nutrient.SUGARS]).isEqualTo(NutrientTarget(50.0, TargetKind.LIMIT))
    }

    @Test
    fun `carbs never go negative`() {
        val plan = NutritionMath.plan(man, overrides = mapOf(Nutrient.KCAL to 1_000.0, Nutrient.PROTEIN to 250.0))

        assertThat(plan.targets.getValue(Nutrient.CARBS).amount).isEqualTo(0.0)
    }

    @Test
    fun `nutrients scale from per 100 g and sum`() {
        val paneer = Nutrients(mapOf(Nutrient.KCAL to 321.0, Nutrient.PROTEIN to 25.0))
        val rice = Nutrients(mapOf(Nutrient.KCAL to 130.0, Nutrient.CARBS to 28.0))

        val day = Nutrients.sum(listOf(paneer.forGrams(150.0), rice.forGrams(200.0)))

        assertThat(day[Nutrient.KCAL]).isWithin(1e-9).of(481.5 + 260.0)
        assertThat(day[Nutrient.PROTEIN]).isWithin(1e-9).of(37.5)
        assertThat(day[Nutrient.CARBS]).isWithin(1e-9).of(56.0)
        assertThat(day[Nutrient.FAT]).isEqualTo(0.0)
    }

    @Test
    fun `age comes from the birth year and keys round-trip`() {
        assertThat(NutritionMath.ageOn(1998, LocalDate.of(2026, 10, 27))).isEqualTo(28)
        Nutrient.entries.forEach { assertThat(Nutrient.fromKey(it.key)).isEqualTo(it) }
    }
}
