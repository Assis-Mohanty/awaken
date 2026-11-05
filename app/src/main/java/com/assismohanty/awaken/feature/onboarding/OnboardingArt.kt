package com.assismohanty.awaken.feature.onboarding

import androidx.annotation.DrawableRes
import com.assismohanty.awaken.R
import com.assismohanty.awaken.engine.onboarding.IllustrationSet

/**
 * Every onboarding illustration by its file name (docs/onboarding/ASSET_LIST.md). Names are `onb_<step>_<variant>_<gender>`;
 * a missing `_m` or `_f` image falls back to `_n`. Until final art arrives these are marked placeholders.
 */
object OnboardingArt {
    val all: Map<String, Int> = mapOf(
        "onb_welcome_hero_n" to R.drawable.onb_welcome_hero_n,
        "onb_height_profile_m" to R.drawable.onb_height_profile_m,
        "onb_shape_1_m" to R.drawable.onb_shape_1_m,
        "onb_shape_2_m" to R.drawable.onb_shape_2_m,
        "onb_shape_3_m" to R.drawable.onb_shape_3_m,
        "onb_shape_4_m" to R.drawable.onb_shape_4_m,
        "onb_shape_5_m" to R.drawable.onb_shape_5_m,
        "onb_fitness_beginner_m" to R.drawable.onb_fitness_beginner_m,
        "onb_fitness_intermediate_m" to R.drawable.onb_fitness_intermediate_m,
        "onb_fitness_advanced_m" to R.drawable.onb_fitness_advanced_m,
        "onb_height_profile_f" to R.drawable.onb_height_profile_f,
        "onb_shape_1_f" to R.drawable.onb_shape_1_f,
        "onb_shape_2_f" to R.drawable.onb_shape_2_f,
        "onb_shape_3_f" to R.drawable.onb_shape_3_f,
        "onb_shape_4_f" to R.drawable.onb_shape_4_f,
        "onb_shape_5_f" to R.drawable.onb_shape_5_f,
        "onb_fitness_beginner_f" to R.drawable.onb_fitness_beginner_f,
        "onb_fitness_intermediate_f" to R.drawable.onb_fitness_intermediate_f,
        "onb_fitness_advanced_f" to R.drawable.onb_fitness_advanced_f,
        "onb_height_profile_n" to R.drawable.onb_height_profile_n,
        "onb_shape_1_n" to R.drawable.onb_shape_1_n,
        "onb_shape_2_n" to R.drawable.onb_shape_2_n,
        "onb_shape_3_n" to R.drawable.onb_shape_3_n,
        "onb_shape_4_n" to R.drawable.onb_shape_4_n,
        "onb_shape_5_n" to R.drawable.onb_shape_5_n,
        "onb_fitness_beginner_n" to R.drawable.onb_fitness_beginner_n,
        "onb_fitness_intermediate_n" to R.drawable.onb_fitness_intermediate_n,
        "onb_fitness_advanced_n" to R.drawable.onb_fitness_advanced_n,
        "onb_improve_strength_n" to R.drawable.onb_improve_strength_n,
        "onb_improve_focus_n" to R.drawable.onb_improve_focus_n,
        "onb_improve_sleep_n" to R.drawable.onb_improve_sleep_n,
        "onb_improve_screen_n" to R.drawable.onb_improve_screen_n,
        "onb_improve_discipline_n" to R.drawable.onb_improve_discipline_n,
        "onb_improve_fitness_n" to R.drawable.onb_improve_fitness_n,
        "onb_improve_learning_n" to R.drawable.onb_improve_learning_n,
        "onb_obstacle_scrolling_n" to R.drawable.onb_obstacle_scrolling_n,
        "onb_obstacle_sleep_n" to R.drawable.onb_obstacle_sleep_n,
        "onb_obstacle_sitting_n" to R.drawable.onb_obstacle_sitting_n,
        "onb_obstacle_procrastination_n" to R.drawable.onb_obstacle_procrastination_n,
        "onb_obstacle_none_n" to R.drawable.onb_obstacle_none_n,
        "onb_equipment_bodyweight_n" to R.drawable.onb_equipment_bodyweight_n,
        "onb_equipment_dumbbells_n" to R.drawable.onb_equipment_dumbbells_n,
        "onb_equipment_gym_n" to R.drawable.onb_equipment_gym_n,
        "onb_permission_health_n" to R.drawable.onb_permission_health_n,
        "onb_permission_usage_n" to R.drawable.onb_permission_usage_n,
        "onb_permission_notifications_n" to R.drawable.onb_permission_notifications_n,
    )

    /** The drawable for [step] and [variant] in [set]; the neutral image when that set has none, 0 when there is no such image. */
    @DrawableRes
    fun resolve(step: String, variant: String, set: IllustrationSet?): Int {
        val suffix = when (set) {
            IllustrationSet.MALE -> "m"
            IllustrationSet.FEMALE -> "f"
            else -> "n"
        }
        return all["onb_${step}_${variant}_$suffix"] ?: all["onb_${step}_${variant}_n"] ?: 0
    }
}
