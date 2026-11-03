package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TemplateLibrary
import com.assismohanty.awaken.engine.model.TitlePattern
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/** GD §6.2 title rules: plain words, the target inside the title, and no fantasy names. */
class TemplateTitleTest {

    private fun problems(pattern: String) = TemplateLibrary.titleProblems(pattern)

    @Test
    fun `plain action-first patterns pass`() {
        listOf("Walk {n} steps", "Eat protein-rich food for {n} minutes", "Write a {n}-word letter", "Hold a plank for {dur}")
            .forEach { assertWithMessage(it).that(problems(it)).isEmpty() }
    }

    @Test
    fun `banned words fail as whole words in any case`() {
        assertThat(problems("Fuel the forge for {n} minutes")).contains("uses banned word \"forge\"")
        assertThat(problems("Arch of power {n} reps")).contains("uses banned word \"power\"")
        assertThat(problems("Enter the gate {n} times")).contains("uses banned word \"gate\"")
        assertThat(problems("Lift {n} iron plates").single()).contains("iron")
        // Whole words only: "powerful" and "gateway" are not hits.
        assertThat(problems("Take a powerful gateway walk of {n} minutes").none { "banned" in it }).isTrue()
    }

    @Test
    fun `a pattern needs exactly one target token, no digits, sentence case and two words`() {
        assertThat(problems("Walk your steps")).contains("must contain exactly one {n} or {dur}")
        assertThat(problems("Walk {n} steps in {n} minutes")).contains("must contain exactly one {n} or {dur}")
        assertThat(problems("Walk 7,000 steps {n}")).contains("must not contain digits or stray braces")
        assertThat(problems("Walk {n} Steps")).contains("must be sentence case")
        assertThat(problems("walk {n} steps")).contains("must be sentence case")
        assertThat(problems("{n} steps").none { "words" in it }).isTrue()
        assertThat(problems("Meditate{n}")).contains("must have at least 2 words")
    }

    @Test
    fun `patterns render the target`() {
        assertThat(TitlePattern.render("Walk {n} steps", 7000)).isEqualTo("Walk 7,000 steps")
        assertThat(TitlePattern.render("Walk {n} steps", 950)).isEqualTo("Walk 950 steps")
        assertThat(TitlePattern.render("Compliment {n} {person|people}", 1)).isEqualTo("Compliment 1 person")
        assertThat(TitlePattern.render("Compliment {n} {person|people}", 2)).isEqualTo("Compliment 2 people")
        assertThat(TitlePattern.render("Hold a plank for {dur}", 30)).isEqualTo("Hold a plank for 30 seconds")
        assertThat(TitlePattern.render("Hold a plank for {dur}", 60)).isEqualTo("Hold a plank for 1 minute")
        assertThat(TitlePattern.render("Hold a plank for {dur}", 90)).isEqualTo("Hold a plank for 90 seconds")
        assertThat(TitlePattern.render("Hold a plank for {dur}", 180)).isEqualTo("Hold a plank for 3 minutes")
    }

    @Test
    fun `validate reports a bad title and an over-long render`() {
        val bad = TemplateFixtures.template("str_bad", Stat.STR).copy(title = "Fuel the forge for {n} reps")
        val long = TemplateFixtures.template("str_long", Stat.STR)
            .copy(title = "Do {n} very many slow and careful push-ups today")
        val library = TemplateLibrary(
            version = 1, equipment = emptyList(), tags = emptyList(), units = listOf("reps"), templates = listOf(bad, long),
        )
        val problems = library.validate().filter { "title" in it }
        assertThat(problems.any { "str_bad" in it && "forge" in it }).isTrue()
        assertThat(problems.any { "str_long" in it && "renders to" in it && Difficulty.EXTREME.name in it }).isTrue()
    }
}
