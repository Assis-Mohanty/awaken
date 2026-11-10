package com.assismohanty.awaken.core.protect

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.usage.ForegroundRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.lock.FocusLockDecider
import com.assismohanty.awaken.core.lock.LockSnapshot
import com.assismohanty.awaken.engine.BlockDecision
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.GateRunMath
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The Focus Lock cover's content: the app in front, and the session clock (counting up for a stopwatch). */
data class FocusCover(val packageName: String, val time: Duration, val countsUp: Boolean)

/** The Focus Lock cover as the watcher sees it. The service draws it; a test records the calls. */
interface FocusLockScreen {
    fun cover(cover: FocusCover)
    fun uncover()
}

/**
 * The Focus Lock loop body (GD §8.2), without any Android window code. One [poll] reads the usage log for the app that
 * came to the front and covers it when the session says so. AWAKEN itself is never covered, and while the cover is up it
 * stays up when AWAKEN comes to the front under it (that is how the covered app is stopped). Reads package names only and
 * stores nothing. Not thread-safe: the service calls it from one coroutine.
 */
@Singleton
class FocusLockWatcher @Inject constructor(
    private val usage: UsageGateway,
    private val state: FocusLockDecider,
    private val clock: Clock,
    private val apps: ProtectApps,
    @ApplicationContext context: Context,
) {
    private val ownPackage = context.packageName

    @Volatile
    var snapshot: LockSnapshot = LockSnapshot()

    private var top: String? = null
    private var lastQuery: Instant? = null
    private var seenUntil: Instant? = null

    var isCovering: Boolean = false
        private set

    fun sessionRunning(): Boolean = state.sessionRunning(snapshot, clock.now())

    suspend fun poll(screen: FocusLockScreen): PollResult {
        val now = clock.now()
        val run = snapshot.run
        if (run == null || !state.sessionRunning(snapshot, now)) {
            reset(screen)
            return PollResult.Ok(changed = false)
        }
        // The first look reaches back far enough to find the app that is in front right now.
        val since = lastQuery?.minusMillis(OVERLAP_MS) ?: now.minusMillis(FIRST_WINDOW_MS)
        lastQuery = now
        var changed = false
        when (val read = usage.foreground(since, now)) {
            ForegroundRead.NoAccess -> return PollResult.NoAccess
            is ForegroundRead.Resumed -> {
                val transient = apps.transientWindows() - ownPackage
                for (app in read.apps) {
                    if (seenUntil?.let { !app.at.isAfter(it) } == true) continue
                    seenUntil = app.at
                    if (app.packageName in transient) continue
                    if (app.packageName != top) changed = true
                    top = app.packageName
                }
            }
            ForegroundRead.Unavailable, ForegroundRead.NoChange -> Unit
        }
        val pkg = top
        when {
            // AWAKEN in front: not covered, and a cover that is up stays (the covered app was sent behind AWAKEN).
            pkg == ownPackage -> Unit
            pkg != null && state.decide(pkg, snapshot, now) == BlockDecision.BLOCK -> {
                isCovering = true
                val stopwatch = run.mode == GateMode.STOPWATCH
                val time = if (stopwatch) GateRunMath.elapsed(run.timing, now) else GateRunMath.remaining(run.timing, now)
                screen.cover(FocusCover(pkg, time, stopwatch))
            }
            else -> uncover(screen)
        }
        return PollResult.Ok(changed)
    }

    /** The hunter left the cover on purpose (Back to focus, the Back key): do not cover the same app again from stale events. */
    fun dismissed(screen: FocusLockScreen) {
        top = null
        uncover(screen)
    }

    /** Screen off or locked: nothing is covered, and the next app to show is looked at afresh. */
    fun onScreenOff(screen: FocusLockScreen) = reset(screen)

    private fun reset(screen: FocusLockScreen) {
        top = null
        lastQuery = null
        uncover(screen)
    }

    private fun uncover(screen: FocusLockScreen) {
        if (!isCovering) return
        isCovering = false
        screen.uncover()
    }

    private companion object {
        const val OVERLAP_MS = 1_000L
        const val FIRST_WINDOW_MS = 10 * 60 * 1_000L
    }
}
