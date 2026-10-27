package com.assismohanty.awaken

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.core.data.repo.ThemeMode
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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // A rotation recreates the activity with the same intent; only a fresh launch counts as a request.
        if (savedInstanceState == null) handle(intent)
        setContent {
            val look by appearance.appearance.collectAsStateWithLifecycle()
            val dark = when (look.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            // System-bar icons follow the resolved palette, not the device setting.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            LaunchedEffect(look.themeMode) { applyWindowNightMode(look.themeMode) }
            AwakenTheme(
                darkTheme = dark,
                reduceMotion = look.reduceMotion,
                hapticsEnabled = look.hapticsEnabled,
                themeId = look.themeId,
                presentation = look.presentationMode,
            ) {
                AwakenNavHost(openTodayRequests = openTodayRequests)
            }
        }
    }

    /**
     * Tells the OS which night mode the app uses (API 31+), so the pre-Compose window background (`values-night`) and
     * the next launch's first frame match a forced Dark/Light choice. Older versions follow the device until Compose draws.
     */
    private fun applyWindowNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = getSystemService(UiModeManager::class.java) ?: return
        val wanted = when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        }
        // Persisted per app by the OS; re-applying the same value doesn't change the configuration, so no recreate loop.
        uiModeManager.setApplicationNightMode(wanted)
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
