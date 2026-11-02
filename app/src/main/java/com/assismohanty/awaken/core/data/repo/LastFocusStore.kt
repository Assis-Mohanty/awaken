package com.assismohanty.awaken.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.assismohanty.awaken.core.data.di.LastFocusPrefs
import com.assismohanty.awaken.engine.FocusRules
import com.assismohanty.awaken.engine.PomodoroConfig
import com.assismohanty.awaken.engine.PomodoroRules
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What kind of focus session was started last, so the Quick Settings tile and the widget can start it again. */
enum class FocusKind { PRESET, CUSTOM, STOPWATCH, POMODORO }

/** The last session the hunter started (GD §16.3, ARCH §3.15). Only what is needed to start the same one again. */
data class LastFocus(
    val kind: FocusKind,
    val rank: GateRank,
    /** Custom length in minutes (also the focus round of a Pomodoro). */
    val minutes: Int,
    val stat: Stat,
    val subjectId: String?,
    val label: String,
    val pomodoro: PomodoroConfig,
)

/**
 * The last-used focus session and the Pomodoro settings, in a DataStore (UI preference: not exported, ENGINE_DECISIONS #48).
 * Written whenever a session starts. Every stored number is brought back into range when read, so an edited file can't break a start.
 */
@Singleton
class LastFocusStore @Inject constructor(@LastFocusPrefs private val store: DataStore<Preferences>) {

    private val data: Flow<Preferences> = store.data.catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }

    /** The last session, or null before anything was ever started. */
    val last: Flow<LastFocus?> = data.map { it.toLastFocus() }.distinctUntilChanged()

    /** The Pomodoro settings the setup starts from: the last used, else the defaults. */
    val pomodoroDefaults: Flow<PomodoroConfig> = data.map { it.toPomodoro() }.distinctUntilChanged()

    suspend fun current(): LastFocus? = last.first()

    suspend fun save(focus: LastFocus) {
        store.edit {
            it[KIND] = focus.kind.name
            it[RANK] = focus.rank.name
            it[MINUTES] = focus.minutes
            it[STAT] = focus.stat.name
            if (focus.subjectId == null) it.remove(SUBJECT) else it[SUBJECT] = focus.subjectId
            it[LABEL] = focus.label
            if (focus.kind == FocusKind.POMODORO) {
                it[POMO_FOCUS] = focus.pomodoro.focusMinutes
                it[POMO_SHORT] = focus.pomodoro.shortBreakMinutes
                it[POMO_LONG] = focus.pomodoro.longBreakMinutes
                it[POMO_ROUNDS] = focus.pomodoro.rounds
            }
        }
    }

    private fun Preferences.toPomodoro(): PomodoroConfig = PomodoroRules.safeConfig(
        focus = this[POMO_FOCUS] ?: PomodoroRules.DEFAULT_FOCUS,
        shortBreak = this[POMO_SHORT] ?: PomodoroRules.DEFAULT_SHORT_BREAK,
        longBreak = this[POMO_LONG] ?: PomodoroRules.DEFAULT_LONG_BREAK,
        rounds = this[POMO_ROUNDS] ?: PomodoroRules.DEFAULT_ROUNDS,
    )

    private fun Preferences.toLastFocus(): LastFocus? {
        val kind = this[KIND]?.let { name -> FocusKind.entries.firstOrNull { it.name == name } } ?: return null
        return LastFocus(
            kind = kind,
            rank = this[RANK]?.let { name -> GateRank.entries.firstOrNull { it.name == name } } ?: GateRank.D,
            minutes = FocusRules.clampCustomMinutes(this[MINUTES] ?: FocusRules.CUSTOM_DEFAULT_MINUTES),
            stat = this[STAT]?.let { name -> Stat.entries.firstOrNull { it.name == name } } ?: Stat.INT,
            subjectId = this[SUBJECT],
            label = this[LABEL].orEmpty(),
            pomodoro = toPomodoro(),
        )
    }

    private companion object {
        val KIND = stringPreferencesKey("kind")
        val RANK = stringPreferencesKey("rank")
        val MINUTES = intPreferencesKey("minutes")
        val STAT = stringPreferencesKey("stat")
        val SUBJECT = stringPreferencesKey("subject_id")
        val LABEL = stringPreferencesKey("label")
        val POMO_FOCUS = intPreferencesKey("pomodoro_focus")
        val POMO_SHORT = intPreferencesKey("pomodoro_short")
        val POMO_LONG = intPreferencesKey("pomodoro_long")
        val POMO_ROUNDS = intPreferencesKey("pomodoro_rounds")
    }
}
