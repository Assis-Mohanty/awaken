package com.assismohanty.awaken.feature.protect

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.data.clock.DeviceUptime
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.designsystem.components.HoldToConfirmButton
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.LockdownController
import com.assismohanty.awaken.engine.ExitPhase
import com.assismohanty.awaken.engine.LockdownBreak
import com.assismohanty.awaken.engine.LockdownRules
import com.assismohanty.awaken.engine.LockdownStatus
import com.assismohanty.awaken.feature.quest.shortClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An app the session may still use: a shortcut on the session screen. */
data class LockdownAppChip(val packageName: String, val label: String)

/** Where the emergency exit is, as the screen shows it (LD3). */
sealed interface LockdownExitUi {
    data object Idle : LockdownExitUi
    data class Holding(val progress: Float) : LockdownExitUi
    data class Waiting(val secondsLeft: Int, val progress: Float) : LockdownExitUi
}

data class LockdownSessionUiState(val remainingMs: Long, val apps: List<LockdownAppChip>, val exit: LockdownExitUi)

data class LockdownResultUiState(val id: String, val completed: Boolean, val minutes: Int, val reason: LockdownBreak?)

/** What sits over the app while a Lockdown is running, or until its result has been seen. */
sealed interface LockdownHostState {
    data object Hidden : LockdownHostState
    data class Session(val ui: LockdownSessionUiState) : LockdownHostState
    data class Result(val ui: LockdownResultUiState) : LockdownHostState
}

sealed interface LockdownEvent {
    data class ExitPressed(val pressed: Boolean) : LockdownEvent
    data class ScreenVisible(val visible: Boolean) : LockdownEvent
    data class Dismiss(val id: String) : LockdownEvent
}

