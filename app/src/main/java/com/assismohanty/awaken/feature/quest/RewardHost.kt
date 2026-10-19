package com.assismohanty.awaken.feature.quest

import android.content.res.Resources
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.repo.Chapter
import com.assismohanty.awaken.core.data.repo.NarrativeRepository
import com.assismohanty.awaken.core.data.repo.Notice
import com.assismohanty.awaken.core.data.repo.NoticeStore
import com.assismohanty.awaken.core.designsystem.components.ChapterRevealDialog
import com.assismohanty.awaken.core.designsystem.components.LevelUpDialog
import com.assismohanty.awaken.core.designsystem.components.RankCrest
import com.assismohanty.awaken.core.designsystem.components.RewardModel
import com.assismohanty.awaken.core.designsystem.components.RewardSheet
import com.assismohanty.awaken.core.designsystem.components.SystemToast
import com.assismohanty.awaken.core.designsystem.components.SystemWarningDialog
import com.assismohanty.awaken.core.designsystem.components.achievementNameRes
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.CompletionEvent
import com.assismohanty.awaken.core.domain.CompletionOutcome
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.DifficultyAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RewardHostViewModel @Inject constructor(
    private val completionCenter: CompletionCenter,
    noticeStore: NoticeStore,
    narrative: NarrativeRepository,
) : ViewModel() {
    /** Bundled chapters, for the reveal after a level-up that crosses an unlock level (GD §11). */
    val chapters: StateFlow<List<Chapter>> = flow { emit(narrative.chapters()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    fun consume(eventId: Long) = completionCenter.consume(eventId)

    fun undo(questInstanceId: String) {
        viewModelScope.launch { completionCenter.undo(questInstanceId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private enum class ToastTone { ACHIEVEMENT, SYSTEM, WARNING }

@Immutable
private data class ToastItem(val key: Long, val title: String, val message: String, val tone: ToastTone)

/**
 * Presents queued events app-wide (so the chain survives navigation): RewardSheet → level-up ceremony → chapter reveal → rank-up
 * ceremony → one SystemToast per unlocked achievement → "Quest cleared" snackbar with Undo. Messages become snackbars;
 * rollover notices become the Penalty Zone warning or System toasts.
 */
@Composable
fun RewardHost(snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier, viewModel: RewardHostViewModel = hiltViewModel()) {
    val event by viewModel.current.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val resources = LocalResources.current
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

    fun queueAchievements(achievementIds: List<String>, eventId: Long) {
        achievementIds.forEachIndexed { index, id ->
            val name = achievementNameRes(id)?.let(resources::getString) ?: return@forEachIndexed
            val gold = AchievementEvaluator.byId[id]?.gold ?: 0
            val message = if (gold > 0) resources.getString(R.string.achievement_with_gold, name, gold) else name
            toasts += ToastItem(eventId * TOAST_KEY_STRIDE + index, resources.getString(R.string.system_title_achievement), message, ToastTone.ACHIEVEMENT)
        }
    }

    when (val current = event) {
        is CompletionEvent.Completed -> CompletedPresentation(
            event = current,
            chapters = chapters,
            onFinished = {
                queueAchievements(current.outcome.unlockedAchievements, current.id)
                showMessage(UserMessage.QuestCleared, undoQuestId = current.outcome.questInstanceId.takeIf { current.outcome.undoable })
                viewModel.consume(current.id)
            },
        )
        is CompletionEvent.Message -> LaunchedEffect(current.id) {
            showMessage(current.message, current.undoQuestId)
            viewModel.consume(current.id)
        }
        is CompletionEvent.Achievements -> LaunchedEffect(current.id) {
            queueAchievements(current.achievementIds, current.id)
            viewModel.consume(current.id)
        }
        is CompletionEvent.SystemNotice -> when (current.notice) {
            // The Penalty Zone holds the queue until acknowledged (GD §7.3).
            is Notice.PenaltyWarning -> SystemWarningDialog(
                body = stringResource(R.string.penalty_warning_body),
                detail = stringResource(R.string.penalty_warning_detail),
                actionLabel = stringResource(R.string.action_face_it),
                onDismiss = { viewModel.consume(current.id) },
            )
            else -> LaunchedEffect(current.id) {
                toasts += current.notice.toToasts(resources, current.id)
                viewModel.consume(current.id)
            }
        }
        is CompletionEvent.GateCollapsed -> SystemWarningDialog(
            body = stringResource(R.string.gate_collapsed_body),
            detail = collapseDetail(current.reason, current.backgroundMs, resources),
            actionLabel = stringResource(R.string.action_continue),
            onDismiss = { viewModel.consume(current.id) },
        )
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
        SystemToast(
            visible = visible && toast != null,
            title = toast?.title.orEmpty(),
            message = toast?.message.orEmpty(),
            accent = when (toast?.tone) {
                ToastTone.WARNING -> colors.ember
                ToastTone.SYSTEM -> colors.system
                ToastTone.ACHIEVEMENT, null -> colors.crit
            },
        )
    }
}

private fun Notice.toToasts(resources: Resources, eventId: Long): List<ToastItem> = when (this) {
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
    is Notice.PenaltyWarning -> emptyList() // presented as a dialog, never a toast
}

private enum class Phase { SHEET, LEVEL_UP, CHAPTER, RANK_UP }

/** Chapters whose unlock level this level-up crossed: `unlockLevel` in `(before, after]`. */
internal fun newlyUnlockedChapters(chapters: List<Chapter>, levelBefore: Int, levelAfter: Int): List<IndexedValue<Chapter>> =
    chapters.withIndex().filter { (_, chapter) -> chapter.unlockLevel in (levelBefore + 1)..levelAfter }

@Composable
private fun CompletedPresentation(event: CompletionEvent.Completed, chapters: List<Chapter>, onFinished: () -> Unit) {
    val outcome = event.outcome
    val promotedTo = outcome.promotedTo
    var phase by remember(event.id) { mutableStateOf(Phase.SHEET) }
    val unlocked = remember(event.id, chapters) { newlyUnlockedChapters(chapters, outcome.levelBefore, outcome.levelAfter) }
    var chapterIndex by remember(event.id) { mutableIntStateOf(0) }
    val afterChapters = { if (promotedTo != null) phase = Phase.RANK_UP else onFinished() }
    val afterLevelUp = { if (unlocked.isNotEmpty()) phase = Phase.CHAPTER else afterChapters() }
    when (phase) {
        Phase.SHEET -> RewardSheet(
            model = outcome.toRewardModel(),
            onDone = { if (outcome.leveledUp) phase = Phase.LEVEL_UP else afterLevelUp() },
        )
        Phase.LEVEL_UP -> LevelUpDialog(
            oldLevel = outcome.levelBefore,
            newLevel = outcome.levelAfter,
            statGains = outcome.reward.statXp,
            onDismiss = afterLevelUp,
        )
        Phase.CHAPTER -> unlocked.getOrNull(chapterIndex)?.let { (index, chapter) ->
            ChapterRevealDialog(
                number = index + 1,
                title = chapter.title,
                body = chapter.body,
                onDismiss = { if (chapterIndex + 1 < unlocked.size) chapterIndex++ else afterChapters() },
            )
        }
        // GD §5 promotion cinematic: the level-up ceremony with the new rank's crest and colour.
        Phase.RANK_UP -> if (promotedTo != null) {
            LevelUpDialog(
                oldLevel = outcome.levelAfter,
                newLevel = outcome.levelAfter,
                statGains = emptyMap(),
                onDismiss = onFinished,
                title = stringResource(R.string.system_title_rank_up),
                accent = AwakenTheme.colors.rank(promotedTo),
                crest = { RankCrest(rank = promotedTo, size = AwakenTheme.dimens.crestLarge) },
            )
        }
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
