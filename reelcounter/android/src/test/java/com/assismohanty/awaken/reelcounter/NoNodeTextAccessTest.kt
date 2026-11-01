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
        assertThat(files).isNotEmpty()
        val offenders = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (!line.trimStart().startsWith("import ") && forbidden.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertThat(offenders).isEmpty()
    }
}
