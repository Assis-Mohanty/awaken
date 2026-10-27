package com.assismohanty.awaken.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.assismohanty.awaken.core.data.di.UiPrefsStore
import com.assismohanty.awaken.core.designsystem.lexicon.PresentationMode
import com.assismohanty.awaken.engine.TimeBlocks
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Appearance choice (DESIGN_SYSTEM §2.1): follow the device, or force one palette. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** The toggles the UI reads before anything else (DS §2.5 reduce motion, §2.6 haptics). Not game state, so DataStore. */
data class UiPrefs(
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    /** The reminders pre-prompt was answered (either way); we never ask twice (ARCH §3.5). */
    val notificationsPrompted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Clean or System (GD §14.9); null until first resolved. New installs resolve to CLEAN, installs upgrading from v1.0
     * to SYSTEM ([com.assismohanty.awaken.navigation.AppearanceViewModel]).
     */
    val presentationMode: PresentationMode? = null,
    /** Time-of-day block boundaries (GD §14.5), whole hours. */
    val morningEndHour: Int = TimeBlocks.DEFAULT_MORNING_END,
    val dayEndHour: Int = TimeBlocks.DEFAULT_DAY_END,
) {
    val blockBounds: TimeBlocks.Bounds get() = TimeBlocks.Bounds.safe(morningEndHour, dayEndHour)
}

@Singleton
class UiPrefsRepository @Inject constructor(@UiPrefsStore private val store: DataStore<Preferences>) {

    val prefs: Flow<UiPrefs> = store.data.map {
        UiPrefs(
            hapticsEnabled = it[HAPTICS] ?: true,
            reduceMotion = it[REDUCE_MOTION] ?: false,
            notificationsPrompted = it[NOTIFICATIONS_PROMPTED] ?: false,
            themeMode = it[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { mode -> mode.name == name } } ?: ThemeMode.SYSTEM,
            presentationMode = it[PRESENTATION_MODE]?.let { name -> PresentationMode.entries.firstOrNull { mode -> mode.name == name } },
            morningEndHour = it[MORNING_END] ?: TimeBlocks.DEFAULT_MORNING_END,
            dayEndHour = it[DAY_END] ?: TimeBlocks.DEFAULT_DAY_END,
        )
    }

    suspend fun setNotificationsPrompted() {
        store.edit { it[NOTIFICATIONS_PROMPTED] = true }
    }

    suspend fun setHaptics(enabled: Boolean) {
        store.edit { it[HAPTICS] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        store.edit { it[REDUCE_MOTION] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setPresentationMode(mode: PresentationMode) {
        store.edit { it[PRESENTATION_MODE] = mode.name }
    }

    /** Resolves the first-launch default once; never overrides a choice already made. */
    suspend fun initPresentationMode(mode: PresentationMode) {
        store.edit { if (it[PRESENTATION_MODE] == null) it[PRESENTATION_MODE] = mode.name }
    }

    suspend fun setMorningEnd(hour: Int) {
        require(hour in TimeBlocks.MORNING_END_HOURS)
        store.edit { it[MORNING_END] = hour }
    }

    suspend fun setDayEnd(hour: Int) {
        require(hour in TimeBlocks.DAY_END_HOURS)
        store.edit { it[DAY_END] = hour }
    }

    private companion object {
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val NOTIFICATIONS_PROMPTED = booleanPreferencesKey("notifications_prompted")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRESENTATION_MODE = stringPreferencesKey("presentation_mode")
        val MORNING_END = intPreferencesKey("morning_end_hour")
        val DAY_END = intPreferencesKey("day_end_hour")
    }
}
