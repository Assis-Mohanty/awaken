package com.assismohanty.awaken.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs

/** Bottom-bar destinations (DESIGN_SYSTEM §4): four items, icon + label. Paths opens from Progress. */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    TODAY(TodayRoute, AwakenGlyphs.Home, R.string.nav_today),
    GYM(GymRoute, AwakenGlyphs.Dumbbell, R.string.nav_gym),
    DIET(DietRoute, AwakenGlyphs.Bowl, R.string.nav_diet),
    STATUS(StatusRoute, AwakenGlyphs.Bars, R.string.nav_status),
}
