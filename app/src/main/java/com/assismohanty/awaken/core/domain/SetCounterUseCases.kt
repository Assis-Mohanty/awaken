package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.SetCounter
import javax.inject.Inject

sealed interface SetCounterResult {
    data class Updated(val sets: List<Int>) : SetCounterResult

    /** Not a SELF rep quest, already done, or gone: the counter does nothing. */
    data object NotAvailable : SetCounterResult
}

/**
 * Logs one set on a SELF rep quest (GD §6.5; ENGINE_DECISIONS #84). Progress only: nothing is completed and no ledger row
 * is written, so there is nothing to make idempotent. The read-modify-write is one transaction so two quick taps both land.
 */
class AddSetUseCase @Inject constructor(
    private val questDao: QuestDao,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(questInstanceId: String, reps: Int): SetCounterResult = transactions.run {
        val quest = questDao.get(questInstanceId)
        if (quest == null || !SetCounter.appliesTo(quest.unit, quest.verification)) return@run SetCounterResult.NotAvailable
        val next = SetCounter.add(SetCounter.parse(quest.setReps), reps)
        if (questDao.updateSetReps(questInstanceId, SetCounter.format(next)) == 0) {
            SetCounterResult.NotAvailable
        } else {
            SetCounterResult.Updated(next)
        }
    }
}

/** Removes the last logged set. Undoing a set never touches XP: sets never earned any. */
class UndoSetUseCase @Inject constructor(
    private val questDao: QuestDao,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(questInstanceId: String): SetCounterResult = transactions.run {
        val quest = questDao.get(questInstanceId)
        if (quest == null || !SetCounter.appliesTo(quest.unit, quest.verification)) return@run SetCounterResult.NotAvailable
        val next = SetCounter.undoLast(SetCounter.parse(quest.setReps))
        if (questDao.updateSetReps(questInstanceId, SetCounter.format(next)) == 0) {
            SetCounterResult.NotAvailable
        } else {
            SetCounterResult.Updated(next)
        }
    }
}
