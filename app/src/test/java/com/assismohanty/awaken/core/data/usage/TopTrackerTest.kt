package com.assismohanty.awaken.core.data.usage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopTrackerTest {
    private val resumed = 1
    private val paused = 2

    private fun run(vararg events: Pair<Int, String>): List<String> {
        val apps = mutableListOf<ResumedApp>()
        events.forEachIndexed { index, (type, pkg) -> TopTracker.add(apps, type, pkg, index.toLong()) }
        return apps.map { it.packageName }
    }

    @Test
    fun everyChangeOfAppIsKeptInOrder() {
        assertThat(run(resumed to "a", paused to "a", resumed to "b", resumed to "a")).containsExactly("a", "b", "a").inOrder()
    }

    @Test
    fun anotherScreenOfTheSameAppIsNotAChange() {
        assertThat(run(resumed to "a", resumed to "a", resumed to "b")).containsExactly("a", "b").inOrder()
    }

    @Test
    fun aPauseAloneAddsNothing() {
        assertThat(run(paused to "a")).isEmpty()
    }

    @Test
    fun otherEventTypesAndBlankNamesAreIgnored() {
        val apps = mutableListOf<ResumedApp>()
        TopTracker.add(apps, 15, "b", 0)
        TopTracker.add(apps, resumed, "", 0)
        TopTracker.add(apps, resumed, null, 0)
        assertThat(apps).isEmpty()
    }
}
