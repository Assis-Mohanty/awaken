package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One exercise in a plan day. [reps] is a `[min, max]` target; a timed exercise (plank) has [seconds] instead. */
@Serializable
data class PlanExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: List<Int> = emptyList(),
    val seconds: List<Int> = emptyList(),
    val restSec: Int,
    /** Exercises sharing a letter are done back to back (a superset); rest comes after the last one. */
    val superset: String? = null,
    /** Left and right are two steps of one set. */
    val unilateral: Boolean = false,
) {
    val timed: Boolean get() = seconds.isNotEmpty()
}

@Serializable
data class PlanDay(
    /** ISO weekday 1..7 for a multi-day plan; null for a single-day plan (the hunter picks the weekday on Add). */
    val weekday: Int? = null,
    val name: String,
    val warmup: List<PlanExercise> = emptyList(),
    val work: List<PlanExercise>,
)

@Serializable
data class Plan(
    val id: String,
    val name: String,
    /** The lowest fitness level it is for: `any`, `beginner`, `intermediate` or `advanced`. */
    val level: String = "any",
    /** `strength`, `muscle`, `general`. */
    val goal: List<String> = emptyList(),
    /** Gear the plan needs, as [Gear.key] values. Checked against what its exercises need. */
    val equipment: List<String> = emptyList(),
    val days: List<PlanDay>,
) {
    val dayCount: Int get() = days.size
    val gear: Set<Gear> get() = equipment.mapNotNull(Gear::fromKey).toSet()

    /** 0 for `any`/`beginner`, 1 intermediate, 2 advanced. */
    val minLevel: Int get() = when (level) {
        "intermediate" -> 1
        "advanced" -> 2
        else -> 0
    }
}

@Serializable
data class PlanLibrary(val version: Int = 1, val plans: List<Plan>) {
    companion object {
        /** G1: the most plans the library may hold. */
        const val MAX_PLANS = 20
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): PlanLibrary = json.decodeFromString(serializer(), text)
    }
}

/** What the hunter told us, as plan filters and ranking input (GD §18.1). */
data class PlanProfile(
    val gear: Set<Gear> = emptySet(),
    val injuries: Set<InjuryRegion> = emptySet(),
    val level: FitnessLevel? = null,
    /** `strength`, `muscle` or `general`; null = no preference. */
    val goal: String? = null,
    val trainingDays: Int = 0,
) {
    val levelRank: Int get() = when (level) {
        FitnessLevel.BEGINNER -> 0
        FitnessLevel.ADVANCED -> 2
        FitnessLevel.INTERMEDIATE, null -> 1
    }
}

/** A plan as it would be added for one hunter: days with unusable exercises swapped or dropped. */
data class PlanFit(val plan: Plan, val days: List<PlanDay>, val swapped: Int, val dropped: Int)

/** Problems a validator found, one line each. */
object PlanRules {
    /** G2 */
    const val MAX_WORK_PER_DAY = 8
    const val MAX_SETS = 6

    /** G3: a sanity ceiling on weekly sets per primary muscle in one plan. */
    const val MAX_WEEKLY_SETS_PER_MUSCLE = 20

    /** G4: with a strength goal the first compound of a day is trained heavy. */
    val STRENGTH_REPS = listOf(4, 6)

    /**
     * Fits [plan] to [profile]: an exercise that needs gear the hunter lacks, or that an injury hides, is replaced by the
     * easiest library alternative (same lead muscle) or dropped; beginners also get expert exercises swapped down. A day
     * with no work left hides the whole plan (null).
     */
    fun fit(plan: Plan, profile: PlanProfile, library: Map<String, ExerciseInfo>): PlanFit? {
        if (plan.minLevel > profile.levelRank) return null
        // A plan is shown only when every piece of its gear is owned (GD §18.1).
        if (!profile.gear.containsAll(plan.gear)) return null
        var swapped = 0
        var dropped = 0
        val pool = library.values
        fun adapt(item: PlanExercise): PlanExercise? {
            val info = library[item.exerciseId] ?: run { dropped++; return null }
            val unusable = !info.needs.usableWith(profile.gear) || info.hiddenBy(profile.injuries) ||
                (profile.levelRank == 0 && info.level == ExerciseLevel.EXPERT)
            if (!unusable) return item
            val alt = ExerciseAlternatives.of(info, pool, profile.gear, profile.injuries)
            val replacement = (alt.easier + alt.similar).firstOrNull { c ->
                !(profile.levelRank == 0 && c.level == ExerciseLevel.EXPERT)
            }
            return if (replacement == null) {
                dropped++
                null
            } else {
                swapped++
                item.copy(exerciseId = replacement.id)
            }
        }
        val days = plan.days.map { day ->
            val work = day.work.mapNotNull(::adapt).let { strengthFirst(it, profile, library) }
            day.copy(warmup = day.warmup.mapNotNull(::adapt), work = work)
        }
        if (days.any { it.work.isEmpty() }) return null
        return PlanFit(plan, days, swapped, dropped)
    }

    private fun strengthFirst(work: List<PlanExercise>, profile: PlanProfile, library: Map<String, ExerciseInfo>): List<PlanExercise> {
        if (profile.goal != "strength") return work
        val index = work.indexOfFirst { library[it.exerciseId]?.mechanic == "compound" && !it.timed && it.superset == null }
        if (index < 0) return work
        return work.toMutableList().also { it[index] = it[index].copy(reps = STRENGTH_REPS) }
    }

