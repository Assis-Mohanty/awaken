package com.assismohanty.awaken.architecture

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * ARCHITECTURE §1: a screen is a `*Route` (ViewModel wiring only) that calls a stateless `*Screen(state, onEvent)`.
 * `hiltViewModel()` and `collectAsStateWithLifecycle()` may appear only inside a `*Route`, `*Entry` or `*Host` function, and every
 * `*Screen` has a `@PhonePreviews` preview, so a redesign can rebuild the screens without touching state.
 */
class ScreenLayeringTest {

    private val featureDir = File("src/main/java/com/assismohanty/awaken/feature")

    private val topLevelFun = Regex("""^(?:private |internal |public )?fun (?:<[^>]+> )?(?:[\w.]+\.)?(\w+)\(""")
    private val wiring = Regex("""\bhiltViewModel\(|\bcollectAsStateWithLifecycle\(""")
    private val allowedWrapper = Regex("""(Route|Entry|Host)$""")

    private fun sources(): List<File> = featureDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun viewModelWiringLivesOnlyInRoutesAndEntries() {
        val violations = sources().flatMap { file ->
            var current = ""
            file.readLines().mapIndexedNotNull { index, line ->
                topLevelFun.find(line)?.let { current = it.groupValues[1] }
                val trimmed = line.trimStart()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("import ")) return@mapIndexedNotNull null
                if (wiring.containsMatchIn(line) && !allowedWrapper.containsMatchIn(current)) {
                    "${file.relativeTo(featureDir)}:${index + 1} in `$current`: ${line.trim()}"
                } else {
                    null
                }
            }
        }
        assertWithMessage("Move ViewModel wiring into a *Route and pass state down:\n" + violations.joinToString("\n"))
            .that(violations).isEmpty()
    }

    @Test
    fun everyScreenHasAPhonePreview() {
        val screenName = Regex("""^fun (\w+Screen)\(""")
        val missing = sources().flatMap { file ->
            val lines = file.readLines()
            val text = file.readText()
            lines.mapNotNull { screenName.find(it)?.groupValues?.get(1) }.filter { name ->
                // The screen is called somewhere after the file's first @PhonePreviews annotation.
                !Regex("""\b$name\(""").containsMatchIn(text.substringAfter("@PhonePreviews", ""))
            }.map { screen -> "${file.relativeTo(featureDir)}: $screen has no @PhonePreviews preview" }
        }
        assertWithMessage(missing.joinToString("\n")).that(missing).isEmpty()
    }
}
