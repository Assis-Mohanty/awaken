package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
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
    "kcal" -> R.plurals.unit_kcal
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
fun unitLabel(unit: String, count: Int): String = unitPluralRes(unit)?.let { lexPlural(it, count) } ?: unit

@Composable
@ReadOnlyComposable
fun Stat.label(): String = lexString(nameRes())

@Composable
@ReadOnlyComposable
fun Difficulty.label(): String = lexString(nameRes())

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** "1,240" in the user's locale. */
@Composable
@ReadOnlyComposable
fun formatCount(value: Long): String = NumberFormat.getIntegerInstance(currentLocale()).format(value)

@Composable
@ReadOnlyComposable
fun formatCount(value: Int): String = formatCount(value.toLong())

/** "45 min", "2 h", "2 h 15 min": screen-time durations (Screen time screen, Today tile). */
@Composable
fun formatMinutes(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> lexString(R.string.screentime_duration_minutes, formatCount(rest))
        rest == 0 -> lexString(R.string.screentime_duration_hours, formatCount(hours))
        else -> lexString(R.string.screentime_duration_hours_minutes, formatCount(hours), formatCount(rest))
    }
}

private const val MINUTES_PER_HOUR = 60

/** "2.4": one decimal in the user's locale (insight means, recap averages). */
@Composable
@ReadOnlyComposable
fun formatOneDecimal(value: Double): String =
    NumberFormat.getNumberInstance(currentLocale()).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(value)

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

/** Store item display name (GD §9.2); null for an id this build doesn't know. */
@StringRes
fun shopItemNameRes(itemId: String): Int? = when (itemId) {
    "streak_shield" -> R.string.shop_streak_shield
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

/** The words after "Someone who" for an identity id (GD §14.7); null for an id this build doesn't know. */
@StringRes
fun identityPhraseRes(id: String): Int? = when (id) {
    "trains" -> R.string.identity_trains
    "runs" -> R.string.identity_runs
    "reads" -> R.string.identity_reads
    "ships" -> R.string.identity_ships
    "learns" -> R.string.identity_learns
    "focuses" -> R.string.identity_focuses
    "keeps_promises" -> R.string.identity_keeps_promises
    "meditates" -> R.string.identity_meditates
    "sleeps_well" -> R.string.identity_sleeps_well
    "eats_well" -> R.string.identity_eats_well
    "stays_in_touch" -> R.string.identity_stays_in_touch
    "speaks_up" -> R.string.identity_speaks_up
    else -> null
}
