package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.PomodoroDao
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.core.data.db.entity.PomodoroSessionEntity
import com.assismohanty.awaken.core.data.repo.FocusKind
import com.assismohanty.awaken.core.data.repo.LastFocus
import com.assismohanty.awaken.core.data.repo.LastFocusStore
import com.assismohanty.awaken.engine.PomodoroConfig
import com.assismohanty.awaken.engine.PomodoroPhase
import com.assismohanty.awaken.engine.PomodoroRules
import com.assismohanty.awaken.engine.PomodoroStatus
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Starts and ends the focus rounds of a Pomodoro session. Implemented by the gate coordinator, which also arms the gate's end. */
interface FocusRoundPort {
    /** Starts focus round [round] of [sessionId] as an ordinary custom gate run of [config]'s focus length. */
    suspend fun startRound(config: PomodoroConfig, stat: Stat, subjectId: String?, sessionId: String, round: Int): StartGateResult

    /** Ends the running round early (it collapses, no reward). */
    suspend fun endRunningRound()
}

/** Arms the "break over" notification. The WorkManager implementation lives in `core/work`. */
interface BreakAlarm {
    suspend fun schedule(at: Instant)

    suspend fun cancel()
}

sealed interface PomodoroStartResult {
    data class Started(val session: PomodoroSessionEntity) : PomodoroStartResult
    data object AlreadyRunning : PomodoroStartResult
    data object TooLate : PomodoroStartResult
    data object NoHunter : PomodoroStartResult

    /** The break is not over yet, so the next round cannot start (and a skipped break counts as over). */
    data object NotReady : PomodoroStartResult
}

/** What the break-over notification says (GD §16.3): the next round to start, or that the session is complete. */
data class BreakAlert(val nextRound: Int?, val rounds: Int)

/** The session's settings as a validated config. */
val PomodoroSessionEntity.config: PomodoroConfig
    get() = PomodoroRules.safeConfig(focusMin, shortBreakMin, longBreakMin, rounds)

/** True while a break has run out but the hunter has not started the next round (or finished) yet. */
fun PomodoroSessionEntity.breakOver(now: Instant): Boolean =
    (phase == PomodoroPhase.SHORT_BREAK || phase == PomodoroPhase.LONG_BREAK) && phaseEndsAt?.let { !now.isBefore(it) } == true

/**
 * Pomodoro sessions (GD §16.3, approved 2026-11-02). Every focus round is an ordinary custom gate run, so rewards, the
 * custom-run cap and the breach rule are exactly those of any custom gate. Breaks are not gates: nothing collapses
 * during one. The next round starts on a tap, never by itself (Q3), because a round that began while the hunter was
 * away would spend its breach budget. A collapsed round ends the session; rounds already cleared keep their rewards.
 *
 * Every transition is derived from the round's gate run status and is idempotent, so [sync] is safe to call from anywhere.
 */
