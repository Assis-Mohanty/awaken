package com.assismohanty.awaken.core.widget

import android.content.Context
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.PomodoroDao
import com.assismohanty.awaken.core.data.repo.FocusKind
import com.assismohanty.awaken.core.data.repo.LastFocus
import com.assismohanty.awaken.core.data.repo.LastFocusStore
import com.assismohanty.awaken.core.domain.breakOver
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.PomodoroPhase
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** What the Quick Settings tile and the Start focus widget show (GD §16.3, DS §9.3). */
sealed interface FocusSurface {
    /** Nothing runs. [last] is what a tap starts; null before the first session ever. */
    data class Idle(val last: LastFocus?, val subjectName: String?) : FocusSurface

    /** A focus run is open. A timed run ends at [endsAt]; a stopwatch has none and counts up from [startedAt]. */
    data class Running(val mode: GateMode, val startedAt: Instant, val endsAt: Instant?) : FocusSurface

    /** A Pomodoro break is running or over, waiting for the next round. */
    data class Break(val endsAt: Instant, val over: Boolean, val round: Int, val rounds: Int, val finished: Boolean) : FocusSurface
}

/** Reads the state the tile and the widget draw: one read, no ticking (a surface outside the app cannot tick). */
@Singleton
class FocusSurfaceReader @Inject constructor(
    private val gateRunDao: GateRunDao,
    private val pomodoroDao: PomodoroDao,
    private val lastFocusStore: LastFocusStore,
    private val focusDao: FocusDao,
    private val clock: Clock,
) {
    suspend fun read(): FocusSurface {
        val now = clock.now()
        gateRunDao.running()?.let { run ->
            return FocusSurface.Running(
                mode = run.mode,
                startedAt = run.startedAt,
                endsAt = if (run.mode == GateMode.STOPWATCH) null else GateRunMath.endAt(run.timing, now),
            )
        }
        pomodoroDao.running()?.let { session ->
            val endsAt = session.phaseEndsAt
            val inBreak = session.phase == PomodoroPhase.SHORT_BREAK || session.phase == PomodoroPhase.LONG_BREAK
            if (inBreak && endsAt != null) {
                return FocusSurface.Break(endsAt, session.breakOver(now), session.currentRound, session.rounds, finished = session.phase == PomodoroPhase.LONG_BREAK)
            }
        }
        val last = lastFocusStore.current()
        val subject = last?.subjectId?.let { id -> focusDao.observeAll().first().firstOrNull { it.id == id }?.name }
        return FocusSurface.Idle(last, subject)
    }
}

/** "D · 25 min · Maths", "Stopwatch", "Pomodoro · 25 min × 4": the session a tap would start again. */
fun lastFocusLine(context: Context, last: LastFocus, subjectName: String?): String {
    val base = when (last.kind) {
        FocusKind.PRESET -> context.getString(R.string.focus_last_preset, last.rank.name, last.minutes)
        FocusKind.CUSTOM -> context.getString(R.string.focus_last_custom, last.minutes)
        FocusKind.STOPWATCH -> context.getString(R.string.focus_last_stopwatch)
        FocusKind.POMODORO -> context.getString(R.string.focus_last_pomodoro, last.pomodoro.focusMinutes, last.pomodoro.rounds)
    }
    return if (subjectName.isNullOrBlank()) base else context.getString(R.string.focus_last_with_subject, base, subjectName)
}
