package com.assismohanty.awaken.engine.insight

import com.assismohanty.awaken.engine.model.TimeOfDay
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * GAME_DESIGN §14.10 vectors 59–70. q(d) is a whole number, so where a vector's mean can't be hit with its day count
 * (e.g. 6 days averaging 2.4) the bucket sizes are adjusted to hit the same means; the guardrail being tested is unchanged.
 */
class InsightVectorsTest {

    /** A Wednesday, so day offsets map to weekdays predictably. */
    private val asOf = LocalDate.of(2026, 10, 28)

    private fun day(offset: Long, q: Int, sleepHours: Int? = null, steps: Long? = null, blocks: Map<TimeOfDay, BlockTally> = emptyMap()) =
        InsightDay(asOf.minusDays(offset), q, sleepHours?.let { it * 60 }, steps, blocks)

    /** [count] days starting at [from] (offset), each with [q]. */
    private fun run(from: Long, count: Int, q: Int, sleepHours: Int? = null, steps: Long? = null) =
        (0 until count).map { day(from + it, q, sleepHours, steps) }

    private fun result(days: List<InsightDay>, gates: List<GateStart> = emptyList(), clears: Set<LocalDate> = emptySet()) =
        InsightEngine.evaluate(InsightHistory(days, gates, clears), asOf)

    private inline fun <reified T : Insight> InsightResult.find(rule: InsightRule): T? =
        insights.firstOrNull { it.rule == rule } as T?

    @Test
    fun `vector 59 - 13 analysable days show nothing and need 1 more`() {
        val r = result(run(0, 13, q = 3))
        assertThat(r.insights).isEmpty()
        assertThat(r.daysNeeded).isEqualTo(1)
    }

    @Test
    fun `vector 60 - a sleep bucket under 5 days is not shown`() {
        // ≥ 7 h: 6 days mean 3.33; < 7 h: 4 days mean 2.0; 6 filler days without a sleep session.
        val days = run(0, 6, q = 3, sleepHours = 8).mapIndexed { i, d -> if (i < 2) d.copy(completions = 4) else d } +
            run(6, 4, q = 2, sleepHours = 6) + run(10, 6, q = 3)
        assertThat(result(days).find<BucketInsight>(InsightRule.SLEEP)).isNull()
    }

    @Test
    fun `vector 61 - sleep 3_3 vs 2_4 is shown as 38 percent more`() {
        // ≥ 7 h: 10 days summing 33 (3.3); < 7 h: 5 days summing 12 (2.4). abs 0.9, rel 0.375.
        val a = run(0, 10, q = 3, sleepHours = 8).mapIndexed { i, d -> if (i < 3) d.copy(completions = 4) else d }
        val b = run(10, 5, q = 2, sleepHours = 6).mapIndexed { i, d -> if (i < 2) d.copy(completions = 3) else d }
        val insight = result(a + b).find<BucketInsight>(InsightRule.SLEEP)
        assertThat(insight).isNotNull()
        assertThat(insight?.aIsHigher).isTrue()
        assertThat(insight?.percent).isEqualTo(38)
    }

    @Test
    fun `vector 62 - steps difference under half a quest is not shown`() {
        // ≥ 7,000: 2.2 over 10 days; fewer: 1.9 over 10 days. abs 0.3.
        val a = run(0, 10, q = 2, steps = 9_000).mapIndexed { i, d -> if (i < 2) d.copy(completions = 3) else d }
        val b = run(10, 10, q = 2, steps = 3_000).mapIndexed { i, d -> if (i < 1) d.copy(completions = 1) else d }
        assertThat(result(a + b).find<BucketInsight>(InsightRule.STEPS)).isNull()
    }

    @Test
    fun `vector 63 - steps 1_0 vs 0_4 is shown as 150 percent more`() {
        val a = run(0, 6, q = 1, steps = 8_000)
        val b = run(6, 10, q = 0, steps = 2_000).mapIndexed { i, d -> if (i < 4) d.copy(completions = 1) else d }
        val insight = result(a + b).find<BucketInsight>(InsightRule.STEPS)
        assertThat(insight?.percent).isEqualTo(150)
        assertThat(insight?.aIsHigher).isTrue()
    }

    @Test
    fun `steps with a zero lower bucket pass and carry no percent`() {
        val insight = result(run(0, 7, q = 1, steps = 8_000) + run(7, 7, q = 0, steps = 2_000)).find<BucketInsight>(InsightRule.STEPS)
        assertThat(insight?.percent).isNull()
    }

    @Test
    fun `vector 64 - weekday ignores thin weekdays and picks Tuesday over Sunday`() {
        val days = (0L until 84L).map { asOf.minusDays(it) }.mapNotNull { date ->
            val weekOfWindow = (asOf.toEpochDay() - date.toEpochDay()) / 7
            when (date.dayOfWeek) {
                DayOfWeek.TUESDAY -> if (weekOfWindow < 6) InsightDay(date, 4) else null
                DayOfWeek.SUNDAY -> if (weekOfWindow < 6) InsightDay(date, if (weekOfWindow < 3) 2 else 3) else null
                DayOfWeek.WEDNESDAY -> if (weekOfWindow < 3) InsightDay(date, 0) else null
                else -> if (weekOfWindow < 5) InsightDay(date, 3) else null
            }
        }
        val insight = result(days).find<WeekdayInsight>(InsightRule.WEEKDAY)
        assertThat(insight?.best).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(insight?.worst).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(insight?.percent).isEqualTo(60)
    }

