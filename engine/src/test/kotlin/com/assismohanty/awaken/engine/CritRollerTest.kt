package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class CritRollerTest {

    @Test
    fun `fnv1a64 matches reference vectors`() {
        assertThat(Fnv1a.hash64("")).isEqualTo(0xcbf29ce484222325uL.toLong())
        assertThat(Fnv1a.hash64("a")).isEqualTo(0xaf63dc4c8601ec8cuL.toLong())
        assertThat(Fnv1a.hash64("foobar")).isEqualTo(0x85944171f73967e8uL.toLong())
    }

    @Test
    fun `crit rate over 100k random UUIDs is within 7_5 to 8_5 percent`() {
        val random = Random(20261018)
        val samples = 100_000
        val crits = (0 until samples).count { CritRoller.isCrit(UUID(random.nextLong(), random.nextLong()).toString()) }
        val rate = crits.toDouble() / samples

        assertThat(rate).isAtLeast(0.075)
        assertThat(rate).isAtMost(0.085)
    }

    @Test
    fun `same id always rolls the same result`() {
        repeat(1_000) {
            val id = UUID.randomUUID().toString()
            val first = CritRoller.isCrit(id)
            assertThat(CritRoller.isCrit(id)).isEqualTo(first)
            assertThat(CritRoller.isCrit(id.uppercase())).isEqualTo(first)
        }
    }
}
