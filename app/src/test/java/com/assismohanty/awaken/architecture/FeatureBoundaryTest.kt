package com.assismohanty.awaken.architecture

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * DESIGN_SYSTEM §8.4–8.5: feature screens do layout only. Colours come from semantic tokens, drawing from ChartKit and
 * design-system components, words from the Lexicon. A redesign then changes core/designsystem, not every screen.
 */
class FeatureBoundaryTest {

    private val featureDir = File("src/main/java/com/assismohanty/awaken/feature")

    private data class Rule(val pattern: Regex, val fix: String)

    private val rules = listOf(
        Rule(Regex("""\bAwakenPalette\b|\bLocalAwakenPalette\b|AwakenTheme\.palette\b"""), "use semantic tokens on AwakenTheme.colors"),
        Rule(Regex("""Color\(0x|#[0-9A-Fa-f]{6}\b"""), "no hex colours; use a semantic token"),
        Rule(Regex("""Color\.(Red|White|Black|Gray|Green|Blue|Yellow|Magenta|Cyan|DarkGray|LightGray)\b"""), "use a semantic token"),
        Rule(Regex("""\b\d+(\.\d+)?\.(dp|sp)\b"""), "use Spacing, AwakenTheme.dimens or a type role"),
        Rule(Regex("""\bCanvas\(|\bdrawBehind\b|\bdrawWithContent\b|\bdrawWithCache\b"""), "ask ChartKit or a design-system component to draw it"),
        Rule(Regex("""\.(background|border|clip|shadow)\("""), "move the styling into a core/designsystem component"),
        Rule(Regex("""\bAwakenAlpha\b"""), "alpha steps belong to design-system components"),
        Rule(Regex("""designsystem\.look\b|\bPlatesLook\b|\bLocalLook\b|\bLookId\b"""), "feature code never names a look; use tokens and design-system components"),
        Rule(Regex("""\bstringResource\(|\bpluralStringResource\(|\bLocalResources\b|context\.getString\(|import android\.content\.res\.Resources\b"""), "use lexString / lexPlural / LexResources"),
    )

    @Test
    fun featureCodeUsesOnlyTokensChartKitAndTheLexicon() {
        val sources = featureDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertWithMessage("no feature sources found under ${featureDir.absolutePath}").that(sources).isNotEmpty()
        val violations = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@mapIndexedNotNull null
                rules.firstOrNull { it.pattern.containsMatchIn(line) }?.let { rule ->
                    "${file.relativeTo(featureDir)}:${index + 1}: ${line.trim()}  → ${rule.fix}"
                }
            }
        }
        assertWithMessage(violations.joinToString("\n")).that(violations).isEmpty()
    }

    /** Widgets (Glance) and overlay windows draw outside Compose theming, but still take colours only from the palette file. */
    @Test
    fun widgetAndOverlayHostCodeHasNoRawColours() {
        val dirs = listOf("core/widget", "core/reelcounter").map { File("src/main/java/com/assismohanty/awaken/$it") }
        val colourRules = rules.filter { it.fix.contains("hex") || it.fix == "use a semantic token" }
        val sources = dirs.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        assertWithMessage("no widget/overlay sources found").that(sources).isNotEmpty()
        val violations = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@mapIndexedNotNull null
                colourRules.firstOrNull { it.pattern.containsMatchIn(line) }?.let { rule ->
                    "${file.name}:${index + 1}: ${line.trim()}  → ${rule.fix}"
                }
            }
        }
        assertWithMessage(violations.joinToString("\n")).that(violations).isEmpty()
    }
}
