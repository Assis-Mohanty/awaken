package com.assismohanty.awaken.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.work.NotificationAccess
import com.assismohanty.awaken.engine.body.WeightUnit
import com.assismohanty.awaken.testing.NoDietReminders
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Settings, Diet: meal reminders and the weight unit are stored, and the unit survives a new screen (GD 20.4). */
@RunWith(AndroidJUnit4::class)
class DietSettingsViewModelTest {
    private val graph = TestGraph()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        graph.close()
    }

    private fun viewModel() = DietSettingsViewModel(
        graph.foodLibrary, graph.mealReminders, graph.uiPrefs, NoDietReminders,
        NotificationAccess(ApplicationProvider.getApplicationContext<Context>()),
    )

    @Test
    fun aReminderAndTheUnitAreStoredAndShownAgain() = runBlocking<Unit> {
        val vm = viewModel()
        withTimeout(5_000) { vm.uiState.first { it.slots.isNotEmpty() } }

        vm.onEvent(DietSettingsEvent.SetReminder("LUNCH", 13 * 60))
        vm.onEvent(DietSettingsEvent.SetUnit(WeightUnit.LB))
        vm.onEvent(DietSettingsEvent.SetHeightUnit(com.assismohanty.awaken.engine.body.HeightUnit.FT_IN))

        val state = withTimeout(5_000) { vm.uiState.first { it.reminders.isNotEmpty() && it.weightUnit == WeightUnit.LB && it.heightUnit == com.assismohanty.awaken.engine.body.HeightUnit.FT_IN } }
        assertThat(state.reminders).containsExactly("LUNCH", 13 * 60)
        assertThat(viewModel().uiState.let { withTimeout(5_000) { it.first { s -> s.weightUnit == WeightUnit.LB } } }.reminders).containsKey("LUNCH")
    }

    @Test
    fun aFifthReminderIsRefused() = runBlocking<Unit> {
        val vm = viewModel()
        withTimeout(5_000) { vm.uiState.first { it.slots.isNotEmpty() } }
        listOf("BREAKFAST", "LUNCH", "SNACK", "DINNER").forEach { vm.onEvent(DietSettingsEvent.SetReminder(it, 9 * 60)) }
        withTimeout(5_000) { vm.uiState.first { it.reminders.size == 4 } }

        vm.onEvent(DietSettingsEvent.SetReminder("custom:1", 10 * 60))

        assertThat(graph.mealReminders.reminders.first().keys).doesNotContain("custom:1")
    }
}
