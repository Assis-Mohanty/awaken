package com.assismohanty.awaken.feature.quest

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import android.Manifest
import com.assismohanty.awaken.core.designsystem.lexicon.LexResources
import com.assismohanty.awaken.core.designsystem.lexicon.rememberLexResources
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.repo.Notice
import com.assismohanty.awaken.core.data.repo.NoticeStore
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.work.NotificationAccess
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.RewardModel
import com.assismohanty.awaken.core.designsystem.components.RewardSheet
import com.assismohanty.awaken.core.designsystem.components.SystemToast
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.CompletionEvent
import com.assismohanty.awaken.core.domain.CompletionOutcome
import com.assismohanty.awaken.core.domain.SubmitFeedbackUseCase
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.path.Feedback
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RewardHostViewModel @Inject constructor(
    private val completionCenter: CompletionCenter,
    noticeStore: NoticeStore,
    private val notificationAccess: NotificationAccess,
    private val uiPrefs: UiPrefsRepository,
    private val submitFeedback: SubmitFeedbackUseCase,
) : ViewModel() {
    /** The event being presented: the head of the queue. */
    val current: StateFlow<CompletionEvent?> = completionCenter.events
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        // Rollover notices (possibly written by the background worker) join the presentation queue once each.
        viewModelScope.launch {
            noticeStore.pending.collect { notices ->
                notices.forEach { notice ->
                    completionCenter.notice(notice)
                    noticeStore.remove(notice)
                }
            }
        }
    }

    /** True while the reminders pre-prompt is on screen. */
    private val _notificationPrompt = MutableStateFlow(false)

    /** Set synchronously, so a second completion right after the answer can't ask again before the write lands. */
    private var answeredThisSession = false
    val notificationPrompt: StateFlow<Boolean> = _notificationPrompt.asStateFlow()

    /** After a quest completion finishes: ask about reminders once, on Android 13+ and only while not yet allowed. */
    fun onQuestCompleted() {
        viewModelScope.launch {
            if (!answeredThisSession && notificationAccess.canAsk() && !uiPrefs.prefs.first().notificationsPrompted) {
                _notificationPrompt.value = true
            }
        }
    }

    /** Either answer (or dismissing) ends the question for good. */
    fun notificationPromptAnswered() {
        answeredThisSession = true
        _notificationPrompt.value = false
        viewModelScope.launch { uiPrefs.setNotificationsPrompted() }
    }

    fun consume(eventId: Long) = completionCenter.consume(eventId)

    /** The optional Too easy / Right / Too hard chip; feeds next week's progression signal (GD §6.6.5). */
    fun feedback(questInstanceId: String, feedback: Feedback?) {
        viewModelScope.launch { submitFeedback(questInstanceId, feedback) }
    }

    fun undo(questInstanceId: String) {
        viewModelScope.launch { completionCenter.undo(questInstanceId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private enum class ToastTone { SYSTEM, WARNING }

@Immutable
private data class ToastItem(val key: Long, val title: String, val message: String, val tone: ToastTone)

/**
 * Presents queued events app-wide (so the chain survives navigation): the reward sheet (with a line for a new level or
 * rank) → "Quest cleared" snackbar with Undo. Messages become snackbars; rollover notices become snackbars or toasts.
 */
@Composable
fun RewardHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: RewardHostViewModel = hiltViewModel(),
) {
    val event by viewModel.current.collectAsStateWithLifecycle()
    val resources = rememberLexResources()
    // Snackbars and toasts outlive the event that started them, so they run in the host's scope.
    val scope = rememberCoroutineScope()
    val toasts = remember { mutableStateListOf<ToastItem>() }
    val colors = AwakenTheme.colors
    val holdMs = AwakenTheme.motion.toastHoldMs
    val exitMs = AwakenTheme.motion.standardMs.toLong()

    fun showMessage(message: UserMessage, undoQuestId: String?) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message.text(resources),
                actionLabel = undoQuestId?.let { resources.getString(R.string.action_undo) },
                duration = if (undoQuestId != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && undoQuestId != null) viewModel.undo(undoQuestId)
        }
    }

    when (val current = event) {
        is CompletionEvent.Completed -> CompletedPresentation(
            onFeedback = { viewModel.feedback(current.outcome.questInstanceId, it) },
            event = current,
            onFinished = {
                showMessage(UserMessage.QuestCleared, undoQuestId = current.outcome.questInstanceId.takeIf { current.outcome.undoable })
                viewModel.consume(current.id)
                viewModel.onQuestCompleted()
            },
        )
        is CompletionEvent.Message -> LaunchedEffect(current.id) {
            showMessage(current.message, current.undoQuestId)
            viewModel.consume(current.id)
        }
        is CompletionEvent.SystemNotice -> when (current.notice) {
            // After a missed day: one calm line; the comeback quest itself is on Today (GD §7.3).
            is Notice.PenaltyWarning -> LaunchedEffect(current.id) {
                showMessage(UserMessage.PenaltyOpen, undoQuestId = null)
                viewModel.consume(current.id)
            }
            else -> LaunchedEffect(current.id) {
                toasts += current.notice.toToasts(resources, current.id)
                viewModel.consume(current.id)
            }
        }
        is CompletionEvent.GateCollapsed -> LaunchedEffect(current.id) {
            scope.launch { snackbarHostState.showSnackbar(collapseDetail(current.reason, current.backgroundMs, resources)) }
            viewModel.consume(current.id)
        }
        null -> Unit
    }

    // The reminders pre-prompt (ARCH §3.5): shown once, after the first completed quest, before the system permission.
    val promptNotifications by viewModel.notificationPrompt.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.notificationPromptAnswered()
    }
    if (promptNotifications) {
        NotificationPromptDialog(
            onAllow = {
                // The system dialog only exists on Android 13+, which is the only place the prompt is shown.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.notificationPromptAnswered()
                }
            },
            onNotNow = viewModel::notificationPromptAnswered,
        )
    }

    val toast = toasts.firstOrNull()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toast?.key) {
        if (toast == null) return@LaunchedEffect
        visible = true
        delay(holdMs)
        visible = false
        delay(exitMs) // let the exit animation finish before the next toast enters
        toasts.remove(toast)
    }
    Box(modifier = modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        SystemToast(
            visible = visible && toast != null,
            title = toast?.title.orEmpty(),
            message = toast?.message.orEmpty(),
            accent = when (toast?.tone) {
                ToastTone.WARNING -> colors.critical
                ToastTone.SYSTEM -> colors.accent
                null -> colors.accent
            },
        )
    }
}

