package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class SettingsRulesTest {

    private val zone = ZoneOffset.UTC

    private fun at(time: String): Instant = Instant.parse("2026-10-19T${time}:00Z")

    @Test
    fun `only midnight to 06 is offered`() {
        assertThat(SettingsRules.DAY_START_HOURS.toList()).containsExactly(0, 1, 2, 3, 4, 5, 6).inOrder()
        assertThat(SettingsRules.canChangeDayStart(at("12:00"), zone, 4, 7)).isFalse()
        assertThat(SettingsRules.canChangeDayStart(at("12:00"), zone, 4, -1)).isFalse()
    }

    @Test
    fun `a change that keeps today is allowed`() {
        assertThat(SettingsRules.canChangeDayStart(at("12:00"), zone, 4, 6)).isTrue()
        assertThat(SettingsRules.canChangeDayStart(at("12:00"), zone, 4, 0)).isTrue()
    }

    @Test
    fun `moving the start later is blocked while it would put today back into yesterday`() {
        // 05:30 is game-day Oct 19 at 04:00 but still Oct 18 at 06:00.
        assertThat(SettingsRules.canChangeDayStart(at("05:30"), zone, 4, 6)).isFalse()
        assertThat(SettingsRules.canChangeDayStart(at("06:00"), zone, 4, 6)).isTrue()
    }

    @Test
    fun `moving the start earlier is allowed even when it advances the day`() {
        // 03:00 is still Oct 18 at 04:00 but already Oct 19 at 02:00: yesterday just ends sooner.
        assertThat(SettingsRules.canChangeDayStart(at("03:00"), zone, 4, 2)).isTrue()
    }

    @Test
    fun `injury tags are limited to the vocabulary and default when empty`() {
        assertThat(SettingsRules.normalizeInjuryTags(setOf("upper"))).containsExactly("upper")
        assertThat(SettingsRules.normalizeInjuryTags(setOf("upper", "bogus", "legs"))).containsExactly("upper", "legs")
        assertThat(SettingsRules.normalizeInjuryTags(emptySet())).containsExactly("impact", "legs")
        assertThat(SettingsRules.normalizeInjuryTags(setOf("bogus"))).containsExactly("impact", "legs")
    }

    @Test
    fun `equipment outside the vocabulary is dropped`() {
        val vocabulary = listOf("dumbbells", "jump_rope")
        assertThat(SettingsRules.validEquipment(setOf("dumbbells", "ghost"), vocabulary)).containsExactly("dumbbells")
    }
}
