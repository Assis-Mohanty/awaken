package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TemplateLibrary
import com.assismohanty.awaken.engine.model.Verification
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Validates the bundled content in app/src/main/assets (copied into test resources by Gradle). */
class ContentAssetsTest {

    private fun resource(path: String): String =
        requireNotNull(javaClass.classLoader.getResource(path)) { "missing test resource $path" }.readText()

    private val library = TemplateLibrary.parse(resource("quests/templates.json"))

    @Test
    fun `template library passes validation`() {
        assertWithMessage(library.validate().joinToString("\n")).that(library.validate()).isEmpty()
    }

    @Test
    fun `at least 150 regular templates and 25 per stat`() {
        assertThat(library.regular.size).isAtLeast(150)
        Stat.entries.forEach { stat ->
            assertWithMessage("$stat").that(library.regular.count { it.stat == stat }).isAtLeast(TemplateLibrary.MIN_PER_STAT)
        }
    }

    @Test
    fun `ids are unique and titles are not empty`() {
        assertThat(library.templates.map { it.id }).containsNoDuplicates()
        library.templates.forEach { t ->
            assertWithMessage(t.id).that(t.titles).isNotEmpty()
            t.titles.forEach { assertWithMessage(t.id).that(it.isNotBlank()).isTrue() }
        }
    }

    @Test
    fun `all four targets present and increasing`() {
        library.templates.forEach { t ->
            val targets = Difficulty.entries.map { requireNotNull(t.targets[it]) { "${t.id} missing $it" } }
            val pairs = targets.zipWithNext()
            if (t.verification == Verification.HEALTH_SLEEP) {
                assertWithMessage(t.id).that(pairs.all { (a, b) -> b >= a }).isTrue()
            } else {
                assertWithMessage(t.id).that(pairs.all { (a, b) -> b > a }).isTrue()
            }
        }
    }

    @Test
    fun `health targets follow the content spec`() {
        fun targetsOf(v: Verification) = library.templates.filter { it.verification == v }.map { t -> Difficulty.entries.map { t.targets[it] } }
        assertThat(targetsOf(Verification.HEALTH_STEPS)).isNotEmpty()
        targetsOf(Verification.HEALTH_STEPS).forEach { assertThat(it).containsExactly(4000, 7000, 10000, 14000).inOrder() }
        assertThat(targetsOf(Verification.HEALTH_EXERCISE)).isNotEmpty()
        targetsOf(Verification.HEALTH_EXERCISE).forEach { assertThat(it).containsExactly(15, 30, 45, 60).inOrder() }
        assertThat(targetsOf(Verification.HEALTH_DISTANCE)).isNotEmpty()
        targetsOf(Verification.HEALTH_DISTANCE).forEach { assertThat(it).containsExactly(2, 4, 7, 12).inOrder() }
        assertThat(targetsOf(Verification.HEALTH_CALORIES)).isNotEmpty()
        targetsOf(Verification.HEALTH_CALORIES).forEach { assertThat(it).containsExactly(150, 300, 500, 800).inOrder() }
        val sleep = library.templates.filter { it.verification == Verification.HEALTH_SLEEP }
        assertThat(sleep.map { it.stat }).containsExactly(Stat.VIT)
        assertThat(sleep.single().targets.values.toSet()).containsExactly(7)
    }

    @Test
    fun `penalty set has 15 quests doable anywhere`() {
        assertThat(library.penalty).hasSize(15)
        library.penalty.forEach { t ->
            assertWithMessage(t.id).that(t.equipment).isEmpty()
            assertWithMessage(t.id).that(t.tags).doesNotContain("outdoor")
            assertWithMessage(t.id).that(t.verification).isEqualTo(Verification.SELF)
        }
    }

    @Test
    fun `penalty quest is available even with injury mode on and no health connect`() {
        val context = HunterContext("h", focusStats = listOf(Stat.STR, Stat.INT), injuryTags = DirectiveGenerator.DEFAULT_INJURY_TAGS, healthAvailable = false)
        assertThat(DirectiveGenerator.penaltyQuest(library.templates, context, LocalDate.of(2026, 10, 18))).isNotNull()
    }

    @Test
    fun `real library supports a full directive under restrictive settings for 30 days`() {
        val restrictive = HunterContext("h", focusStats = listOf(Stat.STR, Stat.CHA), injuryTags = DirectiveGenerator.DEFAULT_INJURY_TAGS, healthAvailable = false)
        val history = ArrayList<com.assismohanty.awaken.engine.model.Directive>()
        val start = LocalDate.of(2026, 10, 1)
        repeat(30) { i ->
            val day = start.plusDays(i.toLong())
            val window = DirectiveGenerator.noveltyWindow(day)
            val recent = history.filter { it.gameDate in window }.flatMap { d -> d.quests.mapNotNull { it.templateId } }.toSet()
            val directive = DirectiveGenerator.generate(library.templates, restrictive.copy(recentTemplateIds = recent), day)
            assertWithMessage("$day").that(directive.quests).hasSize(4)
            history += directive
        }
    }

    @Test
    fun `no borrowed franchise names`() {
        val banned = listOf("solo leveling", "jin-woo", "jinwoo", "monarch", "arise", "igris", "beru", "hunter association")
        val text = (resource("quests/templates.json")).lowercase()
        banned.forEach { assertWithMessage(it).that(text.contains(it)).isFalse() }
    }

    @Test
    fun `identities file has the 12 launch identities and passes validation`() {
        val catalog = IdentityCatalog.parse(resource("identities.json"))
        assertWithMessage(catalog.validate().joinToString("\n")).that(catalog.validate()).isEmpty()
        assertThat(catalog.identities).hasSize(12)
    }
}
