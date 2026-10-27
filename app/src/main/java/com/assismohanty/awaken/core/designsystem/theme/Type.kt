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
 * Plus Jakarta Sans (SIL OFL, `res/font/plus_jakarta_sans.ttf`, variable `wght` 200–800): one modern family for
 * everything (DESIGN_SYSTEM §2.2). Each weight pins the axis.
 */
@OptIn(ExperimentalTextApi::class)
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private const val TABULAR_NUMS = "tnum"

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0, tabular: Boolean = false) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = if (tabular) TABULAR_NUMS else null,
)

/** Type roles (DESIGN_SYSTEM §2.2). Numbers are big and bold; words are calm. */
@Immutable
data class AwakenType(
    /** The hero numeral inside a ring ("61"). */
    val metricXL: TextStyle = style(FontWeight.ExtraBold, 60, 64, tracking = -0.03, tabular = true),
    /** A large value in a card ("2,503"). */
    val metricL: TextStyle = style(FontWeight.Bold, 34, 40, tracking = -0.02, tabular = true),
    /** Screen headlines and greetings. */
    val headline: TextStyle = style(FontWeight.Bold, 26, 32, tracking = -0.01),
    /** Section headings on a screen ("Latest measurements"). */
    val section: TextStyle = style(FontWeight.Bold, 20, 26, tracking = -0.01),
    /** Card titles. System mode passes UPPERCASE strings; Clean passes sentence case. */
    val windowTitle: TextStyle = style(FontWeight.Bold, 17, 22),
    /** Small uppercase label over a value ("RESULT", "TREND"). Callers uppercase the text. */
    val overline: TextStyle = style(FontWeight.SemiBold, 11, 14, tracking = 0.1),
    val numeric: TextStyle = style(FontWeight.SemiBold, 20, 24, tabular = true),
    val numericSmall: TextStyle = style(FontWeight.SemiBold, 14, 18, tabular = true),
    val title: TextStyle = style(FontWeight.SemiBold, 17, 22),
    val body: TextStyle = style(FontWeight.Normal, 15, 22),
    val label: TextStyle = style(FontWeight.Medium, 13, 18),
    val caption: TextStyle = style(FontWeight.Normal, 12, 16),
) {
    companion object {
        /** System mode keeps the HUD feel: tracked-out window titles. */
        val System = AwakenType(windowTitle = style(FontWeight.Bold, 16, 22, tracking = 0.08))
    }
}

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
    labelLarge = label.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = label,
    labelSmall = caption,
)
