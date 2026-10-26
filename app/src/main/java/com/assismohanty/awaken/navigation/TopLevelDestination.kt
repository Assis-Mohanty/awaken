package com.assismohanty.awaken.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs

/** Bottom-bar destinations with the System's own line glyphs. */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    TODAY(TodayRoute, AwakenGlyphs.Directive, R.string.nav_today),
    PATHS(PathsRoute, AwakenGlyphs.Path, R.string.nav_paths),
    GYM(GymRoute, AwakenGlyphs.Dumbbell, R.string.nav_gym),
    DIET(DietRoute, AwakenGlyphs.Bowl, R.string.nav_diet),
    STATUS(StatusRoute, AwakenGlyphs.Pentagon, R.string.nav_status),
}