@HiltViewModel
class LockdownHostViewModel @Inject constructor(
    private val controller: LockdownController,
    private val uiPrefs: UiPrefsRepository,
    private val usage: UsageGateway,
    private val uptime: DeviceUptime,
) : ViewModel() {
    private val pressing = MutableStateFlow(false)
    private val visible = MutableStateFlow(true)
    private val exit = MutableStateFlow<ExitPhase>(ExitPhase.Idle)
    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val clockTicks = flow {
        while (true) {
            emit(uptime.elapsedRealtimeMs())
            delay(TICK_MS)
        }
    }

    @Volatile
    private var running = false

    val uiState: StateFlow<LockdownHostState> = combine(
        controller.observeRunning(), controller.observeLatest(), uiPrefs.prefs, labels, combine(exit, clockTicks) { phase, now -> phase to now },
    ) { session, latest, prefs, names, (phase, now) ->
        running = session != null
        when {
            session != null -> LockdownHostState.Session(
                LockdownSessionUiState(
                    remainingMs = controller.remaining(session).toMillis(),
                    apps = session.allowedPackages.map { LockdownAppChip(it, names[it] ?: it) }.sortedBy { it.label.lowercase() },
                    exit = phase.toUi(now),
                ),
            )
            latest != null && latest.status != LockdownStatus.RUNNING && latest.id != prefs.lockdownSeenId ->
                LockdownHostState.Result(LockdownResultUiState(latest.id, latest.status == LockdownStatus.COMPLETED, (latest.plannedMs / MS_PER_MINUTE).toInt(), latest.brokenReason))
            else -> LockdownHostState.Hidden
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockdownHostState.Hidden)

    init {
        viewModelScope.launch { labels.value = usage.launcherApps().associate { it.packageName to it.label } }
        // The emergency exit's clock, and a look at the session once a second so it ends even if the service is not running.
        viewModelScope.launch {
            var beat = 0
            while (true) {
                delay(EXIT_STEP_MS)
                if (!running) {
                    exit.value = ExitPhase.Idle
                    continue
                }
                val next = LockdownRules.nextExit(exit.value, pressing.value, visible.value, uptime.elapsedRealtimeMs())
                exit.value = if (next == ExitPhase.Done) ExitPhase.Idle else next
                if (next == ExitPhase.Done) controller.emergencyExit()
                if (++beat % BEATS_PER_TICK == 0) controller.tick()
            }
        }
    }

    fun onEvent(event: LockdownEvent) {
        when (event) {
            is LockdownEvent.ExitPressed -> pressing.value = event.pressed
            is LockdownEvent.ScreenVisible -> visible.value = event.visible
            is LockdownEvent.Dismiss -> viewModelScope.launch { uiPrefs.setLockdownSeen(event.id) }
        }
    }

    private fun ExitPhase.toUi(now: Long): LockdownExitUi = when (this) {
        ExitPhase.Idle, ExitPhase.Done -> LockdownExitUi.Idle
        is ExitPhase.Holding -> LockdownExitUi.Holding(((now - sinceMs).toFloat() / LockdownRules.EXIT_HOLD_MS).coerceIn(0f, 1f))
        is ExitPhase.Waiting -> {
            val left = (LockdownRules.EXIT_WAIT_MS - (now - sinceMs)).coerceAtLeast(0)
            LockdownExitUi.Waiting(((left + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt(), 1f - left.toFloat() / LockdownRules.EXIT_WAIT_MS)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 250L
        const val EXIT_STEP_MS = 100L
        const val BEATS_PER_TICK = 10
        const val MS_PER_MINUTE = 60_000L
        const val MS_PER_SECOND = 1_000L
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
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(LockdownEvent.ScreenVisible(true))
        onPauseOrDispose { viewModel.onEvent(LockdownEvent.ScreenVisible(false)) }
    }
    Box(modifier = modifier) {
        content()
        when (val current = state) {
            LockdownHostState.Hidden -> Unit
            is LockdownHostState.Session -> LockdownSessionScreen(
                state = current.ui,
                onEvent = viewModel::onEvent,
                onOpenApp = { pkg ->
                    context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
            )
            is LockdownHostState.Result -> LockdownResultScreen(current.ui, onDone = { viewModel.onEvent(LockdownEvent.Dismiss(current.ui.id)) })
        }
    }
}

/** The only screen of AWAKEN while a Lockdown runs: the time left, the apps still allowed, and the emergency exit. */
@Composable
fun LockdownSessionScreen(
    state: LockdownSessionUiState,
    onEvent: (LockdownEvent) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    BackHandler(enabled = true) { }
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
            Text(lexString(R.string.lockdown_title), style = type.title, color = colors.textSecondary)
            Text(
                lexString(R.string.lockdown_left, shortClock(state.remainingMs)),
                style = type.metricXL,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            SystemWindow(title = lexString(R.string.lockdown_session_apps), accent = colors.accent, reveal = false) {
                if (state.apps.isEmpty()) {
                    Text(lexString(R.string.lockdown_session_no_apps), style = type.caption, color = colors.textSecondary)
                }
                state.apps.forEach { app -> SecondaryButton(app.label, { onOpenApp(app.packageName) }, Modifier.fillMaxWidth()) }
            }
            SystemWindow(title = lexString(R.string.lockdown_exit_title), accent = colors.critical, reveal = false) {
                Text(lexString(R.string.lockdown_exit_note), style = type.caption, color = colors.textSecondary)
                val (label, progress) = when (val exit = state.exit) {
                    LockdownExitUi.Idle -> lexString(R.string.lockdown_exit_hold) to 0f
                    is LockdownExitUi.Holding -> lexString(R.string.lockdown_exit_holding) to exit.progress
                    is LockdownExitUi.Waiting -> lexString(R.string.lockdown_exit_waiting, exit.secondsLeft) to exit.progress
                }
                HoldToConfirmButton(
                    text = label,
                    progress = progress,
                    description = lexString(R.string.cd_lockdown_exit),
                    onPressedChange = { onEvent(LockdownEvent.ExitPressed(it)) },
                )
            }
        }
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
                .align(Alignment.Center),
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

private val previewApps = listOf(LockdownAppChip("a", "Notes"), LockdownAppChip("b", "Spotify"))

@PhonePreviews
@Composable
private fun LockdownSessionPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSessionScreen(LockdownSessionUiState(18 * 60_000L + 42_000L, previewApps, LockdownExitUi.Idle), {}, {})
    }
}

@PhonePreviews
@Composable
private fun LockdownSessionExitWaitingPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSessionScreen(LockdownSessionUiState(5 * 60_000L, emptyList(), LockdownExitUi.Waiting(41, 0.32f)), {}, {})
    }
}

@PhonePreviews
@Composable
private fun LockdownSessionHoldingPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownSessionScreen(LockdownSessionUiState(2 * 3_600_000L + 5_000L, previewApps + LockdownAppChip("c", "A very long app name that keeps going"), LockdownExitUi.Holding(0.5f)), {}, {})
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
    AwakenTheme(reduceMotion = true) { LockdownResultScreen(LockdownResultUiState("2", completed = false, minutes = 30, reason = LockdownBreak.PERMISSION_REVOKED), {}) }
}
