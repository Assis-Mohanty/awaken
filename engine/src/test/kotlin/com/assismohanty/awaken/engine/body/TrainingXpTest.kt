package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.jupiter.api.Test

/** GD §18 vectors G-1..G-9 (XP). */
class TrainingXpTest {
    private val t0 = Instant.parse("2026-11-10T10:00:00Z")

    private fun set(
        id: String,
        index: Int,
        atSec: Long?,
        exercise: String = "bench",
        weight: Double = 60.0,
        reps: Int = 8,
        rest: Int? = 120,
        warmup: Boolean = false,
        flagged: Boolean = false,
        best: Double? = null,
        hold: Int? = null,
    ) = XpSet(id, exercise, index, weight, reps, hold, warmup, atSec?.let { t0.plusSeconds(it) }, rest, flagged, best)

    @Test
    fun `G-1 three work sets with enough rest pay 15 XP`() {
        val r = TrainingXp.evaluate(listOf(set("a", 0, 0), set("b", 1, 95), set("c", 2, 190)))
        assertThat(r.countedSets).isEqualTo(3)
        assertThat(r.xp).isEqualTo(15)
    }

    @Test
    fun `G-2 a set under the minimum rest does not count`() {
        val r = TrainingXp.evaluate(listOf(set("a", 0, 0), set("b", 1, 20), set("c", 2, 190)))
        assertThat(r.verdicts["b"]).isEqualTo(SetVerdict.TOO_SOON)
        assertThat(r.xp).isEqualTo(10)
    }

    @Test
    fun `G-3 warm-ups pay nothing`() {
        val r = TrainingXp.evaluate(
            listOf(set("w1", 0, 0, warmup = true), set("w2", 1, 60, warmup = true), set("a", 2, 200), set("b", 3, 400), set("c", 4, 600)),
        )
        assertThat(r.verdicts["w1"]).isEqualTo(SetVerdict.WARMUP)
        assertThat(r.xp).isEqualTo(15)
    }

    @Test
    fun `G-4 a light weighted set counts half and 2_5 rounds up`() {
        val r = TrainingXp.evaluate(listOf(set("a", 0, 0, weight = 40.0, reps = 10, best = 100.0)))
        assertThat(r.verdicts["a"]).isEqualTo(SetVerdict.COUNTED_LIGHT)
        assertThat(r.rawXp).isEqualTo(3)
    }

    @Test
    fun `G-5 only ten sets per exercise per day count`() {
        val sets = (0 until 14).map { set("s$it", it, it * 200L) }
        val r = TrainingXp.evaluate(sets)
        assertThat(r.countedSets).isEqualTo(10)
        assertThat(r.verdicts["s10"]).isEqualTo(SetVerdict.EXERCISE_LIMIT)
        assertThat(r.xp).isEqualTo(50)
    }

    @Test
    fun `G-5b earlier workouts the same day use up the per-exercise limit`() {
        val r = TrainingXp.evaluate(listOf(set("a", 0, 0), set("b", 1, 200)), priorCountedByExercise = mapOf("bench" to 9))
        assertThat(r.countedSets).isEqualTo(1)
    }

    @Test
    fun `G-6 the daily gym cap is 150`() {
        val first = TrainingXp.evaluate(rows(25, "a"))
        assertThat(first.xp).isEqualTo(125)
        val second = TrainingXp.evaluate(rows(15, "b"), gymEarnedToday = 125, selfEarnedToday = 125)
        assertThat(second.rawXp).isEqualTo(75)
        assertThat(second.xp).isEqualTo(25)
        assertThat(second.cappedBy).isEqualTo(XpCap.DAILY_GYM)
    }

    @Test
    fun `G-7 gym XP also counts toward the self-report cap`() {
        val r = TrainingXp.evaluate(rows(20, "a"), selfEarnedToday = 580)
        assertThat(r.rawXp).isEqualTo(100)
        assertThat(r.xp).isEqualTo(20)
        assertThat(r.cappedBy).isEqualTo(XpCap.SELF)
    }

