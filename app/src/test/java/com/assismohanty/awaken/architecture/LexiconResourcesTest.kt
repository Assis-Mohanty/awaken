package com.assismohanty.awaken.architecture

import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.Lexicon
import com.assismohanty.awaken.core.designsystem.lexicon.PresentationMode
import com.assismohanty.awaken.core.designsystem.lexicon.Term
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** GD §14.9 / DESIGN_SYSTEM §8.3: every System string has a Clean twin, and the twins stay in step with the code. */
class LexiconResourcesTest {

    private val valuesDir = File("src/main/res/values")

    private data class Res(val name: String, val text: String, val plural: Boolean)

    private val resources: List<Res> by lazy {
        val stringRe = Regex("""<string name="([^"]+)"([^>]*)>([\s\S]*?)</string>""")
        val pluralRe = Regex("""<plurals name="([^"]+)"[^>]*>([\s\S]*?)</plurals>""")
        valuesDir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty().flatMap { file ->
            val xml = file.readText()
            stringRe.findAll(xml).filterNot { "translatable=\"false\"" in it.groupValues[2] }
                .map { Res(it.groupValues[1], it.groupValues[3], plural = false) }.toList() +
                pluralRe.findAll(xml).map { Res(it.groupValues[1], it.groupValues[2], plural = true) }.toList()
        }
    }

    private val byName: Map<String, Res> by lazy { resources.associateBy { it.name } }

    private fun args(text: String): List<String> = Regex("""%(\d+\$)?[sdf]""").findAll(text).map { it.value }.sorted().toList()

    private fun idsByName(rClass: Class<*>): Map<Int, String> = rClass.fields.associate { it.getInt(null) to it.name }

    @Test
    fun everyCleanStringHasABaseWithTheSameArguments() {
        val twins = resources.filter { it.name.endsWith("_clean") && !it.name.startsWith("term_") }
        assertThat(twins).isNotEmpty()
        twins.forEach { twin ->
            val base = byName[twin.name.removeSuffix("_clean")]
            assertWithMessage("${twin.name} has no System string").that(base).isNotNull()
            assertWithMessage("${twin.name} vs its System string").that(base?.plural).isEqualTo(twin.plural)
            assertWithMessage("${twin.name} format arguments").that(args(twin.text)).isEqualTo(args(base?.text.orEmpty()))
        }
    }

    @Test
    fun pairsMatchTheCleanResourcesExactly() {
        val stringNames = idsByName(R.string::class.java)
        val pluralNames = idsByName(R.plurals::class.java)
        val clean = Lexicon.of(PresentationMode.CLEAN)
        val expectedStrings = resources.filter { !it.plural && it.name.endsWith("_clean") && !it.name.startsWith("term_") }
            .map { it.name.removeSuffix("_clean") }.toSet()
        val expectedPlurals = resources.filter { it.plural && it.name.endsWith("_clean") }.map { it.name.removeSuffix("_clean") }.toSet()

        val mappedStrings = stringNames.filter { (id, _) -> clean.resolve(id) != id }
        mappedStrings.forEach { (id, name) -> assertThat(stringNames[clean.resolve(id)]).isEqualTo("${name}_clean") }
        assertThat(mappedStrings.values.toSet()).isEqualTo(expectedStrings)

        val mappedPlurals = pluralNames.filter { (id, _) -> clean.resolvePlural(id) != id }
        mappedPlurals.forEach { (id, name) -> assertThat(pluralNames[clean.resolvePlural(id)]).isEqualTo("${name}_clean") }
        assertThat(mappedPlurals.values.toSet()).isEqualTo(expectedPlurals)

        // System words are the strings themselves.
        stringNames.keys.forEach { id -> assertThat(Lexicon.System.resolve(id)).isEqualTo(id) }
    }

    @Test
    fun everyTermHasBothWords() {
        val names = idsByName(R.string::class.java)
        Term.entries.forEach { term ->
            val key = term.name.lowercase()
            assertThat(names[term.clean]).isEqualTo("term_${key}_clean")
            assertThat(names[term.system]).isEqualTo("term_${key}_system")
        }
    }

    @Test
    fun everyStringInSystemVoiceHasACleanTwin() {
        val missing = resources
            .filter { !it.name.endsWith("_clean") && !it.name.startsWith("term_") && it.name !in SHARED_IN_BOTH_MODES }
            .filter { speaksAsTheSystem(it.text) }
            .filter { "${it.name}_clean" !in byName }
            .map { "${it.name}: ${it.text.trim()}" }
        assertWithMessage("Add a Clean twin to strings_clean.xml (or allowlist a proper name):\n" + missing.joinToString("\n"))
            .that(missing).isEmpty()
    }

    private fun speaksAsTheSystem(text: String): Boolean {
        val letters = text.replace(Regex("""%(\d+\$)?[sdf]|\\'|&[a-z]+;|<[^>]+>"""), "")
        val shouting = letters.any { it.isLetter() } && letters == letters.uppercase() && Regex("[A-Z]{3}").containsMatchIn(letters)
        return shouting || SYSTEM_TERMS.containsMatchIn(text)
    }

    private companion object {
        val SYSTEM_TERMS = Regex(
            """\b(hunters?|gates?|gate's|dungeons?|directives?|penalty|penalties|rank trials?|trials?|relic cach(e|es)|loot|""" +
                """fragments?|the system|system('s)?|awaken(ing|ed)|vault|chapters?|sigils?|oaths?|streak shields?|shields?|""" +
                """recalibrat\w*|crit(ical|s)?|breach(ed|es)?|collapsed?|collapses|oracle|status)\b""",
            RegexOption.IGNORE_CASE,
        )

        /** Proper names and non-System words that read the same in both modes (approved: achievement names and titles stay). */
        val SHARED_IN_BOTH_MODES = setOf(
            "app_name", "settings_about", "settings_theme_system", "health_state_unavailable", "health_stage_rem", "campaign_act_iii",
            "achievement_awakened", "achievement_night_owl", "achievement_collector",
            "title_the_awakened", "title_rising_hunter", "title_gate_sovereign",
            "title_d_rank_hunter", "title_c_rank_hunter", "title_b_rank_hunter", "title_a_rank_hunter", "title_s_rank_hunter",
        )
    }
}
