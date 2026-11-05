package com.assismohanty.awaken.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs

/** Bottom-bar destinations (DESIGN_SYSTEM §4): five items, icon + label. Paths opens from Progress. */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    @StringRes val labelRes: Int,
) {
    TODAY(TodayRoute, AwakenGlyphs.Home, AwakenGlyphs.HomeSolid, R.string.nav_today),
    FOCUS(FocusRoute, AwakenGlyphs.Focus, AwakenGlyphs.FocusSolid, R.string.nav_focus),
    GYM(GymRoute, AwakenGlyphs.Dumbbell, AwakenGlyphs.DumbbellSolid, R.string.nav_gym),
    DIET(DietRoute, AwakenGlyphs.Bowl, AwakenGlyphs.BowlSolid, R.string.nav_diet),
    STATUS(StatusRoute, AwakenGlyphs.Bars, AwakenGlyphs.BarsSolid, R.string.nav_status),
}
