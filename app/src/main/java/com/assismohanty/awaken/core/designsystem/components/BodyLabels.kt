package com.assismohanty.awaken.core.designsystem.components

import androidx.annotation.StringRes
import com.assismohanty.awaken.R
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.NutrientUnit

// Display names for the Gym and Diet vocabularies (GD §13).

@StringRes
fun Muscle.nameRes(): Int = when (this) {
    Muscle.ABDOMINALS -> R.string.muscle_abdominals
    Muscle.ABDUCTORS -> R.string.muscle_abductors
    Muscle.ADDUCTORS -> R.string.muscle_adductors
    Muscle.BICEPS -> R.string.muscle_biceps
    Muscle.CALVES -> R.string.muscle_calves
    Muscle.CHEST -> R.string.muscle_chest
    Muscle.FOREARMS -> R.string.muscle_forearms
    Muscle.GLUTES -> R.string.muscle_glutes
    Muscle.HAMSTRINGS -> R.string.muscle_hamstrings
    Muscle.LATS -> R.string.muscle_lats
    Muscle.LOWER_BACK -> R.string.muscle_lower_back
    Muscle.MIDDLE_BACK -> R.string.muscle_middle_back
    Muscle.NECK -> R.string.muscle_neck
    Muscle.QUADRICEPS -> R.string.muscle_quadriceps
    Muscle.SHOULDERS -> R.string.muscle_shoulders
    Muscle.TRAPS -> R.string.muscle_traps
    Muscle.TRICEPS -> R.string.muscle_triceps
}

@StringRes
fun Equipment.nameRes(): Int = when (this) {
    Equipment.BODY_ONLY -> R.string.gear_body_only
    Equipment.DUMBBELL -> R.string.gear_dumbbell
    Equipment.BARBELL -> R.string.gear_barbell
    Equipment.EZ_BAR -> R.string.gear_ez_bar
    Equipment.KETTLEBELLS -> R.string.gear_kettlebells
    Equipment.CABLE -> R.string.gear_cable
    Equipment.MACHINE -> R.string.gear_machine
    Equipment.BANDS -> R.string.gear_bands
    Equipment.MEDICINE_BALL -> R.string.gear_medicine_ball
    Equipment.EXERCISE_BALL -> R.string.gear_exercise_ball
    Equipment.FOAM_ROLL -> R.string.gear_foam_roll
    Equipment.OTHER -> R.string.gear_other
}

@StringRes
fun Nutrient.nameRes(): Int = when (this) {
    Nutrient.KCAL -> R.string.nutrient_kcal
    Nutrient.PROTEIN -> R.string.nutrient_protein
    Nutrient.CARBS -> R.string.nutrient_carbs
    Nutrient.FAT -> R.string.nutrient_fat
    Nutrient.FIBER -> R.string.nutrient_fiber
    Nutrient.SUGARS -> R.string.nutrient_sugars
    Nutrient.SAT_FAT -> R.string.nutrient_sat_fat
    Nutrient.MONO_FAT -> R.string.nutrient_mono_fat
    Nutrient.POLY_FAT -> R.string.nutrient_poly_fat
    Nutrient.TRANS_FAT -> R.string.nutrient_trans_fat
    Nutrient.CHOLESTEROL -> R.string.nutrient_cholesterol
    Nutrient.SODIUM -> R.string.nutrient_sodium
    Nutrient.POTASSIUM -> R.string.nutrient_potassium
    Nutrient.CALCIUM -> R.string.nutrient_calcium
    Nutrient.IRON -> R.string.nutrient_iron
    Nutrient.MAGNESIUM -> R.string.nutrient_magnesium
    Nutrient.VITAMIN_A -> R.string.nutrient_vitamin_a
    Nutrient.VITAMIN_C -> R.string.nutrient_vitamin_c
    Nutrient.VITAMIN_D -> R.string.nutrient_vitamin_d
    Nutrient.CAFFEINE -> R.string.nutrient_caffeine
    Nutrient.ALCOHOL -> R.string.nutrient_alcohol
}

@StringRes
fun NutrientUnit.symbolRes(): Int = when (this) {
    NutrientUnit.KCAL -> R.string.nutrient_unit_kcal
    NutrientUnit.G -> R.string.nutrient_unit_g
    NutrientUnit.MG -> R.string.nutrient_unit_mg
    NutrientUnit.UG -> R.string.nutrient_unit_ug
}
