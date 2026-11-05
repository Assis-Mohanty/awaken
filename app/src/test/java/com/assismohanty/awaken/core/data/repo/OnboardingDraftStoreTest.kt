package com.assismohanty.awaken.core.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.Pace
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OnboardingDraftStoreTest {

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(Files.createTempDirectory("onboarding-draft").toFile(), "draft.preferences_pb")
    }

    @Test
    fun nothingSavedMeansNoDraft() = runTest {
        assertThat(OnboardingDraftStore(dataStore()).load()).isNull()
    }

    @Test
    fun aDraftRoundTripsWithItsStepAndAnswers() = runTest {
        val store = OnboardingDraftStore(dataStore())
        val draft = OnboardingDraft(
            step = 7,
            answers = OnboardingAnswers(hunterId = "h1", name = "Sam", improve = listOf(ImproveGoal.SLEEP), pace = Pace.STEADY),
        )
        store.save(draft)
        assertThat(store.load()).isEqualTo(draft)
    }

    @Test
    fun theLatestSaveWins() = runTest {
        val store = OnboardingDraftStore(dataStore())
        store.save(OnboardingDraft(step = 2))
        store.save(OnboardingDraft(step = 3))
        assertThat(store.load()?.step).isEqualTo(3)
    }

    @Test
    fun clearRemovesTheDraft() = runTest {
        val store = OnboardingDraftStore(dataStore())
        store.save(OnboardingDraft(step = 4))
        store.clear()
        assertThat(store.load()).isNull()
    }

    @Test
    fun aCorruptDraftIsDropped() = runTest {
        val data = dataStore()
        data.edit { it[stringPreferencesKey("draft")] = "{ not json" }
        assertThat(OnboardingDraftStore(data).load()).isNull()
    }

    @Test
    fun aDraftFromAnotherSchemaVersionIsDropped() = runTest {
        val store = OnboardingDraftStore(dataStore())
        store.save(OnboardingDraft(step = 5, schemaVersion = OnboardingDraftStore.SCHEMA_VERSION + 1))
        assertThat(store.load()).isNull()
    }

    @Test
    fun unknownKeysFromANewerAppDoNotBreakLoading() = runTest {
        val data = dataStore()
        data.edit {
            it[stringPreferencesKey("draft")] =
                """{"step":2,"answers":{"name":"Sam","somethingNew":1},"schemaVersion":${OnboardingDraftStore.SCHEMA_VERSION}}"""
        }
        assertThat(OnboardingDraftStore(data).load()?.answers?.name).isEqualTo("Sam")
    }
}
