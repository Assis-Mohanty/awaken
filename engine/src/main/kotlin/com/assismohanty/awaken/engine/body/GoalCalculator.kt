package com.assismohanty.awaken.engine.body

import kotlin.math.floor
import kotlin.math.roundToInt

/** How fast to lose: calories below maintenance per day. Gaining has one approved pace, +300 ([WeightGoal.GAIN]). */
enum class LossPace(val delta: Int) { GENTLE(-250), STEADY(-500), FASTER(-750) }

enum class MacroPreset { BALANCED, HIGH_PROTEIN, CUSTOM }

/** A custom split in whole percent of calories; only built when the three add up to 100. */
data class MacroSplit private constructor(val proteinPct: Int, val carbsPct: Int, val fatPct: Int) {
    companion object {
        fun of(proteinPct: Int, carbsPct: Int, fatPct: Int): MacroSplit? =
            if (proteinPct >= 0 && carbsPct >= 0 && fatPct >= 0 && proteinPct + carbsPct + fatPct == 100) {
                MacroSplit(proteinPct, carbsPct, fatPct)
            } else {
                null
            }
    }
}

data class GoalSettings(
    val lossPace: LossPace = LossPace.STEADY,
    val preset: MacroPreset = MacroPreset.BALANCED,
    /** Overrides the preset's protein (clamped to [GoalCalculator.PROTEIN_PER_KG_RANGE]); ignored by a valid custom split. */
    val proteinPerKg: Double? = null,
    val split: MacroSplit? = null,
)

/** [capped]: the pace was reduced to 25 % of TDEE. [atFloor]: the floor raised the target. [splitFits]: protein and fat leave room for carbs. */
data class GoalPlan(val plan: NutritionPlan, val appliedDelta: Int, val capped: Boolean, val atFloor: Boolean, val splitFits: Boolean)

/**
 * Calorie target and macro split from a profile and the hunter's choices (GD section 20.1). The defaults give exactly the
 * GD 13.1 plan. Sources and what could not be verified are in GD 20.1. Pure.
 */
object GoalCalculator {
    /** Our own safety cap: never plan a deficit above this share of TDEE. */
    const val MAX_DEFICIT_SHARE = 0.25

    /** Top of the ISSN range of 1.4 to 2.0 g/kg a day (Jager et al. 2017). */
    const val HIGH_PROTEIN_PER_KG = 2.0

    /** Upper bound is the ISSN figure; the 0.8 lower bound is the usual adult RDA (not verified). */
    val PROTEIN_PER_KG_RANGE = 0.8..2.0

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0
    private const val FAT_SHARE = 0.25
    private const val PERCENT = 100.0

    /** A chosen change in kcal per day, with any deficit cut to [MAX_DEFICIT_SHARE] of [tdee] (whole kcal, rounded down). */
    fun appliedDelta(tdee: Int, chosen: Int): Int = if (chosen < 0) -minOf(-chosen, floor(tdee * MAX_DEFICIT_SHARE).toInt()) else chosen

    /** A typed calorie target is raised to the floor. */
    fun clampKcalOverride(sex: Sex, kcal: Double): Double = maxOf(kcal, NutritionMath.floorKcal(sex).toDouble())

    fun plan(profile: BodyProfile, settings: GoalSettings = GoalSettings(), overrides: Map<Nutrient, Double> = emptyMap()): GoalPlan {
        val tdee = NutritionMath.tdee(profile).roundToInt()
        val chosen = when (profile.goal) {
            WeightGoal.LOSE -> settings.lossPace.delta
            WeightGoal.MAINTAIN -> 0
            WeightGoal.GAIN -> profile.goal.kcalDelta
        }
        val delta = appliedDelta(tdee, chosen)
        val floor = NutritionMath.floorKcal(profile.sex)
        val kcal = overrides[Nutrient.KCAL]?.let { clampKcalOverride(profile.sex, it) } ?: maxOf(tdee + delta, floor).toDouble()

        val split = settings.split.takeIf { settings.preset == MacroPreset.CUSTOM }
        val protein: Double
        val fat: Double
        val carbs: Double
        var fits = true
        if (split != null) {
            protein = kcal * split.proteinPct / PERCENT / KCAL_PER_G_PROTEIN
            carbs = kcal * split.carbsPct / PERCENT / KCAL_PER_G_CARBS
            fat = kcal * split.fatPct / PERCENT / KCAL_PER_G_FAT
        } else {
            val perKg = settings.proteinPerKg?.coerceIn(PROTEIN_PER_KG_RANGE)
                ?: if (settings.preset == MacroPreset.HIGH_PROTEIN) HIGH_PROTEIN_PER_KG else profile.goal.proteinPerKg
            protein = perKg * profile.weightKg
            fat = kcal * FAT_SHARE / KCAL_PER_G_FAT
            val rest = kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT
            fits = rest >= 0.0
            carbs = (rest / KCAL_PER_G_CARBS).coerceAtLeast(0.0)
        }
        // The typed kcal was already clamped above; the macros computed here stay goals unless the hunter overrides them.
        val computed = mapOf(Nutrient.KCAL to kcal, Nutrient.PROTEIN to protein, Nutrient.FAT to fat, Nutrient.CARBS to carbs)
        val base = NutritionMath.plan(profile, computed + overrides.filterKeys { it != Nutrient.KCAL })
        val targets = base.targets.mapValues { (nutrient, target) ->
            if (nutrient in computed && nutrient !in overrides) target.copy(kind = TargetKind.GOAL) else target
        }
        return GoalPlan(
            plan = base.copy(targets = targets),
            appliedDelta = delta,
            capped = delta != chosen,
            atFloor = tdee + delta < floor,
            splitFits = fits,
        )
    }
}
