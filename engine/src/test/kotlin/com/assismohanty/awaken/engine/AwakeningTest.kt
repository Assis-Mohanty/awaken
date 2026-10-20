package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.Awakening.Answer
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TemplateLibrary
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AwakeningTest {

    private val date = LocalDate.of(2026, 10, 18)
    private val library = TemplateLibrary.parse(requireNotNull(javaClass.classLoader.getResource("quests/templates.json")).readText())

    @Test
    fun `calibration maps answers to baseline tiers, WIL and CHA start easy`() {
        assertThat(Awakening.baselineTiers(Answer.LOW, Answer.MID, Answer.HIGH)).containsExactly(
            Stat.STR, Difficulty.EASY, Stat.INT, Difficulty.NORMAL, Stat.WIL, Difficulty.EASY,
            Stat.VIT, Difficulty.HARD, Stat.CHA, Difficulty.EASY,
        )
    }

    @Test
    fun `hunter name is 3 to 20 characters after trimming`() {
        assertThat(Awakening.isValidName("Al")).isFalse()
        assertThat(Awakening.isValidName("  Ali  ")).isTrue()
        assertThat(Awakening.isValidName("a".repeat(20))).isTrue()
        assertThat(Awakening.isValidName("a".repeat(21))).isFalse()
    }

    @Test
    fun `first directive has an easy self-reported indoor-friendly quest in a focus stat`() {
        for (offset in 0L until 30L) {
            val context = HunterContext(hunterId = "h$offset", focusStats = listOf(Stat.WIL, Stat.CHA), firstDirective = true)
            val directive = DirectiveGenerator.generate(library.templates, context, date.plusDays(offset))
            val byId = library.templates.associateBy { it.id }

            assertThat(directive.quests).hasSize(4)
            val starter = directive.sides.first()
            assertThat(starter.type).isEqualTo(QuestType.SIDE)
            assertThat(starter.difficulty).isEqualTo(Difficulty.EASY)
            assertThat(starter.stat).isIn(context.focusStats)
            assertThat(starter.verification.source).isEqualTo(VerificationSource.SELF)
            assertThat(byId.getValue(checkNotNull(starter.templateId)).tags).doesNotContain("outdoor")
            assertThat(directive.quests.map { it.templateId }).containsNoDuplicates()
        }
    }

    @Test
    fun `without the first-directive flag generation is unchanged`() {
        val context = HunterContext(hunterId = "h", focusStats = listOf(Stat.STR, Stat.INT))
        val directive = DirectiveGenerator.generate(library.templates, context, date)
        directive.sides.forEach { assertThat(it.difficulty).isEqualTo(Difficulty.NORMAL) }
    }
}

class SystemLinesTest {
    private val date = LocalDate.of(2026, 10, 18)
    private val lines = mapOf(
        "generic" to listOf("g1", "g2", "g3"),
        "comeback" to listOf("c1"),
        "streak_milestone" to listOf("{streak} days."),
        "neglected_int" to listOf("INT quiet"),
    )

    @Test
    fun `context priority`() {
        assertThat(SystemLines.contextKey(penaltyOpen = true, trialActive = true, streak = 7, todayCleared = true, neglected = Stat.INT)).isEqualTo("comeback")
        assertThat(SystemLines.contextKey(false, trialActive = true, streak = 7, todayCleared = true, neglected = null)).isEqualTo("trial_active")
        assertThat(SystemLines.contextKey(false, false, streak = 7, todayCleared = true, neglected = null)).isEqualTo("streak_milestone")
        assertThat(SystemLines.contextKey(false, false, streak = 8, todayCleared = true, neglected = Stat.INT)).isEqualTo("cleared_day")
        assertThat(SystemLines.contextKey(false, false, streak = 8, todayCleared = false, neglected = Stat.INT)).isEqualTo("neglected_int")
        assertThat(SystemLines.contextKey(false, false, streak = 0, todayCleared = false, neglected = null)).isEqualTo("generic")
    }

    @Test
    fun `pick is deterministic, fills the streak and falls back to generic`() {
        val first = SystemLines.pick(lines, "generic", "h", date, streak = 0)
        assertThat(SystemLines.pick(lines, "generic", "h", date, streak = 0)).isEqualTo(first)
        assertThat(SystemLines.pick(lines, "streak_milestone", "h", date, streak = 7)).isEqualTo("7 days.")
        assertThat(SystemLines.pick(lines, "cleared_day", "h", date, streak = 0)).isIn(lines.getValue("generic"))
        assertThat(SystemLines.pick(emptyMap(), "generic", "h", date, streak = 0)).isNull()
    }

    @Test
    fun `neglected stat ignores never-touched stats and picks the longest gap`() {
        val lastActive = mapOf(Stat.STR to date.minusDays(2), Stat.INT to date.minusDays(5), Stat.WIL to date.minusDays(4))
        assertThat(SystemLines.neglectedStat(lastActive, date)).isEqualTo(Stat.INT)
        assertThat(SystemLines.neglectedStat(emptyMap(), date)).isNull()
        assertThat(SystemLines.neglectedStat(mapOf(Stat.STR to date.minusDays(2)), date)).isNull()
    }
}
