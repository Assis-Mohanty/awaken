package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.Exercise
import com.assismohanty.awaken.core.data.catalog.GymContentRepository
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.DietDao
import com.assismohanty.awaken.core.data.db.dao.GymDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.DayOverrideEntity
import com.assismohanty.awaken.core.data.db.entity.GymSettingsEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.WeekSlotEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.TrainingDays
import com.assismohanty.awaken.engine.body.DayPlan
import com.assismohanty.awaken.engine.body.ExerciseInfo
import com.assismohanty.awaken.engine.body.Gear
import com.assismohanty.awaken.engine.body.PlanExercise
import com.assismohanty.awaken.engine.body.PlanFit
import com.assismohanty.awaken.engine.body.PlanProfile
import com.assismohanty.awaken.engine.body.PlanRules
import com.assismohanty.awaken.engine.body.RestRules
import com.assismohanty.awaken.engine.body.RoutineLine
import com.assismohanty.awaken.engine.body.RoutineMath
import com.assismohanty.awaken.engine.body.TrainingMath
import com.assismohanty.awaken.engine.body.WeekSchedule
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One exercise of a routine with its target (GD §18.1). A timed exercise keeps seconds in [repMin] and [repMax]. */
data class RoutineExerciseDetail(
    val position: Int,
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val repMin: Int?,
    val repMax: Int?,
    val restSec: Int?,
    val superset: String?,
    val unilateral: Boolean,
    val warmup: Boolean,
    val timed: Boolean,
)

/** A routine as My routines and the day card show it. */
data class RoutineDetail(
    val id: String,
    val name: String,
    val exercises: List<RoutineExerciseDetail>,
    /** ISO weekdays 1..7 this routine repeats on. */
    val weekdays: Set<Int>,
    val sourcePlanId: String?,
) {
    val work: List<RoutineExerciseDetail> get() = exercises.filterNot { it.warmup }
    val warmup: List<RoutineExerciseDetail> get() = exercises.filter { it.warmup }
    val workSets: Int get() = work.sumOf { it.sets }

    fun minutes(lines: List<RoutineExerciseDetail> = exercises): Int = RoutineMath.estimateMinutes(
        lines.mapIndexed { i, e -> RoutineLine(e.sets, e.restSec, supersetWithNext = e.superset != null && lines.getOrNull(i + 1)?.superset == e.superset) },
    )
}

/** One day of the week strip. */
data class WeekDay(val date: LocalDate, val plan: DayPlan, val finished: Boolean, val today: Boolean)

/** What Gym knows about the hunter (GD §18.1): the onboarding answers plus the rest and logging settings. */
data class GymAnswers(
    val fitness: FitnessLevel? = null,
    /** `strength`, `muscle` or `general`. */
    val goal: String? = null,
    val gear: Set<Gear> = emptySet(),
    /** False until the hunter has chosen or onboarding saved it: Gym then asks. */
    val gearKnown: Boolean = false,
    val injuries: Set<InjuryRegion> = emptySet(),
    /** `M` or `F`; only for comparing lifts with published data. */
    val sex: String? = null,
    val bodyWeightKg: Double? = null,
    /** ISO weekdays. */
    val trainingDays: Set<Int> = emptySet(),
    val restCompoundSec: Int = DEFAULT_REST_COMPOUND_SEC,
    val restIsolationSec: Int = DEFAULT_REST_ISOLATION_SEC,
    val restSound: Boolean = true,
    val restVibrate: Boolean = true,
    val coached: Boolean = true,
) {
    fun profile() = PlanProfile(gear, injuries, fitness, goal, trainingDays.size)

    companion object {
        /** G5 */
        const val DEFAULT_REST_COMPOUND_SEC = 120
        const val DEFAULT_REST_ISOLATION_SEC = 60
    }
}

/** The result of adding a plan to the week. */
data class AddedPlan(val routineIds: List<String>, val weekdays: Set<Int>)

/**
 * Plans, routines and the weekly schedule (GD §18.1). A tracker, like [GymTracker]: it writes no ledger row. Plans are
 * copied into routines when added, so a plan never changes under the hunter; routines then repeat on their weekdays.
 */
