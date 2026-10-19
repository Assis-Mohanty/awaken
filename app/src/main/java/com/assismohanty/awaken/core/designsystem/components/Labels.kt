package com.assismohanty.awaken.core.designsystem.components

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import java.text.NumberFormat
import java.util.Locale

@StringRes
fun Stat.nameRes(): Int = when (this) {
    Stat.STR -> R.string.stat_str
    Stat.INT -> R.string.stat_int
    Stat.WIL -> R.string.stat_wil
    Stat.VIT -> R.string.stat_vit
    Stat.CHA -> R.string.stat_cha
}

@StringRes
fun Difficulty.nameRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.NORMAL -> R.string.difficulty_normal
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.EXTREME -> R.string.difficulty_extreme
}

/** Plural resource for a template unit (the closed `units` vocabulary); null for free-text habit units. */
@PluralsRes
fun unitPluralRes(unit: String): Int? = when (unit) {
    "reps" -> R.plurals.unit_reps
    "sec" -> R.plurals.unit_sec
    "min" -> R.plurals.unit_min
    "km" -> R.plurals.unit_km
    "floors" -> R.plurals.unit_floors
    "rounds" -> R.plurals.unit_rounds
    "sprints" -> R.plurals.unit_sprints
    "steps" -> R.plurals.unit_steps
    "hours" -> R.plurals.unit_hours
    "pages" -> R.plurals.unit_pages
    "problems" -> R.plurals.unit_problems
    "cards" -> R.plurals.unit_cards
    "lessons" -> R.plurals.unit_lessons
    "words" -> R.plurals.unit_words
    "items" -> R.plurals.unit_items
    "puzzles" -> R.plurals.unit_puzzles
    "glasses" -> R.plurals.unit_glasses
    "servings" -> R.plurals.unit_servings
    "meals" -> R.plurals.unit_meals
    "breaks" -> R.plurals.unit_breaks
    "people" -> R.plurals.unit_people
    "messages" -> R.plurals.unit_messages
    "posts" -> R.plurals.unit_posts
    else -> null
}

/** "1 item" / "3 items": template units agree with the count; custom habit units are shown as typed. */
@Composable
fun unitLabel(unit: String, count: Int): String = unitPluralRes(unit)?.let { pluralStringResource(it, count) } ?: unit

/** Display text for an engine title id (GD §10); null for an id this build doesn't know. */
@StringRes
fun titleNameRes(titleId: String): Int? = when (titleId) {
    "the_awakened" -> R.string.title_the_awakened
    "rising_hunter" -> R.string.title_rising_hunter
    "veteran" -> R.string.title_veteran
    "unbroken" -> R.string.title_unbroken
    "the_relentless" -> R.string.title_the_relentless
    "fortunes_favoured" -> R.string.title_fortunes_favoured
    "gate_sovereign" -> R.string.title_gate_sovereign
    "proven" -> R.string.title_proven
    "the_redeemed" -> R.string.title_the_redeemed
    "balanced" -> R.string.title_balanced
    "ironborn" -> R.string.title_ironborn
    "sage" -> R.string.title_sage
    "stoic" -> R.string.title_stoic
    "evergreen" -> R.string.title_evergreen
    "silver_tongue" -> R.string.title_silver_tongue
    "d_rank_hunter" -> R.string.title_d_rank_hunter
    "c_rank_hunter" -> R.string.title_c_rank_hunter
    "b_rank_hunter" -> R.string.title_b_rank_hunter
    "a_rank_hunter" -> R.string.title_a_rank_hunter
    "s_rank_hunter" -> R.string.title_s_rank_hunter
    else -> null
}

/** Unlock condition shown on a locked achievement (GD §10); null for an id this build doesn't know. */
@StringRes
fun achievementConditionRes(achievementId: String): Int? = when (achievementId) {
    "first_blood" -> R.string.condition_first_blood
    "awakened" -> R.string.condition_awakened
    "lvl5" -> R.string.condition_lvl5
    "lvl10" -> R.string.condition_lvl10
    "lvl25" -> R.string.condition_lvl25
    "lvl50" -> R.string.condition_lvl50
    "streak3" -> R.string.condition_streak3
    "streak7" -> R.string.condition_streak7
    "streak30" -> R.string.condition_streak30
    "streak100" -> R.string.condition_streak100
    "crit1" -> R.string.condition_crit1
    "crit25" -> R.string.condition_crit25
    "gate_first" -> R.string.condition_gate_first
    "gate_hours10" -> R.string.condition_gate_hours10
    "gate_hours100" -> R.string.condition_gate_hours100
    "verified10" -> R.string.condition_verified10
    "verified100" -> R.string.condition_verified100
    "redeemed" -> R.string.condition_redeemed
    "balanced" -> R.string.condition_balanced
    "str20" -> R.string.condition_str20
    "int20" -> R.string.condition_int20
    "wil20" -> R.string.condition_wil20
    "vit20" -> R.string.condition_vit20
    "cha20" -> R.string.condition_cha20
    "rank_d" -> R.string.condition_rank_d
    "rank_c" -> R.string.condition_rank_c
    "rank_b" -> R.string.condition_rank_b
    "rank_a" -> R.string.condition_rank_a
    "rank_s" -> R.string.condition_rank_s
    "early_bird" -> R.string.condition_early_bird
    "night_owl" -> R.string.condition_night_owl
    "collector" -> R.string.condition_collector
    else -> null
}

