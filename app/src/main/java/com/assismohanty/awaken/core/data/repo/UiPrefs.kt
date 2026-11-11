package com.assismohanty.awaken.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.assismohanty.awaken.core.audio.ChimeChoice
import com.assismohanty.awaken.core.data.di.UiPrefsStore
import com.assismohanty.awaken.engine.TimeBlocks
import com.assismohanty.awaken.engine.body.WeightUnit
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
    /** What a finished timer sounds like (phone feedback Part 4). */
    val chime: ChimeChoice = ChimeChoice.SOFT_BELL,
    /** Focus Lock (GD 8.2): the hunter read the disclosure and agreed. The Accessibility Service itself is switched on in Android settings. */
    val focusLock: Boolean = false,
    /** Packages the hunter chose to leave uncovered by Focus Lock (GD 8.2). */
    val allowedApps: Set<String> = emptySet(),
    /**
     * Clean or System (GD §14.9); null until first resolved. New installs resolve to CLEAN, installs upgrading from v1.0
     * to SYSTEM ([com.assismohanty.awaken.navigation.AppearanceViewModel]).
     */
    /** Time-of-day block boundaries (GD §14.5), whole hours. */
    val morningEndHour: Int = TimeBlocks.DEFAULT_MORNING_END,
    val dayEndHour: Int = TimeBlocks.DEFAULT_DAY_END,
    /** The newest ended Lockdown whose result the hunter has seen (GD §19.2); empty before any. */
    val lockdownSeenId: String = "",
    /** How Diet shows weights; stored weights are always kilograms. */
    val weightUnit: WeightUnit = WeightUnit.KG,
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
            focusLock = it[FOCUS_LOCK] ?: false,
            allowedApps = it[ALLOWED_APPS] ?: emptySet(),
            chime = it[CHIME]?.let { name -> ChimeChoice.entries.firstOrNull { choice -> choice.name == name } } ?: ChimeChoice.SOFT_BELL,
            morningEndHour = it[MORNING_END] ?: TimeBlocks.DEFAULT_MORNING_END,
            dayEndHour = it[DAY_END] ?: TimeBlocks.DEFAULT_DAY_END,
            lockdownSeenId = it[LOCKDOWN_SEEN].orEmpty(),
            weightUnit = it[WEIGHT_UNIT]?.let { name -> WeightUnit.entries.firstOrNull { unit -> unit.name == name } } ?: WeightUnit.KG,
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

    suspend fun setFocusLock(enabled: Boolean) {
        store.edit { it[FOCUS_LOCK] = enabled }
    }

    suspend fun setAllowedApps(packages: Set<String>) {
        store.edit { it[ALLOWED_APPS] = packages }
    }

    suspend fun setChime(choice: ChimeChoice) {
        store.edit { it[CHIME] = choice.name }
    }

    suspend fun setMorningEnd(hour: Int) {
        require(hour in TimeBlocks.MORNING_END_HOURS)
        store.edit { it[MORNING_END] = hour }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        store.edit { it[WEIGHT_UNIT] = unit.name }
    }

    suspend fun setLockdownSeen(id: String) {
        store.edit { it[LOCKDOWN_SEEN] = id }
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
        val CHIME = stringPreferencesKey("timer_chime")
        val FOCUS_LOCK = booleanPreferencesKey("focus_lock")
        val ALLOWED_APPS = stringSetPreferencesKey("focus_lock_allowed_apps")
        val MORNING_END = intPreferencesKey("morning_end_hour")
        val DAY_END = intPreferencesKey("day_end_hour")
        val LOCKDOWN_SEEN = stringPreferencesKey("lockdown_seen_id")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
    }
}
