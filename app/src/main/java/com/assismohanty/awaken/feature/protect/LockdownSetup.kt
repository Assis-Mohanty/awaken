package com.assismohanty.awaken.feature.protect

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.usage.InstalledApp
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.data.usage.trackedMillis
import com.assismohanty.awaken.core.designsystem.components.AppSlot
import com.assismohanty.awaken.core.designsystem.components.AppSlotsBar
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SlideToConfirm
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.WheelPicker
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.rememberLexResources
import com.assismohanty.awaken.core.designsystem.preview.LandscapePreviews
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.DistractingApps
import com.assismohanty.awaken.core.domain.LockdownController
import com.assismohanty.awaken.core.domain.LockdownEnvironment
import com.assismohanty.awaken.core.domain.ShieldStatus
import com.assismohanty.awaken.core.domain.StartLockdownResult
import com.assismohanty.awaken.core.domain.StartLockdownUseCase
import com.assismohanty.awaken.core.lock.FocusLockSettingsLauncher
import com.assismohanty.awaken.core.protect.AppIcons
import com.assismohanty.awaken.core.protect.ProtectController
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.ExitCheck
import com.assismohanty.awaken.engine.FocusLockRules
import com.assismohanty.awaken.engine.LockdownRules
import com.assismohanty.awaken.feature.focuslock.StatusRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An app that can be put in a slot. */
data class LockdownPickOption(val packageName: String, val label: String, val icon: ImageBitmap?)

/** What went wrong when starting, in words the screen can show. */
sealed interface LockdownMessage {
    data class NotAllowed(val label: String) : LockdownMessage
    data object NeedsPermissions : LockdownMessage
    data object TooLate : LockdownMessage
    data object Busy : LockdownMessage
}

data class LockdownSetupUiState(
    val minutes: Int = LockdownRules.DEFAULT_MINUTES,
    val slots: List<LockdownSlotUi> = List(LockdownRules.MAX_APPS) { LockdownSlotUi(null, null, null) },
    /** The slot being filled, or null while no picker is open. */
    val pickerSlot: Int? = null,
    val query: String = "",
    val options: List<LockdownPickOption> = emptyList(),
    val usageAccess: Boolean = false,
    val appearOnTop: Boolean = false,
    val shield: ShieldStatus = ShieldStatus(0, false),
    val message: LockdownMessage? = null,
    val starting: Boolean = false,
    val finished: Boolean = false,
) {
    val permissionsReady: Boolean get() = usageAccess && appearOnTop
    val canStart: Boolean get() = permissionsReady && !starting
}