@Singleton
class GymPlanner @Inject constructor(
    private val gymDao: GymDao,
    private val hunterDao: HunterDao,
    private val dietDao: DietDao,
    private val content: GymContentRepository,
    private val tracker: GymTracker,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    // Library

    /** Every exercise as the engine reads it: the catalog and the hunter's own, joined with the overlay. */
    suspend fun infos(): Map<String, ExerciseInfo> {
        val overlay = content.overlay().exercises
        return tracker.exercises().associate { it.id to it.toInfo(overlay[it.id]) }
    }

    private fun Exercise.toInfo(entry: com.assismohanty.awaken.engine.body.OverlayEntry?): ExerciseInfo = com.assismohanty.awaken.engine.body.GymContent.info(
        com.assismohanty.awaken.engine.body.CatalogRow(
            id = id, name = name, category = category, equipment = equipment.key, level = level, mechanic = mechanic,
            primary = primary.map { it.key }, secondary = secondary.map { it.key },
        ),
        entry,
    )

    // Answers and settings

    fun observeAnswers(): Flow<GymAnswers> = combine(gymDao.observeGymSettings(), hunterDao.observeProfile()) { settings, profile ->
        val derived = when {
            profile == null -> emptySet()
            "gym_access" in profile.ownedEquipment -> Gear.entries.toSet()
            "dumbbells" in profile.ownedEquipment -> setOf(Gear.DUMBBELLS, Gear.BENCH)
            else -> emptySet()
        }
        val stored = settings?.equipment
        GymAnswers(
            fitness = settings?.fitnessLevel?.let { name -> FitnessLevel.entries.firstOrNull { it.name == name } },
            goal = settings?.goal,
            gear = if (stored != null) parseGear(stored) else derived,
            gearKnown = stored != null,
            injuries = settings?.injuries.orEmpty().split(",").mapNotNull { n -> InjuryRegion.entries.firstOrNull { it.name == n } }.toSet(),
            sex = settings?.sex,
            bodyWeightKg = profile?.weightKg,
            trainingDays = TrainingDays.days(profile?.trainingDays ?: TrainingDays.NONE),
            restCompoundSec = settings?.restCompoundSec ?: GymAnswers.DEFAULT_REST_COMPOUND_SEC,
            restIsolationSec = settings?.restIsolationSec ?: GymAnswers.DEFAULT_REST_ISOLATION_SEC,
            restSound = settings?.restSound ?: true,
            restVibrate = settings?.restVibrate ?: true,
            coached = settings?.coached ?: true,
        )
    }

    private fun parseGear(csv: String): Set<Gear> = csv.split(",").mapNotNull { Gear.fromKey(it.trim()) }.toSet()

    /** Saves the settings that changed. Body weight is one value: it also goes to the Diet profile when there is one (GD §18.1). */
    suspend fun saveAnswers(answers: GymAnswers) = transactions.run {
        val stored = gymDao.gymSettings() ?: tracker.prefs().let { prefs ->
            GymSettingsEntity(
                barKg = prefs.kg.barWeight, platesKg = prefs.kg.plates.joinToString(","), barLb = prefs.lb.barWeight,
                platesLb = prefs.lb.plates.joinToString(","), unit = prefs.unit.name, repMin = prefs.repRange.min, repMax = prefs.repRange.max,
            )
        }
        gymDao.upsertGymSettings(
            stored.copy(
                fitnessLevel = answers.fitness?.name,
                goal = answers.goal,
                equipment = answers.gear.joinToString(",") { it.key },
                injuries = answers.injuries.filter { it != InjuryRegion.NO_INJURIES }.joinToString(",") { it.name },
                sex = answers.sex,
                restCompoundSec = RestRules.clamp(answers.restCompoundSec) ?: GymAnswers.DEFAULT_REST_COMPOUND_SEC,
                restIsolationSec = RestRules.clamp(answers.restIsolationSec) ?: GymAnswers.DEFAULT_REST_ISOLATION_SEC,
                restSound = answers.restSound,
                restVibrate = answers.restVibrate,
                coached = answers.coached,
            ),
        )
        val profile = hunterDao.getProfile()
        val days = TrainingDays.mask(answers.trainingDays)
        if (profile != null && profile.trainingDays != days) hunterDao.setTrainingDays(days, profile.trainingReminder && days != TrainingDays.NONE)
        val weight = answers.bodyWeightKg?.takeIf { it in MIN_BODY_WEIGHT_KG..TrainingMath.MAX_WEIGHT_KG }
        if (profile != null && profile.weightKg != weight) {
            hunterDao.setWeightKg(weight)
            if (weight != null) dietDao.profile()?.let { dietDao.upsertProfile(it.copy(weightKg = weight, updatedAt = clock.now())) }
        }
    }

    // Plans

    suspend fun availablePlans(answers: GymAnswers): List<PlanFit> =
        PlanRules.available(content.plans(), answers.profile(), infos())

    suspend fun plan(planId: String): com.assismohanty.awaken.engine.body.Plan? = content.plans().plans.firstOrNull { it.id == planId }

    /**
     * Copies each day of [planId], fitted to [answers], into a new routine and puts it on its weekday (GD §18.1). A
     * single-day plan goes on [weekdays]; a multi-day plan keeps its own weekdays. A weekday that already holds a routine
     * is taken over; the old routine stays in My routines, just unscheduled.
     */
    suspend fun addPlan(planId: String, answers: GymAnswers, weekdays: Set<Int>): AddedPlan? = transactions.run {
        val library = content.plans()
        val infos = infos()
        val fit = library.plans.firstOrNull { it.id == planId }?.let { PlanRules.fit(it, answers.profile(), infos) } ?: return@run null
        val order = (gymDao.routines().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val ids = mutableListOf<String>()
        val scheduled = mutableSetOf<Int>()
        fit.days.forEachIndexed { index, day ->
            val routine = RoutineEntity(
                id = UUID.randomUUID().toString(),
                name = if (fit.plan.dayCount == 1) fit.plan.name else "${fit.plan.name} · ${day.name}",
                updatedAt = clock.now(),
                sortOrder = order + index,
                sourcePlanId = fit.plan.id,
            )
            gymDao.upsertRoutine(routine)
            gymDao.insertRoutineExercises(rowsOf(routine.id, day.warmup, warmup = true) + rowsOf(routine.id, day.work, warmup = false, offset = day.warmup.size))
            ids += routine.id
            val days = if (fit.plan.dayCount == 1) weekdays else setOfNotNull(day.weekday)
            days.filter { it in 1..7 }.forEach {
                gymDao.upsertWeekSlot(WeekSlotEntity(it, routine.id))
                scheduled += it
            }
        }
        AddedPlan(ids, scheduled)
    }

    private fun rowsOf(routineId: String, items: List<PlanExercise>, warmup: Boolean, offset: Int = 0): List<RoutineExerciseEntity> =
        items.mapIndexed { i, item ->
            RoutineExerciseEntity(
                routineId = routineId,
                position = offset + i,
                exerciseId = item.exerciseId,
                setCount = item.sets,
                restSec = RestRules.clamp(item.restSec),
                repMin = (if (item.timed) item.seconds else item.reps).getOrNull(0),
                repMax = (if (item.timed) item.seconds else item.reps).getOrNull(1),
                superset = item.superset,
                unilateral = item.unilateral,
                section = if (warmup) RoutineExerciseEntity.SECTION_WARMUP else RoutineExerciseEntity.SECTION_WORK,
                measure = if (item.timed) RoutineExerciseEntity.MEASURE_TIME else RoutineExerciseEntity.MEASURE_REPS,
            )
        }

    // Routines

    fun observeRoutines(): Flow<List<RoutineDetail>> = combine(
        gymDao.observeRoutines(), gymDao.observeRoutineExercises(), gymDao.observeWeekSlots(),
    ) { routines, rows, slots ->
        val names = if (rows.isEmpty()) emptyMap() else tracker.exercises().associate { it.id to it.name }
        routines.map { r ->
            RoutineDetail(
                id = r.id,
                name = r.name,
                exercises = rows.filter { it.routineId == r.id }.sortedBy { it.position }.map { e ->
                    RoutineExerciseDetail(
                        position = e.position, exerciseId = e.exerciseId, name = names[e.exerciseId] ?: e.exerciseId, sets = e.setCount,
                        repMin = e.repMin, repMax = e.repMax, restSec = e.restSec, superset = e.superset, unilateral = e.unilateral,
                        warmup = e.section == RoutineExerciseEntity.SECTION_WARMUP, timed = e.measure == RoutineExerciseEntity.MEASURE_TIME,
                    )
                },
                weekdays = slots.filter { it.routineId == r.id }.map { it.weekday }.toSet(),
                sourcePlanId = r.sourcePlanId,
            )
        }
    }

    suspend fun createRoutine(name: String): String = transactions.run {
        val routine = RoutineEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(GymTracker.MAX_NAME),
            updatedAt = clock.now(),
            sortOrder = (gymDao.routines().maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        gymDao.upsertRoutine(routine)
        routine.id
    }

    suspend fun renameRoutine(routineId: String, name: String) {
        val routine = gymDao.routine(routineId) ?: return
        val clean = name.trim().take(GymTracker.MAX_NAME)
        if (clean.isNotEmpty()) gymDao.upsertRoutine(routine.copy(name = clean, updatedAt = clock.now()))
    }

    /** A copy with the same exercises and targets, placed right after the original and on no weekday. */
    suspend fun duplicateRoutine(routineId: String, copyName: String): String? = transactions.run {
        val source = gymDao.routine(routineId) ?: return@run null
        val copy = source.copy(id = UUID.randomUUID().toString(), name = copyName.take(GymTracker.MAX_NAME), updatedAt = clock.now(), sortOrder = source.sortOrder + 1)
        gymDao.routines().filter { it.sortOrder > source.sortOrder }.forEach { gymDao.upsertRoutine(it.copy(sortOrder = it.sortOrder + 1)) }
        gymDao.upsertRoutine(copy)
        gymDao.insertRoutineExercises(gymDao.routineExercises(routineId).map { it.copy(routineId = copy.id) })
        copy.id
    }

    /** Moves a routine one place up (-1) or down (+1) in My routines. */
    suspend fun moveRoutine(routineId: String, delta: Int) = transactions.run {
        val list = gymDao.routines().toMutableList()
        val from = list.indexOfFirst { it.id == routineId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@run
        list.add(to, list.removeAt(from))
        list.forEachIndexed { i, r -> if (r.sortOrder != i) gymDao.upsertRoutine(r.copy(sortOrder = i)) }
    }

    /** Puts [routineId] on exactly [weekdays]; a weekday it takes over loses its old routine, and the weekdays it leaves are freed. */
    suspend fun setWeekdays(routineId: String, weekdays: Set<Int>) = transactions.run {
        gymDao.weekSlots().filter { it.routineId == routineId && it.weekday !in weekdays }.forEach { gymDao.deleteWeekSlot(it.weekday) }
        weekdays.filter { it in 1..7 }.forEach { gymDao.upsertWeekSlot(WeekSlotEntity(it, routineId)) }
    }

    suspend fun deleteRoutine(routineId: String) = tracker.deleteRoutine(routineId)

    // Routine editor: every change rewrites the routine's rows in order, in one transaction.

    private suspend fun edit(routineId: String, change: suspend (MutableList<RoutineExerciseEntity>) -> Unit) = transactions.run {
        val routine = gymDao.routine(routineId) ?: return@run
        val rows = gymDao.routineExercises(routineId).sortedBy { it.position }.toMutableList()
        change(rows)
        gymDao.clearRoutineExercises(routineId)
        gymDao.insertRoutineExercises(rows.mapIndexed { i, r -> r.copy(position = i) })
        gymDao.upsertRoutine(routine.copy(updatedAt = clock.now()))
    }

    /** Adds an exercise with 3 sets of the default rep range and the default rest for compound or isolation moves (G5). */
    suspend fun addExercise(routineId: String, exerciseId: String, answers: GymAnswers) {
        val info = infos()[exerciseId]
        val range = tracker.prefs().repRange
        val rest = if (info?.mechanic == "compound") answers.restCompoundSec else answers.restIsolationSec
        edit(routineId) { rows ->
            rows += RoutineExerciseEntity(
                routineId = routineId, position = rows.size, exerciseId = exerciseId, setCount = DEFAULT_SETS, restSec = RestRules.clamp(rest),
                repMin = range.min, repMax = range.max,
            )
        }
    }

    suspend fun removeExercise(routineId: String, position: Int) = edit(routineId) { rows -> if (position in rows.indices) rows.removeAt(position) }

    suspend fun moveExercise(routineId: String, position: Int, delta: Int) = edit(routineId) { rows ->
        val to = position + delta
        if (position in rows.indices && to in rows.indices) rows.add(to, rows.removeAt(position))
    }

    /** Sets, target range and rest for one exercise; invalid input (no sets, min above max) is ignored. */
    suspend fun updateExercise(routineId: String, position: Int, sets: Int, repMin: Int?, repMax: Int?, restSec: Int?) = edit(routineId) { rows ->
        val row = rows.getOrNull(position) ?: return@edit
        if (sets !in 1..GymTracker.MAX_SETS_PER_EXERCISE) return@edit
        if (repMin != null && repMax != null && repMin > repMax) return@edit
        rows[position] = row.copy(setCount = sets, repMin = repMin, repMax = repMax, restSec = RestRules.clamp(restSec))
    }

    /** Links the exercise with the next one as a superset (2 or 3 in a row, GD §18.1), or unlinks it when they already are. */
    suspend fun toggleSuperset(routineId: String, position: Int) = edit(routineId) { rows ->
        val a = rows.getOrNull(position) ?: return@edit
        val b = rows.getOrNull(position + 1) ?: return@edit
        if (a.superset != null && a.superset == b.superset) {
            // Split the group between the two; a side left with one exercise is no superset any more.
            val letter = a.superset
            val left = rows.indices.filter { it <= position && rows[it].superset == letter }
            val right = rows.indices.filter { it > position && rows[it].superset == letter }
            if (left.size < 2) left.forEach { rows[it] = rows[it].copy(superset = null) }
            if (right.size < 2) right.forEach { rows[it] = rows[it].copy(superset = null) }
            return@edit
        }
        if (b.superset != null) return@edit
        val letter = a.superset ?: ('A'..'Z').map { it.toString() }.first { l -> rows.none { it.superset == l } }
        if (rows.count { it.superset == letter } >= MAX_SUPERSET) return@edit
        rows[position] = a.copy(superset = letter)
        rows[position + 1] = b.copy(superset = letter)
    }

    // The week

    fun observeWeek(weekStart: LocalDate): Flow<List<WeekDay>> = combine(
        gymDao.observeWeekSlots(), gymDao.observeDayOverrides(weekStart, weekStart.plusDays(6)), gymDao.observeFinishedWorkouts(),
    ) { slots, overrides, finished ->
        val today = tracker.today()
        val slotMap = slots.associate { it.weekday to it.routineId }
        val overrideMap = overrides.associate { it.gameDate to it.routineId }
        val doneDates = finished.map { it.gameDate }.toSet()
        (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            WeekDay(date, WeekSchedule.dayPlan(date, slotMap, overrideMap), date in doneDates, date == today)
        }
    }

    /** "Swap with today" (GD §18.1): [selected] and today trade what they hold, for those two dates only. */
    suspend fun swapWithToday(selected: LocalDate) = transactions.run {
        val today = tracker.today()
        if (selected == today || !WeekSchedule.sameWeek(selected, today)) return@run
        val weekStart = TrainingMath.weekStart(today)
        val slots = gymDao.weekSlots().associate { it.weekday to it.routineId }
        val overrides = gymDao.dayOverrides(weekStart, weekStart.plusDays(6)).associate { it.gameDate to it.routineId }
        val swap = WeekSchedule.swap(selected, today, slots, overrides)
        gymDao.upsertDayOverrides(swap.map { (date, routineId) -> DayOverrideEntity(date, routineId) })
        // Overrides older than the previous week no longer matter.
        gymDao.deleteOverridesBefore(weekStart.minusDays(7))
    }

    private companion object {
        const val DEFAULT_SETS = 3
        const val MAX_SUPERSET = 3
        const val MIN_BODY_WEIGHT_KG = 20.0
    }
}

/** The default weekdays to offer for a plan: the hunter's training days when they exist, else Monday. */
fun defaultPlanWeekdays(trainingDays: Set<Int>): Set<Int> = trainingDays.take(1).toSet().ifEmpty { setOf(1) }
