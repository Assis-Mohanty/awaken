package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.model.LedgerEntry
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import java.time.Instant

/** One completed set of a finished workout, as the XP rule needs it. */
data class XpSet(
    val id: String,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    /** Seconds held, for a timed exercise (plank); null for a reps exercise. */
    val durationSec: Int? = null,
    val warmup: Boolean = false,
    /** When the set was completed; null for sets logged before v29, which never pay. */
    val completedAt: Instant?,
    /** The rest planned after this exercise's sets, for the minimum-rest check. */
    val plannedRestSec: Int?,
    /** The estimate was flagged unconfirmed (GD §18.4). */
    val flagged: Boolean = false,
    /** The exercise's best confirmed estimated 1RM before this workout, for the light-set test. */
    val bestConfirmedE1rmKg: Double? = null,
)

enum class SetVerdict { COUNTED_FULL, COUNTED_LIGHT, WARMUP, NOT_TIMED, EMPTY, TOO_SOON, FLAGGED, EXERCISE_LIMIT }

enum class XpCap { DAILY_GYM, SELF }

data class TrainingXpResult(
    val verdicts: Map<String, SetVerdict>,
    val fullSets: Int,
    val lightSets: Int,
    /** XP before the daily caps: `round(5 × effort)`. */
    val rawXp: Int,
    /** XP paid, after the daily gym cap and the self-report cap. */
    val xp: Int,
    val cappedBy: XpCap?,
) {
    val countedSets: Int get() = fullSets + lightSets
}

/**
 * Gym XP (GD §18). Only completed work sets that respected the minimum rest count; XP is `5 × effort` per set with a cap
 * per exercise and per game-day, and it also counts toward the self-report cap (GD §4.3). Pure: the caller supplies what
 * was already earned today. Paid once at Finish as ledger rows ([ledger]); never repriced after the workout ends.
 */
object TrainingXp {
    /** G14 */
    const val XP_PER_SET = 5

    /** G13: counted sets per exercise per game-day. */
    const val MAX_SETS_PER_EXERCISE_PER_DAY = 10

    /** G15: gym XP per game-day. */
    const val DAILY_CAP = 150

    /** G11: a weighted set under this share of the best confirmed 1RM is light. */
    const val LIGHT_LOAD_SHARE = 0.5

    /** G12 */
    const val LIGHT_BODYWEIGHT_REPS = 5
    const val LIGHT_HOLD_SEC = 20
    const val MIN_HOLD_SEC = 10

    private const val FULL = 1.0
    private const val LIGHT = 0.5

    /** [priorCountedByExercise]: sets already counted today in earlier workouts, per exercise. [gymEarnedToday] and [selfEarnedToday] include them. */
    fun evaluate(
        sets: List<XpSet>,
        priorCountedByExercise: Map<String, Int> = emptyMap(),
        gymEarnedToday: Int = 0,
        selfEarnedToday: Int = 0,
    ): TrainingXpResult {
        val verdicts = mutableMapOf<String, SetVerdict>()
        val countedPerExercise = priorCountedByExercise.toMutableMap()
        val lastCompleted = mutableMapOf<String, Pair<Int, Instant>>()
        var effort = 0.0
        var full = 0
        var light = 0
        sets.sortedWith(compareBy({ it.completedAt ?: Instant.MIN }, { it.setIndex })).forEach { set ->
            val completedAt = set.completedAt
            val previous = lastCompleted[set.exerciseId]
            // The gap is measured against the last set of this exercise from a different round, so the second side of a
            // left/right set and a superset partner are never held back.
            if (completedAt != null) {
                if (previous == null || previous.first != set.setIndex) lastCompleted[set.exerciseId] = set.setIndex to completedAt
            }
            val verdict = when {
                set.warmup -> SetVerdict.WARMUP
                completedAt == null -> SetVerdict.NOT_TIMED
                (set.durationSec ?: 0) < MIN_HOLD_SEC && set.durationSec != null -> SetVerdict.EMPTY
                set.durationSec == null && set.reps < 1 -> SetVerdict.EMPTY
                set.flagged -> SetVerdict.FLAGGED
                previous != null && previous.first != set.setIndex &&
                    java.time.Duration.between(previous.second, completedAt).seconds < RestRules.minRest(set.plannedRestSec) -> SetVerdict.TOO_SOON
                (countedPerExercise[set.exerciseId] ?: 0) >= MAX_SETS_PER_EXERCISE_PER_DAY -> SetVerdict.EXERCISE_LIMIT
                isLight(set) -> SetVerdict.COUNTED_LIGHT
                else -> SetVerdict.COUNTED_FULL
            }
            verdicts[set.id] = verdict
            when (verdict) {
                SetVerdict.COUNTED_FULL -> { effort += FULL; full++; countedPerExercise.merge(set.exerciseId, 1, Int::plus) }
                SetVerdict.COUNTED_LIGHT -> { effort += LIGHT; light++; countedPerExercise.merge(set.exerciseId, 1, Int::plus) }
                else -> Unit
            }
        }
        val raw = Math.round(XP_PER_SET * effort).toInt()
        val gymRoom = (DAILY_CAP - gymEarnedToday).coerceAtLeast(0)
        val selfRoom = (com.assismohanty.awaken.engine.DailyCaps.SELF_CAP - selfEarnedToday).coerceAtLeast(0)
        val xp = minOf(raw, gymRoom, selfRoom)
        val cappedBy = when {
            xp >= raw -> null
            gymRoom <= selfRoom -> XpCap.DAILY_GYM
            else -> XpCap.SELF
        }
        return TrainingXpResult(verdicts, full, light, raw, xp, cappedBy)
    }

    private fun isLight(set: XpSet): Boolean = when {
        set.durationSec != null -> set.durationSec < LIGHT_HOLD_SEC
        set.weightKg > 0.0 -> set.bestConfirmedE1rmKg?.let { set.weightKg < it * LIGHT_LOAD_SHARE } == true
        else -> set.reps < LIGHT_BODYWEIGHT_REPS
    }

    /** The two ledger rows for a workout's XP: total XP and STR stat XP, keyed by the workout so finishing twice is a no-op. */
    fun ledger(workoutId: String, xp: Int): List<LedgerEntry> =
        if (xp <= 0) {
            emptyList()
        } else {
            listOf(
                LedgerEntry(LedgerKind.XP_TOTAL, xp.toLong(), SourceType.GYM_WORKOUT, workoutId),
                LedgerEntry(LedgerKind.XP_STAT, xp.toLong(), SourceType.GYM_WORKOUT, workoutId, stat = Stat.STR.name),
            )
        }

    /** Reversing rows for a deleted workout: the same keys under `UNDO`, deltas negated; the originals stay in the ledger. */
    fun reversal(originals: List<LedgerEntry>): List<LedgerEntry> =
        originals.filter { it.sourceType == SourceType.GYM_WORKOUT }.map { it.copy(delta = -it.delta, sourceType = SourceType.UNDO) }
}
