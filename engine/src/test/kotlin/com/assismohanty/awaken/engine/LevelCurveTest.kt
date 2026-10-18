package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LevelCurveTest {

    @Test
    fun `cumulative is strictly increasing`() {
        for (level in 1 until LevelCurve.MAX_LEVEL) {
            assertThat(LevelCurve.cumulative(level + 1)).isGreaterThan(LevelCurve.cumulative(level))
        }
    }

    @Test
    fun `level of cumulative(L) is L for every level`() {
        for (level in 1..LevelCurve.MAX_LEVEL) {
            assertThat(LevelCurve.level(LevelCurve.cumulative(level))).isEqualTo(level)
        }
    }

    @Test
    fun `one XP short of cumulative(L) is level L-1`() {
        for (level in 2..LevelCurve.MAX_LEVEL) {
            assertThat(LevelCurve.level(LevelCurve.cumulative(level) - 1)).isEqualTo(level - 1)
        }
    }

    @Test
    fun `level is capped at 100`() {
        assertThat(LevelCurve.level(Long.MAX_VALUE)).isEqualTo(100)
        assertThat(LevelCurve.progress(LevelCurve.cumulative(100) + 5).xpForLevel).isEqualTo(0)
    }

    @Test
    fun `negative total XP clamps to level 1`() {
        assertThat(LevelCurve.level(-50)).isEqualTo(1)
    }

    @Test
    fun `calibration table in GD 4_4 matches the formula`() {
        val expected = mapOf(
            2 to (112 to 112L), 5 to (210 to 628L), 10 to (504 to 2_505L), 20 to (1_434 to 12_326L),
            35 to (3_485 to 49_372L), 50 to (6_174 to 122_432L), 70 to (10_604 to 290_924L),
        )
        expected.forEach { (level, stepAndCumulative) ->
            assertThat(LevelCurve.xpToNext(level - 1)).isEqualTo(stepAndCumulative.first)
            assertThat(LevelCurve.cumulative(level)).isEqualTo(stepAndCumulative.second)
        }
        assertThat(LevelCurve.cumulative(100)).isEqualTo(731_908L)
    }

    @Test
    fun `progress reports XP within the current level`() {
        val progress = LevelCurve.progress(150)
        assertThat(progress).isEqualTo(LevelCurve.Progress(level = 2, xpIntoLevel = 38, xpForLevel = 136))
    }
}
