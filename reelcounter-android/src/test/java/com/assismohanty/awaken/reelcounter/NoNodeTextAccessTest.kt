package com.assismohanty.awaken.reelcounter

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** The service must never read node or event text. This scans the code that touches nodes. */
class NoNodeTextAccessTest {

    private val forbidden = Regex(
        """\.(text|contentDescription|hintText|stateDescription|tooltipText|paneTitle|beforeText)\b""" +
            """|get(Text|ContentDescription|HintText|StateDescription|TooltipText|PaneTitle|BeforeText)\(""",
    )

    private fun sources(vararg roots: String): List<File> = roots
        .map { File(it) }
        .filter { it.exists() }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test
    fun serviceInspectorAndOverlayCodeNeverReadsText() {
        val files = sources("src/main/java", "src/debug/java")
            .filter { f -> listOf("/service/", "/inspector/", "/overlay/").any { f.invariantSeparatorsPath.contains(it) } }
            .filter { it.name != STUDY_TEXT_FILE }
        assertThat(files).isNotEmpty()
        val offenders = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (!line.trimStart().startsWith("import ") && forbidden.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertThat(offenders).isEmpty()
    }

    /** Study mode's single, named exception: one read of one node's text, in one file. */
    @Test
    fun theOnlyTextReadIsTheStudyChannelName() {
        val file = sources("src/main/java").single { it.name == STUDY_TEXT_FILE }
        val reads = file.readLines().count { !it.trimStart().startsWith("import ") && !it.trimStart().startsWith("*") && forbidden.containsMatchIn(it) }
        assertThat(reads).isEqualTo(1)
    }

    private companion object {
        const val STUDY_TEXT_FILE = "StudyChannelText.kt"
    }
}
