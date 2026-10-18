package com.assismohanty.awaken.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import com.assismohanty.awaken.R

/** Bottom-bar destinations. Icons are placeholders until the Phase 5 icon set. */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    TODAY(TodayRoute, Icons.Filled.DateRange, R.string.nav_today),
    GATES(GatesRoute, Icons.Filled.PlayArrow, R.string.nav_gates),
    STATUS(StatusRoute, Icons.Filled.AccountCircle, R.string.nav_status),
    VAULT(VaultRoute, Icons.Filled.Lock, R.string.nav_vault),
}
