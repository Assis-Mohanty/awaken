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
    GATES(GatesRoute, AwakenGlyphs.Portal, R.string.nav_gates),
    STATUS(StatusRoute, AwakenGlyphs.Pentagon, R.string.nav_status),
    VAULT(VaultRoute, AwakenGlyphs.Vault, R.string.nav_vault),
}
