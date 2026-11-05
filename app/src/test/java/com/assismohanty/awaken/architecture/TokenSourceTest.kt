package com.assismohanty.awaken.architecture

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * DESIGN_SYSTEM §2.1/§8.4: one palette file. Hex colours exist only in `core/designsystem/theme/Color.kt`, so a redesign
 * re-colours the app by editing that file. Widget and Reel Counter host code must not hold literal colours either.
 */
class TokenSourceTest {

    private val roots = listOf("src/main/java", "src/debug/java").map(::File).filter { it.exists() }
    private val paletteFile = "core/designsystem/theme/Color.kt"
    private val hex = Regex("""Color\(0x|ColorProvider\(Color\(0x|#[0-9A-Fa-f]{6}\b""")

    @Test
    fun hexColoursLiveOnlyInThePaletteFile() {
        val violations = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if (path.endsWith(paletteFile)) return@flatMap emptyList()
                file.readLines().mapIndexedNotNull { index, line ->
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@mapIndexedNotNull null
                    if (hex.containsMatchIn(line)) "$path:${index + 1}: ${line.trim()}" else null
                }
            }.toList()
        }
        assertWithMessage("Move colours into AwakenPalette (core/designsystem/theme/Color.kt):\n" + violations.joinToString("\n"))
            .that(violations).isEmpty()
    }
}
