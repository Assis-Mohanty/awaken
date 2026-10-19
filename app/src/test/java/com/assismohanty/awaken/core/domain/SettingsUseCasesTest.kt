package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 10b: Settings edits change the profile only and obey the engine rules (DS §4.7, ENGINE_DECISIONS #46–#47). */
@RunWith(AndroidJUnit4::class)
class SettingsUseCasesTest {
    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private suspend fun profile() = graph.db.hunterDao().getProfile()!!

    @Test
    fun theNameIsTrimmedAndMustBeThreeToTwentyCharacters() = runTest {
        graph.seedHunter()

        assertThat(graph.updateSettings.rename("  Kael  ")).isEqualTo(SettingsResult.Saved)
        assertThat(profile().name).isEqualTo("Kael")
        assertThat(graph.updateSettings.rename("Al")).isEqualTo(SettingsResult.InvalidName)
        assertThat(graph.updateSettings.rename("x".repeat(21))).isEqualTo(SettingsResult.InvalidName)
        assertThat(profile().name).isEqualTo("Kael")
    }

    @Test
    fun focusNeedsTwoDistinctStats() = runTest {
        graph.seedHunter()

        assertThat(graph.updateSettings.setFocusStats(Stat.WIL, Stat.WIL)).isEqualTo(SettingsResult.InvalidFocus)
        assertThat(graph.updateSettings.setFocusStats(Stat.WIL, Stat.CHA)).isEqualTo(SettingsResult.Saved)
        assertThat(profile().focusStats).containsExactly(Stat.WIL, Stat.CHA).inOrder()
    }

    @Test
    fun theDayStartIsOnlyOfferedFromMidnightToSix() = runTest {
        graph.seedHunter()

        assertThat(graph.updateSettings.setDayStartHour(7)).isEqualTo(SettingsResult.DayStartBlocked)
        assertThat(graph.updateSettings.setDayStartHour(2)).isEqualTo(SettingsResult.Saved)
        assertThat(profile().dayStartHour).isEqualTo(2)
    }

    @Test
    fun theDayStartCannotMoveTodayBackIntoAFinishedDay() = runTest {
        graph.seedHunter() // day start 4
        graph.clock.instant = Instant.parse("2026-10-19T05:30:00Z")

        assertThat(graph.updateSettings.setDayStartHour(6)).isEqualTo(SettingsResult.DayStartBlocked)
        assertThat(profile().dayStartHour).isEqualTo(4)

        graph.clock.instant = Instant.parse("2026-10-19T06:00:00Z")
        assertThat(graph.updateSettings.setDayStartHour(6)).isEqualTo(SettingsResult.Saved)
        assertThat(profile().dayStartHour).isEqualTo(6)
    }

    @Test
    fun theReminderCanBeSetAndCleared() = runTest {
        graph.seedHunter()

        graph.updateSettings.setReminder(LocalTime.of(7, 30))
        assertThat(profile().reminderTime).isEqualTo("07:30")
        graph.updateSettings.setReminder(null)
        assertThat(profile().reminderTime).isNull()
    }

    @Test
    fun onlyKnownEquipmentIsStored() = runTest {
        graph.seedHunter()

        graph.updateSettings.setEquipment(setOf("dumbbells", "jump_rope", "ghost_gear"))

        assertThat(profile().ownedEquipment).containsExactly("dumbbells", "jump_rope")
    }

    @Test
    fun injuryTagsFeedTheDirectiveFilterOnlyWhileTheModeIsOn() = runTest {
        graph.seedHunter()
        val today = graph.today()

        graph.updateSettings.setInjury(true, setOf("upper"))
        assertThat(graph.reader.hunterContext(profile(), today).injuryTags).containsExactly("upper")

        graph.updateSettings.setInjury(false, setOf("upper"))
        assertThat(graph.reader.hunterContext(profile(), today).injuryTags).isEmpty()
        assertThat(profile().injuryTags).containsExactly("upper")

        graph.updateSettings.setInjury(true, emptySet())
        assertThat(profile().injuryTags).containsExactly("impact", "legs")
    }

    @Test
    fun theSettingsModelOffersTheWholeEquipmentVocabulary() = runTest {
        graph.seedHunter()
        graph.updateSettings.setReminder(LocalTime.of(21, 0))

        val model = graph.observeSettings().filterNotNull().first()

        assertThat(model.equipmentOptions).containsAtLeast("dumbbells", "kettlebell", "pullup_bar", "resistance_band", "jump_rope", "gym_access", "bicycle")
        assertThat(model.reminder).isEqualTo(LocalTime.of(21, 0))
        assertThat(model.dayStartHour).isEqualTo(4)
    }

    @Test
    fun noHunterMeansNothingToSave() = runTest {
        assertThat(graph.updateSettings.rename("Kael")).isEqualTo(SettingsResult.NoHunter)
        assertThat(graph.updateSettings.setDayStartHour(2)).isEqualTo(SettingsResult.NoHunter)
    }
}
