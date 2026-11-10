package com.assismohanty.awaken.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.assismohanty.awaken.core.data.di.PausePrefs
import com.assismohanty.awaken.engine.PauseConfig
import com.assismohanty.awaken.engine.PauseHours
import com.assismohanty.awaken.engine.PauseRules
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Pause settings as the hunter set them: the engine's [config] plus the one switch the engine does not need. */
data class PauseSettings(
    val config: PauseConfig = PauseConfig(enabled = false),
    /** PZ11: wait for a tap instead of opening the app when the countdown ends. */
    val requireTap: Boolean = false,
    /** The hours switch, kept apart from [PauseConfig.hours] so the window survives switching it off and on. */
    val hoursOn: Boolean = false,
    val startHour: Int = DEFAULT_START_HOUR,
    val endHour: Int = DEFAULT_END_HOUR,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
) {
    companion object {
        const val DEFAULT_START_HOUR = 9
        const val DEFAULT_END_HOUR = 18
    }
}

/**
 * Pause settings in a DataStore (UI preference: not exported, ENGINE_DECISIONS #48). Every stored number is brought back
 * into the allowed choices when read, so an edited file can't give a zero-second pause or a 40-app list.
 */
@Singleton
class PauseStore @Inject constructor(@PausePrefs private val store: DataStore<Preferences>) {

    val settings: Flow<PauseSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): PauseSettings = settings.first()

    suspend fun setEnabled(on: Boolean) = store.edit { it[ENABLED] = on }

    suspend fun setTargets(packages: Set<String>) = store.edit { it[TARGETS] = packages.take(PauseRules.MAX_TARGETS).toSet() }

    suspend fun setDelay(seconds: Int) = store.edit { it[DELAY] = seconds }

    suspend fun setProgressive(on: Boolean) = store.edit { it[PROGRESSIVE] = on }

    suspend fun setCooldown(minutes: Int) = store.edit { it[COOLDOWN] = minutes }

    suspend fun setIntention(on: Boolean) = store.edit { it[INTENTION] = on }

    suspend fun setRequireTap(on: Boolean) = store.edit { it[REQUIRE_TAP] = on }

    suspend fun setHoursOn(on: Boolean) = store.edit { it[HOURS_ON] = on }

    suspend fun setHours(startHour: Int, endHour: Int) = store.edit {
        it[HOURS_START] = startHour.coerceIn(0, 23)
        it[HOURS_END] = endHour.coerceIn(0, 23)
    }

    suspend fun setDays(days: Set<DayOfWeek>) = store.edit { it[DAYS] = days.map { day -> day.value.toString() }.toSet() }

    private fun Preferences.toSettings(): PauseSettings {
        val days = this[DAYS]?.mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 1..7 }?.let(DayOfWeek::of) }?.toSet()
            ?: DayOfWeek.entries.toSet()
        val start = (this[HOURS_START] ?: PauseSettings.DEFAULT_START_HOUR).coerceIn(0, 23)
        val end = (this[HOURS_END] ?: PauseSettings.DEFAULT_END_HOUR).coerceIn(0, 23)
        val hoursOn = this[HOURS_ON] ?: false
        val config = PauseConfig(
            enabled = this[ENABLED] ?: false,
            targets = (this[TARGETS] ?: emptySet()).take(PauseRules.MAX_TARGETS).toSet(),
            delayBaseSeconds = (this[DELAY] ?: PauseRules.DEFAULT_DELAY_SECONDS).takeIf { it in PauseRules.DELAY_CHOICES } ?: PauseRules.DEFAULT_DELAY_SECONDS,
            progressive = this[PROGRESSIVE] ?: true,
            cooldownMinutes = (this[COOLDOWN] ?: PauseRules.DEFAULT_COOLDOWN_MINUTES).takeIf { it in PauseRules.COOLDOWN_CHOICES } ?: PauseRules.DEFAULT_COOLDOWN_MINUTES,
            intentionPrompt = this[INTENTION] ?: false,
            hours = if (hoursOn) PauseHours(LocalTime.of(start, 0), LocalTime.of(end, 0), days) else null,
        )
        return PauseSettings(config, this[REQUIRE_TAP] ?: false, hoursOn, start, end, days)
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val TARGETS = stringSetPreferencesKey("targets")
        val DELAY = intPreferencesKey("delay_seconds")
        val PROGRESSIVE = booleanPreferencesKey("progressive")
        val COOLDOWN = intPreferencesKey("cooldown_minutes")
        val INTENTION = booleanPreferencesKey("intention")
        val REQUIRE_TAP = booleanPreferencesKey("require_tap")
        val HOURS_ON = booleanPreferencesKey("hours_on")
        val HOURS_START = intPreferencesKey("hours_start")
        val HOURS_END = intPreferencesKey("hours_end")
        val DAYS = stringSetPreferencesKey("days")
    }
}