sealed interface LockdownSetupEvent {
    data object Resumed : LockdownSetupEvent
    data class SetMinutes(val minutes: Int) : LockdownSetupEvent
    data class OpenSlot(val index: Int) : LockdownSetupEvent
    data object CloseSlot : LockdownSetupEvent
    data class Query(val text: String) : LockdownSetupEvent
    data class Pick(val packageName: String) : LockdownSetupEvent
    data object ClearSlot : LockdownSetupEvent
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
    private val icons: AppIcons,
    private val clock: Clock,
) : ViewModel() {
    private data class Draft(
        val minutes: Int = LockdownRules.DEFAULT_MINUTES,
        val chosen: List<String?> = List(LockdownRules.MAX_APPS) { null },
        val pickerSlot: Int? = null,
        val query: String = "",
        val message: LockdownMessage? = null,
        val starting: Boolean = false,
        val finished: Boolean = false,
    )

    private data class Checks(val usage: Boolean, val overlay: Boolean)

    private val draft = MutableStateFlow(Draft())
    private val eligible = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val checks = MutableStateFlow(Checks(env.usageAccess(), env.canAppearOnTop()))
    private val minutesUsed = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val shield = MutableStateFlow(ShieldStatus(0, false))

    val uiState: StateFlow<LockdownSetupUiState> = combine(draft, eligible, checks, minutesUsed, shield) { d, apps, c, used, shields ->
        val labels = apps.associate { it.packageName to it.label }
        val slots = d.chosen.map { pkg -> pkg?.let { LockdownSlotUi(it, labels[it] ?: it, icons.icon(it)) } ?: LockdownSlotUi(null, null, null) }
        // Without a search, only the few apps used most are offered; typing finds any other.
        val searching = d.query.isNotBlank()
        val options = apps
            .filter { it.packageName !in d.chosen }
            .filter { !searching || it.label.contains(d.query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<InstalledApp> { used[it.packageName] ?: 0L }.thenBy { it.label.lowercase() })
            .let { if (searching) it else it.take(SUGGESTED) }
            .map { LockdownPickOption(it.packageName, it.label, icons.icon(it.packageName)) }
        LockdownSetupUiState(d.minutes, slots, d.pickerSlot, d.query, options, c.usage, c.overlay, shields, d.message, d.starting, d.finished)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockdownSetupUiState())

    init {
        viewModelScope.launch {
            val distracting = distractingApps.current()
            val ok = env.okFromPhone()
            eligible.value = usage.launcherApps().filter { app ->
                !FocusLockRules.neverBlocked(app.packageName, ok) &&
                    LockdownRules.canAllow(if (app.packageName in distracting) AppCategory.DISTRACTING else AppCategory.NEUTRAL, app.kind)
            }
            val now = clock.now()
            minutesUsed.value = usage.read(now.minus(Duration.ofDays(USAGE_DAYS)), now).trackedMillis.orEmpty()
        }
        refreshChecks()
    }

    fun onEvent(event: LockdownSetupEvent) {
        when (event) {
            LockdownSetupEvent.Resumed -> refreshChecks()
            is LockdownSetupEvent.SetMinutes -> if (LockdownRules.isValidMinutes(event.minutes)) draft.update { it.copy(minutes = event.minutes) }
            is LockdownSetupEvent.OpenSlot -> draft.update { it.copy(pickerSlot = event.index, query = "") }
            LockdownSetupEvent.CloseSlot -> draft.update { it.copy(pickerSlot = null, query = "") }
            is LockdownSetupEvent.Query -> draft.update { it.copy(query = event.text) }
            is LockdownSetupEvent.Pick -> draft.update { d ->
                val slot = d.pickerSlot ?: return@update d
                d.copy(chosen = d.chosen.toMutableList().also { it[slot] = event.packageName }, pickerSlot = null, query = "")
            }
            LockdownSetupEvent.ClearSlot -> draft.update { d ->
                val slot = d.pickerSlot ?: return@update d
                d.copy(chosen = d.chosen.toMutableList().also { it[slot] = null }, pickerSlot = null, query = "")
            }
            LockdownSetupEvent.Start -> startNow()
        }
    }

    private fun refreshChecks() {
        viewModelScope.launch {
            checks.value = Checks(env.usageAccess(), env.canAppearOnTop())
            shield.value = controller.shieldStatus()
        }
    }

    private fun startNow() {
        val d = draft.value
        if (d.starting) return
        draft.update { it.copy(starting = true, message = null) }
        viewModelScope.launch {
            when (val result = start(d.minutes, d.chosen.filterNotNull().toSet())) {
                is StartLockdownResult.Started -> {
                    protect.refresh()
                    draft.update { it.copy(starting = false, finished = true) }
                }
                else -> draft.update { it.copy(starting = false, message = result.toMessage()) }
            }
        }
    }

    private fun StartLockdownResult.toMessage(): LockdownMessage? = when (this) {
        is StartLockdownResult.AppNotAllowed -> LockdownMessage.NotAllowed(eligible.value.firstOrNull { it.packageName == packageName }?.label ?: packageName)
        StartLockdownResult.NeedsPermissions -> LockdownMessage.NeedsPermissions
        StartLockdownResult.TooLate -> LockdownMessage.TooLate
        StartLockdownResult.AlreadyRunning -> LockdownMessage.Busy
        else -> null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SUGGESTED = 8
        const val USAGE_DAYS = 7L
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
        onCall = { context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        modifier = modifier,
    )
}

/**
 * Lockdown setup (GD §19.2): scroll to the time, or tap 15 minutes, 45 minutes or an hour; say plainly that there is no
 * way out; show the shield that can be used once a day; choose up to three apps with the plus slots; slide to start.
 */
@Composable
fun LockdownSetupScreen(
    state: LockdownSetupUiState,
    onEvent: (LockdownSetupEvent) -> Unit,
    onBack: () -> Unit,
    onFixUsage: () -> Unit,
    onFixOverlay: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val words = rememberLexResources()
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(title = lexString(R.string.lockdown_title), subtitle = lexString(R.string.lockdown_setup_subtitle), onBack = onBack) {
            state.message?.let { Text(it.text(), style = type.body, color = colors.critical) }
            AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
                WheelPicker(
                    options = LockdownRules.MINUTE_CHOICES,
                    selectedIndex = LockdownRules.MINUTE_CHOICES.indexOf(state.minutes).coerceAtLeast(0),
                    onSelected = { onEvent(LockdownSetupEvent.SetMinutes(LockdownRules.MINUTE_CHOICES[it])) },
                    label = { words.getString(R.string.lockdown_minutes, it) },
                    description = lexString(R.string.lockdown_time_description),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                    LockdownRules.PRESET_MINUTES.forEach { minutes ->
                        ChoiceChip(
                            text = if (minutes == HOUR) lexString(R.string.lockdown_preset_hour) else lexString(R.string.lockdown_minutes, minutes),
                            selected = state.minutes == minutes,
                            onClick = { onEvent(LockdownSetupEvent.SetMinutes(minutes)) },
                        )
                    }
                }
            }
            SystemWindow(title = lexString(R.string.lockdown_warning_title), accent = colors.critical, reveal = false) {
                Text(lexString(R.string.lockdown_warning_body), style = type.body, color = colors.textPrimary)
                Text(
                    text = when (state.shield.check) {
                        ExitCheck.ALLOWED -> lexPlural(R.plurals.lockdown_shield_available, state.shield.shields, state.shield.shields)
                        ExitCheck.NO_SHIELD -> lexString(R.string.lockdown_shield_none)
                        ExitCheck.USED_TODAY -> lexString(R.string.lockdown_shield_used)
                    },
                    style = type.caption,
                    color = colors.textSecondary,
                )
            }
            if (!state.permissionsReady) {
                SystemWindow(title = null, accent = colors.accent, reveal = false) {
                    Text(lexString(R.string.lockdown_disclosure), style = type.body, color = colors.textPrimary)
                    StatusRow(R.string.pause_perm_usage, state.usageAccess, onFixUsage)
                    StatusRow(R.string.pause_perm_overlay, state.appearOnTop, onFixOverlay)
                }
            }
            Text(lexString(R.string.lockdown_slots_title), style = type.label, color = colors.textPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
            AppSlotsBar(
                slots = state.slots.map { slot ->
                    AppSlot(slot.icon, slot.packageName != null, slot.label?.let { lexString(R.string.lockdown_slot_filled_cd, it) } ?: lexString(R.string.lockdown_slot_empty_cd))
                },
                callDescription = lexString(R.string.lockdown_call_cd),
                onSlot = { onEvent(LockdownSetupEvent.OpenSlot(it)) },
                onCall = onCall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            SlideToConfirm(
                text = lexString(R.string.lockdown_slide_start),
                handle = AwakenGlyphs.Lock,
                description = lexString(R.string.lockdown_slide_start_cd),
                onConfirmed = { onEvent(LockdownSetupEvent.Start) },
                enabled = state.canStart,
            )
        }
        if (state.pickerSlot != null) PickerSheet(state, onEvent)
    }
}

@Composable
private fun PickerSheet(state: LockdownSetupUiState, onEvent: (LockdownSetupEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val filled = state.pickerSlot?.let { state.slots.getOrNull(it)?.packageName } != null
    AwakenBottomSheet(onDismiss = { onEvent(LockdownSetupEvent.CloseSlot) }, title = lexString(R.string.lockdown_pick_title)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(LockdownSetupEvent.Query(it)) },
            singleLine = true,
            label = { Text(lexString(R.string.lockdown_pick_search)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(lexString(R.string.lockdown_pick_note), style = type.caption, color = colors.textSecondary)
        if (filled) QuietButton(lexString(R.string.lockdown_pick_remove), { onEvent(LockdownSetupEvent.ClearSlot) }, Modifier.fillMaxWidth())
        if (state.options.isEmpty()) Text(lexString(R.string.lockdown_pick_none), style = type.caption, color = colors.textSecondary)
        state.options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AwakenTheme.dimens.touchTarget)
                    .clickable(role = Role.Button) { onEvent(LockdownSetupEvent.Pick(option.packageName)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (option.icon != null) Image(bitmap = option.icon, contentDescription = null, modifier = Modifier.size(AwakenTheme.dimens.icon + Spacing.xs))
                Text(option.label, style = type.label, color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun LockdownMessage.text(): String = when (this) {
    is LockdownMessage.NotAllowed -> lexString(R.string.lockdown_not_allowed_message, label)
    LockdownMessage.NeedsPermissions -> lexString(R.string.lockdown_needs_permissions)
    LockdownMessage.TooLate -> lexString(R.string.lockdown_too_late_message)
    LockdownMessage.Busy -> lexString(R.string.lockdown_busy_message)
}

private const val HOUR = 60

private val previewSlots = listOf(LockdownSlotUi("a", "Notes", null), LockdownSlotUi(null, null, null), LockdownSlotUi(null, null, null))

@PhonePreviews
@LandscapePreviews
@Composable
private fun LockdownSetupPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSetupScreen(
            LockdownSetupUiState(minutes = 45, slots = previewSlots, usageAccess = true, appearOnTop = true, shield = ShieldStatus(3, false)),
            {}, {}, {}, {}, {},
        )
    }
}

@PhonePreviews
@Composable
private fun LockdownSetupNoShieldNeedsPermissionsPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSetupScreen(
            LockdownSetupUiState(minutes = 60, shield = ShieldStatus(0, false), message = LockdownMessage.NotAllowed("Instagram")),
            {}, {}, {}, {}, {},
        )
    }
}

@PhonePreviews
@Composable
private fun LockdownSetupShieldUsedPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSetupScreen(
            LockdownSetupUiState(minutes = 15, slots = previewSlots, usageAccess = true, appearOnTop = true, shield = ShieldStatus(2, true)),
            {}, {}, {}, {}, {},
        )
    }
}
