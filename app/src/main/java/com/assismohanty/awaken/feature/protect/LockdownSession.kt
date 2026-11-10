package com.assismohanty.awaken.feature.protect

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.designsystem.components.AppSlot
import com.assismohanty.awaken.core.designsystem.components.AppSlotsBar
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.FitText
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SlideToConfirm
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.LandscapePreviews
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.LockdownController
import com.assismohanty.awaken.core.domain.ShieldStatus
import com.assismohanty.awaken.core.protect.AppIcons
import com.assismohanty.awaken.engine.ExitCheck
import com.assismohanty.awaken.engine.LockdownBreak
import com.assismohanty.awaken.engine.LockdownRules
import com.assismohanty.awaken.engine.LockdownStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

/** One of the three app slots on the session screen; an empty one is shown but does nothing. */
data class LockdownSlotUi(val packageName: String?, val label: String?, val icon: ImageBitmap?)

data class LockdownSessionUiState(
    val remainingMs: Long,
    val endsAtText: String,
    val slots: List<LockdownSlotUi>,
    val shields: Int,
    val exit: ExitCheck,
)

data class LockdownResultUiState(val id: String, val completed: Boolean, val minutes: Int, val reason: LockdownBreak?)

/** What sits over the app while a Lockdown is running, or until its result has been seen. */
sealed interface LockdownHostState {
    data object Hidden : LockdownHostState
    data class Session(val ui: LockdownSessionUiState) : LockdownHostState
    data class Result(val ui: LockdownResultUiState) : LockdownHostState
}

sealed interface LockdownEvent {
    /** The slide to exit finished: spend a shield and leave. */
    data object Exit : LockdownEvent
    data class Dismiss(val id: String) : LockdownEvent
}

