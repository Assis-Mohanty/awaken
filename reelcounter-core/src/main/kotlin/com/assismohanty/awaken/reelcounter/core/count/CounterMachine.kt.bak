package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import java.time.Duration
import java.time.Instant

/** Pure reducer: (state, event) gives (state, effects). The algorithm is specified in PLAN.md section 4. */
class CounterMachine(private val config: CounterConfig = CounterConfig()) {

    fun reduce(state: CounterState, event: CounterEvent): Transition {
        if (event is CounterEvent.DayRolled && !event.newDay.isAfter(state.day)) {
            return Transition(state, emptyList())
        }
        val fx = mutableListOf<CounterEffect>()
        var s = advanceTime(state, event.at, fx)
        s = commitPending(s, event.at, fx)
        s = when (event) {
            is CounterEvent.ScreenEntered -> enter(s, event, fx)
            is CounterEvent.ItemChanged ->
                if (s.active?.app == event.app) setItem(s, event.app, event.index, event.at) else s
            is CounterEvent.ItemAdopted ->
                if (s.active?.app == event.app) adopt(s, event.app, event.index, event.at) else s
            is CounterEvent.ScreenLeft ->
                if (s.active?.app == event.app) {
                    fx += CounterEffect.BubbleChanged(null)
                    leave(s, event.app, event.at)
                } else {
                    s
                }
            is CounterEvent.Tick -> s
            is CounterEvent.DayRolled -> s.copy(
                day = event.newDay,
                apps = s.apps.mapValues { AppDayState() },
            )
            is CounterEvent.Snoozed -> snooze(s, event.app, fx)
            is CounterEvent.LimitsChanged -> s.copy(limits = event.limits.filterValues { it >= 1 })
        }
        return Transition(s, fx)
    }

    private fun millis(from: Instant, to: Instant): Long = Duration.between(from, to).toMillis()

    private fun advanceTime(s: CounterState, at: Instant, fx: MutableList<CounterEffect>): CounterState {
        val active = s.active ?: return s
        val delta = millis(active.lastAt, at)
        if (delta <= 0) return s
        val app = active.app
        val appState = s.apps.getValue(app)
        var carry = appState.carryMillis + minOf(delta, config.maxTickGapMillis)
        val secs = carry / 1000
        carry -= secs * 1000
        if (secs > 0) fx += CounterEffect.TimeAdded(app, s.day, secs)
        return s.copy(
            active = active.copy(lastAt = at),
            apps = s.apps + (app to appState.copy(carryMillis = carry)),
        )
    }

    private fun commitPending(s: CounterState, at: Instant, fx: MutableList<CounterEffect>): CounterState {
        val active = s.active ?: return s
        val session = s.sessions[active.app] ?: return s
        val p = session.pending ?: return s
        if (millis(p.since, at) < config.settleMillis) return s

        val app = active.app
        val settled = session.copy(currentIndex = p.index, pending = null)
        if (p.index <= session.highestCounted) {
            return s.copy(sessions = s.sessions + (app to settled))
        }
        val appState = s.apps.getValue(app).let { it.copy(count = it.count + 1) }
        fx += CounterEffect.Counted(app, s.day, session.id, p.index, p.since, appState.count)
        val next = s.copy(
            apps = s.apps + (app to appState),
            sessions = s.sessions + (app to settled.copy(highestCounted = p.index)),
        )
        val allowance = next.allowance(app, config.snoozeSize)
        if (allowance != null && appState.count >= allowance) {
            fx += CounterEffect.NudgeRequested(app, appState.count, config.maxSnoozes - appState.snoozesUsed)
        }
        return next
    }

    /** A repeat of the reference index never restarts the settle window. */
    private fun setItem(s: CounterState, app: ShortVideoApp, index: Int, at: Instant): CounterState {
        val session = s.sessions[app] ?: return s
        val ref = session.pending?.index ?: session.currentIndex
        if (index == ref) return s
        return s.copy(sessions = s.sessions + (app to session.copy(pending = Pending(index, at))))
    }

    private fun leave(s: CounterState, app: ShortVideoApp, at: Instant): CounterState {
        val session = s.sessions[app]
        val active = if (s.active?.app == app) null else s.active
        if (session == null) return s.copy(active = active)
        return s.copy(
            sessions = s.sessions + (app to session.copy(pending = null, leftAt = at)),
            active = active,
        )
    }

    private fun enter(s: CounterState, e: CounterEvent.ScreenEntered, fx: MutableList<CounterEffect>): CounterState {
        val current = s.active
        if (current?.app == e.app) return setItem(s, e.app, e.index, e.at)

        var t = s
        if (current != null) t = leave(t, current.app, e.at)

        val existing = t.sessions[e.app]
        val leftAt = existing?.leftAt
        val resume = existing != null && (leftAt == null || millis(leftAt, e.at) <= config.resumeGraceMillis)
        val session = if (existing != null && resume && e.index < existing.highestCounted) {
            // The numbering went backwards (a restart renumbers from 0): a new session seeded at this video, never a resume.
            seeded(e.index, e.at)
        } else if (existing != null && resume) {
            existing.copy(leftAt = null)
        } else {
            Session(id = e.at.toEpochMilli(), highestCounted = -1, currentIndex = -1, pending = null, leftAt = null)
        }
        t = t.copy(
            sessions = t.sessions + (e.app to session),
            active = ActiveScreen(e.app, e.at),
        )
        t = setItem(t, e.app, e.index, e.at)
        fx += CounterEffect.BubbleChanged(e.app)
        return t
    }

    /**
     * The app's own index for the video that was on screen when the visit began. It never counts by itself: that video
     * was counted (or resumed) on entry. One step back is a swipe back; anything lower means the list was renumbered.
     */
    private fun adopt(s: CounterState, app: ShortVideoApp, index: Int, at: Instant): CounterState {
        val session = s.sessions[app] ?: return s
        val pending = session.pending
        val next = when {
            index < session.currentIndex - 1 -> seeded(index, at)
            pending != null -> session.copy(pending = pending.copy(index = index))
            else -> session.copy(currentIndex = index, highestCounted = maxOf(session.highestCounted, index))
        }
        return s.copy(sessions = s.sessions + (app to next))
    }

    private fun seeded(index: Int, at: Instant) =
        Session(id = at.toEpochMilli(), highestCounted = index, currentIndex = index, pending = null, leftAt = null)

    private fun snooze(s: CounterState, app: ShortVideoApp, fx: MutableList<CounterEffect>): CounterState {
        val appState = s.apps.getValue(app)
        if (s.limits[app] == null || appState.snoozesUsed >= config.maxSnoozes) {
            fx += CounterEffect.SnoozeRejected(app)
            return s
        }
        val used = appState.snoozesUsed + 1
        fx += CounterEffect.SnoozeAccepted(app, s.day, used)
        return s.copy(apps = s.apps + (app to appState.copy(snoozesUsed = used)))
    }
}
