package com.assismohanty.awaken.core.data.usage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopTrackerTest {
    private val resumed = 1
    private val paused = 2

    private fun run(vararg events: Pair<Int, String>): String? = events.fold(null as String?) { top, (type, pkg) -> TopTracker.next(top, type, pkg) }

    @Test
    fun theLastAppToResumeIsOnTop() {
        assertThat(run(resumed to "a", paused to "a", resumed to "b")).isEqualTo("b")
    }

    @Test
    fun aPauseAloneChangesNothing() {
        assertThat(run(paused to "a")).isNull()
        assertThat(run(resumed to "a", paused to "a")).isEqualTo("a")
    }

    @Test
    fun otherEventTypesAndBlankNamesAreIgnored() {
        assertThat(TopTracker.next("a", 15, "b")).isEqualTo("a")
        assertThat(TopTracker.next("a", resumed, "")).isEqualTo("a")
        assertThat(TopTracker.next("a", resumed, null)).isEqualTo("a")
    }
}
