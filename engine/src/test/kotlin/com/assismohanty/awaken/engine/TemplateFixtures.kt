package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestTemplate
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification

/** Synthetic template library: 30 templates per stat (150 total) with a mix of equipment, tags and verification. */
object TemplateFixtures {
    const val PER_STAT = 30

    fun library(perStat: Int = PER_STAT): List<QuestTemplate> = Stat.entries.flatMap { stat ->
        (0 until perStat).map { i ->
            template(
                id = "${stat.name.lowercase()}_$i",
                stat = stat,
                equipment = if (i % 10 == 0) listOf("dumbbell") else emptyList(),
                tags = buildList {
                    if (stat == Stat.STR && i % 3 == 0) add("impact")
                    if (stat == Stat.STR && i % 4 == 1) add("legs")
                },
                verification = when (i % 5) {
                    3 -> Verification.TIMER
                    4 -> Verification.HEALTH_STEPS
                    else -> Verification.SELF
                },
            )
        }
    }

    fun template(
        id: String,
        stat: Stat,
        equipment: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        verification: Verification = Verification.SELF,
    ) = QuestTemplate(
        id = id,
        stat = stat,
        secondaryStat = null,
        titles = listOf("$id A", "$id B", "$id C"),
        description = "Do {target} things.",
        unit = "reps",
        targets = mapOf(Difficulty.EASY to 10, Difficulty.NORMAL to 20, Difficulty.HARD to 40, Difficulty.EXTREME to 60),
        verification = verification,
        equipment = equipment,
        tags = tags,
    )
}
