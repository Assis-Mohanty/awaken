package com.assismohanty.awaken.architecture

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** DESIGN_SYSTEM §8.3: one plain vocabulary. No game jargon in any user-visible string, and no `_clean` twins. */
class LexiconResourcesTest {

    private val valuesDir = File("src/main/res/values")

    private data class Res(val name: String, val text: String)

    private val resources: List<Res> by lazy {
        val stringRe = Regex("""<string name="([^"]+)"([^>]*)>([\s\S]*?)</string>""")
        val pluralRe = Regex("""<plurals name="([^"]+)"[^>]*>([\s\S]*?)</plurals>""")
        valuesDir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty().flatMap { file ->
            val xml = file.readText()
            stringRe.findAll(xml).filterNot { "translatable=\"false\"" in it.groupValues[2] }
                .map { Res(it.groupValues[1], it.groupValues[3]) }.toList() +
                pluralRe.findAll(xml).map { Res(it.groupValues[1], it.groupValues[2]) }.toList()
        }
    }

    @Test
    fun thereIsOnlyOneSetOfStrings() {
        assertThat(resources).isNotEmpty()
        assertThat(File(valuesDir, "strings_clean.xml").exists()).isFalse()
        assertThat(resources.filter { it.name.endsWith("_clean") || (it.name.endsWith("_system") && it.name != "settings_theme_system") }.map { it.name }).isEmpty()
    }

    @Test
    fun noStringUsesGameJargon() {
        val hits = resources
            .filter { it.name !in ALLOWED_NAMES }
            .filter { BANNED.containsMatchIn(it.text.replace("AWAKEN", "")) }
            .map { "${it.name}: ${it.text.trim()}" }
        assertWithMessage("Plain words only (add to ALLOWED_NAMES only for a proper name):\n" + hits.joinToString("\n"))
            .that(hits).isEmpty()
    }

    @Test
    fun noStringShouts() {
        val shouting = resources.filter { it.name !in ALLOWED_NAMES }.filter { res ->
            val letters = res.text.replace(Regex("""%(\d+\$)?[sdf]|\\'|&[a-z]+;|<[^>]+>"""), "")
            letters.any { it.isLetter() } && letters == letters.uppercase() && Regex("[A-Z]{4}").containsMatchIn(letters)
        }.map { "${it.name}: ${it.text.trim()}" }
        assertWithMessage("Sentence case, not capitals:\n" + shouting.joinToString("\n")).that(shouting).isEmpty()
    }

    private companion object {
        val BANNED = Regex(
            """\b(system|hunters?|gates?|dungeons?|directives?|trials?|oaths?|realms?|sigils?|relics?|shards?|chapters?|""" +
                """awaken(ing|ed)?)\b""",
            RegexOption.IGNORE_CASE,
        )

        /** The app name, and words that are ordinary English or a platform term here, not game jargon. */
        val ALLOWED_NAMES = setOf(
            "app_name", "settings_about", "settings_theme_system", "privacy_storage",
        )
    }
}
