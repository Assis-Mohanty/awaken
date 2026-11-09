package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.onboarding.InjuryRegion

/** The catalog's three levels, in order. */
enum class ExerciseLevel(val key: String) {
    BEGINNER("beginner"),
    INTERMEDIATE("intermediate"),
    EXPERT("expert"),
    ;

    companion object {
        fun fromKey(key: String?): ExerciseLevel? = entries.firstOrNull { it.key == key }
    }
}

/** What the engine needs to know about one exercise: library data only, never a guess. */
data class ExerciseInfo(
    val id: String,
    val name: String,
    val equipment: Equipment,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle> = emptySet(),
    val level: ExerciseLevel? = null,
    val mechanic: String? = null,
    val category: String? = null,
    /** Gear the overlay adds on top of [equipment] (a pull-up bar for a "body only" pull-up). */
    val requires: Set<Gear> = emptySet(),
    /** Regions the overlay says this exercise loads, on top of the muscle rules. */
    val loads: Set<InjuryRegion> = emptySet(),
) {
    val needs: ExerciseNeeds get() = ExerciseNeeds(equipment, requires)

    /** The muscle the exercise is mainly for: its first primary muscle in the engine's muscle order. */
    val lead: Muscle? get() = primary.minByOrNull { it.ordinal }

    fun hiddenBy(injuries: Set<InjuryRegion>): Boolean = InjuryRules.hidden(injuries, primary, mechanic, category, loads)
}

/** Replacements for one exercise by difficulty; any list may be empty, and an empty list shows no option (GD §18.2). */
data class Alternatives(val easier: List<ExerciseInfo>, val similar: List<ExerciseInfo>, val harder: List<ExerciseInfo>) {
    val isEmpty: Boolean get() = easier.isEmpty() && similar.isEmpty() && harder.isEmpty()
}

/**
 * "Swap with" (GD §18.2): same lead muscle and mechanic, equipment the hunter has, nothing an injury hides, and a catalog
 * level one step down (easier), the same (similar) or one step up (harder). Library data only: an exercise without a
 * level or a mechanic has no alternatives. Pure.
 */
object ExerciseAlternatives {
    const val MAX_PER_LIST = 5
    private val STRENGTH_CATEGORIES = setOf("strength", "powerlifting")

    fun of(source: ExerciseInfo, pool: Collection<ExerciseInfo>, owned: Set<Gear>, injuries: Set<InjuryRegion> = emptySet()): Alternatives {
        val lead = source.lead
        val level = source.level
        val mechanic = source.mechanic
        if (lead == null || level == null || mechanic == null) return Alternatives(emptyList(), emptyList(), emptyList())
        val candidates = pool.filter { c ->
            c.id != source.id &&
                c.lead == lead &&
                c.mechanic == mechanic &&
                c.level != null &&
                c.category in STRENGTH_CATEGORIES &&
                c.needs.usableWith(owned) &&
                !c.hiddenBy(injuries)
        }
        fun pick(delta: Int): List<ExerciseInfo> = candidates
            .filter { it.level!!.ordinal == level.ordinal + delta }
            .sortedWith(compareBy({ -(it.secondary intersect source.secondary).size }, { it.name }))
            .take(MAX_PER_LIST)
        return Alternatives(easier = pick(-1), similar = pick(0), harder = pick(1))
    }
}