@HiltViewModel
class LockdownHostViewModel @Inject constructor(
    private val controller: LockdownController,
    private val uiPrefs: UiPrefsRepository,
    private val usage: UsageGateway,
    private val icons: AppIcons,
    private val clock: Clock,
) : ViewModel() {
    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }
    private val shield: StateFlow<ShieldStatus> = combine(controller.observeShields(), controller.observeRunning()) { _, _ -> controller.shieldStatus() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShieldStatus(0, false))

    @Volatile
    private var running = false

    val uiState: StateFlow<LockdownHostState> = combine(
        controller.observeRunning(), controller.observeLatest(), uiPrefs.prefs, labels, combine(shield, ticks) { status, _ -> status },
    ) { session, latest, prefs, names, status ->
        running = session != null
        when {
            session != null -> {
                val remaining = controller.remaining(session)
                val chosen = session.allowedPackages.sortedBy { (names[it] ?: it).lowercase() }
                LockdownHostState.Session(
                    LockdownSessionUiState(
                        remainingMs = remaining.toMillis(),
                        endsAtText = TIME.format(clock.now().plus(remaining).atZone(clock.zone())),
                        slots = (0 until LockdownRules.MAX_APPS).map { index ->
                            chosen.getOrNull(index)?.let { LockdownSlotUi(it, names[it] ?: it, icons.icon(it)) } ?: LockdownSlotUi(null, null, null)
                        },
                        shields = status.shields,
                        exit = status.check,
                    ),
                )
            }
            latest != null && latest.status != LockdownStatus.RUNNING && latest.id != prefs.lockdownSeenId ->
                LockdownHostState.Result(LockdownResultUiState(latest.id, latest.status == LockdownStatus.COMPLETED, (latest.plannedMs / MS_PER_MINUTE).toInt(), latest.brokenReason))
            else -> LockdownHostState.Hidden
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockdownHostState.Hidden)

    init {
        viewModelScope.launch { labels.value = usage.launcherApps().associate { it.packageName to it.label } }
        // A look at the session once a second, so it still ends (or completes) with the app open and the service not running.
        viewModelScope.launch {
            while (true) {
                delay(BEAT_MS)
                if (running) controller.tick()
            }
        }
    }

    fun onEvent(event: LockdownEvent) {
        when (event) {
            LockdownEvent.Exit -> viewModelScope.launch { controller.leaveWithShield() }
            is LockdownEvent.Dismiss -> viewModelScope.launch { uiPrefs.setLockdownSeen(event.id) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 250L
        const val BEAT_MS = 1_000L
        const val MS_PER_MINUTE = 60_000L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}

/**
 * Wraps the whole app: while a Lockdown runs, its session screen covers everything else and swallows touches and Back;
 * when it ends, its result stays until it has been seen (GD §19.2).
 */
@Composable
fun LockdownHost(modifier: Modifier = Modifier, viewModel: LockdownHostViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Box(modifier = modifier) {
        content()
        when (val current = state) {
            LockdownHostState.Hidden -> Unit
            is LockdownHostState.Session -> LockdownSessionScreen(
                state = current.ui,
                onExit = { viewModel.onEvent(LockdownEvent.Exit) },
                onOpenApp = { pkg ->
                    context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                onCall = { context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
            is LockdownHostState.Result -> LockdownResultScreen(current.ui, onDone = { viewModel.onEvent(LockdownEvent.Dismiss(current.ui.id)) })
        }
    }
}

/** The clock as it reads: 18:42, or 1:05:09 from an hour up. */
internal fun lockdownClockText(remainingMs: Long): String {
    val total = (remainingMs / MS_PER_SECOND).coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/**
 * The only screen of AWAKEN while a Lockdown runs: the time left as the biggest thing on the screen (the app's own
 * numbers, sized to fill their card), the seconds, when it ends, the shields, a slide to leave (which costs a shield, once
 * a day), and the apps still allowed with a call button. It fills the screen in portrait, and splits in two sideways.
 * Going anywhere else brings the hunter back here. There is no button to end it.
 */
@Composable
fun LockdownSessionScreen(
    state: LockdownSessionUiState,
    onExit: () -> Unit,
    onOpenApp: (String) -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    BackHandler(enabled = true) { }
    ScreenBackground(modifier = modifier.fillMaxSize().pointerInput(Unit) { }) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        ) {
            val sideways = maxWidth > maxHeight
            val title: @Composable () -> Unit = {
                Text(lexString(R.string.lockdown_title), style = type.title, color = colors.textSecondary)
            }
            // The time left fills the middle of the screen, with no card around it: nothing here is left empty.
            val clock: @Composable (Modifier) -> Unit = { m ->
                Box(modifier = m.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FitText(lockdownClockText(state.remainingMs), type.metricXL, Modifier.fillMaxWidth())
                }
            }
            val facts: @Composable () -> Unit = { Facts(state) }
            val controls: @Composable () -> Unit = { Controls(state, onExit, onOpenApp, onCall) }
            if (sideways) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        title()
                        clock(Modifier.weight(1f))
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically)) {
                        facts()
                        controls()
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { title() }
                    clock(Modifier.weight(1f))
                    facts()
                    controls()
                }
            }
        }
    }
}

/** When it ends and how many shields are held: two small cards side by side. */
@Composable
private fun Facts(state: LockdownSessionUiState) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val shieldDescription = lexPlural(R.plurals.lockdown_shield_count_cd, state.shields, state.shields)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AwakenCard(reveal = false, modifier = Modifier.weight(1f)) {
            Text(lexString(R.string.lockdown_ends).uppercase(), style = type.caption, color = colors.textSecondary)
            Text(state.endsAtText, style = type.title, color = colors.textPrimary)
        }
        AwakenCard(reveal = false, modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = shieldDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            ) {
                Icon(AwakenGlyphs.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(AwakenTheme.dimens.icon))
                Text(state.shields.toString(), style = type.title, color = colors.textPrimary)
            }
        }
    }
}

