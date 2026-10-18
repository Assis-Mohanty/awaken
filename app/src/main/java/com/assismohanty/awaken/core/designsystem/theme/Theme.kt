package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark-only in v1 (DESIGN_SYSTEM.md). No dynamic colour: the System has one look.
private val AwakenColorScheme = darkColorScheme(
    primary = SystemBlue,
    onPrimary = Void,
    primaryContainer = PanelRaised,
    onPrimaryContainer = Aether,
    secondary = Aether,
    onSecondary = Void,
    background = Void,
    onBackground = Bone,
    surface = Void,
    onSurface = Bone,
    surfaceVariant = Depth,
    onSurfaceVariant = Mist,
    surfaceContainer = Depth,
    outline = Edge,
    outlineVariant = Fog,
    error = Ember,
    onError = Void,
)

@Composable
fun AwakenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AwakenColorScheme,
        typography = AwakenTypography,
        content = content,
    )
}
