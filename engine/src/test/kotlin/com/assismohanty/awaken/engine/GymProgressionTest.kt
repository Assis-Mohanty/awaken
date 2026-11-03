package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.body.ExerciseHistory
import com.assismohanty.awaken.engine.body.ExerciseSession
import com.assismohanty.awaken.engine.body.HistoryMetric
import com.assismohanty.awaken.engine.body.HistoryRange
import com.assismohanty.awaken.engine.body.LoggedSet
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.PlatePlan
import com.assismohanty.awaken.engine.body.PlateMath
import com.assismohanty.awaken.engine.body.PlateUnit
import com.assismohanty.awaken.engine.body.ProgressionHint
import com.assismohanty.awaken.engine.body.ProgressionRules
import com.assismohanty.awaken.engine.body.RecordRules
import com.assismohanty.awaken.engine.body.RecordType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** GD §16.6-16.9 progression hints, records, plates and history (vectors #103-#112). */
class GymProgressionTest {
    private fun sets(weight: Double, vararg reps: Int) = reps.map { LoggedSet(weight, it) }

    private val range = ProgressionRules.DEFAULT_RANGE
    private val upper = ProgressionRules.defaultIncrement(setOf(Muscle.CHEST))
    private val lower = ProgressionRules.defaultIncrement(setOf(Muscle.QUADRICEPS, Muscle.GLUTES))

    @Test
    fun `vector 103 - every set at the top of the range adds the upper-body step and restarts at the bottom`() {
        val hint = ProgressionRules.suggest(sets(50.0, 12, 12, 12), range, upper)
        assertThat(hint).isEqualTo(ProgressionHint.Suggest(52.5, listOf(8, 8, 8)))
    }

    @Test
    fun `vector 104 - otherwise keep the weight and add one rep per set up to the top`() {
        val hint = ProgressionRules.suggest(sets(50.0, 12, 12, 10), range, upper)
        assertThat(hint).isEqualTo(ProgressionHint.Suggest(50.0, listOf(12, 12, 11)))
    }

    @Test
    fun `vector 105 - lower-body exercises step by 5 kg`() {
        assertThat(lower).isEqualTo(5.0)
        val hint = ProgressionRules.suggest(sets(100.0, 12, 12, 12), range, lower)
        assertThat(hint).isEqualTo(ProgressionHint.Suggest(105.0, listOf(8, 8, 8)))
    }

    @Test
    fun `vector 106 - an unweighted exercise at the top gets no number`() {
        assertThat(ProgressionRules.suggest(sets(0.0, 12, 12, 12), range, upper)).isEqualTo(ProgressionHint.AddWeightOrVariation)
        assertThat(ProgressionRules.suggest(sets(0.0, 12, 12, 9), range, upper)).isEqualTo(ProgressionHint.Suggest(0.0, listOf(12, 12, 10)))
    }

    @Test
    fun `only the heaviest weight counts as working sets and warm-ups and undone sets are ignored`() {
        val last = listOf(LoggedSet(40.0, 12), LoggedSet(60.0, 12), LoggedSet(60.0, 12), LoggedSet(60.0, 3, done = false))
        assertThat(ProgressionRules.suggest(last, range, upper)).isEqualTo(ProgressionHint.Suggest(62.5, listOf(8, 8)))
    }

    @Test
    fun `a custom range changes where the weight goes up`() {
        val hint = ProgressionRules.suggest(sets(50.0, 6, 6), com.assismohanty.awaken.engine.body.RepRange(3, 6), upper)
        assertThat(hint).isEqualTo(ProgressionHint.Suggest(52.5, listOf(3, 3)))
    }

    @Test
    fun `vector 107 - no earlier session means no hint and no record flags`() {
        assertThat(ProgressionRules.suggest(emptyList(), range, upper)).isNull()
        assertThat(RecordRules.events(emptyList(), sets(100.0, 5, 5))).isEmpty()
        assertThat(RecordRules.setFlags(emptyList(), sets(100.0, 5, 5)).flatten()).isEmpty()
    }

    private val history = listOf(sets(100.0, 3) + sets(80.0, 8))

    @Test
    fun `vector 108 - heaviest weight and reps at a weight are records, a lower e1RM is not`() {
        val today = listOf(LoggedSet(102.5, 1), LoggedSet(80.0, 9))
        val types = RecordRules.events(history, today).map { it.type }
        assertThat(types).containsAtLeast(RecordType.HEAVIEST_WEIGHT, RecordType.MOST_REPS_AT_WEIGHT)
        assertThat(types).doesNotContain(RecordType.BEST_E1RM)
        val reps = RecordRules.events(history, today).first { it.type == RecordType.MOST_REPS_AT_WEIGHT }
        assertThat(reps.weightKg).isEqualTo(80.0)
        assertThat(reps.value).isEqualTo(9.0)
        // The earlier session was one block of 940 kg volume; 822.5 does not beat it.
        assertThat(types).doesNotContain(RecordType.BEST_SESSION_VOLUME)
    }

    @Test
    fun `vector 109 - a tie is not a record`() {
        assertThat(RecordRules.events(history, sets(100.0, 3))).isEmpty()
    }

    @Test
    fun `a set beating an earlier set of the same session counts only once`() {
        val flags = RecordRules.setFlags(history, listOf(LoggedSet(105.0, 1), LoggedSet(105.0, 1), LoggedSet(107.5, 1)))
        assertThat(flags[0]).contains(RecordType.HEAVIEST_WEIGHT)
        assertThat(flags[1]).doesNotContain(RecordType.HEAVIEST_WEIGHT)
        assertThat(flags[2]).contains(RecordType.HEAVIEST_WEIGHT)
        assertThat(RecordRules.events(history, listOf(LoggedSet(105.0, 1), LoggedSet(107.5, 1))).count { it.type == RecordType.HEAVIEST_WEIGHT }).isEqualTo(1)
    }

    @Test
    fun `event count walks the sessions oldest first and the first session scores nothing`() {
        val sessions = listOf(sets(50.0, 10), sets(55.0, 10), sets(55.0, 10))
        // Session 2 beats session 1 on heaviest, e1RM and volume; session 3 only ties.
        assertThat(RecordRules.eventCount(sessions)).isEqualTo(3)
    }

    @Test
    fun `vector 111 - kg plates`() {
        val setup = PlateMath.defaults(PlateUnit.KG)
        assertThat(PlateMath.plan(100.0, setup)).isEqualTo(PlatePlan.Exact(listOf(25.0, 15.0)))
        assertThat(PlateMath.plan(62.5, setup)).isEqualTo(PlatePlan.Exact(listOf(20.0, 1.25)))
        assertThat(PlateMath.plan(61.0, setup)).isEqualTo(PlatePlan.Short(listOf(20.0), 60.0, 1.0))
        assertThat(PlateMath.plan(15.0, setup)).isEqualTo(PlatePlan.BelowBar(20.0))
        assertThat(PlateMath.plan(20.0, setup)).isEqualTo(PlatePlan.Exact(emptyList()))
    }

    @Test
    fun `vector 112 - lb plates`() {
        val setup = PlateMath.defaults(PlateUnit.LB)
        assertThat(PlateMath.plan(225.0, setup)).isEqualTo(PlatePlan.Exact(listOf(45.0, 45.0)))
        assertThat(PlateMath.plan(135.0, setup)).isEqualTo(PlatePlan.Exact(listOf(45.0)))
    }

    @Test
    fun `owned plates limit what can be built`() {
        val setup = com.assismohanty.awaken.engine.body.PlateSetup(20.0, listOf(20.0, 10.0))
        assertThat(PlateMath.plan(100.0, setup)).isEqualTo(PlatePlan.Exact(listOf(20.0, 20.0)))
        assertThat(PlateMath.plan(70.0, setup)).isEqualTo(PlatePlan.Short(listOf(20.0), 60.0, 10.0))
    }

    private val today = LocalDate.of(2026, 11, 2)

    private fun session(daysAgo: Long, weight: Double, vararg reps: Int) = ExerciseSession(today.minusDays(daysAgo), sets(weight, *reps))

    @Test
    fun `history series use the best done set per session and skip e1RM when none qualifies`() {
        val s = session(3, 80.0, 5, 5, 4)
        assertThat(ExerciseHistory.value(s, HistoryMetric.TOP_WEIGHT)).isEqualTo(80.0)
        assertThat(ExerciseHistory.value(s, HistoryMetric.VOLUME)).isEqualTo(1120.0)
        assertThat(ExerciseHistory.value(s, HistoryMetric.E1RM)).isWithin(1e-9).of(80.0 * (1 + 5 / 30.0))
        assertThat(ExerciseHistory.value(session(1, 80.0, 20), HistoryMetric.E1RM)).isNull()
    }

    @Test
    fun `ranges cut by date and Year and All keep the best session of each ISO week`() {
        val sessions = listOf(session(2, 100.0, 5), session(4, 90.0, 5), session(40, 80.0, 5), session(400, 60.0, 5))
        assertThat(ExerciseHistory.series(sessions, HistoryMetric.TOP_WEIGHT, HistoryRange.MONTH, today)).hasSize(2)
        assertThat(ExerciseHistory.series(sessions, HistoryMetric.TOP_WEIGHT, HistoryRange.THREE_MONTHS, today)).hasSize(3)
        // 2026-10-29 and 2026-10-31 share an ISO week, so Year keeps only the better one.
        val year = ExerciseHistory.series(sessions, HistoryMetric.TOP_WEIGHT, HistoryRange.YEAR, today)
        assertThat(year.map { it.value }).containsExactly(80.0, 100.0).inOrder()
        assertThat(ExerciseHistory.series(sessions, HistoryMetric.TOP_WEIGHT, HistoryRange.ALL, today)).hasSize(3)
    }

}
