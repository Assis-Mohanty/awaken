package com.assismohanty.awaken.debug

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.core.designsystem.components.DebugBanner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R

/** Debug-build hooks into main navigation. The release source set has a no-op twin with the same API. */
object DebugEntry {
    const val ENABLED = true

    @Composable
    fun LauncherButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
        val description = stringResource(R.string.debug_open_description)
        TextButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
            Text(stringResource(R.string.debug_open))
        }
    }

    /**
     * A strip across the top of every screen while a debug override fakes data (simulated steps or usage, time travel),
     * with one tap back to real data. Nothing shows when everything is real.
     */
    @Composable
    fun OverrideBanner(modifier: Modifier = Modifier) {
        val viewModel: DebugBannerViewModel = hiltViewModel()
        val overrides by viewModel.overrides.collectAsStateWithLifecycle()
        if (overrides.isEmpty()) return
        val parts = overrides.map { override ->
            when (override) {
                is DebugOverride.ClockOffset -> pluralStringResource(R.plurals.debug_banner_clock, override.days.toInt(), override.days)
                DebugOverride.SimulatedSteps -> stringResource(R.string.debug_banner_steps)
                DebugOverride.SimulatedUsage -> stringResource(R.string.debug_banner_usage)
            }
        }
        DebugBanner(
            text = stringResource(R.string.debug_banner, parts.joinToString(" · ")),
            action = stringResource(R.string.debug_banner_reset),
            onReset = viewModel::reset,
            modifier = modifier,
        )
    }

    @Composable
    fun Screen(onBack: () -> Unit, onOpenOnboarding: () -> Unit) {
        var showCatalog by rememberSaveable { mutableStateOf(false) }
        if (showCatalog) {
            BackHandler { showCatalog = false }
            DesignSystemCatalog(onBack = { showCatalog = false })
        } else {
            DebugRoute(onBack = onBack, onOpenCatalog = { showCatalog = true }, onOpenOnboarding = onOpenOnboarding)
        }
    }
}
