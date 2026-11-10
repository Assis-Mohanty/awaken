package com.assismohanty.awaken.core.protect

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PauseDao
import com.assismohanty.awaken.core.data.db.entity.PauseEventEntity
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.core.data.repo.PauseStore
import com.assismohanty.awaken.core.data.usage.ForegroundRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.PauseAppState
import com.assismohanty.awaken.engine.PauseDecision
import com.assismohanty.awaken.engine.PauseOutcome
import com.assismohanty.awaken.engine.PausePlan
import com.assismohanty.awaken.engine.PauseRules
import com.assismohanty.awaken.engine.PauseVariant
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What the pause screen needs to know; the service turns it into words, an icon and a theme. */
data class PauseRequest(
    val packageName: String,
    val plan: PausePlan,
    /** "Opened N times today": this open included. */
    val opensToday: Int,
    val requireTap: Boolean,
)

/** The pause screen as the watcher sees it. The service draws it; a test records the calls. */
interface PauseScreen {
    fun show(request: PauseRequest)
    fun hide()

    /** Not now: leave the app for the home screen. */
    fun goHome()
}

/** Whether something else (a Focus Gate with Focus Lock) already covers an app, so Pause stays out of its way. */
fun interface LockCoverage {
    suspend fun covers(packageName: String, now: Instant): Boolean
}

/** The apps and windows the watcher steps around: this phone's keyboard, launcher, dialer, payment apps and AWAKEN. */
interface ProtectApps {
    fun alwaysAllowed(): Set<String>

    /** Windows over an app that are not a new app: [PauseRules.TRANSIENT_WINDOWS] plus this phone's keyboard and AWAKEN. */
    fun transientWindows(): Set<String>
}

sealed interface PollResult {
    /** [changed]: a different app came to the front since the last look, so the service looks faster for a while. */
    data class Ok(val changed: Boolean) : PollResult

    /** Usage access is gone: the watcher can see nothing, so the service stops and asks to be set up again. */
    data object NoAccess : PollResult
}

/**
 * The Pause decision loop body (GD §19.1), without any Android window code. One [poll] looks at the event log once,
 * decides with [PauseRules] and tells the [PauseScreen]. The service owns the loop, the screen state and the timing.
 *
 * Not thread-safe: the service calls everything from one coroutine scope on one thread.
 */
@Singleton
class PauseWatcher @Inject constructor(
    private val usage: UsageGateway,
    private val store: PauseStore,
    private val dao: PauseDao,
    private val hunterDao: HunterDao,
    private val clock: Clock,
    private val apps: ProtectApps,
    private val coverage: LockCoverage,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private var top: String? = null
    private var previous: String? = null
    private var lastQuery: Instant? = null
    private var seenUntil: Instant? = null
    private var showing: PauseRequest? = null
    private var intention: PauseRules.Intention? = null
    private var prunedFor: LocalDate? = null

    val isShowing: Boolean get() = showing != null

    /** Looks once. The first look only covers the last few seconds, so a service start does not replay old opens. */
    suspend fun poll(screen: PauseScreen): PollResult {
        val now = clock.now()
        val since = lastQuery?.minusMillis(OVERLAP_MS) ?: now.minusMillis(FIRST_WINDOW_MS)
        lastQuery = now
        return when (val read = usage.foreground(since, now)) {
            ForegroundRead.NoAccess -> PollResult.NoAccess
            ForegroundRead.Unavailable, ForegroundRead.NoChange -> PollResult.Ok(changed = false)
            is ForegroundRead.Resumed -> {
                var changed = false
                // The window overlaps the last look a little, so skip what was already handled; take the rest in order.
                for (app in read.apps) {
                    if (seenUntil?.let { !app.at.isAfter(it) } == true) continue
                    seenUntil = app.at
                    if (app.packageName != top) changed = true
                    onTop(app.packageName, now, screen)
                }
                PollResult.Ok(changed)
            }
        }
    }

    /** Screen off or locked: whatever was open is over, and the next app to show is a fresh open. */
    fun onScreenOff(screen: PauseScreen) {
        top = null
        previous = null
        lastQuery = null
        dismiss(screen)
    }

    /** A call began or another cover took over: take the pause away without recording anything. */
    fun dismiss(screen: PauseScreen) {
        if (showing == null) return
        showing = null
        intention = null
        screen.hide()
    }

    fun onIntention(chosen: PauseRules.Intention) {
        if (showing != null) intention = chosen
    }

    /** The countdown ended (or the hunter tapped Continue): the app opens, and its cooldown starts. */
    suspend fun onContinue(screen: PauseScreen) = finish(PauseOutcome.CONTINUED, screen)

    /** Not now: recorded, no cooldown, and the hunter goes home. */
    suspend fun onNotNow(screen: PauseScreen) {
        if (finish(PauseOutcome.NOT_NOW, screen)) screen.goHome()
    }

    private suspend fun finish(outcome: PauseOutcome, screen: PauseScreen): Boolean {
        val request = showing ?: return false
        val chosen = intention
        showing = null
        intention = null
        screen.hide()
        val now = clock.now()
        val seconds = request.plan.variants.filterIsInstance<PauseVariant.Breathing>().firstOrNull()?.seconds ?: 0
        val day = gameDay()
        withContext(io) {
            dao.insert(PauseEventEntity(packageName = request.packageName, gameDay = day, occurredAt = now, outcome = outcome, delaySeconds = seconds, intention = chosen))
        }
        return true
    }

    private suspend fun onTop(pkg: String, now: Instant, screen: PauseScreen) {
        if (pkg == top) return
        top = pkg
        val transient = apps.transientWindows()
        // A keyboard or permission dialog over the paused app is not leaving it; any other app is.
        if (pkg !in transient) showing?.let { if (it.packageName != pkg) dismiss(screen) }
        val settings = store.current()
        val config = settings.config
        val target = pkg in config.targets
        val day = gameDay()
        val state = if (target) {
            withContext(io) { PauseAppState(pausesToday = dao.countOn(pkg, day), lastContinuedAt = dao.lastContinued(pkg)) }
        } else {
            PauseAppState()
        }
        val decision = PauseRules.decide(
            packageName = pkg,
            previousPackage = previous,
            now = now,
            zone = clock.zone(),
            gameDay = day,
            config = config,
            state = state,
            alwaysAllowed = apps.alwaysAllowed(),
            lockdownRunning = false,
            lockCovering = target && coverage.covers(pkg, now),
        )
        previous = PauseRules.trackPrevious(previous, pkg, transient)
        if (decision is PauseDecision.Show && showing == null) {
            val request = PauseRequest(pkg, decision.plan, decision.plan.n, settings.requireTap)
            showing = request
            intention = null
            screen.show(request)
        }
        prune(day)
    }

    /** Rows older than eight game-days are deleted, once per game-day (PZ9). */
    private suspend fun prune(today: LocalDate) {
        if (prunedFor == today) return
        prunedFor = today
        withContext(io) { dao.deleteBefore(today.minusDays(PauseRules.KEEP_DAYS.toLong())) }
    }

    private suspend fun gameDay(): LocalDate {
        val startHour = withContext(io) { hunterDao.getProfile()?.dayStartHour } ?: GameDay.DEFAULT_DAY_START_HOUR
        return clock.gameDate(startHour)
    }

    private companion object {
        const val OVERLAP_MS = 1_000L
        const val FIRST_WINDOW_MS = 3_000L
    }
}
