package com.assismohanty.awaken.core.data.usage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForegroundTallyTest {

    @Test
    fun aResumePauseVisitCounts() {
        val tally = ForegroundTally(windowStart = 0)
        tally.resumed("a", "Main", 1_000)
        tally.paused("a", "Main", 61_000)
        assertThat(tally.finish(100_000)).containsExactly("a", 60_000L)
    }

    @Test
    fun switchingActivitiesInsideOneAppIsOneContinuousVisit() {
        val tally = ForegroundTally(windowStart = 0)
        tally.resumed("a", "List", 0)
        tally.resumed("a", "Detail", 10_000) // Detail resumes before List reports its pause
        tally.paused("a", "List", 10_100)
        tally.paused("a", "Detail", 30_000)
        assertThat(tally.finish(100_000)).containsExactly("a", 30_000L)
    }

    @Test
    fun anAppAlreadyOpenAtTheWindowStartCountsFromTheStart() {
        val tally = ForegroundTally(windowStart = 5_000)
        tally.paused("a", "Main", 20_000)
        assertThat(tally.finish(100_000)).containsExactly("a", 15_000L)
    }

    @Test
    fun anAppStillOpenAtTheEndCountsUntilTheEnd() {
        val tally = ForegroundTally(windowStart = 0)
        tally.resumed("a", "Main", 40_000)
        assertThat(tally.finish(100_000)).containsExactly("a", 60_000L)
    }

    @Test
    fun separateVisitsAddUp() {
        val tally = ForegroundTally(windowStart = 0)
        tally.resumed("a", "Main", 0)
        tally.paused("a", "Main", 10_000)
        tally.resumed("b", "Main", 10_000)
        tally.paused("b", "Main", 15_000)
        tally.resumed("a", "Main", 15_000)
        tally.paused("a", "Main", 20_000)
        assertThat(tally.finish(100_000)).containsExactly("a", 15_000L, "b", 5_000L)
    }
}
