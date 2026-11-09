package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** Minimum rest, steppers, alternatives, plan fitting, the week schedule and benchmark maths (GD §18). */
class GymRedesignRulesTest {

    // G-13: minimum rest
    @Test
    fun `G-13 minimum rest is a quarter of the planned rest within 10 to 30 s`() {
        assertThat(listOf(60, 90, 120, 30, null, 600).map { RestRules.minRest(it) }).containsExactly(15, 23, 30, 10, 10, 30).inOrder()
    }

    @Test
    fun `minimum rest left counts down from the previous set`() {
        val t = java.time.Instant.parse("2026-11-10T10:00:00Z")
        assertThat(RestRules.minRestLeft(null, t, 120)).isEqualTo(0)
        assertThat(RestRules.minRestLeft(t, t.plusSeconds(10), 120)).isEqualTo(20)
        assertThat(RestRules.minRestLeft(t, t.plusSeconds(30), 120)).isEqualTo(0)
    }

    @Test
    fun `stepper follows the equipment and the smallest plate`() {
        val kg = PlateMath.defaults(PlateUnit.KG)
        val lb = PlateMath.defaults(PlateUnit.LB)
        assertThat(StepIncrement.step(Equipment.BARBELL, PlateUnit.KG, kg)).isEqualTo(2.5)
        assertThat(StepIncrement.step(Equipment.BARBELL, PlateUnit.KG, PlateSetup(20.0, listOf(10.0, 5.0)))).isEqualTo(10.0)
        assertThat(StepIncrement.step(Equipment.BARBELL, PlateUnit.LB, lb)).isEqualTo(5.0)
        assertThat(StepIncrement.step(Equipment.DUMBBELL, PlateUnit.KG, kg)).isEqualTo(2.5)
        assertThat(StepIncrement.step(Equipment.CABLE, PlateUnit.KG, kg)).isEqualTo(5.0)
        assertThat(StepIncrement.step(Equipment.MACHINE, PlateUnit.LB, lb)).isEqualTo(10.0)
        assertThat(StepIncrement.step(Equipment.KETTLEBELLS, PlateUnit.KG, kg)).isEqualTo(4.0)
        assertThat(StepIncrement.step(Equipment.BODY_ONLY, PlateUnit.KG, kg)).isEqualTo(2.5)
    }

    // Library for alternatives and plan fitting
    private fun ex(
        id: String,
        equipment: Equipment,
        level: ExerciseLevel,
        primary: Set<Muscle> = setOf(Muscle.CHEST),
        mechanic: String = "compound",
        requires: Set<Gear> = emptySet(),
        loads: Set<InjuryRegion> = emptySet(),
        secondary: Set<Muscle> = emptySet(),
    ) = ExerciseInfo(id, id, equipment, primary, secondary, level, mechanic, "strength", requires, loads)

    private val bench = ex("bench", Equipment.BARBELL, ExerciseLevel.BEGINNER, requires = setOf(Gear.BENCH))
    private val library = listOf(
        bench,
        ex("db_press", Equipment.DUMBBELL, ExerciseLevel.BEGINNER, requires = setOf(Gear.BENCH)),
        ex("machine_press", Equipment.MACHINE, ExerciseLevel.BEGINNER),
        ex("pushup", Equipment.BODY_ONLY, ExerciseLevel.BEGINNER),
        ex("incline", Equipment.BARBELL, ExerciseLevel.INTERMEDIATE, requires = setOf(Gear.BENCH)),
        ex("pec_fly", Equipment.CABLE, ExerciseLevel.BEGINNER, mechanic = "isolation"),
        ex("row", Equipment.BARBELL, ExerciseLevel.BEGINNER, primary = setOf(Muscle.MIDDLE_BACK)),
    ).associateBy { it.id }

