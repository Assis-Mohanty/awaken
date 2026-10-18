package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EngineInfoTest {
    @Test
    fun `rules version is positive`() {
        assertThat(EngineInfo.RULES_VERSION).isAtLeast(1)
    }
}
