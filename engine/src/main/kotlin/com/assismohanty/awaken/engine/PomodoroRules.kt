package com.assismohanty.awaken.engine

/** Where a Pomodoro session is (GD §16.3). Focus rounds are Focus Gate runs; the breaks are not gates. */
enum class PomodoroPhase { FOCUS, SHORT_BREAK, LONG_BREAK, DONE }

/** How a session stands. A collapsed round ends the session; rounds already cleared keep their rewards. */
enum class PomodoroStatus { RUNNING, FINISHED, ENDED, COLLAPSED }

/** One session's settings (N7-N10). Always valid: the constructor refuses anything outside the approved ranges. */
data class PomodoroConfig(
    val focusMinutes: Int = PomodoroRules.DEFAULT_FOCUS,
    val shortBreakMinutes: Int = PomodoroRules.DEFAULT_SHORT_BREAK,
    val longBreakMinutes: Int = PomodoroRules.DEFAULT_LONG_BREAK,
    val rounds: Int = PomodoroRules.DEFAULT_ROUNDS,
) {
    init {
        require(PomodoroRules.isValidFocus(focusMinutes)) { "focus $focusMinutes outside the Pomodoro range" }
        require(PomodoroRules.isValidShortBreak(shortBreakMinutes)) { "short break $shortBreakMinutes outside the Pomodoro range" }
        require(PomodoroRules.isValidLongBreak(longBreakMinutes)) { "long break $longBreakMinutes outside the Pomodoro range" }
        require(rounds in PomodoroRules.ROUNDS_RANGE) { "rounds $rounds outside the Pomodoro range" }
    }
}

/** What follows a finished break: the next focus round, or the end of the session. */
data class PomodoroStep(val phase: PomodoroPhase, val round: Int)

/**
 * The Pomodoro phase machine (GD §16.3, approved 2026-11-02). A session of R rounds runs
 * `FOCUS 1, short, FOCUS 2, short, … FOCUS R, long, DONE`. Each focus round is an ordinary custom Focus Gate run, so the
 * custom-run tiers, the daily cap (§8.1 P5) and the breach rule apply to it unchanged. Pure.
 */
object PomodoroRules {
    /** N7: focus round 25 minutes, 10-60 in 5-minute steps. */
    const val DEFAULT_FOCUS = 25
    val FOCUS_RANGE = 10..60
    const val FOCUS_STEP = 5

    /** N8: short break 5 minutes, 1-15. */
    const val DEFAULT_SHORT_BREAK = 5
    val SHORT_BREAK_RANGE = 1..15

    /** N9: long break 15 minutes, 5-30 in 5-minute steps. */
    const val DEFAULT_LONG_BREAK = 15
    val LONG_BREAK_RANGE = 5..30
    const val LONG_BREAK_STEP = 5

    /** N10: 4 rounds a session, 2-6. */
    const val DEFAULT_ROUNDS = 4
    val ROUNDS_RANGE = 2..6

    fun isValidFocus(minutes: Int): Boolean = minutes in FOCUS_RANGE && (minutes - FOCUS_RANGE.first) % FOCUS_STEP == 0

    fun isValidShortBreak(minutes: Int): Boolean = minutes in SHORT_BREAK_RANGE

    fun isValidLongBreak(minutes: Int): Boolean = minutes in LONG_BREAK_RANGE && (minutes - LONG_BREAK_RANGE.first) % LONG_BREAK_STEP == 0

    /** Snaps a stored or typed value to the nearest valid one, so a hand-edited setting can never break a session. */
    fun clampFocus(minutes: Int): Int = FOCUS_RANGE.first + (minutes.coerceIn(FOCUS_RANGE) - FOCUS_RANGE.first) / FOCUS_STEP * FOCUS_STEP

    fun clampShortBreak(minutes: Int): Int = minutes.coerceIn(SHORT_BREAK_RANGE)

    fun clampLongBreak(minutes: Int): Int =
        LONG_BREAK_RANGE.first + (minutes.coerceIn(LONG_BREAK_RANGE) - LONG_BREAK_RANGE.first) / LONG_BREAK_STEP * LONG_BREAK_STEP

    fun clampRounds(rounds: Int): Int = rounds.coerceIn(ROUNDS_RANGE)

    /** The settings with every value brought into range. */
    fun safeConfig(focus: Int, shortBreak: Int, longBreak: Int, rounds: Int): PomodoroConfig =
        PomodoroConfig(clampFocus(focus), clampShortBreak(shortBreak), clampLongBreak(longBreak), clampRounds(rounds))

    /** The break after focus round [round] of [rounds]: short between rounds, long after the last one. */
    fun breakAfterFocus(round: Int, rounds: Int): PomodoroPhase =
        if (round < rounds) PomodoroPhase.SHORT_BREAK else PomodoroPhase.LONG_BREAK

    fun breakMinutes(phase: PomodoroPhase, config: PomodoroConfig): Int = when (phase) {
        PomodoroPhase.SHORT_BREAK -> config.shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> config.longBreakMinutes
        PomodoroPhase.FOCUS, PomodoroPhase.DONE -> 0
    }

    /** After a break ends: the next focus round (started by a tap, never by itself), or DONE after the long break. */
    fun afterBreak(phase: PomodoroPhase, round: Int): PomodoroStep = when (phase) {
        PomodoroPhase.SHORT_BREAK -> PomodoroStep(PomodoroPhase.FOCUS, round + 1)
        PomodoroPhase.LONG_BREAK -> PomodoroStep(PomodoroPhase.DONE, round)
        PomodoroPhase.FOCUS, PomodoroPhase.DONE -> PomodoroStep(phase, round)
    }

    /** Is a break over at [nowMillis], given it ends at [endsAtMillis]? A skipped break just ends now. */
    fun breakIsOver(endsAtMillis: Long, nowMillis: Long): Boolean = nowMillis >= endsAtMillis
}
