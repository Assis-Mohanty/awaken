package com.assismohanty.awaken.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.assismohanty.awaken.core.data.di.OnboardingDraftPrefs
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Where the quiz flow stood: the step and every answer so far. Saved after each step (docs/onboarding/PLAN.md §3.2). */
@Serializable
data class OnboardingDraft(
    val step: Int = 0,
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val schemaVersion: Int = OnboardingDraftStore.SCHEMA_VERSION,
)

/**
 * Persists the onboarding draft so the flow resumes where it was if the app is killed. One JSON string in its own DataStore
 * file, which neither backup rule includes. A draft that cannot be read, or that an older version wrote, is dropped and the
 * flow starts again: it only ever holds answers the person can give again.
 */
@Singleton
class OnboardingDraftStore @Inject constructor(@OnboardingDraftPrefs private val store: DataStore<Preferences>) {

    suspend fun load(): OnboardingDraft? {
        val raw = store.data.first()[KEY] ?: return null
        return try {
            json.decodeFromString<OnboardingDraft>(raw).takeIf { it.schemaVersion == SCHEMA_VERSION }
        } catch (e: SerializationException) {
            Timber.w(e, "Dropping an unreadable onboarding draft")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Dropping an unreadable onboarding draft")
            null
        }
    }

    suspend fun save(draft: OnboardingDraft) {
        store.edit { it[KEY] = json.encodeToString(OnboardingDraft.serializer(), draft) }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    companion object {
        /** Bump when [OnboardingAnswers] changes in a way an old draft cannot be read as. */
        const val SCHEMA_VERSION = 1
        private val KEY = stringPreferencesKey("draft")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