    @Test
    fun `G-15 alternatives keep the lead muscle mechanic and owned gear only`() {
        val alt = ExerciseAlternatives.of(bench, library.values, owned = setOf(Gear.BARBELL, Gear.BENCH, Gear.DUMBBELLS))
        assertThat(alt.similar.map { it.id }).containsExactly("db_press", "pushup").inOrder()
        assertThat(alt.harder.map { it.id }).containsExactly("incline")
        assertThat(alt.easier).isEmpty()
        // No machines owned: the machine press is not offered; no bench: the dumbbell press is not either.
        val noBench = ExerciseAlternatives.of(bench, library.values, owned = setOf(Gear.BARBELL, Gear.DUMBBELLS))
        assertThat(noBench.similar.map { it.id }).containsExactly("pushup")
    }

    @Test
    fun `no level or mechanic means no alternatives and injuries hide candidates`() {
        val unknown = bench.copy(level = null)
        assertThat(ExerciseAlternatives.of(unknown, library.values, Gear.entries.toSet()).isEmpty).isTrue()
        val hidden = ExerciseAlternatives.of(bench, library.values, Gear.entries.toSet(), setOf(InjuryRegion.WRIST))
        assertThat(hidden.similar.map { it.id }).contains("machine_press")
        val hiddenAll = ex("x", Equipment.MACHINE, ExerciseLevel.BEGINNER, loads = setOf(InjuryRegion.WRIST))
        val withLoads = ExerciseAlternatives.of(bench, listOf(hiddenAll), Gear.entries.toSet(), setOf(InjuryRegion.WRIST))
        assertThat(withLoads.isEmpty).isTrue()
    }

    @Test
    fun `injury rules follow muscles and the leg and jump rules`() {
        assertThat(InjuryRules.loads(InjuryRegion.SHOULDERS, setOf(Muscle.SHOULDERS), "isolation", "strength", emptySet())).isTrue()
        assertThat(InjuryRules.loads(InjuryRegion.SHOULDERS, setOf(Muscle.CHEST), "compound", "strength", emptySet())).isFalse()
        assertThat(InjuryRules.loads(InjuryRegion.SHOULDERS, setOf(Muscle.TRICEPS), "compound", "strength", setOf(InjuryRegion.SHOULDERS))).isTrue()
        assertThat(InjuryRules.loads(InjuryRegion.KNEE, setOf(Muscle.QUADRICEPS), "compound", "strength", emptySet())).isTrue()
        assertThat(InjuryRules.loads(InjuryRegion.KNEE, setOf(Muscle.QUADRICEPS), "isolation", "strength", emptySet())).isFalse()
        assertThat(InjuryRules.loads(InjuryRegion.KNEE, setOf(Muscle.CALVES), "isolation", "plyometrics", emptySet())).isTrue()
        assertThat(InjuryRules.loads(InjuryRegion.HIP, setOf(Muscle.GLUTES), "compound", "strength", emptySet())).isTrue()
        assertThat(InjuryRules.loads(InjuryRegion.NO_INJURIES, setOf(Muscle.GLUTES), "compound", "strength", emptySet())).isFalse()
    }

    // Plans
    private fun item(id: String, sets: Int = 3) = PlanExercise(id, sets, reps = listOf(8, 10), restSec = 90)

    private val plan = Plan(
        id = "p", name = "Chest", level = "any", goal = listOf("muscle"), equipment = listOf("barbell", "bench", "cables"),
        days = listOf(PlanDay(name = "Chest", work = listOf(item("bench"), item("incline"), item("pec_fly")))),
    )

    @Test
    fun `a plan needs every piece of its gear`() {
        val owned = setOf(Gear.BARBELL, Gear.BENCH, Gear.CABLES)
        assertThat(PlanRules.fit(plan, PlanProfile(gear = owned), library)).isNotNull()
        assertThat(PlanRules.fit(plan, PlanProfile(gear = owned - Gear.CABLES), library)).isNull()
    }

