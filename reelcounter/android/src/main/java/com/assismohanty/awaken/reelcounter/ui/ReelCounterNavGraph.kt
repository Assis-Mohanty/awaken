package com.assismohanty.awaken.reelcounter.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.assismohanty.awaken.reelcounter.inspector.InspectorHooks
import com.assismohanty.awaken.reelcounter.ui.today.TodayDebugRouteContent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ReelNavViewModel @Inject constructor(val inspector: InspectorHooks) : ViewModel()

/** The host entry point: AWAKEN (or the demo app) calls this inside its own theme. */
@Composable
fun ReelCounterNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val inspectorScreen = hiltViewModel<ReelNavViewModel>().inspector.screen
    NavHost(navController = navController, startDestination = TodayDebugRoute, modifier = modifier) {
        composable<TodayDebugRoute> {
            TodayDebugRouteContent(
                onOpenInspector = inspectorScreen?.let { { navController.navigate(InspectorRoute) } },
            )
        }
        if (inspectorScreen != null) {
            composable<InspectorRoute> { inspectorScreen { navController.popBackStack() } }
        }
    }
}
