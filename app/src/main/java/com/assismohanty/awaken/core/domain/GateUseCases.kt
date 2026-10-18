package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.DeviceUptime
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.BackgroundDelta
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.GatePhase
import com.assismohanty.awaken.engine.GateRules
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface StartGateResult {
    data class Started(val run: GateRunEntity) : StartGateResult
    data object NoHunter : StartGateResult
    data object AlreadyRunning : StartGateResult
    /** The gate could not end, with a full pause, before the day resets (ENGINE_DECISIONS #35). */
    data object TooLate : StartGateResult
}

/** What a check of the running gate found. */
sealed interface GateCheck {
    data object NoRun : GateCheck
    data class Running(val run: GateRunEntity) : GateCheck
    data class Cleared(val outcome: CompletionOutcome) : GateCheck
    data class Collapsed(val run: GateRunEntity) : GateCheck
}

/** Stats a gate can train (GD §8: INT default, or WIL/CHA/STR for practice sessions). */
val GATE_STATS: List<Stat> = listOf(Stat.INT, Stat.WIL, Stat.CHA, Stat.STR)

const val GATE_LABEL_MAX = 40

/** Opens a Focus Gate: a STARTED TIMER quest plus its RUNNING run, in one transaction (GD §8, ARCH §3.3). */
class StartGateUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val gateRunDao: GateRunDao,
    private val transactions: TransactionRunner,
    private val uptime: DeviceUptime,
    private val clock: Clock,
) {
    suspend operator fun invoke(rank: GateRank, stat: Stat, label: String?): StartGateResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run StartGateResult.NoHunter
        if (gateRunDao.running() != null) return@run StartGateResult.AlreadyRunning
        val now = clock.now()
        val today = clock.gameDate(profile.dayStartHour)
        val nextDayStart = GameDay.endOf(today, clock.zone(), profile.dayStartHour)
        if (!GateRunMath.canStart(rank, now, nextDayStart)) return@run StartGateResult.TooLate

        val cleanLabel = label?.trim()?.take(GATE_LABEL_MAX)?.takeIf { it.isNotEmpty() }
        val plannedSec = GateRunMath.planned(rank).seconds.toInt()
        val runId = UUID.randomUUID().toString()
        val quest = QuestInstanceEntity(
            id = "gate:$runId",
            gameDate = today,
            type = QuestType.GATE,
            templateId = null,
            habitId = null,
            stat = stat,
            secondaryStat = null,
            title = cleanLabel ?: "${rank.name}-Gate",
            description = "",
            unit = "min",
            target = rank.durationMinutes,
            difficulty = rank.difficulty,
            verification = Verification.TIMER,
            minDurationSec = plannedSec,
            status = QuestStatus.STARTED,
            startedAt = now,
            createdAt = now,
        )
        val run = GateRunEntity(
            id = runId,
            questInstanceId = quest.id,
            gateRank = rank,
            stat = stat,
            label = cleanLabel,
            plannedSec = plannedSec,
            startedAt = now,
            bootCount = uptime.bootCount(),
            status = GateRunStatus.RUNNING,
        )
        questDao.insertAll(listOf(quest))
        gateRunDao.upsert(run)
        StartGateResult.Started(run)
    }
}

/**
 * Everything that happens to a running gate after it opens: pause, abandon, background accounting on app stop/start,
 * and the claim (ARCH §3.3). Calls are serialized, so a lifecycle start and the screen's tick can't double-claim.
 */