/** The slide to leave, why it may be off, and the app slots with the call button. */
@Composable
private fun Controls(state: LockdownSessionUiState, onExit: () -> Unit, onOpenApp: (String) -> Unit, onCall: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        SlideToConfirm(
            text = lexString(R.string.lockdown_slide_exit),
            handle = AwakenGlyphs.Shield,
            description = lexString(R.string.lockdown_slide_exit_cd),
            onConfirmed = onExit,
            enabled = state.exit == ExitCheck.ALLOWED,
            handleColor = colors.critical,
        )
        Text(
            text = when (state.exit) {
                ExitCheck.ALLOWED -> lexString(R.string.lockdown_exit_uses, state.shields)
                ExitCheck.NO_SHIELD -> lexString(R.string.lockdown_exit_none)
                ExitCheck.USED_TODAY -> lexString(R.string.lockdown_exit_used)
            },
            style = type.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        AppSlotsBar(
            slots = state.slots.map { slot ->
                AppSlot(slot.icon, slot.packageName != null, slot.label?.let { lexString(R.string.lockdown_slot_open_cd, it) } ?: lexString(R.string.lockdown_slot_empty_session_cd))
            },
            callDescription = lexString(R.string.lockdown_call_cd),
            onSlot = { index -> state.slots.getOrNull(index)?.packageName?.let(onOpenApp) },
            onCall = onCall,
        )
    }
}

/** How a Lockdown ended, until the hunter taps Done. */
@Composable
fun LockdownResultScreen(state: LockdownResultUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    BackHandler(enabled = true) { onDone() }
    ScreenBackground(modifier = modifier.fillMaxSize().pointerInput(Unit) { }) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg)
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                lexString(if (state.completed) R.string.lockdown_done_title else R.string.lockdown_broken_title),
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (state.completed) {
                Text(lexString(R.string.lockdown_done_body, state.minutes), style = type.body, color = colors.textPrimary, textAlign = TextAlign.Center)
            } else {
                val reason = when (state.reason) {
                    LockdownBreak.EMERGENCY_EXIT -> R.string.lockdown_broken_exit
                    LockdownBreak.PERMISSION_REVOKED -> R.string.lockdown_broken_permission
                    LockdownBreak.UNCOVERED, null -> R.string.lockdown_broken_uncovered
                }
                Text(lexString(reason), style = type.body, color = colors.textPrimary, textAlign = TextAlign.Center)
                Text(lexString(R.string.lockdown_broken_note), style = type.caption, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
            PrimaryButton(text = lexString(R.string.lockdown_done_button), onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val previewSlots = listOf(LockdownSlotUi("a", "Notes", null), LockdownSlotUi(null, null, null), LockdownSlotUi(null, null, null))

@PhonePreviews
@LandscapePreviews
@Composable
private fun LockdownSessionPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSessionScreen(LockdownSessionUiState(18 * 60_000L + 42_000L, "11:06 AM", previewSlots, shields = 3, exit = ExitCheck.ALLOWED), {}, {}, {})
    }
}

@PhonePreviews
@Composable
private fun LockdownSessionNoWayOutPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSessionScreen(LockdownSessionUiState(2 * 3_600_000L + 5_000L, "1:20 PM", previewSlots, shields = 0, exit = ExitCheck.NO_SHIELD), {}, {}, {})
    }
}

@PhonePreviews
@Composable
private fun LockdownSessionUsedTodayPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSessionScreen(LockdownSessionUiState(4_000L, "11:06 AM", previewSlots, shields = 2, exit = ExitCheck.USED_TODAY), {}, {}, {})
    }
}

@PhonePreviews
@Composable
private fun LockdownResultCompletedPreview() {
    AwakenTheme(reduceMotion = true) { LockdownResultScreen(LockdownResultUiState("1", completed = true, minutes = 30, reason = null), {}) }
}

@PhonePreviews
@Composable
private fun LockdownResultBrokenPreview() {
    AwakenTheme(reduceMotion = true) { LockdownResultScreen(LockdownResultUiState("2", completed = false, minutes = 30, reason = LockdownBreak.EMERGENCY_EXIT), {}) }
}
