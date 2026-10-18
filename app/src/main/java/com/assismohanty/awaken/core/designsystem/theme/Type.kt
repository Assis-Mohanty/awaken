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

/** The System's voice: titles, numerals, rank letters (DESIGN_SYSTEM §2.2). */
val SairaCondensed = FontFamily(
    Font(R.font.saira_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.saira_condensed_bold, FontWeight.Bold),
)

/** Everything people read. Bundled as Google Fonts' variable TTF; each weight pins the `wght` axis. */
@OptIn(ExperimentalTextApi::class)
val IbmPlexSans = FontFamily(
    Font(R.font.ibm_plex_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.ibm_plex_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.ibm_plex_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private const val TABULAR_NUMS = "tnum"

@Immutable
data class AwakenType(
    val displayXL: TextStyle = TextStyle(fontFamily = SairaCondensed, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp),
    val display: TextStyle = TextStyle(fontFamily = SairaCondensed, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
    /** UPPERCASE only here — callers pass already-uppercased System titles from strings.xml. */
    val windowTitle: TextStyle = TextStyle(
        fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.06.em,
    ),
    val numeric: TextStyle = TextStyle(
        fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp,
        fontFeatureSettings = TABULAR_NUMS,
    ),
    val numericSmall: TextStyle = TextStyle(
        fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp,
        fontFeatureSettings = TABULAR_NUMS,
    ),
    val title: TextStyle = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    val body: TextStyle = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    val label: TextStyle = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    val caption: TextStyle = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)

val LocalAwakenType = staticCompositionLocalOf { AwakenType() }

/** Material components (text fields, sheets, snackbars) pick up the same families. */
internal fun AwakenType.toMaterialTypography(): Typography = Typography(
    displayLarge = displayXL,
    displayMedium = display,
    headlineMedium = display.copy(fontSize = 28.sp, lineHeight = 32.sp),
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
