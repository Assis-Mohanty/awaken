package com.assismohanty.awaken.feature.protect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.designsystem.components.AwakenSwitch
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.DistractingApps
import com.assismohanty.awaken.core.domain.LockdownController
import com.assismohanty.awaken.core.domain.LockdownEnvironment
import com.assismohanty.awaken.core.domain.StartLockdownResult
import com.assismohanty.awaken.core.domain.StartLockdownUseCase
import com.assismohanty.awaken.core.lock.FocusLockSettingsLauncher
import com.assismohanty.awaken.core.protect.ProtectController
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.FocusLockRules
import com.assismohanty.awaken.engine.LockdownRules
import com.assismohanty.awaken.feature.focuslock.StatusRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LockdownStep { SET_UP, CONFIRM }

/** Why an app in the list can't be ticked. */
enum class LockdownAppReason { ALWAYS_WORKS, NOT_ALLOWED }

data class LockdownAppOption(val packageName: String, val label: String, val chosen: Boolean, val reason: LockdownAppReason?)

/** What went wrong when starting, in words the screen can show. */
sealed interface LockdownMessage {
    data class Wait(val minutes: Int) : LockdownMessage
    data class NotAllowed(val label: String) : LockdownMessage
    data object NeedsPermissions : LockdownMessage
    data object TooLate : LockdownMessage
    data object Busy : LockdownMessage
}

data class LockdownSetupUiState(
    val step: LockdownStep = LockdownStep.SET_UP,
    val minutes: Int = LockdownRules.DEFAULT_MINUTES,
    val query: String = "",
    val loading: Boolean = true,
    val apps: List<LockdownAppOption> = emptyList(),
    val chosenCount: Int = 0,
    val usageAccess: Boolean = false,
    val appearOnTop: Boolean = false,
    val waitMinutes: Int = 0,
    val message: LockdownMessage? = null,
    val starting: Boolean = false,
    val finished: Boolean = false,
) {
    val full: Boolean get() = chosenCount >= LockdownRules.MAX_APPS
    val permissionsReady: Boolean get() = usageAccess && appearOnTop
    val canContinue: Boolean get() = permissionsReady && waitMinutes == 0
}

sealed interface LockdownSetupEvent {
    data object Resumed : LockdownSetupEvent
    data object Shorter : LockdownSetupEvent
    data object Longer : LockdownSetupEvent
    data class Query(val text: String) : LockdownSetupEvent
    data class Toggle(val packageName: String) : LockdownSetupEvent
    data object Continue : LockdownSetupEvent
    data object Back : LockdownSetupEvent
    data object Start : LockdownSetupEvent
}

