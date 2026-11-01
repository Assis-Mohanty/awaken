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
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.usage.OpenedRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.engine.FocusLockRules
import kotlinx.coroutines.flow.first
import com.assismohanty.awaken.engine.BackgroundDelta
import com.assismohanty.awaken.engine.FocusRules
import com.assismohanty.awaken.engine.FocusRunOutcome
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.GatePhase
import com.assismohanty.awaken.engine.GateRules
import com.assismohanty.awaken.engine.GateTiming
import java.time.Instant
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.Duration
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
    /** A custom or stopwatch run that was recorded but not paid: under the minimum, or over the daily cap (GD §8.1). */
    data class Logged(val run: GateRunEntity, val outcome: FocusRunOutcome) : GateCheck
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
    /** A preset E/D/C/B gate. */
    suspend operator fun invoke(rank: GateRank, stat: Stat, label: String?): StartGateResult =
        open(rank, GateMode.PRESET, rank.durationMinutes, stat, label, subjectId = null)

    /** A custom length (GD §8.1 P1) or an open-ended stopwatch. [minutes] is ignored for the stopwatch. */
    suspend fun startFocus(mode: GateMode, minutes: Int, stat: Stat, label: String?, subjectId: String?): StartGateResult {
        require(mode != GateMode.PRESET) { "Presets start through invoke(rank, stat, label)" }
        val planned = if (mode == GateMode.CUSTOM) FocusRules.clampCustomMinutes(minutes) else FocusRules.STOPWATCH_MIN_MINUTES
        // The quest opens at the lowest tier; a stopwatch is repriced when it stops, a custom run keeps its tier.
        val rank = FocusRules.tierFor(planned) ?: GateRank.E
        return open(rank, mode, planned, stat, label, subjectId)
    }

    private suspend fun open(rank: GateRank, mode: GateMode, minutes: Int, stat: Stat, label: String?, subjectId: String?): StartGateResult =
        transactions.run {
            val profile = hunterDao.getProfile() ?: return@run StartGateResult.NoHunter
            if (gateRunDao.running() != null) return@run StartGateResult.AlreadyRunning
            val now = clock.now()
            val today = clock.gameDate(profile.dayStartHour)
            val nextDayStart = GameDay.endOf(today, clock.zone(), profile.dayStartHour)
            if (!GateRunMath.canStart(Duration.ofMinutes(minutes.toLong()), now, nextDayStart)) return@run StartGateResult.TooLate

            val cleanLabel = label?.trim()?.take(GATE_LABEL_MAX)?.takeIf { it.isNotEmpty() }
            val plannedSec = if (mode == GateMode.STOPWATCH) FocusRules.STOPWATCH_PLACEHOLDER_SEC else minutes * 60
            val runId = UUID.randomUUID().toString()
            val quest = QuestInstanceEntity(
                id = "gate:$runId",
                gameDate = today,
                type = QuestType.GATE,
                templateId = null,
                habitId = null,
                stat = stat,
                secondaryStat = null,
                title = cleanLabel ?: if (mode == GateMode.STOPWATCH) "Stopwatch" else "${rank.name}-Gate",
                description = "",
                unit = "min",
                target = minutes,
                difficulty = rank.difficulty,
                verification = Verification.TIMER,
                minDurationSec = if (mode == GateMode.STOPWATCH) 0 else plannedSec,
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
                mode = mode,
                subjectId = subjectId,
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
    private val focusDao: FocusDao,
    private val completeQuest: CompleteQuestUseCase,
    private val transactions: TransactionRunner,
    private val uptime: DeviceUptime,
    private val clock: Clock,
    private val focusLock: FocusLockStatus,
    private val usage: UsageGateway,
    private val distractingApps: DistractingApps,
) {
    private val mutex = Mutex()

    /** Uses the run's one pause (GD §8). Returns false if it can't pause now. */
    suspend fun pause(): Boolean = mutex.withLock {
        transactions.run {
            val run = gateRunDao.running() ?: return@run false
            val now = clock.now()
            // A stopwatch has no pause limit: each resume folds its pause into the total, so the next tap can pause again.
            val pausesUsed = if (run.pausedAt == null || run.mode == GateMode.STOPWATCH) 0 else 1
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
            if (paused && run.mode == GateMode.STOPWATCH) {
                val folded = run.timing.pausedBeforeMs + Duration.between(run.pausedAt, now).toMillis().coerceAtLeast(0)
                gateRunDao.upsert(run.copy(pausedAt = null, resumedAt = null, pausedBeforeMs = folded))
            } else if (paused) {
                gateRunDao.upsert(run.copy(resumedAt = now))
            }
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

    /** Focus Lock: the hunter ended the running session from the cover. It collapses, with no reward (GD 8.2). */
    suspend fun endEarly(): GateCheck = mutex.withLock {
        transactions.run {
            val run = gateRunDao.running() ?: return@run GateCheck.NoRun
            close(run, GateRunStatus.COLLAPSED, GateCollapseReason.ENDED_EARLY)
        }
    }

    /** The app left the foreground: remember when, in wall and elapsed time (survives process death). */
    suspend fun onAppStopped(): GateRunEntity? = mutex.withLock {
        transactions.run {
            val run = gateRunDao.running() ?: return@run null
            if (run.lastStopAt != null) return@run run
            val stopped = run.copy(lastStopAt = clock.now(), lastStopElapsedMs = uptime.elapsedRealtimeMs(), lockAtStop = focusLock.isActive())
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
                        // Focus Lock covered the whole stretch (it was on at the stop and still is): time away is not a breach (GD 8.2).
                        val covered = run.lockAtStop && focusLock.isActive()
                        val added = if (covered) 0L else delta.addedMs
                        val timing = run.timing
                        run = run.copy(backgroundMs = run.backgroundMs + added, lastStopAt = null, lastStopElapsedMs = null, lockAtStop = false)
                        gateRunDao.upsert(run)
                        if (!covered && !GateRules.isCollapsed(run.backgroundMs) && openedDistraction(timing, stopAt, now)) {
                            return@run close(run, GateRunStatus.COLLAPSED, GateCollapseReason.DISTRACTING_APP)
                        }
                    }
                }
            }
            if (GateRules.isCollapsed(run.backgroundMs)) return@run close(run, GateRunStatus.COLLAPSED, GateCollapseReason.BREACH)
            if (run.mode == GateMode.STOPWATCH) {
                // A stopwatch ends only when the hunter stops it; if its game-day closed first, it collapses.
                val quest = questDao.get(run.questInstanceId)
                return@run if (quest?.status == QuestStatus.STARTED) GateCheck.Running(run) else close(run, GateRunStatus.COLLAPSED, GateCollapseReason.DAY_RESET)
            }
            if (GateRunMath.phase(run.timing, now) != GatePhase.Finished) return@run GateCheck.Running(run)
            claim(run)
        }
    }

    /**
     * The fallback when Focus Lock did not cover a stretch away (GD 8.2): true if a Distracting app came to the front
     * during it. Fails closed: only a successful usage read can collapse a gate. The part of the stretch inside the
     * pause, and after a timed gate's planned end, is not looked at.
     */
    private suspend fun openedDistraction(timing: GateTiming, stopAt: Instant, now: Instant): Boolean {
        var from = stopAt
        val pausedAt = timing.pausedAt
        val pauseEnd = GateRunMath.pauseEndsAt(timing)
        if (pausedAt != null && pauseEnd != null && !stopAt.isBefore(pausedAt) && stopAt.isBefore(pauseEnd)) from = pauseEnd
        val to = if (timing.pauseCap == null) now else minOf(now, GateRunMath.endAt(timing, now))
        if (!to.isAfter(from)) return false
        val read = usage.opened(from, to) as? OpenedRead.Opened ?: return false
        return FocusLockRules.distractedDuring(read.packages, distractingApps.current(), focusLock.alwaysAllowed())
    }

    /** The hunter stops the stopwatch: it is priced by the time it counted (GD §8.1 P2–P3). */
    suspend fun stopStopwatch(): GateCheck = mutex.withLock {
        transactions.run {
            val run = gateRunDao.running()?.takeIf { it.mode == GateMode.STOPWATCH } ?: return@run GateCheck.NoRun
            val seconds = GateRunMath.elapsed(run.timing, clock.now()).seconds
            claim(run.copy(plannedSec = seconds.toInt()))
        }
    }

    private suspend fun claim(run: GateRunEntity): GateCheck {
        if (run.mode == GateMode.PRESET) return claimPaid(run, fragment = true)
        val quest = questDao.get(run.questInstanceId)
        val today = quest?.gameDate
        val paidToday = if (today == null) 0 else focusDao.paidCustomRuns(today)
        return when (val outcome = FocusRules.outcome(run.plannedSec / 60, paidToday)) {
            is FocusRunOutcome.Paid -> {
                // A stopwatch is priced now; a custom run keeps the tier it opened with (the same function of its length).
                questDao.setGateTier(run.questInstanceId, outcome.rank.difficulty, run.plannedSec / 60)
                claimPaid(run.copy(gateRank = outcome.rank), outcome.fragment)
            }
            FocusRunOutcome.ShortRun, FocusRunOutcome.OverCap -> {
                val logged = run.copy(status = GateRunStatus.LOGGED, endedAt = clock.now(), lastStopAt = null, lastStopElapsedMs = null)
                gateRunDao.upsert(logged)
                if (quest != null) questDao.setStatus(quest.id, QuestStatus.EXPIRED, completedAt = null)
                GateCheck.Logged(logged, outcome)
            }
        }
    }

    private suspend fun claimPaid(run: GateRunEntity, fragment: Boolean): GateCheck =
        when (val result = completeQuest.completeVerified(run.questInstanceId, VerificationSource.TIMER, gateFragment = fragment)) {
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
        if (quest?.pathSlotId != null) {
            // A Path quest outlives its gate attempt: leaving or collapsing reopens it for another go today.
            questDao.reopen(quest.id)
        } else if (quest != null && (quest.status == QuestStatus.STARTED || quest.status == QuestStatus.ACTIVE)) {
            questDao.setStatus(quest.id, QuestStatus.EXPIRED, completedAt = null)
        }
        return GateCheck.Collapsed(closed)
    }
}
