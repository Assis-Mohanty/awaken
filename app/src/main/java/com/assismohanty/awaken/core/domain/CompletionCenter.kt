package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.repo.Notice
import com.assismohanty.awaken.engine.GateCollapseReason
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Something the reward host should present: a completion outcome, or a short message (failure, undo, reroll). */
sealed interface CompletionEvent {
    val id: Long

    data class Completed(override val id: Long, val outcome: CompletionOutcome) : CompletionEvent
    data class Message(override val id: Long, val message: UserMessage, val undoQuestId: String? = null) : CompletionEvent

    /** A System notice from the day rollover (penalty warning, shield, recalibration, failed trial). */
    data class SystemNotice(override val id: Long, val notice: Notice) : CompletionEvent

    /** Unlocks outside a quest completion (e.g. "Awakened" at the end of onboarding). */
    data class Achievements(override val id: Long, val achievementIds: List<String>) : CompletionEvent

    /** A Focus Gate collapsed: the ember window (DESIGN_SYSTEM §4.4). */
    data class GateCollapsed(override val id: Long, val reason: GateCollapseReason, val backgroundMs: Long) : CompletionEvent
}

/** UI-agnostic message; the app maps it to a string resource. */
sealed interface UserMessage {
    data class TooSoon(val remainingSeconds: Long) : UserMessage
    data object VerificationRequired : UserMessage
    data object WrongDay : UserMessage
    data object AlreadyDone : UserMessage
    data object QuestCleared : UserMessage
    data object Undone : UserMessage
    data object UndoExpired : UserMessage
    data object UndoDayClosed : UserMessage
    data object UndoPromotion : UserMessage
    data object UndoGate : UserMessage
    data object GateTooLate : UserMessage
    data object TrialStarted : UserMessage
    data object TrialNoStages : UserMessage
    data class Rerolled(val cost: Int) : UserMessage
    data object RerollLimit : UserMessage
    data class RerollGold(val cost: Int) : UserMessage
    data object RerollNone : UserMessage
    data object ShareFailed : UserMessage
    data class VaultBought(val itemId: String) : UserMessage
    data class VaultEquipped(val itemId: String) : UserMessage
    data object VaultDuplicate : UserMessage
    data object VaultNoFragments : UserMessage
    data object VaultPoolEmpty : UserMessage
    data object Error : UserMessage
}

/**
 * The single place quest completions start from in the UI (Today rows and Quest detail). Results are queued for the
 * app-level reward host, so the reward sheet → level-up → toasts chain survives navigating between screens.
 */
@Singleton
class CompletionCenter @Inject constructor(
    private val completeQuest: CompleteQuestUseCase,
    private val undoCompletion: UndoCompletionUseCase,
) {
    private val queue = MutableStateFlow<List<CompletionEvent>>(emptyList())
    private var nextId = 0L

    val events: StateFlow<List<CompletionEvent>> = queue.asStateFlow()

    /** Manual (SELF) completion; returns true when the quest was completed. */
    suspend fun complete(questInstanceId: String): Boolean {
        val result = completeQuest(questInstanceId)
        when (result) {
            is CompleteQuestResult.Completed -> post { CompletionEvent.Completed(it, result.outcome) }
            is CompleteQuestResult.TooSoon -> message(UserMessage.TooSoon(result.remaining.seconds.coerceAtLeast(1)))
            CompleteQuestResult.VerificationRequired -> message(UserMessage.VerificationRequired)
            CompleteQuestResult.WrongDay -> message(UserMessage.WrongDay)
            is CompleteQuestResult.NotCompletable -> message(UserMessage.AlreadyDone)
            CompleteQuestResult.NotFound, CompleteQuestResult.NoHunter -> message(UserMessage.Error)
        }
        return result is CompleteQuestResult.Completed
    }

    suspend fun undo(questInstanceId: String) {
        message(
            when (undoCompletion(questInstanceId)) {
                is UndoResult.Undone -> UserMessage.Undone
                UndoResult.WindowExpired -> UserMessage.UndoExpired
                UndoResult.DayClosed -> UserMessage.UndoDayClosed
                UndoResult.TrialPassed -> UserMessage.UndoPromotion
                UndoResult.GateClear -> UserMessage.UndoGate
                UndoResult.NothingToUndo -> UserMessage.Error
            },
        )
    }

    fun message(message: UserMessage, undoQuestId: String? = null) = post { CompletionEvent.Message(it, message, undoQuestId) }

    fun notice(notice: Notice) = post { CompletionEvent.SystemNotice(it, notice) }

    /** A completion made by a verifier (a cleared Focus Gate) rather than by [complete]. */
    fun completed(outcome: CompletionOutcome) = post { CompletionEvent.Completed(it, outcome) }

    fun gateCollapsed(reason: GateCollapseReason, backgroundMs: Long) =
        post { CompletionEvent.GateCollapsed(it, reason, backgroundMs) }

    fun achievements(achievementIds: List<String>) {
        if (achievementIds.isNotEmpty()) post { CompletionEvent.Achievements(it, achievementIds) }
    }

    /** Called by the host once an event has been fully presented. */
    fun consume(eventId: Long) = queue.update { events -> events.filterNot { it.id == eventId } }

    private fun post(build: (Long) -> CompletionEvent) {
        queue.update { it + build(nextId++) }
    }
}
