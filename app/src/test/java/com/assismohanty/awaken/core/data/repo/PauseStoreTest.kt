package com.assismohanty.awaken.core.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.assismohanty.awaken.engine.PauseRules
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PauseStoreTest {
    private fun TestScope.store() = PauseStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(Files.createTempDirectory("pause").toFile(), "pause.preferences_pb") },
    )

    @Test
    fun theDefaultsMatchTheApprovedNumbers() = runTest {
        val settings = store().current()
        assertThat(settings.config.enabled).isFalse()
        assertThat(settings.config.targets).isEmpty()
        assertThat(settings.config.delayBaseSeconds).isEqualTo(3)
        assertThat(settings.config.progressive).isTrue()
        assertThat(settings.config.cooldownMinutes).isEqualTo(0)
        assertThat(settings.config.intentionPrompt).isFalse()
        assertThat(settings.config.hours).isNull()
        assertThat(settings.requireTap).isFalse()
    }

    @Test
    fun settingsRoundTrip() = runTest {
        val store = store()
        store.setEnabled(true)
        store.setTargets(setOf("a", "b"))
        store.setDelay(10)
        store.setProgressive(false)
        store.setCooldown(15)
        store.setIntention(true)
        store.setRequireTap(true)
        val settings = store.current()
        assertThat(settings.config.enabled).isTrue()
        assertThat(settings.config.targets).containsExactly("a", "b")
        assertThat(settings.config.delayBaseSeconds).isEqualTo(10)
        assertThat(settings.config.progressive).isFalse()
        assertThat(settings.config.cooldownMinutes).isEqualTo(15)
        assertThat(settings.config.intentionPrompt).isTrue()
        assertThat(settings.requireTap).isTrue()
    }

    @Test
    fun aValueOutsideTheChoicesFallsBackToTheDefault() = runTest {
        val store = store()
        store.setDelay(0)
        store.setCooldown(999)
        assertThat(store.current().config.delayBaseSeconds).isEqualTo(PauseRules.DEFAULT_DELAY_SECONDS)
        assertThat(store.current().config.cooldownMinutes).isEqualTo(PauseRules.DEFAULT_COOLDOWN_MINUTES)
    }

    @Test
    fun noMoreThanTwelveTargetsAreKept() = runTest {
        val store = store()
        store.setTargets((1..20).map { "app$it" }.toSet())
        assertThat(store.current().config.targets).hasSize(PauseRules.MAX_TARGETS)
    }

    @Test
    fun hoursOnlyApplyWhileTheirSwitchIsOnAndTheWindowIsRemembered() = runTest {
        val store = store()
        store.setHours(22, 2)
        store.setDays(setOf(DayOfWeek.MONDAY))
        assertThat(store.current().config.hours).isNull()
        store.setHoursOn(true)
        val hours = store.current().config.hours
        assertThat(hours?.start).isEqualTo(LocalTime.of(22, 0))
        assertThat(hours?.end).isEqualTo(LocalTime.of(2, 0))
        assertThat(hours?.days).containsExactly(DayOfWeek.MONDAY)
        store.setHoursOn(false)
        assertThat(store.current().startHour).isEqualTo(22)
    }
}
