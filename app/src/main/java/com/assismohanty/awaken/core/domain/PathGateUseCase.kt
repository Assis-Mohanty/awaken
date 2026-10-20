package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.DeviceUptime
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.VerificationSource
import java.util.UUID
import javax.inject.Inject

sealed interface StartPathGateResult {
    data class Started(val run: GateRunEntity) : StartPathGateResult
    data object NoHunter : StartPathGateResult
    /** Not an open GATE-mode Path quest of today. */
    data object NotAGateQuest : StartPathGateResult
    data object AlreadyRunning : StartPathGateResult
    /** The gate could not end, with a full pause, before the day resets (ENGINE_DECISIONS #35). */
    data object TooLate : StartPathGateResult
}

/**
 * [Enter C-Gate · 50 min] on a GATE-mode Path quest (GD §6.6.3-6.6.4, DS §4.3). The run is linked to that very quest, with
 * the rank the engine priced it at and the quest's own stat, so clearing it goes through the normal verified completion:
 * exactly one reward, paid as the Path quest (vector 33), and no separate gate reward. If the gate collapses or is left,
 * the quest stays open ([GateRunController]).
 */
class StartPathGateUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val pathDao: PathDao,
    private val gateRunDao: GateRunDao,
    private val transactions: TransactionRunner,
    private val uptime: DeviceUptime,
    private val clock: Clock,
) {
    suspend operator fun invoke(questInstanceId: String): StartPathGateResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run StartPathGateResult.NoHunter
        val quest = questDao.get(questInstanceId) ?: return@run StartPathGateResult.NotAGateQuest
        val today = clock.gameDate(profile.dayStartHour)
        val slot = quest.pathSlotId?.let { pathDao.slot(it) }
        val rank = slot?.gateRank
        val open = quest.status == QuestStatus.ACTIVE || quest.status == QuestStatus.STARTED
        if (rank == null || !open || quest.gameDate != today || quest.verification.source != VerificationSource.TIMER) {
            return@run StartPathGateResult.NotAGateQuest
        }
        if (gateRunDao.running() != null) return@run StartPathGateResult.AlreadyRunning
        val now = clock.now()
        if (!GateRunMath.canStart(rank, now, GameDay.endOf(today, clock.zone(), profile.dayStartHour))) {
            return@run StartPathGateResult.TooLate
        }

        val run = GateRunEntity(
            id = UUID.randomUUID().toString(),
            questInstanceId = quest.id,
            gateRank = rank,
            stat = quest.stat,
            label = quest.title.take(GATE_LABEL_MAX),
            plannedSec = GateRunMath.planned(rank).seconds.toInt(),
            startedAt = now,
            bootCount = uptime.bootCount(),
            status = GateRunStatus.RUNNING,
        )
        questDao.markStarted(quest.id, now)
        gateRunDao.upsert(run)
        StartPathGateResult.Started(run)
    }
}
