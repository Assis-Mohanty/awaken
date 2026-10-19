package com.assismohanty.awaken

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.work.ACTION_OPEN_TODAY
import com.assismohanty.awaken.navigation.AppearanceViewModel
import com.assismohanty.awaken.navigation.AwakenNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appearance: AppearanceViewModel by viewModels()

    /** Bumped for every notification/widget tap that asks for Today, so the same request can repeat. */
    private var openTodayRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-only app: force light system-bar icons regardless of the device's light/dark setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // A rotation recreates the activity with the same intent; only a fresh launch counts as a request.
        if (savedInstanceState == null) handle(intent)
        setContent {
            val look by appearance.appearance.collectAsStateWithLifecycle()
            AwakenTheme(reduceMotion = look.reduceMotion, hapticsEnabled = look.hapticsEnabled, themeId = look.themeId) {
                AwakenNavHost(openTodayRequests = openTodayRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_TODAY) openTodayRequests++
    }
}