    @Test
    fun `vector 65 - morning completes more than evening`() {
        val days = (0 until 14).map { i ->
            val blocks = buildMap {
                if (i < 10) put(TimeOfDay.MORNING, if (i < 2) BlockTally(3, 1) else BlockTally(2, 2)) // 18 of 22
                if (i < 9) put(TimeOfDay.EVENING, BlockTally(2, 1)) // 9 of 18
                if (i < 6) put(TimeOfDay.DAY, BlockTally(1, 1).let { if (i < 2) BlockTally(1, 0) else it }) // 4 of 6
            }
            day(i.toLong(), q = 3, blocks = blocks)
        }
        val insight = result(days).find<BlockInsight>(InsightRule.TIME_OF_DAY)
        assertThat(insight?.best).isEqualTo(TimeOfDay.MORNING)
        assertThat(insight?.worst).isEqualTo(TimeOfDay.EVENING)
        assertThat(insight?.percent).isEqualTo(64)
    }

    @Test
    fun `vector 66 - focus gates cluster around 9`() {
        val gates = List(4) { GateStart(asOf, 9) } + List(3) { GateStart(asOf, 21) } + listOf(7, 13, 17).map { GateStart(asOf, it) }
        val insight = result(run(0, 14, q = 2), gates).find<FocusHourInsight>(InsightRule.FOCUS_HOUR)
        assertThat(insight).isEqualTo(FocusHourInsight(hour = 9, gates = 4, total = 10))
    }

    @Test
    fun `vector 67 - fewer than 8 gates show no focus hour`() {
        val gates = List(7) { GateStart(asOf, 9) }
        assertThat(result(run(0, 14, q = 2), gates).find<FocusHourInsight>(InsightRule.FOCUS_HOUR)).isNull()
    }

    @Test
    fun `a tie for the top focus hour shows nothing`() {
        val gates = List(4) { GateStart(asOf, 9) } + List(4) { GateStart(asOf, 21) }
        assertThat(result(run(0, 14, q = 2), gates).find<FocusHourInsight>(InsightRule.FOCUS_HOUR)).isNull()
    }

    @Test
    fun `momentum compares the 3 days after a trial stage with the rest`() {
        val clears = setOf(asOf.minusDays(10), asOf.minusDays(20))
        val days = run(0, 30, q = 1).map { d ->
            val after = (1L..3L).any { d.date.minusDays(it) in clears }
            if (after) d.copy(completions = 3) else d
        }
        val insight = result(days, clears = clears).find<BucketInsight>(InsightRule.MOMENTUM)
        assertThat(insight?.aIsHigher).isTrue()
        assertThat(insight?.percent).isEqualTo(200)
    }

    private val fiveQualifying: List<Insight> = listOf(
        BucketInsight(InsightRule.SLEEP, true, 3.0, 2.0, 50),
        WeekdayInsight(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY, 4.0, 2.0, 100),
        BlockInsight(TimeOfDay.MORNING, TimeOfDay.EVENING, 0.8, 0.5, 60),
        FocusHourInsight(9, 4, 10),
        BucketInsight(InsightRule.STEPS, true, 1.0, 0.4, 150),
    )

    @Test
    fun `vector 68 - last week's base comes last`() {
        val previousBase = fiveQualifying.filter { it.rule in setOf(InsightRule.SLEEP, InsightRule.WEEKDAY, InsightRule.STEPS) }
        val selected = InsightEngine.select("hunter-1", "2026-W44", fiveQualifying, previousBase)
        assertThat(selected).hasSize(3)
        assertThat(selected.take(2).map { it.rule }).containsExactly(InsightRule.TIME_OF_DAY, InsightRule.FOCUS_HOUR)
        assertThat(selected.last().rule).isIn(listOf(InsightRule.SLEEP, InsightRule.WEEKDAY, InsightRule.STEPS))
    }

    @Test
    fun `vector 69 - a lone insight repeats week after week`() {
        val only = listOf(fiveQualifying[1])
        val w1 = InsightEngine.select("hunter-1", "2026-W44", only, previousBase = emptyList())
        val w2 = InsightEngine.select("hunter-1", "2026-W45", only, previousBase = InsightEngine.base("hunter-1", "2026-W44", only))
        assertThat(w1).containsExactlyElementsIn(only)
        assertThat(w2).containsExactlyElementsIn(only)
    }

    @Test
    fun `vector 70 - the same history and week give the same list`() {
        val a = run(0, 10, q = 3, sleepHours = 8) + run(10, 6, q = 1, sleepHours = 6)
        assertThat(result(a)).isEqualTo(result(a))
        val first = InsightEngine.select("hunter-1", "2026-W44", fiveQualifying, emptyList())
        val second = InsightEngine.select("hunter-1", "2026-W44", fiveQualifying, emptyList())
        assertThat(first).containsExactlyElementsIn(second).inOrder()
    }
}