    @Test
    fun `an injury swaps an exercise for the easiest alternative or drops it`() {
        val hurt = library + ("bench" to bench.copy(loads = setOf(InjuryRegion.SHOULDERS)))
        val fit = PlanRules.fit(plan, PlanProfile(gear = Gear.entries.toSet(), injuries = setOf(InjuryRegion.SHOULDERS)), hurt)!!
        // Same lead muscle, easier first, then similar: the dumbbell press replaces the barbell bench; nothing is dropped.
        assertThat(fit.days[0].work.map { it.exerciseId }).containsExactly("db_press", "incline", "pec_fly").inOrder()
        assertThat(fit.swapped).isEqualTo(1)
        val onlyBench = hurt.filterKeys { it == "bench" || it == "pec_fly" || it == "incline" }
        val dropped = PlanRules.fit(plan, PlanProfile(gear = Gear.entries.toSet(), injuries = setOf(InjuryRegion.SHOULDERS)), onlyBench)!!
        assertThat(dropped.dropped).isEqualTo(1)
        assertThat(dropped.days[0].work).hasSize(2)
    }

    @Test
    fun `beginners do not see advanced plans and a strength goal makes the first compound heavy`() {
        val advanced = plan.copy(level = "intermediate")
        assertThat(PlanRules.fit(advanced, PlanProfile(gear = Gear.entries.toSet(), level = FitnessLevel.BEGINNER), library)).isNull()
        val fit = PlanRules.fit(plan, PlanProfile(gear = Gear.entries.toSet(), goal = "strength"), library)!!
        assertThat(fit.days[0].work[0].reps).containsExactly(4, 6).inOrder()
        assertThat(fit.days[0].work[1].reps).containsExactly(8, 10).inOrder()
    }

    @Test
    fun `plans rank by goal then day count then level then name`() {
        fun p(id: String, days: Int, goal: String, level: String = "any") =
            Plan(id, id, level, listOf(goal), emptyList(), List(days) { PlanDay(weekday = it + 1, name = "d", work = listOf(item("bench"))) })
        val fits = listOf(p("c", 4, "muscle"), p("a", 3, "general"), p("b", 3, "muscle"), p("z", 1, "muscle"))
            .mapNotNull { PlanRules.fit(it, PlanProfile(gear = Gear.entries.toSet()), library) }
        val ranked = PlanRules.rank(fits, PlanProfile(goal = "muscle", trainingDays = 3)).map { it.plan.id }
        assertThat(ranked).containsExactly("b", "c", "z", "a").inOrder()
    }

    @Test
    fun `validation reports unknown ids, empty days, bad supersets and ordering`() {
        val bad = PlanLibrary(
            plans = listOf(
                plan.copy(
                    id = "bad",
                    days = listOf(
                        PlanDay(name = "empty", work = emptyList()),
                    ),
                ),
                plan.copy(id = "ghost", days = listOf(PlanDay(name = "g", work = listOf(item("nope"))))),
                plan.copy(id = "order", days = listOf(PlanDay(name = "o", work = listOf(item("pec_fly"), item("bench"))))),
                plan.copy(id = "ss", days = listOf(PlanDay(name = "s", work = listOf(item("bench").copy(superset = "A"))))),
            ),
        )
        val problems = PlanRules.validate(bad, library).joinToString("\n")
        assertThat(problems).contains("bad/empty: empty day")
        assertThat(problems).contains("unknown exercise nope")
        assertThat(problems).contains("compound exercise follows an isolation")
        assertThat(problems).contains("superset A has 1")
        assertThat(PlanRules.validate(PlanLibrary(plans = listOf(plan)), library)).isEmpty()
    }

    // Week schedule
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `a slot repeats every week and an override wins for its date only`() {
        val slots = mapOf(1 to "chest", 3 to "legs")
        assertThat(WeekSchedule.dayPlan(monday, slots, emptyMap())).isEqualTo(DayPlan.Workout("chest"))
        assertThat(WeekSchedule.dayPlan(monday.plusDays(7), slots, emptyMap())).isEqualTo(DayPlan.Workout("chest"))
        assertThat(WeekSchedule.dayPlan(monday.plusDays(1), slots, emptyMap())).isEqualTo(DayPlan.Rest)
        assertThat(WeekSchedule.dayPlan(monday, slots, mapOf(monday to null))).isEqualTo(DayPlan.Rest)
        assertThat(WeekSchedule.dayPlan(monday.plusDays(7), slots, mapOf(monday to null))).isEqualTo(DayPlan.Workout("chest"))
    }

