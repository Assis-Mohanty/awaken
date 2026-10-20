package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Verification

/**
 * Reward pricing for Path quests, the anti-abuse core (GD §6.6.4). The model's numbers never set a reward: a GATE
 * blueprint becomes a Focus Gate (time is measured, TIMER), a SELF blueprint is capped at Normal with a soft speed bump.
 * XP and gold then follow §4.1 / §9.1 unchanged, daily caps included.
 */
object PathPricing {
    private const val SECONDS_PER_EST_MINUTE = 15
    private const val MIN_DURATION_FLOOR = 60
    private const val MIN_DURATION_CEILING = 900

    fun price(blueprints: List<ValidatedBlueprint>): List<PricedBlueprint> = blueprints.map(::price)

    fun price(blueprint: ValidatedBlueprint): PricedBlueprint = when (blueprint.mode) {
        PathMode.GATE -> {
            val gate = gateFor(blueprint.estMinutes)
            PricedBlueprint(blueprint, gate.difficulty, Verification.TIMER, gate, minDurationSec = 0)
        }
        PathMode.SELF -> PricedBlueprint(
            blueprint = blueprint,
            difficulty = minOf(blueprint.effort, Difficulty.NORMAL),
            verification = Verification.SELF,
            gateRank = null,
            minDurationSec = (blueprint.estMinutes * SECONDS_PER_EST_MINUTE).coerceIn(MIN_DURATION_FLOOR, MIN_DURATION_CEILING),
        )
    }

    /** The smallest gate whose duration covers [estMinutes]; anything over 90 minutes is a B-Gate. */
    fun gateFor(estMinutes: Int): GateRank = GateRank.entries.firstOrNull { it.durationMinutes >= estMinutes } ?: GateRank.B
}
