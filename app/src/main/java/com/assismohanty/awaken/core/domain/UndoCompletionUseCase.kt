package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.repo.COMPLETION_SOURCE_TYPES
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.engine.model.QuestStatus
import javax.inject.Inject

sealed interface UndoResult {
    data class Undone(val completionId: String) : UndoResult
    data object NothingToUndo : UndoResult
    /** More than 10 minutes since completion (GD §6.5). */
    data object WindowExpired : UndoResult
    /** The completion's game-day has ended; finalized days are never re-opened. */
    data object DayClosed : UndoResult
    /** The stage that passed a Rank Trial: promotion is permanent (ENGINE_DECISIONS #32). */
    data object TrialPassed : UndoResult
    /** A cleared Focus Gate is timer-verified and final (ENGINE_DECISIONS #36). */
    data object GateClear : UndoResult
    /** A Health Connect completion: the same data would re-complete it on the next sync (ENGINE_DECISIONS #52). */
    data object HealthVerified : UndoResult
}

/**
 * Reverses the latest completion of a quest by inserting UNDO rows keyed by its completionId (GD §9.3). The ledger is
 * never edited. Achievements are not revoked (GD §6.5).
 */
class UndoCompletionUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val trialDao: TrialDao,
    private val completionDao: CompletionDao,
    private val ledgerDao: LedgerDao,
    private val ledger: LedgerRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(questInstanceId: String): UndoResult = transactions.run {
        val completion = completionDao.latestActive(questInstanceId) ?: return@run UndoResult.NothingToUndo
        if (completion.questType == QuestType.GATE) return@run UndoResult.GateClear
        if (completion.verificationSource == VerificationSource.HEALTH) return@run UndoResult.HealthVerified
        val now = clock.now()
        if (!QuestRules.canUndo(completion.completedAt, now)) return@run UndoResult.WindowExpired
        val profile = hunterDao.getProfile() ?: return@run UndoResult.NothingToUndo
        if (completion.gameDate != clock.gameDate(profile.dayStartHour)) return@run UndoResult.DayClosed
        val isTrialStage = completion.questType == QuestType.TRIAL
        if (isTrialStage) {
            val trial = trialDao.covering(completion.gameDate)
            if (trial != null && !RankRules.canUndoStage(trial.status)) return@run UndoResult.TrialPassed
        }

        val originals = ledgerDao.bySource(COMPLETION_SOURCE_TYPES, completion.id)
        ledger.insertIdempotent(ledger.reversalsOf(originals, now))
        completionDao.markUndone(completion.id, now)
        val quest = questDao.get(questInstanceId)
        val reopened = if (quest?.startedAt != null) QuestStatus.STARTED else QuestStatus.ACTIVE
        questDao.setStatus(questInstanceId, reopened, completedAt = null)
        // The cleared stage had closed the day's other stages; they are open again.
        if (isTrialStage) questDao.reopenTrialStages(completion.gameDate)
        UndoResult.Undone(completion.id)
    }
}
