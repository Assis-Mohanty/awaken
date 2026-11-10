package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.DeviceUptime
import com.assismohanty.awaken.core.data.db.dao.LockdownDao
import com.assismohanty.awaken.core.data.db.entity.LockdownSessionEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.core.data.usage.OpenedRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.AppKind
import com.assismohanty.awaken.engine.BlockDecision
import com.assismohanty.awaken.engine.GapLog
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.LockdownBreak
import com.assismohanty.awaken.engine.LockdownClock
import com.assismohanty.awaken.engine.LockdownRules
import com.assismohanty.awaken.engine.LockdownStatus
import com.assismohanty.awaken.engine.model.Stat
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** What Android currently allows for Pause and Lockdown. A seam so the domain has no Android classes (GD §19.2 LD9). */
interface LockdownEnvironment {
    fun usageAccess(): Boolean
    fun canAppearOnTop(): Boolean

    /** This phone's launcher, keyboard, dialer, messages and payment apps, and AWAKEN: never covered. */
    fun alwaysAllowed(): Set<String>

    /** A real app (a launcher icon the hunter installed); anything else is the phone's own window and is never covered. */
    fun isCoverable(packageName: String): Boolean
}

sealed interface StartLockdownResult {
    data class Started(val session: LockdownSessionEntity) : StartLockdownResult
    data object NoHunter : StartLockdownResult
    data object InvalidMinutes : StartLockdownResult
    data object TooManyApps : StartLockdownResult

    /** An allowed app is Distracting, social, video or a game (LD7). */
    data class AppNotAllowed(val packageName: String) : StartLockdownResult
    data object NeedsPermissions : StartLockdownResult
    data class InCooldown(val left: Duration) : StartLockdownResult
    data object AlreadyRunning : StartLockdownResult
    data object TooLate : StartLockdownResult
}

/**
 * Starts a Lockdown (GD §19.2): checks the rules, opens its gate run (a CUSTOM run flagged as Lockdown, so the reward,
 * the daily cap and the ledger rows are the existing ones) and its session row in one transaction.
 */
class StartLockdownUseCase @Inject constructor(
    private val startGate: StartGateUseCase,
    private val dao: LockdownDao,
    private val usage: UsageGateway,
    private val distractingApps: DistractingApps,
    private val env: LockdownEnvironment,
    private val transactions: TransactionRunner,
    private val uptime: DeviceUptime,
    private val clock: Clock,
) {
    suspend operator fun invoke(minutes: Int, allowed: Set<String>): StartLockdownResult {
        if (!LockdownRules.isValidMinutes(minutes)) return StartLockdownResult.InvalidMinutes
        if (allowed.size > LockdownRules.MAX_APPS) return StartLockdownResult.TooManyApps
        if (!env.usageAccess() || !env.canAppearOnTop()) return StartLockdownResult.NeedsPermissions
        val kinds = usage.launcherApps().associate { it.packageName to it.kind }
        val distracting = distractingApps.current()
        allowed.firstOrNull { pkg ->
            val category = if (pkg in distracting) AppCategory.DISTRACTING else AppCategory.NEUTRAL
            !LockdownRules.canAllow(category, kinds[pkg] ?: AppKind.OTHER)
        }?.let { return StartLockdownResult.AppNotAllowed(it) }
        val now = clock.now()
        val left = LockdownRules.cooldownLeft(dao.lastBrokenAt(), now)
        if (!left.isZero) return StartLockdownResult.InCooldown(left)
        return transactions.run {
            if (dao.running() != null) return@run StartLockdownResult.AlreadyRunning
            when (val gate = startGate.startFocus(GateMode.CUSTOM, minutes, Stat.INT, label = "Lockdown", subjectId = null, lockdown = true)) {
                is StartGateResult.Started -> {
                    val timing = LockdownRules.start(minutes, uptime.elapsedRealtimeMs(), now, uptime.bootCount())
                    val session = LockdownSessionEntity(
                        id = UUID.randomUUID().toString(),
                        gateRunId = gate.run.id,
                        plannedMs = timing.plannedMs,
                        servedMs = 0,
                        anchorElapsedMs = timing.anchorElapsedMs,
                        anchorWall = now,
                        bootCount = timing.bootCount,
                        allowedPackages = allowed,
                        status = LockdownStatus.RUNNING,
                        heartbeatWall = now,
                        startedAt = now,
                    )
                    dao.upsert(session)
                    StartLockdownResult.Started(session)
                }
                StartGateResult.NoHunter -> StartLockdownResult.NoHunter
                StartGateResult.TooLate -> StartLockdownResult.TooLate
                // Another focus session already runs.
                StartGateResult.AlreadyRunning, StartGateResult.NotATimedHabit -> StartLockdownResult.AlreadyRunning
            }
        }
    }
}

