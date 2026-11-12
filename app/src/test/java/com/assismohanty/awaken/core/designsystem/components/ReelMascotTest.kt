package com.assismohanty.awaken.core.designsystem.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The candle melts evenly towards the limit and burns out exactly at it (owner brief, 2026-11-12: eight phases). */
class ReelMascotTest {
    @Test
    fun withNoLimitTheCandleStaysFresh() {
        assertThat(ReelMascot.phase(500, null)).isEqualTo(0)
        assertThat(ReelMascot.phase(5, 0)).isEqualTo(0)
    }

    @Test
    fun itStepsEvenlyAndBurnsOutOnlyAtTheLimit() {
        assertThat((0..70 step 10).map { ReelMascot.phase(it, 70) }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7).inOrder()
        assertThat(ReelMascot.phase(69, 70)).isEqualTo(6)
        assertThat(ReelMascot.phase(120, 70)).isEqualTo(7)
    }

    @Test
    fun aSmallLimitStillReachesTheLastPhaseOnlyAtTheLimit() {
        assertThat((0..3).map { ReelMascot.phase(it, 3) }).containsExactly(0, 2, 4, 7).inOrder()
    }
}
