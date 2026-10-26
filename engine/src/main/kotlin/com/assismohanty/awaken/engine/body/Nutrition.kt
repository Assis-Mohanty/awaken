package com.assismohanty.awaken.engine.body

import java.time.LocalDate
import kotlin.math.roundToInt

/** Units a nutrient is measured in. Catalog values are per 100 g of food in these units. */
enum class NutrientUnit { KCAL, G, MG, UG }

/** Whether a target is something to reach (protein) or stay under (sodium). */
enum class TargetKind { GOAL, LIMIT }

/** Every nutrient the diary tracks (GD §13.1). [key] is the stable id used by the bundled catalog and Room. */
enum class Nutrient(val key: String, val unit: NutrientUnit) {
    KCAL("kcal", NutrientUnit.KCAL),
    PROTEIN("protein", NutrientUnit.G),
    CARBS("carbs", NutrientUnit.G),
    FAT("fat", NutrientUnit.G),
    FIBER("fiber", NutrientUnit.G),
    SUGARS("sugars", NutrientUnit.G),
    SAT_FAT("sat_fat", NutrientUnit.G),
    MONO_FAT("mono_fat", NutrientUnit.G),
    POLY_FAT("poly_fat", NutrientUnit.G),
    TRANS_FAT("trans_fat", NutrientUnit.G),
    CHOLESTEROL("cholesterol", NutrientUnit.MG),
    SODIUM("sodium", NutrientUnit.MG),
    POTASSIUM("potassium", NutrientUnit.MG),
    CALCIUM("calcium", NutrientUnit.MG),
    IRON("iron", NutrientUnit.MG),
    MAGNESIUM("magnesium", NutrientUnit.MG),
    VITAMIN_A("vitamin_a", NutrientUnit.UG),
    VITAMIN_C("vitamin_c", NutrientUnit.MG),
    VITAMIN_D("vitamin_d", NutrientUnit.UG),
    CAFFEINE("caffeine", NutrientUnit.MG),
    ALCOHOL("alcohol", NutrientUnit.G),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): Nutrient? = byKey[key]
    }
}

/** An amount of each nutrient; a missing nutrient is unknown and counts as zero in totals. */
data class Nutrients(val values: Map<Nutrient, Double> = emptyMap()) {
    operator fun get(nutrient: Nutrient): Double = values[nutrient] ?: 0.0

    operator fun plus(other: Nutrients): Nutrients =
        Nutrients((values.keys + other.values.keys).associateWith { this[it] + other[it] })

    /** These values are per 100 g; the amount in [grams] of the food. */
    fun forGrams(grams: Double): Nutrients = Nutrients(values.mapValues { (_, per100) -> per100 * grams / PER_GRAMS })

    companion object {
        const val PER_GRAMS = 100.0

        fun sum(items: Iterable<Nutrients>): Nutrients = items.fold(Nutrients(), Nutrients::plus)
    }
}

enum class Sex { MALE, FEMALE }

/** Activity multipliers on BMR (GD §13.1). */
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    VERY(1.725),
    EXTRA(1.9),
}

/** Daily calorie adjustment and protein per kg for each goal (GD §13.1). */
enum class WeightGoal(val kcalDelta: Int, val proteinPerKg: Double) {
    LOSE(-500, 2.0),
    MAINTAIN(0, 1.6),
    GAIN(300, 1.6),
}

data class BodyProfile(
    val sex: Sex,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
    val goal: WeightGoal,
)

data class NutrientTarget(val amount: Double, val kind: TargetKind)

/** The day's budget: BMR and TDEE for display, and a target for every nutrient that has one. */
data class NutritionPlan(
    val bmr: Int,
    val tdee: Int,
    val targets: Map<Nutrient, NutrientTarget>,
) {
    val kcal: Int get() = targets[Nutrient.KCAL]?.amount?.roundToInt() ?: 0
}

/** Calorie budget and nutrient targets (GD §13.1). Pure: the app passes the profile and the hunter's overrides. */
object NutritionMath {
    // Input sanity for the setup form (not game numbers).
    val AGE_RANGE = 13..100
    val HEIGHT_CM_RANGE = 120.0..230.0
    val WEIGHT_KG_RANGE = 30.0..300.0

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0
    private const val FAT_SHARE = 0.25
    private const val SAT_FAT_SHARE = 0.10
    private const val FIBER_PER_1000_KCAL = 14.0
    private const val FLOOR_FEMALE = 1_200
    private const val FLOOR_MALE = 1_500

