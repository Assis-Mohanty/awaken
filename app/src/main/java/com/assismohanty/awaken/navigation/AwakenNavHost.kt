package com.assismohanty.awaken.navigation

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.assismohanty.awaken.core.designsystem.components.FloatingNavBar
import com.assismohanty.awaken.core.designsystem.components.GateBackground
import com.assismohanty.awaken.core.designsystem.components.NavBarItem
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
import com.assismohanty.awaken.feature.review.ReviewRoute as ReviewEntry
import com.assismohanty.awaken.feature.recap.RecapRoute as RecapEntry
import com.assismohanty.awaken.feature.trial.TrialDetailRoute as TrialDetailEntry
import com.assismohanty.awaken.feature.vault.VaultRoute as VaultEntry

// Feature `*Route` composables are imported as `*Entry`: the serializable route types here share their names.

/** Onboarding until a hunter exists, then the four tabs (DESIGN_SYSTEM §4). */
@Composable
fun AwakenNavHost(
    modifier: Modifier = Modifier,
    openTodayRequests: Int = 0,
    openReviewRequests: Int = 0,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val launch by appViewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(appViewModel) {
        appViewModel.onEvent(AppEvent.Started)
        onStopOrDispose { appViewModel.onEvent(AppEvent.Stopped) }
    }
    when (val state = launch) {
        // One frame of void while Room answers; no spinner for a local read.
        LaunchState.Loading -> Box(modifier = modifier.fillMaxSize().background(AwakenTheme.palette.void))
        LaunchState.NeedsOnboarding -> GateBackground(rank = Rank.E, modifier = modifier) { OnboardingEntry() }
        is LaunchState.Ready -> MainScaffold(
            rank = state.rank,
            openTodayRequests = openTodayRequests,
            openReviewRequests = openReviewRequests,
            modifier = modifier,
        )
    }
}

@Composable
private fun MainScaffold(
    openTodayRequests: Int,
    rank: Rank,
    openReviewRequests: Int,
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
    // The Sunday notification's review action (GD §14.4) opens the review on top of Today.
    LaunchedEffect(openReviewRequests) {
        if (openReviewRequests > 0) navController.navigate(ReviewRoute) { launchSingleTop = true }
    }
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
    }

    GateBackground(rank = rank, modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            // Debug builds only: says when steps, usage or the date are faked, with a reset (no-op in release).
            topBar = { DebugEntry.OverrideBanner(Modifier.statusBarsPadding()) },
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = AwakenTheme.palette.bone,
                        contentColor = AwakenTheme.palette.void,
                        actionColor = AwakenTheme.palette.void,
                        shape = AwakenTheme.shapes.inner,
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    val destinations = TopLevelDestination.entries
                    val selected = destinations.indexOfFirst { destination ->
                        currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                    }
                    FloatingNavBar(
                        items = destinations.map { NavBarItem(lexString(it.labelRes), it.icon, it.selectedIcon) },
                        selectedIndex = selected,
                        onSelect = { index ->
                            navController.navigate(destinations[index].route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TodayRoute,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<TodayRoute> {
                    TodayEntry(
                        onOpenQuest = { navController.navigate(QuestDetailRoute(it)) },
                        onOpenScreenTime = { navController.navigate(ScreenTimeRoute) },
                        onOpenCampaign = { navController.navigate(CampaignRoute) },
                        onOpenReview = { navController.navigate(ReviewRoute) { launchSingleTop = true } },
                        snackbarHostState = snackbarHostState,
                        onOpenTrial = { navController.navigate(TrialRoute) },
                        onOpenGates = { navController.navigate(GatesRoute) { launchSingleTop = true } },
                        onOpenVault = { navController.navigate(VaultRoute) { launchSingleTop = true } },
                    )
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
                    PathsEntry(onOpenPath = { navController.navigate(PathDetailRoute(it)) }, onBack = { navController.popBackStack() })
                }
                composable<PathDetailRoute> { PathDetailEntry(onBack = { navController.popBackStack() }) }
                composable<GatesRoute> { GatesEntry(onBack = { navController.popBackStack() }) }
                composable<GymRoute> { GymEntry() }
                composable<DietRoute> { DietEntry() }
                composable<StatusRoute> {
                    StatusEntry(
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenPaths = { navController.navigate(PathsRoute) { launchSingleTop = true } },
                        onOpenReview = { navController.navigate(ReviewRoute) { launchSingleTop = true } },
                        onOpenRecap = { navController.navigate(RecapRoute(it)) { launchSingleTop = true } },
                    )
                }
                composable<SettingsRoute> {
                    SettingsEntry(
                        onBack = { navController.popBackStack() },
                        onOpenHealth = { navController.navigate(HealthRoute) },
                        onOpenScreenTime = { navController.navigate(ScreenTimeRoute) },
                        onOpenDebug = { navController.navigate(DebugRoute) },
                    )
                }
                composable<HealthRoute> { HealthEntry(onBack = { navController.popBackStack() }) }
                composable<ScreenTimeRoute> { ScreenTimeEntry(onBack = { navController.popBackStack() }) }
                composable<ReviewRoute> { ReviewEntry(onClose = { navController.popBackStack() }) }
                composable<RecapRoute> { RecapEntry(onBack = { navController.popBackStack() }) }
                composable<CampaignRoute> { CampaignEntry(onBack = { navController.popBackStack() }) }
                composable<VaultRoute> { VaultEntry(onBack = { navController.popBackStack() }) }
                if (DebugEntry.ENABLED) {
                    composable<DebugRoute> { DebugEntry.Screen(onBack = { navController.popBackStack() }) }
                }
            }
        }
        // Above the scaffold so reward sheets and toasts cover every tab and Quest detail.
        RewardHost(
            snackbarHostState = snackbarHostState,
            onOpenRecap = { navController.navigate(RecapRoute(it)) { launchSingleTop = true } },
        )
    }
}
