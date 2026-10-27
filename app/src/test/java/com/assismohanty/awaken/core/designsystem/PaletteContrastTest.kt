package com.assismohanty.awaken.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.assismohanty.awaken.core.designsystem.theme.AwakenPalette
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** DESIGN_SYSTEM §2.1 contrast floors hold in every palette: ink ≥ 7:1, secondary ≥ 5:1, hues and stat inks ≥ 4.5:1 on cards and stat inks on their own tint. */
class PaletteContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, colors: AwakenPalette) {
        assertWithMessage("$name bone").that(contrast(colors.bone, colors.panel)).isAtLeast(7.0)
        assertWithMessage("$name mist").that(contrast(colors.mist, colors.panel)).isAtLeast(5.0)
        val hues = listOf("ember" to colors.ember, "crit" to colors.crit, "verified" to colors.verified) +
            Rank.entries.map { "rank $it" to colors.rank(it) } +
            Stat.entries.map { "stat $it" to colors.stat(it) }
        hues.forEach { (label, hue) -> assertWithMessage("$name $label").that(contrast(hue, colors.panel)).isAtLeast(4.5) }
        Stat.entries.forEach { stat ->
            assertWithMessage("$name stat $stat on its tint").that(contrast(colors.stat(stat), colors.statTint(stat))).isAtLeast(4.5)
        }
        assertWithMessage("$name text on accent").that(contrast(colors.onAccent, colors.system)).isAtLeast(4.5)
    }

    @Test
    fun darkPaletteMeetsTheFloors() = check("dark", AwakenPalette.Dark)

    @Test
    fun lightPaletteMeetsTheFloors() = check("light", AwakenPalette.Light)

    @Test
    fun systemPaletteMeetsTheFloors() = check("system", AwakenPalette.System)
}