    /** FDA Daily Values used as default micronutrient targets/limits (GD §13.1). */
    private val DAILY_VALUES: Map<Nutrient, NutrientTarget> = mapOf(
        Nutrient.CHOLESTEROL to NutrientTarget(300.0, TargetKind.LIMIT),
        Nutrient.SODIUM to NutrientTarget(2_300.0, TargetKind.LIMIT),
        Nutrient.POTASSIUM to NutrientTarget(4_700.0, TargetKind.GOAL),
        Nutrient.CALCIUM to NutrientTarget(1_300.0, TargetKind.GOAL),
        Nutrient.IRON to NutrientTarget(18.0, TargetKind.GOAL),
        Nutrient.MAGNESIUM to NutrientTarget(420.0, TargetKind.GOAL),
        Nutrient.VITAMIN_A to NutrientTarget(900.0, TargetKind.GOAL),
        Nutrient.VITAMIN_C to NutrientTarget(90.0, TargetKind.GOAL),
        Nutrient.VITAMIN_D to NutrientTarget(20.0, TargetKind.GOAL),
    )

    /** Which way each nutrient's target points when the hunter sets one that has no default. */
    fun kindOf(nutrient: Nutrient): TargetKind = when (nutrient) {
        Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FIBER,
        Nutrient.POTASSIUM, Nutrient.CALCIUM, Nutrient.IRON, Nutrient.MAGNESIUM,
        Nutrient.VITAMIN_A, Nutrient.VITAMIN_C, Nutrient.VITAMIN_D,
        -> TargetKind.GOAL
        else -> TargetKind.LIMIT
    }

    fun ageOn(birthYear: Int, date: LocalDate): Int = date.year - birthYear

    /** Mifflin–St Jeor resting energy, kcal/day. */
    fun bmr(profile: BodyProfile): Double {
        val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.age
        return base + if (profile.sex == Sex.MALE) 5.0 else -161.0
    }

    fun tdee(profile: BodyProfile): Double = bmr(profile) * profile.activity.factor

    /** TDEE plus the goal's delta, never below the sex's floor. */
    fun calorieTarget(profile: BodyProfile): Int {
        val floor = if (profile.sex == Sex.MALE) FLOOR_MALE else FLOOR_FEMALE
        return (tdee(profile).roundToInt() + profile.goal.kcalDelta).coerceAtLeast(floor)
    }

    /**
     * Every default target, then [overrides] on top. Macros follow the effective calories, so overriding kcal alone
     * rescales fat, carbs, fiber and saturated fat; overriding a macro itself wins outright.
     */
    fun plan(profile: BodyProfile, overrides: Map<Nutrient, Double> = emptyMap()): NutritionPlan {
        val kcal = overrides[Nutrient.KCAL] ?: calorieTarget(profile).toDouble()
        val protein = overrides[Nutrient.PROTEIN] ?: (profile.goal.proteinPerKg * profile.weightKg)
        val fat = overrides[Nutrient.FAT] ?: (kcal * FAT_SHARE / KCAL_PER_G_FAT)
        val carbs = overrides[Nutrient.CARBS]
            ?: ((kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT) / KCAL_PER_G_CARBS).coerceAtLeast(0.0)
        val defaults = buildMap {
            put(Nutrient.KCAL, NutrientTarget(kcal, TargetKind.GOAL))
            put(Nutrient.PROTEIN, NutrientTarget(protein, TargetKind.GOAL))
            put(Nutrient.FAT, NutrientTarget(fat, TargetKind.GOAL))
            put(Nutrient.CARBS, NutrientTarget(carbs, TargetKind.GOAL))
            put(Nutrient.FIBER, NutrientTarget(kcal / 1_000.0 * FIBER_PER_1000_KCAL, TargetKind.GOAL))
            put(Nutrient.SAT_FAT, NutrientTarget(kcal * SAT_FAT_SHARE / KCAL_PER_G_FAT, TargetKind.LIMIT))
            putAll(DAILY_VALUES)
        }
        val overridden = overrides.mapValues { (nutrient, amount) -> NutrientTarget(amount, kindOf(nutrient)) }
        return NutritionPlan(
            bmr = bmr(profile).roundToInt(),
            tdee = tdee(profile).roundToInt(),
            targets = defaults + overridden,
        )
    }
}
