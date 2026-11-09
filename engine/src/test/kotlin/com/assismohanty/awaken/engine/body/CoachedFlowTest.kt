package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CoachedFlowTest {
    private fun row(position: Int, setIndex: Int, done: Boolean = false, side: String? = null, superset: String? = null) =
        FlowRow(position, setIndex, side, done, superset)

    @Test
    fun `plain exercises run in order, set by set`() {
        val rows = listOf(row(0, 0), row(0, 1), row(1, 0))
        assertThat(CoachedFlow.current(rows)).isEqualTo(FlowCursor(0, 0, null))
        val next = listOf(row(0, 0, done = true), row(0, 1), row(1, 0))
        assertThat(CoachedFlow.current(next)).isEqualTo(FlowCursor(0, 1, null))
        val moved = listOf(row(0, 0, true), row(0, 1, true), row(1, 0))
        assertThat(CoachedFlow.current(moved)).isEqualTo(FlowCursor(1, 0, null))
        assertThat(CoachedFlow.current(moved.map { it.copy(done = true) })).isNull()
        assertThat(CoachedFlow.finished(moved.map { it.copy(done = true) })).isTrue()
    }

    @Test
    fun `superset partners take turns round by round`() {
        fun rows(vararg done: Boolean) = listOf(
            row(0, 0, done[0], superset = "A"), row(1, 0, done[1], superset = "A"),
            row(0, 1, done[2], superset = "A"), row(1, 1, done[3], superset = "A"),
            row(2, 0),
        )
        assertThat(CoachedFlow.current(rows(false, false, false, false))).isEqualTo(FlowCursor(0, 0, null))
        assertThat(CoachedFlow.current(rows(true, false, false, false))).isEqualTo(FlowCursor(1, 0, null))
        assertThat(CoachedFlow.current(rows(true, true, false, false))).isEqualTo(FlowCursor(0, 1, null))
        assertThat(CoachedFlow.current(rows(true, true, true, true))).isEqualTo(FlowCursor(2, 0, null))
    }

    @Test
    fun `left comes before right and rest waits for the second side`() {
        val l = row(0, 0, side = "LEFT")
        val r = row(0, 0, side = "RIGHT")
        assertThat(CoachedFlow.current(listOf(r, l))).isEqualTo(FlowCursor(0, 0, "LEFT"))
        assertThat(CoachedFlow.restAfter(listOf(l, r), l)).isFalse()
        assertThat(CoachedFlow.restAfter(listOf(l.copy(done = true), r), r)).isTrue()
    }

    @Test
    fun `rest starts after the last exercise of a superset round only`() {
        val a0 = row(0, 0, superset = "A")
        val b0 = row(1, 0, superset = "A")
        val a1 = row(0, 1, superset = "A")
        val b1 = row(1, 1, superset = "A")
        assertThat(CoachedFlow.restAfter(listOf(a0, b0, a1, b1), a0)).isFalse()
        assertThat(CoachedFlow.restAfter(listOf(a0.copy(done = true), b0, a1, b1), b0)).isTrue()
        // A plain exercise always rests after a set.
        assertThat(CoachedFlow.restAfter(listOf(row(2, 0), row(2, 1)), row(2, 0))).isTrue()
    }

    @Test
    fun `set numbers count distinct set indexes and the last set is detected`() {
        val rows = listOf(row(0, 0, true, "LEFT"), row(0, 0, true, "RIGHT"), row(0, 1, side = "LEFT"), row(0, 1, side = "RIGHT"))
        assertThat(CoachedFlow.setNumber(rows, 0, 1)).isEqualTo(2 to 2)
        assertThat(CoachedFlow.onLastSet(rows)).isFalse()
        assertThat(CoachedFlow.onLastSet(rows.take(3).map { it.copy(done = true) } + rows[3])).isTrue()
    }

    @Test
    fun `a superset of exercises that are not neighbours is not one block`() {
        val rows = listOf(row(0, 0, superset = "A"), row(1, 0), row(2, 0, superset = "A"))
        assertThat(CoachedFlow.current(rows)).isEqualTo(FlowCursor(0, 0, null))
        val after = rows.map { if (it.position == 0) it.copy(done = true) else it }
        assertThat(CoachedFlow.current(after)).isEqualTo(FlowCursor(1, 0, null))
    }
}
