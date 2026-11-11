package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HydrationTest {
    @Test
    fun defaultsFollowSexAndAChosenTargetIsKeptWithinRange() {
        assertThat(Hydration.targetMl(Sex.FEMALE, null)).isEqualTo(2_000)
        assertThat(Hydration.targetMl(Sex.MALE, null)).isEqualTo(2_500)
        assertThat(Hydration.targetMl(Sex.MALE, 3_000)).isEqualTo(3_000)
        assertThat(Hydration.targetMl(Sex.MALE, 100)).isEqualTo(500)
        assertThat(Hydration.targetMl(Sex.FEMALE, 9_000)).isEqualTo(6_000)
    }
}
