package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Verification
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SetCounterTest {

    @Test
    fun `only SELF rep quests get a counter`() {
        assertThat(SetCounter.appliesTo("reps", Verification.SELF)).isTrue()
        assertThat(SetCounter.appliesTo("min", Verification.SELF)).isFalse()
        assertThat(SetCounter.appliesTo("reps", Verification.TIMER)).isFalse()
        assertThat(SetCounter.appliesTo("reps", Verification.HEALTH_EXERCISE)).isFalse()
    }

    @Test
    fun `sets add up and round-trip through the stored text`() {
        val sets = SetCounter.add(SetCounter.add(emptyList(), 10), 8)

        assertThat(SetCounter.total(sets)).isEqualTo(18)
        assertThat(SetCounter.format(sets)).isEqualTo("10,8")
        assertThat(SetCounter.parse("10,8")).containsExactly(10, 8).inOrder()
        assertThat(SetCounter.parse("")).isEmpty()
    }

    @Test
    fun `malformed stored text is skipped not trusted`() {
        assertThat(SetCounter.parse("10,abc,0,-4,301,12,")).containsExactly(10, 12).inOrder()
    }

    @Test
    fun `reps per set are clamped to 1 and 300`() {
        assertThat(SetCounter.add(emptyList(), 0)).containsExactly(1)
        assertThat(SetCounter.add(emptyList(), 9_999)).containsExactly(300)
    }

    @Test
    fun `a full counter ignores another set`() {
        val full = List(SetCounter.MAX_SETS) { 5 }

        assertThat(SetCounter.add(full, 5)).isEqualTo(full)
        assertThat(SetCounter.parse(SetCounter.format(full + 7))).hasSize(SetCounter.MAX_SETS)
    }

    @Test
    fun `undo removes only the last set and is safe on an empty counter`() {
        assertThat(SetCounter.undoLast(listOf(10, 8, 6))).containsExactly(10, 8).inOrder()
        assertThat(SetCounter.undoLast(emptyList())).isEmpty()
    }

    @Test
    fun `the target is reached at exactly the target and never completes anything by itself`() {
        assertThat(SetCounter.isTargetReached(listOf(10, 15), 25)).isTrue()
        assertThat(SetCounter.isTargetReached(listOf(10, 14), 25)).isFalse()
        assertThat(SetCounter.remaining(listOf(10, 14), 25)).isEqualTo(1)
        assertThat(SetCounter.remaining(listOf(30), 25)).isEqualTo(0)
    }

    @Test
    fun `the stepper starts at what is missing, then at the last set`() {
        assertThat(SetCounter.suggestedReps(emptyList(), 25)).isEqualTo(25)
        assertThat(SetCounter.suggestedReps(listOf(10), 25)).isEqualTo(15)
        assertThat(SetCounter.suggestedReps(listOf(10, 15), 25)).isEqualTo(15)
        assertThat(SetCounter.suggestedReps(emptyList(), 0)).isEqualTo(1)
    }
}
