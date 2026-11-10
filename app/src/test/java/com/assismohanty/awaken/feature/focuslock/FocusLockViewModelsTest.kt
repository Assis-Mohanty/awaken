package com.assismohanty.awaken.feature.focuslock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.lock.AlwaysAllowed
import com.assismohanty.awaken.core.lock.FocusLockSystemAccess
import com.assismohanty.awaken.testing.FakeUsageGateway
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
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

/** Focus Lock setup and Allowed apps through their ViewModels (GD 8.2). */
@RunWith(AndroidJUnit4::class)
class FocusLockViewModelsTest {
    private val graph = TestGraph()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        graph.close()
    }

    @Test
    fun agreeingSwitchesFocusLockOnAndMovesToTheAndroidStep() = runBlocking<Unit> {
        val vm = FocusLockSetupViewModel(graph.uiPrefs, FocusLockSystemAccess(context, graph.usage))
        assertThat(vm.uiState.value.step).isEqualTo(SetupStep.EXPLAIN)
        assertThat(graph.uiPrefs.prefs.first().focusLock).isFalse()

        vm.onEvent(FocusLockSetupEvent.Agree)

        withTimeout(5_000) { vm.uiState.first { it.step == SetupStep.ENABLE } }
        assertThat(graph.uiPrefs.prefs.first().focusLock).isTrue()
        vm.onEvent(FocusLockSetupEvent.Next)
        assertThat(vm.uiState.value.step).isEqualTo(SetupStep.BATTERY)
        vm.onEvent(FocusLockSetupEvent.Finish)
        assertThat(vm.uiState.value.step).isEqualTo(SetupStep.DONE)
    }

    @Test
    fun notNowLeavesFocusLockOffAndClosesTheSetup() = runBlocking<Unit> {
        val vm = FocusLockSetupViewModel(graph.uiPrefs, FocusLockSystemAccess(context, graph.usage))

        vm.onEvent(FocusLockSetupEvent.NotNow)

        assertThat(vm.uiState.value.finished).isTrue()
        assertThat(graph.uiPrefs.prefs.first().focusLock).isFalse()
    }

    @Test
    fun allowedAppsListsLauncherAppsAndTogglingSavesThem() = runBlocking<Unit> {
        val vm = AllowedAppsViewModel(graph.uiPrefs, graph.usage, AlwaysAllowed(context, graph.uptime))
        val loaded = withTimeout(5_000) { vm.uiState.first { !it.loading } }
        assertThat(loaded.rows.map { it.packageName }).contains(FakeUsageGateway.INSTAGRAM)
        assertThat(loaded.rows.first { it.packageName == FakeUsageGateway.INSTAGRAM }.allowed).isFalse()

        vm.onEvent(AllowedAppsEvent.Toggle(FakeUsageGateway.INSTAGRAM))
        val ticked = withTimeout(5_000) { vm.uiState.first { s -> s.rows.any { it.packageName == FakeUsageGateway.INSTAGRAM && it.allowed } } }
        assertThat(ticked.rows.first { it.packageName == FakeUsageGateway.INSTAGRAM }.locked).isFalse()
        assertThat(graph.uiPrefs.prefs.first().allowedApps).containsExactly(FakeUsageGateway.INSTAGRAM)

        vm.onEvent(AllowedAppsEvent.Toggle(FakeUsageGateway.INSTAGRAM))
        withTimeout(5_000) { vm.uiState.first { s -> s.rows.any { it.packageName == FakeUsageGateway.INSTAGRAM && !it.allowed } } }
        assertThat(graph.uiPrefs.prefs.first().allowedApps).isEmpty()
    }

    @Test
    fun searchFiltersTheRows() = runBlocking<Unit> {
        val vm = AllowedAppsViewModel(graph.uiPrefs, graph.usage, AlwaysAllowed(context, graph.uptime))
        withTimeout(5_000) { vm.uiState.first { !it.loading } }

        vm.onEvent(AllowedAppsEvent.Query("insta"))

        val filtered = withTimeout(5_000) { vm.uiState.first { it.query == "insta" } }
        assertThat(filtered.rows.map { it.packageName }).containsExactly(FakeUsageGateway.INSTAGRAM)
    }
}
