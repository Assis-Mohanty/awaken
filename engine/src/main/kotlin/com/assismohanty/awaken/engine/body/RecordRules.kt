package com.assismohanty.awaken.engine.body

/** The four personal records of an exercise (GD §16.7). */
enum class RecordType { HEAVIEST_WEIGHT, BEST_E1RM, MOST_REPS_AT_WEIGHT, BEST_SESSION_VOLUME }

/** One record broken in a session; at most one per type per exercise per session. */
data class RecordEvent(val type: RecordType, val value: Double, val weightKg: Double? = null)

/**
 * Personal records (GD §16.7). A record counts only against the sessions *before* the one being judged, ties are not
 * records, and an exercise's first session sets records but flags none. "Most reps at a weight" needs an earlier record
 * at that exact weight to beat. Pure.
 */
object RecordRules {
    private const val EPS = 1e-9

    private fun List<LoggedSet>.counted() = filter { it.done && it.reps >= 1 }

    private class Held(history: List<List<LoggedSet>>) {
        var heaviest = 0.0
        var e1rm = 0.0
        val repsAt = mutableMapOf<Double, Int>()
        var volume = 0.0
        var sessions = 0

        init {
            history.forEach { session ->
                val counted = session.counted()
                if (counted.isNotEmpty()) {
                    sessions++
                    counted.forEach { absorb(it) }
                    volume = maxOf(volume, TrainingMath.volume(counted))
                }
            }
        }

        /** Folds one set into the per-set records (volume is per session). */
        fun absorb(set: LoggedSet) {
            heaviest = maxOf(heaviest, set.weightKg)
            TrainingMath.estimatedOneRepMax(set)?.let { e1rm = maxOf(e1rm, it) }
            repsAt.merge(set.weightKg, set.reps, ::maxOf)
        }
    }

    /**
     * For each set of today's session (in order): the record types that set breaks, against the earlier sessions
     * [history] plus the sets already done earlier today. Volume is session-level and not flagged here.
     */
    fun setFlags(history: List<List<LoggedSet>>, today: List<LoggedSet>): List<Set<RecordType>> {
        val held = Held(history)
        if (held.sessions == 0) return today.map { emptySet() }
        return today.map { set ->
            if (!set.done || set.reps < 1) return@map emptySet<RecordType>()
            val flags = mutableSetOf<RecordType>()
            if (set.weightKg > held.heaviest + EPS) flags += RecordType.HEAVIEST_WEIGHT
            val e1rm = TrainingMath.estimatedOneRepMax(set)
            if (e1rm != null && e1rm > held.e1rm + EPS) flags += RecordType.BEST_E1RM
            val heldReps = held.repsAt[set.weightKg]
            if (heldReps != null && set.reps > heldReps) flags += RecordType.MOST_REPS_AT_WEIGHT
            held.absorb(set)
            flags
        }
    }

    /** The records [today] broke, at most one per type: for the finish summary and the achievement count. */
    fun events(history: List<List<LoggedSet>>, today: List<LoggedSet>): List<RecordEvent> {
        val held = Held(history)
        if (held.sessions == 0) return emptyList()
        val flags = setFlags(history, today)
        val events = mutableListOf<RecordEvent>()
        val flagged = today.indices.filter { flags[it].isNotEmpty() }
        flagged.filter { RecordType.HEAVIEST_WEIGHT in flags[it] }.maxByOrNull { today[it].weightKg }
            ?.let { events += RecordEvent(RecordType.HEAVIEST_WEIGHT, today[it].weightKg) }
        flagged.filter { RecordType.BEST_E1RM in flags[it] }.mapNotNull { TrainingMath.estimatedOneRepMax(today[it]) }.maxOrNull()
            ?.let { events += RecordEvent(RecordType.BEST_E1RM, it) }
        flagged.filter { RecordType.MOST_REPS_AT_WEIGHT in flags[it] }.maxByOrNull { today[it].weightKg }
            ?.let { events += RecordEvent(RecordType.MOST_REPS_AT_WEIGHT, today[it].reps.toDouble(), today[it].weightKg) }
        val volume = TrainingMath.volume(today.counted())
        if (volume > held.volume + EPS) events += RecordEvent(RecordType.BEST_SESSION_VOLUME, volume)
        return events
    }

    /** All record events across one exercise's finished sessions, oldest first. Feeds `pr1 / pr25 / pr100`. */
    fun eventCount(sessionsOldestFirst: List<List<LoggedSet>>): Int =
        sessionsOldestFirst.indices.sumOf { i -> events(sessionsOldestFirst.subList(0, i), sessionsOldestFirst[i]).size }
}
