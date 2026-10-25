package com.assismohanty.awaken.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
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
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.debug.DebugEntry
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.feature.gate.GatesEntry
import com.assismohanty.awaken.feature.onboarding.OnboardingRoute as OnboardingEntry
import com.assismohanty.awaken.feature.paths.PathDetailRoute as PathDetailEntry
import com.assismohanty.awaken.feature.paths.PathsRoute as PathsEntry
import com.assismohanty.awaken.feature.quest.QuestDetailRoute as QuestDetailEntry
import com.assismohanty.awaken.feature.quest.RewardHost
import com.assismohanty.awaken.feature.health.HealthRoute as HealthEntry
import com.assismohanty.awaken.feature.screentime.ScreenTimeRoute as ScreenTimeEntry
import com.assismohanty.awaken.feature.settings.SettingsRoute as SettingsEntry
import com.assismohanty.awaken.feature.status.StatusRoute as StatusEntry
import com.assismohanty.awaken.feature.today.TodayRoute as TodayEntry
import com.assismohanty.awaken.feature.trial.TrialDetailRoute as TrialDetailEntry
import com.assismohanty.awaken.feature.vault.VaultRoute as VaultEntry

// Feature `*Route` composables are imported as `*Entry`: the serializable route types here share their names.

/** Onboarding until a hunter exists, then the five tabs (DESIGN_SYSTEM §4). */
@Composable
fun AwakenNavHost(modifier: Modifier = Modifier, openTodayRequests: Int = 0, appViewModel: AppViewModel = hiltViewModel()) {
    val launch by appViewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(appViewModel) {
        appViewModel.onEvent(AppEvent.Started)
        onStopOrDispose { appViewModel.onEvent(AppEvent.Stopped) }
    }
    when (val state = launch) {
        // One frame of void while Room answers; no spinner for a local read.
        LaunchState.Loading -> Box(modifier = modifier.fillMaxSize().background(AwakenTheme.colors.void))
        LaunchState.NeedsOnboarding -> GateBackground(rank = Rank.E, modifier = modifier) { OnboardingEntry() }
        is LaunchState.Ready -> MainScaffold(rank = state.rank, openTodayRequests = openTodayRequests, modifier = modifier)
    }
}

@Composable
private fun MainScaffold(
    openTodayRequests: Int,
    rank: Rank,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    // A notification or widget tap asks for Today even when another tab or screen was open (ARCH §3.5, §3.6).
    LaunchedEffect(openTodayRequests) {
        if (openTodayRequests > 0) {
            navController.navigate(TodayRoute) {
                popUpTo<TodayRoute>()
                launchSingleTop = true
            }
        }
    }
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
                    NavigationBar(
                        containerColor = AwakenTheme.colors.depth,
                        modifier = Modifier.topHairline(AwakenTheme.colors.edge, AwakenTheme.dimens.hairline),
                    ) {
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
                                icon = { NavIcon(destination.icon, selected) },
                                label = { Text(label) },
                                colors = navItemColors(),
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
                        TodayEntry(
                            onOpenQuest = { navController.navigate(QuestDetailRoute(it)) },
                            onOpenScreenTime = { navController.navigate(ScreenTimeRoute) },
                            onOpenTrial = { navController.navigate(TrialRoute) },
                        )
                        if (DebugEntry.ENABLED) {
                            DebugEntry.LauncherButton(
                                onClick = { navController.navigate(DebugRoute) },
                                // Bottom corner: the HunterHeader owns the top of Today.
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }
                    }
                }
                composable<QuestDetailRoute> {
                    QuestDetailEntry(
                        onBack = { navController.popBackStack() },
                        onOpenGates = {
                            navController.navigate(GatesRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable<TrialRoute> {
                    TrialDetailEntry(
                        onBack = { navController.popBackStack() },
                        onOpenQuest = { navController.navigate(QuestDetailRoute(it)) },
                    )
                }
                composable<PathsRoute> { PathsEntry(onOpenPath = { navController.navigate(PathDetailRoute(it)) }) }
                composable<PathDetailRoute> { PathDetailEntry(onBack = { navController.popBackStack() }) }
                composable<GatesRoute> { GatesEntry() }
                composable<StatusRoute> { StatusEntry(onOpenSettings = { navController.navigate(SettingsRoute) }) }
                composable<SettingsRoute> {
                    SettingsEntry(
                        onBack = { navController.popBackStack() },
                        onOpenHealth = { navController.navigate(HealthRoute) },
                        onOpenScreenTime = { navController.navigate(ScreenTimeRoute) },
                    )
                }
                composable<HealthRoute> { HealthEntry(onBack = { navController.popBackStack() }) }
                composable<ScreenTimeRoute> { ScreenTimeEntry(onBack = { navController.popBackStack() }) }
                composable<VaultRoute> { VaultEntry() }
                if (DebugEntry.ENABLED) {
                    composable<DebugRoute> { DebugEntry.Screen(onBack = { navController.popBackStack() }) }
                }
            }
        }
        // Above the scaffold so reward sheets and toasts cover every tab and Quest detail.
        RewardHost(snackbarHostState = snackbarHostState)
    }
}

/** Bottom bar seam: one `edge` hairline along the top instead of an elevation shadow (DS §2.3). */
private fun Modifier.topHairline(color: Color, width: Dp): Modifier = drawBehind {
    drawLine(color, start = Offset(0f, 0f), end = Offset(size.width, 0f), strokeWidth = width.toPx())
}

/** Tab icon with a short `system` bar above it when selected: our selection mark instead of Material's pill (DS §3). */
@Composable
private fun NavIcon(icon: ImageVector, selected: Boolean) {
    val motion = AwakenTheme.motion
    val dimens = AwakenTheme.dimens
    val mark by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(motion.duration(motion.standardMs), easing = motion.standardEasing),
        label = "navMark",
    )
    val markColor = AwakenTheme.colors.system
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Box(
            modifier = Modifier
                .size(width = dimens.navIndicatorWidth, height = dimens.navIndicatorHeight)
                .graphicsLayer { scaleX = mark }
                .background(markColor.copy(alpha = mark)),
        )
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AwakenTheme.colors.bone,
    selectedTextColor = AwakenTheme.colors.bone,
    unselectedIconColor = AwakenTheme.colors.mist,
    unselectedTextColor = AwakenTheme.colors.mist,
    indicatorColor = Color.Transparent,
)
