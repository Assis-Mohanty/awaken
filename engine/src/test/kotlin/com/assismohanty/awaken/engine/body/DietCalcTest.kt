package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** GD section 20 vectors 198 to 215. "Man" and "woman" are the §13.1 profiles (vectors 46 and 47). */
class DietCalcTest {
    private val man = BodyProfile(Sex.MALE, age = 25, heightCm = 175.0, weightKg = 80.0, ActivityLevel.MODERATE, WeightGoal.LOSE)
    private val woman = BodyProfile(Sex.FEMALE, age = 30, heightCm = 160.0, weightKg = 60.0, ActivityLevel.SEDENTARY, WeightGoal.LOSE)

    private fun GoalPlan.kcal() = plan.targets.getValue(Nutrient.KCAL).amount
    private fun GoalPlan.grams(n: Nutrient) = plan.targets.getValue(n).amount

    // Goals

    @Test
    fun `198 steady loss gives the GD 13_1 plan`() {
        val p = GoalCalculator.plan(man)

        assertThat(p.kcal()).isEqualTo(2_249.0)
        assertThat(p.grams(Nutrient.PROTEIN)).isWithin(1e-9).of(160.0)
        assertThat(p.grams(Nutrient.FAT)).isWithin(0.05).of(62.5)
        assertThat(p.grams(Nutrient.CARBS)).isWithin(0.05).of(261.7)
        assertThat(p.capped).isFalse()
        assertThat(p.plan).isEqualTo(NutritionMath.plan(man))
    }

    @Test
    fun `199 faster loss is cut to 25 percent of TDEE`() {
        val p = GoalCalculator.plan(man, GoalSettings(lossPace = LossPace.FASTER))

        assertThat(p.kcal()).isEqualTo(2_062.0)
        assertThat(p.appliedDelta).isEqualTo(-687)
        assertThat(p.capped).isTrue()
    }

    @Test
    fun `200 gaining is plus 300`() {
        assertThat(GoalCalculator.plan(man.copy(goal = WeightGoal.GAIN)).kcal()).isEqualTo(3_049.0)
    }

    @Test
    fun `201 high protein is 2_0 g per kg`() {
        val p = GoalCalculator.plan(man.copy(goal = WeightGoal.MAINTAIN), GoalSettings(preset = MacroPreset.HIGH_PROTEIN))

        assertThat(p.grams(Nutrient.PROTEIN)).isWithin(1e-9).of(160.0)
        assertThat(p.grams(Nutrient.FAT)).isWithin(0.05).of(76.4)
        assertThat(p.grams(Nutrient.CARBS)).isWithin(0.05).of(355.4)
    }

    @Test
    fun `202 and 203 the floor holds after the cap`() {
        assertThat(GoalCalculator.plan(woman, GoalSettings(lossPace = LossPace.GENTLE)).kcal()).isEqualTo(1_297.0)
        val faster = GoalCalculator.plan(woman, GoalSettings(lossPace = LossPace.FASTER))
        assertThat(faster.kcal()).isEqualTo(1_200.0)
        assertThat(faster.atFloor).isTrue()
    }

    @Test
    fun `204 a typed target below the floor is raised to it`() {
        assertThat(GoalCalculator.clampKcalOverride(Sex.FEMALE, 1_000.0)).isEqualTo(1_200.0)
        assertThat(GoalCalculator.plan(woman, overrides = mapOf(Nutrient.KCAL to 1_000.0)).kcal()).isEqualTo(1_200.0)
        assertThat(GoalCalculator.clampKcalOverride(Sex.MALE, 2_000.0)).isEqualTo(2_000.0)
    }

    @Test
    fun `protein per kg and custom splits`() {
        val perKg = GoalCalculator.plan(man, GoalSettings(proteinPerKg = 3.0)) // clamped to the 2.0 top
        assertThat(perKg.grams(Nutrient.PROTEIN)).isWithin(1e-9).of(160.0)
        assertThat(GoalCalculator.plan(man, GoalSettings(proteinPerKg = 1.0)).grams(Nutrient.PROTEIN)).isWithin(1e-9).of(80.0)

        assertThat(MacroSplit.of(30, 40, 31)).isNull()
        assertThat(MacroSplit.of(-10, 60, 50)).isNull()
        val split = MacroSplit.of(30, 40, 30)
        val custom = GoalCalculator.plan(man, GoalSettings(preset = MacroPreset.CUSTOM, split = split))
        assertThat(custom.grams(Nutrient.PROTEIN)).isWithin(0.01).of(2_249 * 0.30 / 4)
        assertThat(custom.grams(Nutrient.CARBS)).isWithin(0.01).of(2_249 * 0.40 / 4)
        assertThat(custom.grams(Nutrient.FAT)).isWithin(0.01).of(2_249 * 0.30 / 9)
    }

