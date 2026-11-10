package com.assismohanty.awaken.core.protect

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.usage.ForegroundRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.domain.LockdownController
import com.assismohanty.awaken.core.domain.LockdownEnvironment
import com.assismohanty.awaken.core.domain.LockdownTick
import com.assismohanty.awaken.engine.BlockDecision
import com.assismohanty.awaken.engine.LockdownRules
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The block screen's content: which app is covered and how long is left (GD §19.2). */
data class LockdownCover(val packageName: String?, val remaining: Duration)

/** The Lockdown block screen as the watcher sees it. The service draws it; a test records the calls. */
interface LockdownScreen {
    fun cover(cover: LockdownCover)
    fun uncover()
}

/**
 * The Lockdown loop body (GD §19.2), without any Android window code. One [poll] checks the session (permissions,
 * heartbeat, completion), reads the usage log, and covers the app in front unless it is allowed. Not thread-safe: the
 * service calls it from one coroutine.
 */
@Singleton
class LockdownWatcher @Inject constructor(
    private val usage: UsageGateway,
    private val controller: LockdownController,
    private val env: LockdownEnvironment,
    private val clock: Clock,
) {
    private var top: String? = null
    private var lastQuery: Instant? = null
    private var seenUntil: Instant? = null
    private var covering = false

    /** The watcher came up: close any stretch it was down for before covering anything (LD6). */
    suspend fun start(screen: LockdownScreen): LockdownTick {
        top = null
        lastQuery = null
        return controller.onWatcherStart().also { if (it !is LockdownTick.Running) uncover(screen) }
    }

    suspend fun poll(screen: LockdownScreen): LockdownTick {
        val tick = controller.tick()
        if (tick !is LockdownTick.Running) {
            uncover(screen)
            return tick
        }
        val now = clock.now()
        // The first look reaches back far enough to find the app that is in front right now.
        val since = lastQuery?.minusMillis(OVERLAP_MS) ?: now.minusMillis(FIRST_WINDOW_MS)
        lastQuery = now
        val read = usage.foreground(since, now)
        if (read is ForegroundRead.Resumed) {
            val newest = read.apps.lastOrNull { app -> seenUntil?.let { app.at.isAfter(it) } != false }
            if (newest != null) {
                seenUntil = newest.at
                top = newest.packageName
            }
        }
        val pkg = top
        val decision = LockdownRules.decide(pkg, pkg != null && env.isCoverable(pkg), tick.session.allowedPackages, env.okFromPhone(), env.homePackages())
        // Fail closed: with no app seen yet the session screen is up, and it comes down only for an app that may be open.
        if (pkg == null || decision == BlockDecision.BLOCK) {
            covering = true
            screen.cover(LockdownCover(pkg, tick.remaining))
        } else {
            uncover(screen)
        }
        return tick
    }

    /** Screen off or locked: nothing is covered, and the next app to show is looked at afresh. */
    fun onScreenOff(screen: LockdownScreen) {
        top = null
        lastQuery = null
        uncover(screen)
    }

    private fun uncover(screen: LockdownScreen) {
        if (!covering) return
        covering = false
        screen.uncover()
    }

    private companion object {
        const val OVERLAP_MS = 1_000L
        const val FIRST_WINDOW_MS = 10 * 60 * 1_000L
    }
}
