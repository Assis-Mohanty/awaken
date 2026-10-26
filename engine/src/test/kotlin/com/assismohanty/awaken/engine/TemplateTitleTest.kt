package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TemplateLibrary
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/** GD §6.2 title rules, so fantasy names like "Fuel the Forge" can't come back. */
class TemplateTitleTest {

    private fun problems(title: String) = TemplateLibrary.titleProblems(title)

    @Test
    fun `plain action-first titles pass`() {
        listOf("Do push-ups", "Walk your steps", "Eat protein-rich food", "List what you're grateful for", "Read aloud")
            .forEach { assertWithMessage(it).that(problems(it)).isEmpty() }
    }

    @Test
    fun `titles over 40 chars fail`() {
        val title = "Stretch your neck and shoulders slowly now" // 42 chars, 7 words
        assertThat(title.length).isGreaterThan(TemplateLibrary.MAX_TITLE_LENGTH)
        assertThat(problems(title)).contains("is longer than 40 chars")
    }

    @Test
    fun `banned words fail as whole words in any case`() {
        assertThat(problems("Fuel the forge")).contains("uses banned word \"forge\"")
        assertThat(problems("Arch of power")).contains("uses banned word \"power\"")
        assertThat(problems("Enter the gate")).contains("uses banned word \"gate\"")
        assertThat(problems("Lift iron plates").single()).contains("iron")
        // Whole words only: "powerful" and "gateway" are not hits.
        assertThat(problems("Take a powerful gateway walk").none { "banned" in it }).isTrue()
    }

    @Test
    fun `numbers, placeholders, case and word count fail`() {
        assertThat(problems("Walk 7,000 steps")).contains("must not contain numbers or placeholders")
        assertThat(problems("Read {target} pages")).contains("must not contain numbers or placeholders")
        assertThat(problems("Walk Your Steps")).contains("must be sentence case")
        assertThat(problems("walk your steps")).contains("must be sentence case")
        assertThat(problems("Meditate")).contains("must have 2–5 words")
        assertThat(problems("Take a long and quiet walk outside")).contains("must have 2–5 words")
    }

    @Test
    fun `validate reports a bad title in the library`() {
        val bad = TemplateFixtures.template("str_bad", Stat.STR)
            .copy(titles = listOf("Fuel the Forge"))
        val library = TemplateLibrary(version = 1, equipment = emptyList(), tags = emptyList(), units = listOf("reps"), templates = listOf(bad))
        assertThat(library.validate().filter { "title" in it }).isNotEmpty()
    }
}
