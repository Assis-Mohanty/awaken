package com.assismohanty.awaken.feature.protect

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.repo.PauseStore
import com.assismohanty.awaken.core.lock.AlwaysAllowed
import com.assismohanty.awaken.core.lock.FocusLockSystemAccess
import com.assismohanty.awaken.core.protect.ProtectSystemAccess
import com.assismohanty.awaken.engine.PauseRules
import com.assismohanty.awaken.testing.FakeUsageGateway
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.DayOfWeek
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

/** Pause settings and the app picker through their ViewModels (GD §19.1). */
@RunWith(AndroidJUnit4::class)
class PauseViewModelsTest {
    private val graph = TestGraph()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val store = PauseStore(PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "pause-${UUID.randomUUID()}.preferences_pb") })

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
        graph.close()
    }

    private fun settingsVm() = PauseSettingsViewModel(store, ProtectSystemAccess(context, graph.usage, FocusLockSystemAccess(context)))

    private fun appsVm() = PauseAppsViewModel(store, graph.usage, AlwaysAllowed(context, graph.uptime))

    @Test
    fun theSwitchAndTheChoicesAreSaved() = runBlocking<Unit> {
        val vm = settingsVm()
        vm.onEvent(PauseSettingsEvent.SetEnabled(true))
        vm.onEvent(PauseSettingsEvent.SetDelay(5))
        vm.onEvent(PauseSettingsEvent.SetCooldown(10))
        val config = withTimeout(5_000) { vm.uiState.first { it.settings.config.enabled && it.settings.config.cooldownMinutes == 10 } }.settings.config
        assertThat(config.delayBaseSeconds).isEqualTo(5)
    }

    @Test
    fun aDelayThatIsNotOneOfTheChoicesIsIgnored() = runBlocking<Unit> {
        val vm = settingsVm()
        vm.onEvent(PauseSettingsEvent.SetDelay(7))
        assertThat(store.current().config.delayBaseSeconds).isEqualTo(PauseRules.DEFAULT_DELAY_SECONDS)
    }

    @Test
    fun theLastSelectedDayCannotBeSwitchedOff() = runBlocking<Unit> {
        val vm = settingsVm()
        DayOfWeek.entries.filter { it != DayOfWeek.FRIDAY }.forEach { vm.onEvent(PauseSettingsEvent.ToggleDay(it)) }
        assertThat(store.current().days).containsExactly(DayOfWeek.FRIDAY)
        vm.onEvent(PauseSettingsEvent.ToggleDay(DayOfWeek.FRIDAY))
        assertThat(store.current().days).containsExactly(DayOfWeek.FRIDAY)
    }

    @Test
    fun withoutUsageAccessThePermissionsAreNotReadyAndPauseIsNotWatching() = runBlocking<Unit> {
        graph.usage.access = false
        val vm = settingsVm()
        vm.onEvent(PauseSettingsEvent.SetEnabled(true))
        store.setTargets(setOf(FakeUsageGateway.INSTAGRAM))
        val state = withTimeout(5_000) { vm.uiState.first { it.settings.config.targets.isNotEmpty() } }
        assertThat(state.usageAccess).isFalse()
        assertThat(state.watching).isFalse()
    }

    @Test
    fun tickingAnAppChoosesItAndTickingAgainRemovesIt() = runBlocking<Unit> {
        val vm = appsVm()
        val loaded = withTimeout(5_000) { vm.uiState.first { !it.loading } }
        // Instagram is suggested, so it is offered first; the other launcher apps follow.
        assertThat(loaded.suggested.map { it.packageName }).containsExactly(FakeUsageGateway.INSTAGRAM)
        vm.onEvent(PauseAppsEvent.Toggle(FakeUsageGateway.INSTAGRAM))
        assertThat(store.current().config.targets).containsExactly(FakeUsageGateway.INSTAGRAM)
        vm.onEvent(PauseAppsEvent.Toggle(FakeUsageGateway.INSTAGRAM))
        assertThat(store.current().config.targets).isEmpty()
    }

    @Test
    fun anAppThatCanNeverBeCoveredCannotBePicked() = runBlocking<Unit> {
        graph.usage.apps = listOf(com.assismohanty.awaken.core.data.usage.InstalledApp("com.android.dialer", "Phone", com.assismohanty.awaken.engine.AppKind.OTHER))
        val vm = appsVm()
        val loaded = withTimeout(5_000) { vm.uiState.first { !it.loading } }
        assertThat(loaded.others.single().locked).isTrue()
        vm.onEvent(PauseAppsEvent.Toggle("com.android.dialer"))
        assertThat(store.current().config.targets).isEmpty()
    }

    @Test
    fun theThirteenthAppIsRefused() = runBlocking<Unit> {
        store.setTargets((1..PauseRules.MAX_TARGETS).map { "app$it" }.toSet())
        val vm = appsVm()
        val loaded = withTimeout(5_000) { vm.uiState.first { !it.loading } }
        assertThat(loaded.full).isTrue()
        vm.onEvent(PauseAppsEvent.Toggle(FakeUsageGateway.INSTAGRAM))
        assertThat(store.current().config.targets).hasSize(PauseRules.MAX_TARGETS)
        assertThat(store.current().config.targets).doesNotContain(FakeUsageGateway.INSTAGRAM)
    }
}