@Singleton
class GateRunController @Inject constructor(
    private val questDao: QuestDao,
    private val gateRunDao: GateRunDao,
    private val completeQuest: CompleteQuestUseCase,
    private val transactions: TransactionRunner,
    private val uptime: DeviceUptime,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Uses the run's one pause (GD §8). Returns false if it can't pause now. */
    suspend fun pause(): Boolean = mutex.withLock {
        transactions.run {
            val run = gateRunDao.running() ?: return@run false
            val now = clock.now()
            val pausesUsed = if (run.pausedAt == null) 0 else 1
            val canPause = GateRules.canPause(pausesUsed) && GateRunMath.phase(run.timing, now) is GatePhase.Running
            if (canPause) gateRunDao.upsert(run.copy(pausedAt = now))
            canPause
        }
    }

    suspend fun resume(): Boolean = mutex.withLock {
        transactions.run {
            val run = gateRunDao.running() ?: return@run false
            val now = clock.now()
            val paused = GateRunMath.isPaused(run.timing, now)
            if (paused) gateRunDao.upsert(run.copy(resumedAt = now))
            paused
        }
    }

    suspend fun abandon() {
        mutex.withLock {
            transactions.run {
                gateRunDao.running()?.let { close(it, GateRunStatus.ABANDONED, reason = null) }
            }
        }
    }

    /** The app left the foreground: remember when, in wall and elapsed time (survives process death). */
    suspend fun onAppStopped(): GateRunEntity? = mutex.withLock {
        transactions.run {
            val run = gateRunDao.running() ?: return@run null
            if (run.lastStopAt != null) return@run run
            val stopped = run.copy(lastStopAt = clock.now(), lastStopElapsedMs = uptime.elapsedRealtimeMs())
            gateRunDao.upsert(stopped)
            stopped
        }
    }

    /**
     * The app is in the foreground (process start, return from background, or a screen tick): account any background
     * stretch, then collapse, claim or keep running.
     */
    suspend fun check(): GateCheck = mutex.withLock {
        transactions.run {
            var run = gateRunDao.running() ?: return@run GateCheck.NoRun
            val now = clock.now()
            val stopAt = run.lastStopAt
            val stopElapsed = run.lastStopElapsedMs
            if (stopAt != null && stopElapsed != null) {
                val nowElapsed = uptime.elapsedRealtimeMs()
                // A lower reading than at the stop also means elapsed time restarted.
                val sameBoot = uptime.bootCount() == run.bootCount && nowElapsed >= stopElapsed
                when (val delta = GateRunMath.backgroundOnResume(run.timing, stopAt, stopElapsed, nowElapsed, sameBoot)) {
                    BackgroundDelta.Unknowable -> return@run close(run, GateRunStatus.COLLAPSED, GateCollapseReason.DEVICE_RESTARTED)
                    is BackgroundDelta.Counted -> {
                        run = run.copy(backgroundMs = run.backgroundMs + delta.addedMs, lastStopAt = null, lastStopElapsedMs = null)
                        gateRunDao.upsert(run)
                    }
                }
            }
            if (GateRules.isCollapsed(run.backgroundMs)) return@run close(run, GateRunStatus.COLLAPSED, GateCollapseReason.BREACH)
            if (GateRunMath.phase(run.timing, now) != GatePhase.Finished) return@run GateCheck.Running(run)
            claim(run)
        }
    }

    private suspend fun claim(run: GateRunEntity): GateCheck =
        when (val result = completeQuest.completeVerified(run.questInstanceId, VerificationSource.TIMER)) {
            is CompleteQuestResult.Completed -> {
                gateRunDao.upsert(run.copy(status = GateRunStatus.CLEARED, endedAt = clock.now()))
                GateCheck.Cleared(result.outcome)
            }
            // The quest's game-day closed before the claim (rollover expired it): no XP (ENGINE_DECISIONS #35).
            else -> close(run, GateRunStatus.COLLAPSED, GateCollapseReason.DAY_RESET)
        }

    private suspend fun close(run: GateRunEntity, status: GateRunStatus, reason: GateCollapseReason?): GateCheck {
        val closed = run.copy(status = status, endedAt = clock.now(), collapseReason = reason, lastStopAt = null, lastStopElapsedMs = null)
        gateRunDao.upsert(closed)
        val quest = questDao.get(run.questInstanceId)
        if (quest != null && (quest.status == QuestStatus.STARTED || quest.status == QuestStatus.ACTIVE)) {
            questDao.setStatus(quest.id, QuestStatus.EXPIRED, completedAt = null)
        }
        return GateCheck.Collapsed(closed)
    }
}