@HiltViewModel
class LockdownSetupViewModel @Inject constructor(
    private val start: StartLockdownUseCase,
    private val controller: LockdownController,
    private val protect: ProtectController,
    private val usage: UsageGateway,
    private val distractingApps: DistractingApps,
    private val env: LockdownEnvironment,
) : ViewModel() {
    private data class Draft(
        val step: LockdownStep = LockdownStep.SET_UP,
        val minutes: Int = LockdownRules.DEFAULT_MINUTES,
        val query: String = "",
        val chosen: Set<String> = emptySet(),
        val message: LockdownMessage? = null,
        val starting: Boolean = false,
        val finished: Boolean = false,
    )

    private data class Checks(val usage: Boolean, val overlay: Boolean, val waitMinutes: Int)

    private val draft = MutableStateFlow(Draft())
    private val installed = MutableStateFlow<List<com.assismohanty.awaken.core.data.usage.InstalledApp>?>(null)
    private val checks = MutableStateFlow(Checks(env.usageAccess(), env.canAppearOnTop(), 0))
    private val distracting = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<LockdownSetupUiState> = combine(draft, installed, checks, distracting) { d, apps, c, bad ->
        val always = env.alwaysAllowed()
        val options = apps.orEmpty()
            .filter { d.query.isBlank() || it.label.contains(d.query.trim(), ignoreCase = true) }
            .map { app ->
                val reason = when {
                    FocusLockRules.neverBlocked(app.packageName, always) -> LockdownAppReason.ALWAYS_WORKS
                    !LockdownRules.canAllow(if (app.packageName in bad) AppCategory.DISTRACTING else AppCategory.NEUTRAL, app.kind) -> LockdownAppReason.NOT_ALLOWED
                    else -> null
                }
                LockdownAppOption(app.packageName, app.label, app.packageName in d.chosen, reason)
            }
        LockdownSetupUiState(
            d.step, d.minutes, d.query, apps == null, options, d.chosen.size, c.usage, c.overlay, c.waitMinutes, d.message, d.starting, d.finished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockdownSetupUiState())

    init {
        viewModelScope.launch {
            installed.value = usage.launcherApps()
            distracting.value = distractingApps.current()
        }
        refreshChecks()
    }

    fun onEvent(event: LockdownSetupEvent) {
        when (event) {
            LockdownSetupEvent.Resumed -> refreshChecks()
            LockdownSetupEvent.Shorter -> draft.update { it.copy(minutes = (it.minutes - LockdownRules.STEP_MINUTES).coerceAtLeast(LockdownRules.MIN_MINUTES)) }
            LockdownSetupEvent.Longer -> draft.update { it.copy(minutes = (it.minutes + LockdownRules.STEP_MINUTES).coerceAtMost(LockdownRules.MAX_MINUTES)) }
            is LockdownSetupEvent.Query -> draft.update { it.copy(query = event.text) }
            is LockdownSetupEvent.Toggle -> toggle(event.packageName)
            LockdownSetupEvent.Continue -> if (uiState.value.canContinue) draft.update { it.copy(step = LockdownStep.CONFIRM, message = null) }
            LockdownSetupEvent.Back -> draft.update { it.copy(step = LockdownStep.SET_UP) }
            LockdownSetupEvent.Start -> startNow()
        }
    }

    private fun toggle(packageName: String) {
        val option = uiState.value.apps.firstOrNull { it.packageName == packageName } ?: return
        if (option.reason != null) return
        draft.update {
            when {
                packageName in it.chosen -> it.copy(chosen = it.chosen - packageName)
                it.chosen.size < LockdownRules.MAX_APPS -> it.copy(chosen = it.chosen + packageName)
                else -> it
            }
        }
    }

    private fun refreshChecks() {
        viewModelScope.launch {
            checks.value = Checks(env.usageAccess(), env.canAppearOnTop(), controller.cooldownLeft().let { (it.toMinutes() + if (it.toMillis() % MS_PER_MINUTE > 0) 1 else 0).toInt() })
        }
    }

    private fun startNow() {
        val d = draft.value
        if (d.starting) return
        draft.update { it.copy(starting = true, message = null) }
        viewModelScope.launch {
            when (val result = start(d.minutes, d.chosen)) {
                is StartLockdownResult.Started -> {
                    protect.refresh()
                    draft.update { it.copy(starting = false, finished = true) }
                }
                else -> draft.update { it.copy(starting = false, step = LockdownStep.SET_UP, message = result.toMessage()) }
            }
        }
    }

    private fun StartLockdownResult.toMessage(): LockdownMessage? = when (this) {
        is StartLockdownResult.InCooldown -> LockdownMessage.Wait(((left.toMinutes()) + 1).toInt())
        is StartLockdownResult.AppNotAllowed -> LockdownMessage.NotAllowed(installed.value?.firstOrNull { it.packageName == packageName }?.label ?: packageName)
        StartLockdownResult.NeedsPermissions -> LockdownMessage.NeedsPermissions
        StartLockdownResult.TooLate -> LockdownMessage.TooLate
        StartLockdownResult.AlreadyRunning -> LockdownMessage.Busy
        else -> null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MS_PER_MINUTE = 60_000L
    }
}

@Composable
fun LockdownSetupRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: LockdownSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(LockdownSetupEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    LockdownSetupScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onFixUsage = { FocusLockSettingsLauncher.openUsageAccess(context) },
        onFixOverlay = { FocusLockSettingsLauncher.openOverlayPermission(context) },
        modifier = modifier,
    )
}

/** Lockdown setup (GD §19.2): how long, which (up to 3) apps stay usable, then a plain statement that there is no way out. */
@Composable
fun LockdownSetupScreen(
    state: LockdownSetupUiState,
    onEvent: (LockdownSetupEvent) -> Unit,
    onBack: () -> Unit,
    onFixUsage: () -> Unit,
    onFixOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenBackground(modifier = modifier) {
        when (state.step) {
            LockdownStep.SET_UP -> SetUp(state, onEvent, onBack, onFixUsage, onFixOverlay)
            LockdownStep.CONFIRM -> Confirm(state, onEvent)
        }
    }
}

