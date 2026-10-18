package com.assismohanty.awaken.core.designsystem.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
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