/** What one look at the running Lockdown found. */
sealed interface LockdownTick {
    data object None : LockdownTick
    data class Running(val session: LockdownSessionEntity, val remaining: Duration) : LockdownTick
    data class Completed(val session: LockdownSessionEntity, val outcome: GateCheck) : LockdownTick
    data class Broken(val session: LockdownSessionEntity, val reason: LockdownBreak) : LockdownTick
}

/**
 * Everything that happens to a running Lockdown (GD §19.2): the time (monotonic, never the wall clock), the heartbeat, the
 * permission check, finishing, and breaking. Called by the watcher service and by the session screen; serialised by the
 * gate controller's lock and the single-row session.
 */
@Singleton
class LockdownController @Inject constructor(
    private val dao: LockdownDao,
    private val gates: GateRunController,
    private val usage: UsageGateway,
    private val env: LockdownEnvironment,
    private val transactions: TransactionRunner,
    private val uptime: DeviceUptime,
    private val clock: Clock,
) {
    private var lastBeatElapsedMs: Long? = null
    private var lastPermissionCheckMs: Long? = null

    /** True the first time, after [everyMs], and after a reboot restarted the monotonic clock. */
    private fun due(last: Long?, nowMs: Long, everyMs: Long): Boolean = last == null || nowMs < last || nowMs - last >= everyMs

    suspend fun running(): LockdownSessionEntity? = dao.running()

    fun observeRunning(): Flow<LockdownSessionEntity?> = dao.observeRunning()

    fun observeLatest(): Flow<LockdownSessionEntity?> = dao.observeLatest()

    /** How long until another Lockdown can start (zero when it can). */
    suspend fun cooldownLeft(): Duration = LockdownRules.cooldownLeft(dao.lastBrokenAt(), clock.now())

    fun clockOf(session: LockdownSessionEntity): LockdownClock =
        LockdownClock(session.plannedMs, session.servedMs, session.anchorElapsedMs, session.anchorWall, session.bootCount)

    fun remaining(session: LockdownSessionEntity): Duration = LockdownRules.remaining(clockOf(session), uptime.elapsedRealtimeMs(), uptime.bootCount())

    /**
     * The watcher came (back) up: close any stretch it was down for (LD6). Up to 90 s is tolerated; past that the usage log
     * decides. A reboot starts a new boot, so the clock is re-anchored on the saved total (time with the phone off is zero).
     */
    suspend fun onWatcherStart(): LockdownTick {
        val session = dao.running() ?: return LockdownTick.None
        val now = clock.now()
        val gapMs = Duration.between(session.heartbeatWall, now).toMillis().coerceAtLeast(0)
        val verdict = if (gapMs <= LockdownRules.UNCOVERED_BUDGET_MS) {
            null
        } else {
            LockdownRules.gapVerdict(gapMs, gapLog(session, session.heartbeatWall, now))
        }
        if (verdict != null) return breakSession(session, verdict, gapMs)
        val reanchored = reanchor(session, uncovered = if (gapMs > LockdownRules.UNCOVERED_BUDGET_MS) gapMs else 0)
        return LockdownTick.Running(reanchored, remaining(reanchored))
    }

    /** One look: permissions every 5 s, the heartbeat every 30 s, and completion the moment the full length is served. */
    suspend fun tick(): LockdownTick {
        val session = dao.running() ?: return LockdownTick.None
        val elapsed = uptime.elapsedRealtimeMs()
        val boot = uptime.bootCount()
        if (due(lastPermissionCheckMs, elapsed, LockdownRules.PERMISSION_CHECK_MS)) {
            lastPermissionCheckMs = elapsed
            LockdownRules.permissionVerdict(env.usageAccess(), env.canAppearOnTop())?.let { return breakSession(session, it, 0) }
        }
        if (LockdownRules.isComplete(clockOf(session), elapsed, boot)) return complete(session)
        if (due(lastBeatElapsedMs, elapsed, LockdownRules.HEARTBEAT_MS)) {
            lastBeatElapsedMs = elapsed
            val saved = reanchor(session, uncovered = 0)
            return LockdownTick.Running(saved, remaining(saved))
        }
        return LockdownTick.Running(session, remaining(session))
    }

    /** The emergency exit finished (LD3): the session breaks, with no reward and a cooldown. */
    suspend fun emergencyExit(): LockdownTick {
        val session = dao.running() ?: return LockdownTick.None
        return breakSession(session, LockdownBreak.EMERGENCY_EXIT, 0)
    }

    private suspend fun complete(session: LockdownSessionEntity): LockdownTick = transactions.run {
        val outcome = gates.finishLockdown()
        val done = session.copy(servedMs = session.plannedMs, status = LockdownStatus.COMPLETED, endedAt = clock.now(), heartbeatWall = clock.now())
        dao.upsert(done)
        LockdownTick.Completed(done, outcome)
    }

    private suspend fun breakSession(session: LockdownSessionEntity, reason: LockdownBreak, uncovered: Long): LockdownTick = transactions.run {
        gates.breakLockdown()
        val now = clock.now()
        val broken = session.copy(
            servedMs = LockdownRules.served(clockOf(session), uptime.elapsedRealtimeMs(), uptime.bootCount()),
            status = LockdownStatus.BROKEN,
            brokenReason = reason,
            uncoveredMs = session.uncoveredMs + uncovered,
            endedAt = now,
            heartbeatWall = now,
        )
        dao.upsert(broken)
        LockdownTick.Broken(broken, reason)
    }

    private suspend fun reanchor(session: LockdownSessionEntity, uncovered: Long): LockdownSessionEntity {
        val elapsed = uptime.elapsedRealtimeMs()
        val boot = uptime.bootCount()
        val now = clock.now()
        val clock0 = clockOf(session)
        val next = LockdownRules.reanchor(clock0, elapsed, now, boot)
        val saved = session.copy(
            servedMs = next.servedMs,
            anchorElapsedMs = next.anchorElapsedMs,
            anchorWall = next.anchorWall,
            bootCount = next.bootCount,
            heartbeatWall = now,
            clockChanged = session.clockChanged || LockdownRules.clockChanged(clock0, elapsed, now, boot),
            uncoveredMs = session.uncoveredMs + uncovered,
        )
        dao.upsert(saved)
        return saved
    }

    /** What the usage log showed while the watcher was down: only allowed apps, another app, or nothing readable (fail closed). */
    private suspend fun gapLog(session: LockdownSessionEntity, from: Instant, to: Instant): GapLog {
        val read = usage.opened(from, to) as? OpenedRead.Opened ?: return GapLog.UNREADABLE
        val always = env.alwaysAllowed()
        val others = read.packages.any {
            LockdownRules.decide(it, env.isCoverable(it), session.allowedPackages, always) == BlockDecision.BLOCK
        }
        return if (others) GapLog.SAW_OTHER_APP else GapLog.ONLY_ALLOWED
    }
}