@Singleton
class PomodoroSessions @Inject constructor(
    private val dao: PomodoroDao,
    private val rounds: Provider<FocusRoundPort>,
    private val alarm: BreakAlarm,
    private val lastFocus: LastFocusStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    fun observe(): Flow<PomodoroSessionEntity?> = dao.observeRunning()

    suspend fun running(): PomodoroSessionEntity? = dao.running()

    suspend fun start(config: PomodoroConfig, stat: Stat, subjectId: String?): PomodoroStartResult = mutex.withLock {
        if (dao.running() != null) return@withLock PomodoroStartResult.AlreadyRunning
        val now = clock.now()
        val session = PomodoroSessionEntity(
            id = UUID.randomUUID().toString(),
            focusMin = config.focusMinutes,
            shortBreakMin = config.shortBreakMinutes,
            longBreakMin = config.longBreakMinutes,
            rounds = config.rounds,
            stat = stat,
            subjectId = subjectId,
            startedAt = now,
            phase = PomodoroPhase.FOCUS,
            currentRound = 1,
            status = PomodoroStatus.RUNNING,
        )
        dao.upsert(session)
        when (rounds.get().startRound(config, stat, subjectId, session.id, round = 1)) {
            is StartGateResult.Started -> {
                lastFocus.save(LastFocus(FocusKind.POMODORO, GateRank.D, config.focusMinutes, stat, subjectId, label = "", pomodoro = config))
                PomodoroStartResult.Started(session)
            }
            StartGateResult.AlreadyRunning -> failed(session, now, PomodoroStartResult.AlreadyRunning)
            StartGateResult.TooLate -> failed(session, now, PomodoroStartResult.TooLate)
            StartGateResult.NoHunter, StartGateResult.NotATimedHabit -> failed(session, now, PomodoroStartResult.NoHunter)
        }
    }

    private suspend fun failed(session: PomodoroSessionEntity, now: Instant, result: PomodoroStartResult): PomodoroStartResult {
        dao.upsert(session.copy(status = PomodoroStatus.ENDED, endedAt = now))
        return result
    }

    /**
     * Moves the session on from what its round's run did: a cleared (or logged) round starts the break; a collapsed
     * round ends the session as COLLAPSED; an abandoned one as ENDED; a finished long break becomes DONE.
     */
    suspend fun sync(): Unit = mutex.withLock {
        val session = dao.running() ?: return@withLock
        val now = clock.now()
        when (session.phase) {
            PomodoroPhase.FOCUS -> {
                val run = dao.roundRun(session.id, session.currentRound)
                when (run?.status) {
                    GateRunStatus.CLEARED, GateRunStatus.LOGGED -> enterBreak(session, run.endedAt ?: now)
                    GateRunStatus.COLLAPSED -> close(session, PomodoroStatus.COLLAPSED, now)
                    GateRunStatus.ABANDONED -> close(session, PomodoroStatus.ENDED, now)
                    GateRunStatus.RUNNING, null -> Unit
                }
            }
            PomodoroPhase.LONG_BREAK ->
                if (session.breakOver(now)) dao.upsert(session.copy(phase = PomodoroPhase.DONE, phaseEndsAt = null))
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.DONE -> Unit
        }
    }

    private suspend fun enterBreak(session: PomodoroSessionEntity, roundEndedAt: Instant) {
        val phase = PomodoroRules.breakAfterFocus(session.currentRound, session.rounds)
        val endsAt = roundEndedAt.plus(Duration.ofMinutes(PomodoroRules.breakMinutes(phase, session.config).toLong()))
        dao.upsert(session.copy(phase = phase, phaseEndsAt = endsAt))
        alarm.schedule(endsAt)
    }

    private suspend fun close(session: PomodoroSessionEntity, status: PomodoroStatus, now: Instant) {
        dao.upsert(session.copy(status = status, endedAt = now))
        alarm.cancel()
    }

    /** Skips the rest of the break; the next round still waits for a tap. */
    suspend fun skipBreak() {
        mutex.withLock {
            val session = dao.running() ?: return@withLock
            if (session.phase != PomodoroPhase.SHORT_BREAK && session.phase != PomodoroPhase.LONG_BREAK) return@withLock
            dao.upsert(session.copy(phaseEndsAt = clock.now()))
            alarm.cancel()
        }
        sync()
    }

    /** Starts the next focus round once the short break is over (or was skipped). */
    suspend fun startNextRound(): PomodoroStartResult = mutex.withLock {
        val session = dao.running() ?: return@withLock PomodoroStartResult.NoHunter
        val now = clock.now()
        if (session.phase != PomodoroPhase.SHORT_BREAK || !session.breakOver(now)) return@withLock PomodoroStartResult.NotReady
        val next = PomodoroRules.afterBreak(session.phase, session.currentRound)
        when (rounds.get().startRound(session.config, session.stat, session.subjectId, session.id, next.round)) {
            is StartGateResult.Started -> {
                val running = session.copy(phase = PomodoroPhase.FOCUS, currentRound = next.round, phaseEndsAt = null)
                dao.upsert(running)
                PomodoroStartResult.Started(running)
            }
            StartGateResult.AlreadyRunning -> PomodoroStartResult.AlreadyRunning
            StartGateResult.TooLate -> PomodoroStartResult.TooLate
            StartGateResult.NoHunter, StartGateResult.NotATimedHabit -> PomodoroStartResult.NoHunter
        }
    }

    /** Ends the session now, from a round or a break. A running round collapses and pays nothing; cleared rounds keep theirs. */
    suspend fun end(): Unit = mutex.withLock {
        val session = dao.running() ?: return@withLock
        if (session.phase == PomodoroPhase.FOCUS) rounds.get().endRunningRound()
        close(session, PomodoroStatus.ENDED, clock.now())
    }

    /** Acknowledges a completed session (its finish screen). */
    suspend fun dismissFinished(): Unit = mutex.withLock {
        val session = dao.running() ?: return@withLock
        if (session.phase == PomodoroPhase.DONE) dao.upsert(session.copy(status = PomodoroStatus.FINISHED, endedAt = clock.now()))
    }

    /** At the break's end: what the notification should say, or null when nothing needs saying (ended, restarted, not over). */
    suspend fun breakAlert(): BreakAlert? {
        val session = dao.running() ?: return null
        if (!session.breakOver(clock.now())) return null
        return if (session.phase == PomodoroPhase.SHORT_BREAK) BreakAlert(nextRound = session.currentRound + 1, rounds = session.rounds) else BreakAlert(null, session.rounds)
    }
}
