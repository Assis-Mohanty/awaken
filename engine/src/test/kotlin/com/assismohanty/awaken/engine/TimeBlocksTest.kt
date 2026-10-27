package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TimeOfDay
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalTime

/** GD §14.5 time-of-day blocks. */
class TimeBlocksTest {

    @Test
    fun `vector 74 - default blocks with a 04-00 day start`() {
        val at = { hhmm: String -> TimeBlocks.blockAt(LocalTime.parse(hhmm), dayStartHour = 4) }
        assertThat(at("03:30")).isEqualTo(TimeOfDay.EVENING) // still the previous game-day
        assertThat(at("04:00")).isEqualTo(TimeOfDay.MORNING)
        assertThat(at("11:59")).isEqualTo(TimeOfDay.MORNING)
        assertThat(at("12:00")).isEqualTo(TimeOfDay.DAY)
        assertThat(at("16:59")).isEqualTo(TimeOfDay.DAY)
        assertThat(at("17:00")).isEqualTo(TimeOfDay.EVENING)
        assertThat(at("23:59")).isEqualTo(TimeOfDay.EVENING)
    }

    @Test
    fun `edited boundaries move the blocks`() {
        val bounds = TimeBlocks.Bounds(morningEnd = 10, dayEnd = 20)
        assertThat(TimeBlocks.blockAt(LocalTime.of(10, 0), dayStartHour = 0, bounds = bounds)).isEqualTo(TimeOfDay.DAY)
        assertThat(TimeBlocks.blockAt(LocalTime.of(19, 59), dayStartHour = 0, bounds = bounds)).isEqualTo(TimeOfDay.DAY)
        assertThat(TimeBlocks.blockAt(LocalTime.of(0, 30), dayStartHour = 0, bounds = bounds)).isEqualTo(TimeOfDay.MORNING)
    }

    @Test
    fun `bounds outside the settings ranges are refused or fall back`() {
        assertThrows<IllegalArgumentException> { TimeBlocks.Bounds(morningEnd = 8) }
        assertThrows<IllegalArgumentException> { TimeBlocks.Bounds(dayEnd = 22) }
        assertThat(TimeBlocks.Bounds.safe(morningEnd = 3, dayEnd = 99)).isEqualTo(TimeBlocks.Bounds())
    }

    @Test
    fun `anytime sorts after every block`() {
        val sorted = listOf(null, TimeOfDay.EVENING, TimeOfDay.MORNING, TimeOfDay.DAY).sortedBy(TimeBlocks::order)
        assertThat(sorted).containsExactly(TimeOfDay.MORNING, TimeOfDay.DAY, TimeOfDay.EVENING, null).inOrder()
    }

    @Test
    fun `generated quests copy the template block`() {
        val library = TemplateFixtures.library().map { if (it.stat == Stat.VIT) it.copy(timeOfDay = TimeOfDay.MORNING) else it }
        val context = HunterContext(hunterId = "h", focusStats = listOf(Stat.VIT, Stat.INT))
        val directive = DirectiveGenerator.generate(library, context, LocalDate.of(2026, 10, 27))
        directive.quests.forEach { quest ->
            assertThat(quest.timeOfDay).isEqualTo(if (quest.stat == Stat.VIT) TimeOfDay.MORNING else null)
        }
        assertThat(directive.quests.any { it.stat == Stat.VIT }).isTrue()
    }
}