    @Test
    fun `carbs never go negative and the plan says the split does not fit`() {
        val heavy = woman.copy(weightKg = 150.0) // 2.0 g/kg = 300 g protein = 1,200 kcal, the whole floor
        val p = GoalCalculator.plan(heavy, GoalSettings(proteinPerKg = 2.0), overrides = mapOf(Nutrient.KCAL to 1_200.0))

        assertThat(p.splitFits).isFalse()
        assertThat(p.grams(Nutrient.CARBS)).isEqualTo(0.0)
    }

    @Test
    fun `a macro override still wins over the preset`() {
        val p = GoalCalculator.plan(man, overrides = mapOf(Nutrient.PROTEIN to 100.0))

        assertThat(p.grams(Nutrient.PROTEIN)).isEqualTo(100.0)
    }

    // Guard (205, 206)

    @Test
    fun `205 targets need a year difference above 18`() {
        val day = LocalDate.of(2026, 11, 10)

        assertThat(DietGuard.targetsVisible(2008, day, 175.0, 70.0)).isFalse()
        assertThat(DietGuard.targetsVisible(2007, day, 175.0, 70.0)).isTrue()
    }

    @Test
    fun `206 targets need BMI 18_5 or more`() {
        val day = LocalDate.of(2026, 11, 10)

        assertThat(DietGuard.targetsVisible(1990, day, 170.0, 53.4)).isFalse()
        assertThat(DietGuard.targetsVisible(1990, day, 170.0, 53.5)).isTrue()
    }

    // Weight trend (207, 208)

    private val today = LocalDate.of(2026, 11, 10)
    private fun weighIns(vararg kg: Double) = kg.mapIndexed { i, v -> WeighIn(today.minusDays(i.toLong()), v) }

    @Test
    fun `207 the offer appears at one kilo and not before`() {
        val a = WeightTrend.trend(weighIns(79.2, 79.0, 78.8), today)
        val b = WeightTrend.trend(weighIns(79.4, 79.1, 78.9), today)

        assertThat(WeightTrend.shouldPrompt(a, 80.0, null, today)).isTrue()
        assertThat(WeightTrend.shouldPrompt(b, 80.0, null, today)).isFalse()
    }

    @Test
    fun `208 fewer than three weigh-ins in seven days gives no trend`() {
        assertThat(WeightTrend.trend(weighIns(79.0, 79.0), today)).isNull()
        assertThat(WeightTrend.trend(listOf(WeighIn(today, 79.0), WeighIn(today.minusDays(1), 79.0), WeighIn(today.minusDays(7), 79.0)), today)).isNull()
        assertThat(WeightTrend.shouldPrompt(null, 80.0, null, today)).isFalse()
    }

    @Test
    fun `dismissing hides the offer for seven days`() {
        val trend = 78.5

        assertThat(WeightTrend.shouldPrompt(trend, 80.0, today.minusDays(6), today)).isFalse()
        assertThat(WeightTrend.shouldPrompt(trend, 80.0, today.minusDays(7), today)).isTrue()
    }

    // Recipes (209, 210)

    private val oil = Nutrients(mapOf(Nutrient.KCAL to 900.0))
    private val base = Nutrients(mapOf(Nutrient.KCAL to 130.0))
    private val recipe = Recipe(listOf(RecipeIngredient(base, 200.0), RecipeIngredient(oil, 50.0)), servings = 4.0, cookedGrams = 500.0)

    @Test
    fun `209 recipe totals, per serving and per 100 g cooked`() {
        assertThat(RecipeMath.totals(recipe)[Nutrient.KCAL]).isWithin(1e-9).of(710.0)
        assertThat(RecipeMath.perServing(recipe)[Nutrient.KCAL]).isWithin(1e-9).of(177.5)
        assertThat(RecipeMath.per100Cooked(recipe)[Nutrient.KCAL]).isWithin(1e-9).of(142.0)
        assertThat(RecipeMath.servingGrams(recipe)).isEqualTo(125.0)
    }

    @Test
    fun `210 scaling keeps the per-serving amounts`() {
        val big = recipe.scaled(1.5)

        assertThat(big.servings).isEqualTo(6.0)
        assertThat(RecipeMath.totals(big)[Nutrient.KCAL]).isWithin(1e-9).of(1_065.0)
        assertThat(RecipeMath.perServing(big)[Nutrient.KCAL]).isWithin(1e-9).of(177.5)
        assertThat(RecipeMath.per100Cooked(big)[Nutrient.KCAL]).isWithin(1e-9).of(142.0)
    }

    @Test
    fun `a nutrient is known only when every ingredient has it`() {
        val iron = Nutrients(mapOf(Nutrient.KCAL to 100.0, Nutrient.IRON to 2.0))
        val r = Recipe(listOf(RecipeIngredient(iron, 100.0), RecipeIngredient(base, 100.0)), servings = 1.0)

        assertThat(RecipeMath.totals(r).values.keys).containsExactly(Nutrient.KCAL)
    }

