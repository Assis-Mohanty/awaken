package com.assismohanty.awaken.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.assismohanty.awaken.core.data.di.NoticePrefs
import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Something the System tells the hunter once, produced by whichever rollover did the work (app or background worker).
 * UI-only state per ARCH §4: the game facts behind each notice live in Room; losing a notice loses only the message.
 */
@Serializable
sealed interface Notice {
    /** GD §7.3: shown once on the first open after a failed day. Dates are ISO strings. */
    @Serializable
    @SerialName("penalty_warning")
    data class PenaltyWarning(val gameDate: String) : Notice

    @Serializable
    @SerialName("shield_used")
    data class ShieldUsed(val gameDate: String) : Notice

    @Serializable
    @SerialName("recalibrated")
    data class Recalibrated(val changes: Map<Stat, DifficultyAdapter.Change>) : Notice

    @Serializable
    @SerialName("trial_failed")
    data class TrialFailed(val rank: Rank) : Notice
}

@Singleton
class NoticeStore @Inject constructor(@NoticePrefs private val store: DataStore<Preferences>) {

    val pending: Flow<List<Notice>> = store.data.map { decode(it[KEY]) }

    suspend fun add(notices: List<Notice>) {
        if (notices.isEmpty()) return
        store.edit { prefs -> prefs[KEY] = encode((decode(prefs[KEY]) + notices).distinct()) }
    }

    suspend fun remove(notice: Notice) {
        store.edit { prefs -> prefs[KEY] = encode(decode(prefs[KEY]) - notice) }
    }

    private fun encode(notices: List<Notice>): String = json.encodeToString(serializer, notices)

    private fun decode(text: String?): List<Notice> = if (text.isNullOrEmpty()) {
        emptyList()
    } else {
        try {
            json.decodeFromString(serializer, text)
        } catch (e: IllegalArgumentException) {
            // An unreadable notice list (e.g. from a newer build) is dropped; notices are never game state.
            Timber.w(e, "Dropping unreadable notices")
            emptyList()
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("pending_notices")
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(Notice.serializer())
    }
}