/** Display name for an engine achievement id (GD §10); null for an id this build doesn't know. */
@StringRes
fun achievementNameRes(achievementId: String): Int? = when (achievementId) {
    "first_blood" -> R.string.achievement_first_blood
    "awakened" -> R.string.achievement_awakened
    "lvl5" -> R.string.achievement_lvl5
    "lvl10" -> R.string.achievement_lvl10
    "lvl25" -> R.string.achievement_lvl25
    "lvl50" -> R.string.achievement_lvl50
    "streak3" -> R.string.achievement_streak3
    "streak7" -> R.string.achievement_streak7
    "streak30" -> R.string.achievement_streak30
    "streak100" -> R.string.achievement_streak100
    "crit1" -> R.string.achievement_crit1
    "crit25" -> R.string.achievement_crit25
    "gate_first" -> R.string.achievement_gate_first
    "gate_hours10" -> R.string.achievement_gate_hours10
    "gate_hours100" -> R.string.achievement_gate_hours100
    "verified10" -> R.string.achievement_verified10
    "verified100" -> R.string.achievement_verified100
    "redeemed" -> R.string.achievement_redeemed
    "balanced" -> R.string.achievement_balanced
    "str20" -> R.string.achievement_str20
    "int20" -> R.string.achievement_int20
    "wil20" -> R.string.achievement_wil20
    "vit20" -> R.string.achievement_vit20
    "cha20" -> R.string.achievement_cha20
    "rank_d" -> R.string.achievement_rank_d
    "rank_c" -> R.string.achievement_rank_c
    "rank_b" -> R.string.achievement_rank_b
    "rank_a" -> R.string.achievement_rank_a
    "rank_s" -> R.string.achievement_rank_s
    "early_bird" -> R.string.achievement_early_bird
    "night_owl" -> R.string.achievement_night_owl
    "collector" -> R.string.achievement_collector
    else -> null
}

@Composable
@ReadOnlyComposable
fun Stat.label(): String = stringResource(nameRes())

@Composable
@ReadOnlyComposable
fun Difficulty.label(): String = stringResource(nameRes())

@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** "1,240" in the user's locale. */
@Composable
@ReadOnlyComposable
fun formatCount(value: Long): String = NumberFormat.getIntegerInstance(currentLocale()).format(value)

@Composable
@ReadOnlyComposable
fun formatCount(value: Int): String = formatCount(value.toLong())

/** Multipliers: fixed two decimals ("1.20") or trimmed ("1.5", "3") per the breakdown examples. */
@Composable
@ReadOnlyComposable
fun formatMultiplier(value: Double, fixed: Boolean): String {
    val format = NumberFormat.getNumberInstance(currentLocale()).apply {
        minimumFractionDigits = if (fixed) 2 else 0
        maximumFractionDigits = 2
    }
    return format.format(value)
}

/** Vault item display name (GD §9.2); null for an id this build doesn't know. */
@StringRes
fun shopItemNameRes(itemId: String): Int? = when (itemId) {
    "streak_shield" -> R.string.shop_streak_shield
    "theme_crimson_gate" -> R.string.shop_theme_crimson_gate
    "theme_abyssal_violet" -> R.string.shop_theme_abyssal_violet
    "theme_sovereign_gold" -> R.string.shop_theme_sovereign_gold
    "frame_iron" -> R.string.shop_frame_iron
    "frame_ember" -> R.string.shop_frame_ember
    "frame_umbral" -> R.string.shop_frame_umbral
    "frame_regal" -> R.string.shop_frame_regal
    "sigil_blade" -> R.string.shop_sigil_blade
    "sigil_flame" -> R.string.shop_sigil_flame
    "sigil_eye" -> R.string.shop_sigil_eye
    "sigil_wolf" -> R.string.shop_sigil_wolf
    "sigil_crown" -> R.string.shop_sigil_crown
    "sigil_hourglass" -> R.string.shop_sigil_hourglass
    "sigil_star" -> R.string.shop_sigil_star
    "sigil_gate" -> R.string.shop_sigil_gate
    else -> null
}

/** Equipment display name for a template-vocabulary id; null for an id this build doesn't know. */
@StringRes
fun equipmentNameRes(equipmentId: String): Int? = when (equipmentId) {
    "dumbbells" -> R.string.equipment_dumbbells
    "kettlebell" -> R.string.equipment_kettlebell
    "pullup_bar" -> R.string.equipment_pullup_bar
    "resistance_band" -> R.string.equipment_resistance_band
    "jump_rope" -> R.string.equipment_jump_rope
    "gym_access" -> R.string.equipment_gym_access
    "bicycle" -> R.string.equipment_bicycle
    else -> null
}

/** Injury-mode tag display name; null for an id this build doesn't know. */
@StringRes
fun injuryTagNameRes(tag: String): Int? = when (tag) {
    "impact" -> R.string.injury_tag_impact
    "legs" -> R.string.injury_tag_legs
    "upper" -> R.string.injury_tag_upper
    else -> null
}
