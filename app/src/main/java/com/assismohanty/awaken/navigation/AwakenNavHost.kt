package com.assismohanty.awaken.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.assismohanty.awaken.core.designsystem.components.GateBackground
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.debug.DebugEntry
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.feature.gate.GatesScreen
import com.assismohanty.awaken.feature.onboarding.OnboardingRoute as OnboardingEntry
import com.assismohanty.awaken.feature.quest.QuestDetailRoute as QuestDetailEntry
import com.assismohanty.awaken.feature.quest.RewardHost
import com.assismohanty.awaken.feature.status.StatusScreen
import com.assismohanty.awaken.feature.today.TodayRoute as TodayEntry
import com.assismohanty.awaken.feature.vault.VaultScreen

// Feature `*Route` composables are imported as `*Entry`: the serializable route types here share their names.

/** Onboarding until a hunter exists, then the four tabs (DESIGN_SYSTEM §4). */
@Composable
fun AwakenNavHost(modifier: Modifier = Modifier, appViewModel: AppViewModel = hiltViewModel()) {
    val launch by appViewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(appViewModel) {
        appViewModel.onEvent(AppEvent.Started)
        onStopOrDispose { }
    }
    when (val state = launch) {
        // One frame of void while Room answers; no spinner for a local read.
        LaunchState.Loading -> Box(modifier = modifier.fillMaxSize().background(AwakenTheme.colors.void))
        LaunchState.NeedsOnboarding -> GateBackground(rank = Rank.E, modifier = modifier) { OnboardingEntry() }
        is LaunchState.Ready -> MainScaffold(rank = state.rank, modifier = modifier)
    }
}

@Composable
private fun MainScaffold(
    rank: Rank,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
    }

    GateBackground(rank = rank, modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = AwakenTheme.colors.panelRaised,
                        contentColor = AwakenTheme.colors.bone,
                        actionColor = AwakenTheme.colors.system,
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = AwakenTheme.colors.depth) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentDestination?.hierarchy
                                ?.any { it.hasRoute(destination.route::class) } == true
                            val label = stringResource(destination.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                // Label text is visible, so the icon is decorative (avoids TalkBack reading it twice).
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TodayRoute,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<TodayRoute> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TodayEntry(onOpenQuest = { navController.navigate(QuestDetailRoute(it)) })
                        if (DebugEntry.ENABLED) {
                            DebugEntry.LauncherButton(
                                onClick = { navController.navigate(DebugRoute) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                    }
                }
                composable<QuestDetailRoute> { QuestDetailEntry(onBack = { navController.popBackStack() }) }
                composable<GatesRoute> { GatesScreen() }
                composable<StatusRoute> { StatusScreen() }
                composable<VaultRoute> { VaultScreen() }
                if (DebugEntry.ENABLED) {
                    composable<DebugRoute> { DebugEntry.Screen(onBack = { navController.popBackStack() }) }
                }
            }
        }
        // Above the scaffold so reward sheets and toasts cover every tab and Quest detail.
        RewardHost(snackbarHostState = snackbarHostState)
    }
}
