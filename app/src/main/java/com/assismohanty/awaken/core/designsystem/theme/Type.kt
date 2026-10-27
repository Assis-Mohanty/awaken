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

/**
 * Inter (SIL OFL, `res/font/inter.ttf`, variable `wght` 100–900, licence in `assets/licenses/OFL_Inter.txt`): one family
 * for everything (DESIGN_SYSTEM §2.2). Each weight pins the axis.
 */
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Tabular figures everywhere, so numbers never jitter while they count up; Inter's open digits read well at size. */
private const val FEATURES = "tnum, cv05"

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = FEATURES,
)

/**
 * Type roles (DESIGN_SYSTEM §2.2). Hierarchy comes from size and weight, never colour; everything is sentence case.
 * Inter is tuned for small sizes, so large roles tighten their tracking.
 */
@Immutable
data class AwakenType(
    /** The one big number on a screen ("61", "2,503"). */
    val metricXL: TextStyle = style(FontWeight.SemiBold, 56, 60, tracking = -0.02),
    /** The unit next to a big number ("kg", "of 10"). */
    val metricUnit: TextStyle = style(FontWeight.Medium, 20, 24, tracking = -0.01),
    /** A value in a metric card. */
    val metricL: TextStyle = style(FontWeight.SemiBold, 34, 40, tracking = -0.02),
    /** Large screen titles ("Good morning, Sam"); collapses to [title] on scroll. */
    val headline: TextStyle = style(FontWeight.Bold, 34, 41, tracking = -0.02),
    /** Section headings on a screen ("Morning"). */
    val section: TextStyle = style(FontWeight.SemiBold, 22, 28, tracking = -0.015),
    /** Card titles. */
    val windowTitle: TextStyle = style(FontWeight.Medium, 17, 22, tracking = -0.01),
    /** A small label over a value ("Result", "Trend"). Sentence case. */
    val overline: TextStyle = style(FontWeight.Medium, 13, 18),
    val numeric: TextStyle = style(FontWeight.SemiBold, 20, 24, tracking = -0.01),
    val numericSmall: TextStyle = style(FontWeight.Medium, 15, 20),
    /** Collapsed screen titles, row titles, buttons. */
    val title: TextStyle = style(FontWeight.SemiBold, 17, 22, tracking = -0.01),
    val body: TextStyle = style(FontWeight.Normal, 15, 21),
    val label: TextStyle = style(FontWeight.Medium, 13, 18),
    val caption: TextStyle = style(FontWeight.Normal, 13, 18),
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
    labelSmall = caption,
)