@Composable
private fun SetUp(state: LockdownSetupUiState, onEvent: (LockdownSetupEvent) -> Unit, onBack: () -> Unit, onFixUsage: () -> Unit, onFixOverlay: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    LargeTitleScaffold(title = lexString(R.string.lockdown_title), subtitle = lexString(R.string.lockdown_subtitle), onBack = onBack) {
        state.message?.takeIf { it !is LockdownMessage.Wait }?.let { Text(it.text(), style = type.body, color = colors.critical) }
        if (state.waitMinutes > 0) Text(lexString(R.string.lockdown_wait_message, state.waitMinutes), style = type.body, color = colors.critical)
        SystemWindow(title = lexString(R.string.lockdown_length_title), accent = colors.accent, reveal = false) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(lexString(R.string.lockdown_shorter), { onEvent(LockdownSetupEvent.Shorter) }, Modifier.weight(1f), enabled = state.minutes > LockdownRules.MIN_MINUTES)
                Text(
                    lexString(R.string.lockdown_minutes, state.minutes),
                    style = type.metricXL,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(lexString(R.string.lockdown_longer), { onEvent(LockdownSetupEvent.Longer) }, Modifier.weight(1f), enabled = state.minutes < LockdownRules.MAX_MINUTES)
            }
        }
        SystemWindow(title = lexString(R.string.lockdown_apps_title), accent = colors.accent, reveal = false) {
            Text(lexString(R.string.lockdown_apps_note), style = type.caption, color = colors.textSecondary)
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(LockdownSetupEvent.Query(it)) },
                singleLine = true,
                label = { Text(lexString(R.string.lockdown_apps_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.full) Text(lexString(R.string.lockdown_apps_full), style = type.caption, color = colors.textSecondary)
            state.apps.forEach { app -> AppRow(app, state.full, onEvent) }
        }
        SystemWindow(title = null, accent = colors.accent, reveal = false) {
            if (!state.permissionsReady) Text(lexString(R.string.lockdown_disclosure), style = type.body, color = colors.textPrimary)
            StatusRow(R.string.pause_perm_usage, state.usageAccess, onFixUsage)
            StatusRow(R.string.pause_perm_overlay, state.appearOnTop, onFixOverlay)
            if (!state.permissionsReady) Text(lexString(R.string.lockdown_needs_permissions), style = type.caption, color = colors.critical)
        }
        PrimaryButton(lexString(R.string.lockdown_continue), { onEvent(LockdownSetupEvent.Continue) }, Modifier.fillMaxWidth(), enabled = state.canContinue)
    }
}

@Composable
private fun AppRow(app: LockdownAppOption, full: Boolean, onEvent: (LockdownSetupEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val enabled = app.reason == null && (app.chosen || !full)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AwakenTheme.dimens.touchTarget)
            .toggleable(value = app.chosen, enabled = enabled, role = Role.Switch, onValueChange = { onEvent(LockdownSetupEvent.Toggle(app.packageName)) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = AwakenTheme.type.label, color = colors.textPrimary)
            when (app.reason) {
                LockdownAppReason.ALWAYS_WORKS -> Text(lexString(R.string.lockdown_app_always), style = AwakenTheme.type.caption, color = colors.textSecondary)
                LockdownAppReason.NOT_ALLOWED -> Text(lexString(R.string.lockdown_app_blocked), style = AwakenTheme.type.caption, color = colors.textSecondary)
                null -> Unit
            }
        }
        AwakenSwitch(checked = app.chosen, enabled = enabled)
    }
}

@Composable
private fun Confirm(state: LockdownSetupUiState, onEvent: (LockdownSetupEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(lexString(R.string.lockdown_confirm_title), style = type.title, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(lexString(R.string.lockdown_confirm_body, state.minutes), style = type.body, color = colors.textPrimary, textAlign = TextAlign.Center)
        PrimaryButton(lexString(R.string.lockdown_confirm_start), { onEvent(LockdownSetupEvent.Start) }, Modifier.fillMaxWidth(), enabled = !state.starting)
        QuietButton(lexString(R.string.lockdown_confirm_back), { onEvent(LockdownSetupEvent.Back) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LockdownMessage.text(): String = when (this) {
    is LockdownMessage.Wait -> lexString(R.string.lockdown_wait_message, minutes)
    is LockdownMessage.NotAllowed -> lexString(R.string.lockdown_not_allowed_message, label)
    LockdownMessage.NeedsPermissions -> lexString(R.string.lockdown_needs_permissions)
    LockdownMessage.TooLate -> lexString(R.string.lockdown_too_late_message)
    LockdownMessage.Busy -> lexString(R.string.lockdown_busy_message)
}

private val previewOptions = listOf(
    LockdownAppOption("a", "Notes", chosen = true, reason = null),
    LockdownAppOption("b", "Instagram", chosen = false, reason = LockdownAppReason.NOT_ALLOWED),
    LockdownAppOption("c", "Phone", chosen = false, reason = LockdownAppReason.ALWAYS_WORKS),
    LockdownAppOption("d", "Spotify", chosen = false, reason = null),
)

@PhonePreviews
@Composable
private fun LockdownSetupPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSetupScreen(
            LockdownSetupUiState(loading = false, apps = previewOptions, chosenCount = 1, usageAccess = true, appearOnTop = true, minutes = 45),
            {}, {}, {}, {},
        )
    }
}

@PhonePreviews
@Composable
private fun LockdownSetupNeedsPermissionsPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSetupScreen(
            LockdownSetupUiState(loading = false, apps = previewOptions, waitMinutes = 25, message = LockdownMessage.NotAllowed("Instagram")),
            {}, {}, {}, {},
        )
    }
}

@PhonePreviews
@Composable
private fun LockdownConfirmPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSetupScreen(LockdownSetupUiState(step = LockdownStep.CONFIRM, loading = false, minutes = 45), {}, {}, {}, {})
    }
}
