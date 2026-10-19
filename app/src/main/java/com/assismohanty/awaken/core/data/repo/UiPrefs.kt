package com.assismohanty.awaken.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.assismohanty.awaken.core.data.di.UiPrefsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The toggles the UI reads before anything else (DS §2.5 reduce motion, §2.6 haptics). Not game state, so DataStore. */
data class UiPrefs(val hapticsEnabled: Boolean = true, val reduceMotion: Boolean = false)

@Singleton
class UiPrefsRepository @Inject constructor(@UiPrefsStore private val store: DataStore<Preferences>) {

    val prefs: Flow<UiPrefs> = store.data.map { UiPrefs(hapticsEnabled = it[HAPTICS] ?: true, reduceMotion = it[REDUCE_MOTION] ?: false) }

    suspend fun setHaptics(enabled: Boolean) {
        store.edit { it[HAPTICS] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        store.edit { it[REDUCE_MOTION] = enabled }
    }

    private companion object {
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    }
}