    @Test
    fun `G-8 ledger rows are keyed by the workout so finishing twice is a no-op`() {
        val rows = TrainingXp.ledger("w1", 45)
        assertThat(rows.map { it.kind }).containsExactly(LedgerKind.XP_TOTAL, LedgerKind.XP_STAT)
        assertThat(rows.map { it.sourceType }.distinct()).containsExactly(SourceType.GYM_WORKOUT)
        assertThat(rows.last().stat).isEqualTo("STR")
        assertThat(TrainingXp.ledger("w1", 45).map { it.key }).isEqualTo(rows.map { it.key })
        assertThat(TrainingXp.ledger("w1", 0)).isEmpty()
    }

    @Test
    fun `G-9 deleting a workout reverses the rows under UNDO and leaves the originals`() {
        val originals = TrainingXp.ledger("w1", 45)
        val undo = TrainingXp.reversal(originals)
        assertThat(undo.map { it.delta }).containsExactly(-45L, -45L)
        assertThat(undo.map { it.sourceType }.distinct()).containsExactly(SourceType.UNDO)
        assertThat(undo.map { it.sourceId }.distinct()).containsExactly("w1")
    }

    @Test
    fun `G-12 superset partners and the two sides of a set are not held back`() {
        val r = TrainingXp.evaluate(
            listOf(
                set("a1", 0, 0, exercise = "pushdown", rest = 60),
                set("a2", 0, 5, exercise = "skull", rest = 60),
                set("a1b", 1, 45, exercise = "pushdown", rest = 60),
                set("l", 0, 0, exercise = "row", rest = 60),
                set("r", 0, 3, exercise = "row", rest = 60),
            ),
        )
        assertThat(r.verdicts.values.toSet()).containsExactly(SetVerdict.COUNTED_FULL)
    }

    @Test
    fun `sets without a completion time, empty sets and flagged sets pay nothing`() {
        val r = TrainingXp.evaluate(
            listOf(
                set("old", 0, null),
                set("empty", 1, 0, reps = 0),
                set("flag", 2, 200, flagged = true),
            ),
        )
        assertThat(r.verdicts["old"]).isEqualTo(SetVerdict.NOT_TIMED)
        assertThat(r.verdicts["empty"]).isEqualTo(SetVerdict.EMPTY)
        assertThat(r.verdicts["flag"]).isEqualTo(SetVerdict.FLAGGED)
        assertThat(r.xp).isEqualTo(0)
    }

    @Test
    fun `bodyweight and timed sets follow the light rules`() {
        val r = TrainingXp.evaluate(
            listOf(
                set("few", 0, 0, exercise = "pullup", weight = 0.0, reps = 3),
                set("many", 1, 200, exercise = "pullup", weight = 0.0, reps = 8),
                set("short", 0, 0, exercise = "plank", weight = 0.0, reps = 0, hold = 15),
                set("long", 1, 200, exercise = "plank", weight = 0.0, reps = 0, hold = 45),
                set("tiny", 2, 400, exercise = "plank", weight = 0.0, reps = 0, hold = 5),
            ),
        )
        assertThat(r.verdicts["few"]).isEqualTo(SetVerdict.COUNTED_LIGHT)
        assertThat(r.verdicts["many"]).isEqualTo(SetVerdict.COUNTED_FULL)
        assertThat(r.verdicts["short"]).isEqualTo(SetVerdict.COUNTED_LIGHT)
        assertThat(r.verdicts["long"]).isEqualTo(SetVerdict.COUNTED_FULL)
        assertThat(r.verdicts["tiny"]).isEqualTo(SetVerdict.EMPTY)
    }

    private fun rows(count: Int, prefix: String): List<XpSet> =
        (0 until count).map { set("$prefix$it", it % 20, it * 200L, exercise = "ex${it / 10}") }
}
