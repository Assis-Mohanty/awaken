package com.assismohanty.awaken.feature.quest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.LevelUpDialog
import com.assismohanty.awaken.core.designsystem.components.RewardModel
import com.assismohanty.awaken.core.designsystem.components.RewardSheet
import com.assismohanty.awaken.core.designsystem.components.SystemToast
import com.assismohanty.awaken.core.designsystem.components.achievementNameRes
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.CompletionEvent
import com.assismohanty.awaken.core.domain.CompletionOutcome
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.AchievementEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RewardHostViewModel @Inject constructor(private val completionCenter: CompletionCenter) : ViewModel() {
    /** The event being presented: the head of the queue. */
    val current: StateFlow<CompletionEvent?> = completionCenter.events
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun consume(eventId: Long) = completionCenter.consume(eventId)

    fun undo(questInstanceId: String) {
        viewModelScope.launch { completionCenter.undo(questInstanceId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Immutable
private data class AchievementToast(val key: Long, val title: String, val message: String)

/**
 * Presents queued completion events app-wide (so the chain survives navigation): RewardSheet → level-up ceremony →
 * one SystemToast per unlocked achievement → "Quest cleared" snackbar with Undo. Messages become snackbars.
 */
@Composable
fun RewardHost(snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier, viewModel: RewardHostViewModel = hiltViewModel()) {
    val event by viewModel.current.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    // Snackbars and toasts outlive the event that started them, so they run in the host's scope.
    val scope = rememberCoroutineScope()
    val toasts = remember { mutableStateListOf<AchievementToast>() }
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

    fun queueToasts(achievementIds: List<String>, firstKey: Long) {
        achievementIds.forEachIndexed { index, id ->
            val name = achievementNameRes(id)?.let(resources::getString) ?: return@forEachIndexed
            val gold = AchievementEvaluator.byId[id]?.gold ?: 0
            val message = if (gold > 0) resources.getString(R.string.achievement_with_gold, name, gold) else name
            toasts += AchievementToast(firstKey * TOAST_KEY_STRIDE + index, resources.getString(R.string.system_title_achievement), message)
        }
    }

    when (val current = event) {
        is CompletionEvent.Completed -> CompletedPresentation(
            event = current,
            onFinished = {
                queueToasts(current.outcome.unlockedAchievements, current.id)
                showMessage(UserMessage.QuestCleared, undoQuestId = current.outcome.questInstanceId)
                viewModel.consume(current.id)
            },
        )
        is CompletionEvent.Message -> LaunchedEffect(current.id) {
            showMessage(current.message, current.undoQuestId)
            viewModel.consume(current.id)
        }
        is CompletionEvent.Achievements -> LaunchedEffect(current.id) {
            queueToasts(current.achievementIds, current.id)
            viewModel.consume(current.id)
        }
        null -> Unit
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
        SystemToast(visible = visible && toast != null, title = toast?.title.orEmpty(), message = toast?.message.orEmpty())
    }
}

private enum class Phase { SHEET, LEVEL_UP }

@Composable
private fun CompletedPresentation(event: CompletionEvent.Completed, onFinished: () -> Unit) {
    val outcome = event.outcome
    var phase by remember(event.id) { mutableStateOf(Phase.SHEET) }
    when (phase) {
        Phase.SHEET -> RewardSheet(
            model = outcome.toRewardModel(),
            onDone = { if (outcome.leveledUp) phase = Phase.LEVEL_UP else onFinished() },
        )
        Phase.LEVEL_UP -> LevelUpDialog(
            oldLevel = outcome.levelBefore,
            newLevel = outcome.levelAfter,
            statGains = outcome.reward.statXp,
            onDismiss = onFinished,
        )
    }
}

/** Straight from the engine's reward (invariant 4): nothing is recomputed here. */
internal fun CompletionOutcome.toRewardModel() = RewardModel(
    xp = reward.xp,
    statXp = reward.statXp,
    gold = reward.gold,
    fragments = reward.fragments,
    crit = reward.crit,
    capped = reward.cappedSource != null,
    difficulty = difficulty,
    verification = verification,
    breakdown = reward.breakdown,
)

/** Toast keys are unique across events: event id × stride + index within the event. */
private const val TOAST_KEY_STRIDE = 100L