    @Test
    fun `swap with today trades the two dates and one routine can hold two weekdays`() {
        val slots = mapOf(1 to "chest", 4 to "chest")
        val tuesday = monday.plusDays(1)
        val swap = WeekSchedule.swap(tuesday, monday, slots, emptyMap())
        assertThat(swap).containsExactly(tuesday, "chest", monday, null)
        assertThat(WeekSchedule.dayPlan(tuesday, slots, swap)).isEqualTo(DayPlan.Workout("chest"))
        assertThat(WeekSchedule.dayPlan(monday, slots, swap)).isEqualTo(DayPlan.Rest)
        assertThat(WeekSchedule.swap(monday, monday, slots, emptyMap())).isEmpty()
        assertThat(WeekSchedule.sameWeek(monday, monday.plusDays(6))).isTrue()
        assertThat(WeekSchedule.sameWeek(monday, monday.plusDays(7))).isFalse()
    }

    // Benchmarks
    private val quantiles = (1..99).map { 0.5 + it * 0.01 } // 0.51 .. 1.49

    @Test
    fun `G-14 percentile interpolates and clamps to 1 and 99`() {
        assertThat(Benchmarks.percentile(quantiles[49], quantiles)).isWithin(1e-9).of(50.0)
        assertThat(Benchmarks.percentile(0.1, quantiles)).isEqualTo(1.0)
        assertThat(Benchmarks.percentile(5.0, quantiles)).isEqualTo(99.0)
        assertThat(Benchmarks.percentile(1.005, quantiles)).isWithin(1e-9).of(50.5)
        assertThat(Benchmarks.ratioAt(50.5, quantiles)).isWithin(1e-9).of(1.005)
        assertThat(Benchmarks.ratioAt(50.0, quantiles)).isWithin(1e-9).of(quantiles[49])
    }

    @Test
    fun `levels follow the percentile bands and standards follow points`() {
        assertThat(listOf(1.0, 19.9, 20.0, 49.9, 50.0, 79.9, 80.0, 94.9, 95.0, 99.0).map { Benchmarks.level(it) }).containsExactly(
            StrengthLevel.BEGINNER, StrengthLevel.BEGINNER, StrengthLevel.NOVICE, StrengthLevel.NOVICE, StrengthLevel.INTERMEDIATE,
            StrengthLevel.INTERMEDIATE, StrengthLevel.ADVANCED, StrengthLevel.ADVANCED, StrengthLevel.ELITE, StrengthLevel.ELITE,
        ).inOrder()
        assertThat(Benchmarks.standardLevel(39, 40)).isEqualTo(StrengthLevel.BEGINNER)
        assertThat(Benchmarks.standardLevel(50, 40)).isEqualTo(StrengthLevel.NOVICE)
        assertThat(Benchmarks.standardLevel(60, 60)).isEqualTo(StrengthLevel.INTERMEDIATE)
        assertThat(Benchmarks.standardLevel(99, 40)).isEqualTo(StrengthLevel.ADVANCED)
        assertThat(Benchmarks.standardLevel(100, 40)).isEqualTo(StrengthLevel.ELITE)
    }

    @Test
    fun `points are the highest row reached and the curve is relative`() {
        val table = StandardTable("pullup", "M", 22, 26, "reps", 40, listOf(listOf(5.0, 40.0), listOf(10.0, 70.0), listOf(23.0, 100.0)), "t", "t")
        assertThat(Benchmarks.points(4.0, table)).isEqualTo(0)
        assertThat(Benchmarks.points(10.0, table)).isEqualTo(70)
        assertThat(Benchmarks.points(30.0, table)).isEqualTo(100)
        val curve = Benchmarks.curve(quantiles)
        assertThat(curve.maxOf { it.second }).isEqualTo(1.0)
        assertThat(curve.first().first).isEqualTo(quantiles.first())
        assertThat(Benchmarks.ratio(100.0, 80.0)).isEqualTo(1.25)
        assertThat(Benchmarks.ratio(100.0, 0.0)).isNull()
    }
}
