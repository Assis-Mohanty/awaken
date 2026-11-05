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

