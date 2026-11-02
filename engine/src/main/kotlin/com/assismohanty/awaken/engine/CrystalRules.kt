package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.GateRank
import java.time.Duration

/** How a finished run is drawn in the gallery (GD §16.5). */
enum class CrystalForm { WHOLE, CRACKED, SHARD }

/** How a run ended, as far as the crystal cares. */
enum class CrystalOutcome { CLEARED, COLLAPSED, LOGGED }

/**
 * The gate crystal (GD §16.5): presentation only. It reads the same timestamps a run already has and writes nothing, so it
 * can never touch the economy. Pure.
 */
object CrystalRules {
    /** Stages 0..5. */
    const val MAX_STAGE = 5

    /** N13: a stopwatch has no planned end, so it grows toward its top tier (GD §8.1 P2) and stays full beyond it. */
    val STOPWATCH_FULL: Duration = Duration.ofMinutes(GateRank.B.durationMinutes.toLong())

    /** Number of colours in the series (`chartSeries1…6`). */
    const val COLOURS = 6

    /** The stage after [elapsed] focus time of [planned]: 0 / 20 / 40 / 60 / 80 / 100 % of the way. Paused time is not in [elapsed]. */
    fun stage(elapsed: Duration, planned: Duration): Int {
        if (planned.isZero || planned.isNegative || elapsed.isNegative) return 0
        val fifths = elapsed.toMillis() * MAX_STAGE / planned.toMillis()
        return fifths.coerceIn(0, MAX_STAGE.toLong()).toInt()
    }

    /** The planned length a run grows toward: its own for a preset or custom gate, 90 minutes for a stopwatch. */
    fun plannedFor(mode: GateMode, plannedSec: Int): Duration =
        if (mode == GateMode.STOPWATCH) STOPWATCH_FULL else Duration.ofSeconds(plannedSec.toLong())

    /** A finished run's size in the gallery: its focus tier (E/D/C/B), E for anything under the first tier. */
    fun tier(minutes: Int): GateRank = FocusRules.tierFor(minutes) ?: GateRank.E

    /** Whole crystal for a cleared run, cracked at the stage reached for a collapsed one, a small shard for a logged one. */
    fun form(outcome: CrystalOutcome): CrystalForm = when (outcome) {
        CrystalOutcome.CLEARED -> CrystalForm.WHOLE
        CrystalOutcome.COLLAPSED -> CrystalForm.CRACKED
        CrystalOutcome.LOGGED -> CrystalForm.SHARD
    }

    /** Which of the six series colours a subject always gets, or null when the run has no subject (use the stat colour). */
    fun colourIndex(subjectId: String?): Int? = subjectId?.let { Math.floorMod(Fnv1a.hash64(it), COLOURS.toLong()).toInt() }
}
