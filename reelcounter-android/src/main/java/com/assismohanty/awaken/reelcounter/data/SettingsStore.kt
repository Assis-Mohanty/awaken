package com.assismohanty.awaken.reelcounter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.di.ReelPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Where the counter bubble sits across the screen. [CENTER] (top middle) is the default. */
enum class BubbleEdge { START, CENTER, END }

@Singleton
class SettingsStore @Inject constructor(@ReelPrefs private val store: DataStore<Preferences>) {

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }


    val limits: Flow<Map<ShortVideoApp, Int>> = data.map { prefs ->
        ShortVideoApp.entries.mapNotNull { app -> prefs[limitKey(app)]?.let { app to it } }.toMap()
    }.distinctUntilChanged()

    val bubbleEnabled: Flow<Boolean> = data.map { it[BUBBLE_ENABLED] ?: true }.distinctUntilChanged()

    val bubbleEdge: Flow<BubbleEdge> = data.map {
        BubbleEdge.entries.firstOrNull { edge -> edge.name == it[BUBBLE_EDGE] } ?: BubbleEdge.CENTER
    }.distinctUntilChanged()

    val bubbleYFraction: Flow<Float> = data.map { it[BUBBLE_Y_FRACTION] ?: DEFAULT_BUBBLE_Y }.distinctUntilChanged()

    /** The hunter read the disclosure and switched Reel Counter on in AWAKEN. Off by default. */
    val enabled: Flow<Boolean> = data.map { it[ENABLED] ?: false }.distinctUntilChanged()

    val lastHeartbeatMillis: Flow<Long?> = data.map { it[LAST_HEARTBEAT] }.distinctUntilChanged()

    val inspectorEnabled: Flow<Boolean> = data.map { it[INSPECTOR_ENABLED] ?: false }.distinctUntilChanged()


    /** Null removes the limit (off). */
    suspend fun setLimit(app: ShortVideoApp, limit: Int?) = store.edit {
        if (limit == null) it.remove(limitKey(app)) else it[limitKey(app)] = limit
    }

    suspend fun setBubbleEnabled(enabled: Boolean) = store.edit { it[BUBBLE_ENABLED] = enabled }

    suspend fun setBubblePosition(edge: BubbleEdge, yFraction: Float) = store.edit {
        it[BUBBLE_EDGE] = edge.name
        it[BUBBLE_Y_FRACTION] = yFraction.coerceIn(0f, 1f)
    }

    suspend fun setEnabled(on: Boolean) = store.edit { it[ENABLED] = on }

    suspend fun setLastHeartbeat(millis: Long) = store.edit { it[LAST_HEARTBEAT] = millis }

    suspend fun setInspectorEnabled(enabled: Boolean) = store.edit { it[INSPECTOR_ENABLED] = enabled }

    companion object {
        /** Top of the usable band: the bubble starts just under the status bar. */
        const val DEFAULT_BUBBLE_Y = 0f

        private val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        private val BUBBLE_EDGE = stringPreferencesKey("bubble_anchor")
        private val BUBBLE_Y_FRACTION = floatPreferencesKey("bubble_y_fraction")
        private val ENABLED = booleanPreferencesKey("reel_enabled")
        private val LAST_HEARTBEAT = longPreferencesKey("last_heartbeat_ms")
        private val INSPECTOR_ENABLED = booleanPreferencesKey("inspector_enabled")

        private fun limitKey(app: ShortVideoApp) = intPreferencesKey("limit_${app.name}")
    }
}