    /** Goal match → day-count distance → level match → name. Deterministic. */
    fun rank(fits: List<PlanFit>, profile: PlanProfile): List<PlanFit> = fits.sortedWith(
        compareBy<PlanFit>(
            { if (profile.goal != null && profile.goal in it.plan.goal) 0 else 1 },
            { if (it.plan.dayCount == 1) 1 else Math.abs(it.plan.dayCount - profile.trainingDays) },
            { Math.abs(it.plan.minLevel - profile.levelRank) },
            { it.plan.name },
        ),
    )

    fun available(library: PlanLibrary, profile: PlanProfile, exercises: Map<String, ExerciseInfo>): List<PlanFit> =
        rank(library.plans.mapNotNull { fit(it, profile, exercises) }, profile)

    /**
     * Checks a library against the exercise data. Returns one line per problem; empty = valid (G1–G3, ids, gear, empty
     * days, supersets, rest steps, compound before isolation).
     */
    fun validate(library: PlanLibrary, exercises: Map<String, ExerciseInfo>): List<String> {
        val problems = mutableListOf<String>()
        if (library.plans.size > PlanLibrary.MAX_PLANS) problems += "more than ${PlanLibrary.MAX_PLANS} plans"
        if (library.plans.map { it.id }.distinct().size != library.plans.size) problems += "duplicate plan ids"
        library.plans.forEach { plan ->
            val at = plan.id
            if (plan.days.isEmpty()) problems += "$at: no days"
            if (plan.level !in setOf("any", "beginner", "intermediate", "advanced")) problems += "$at: unknown level ${plan.level}"
            if (plan.equipment.any { Gear.fromKey(it) == null }) problems += "$at: unknown gear in ${plan.equipment}"
            if (plan.goal.any { it !in setOf("strength", "muscle", "general") }) problems += "$at: unknown goal ${plan.goal}"
            val days = plan.days
            if (days.size > 1 && days.any { it.weekday == null }) problems += "$at: multi-day plan needs a weekday on every day"
            if (days.size == 1 && days[0].weekday != null) problems += "$at: single-day plan must not fix a weekday"
            days.mapNotNull { it.weekday }.let { w ->
                if (w.any { it !in 1..7 } || w.distinct().size != w.size) problems += "$at: bad weekdays $w"
            }
            val needed = mutableSetOf<Gear>()
            val weekly = mutableMapOf<Muscle, Int>()
            days.forEach { day ->
                val where = "$at/${day.name}"
                if (day.work.isEmpty()) problems += "$where: empty day"
                if (day.work.size > MAX_WORK_PER_DAY) problems += "$where: more than $MAX_WORK_PER_DAY exercises"
                (day.warmup + day.work).forEach { item ->
                    val info = exercises[item.exerciseId]
                    if (info == null) {
                        problems += "$where: unknown exercise ${item.exerciseId}"
                        return@forEach
                    }
                    needed += info.requires
                    Gear.forEquipment(info.equipment)?.let { needed += it }
                    if (item.sets !in 1..MAX_SETS) problems += "$where: ${item.exerciseId} sets ${item.sets}"
                    if (item.timed) {
                        if (item.seconds.size != 2 || item.seconds[0] > item.seconds[1] || item.seconds[0] < 5) problems += "$where: ${item.exerciseId} seconds ${item.seconds}"
                    } else if (item.reps.size != 2 || item.reps[0] > item.reps[1] || item.reps[0] < 1 || item.reps[1] > 30) {
                        problems += "$where: ${item.exerciseId} reps ${item.reps}"
                    }
                    if (item.restSec % RestRules.STEP_SEC != 0 || item.restSec !in RestRules.STEP_SEC..RestRules.MAX_SEC) {
                        problems += "$where: ${item.exerciseId} rest ${item.restSec}"
                    }
                }
                day.work.forEach { item -> exercises[item.exerciseId]?.primary?.forEach { weekly.merge(it, item.sets, Int::plus) } }
                // Supersets: contiguous, 2–3 exercises, same set count.
                day.work.mapNotNull { it.superset }.distinct().forEach { letter ->
                    val idx = day.work.indices.filter { day.work[it].superset == letter }
                    val group = idx.map { day.work[it] }
                    if (idx.size !in 2..3) problems += "$where: superset $letter has ${idx.size} exercises"
                    if (idx.last() - idx.first() != idx.size - 1) problems += "$where: superset $letter is not contiguous"
                    if (group.map { it.sets }.distinct().size != 1) problems += "$where: superset $letter set counts differ"
                }
                // Compound before isolation, supersets exempt.
                val plain = day.work.filter { it.superset == null }.mapNotNull { exercises[it.exerciseId]?.mechanic }
                val firstIsolation = plain.indexOf("isolation")
                if (firstIsolation >= 0 && plain.drop(firstIsolation).any { it == "compound" }) problems += "$where: a compound exercise follows an isolation one"
            }
            weekly.filterValues { it > MAX_WEEKLY_SETS_PER_MUSCLE }.forEach { (m, n) -> problems += "$at: $n weekly sets for ${m.key}" }
            if (!plan.gear.containsAll(needed)) problems += "$at: equipment ${plan.equipment} misses ${needed.map { it.key } - plan.equipment.toSet()}"
        }
        return problems
    }
}
