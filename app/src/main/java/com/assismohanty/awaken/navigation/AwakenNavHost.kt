package com.assismohanty.awaken.navigation

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.assismohanty.awaken.feature.diet.DietRoute as DietEntry
import com.assismohanty.awaken.feature.gate.GatesEntry
import com.assismohanty.awaken.feature.gym.GymRoute as GymEntry
import com.assismohanty.awaken.feature.onboarding.OnboardingRoute as OnboardingEntry
import com.assismohanty.awaken.feature.paths.PathDetailRoute as PathDetailEntry
import com.assismohanty.awaken.feature.paths.PathsRoute as PathsEntry
import com.assismohanty.awaken.feature.quest.QuestDetailRoute as QuestDetailEntry
import com.assismohanty.awaken.feature.quest.RewardHost
import com.assismohanty.awaken.feature.health.HealthRoute as HealthEntry
import com.assismohanty.awaken.feature.campaign.CampaignRoute as CampaignEntry
import com.assismohanty.awaken.feature.screentime.ScreenTimeRoute as ScreenTimeEntry
import com.assismohanty.awaken.feature.settings.SettingsRoute as SettingsEntry
import com.assismohanty.awaken.feature.status.StatusRoute as StatusEntry
import com.assismohanty.awaken.feature.today.TodayRoute as TodayEntry
import com.assismohanty.awaken.feature.trial.TrialDetailRoute as TrialDetailEntry
import com.assismohanty.awaken.feature.vault.VaultRoute as VaultEntry

// Feature `*Route` composables are imported as `*Entry`: the serializable route types here share their names.

/** Onboarding until a hunter exists, then the four tabs (DESIGN_SYSTEM §4). */
@Composable
fun AwakenNavHost(modifier: Modifier = Modifier, openTodayRequests: Int = 0, appViewModel: AppViewModel = hiltViewModel()) {
    val launch by appViewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(appViewModel) {
        appViewModel.onEvent(AppEvent.Started)
        onStopOrDispose { appViewModel.onEvent(AppEvent.Stopped) }
    }
    when (val state = launch) {
        // One frame of void while Room answers; no spinner for a local read.
        LaunchState.Loading -> Box(modifier = modifier.fillMaxSize().background(AwakenTheme.palette.void))
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
                        containerColor = AwakenTheme.palette.bone,
                        contentColor = AwakenTheme.palette.void,
                        actionColor = AwakenTheme.palette.heroWashA,
                        shape = AwakenTheme.shapes.inner,
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = AwakenTheme.palette.panel,
                        modifier = Modifier.topHairline(AwakenTheme.palette.edge, AwakenTheme.dimens.hairline),
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentDestination?.hierarchy
                                ?.any { it.hasRoute(destination.route::class) } == true
                            val label = lexString(destination.labelRes)
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
                                label = { Text(label, style = AwakenTheme.type.caption.copy(fontWeight = FontWeight.SemiBold)) },
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
                            onOpenCampaign = { navController.navigate(CampaignRoute) },
                            onOpenTrial = { navController.navigate(TrialRoute) },
                            onOpenGates = { navController.navigate(GatesRoute) { launchSingleTop = true } },
                            onOpenVault = { navController.navigate(VaultRoute) { launchSingleTop = true } },
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
                        onOpenGates = { navController.navigate(GatesRoute) { launchSingleTop = true } },
                    )
                }
                composable<TrialRoute> {
                    TrialDetailEntry(
                        onBack = { navController.popBackStack() },
                        onOpenQuest = { navController.navigate(QuestDetailRoute(it)) },
                    )
                }
                composable<PathsRoute> {
                    PathsEntry(onOpenPath = { navController.navigate(PathDetailRoute(it)) })
                }
                composable<PathDetailRoute> { PathDetailEntry(onBack = { navController.popBackStack() }) }
                composable<GatesRoute> { GatesEntry(onBack = { navController.popBackStack() }) }
                composable<GymRoute> { GymEntry() }
                composable<DietRoute> { DietEntry() }
                composable<StatusRoute> {
                    StatusEntry(
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenPaths = { navController.navigate(PathsRoute) { launchSingleTop = true } },
                    )
                }
                composable<SettingsRoute> {
                    SettingsEntry(
                        onBack = { navController.popBackStack() },
                        onOpenHealth = { navController.navigate(HealthRoute) },
                        onOpenScreenTime = { navController.navigate(ScreenTimeRoute) },
                    )
                }
                composable<HealthRoute> { HealthEntry(onBack = { navController.popBackStack() }) }
                composable<ScreenTimeRoute> { ScreenTimeEntry(onBack = { navController.popBackStack() }) }
                composable<CampaignRoute> { CampaignEntry(onBack = { navController.popBackStack() }) }
                composable<VaultRoute> { VaultEntry(onBack = { navController.popBackStack() }) }
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

/** Selected tab: accent icon on a soft accent pill, bold label; the rest stay quiet (DESIGN_SYSTEM §4). */
@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AwakenTheme.palette.system,
    selectedTextColor = AwakenTheme.palette.bone,
    unselectedIconColor = AwakenTheme.palette.mist,
    unselectedTextColor = AwakenTheme.palette.mist,
    indicatorColor = AwakenTheme.palette.system.copy(alpha = NAV_PILL_ALPHA),
)

private const val NAV_PILL_ALPHA = 0.12f
