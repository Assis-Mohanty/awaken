package com.assismohanty.awaken.engine.body

import java.time.LocalDate

/** Which ceiling applies to a lift (GD §18.4, G18). */
enum class LiftKind(val ceilingRatio: Double) {
    SQUAT(3.5),
    DEADLIFT(3.5),
    BENCH(2.5),
    OTHER(2.0),
}

/** The done sets of one finished session of an exercise, with its game-date. */
data class PlausibilitySession(val date: LocalDate, val sets: List<LoggedSet>)

/** A flagged set still waiting for a later session to repeat it. */
data class PendingLift(val date: LocalDate, val e1rmKg: Double)

data class PlausibilityResult(
    /** For each session, one flag per set (true = unconfirmed when it was logged). Sets with no estimate are never flagged. */
    val flags: List<List<Boolean>>,
    /** The best estimated 1RM that was never in doubt, or was confirmed later; the benchmark reads this. */
    val bestConfirmedKg: Double?,
    val pending: List<PendingLift>,
)

/**
 * The plausibility flag (GD §18.4). A set whose estimated 1RM jumps more than 20 % over the best confirmed one (that best
 * must come from at least two earlier sessions), or sits above the body-weight ceiling, is saved but earns no XP and does
 * not move the benchmark. It is confirmed when a set on a later game-day reaches 95 % of it. Replayed from finished
 * sessions (invariant 7); nothing is stored. Pure.
 */
object Plausibility {
    const val JUMP_FACTOR = 1.20
    const val MIN_SESSIONS = 2
    const val CONFIRM_FACTOR = 0.95

    fun replay(sessions: List<PlausibilitySession>, bodyWeightKg: Double?, kind: LiftKind = LiftKind.OTHER): PlausibilityResult {
        var best: Double? = null
        var earlierSessions = 0
        val pending = mutableListOf<PendingLift>()
        val flags = mutableListOf<List<Boolean>>()
        sessions.forEach { session ->
            val estimates = session.sets.map { TrainingMath.estimatedOneRepMax(it) }
            // A later game-day can confirm a flagged lift before this session's own sets are judged.
            val bestOfSession = estimates.filterNotNull().maxOrNull()
            if (bestOfSession != null) {
                val confirmed = pending.filter { session.date.isAfter(it.date) && bestOfSession >= it.e1rmKg * CONFIRM_FACTOR }
                confirmed.forEach { best = maxOf(best ?: 0.0, it.e1rmKg) }
                pending.removeAll(confirmed.toSet())
            }
            val startBest = best
            val sessionFlags = estimates.map { e ->
                if (e == null) return@map false
                val jump = startBest != null && earlierSessions >= MIN_SESSIONS && e > startBest * JUMP_FACTOR
                val ceiling = bodyWeightKg != null && bodyWeightKg > 0.0 && e / bodyWeightKg > kind.ceilingRatio
                val flagged = jump || ceiling
                if (flagged) pending += PendingLift(session.date, e) else best = maxOf(best ?: 0.0, e)
                flagged
            }
            flags += sessionFlags
            if (bestOfSession != null) earlierSessions++
        }
        return PlausibilityResult(flags, best, pending.toList())
    }
}
