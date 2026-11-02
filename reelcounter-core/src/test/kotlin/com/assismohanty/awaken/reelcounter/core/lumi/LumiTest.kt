package com.assismohanty.awaken.reelcounter.core.lumi

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LumiTest {

    private fun stage(reels: Int, limit: Int?) = lumiStage(reels, limit).stage

    @Test
    fun `no limit - every threshold starts its stage and the count before it is still the previous one`() {
        val expected = listOf(0 to 1, 9 to 1, 10 to 2, 24 to 2, 25 to 3, 49 to 3, 50 to 4, 79 to 4, 80 to 5, 119 to 5, 120 to 6, 5_000 to 6)
        expected.forEach { (reels, stage) -> assertThat(stage(reels, null)).isEqualTo(stage) }
    }

    @Test
    fun `no limit - progress runs 0 up to 1 inside a stage and is 0 at the last`() {
        assertThat(lumiStage(0, null).progressInStage).isEqualTo(0f)
        assertThat(lumiStage(5, null).progressInStage).isWithin(1e-6f).of(0.5f)
        assertThat(lumiStage(10, null)).isEqualTo(LumiState(2, 0f))
        assertThat(lumiStage(119, null).progressInStage).isLessThan(1f)
        assertThat(lumiStage(120, null)).isEqualTo(LumiState(6, 0f))
        assertThat(lumiStage(900, null)).isEqualTo(LumiState(6, 0f))
    }

    @Test
    fun `limit of 50 - stages change every 10 reels and stage 6 is at the limit`() {
        val expected = listOf(0 to 1, 9 to 1, 10 to 2, 19 to 2, 20 to 3, 29 to 3, 30 to 4, 39 to 4, 40 to 5, 49 to 5, 50 to 6, 51 to 6, 400 to 6)
        expected.forEach { (reels, stage) -> assertThat(stage(reels, 50)).isEqualTo(stage) }
    }

    @Test
    fun `limit - progress is the fraction through the stage`() {
        assertThat(lumiStage(0, 50)).isEqualTo(LumiState(1, 0f))
        assertThat(lumiStage(5, 50).progressInStage).isWithin(1e-6f).of(0.5f)
        assertThat(lumiStage(10, 50)).isEqualTo(LumiState(2, 0f))
        assertThat(lumiStage(49, 50).progressInStage).isWithin(1e-6f).of(0.9f)
        assertThat(lumiStage(50, 50)).isEqualTo(LumiState(6, 0f))
    }

    @Test
    fun `limit that does not divide by 5 - stage boundaries use whole reels, never early or late`() {
        // limit 12: stages start at 0, 2.4, 4.8, 7.2, 9.6 -> the first reel of each stage is 3, 5, 8, 10
        val expected = listOf(0 to 1, 2 to 1, 3 to 2, 4 to 2, 5 to 3, 7 to 3, 8 to 4, 9 to 4, 10 to 5, 11 to 5, 12 to 6)
        expected.forEach { (reels, stage) -> assertThat(stage(reels, 12)).isEqualTo(stage) }
    }

    @Test
    fun `smallest limits still pass through every stage in order`() {
        assertThat((0..5).map { stage(it, 5) }).containsExactly(1, 2, 3, 4, 5, 6).inOrder()
        assertThat(stage(0, 1)).isEqualTo(1)
        assertThat(stage(1, 1)).isEqualTo(6)
    }

    @Test
    fun `a limit changed mid-day re-evaluates the same count`() {
        assertThat(stage(30, 100)).isEqualTo(2)
        assertThat(stage(30, 50)).isEqualTo(4) // tightened: more tired
        assertThat(stage(30, 200)).isEqualTo(1) // relaxed: recovers
        assertThat(stage(30, 30)).isEqualTo(6) // limit lowered to the count: switched off
        assertThat(stage(30, null)).isEqualTo(3) // limit removed: back to the default thresholds
    }

    @Test
    fun `day rollover - a new day starts again at stage 1 whatever the old limit or count was`() {
        assertThat(stage(180, 50)).isEqualTo(6)
        assertThat(lumiStage(0, 50)).isEqualTo(LumiState.Rested)
        assertThat(lumiStage(0, null)).isEqualTo(LumiState.Rested)
    }

    @Test
    fun `a limit that is not positive means no limit`() {
        assertThat(lumiStage(30, 0)).isEqualTo(lumiStage(30, null))
        assertThat(lumiStage(30, -5)).isEqualTo(lumiStage(30, null))
    }

    @Test
    fun `a negative count is treated as zero`() {
        assertThat(lumiStage(-3, 50)).isEqualTo(LumiState.Rested)
        assertThat(lumiStage(-3, null)).isEqualTo(LumiState.Rested)
    }

    @Test
    fun `huge counts and limits do not overflow`() {
        assertThat(lumiStage(Int.MAX_VALUE, Int.MAX_VALUE)).isEqualTo(LumiState(6, 0f))
        assertThat(stage(Int.MAX_VALUE - 1, Int.MAX_VALUE)).isEqualTo(5)
    }

    @Test
    fun `the stage never goes backwards as the count grows, with and without a limit`() {
        for (limit in listOf(null, 5, 12, 50, 99, 500)) {
            var last = 1
            for (reels in 0..700) {
                val now = stage(reels, limit)
                assertThat(now).isAtLeast(last)
                last = now
            }
            assertThat(last).isEqualTo(6)
        }
    }

    @Test
    fun `progress is always from 0 up to but not including 1`() {
        for (limit in listOf(null, 5, 12, 50, 99)) {
            for (reels in 0..300) {
                val progress = lumiStage(reels, limit).progressInStage
                assertThat(progress).isAtLeast(0f)
                assertThat(progress).isLessThan(1f)
            }
        }
    }

    @Test
    fun `thresholds are the proposed ones`() {
        assertThat(LumiRules.NO_LIMIT_THRESHOLDS).containsExactly(0, 10, 25, 50, 80, 120).inOrder()
    }

    @Test
    fun `an invalid state cannot be built`() {
        assertThrows(IllegalArgumentException::class.java) { LumiState(0, 0f) }
        assertThrows(IllegalArgumentException::class.java) { LumiState(7, 0f) }
        assertThrows(IllegalArgumentException::class.java) { LumiState(3, 1.5f) }
    }

    @Test
    fun `daily limit is the sum of the per-app limits, or null when none is set`() {
        assertThat(lumiDailyLimit(emptyList())).isNull()
        assertThat(lumiDailyLimit(listOf(30))).isEqualTo(30)
        assertThat(lumiDailyLimit(listOf(30, 20, 50))).isEqualTo(100)
        assertThat(lumiDailyLimit(listOf(Int.MAX_VALUE, Int.MAX_VALUE))).isEqualTo(Int.MAX_VALUE)
    }
}