    @Test
    fun `bad recipes are refused`() {
        assertThrows(IllegalArgumentException::class.java) { Recipe(emptyList(), servings = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { Recipe(emptyList(), servings = 1.0, cookedGrams = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { RecipeMath.per100Cooked(Recipe(emptyList(), servings = 1.0)) }
    }

    // Atwater (211)

    private val macros = mapOf(Nutrient.PROTEIN to 10.0, Nutrient.CARBS to 20.0, Nutrient.FAT to 5.0)

    @Test
    fun `211 the energy check`() {
        assertThat(Atwater.estimateKcal(Nutrients(macros))).isEqualTo(165.0)
        assertThat(Atwater.check(Nutrients(macros + (Nutrient.KCAL to 165.0)), ownRow = true)).isEqualTo(AtwaterResult.OK)
        val off = Nutrients(macros + (Nutrient.KCAL to 210.0))
        assertThat(Atwater.check(off, ownRow = false)).isEqualTo(AtwaterResult.WARNING)
        assertThat(Atwater.check(off, ownRow = true)).isEqualTo(AtwaterResult.ERROR)
        assertThat(Atwater.check(Nutrients(mapOf(Nutrient.KCAL to 100.0)), ownRow = false)).isEqualTo(AtwaterResult.UNKNOWN)
    }

    @Test
    fun `fibre counts two kcal per gram`() {
        val oats = Nutrients(mapOf(Nutrient.PROTEIN to 10.0, Nutrient.CARBS to 60.0, Nutrient.FAT to 5.0, Nutrient.FIBER to 10.0))

        assertThat(Atwater.estimateKcal(oats)).isEqualTo(40.0 + 200.0 + 20.0 + 45.0)
    }

    // Portions (212, 213)

    @Test
    fun `212 servings to grams to nutrients`() {
        val grams = Portions.grams(2.0, 150.0)
        val food = Nutrients(mapOf(Nutrient.KCAL to 110.0))

        assertThat(grams).isEqualTo(300.0)
        assertThat(food.forGrams(grams)[Nutrient.KCAL]).isWithin(1e-9).of(330.0)
    }

    @Test
    fun `213 a label per serving becomes per 100 g`() {
        val perServing = Nutrients(mapOf(Nutrient.KCAL to 150.0, Nutrient.SODIUM to Portions.sodiumMgFromSalt(0.5)))
        val per100 = Portions.per100FromServing(perServing, 30.0)

        assertThat(per100[Nutrient.KCAL]).isWithin(1e-9).of(500.0)
        assertThat(per100[Nutrient.SODIUM]).isWithin(0.5).of(667.0)
        assertThat(Portions.kcalFromKj(418.4)).isWithin(1e-9).of(100.0)
        assertThrows(IllegalArgumentException::class.java) { Portions.per100FromServing(perServing, 0.0) }
    }

    @Test
    fun `household volumes`() {
        assertThat(Portions.gramsForVolume(PortionUnit.KATORI, 1.0, 1.0)).isEqualTo(150.0)
        assertThat(Portions.gramsForVolume(PortionUnit.CUP, 2.0, 0.5)).isEqualTo(240.0)
        assertThat(Portions.gramsForVolume(PortionUnit.ROTI, 1.0, 1.0)).isNull()
    }

    // Barcode (215)

    @Test
    fun `215 barcodes`() {
        assertThat(Barcode.canonical("036000291452")).isEqualTo("0036000291452")
        assertThat(Barcode.canonical("8901030865275")).isEqualTo("8901030865275")
        assertThat(Barcode.canonical("8901030865278")).isNull()
        assertThat(Barcode.canonical("01234565", upcE = true)).isEqualTo("0012345000065")
        assertThat(Barcode.canonical("01234565")).isEqualTo("0000001234565")
        assertThat(Barcode.canonical("01234566", upcE = true)).isNull()
        assertThat(Barcode.canonical(" 036000291452 ")).isEqualTo("0036000291452")
        assertThat(Barcode.canonical("12ab")).isNull()
        assertThat(Barcode.canonical("")).isNull()
    }

    @Test
    fun `every UPC-E expansion rule round trips a valid check digit`() {
        // body digits abcdef with each last digit class; the check digit is computed from the expanded UPC-A.
        for (f in listOf('0', '1', '2', '3', '4', '5', '9')) {
            val six = "12345".let { if (f in "012") "12${f}345".substring(0, 2) + "345" + f else it + f }
            val e = "0$six"
            val withCheck = (0..9).map { "$e$it" }.mapNotNull { code -> Barcode.canonical(code, upcE = true) }
            assertThat(withCheck).hasSize(1)
        }
    }
}