private fun Notice.toToasts(resources: LexResources, eventId: Long): List<ToastItem> = when (this) {
    is Notice.ShieldUsed -> listOf(
        ToastItem(eventId * TOAST_KEY_STRIDE, resources.getString(R.string.notice_shield_title), resources.getString(R.string.notice_shield_body), ToastTone.SYSTEM),
    )
    is Notice.Recalibrated -> changes.entries.sortedBy { it.key.ordinal }.mapIndexedNotNull { index, (stat, change) ->
        val line = when (change) {
            DifficultyAdapter.Change.UP -> R.string.notice_recalibrated_harder
            DifficultyAdapter.Change.DOWN -> R.string.notice_recalibrated_easier
            DifficultyAdapter.Change.NONE -> return@mapIndexedNotNull null
        }
        ToastItem(
            eventId * TOAST_KEY_STRIDE + index,
            resources.getString(R.string.notice_recalibrated_title),
            resources.getString(line, resources.getString(stat.nameRes())),
            ToastTone.SYSTEM,
        )
    }
    is Notice.TrialFailed -> listOf(
        ToastItem(eventId * TOAST_KEY_STRIDE, resources.getString(R.string.notice_trial_failed_title), resources.getString(R.string.notice_trial_failed_body, rank.name), ToastTone.WARNING),
    )
    is Notice.PenaltyWarning -> emptyList() // presented as a snackbar, never a toast
}

@Composable
private fun CompletedPresentation(
    event: CompletionEvent.Completed,
    onFinished: () -> Unit,
    onFeedback: (Feedback?) -> Unit,
) {
    var feedback by remember(event.id) { mutableStateOf<Feedback?>(null) }
    RewardSheet(
        model = event.outcome.toRewardModel(),
        onDone = onFinished,
        feedback = feedback,
        onFeedback = { feedback = it; onFeedback(it) },
    )
}

/** Straight from the engine's reward (invariant 4): nothing is recomputed here. */
internal fun CompletionOutcome.toRewardModel() = RewardModel(
    xp = reward.xp,
    statXp = reward.statXp,
    gold = reward.gold,
    capped = reward.cappedSource != null,
    difficulty = difficulty,
    verification = verification,
    breakdown = reward.breakdown,
    pathQuest = pathQuest,
    levelReached = levelAfter.takeIf { leveledUp },
    rankReached = promotedTo,
)

/** Toast keys are unique across events: event id × stride + index within the event. */
private const val TOAST_KEY_STRIDE = 100L

@Composable
private fun NotificationPromptDialog(onAllow: () -> Unit, onNotNow: () -> Unit) {
    val colors = AwakenTheme.colors
    Dialog(onDismissRequest = onNotNow) {
        SystemWindow(title = lexString(R.string.notif_prompt_title), accent = colors.accent) {
            Text(lexString(R.string.notif_prompt_body), style = AwakenTheme.type.body, color = colors.textPrimary)
            PrimaryButton(text = lexString(R.string.action_allow_reminders), onClick = onAllow, modifier = Modifier.fillMaxWidth())
            QuietButton(text = lexString(R.string.action_not_now), onClick = onNotNow, modifier = Modifier.fillMaxWidth())
        }
    }
}
