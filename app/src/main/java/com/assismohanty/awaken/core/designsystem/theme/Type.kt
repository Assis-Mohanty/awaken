package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.assismohanty.awaken.R

/*
 * Archivo (SIL OFL, `res/font/archivo.ttf`, variable `wght` 100-900 and `wdth` 62-125, licence in
 * `assets/licenses/OFL_Archivo.txt`): one family for everything (design/DESIGN.md). A font family ignores the width
 * axis when it matches a style, so text at normal width and big numerals at width 104 are two families, each pinning
 * both axes per weight.
 */
private const val WIDTH_NORMAL = 100f
private const val WIDTH_METRIC = 104f
private val Weights = listOf(400, 500, 600, 650)

@OptIn(ExperimentalTextApi::class)
private fun archivo(width: Float) = FontFamily(
    Weights.map { weight ->
        Font(
            R.font.archivo,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
        )
    },
)

/** Text, titles and buttons. */
val Archivo: FontFamily = archivo(WIDTH_NORMAL)

/** Big numbers: slightly wider, so a large numeral stays steady. */
val ArchivoMetric: FontFamily = archivo(WIDTH_METRIC)

/** Tabular, lining figures everywhere, so numbers never jitter while they change. */
private const val FEATURES = "tnum, lnum"

private fun style(
    weight: Int,
    size: Float,
    line: Float,
    tracking: Double = 0.0,
    family: FontFamily = Archivo,
) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = FEATURES,
)

/**
 * Type roles (design/DESIGN.md). Hierarchy comes from size and weight, never colour; everything is sentence case.
 * Large numerals are tight (negative tracking) and medium weight, never chunky.
 */
@Immutable
data class AwakenType(
    /** A clock: the session dial. */
    val display: TextStyle = style(600, 76f, 76f, tracking = -0.04),
    /** The one big number on a screen ("61", "2,503"). */
    val metricXL: TextStyle = style(600, 54f, 56f, tracking = -0.045, family = ArchivoMetric),
    /** The unit next to a big number ("kg", "of 10"). */
    val metricUnit: TextStyle = style(500, 20f, 24f, tracking = -0.01),
    /** The level inside the level ring. */
    val metricS: TextStyle = style(650, 26f, 28f, tracking = -0.03),
    /** A value in a metric card. */
    val metricL: TextStyle = style(600, 34f, 38f, tracking = -0.04, family = ArchivoMetric),
    /** Tab titles ("Today", "Gym"). */
    val headline: TextStyle = style(650, 24f, 28f, tracking = -0.022),
    /** Section headings on a screen ("Morning"). */
    val section: TextStyle = style(650, 19f, 24f, tracking = -0.015),
    /** Card and sheet titles. */
    val windowTitle: TextStyle = style(650, 17f, 22f, tracking = -0.01),
    /** A small label over a value ("Result", "Trend"). Sentence case. */
    val overline: TextStyle = style(400, 13.5f, 18f),
    val numeric: TextStyle = style(650, 20f, 24f, tracking = -0.02),
    val numericSmall: TextStyle = style(600, 15f, 20f),
    /** Pushed screen titles, row titles, buttons. */
    val title: TextStyle = style(600, 17f, 22f, tracking = -0.005),
    val body: TextStyle = style(400, 16f, 22f),
    val label: TextStyle = style(600, 15f, 20f),
    val caption: TextStyle = style(400, 13.5f, 18f),
    /** Nav labels and chart axes: the floor, never smaller. */
    val micro: TextStyle = style(500, 12f, 16f),
)

val LocalAwakenType = staticCompositionLocalOf { AwakenType() }

/** Material components (text fields, sheets, snackbars) pick up the same family. */
internal fun AwakenType.toMaterialTypography(): Typography = Typography(
    displayLarge = metricXL,
    displayMedium = metricL,
    headlineMedium = headline,
    titleLarge = windowTitle,
    titleMedium = title,
    titleSmall = title.copy(fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = title,
    labelMedium = label,
    labelSmall = micro,
)
