package com.assismohanty.awaken.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.assismohanty.awaken.debug.DebugEntry
import com.assismohanty.awaken.feature.gate.GatesScreen
import com.assismohanty.awaken.feature.status.StatusScreen
import com.assismohanty.awaken.feature.today.TodayScreen
import com.assismohanty.awaken.feature.vault.VaultScreen

@Composable
fun AwakenNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> {
                Box(modifier = Modifier.fillMaxSize()) {
                    TodayScreen()
                    if (DebugEntry.ENABLED) {
                        DebugEntry.LauncherButton(
                            onClick = { navController.navigate(DebugRoute) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
            composable<GatesRoute> { GatesScreen() }
            composable<StatusRoute> { StatusScreen() }
            composable<VaultRoute> { VaultScreen() }
            if (DebugEntry.ENABLED) {
                composable<DebugRoute> { DebugEntry.Screen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}
