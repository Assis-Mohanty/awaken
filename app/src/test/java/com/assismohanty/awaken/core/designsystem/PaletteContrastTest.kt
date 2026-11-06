package com.assismohanty.awaken.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.assismohanty.awaken.core.designsystem.theme.AwakenPalette
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Contrast floors in both looks (design/DESIGN.md): ink >= 7:1 on cards and on the page, secondary ink >= 4.5:1 (AA) on
 * both, button text >= 4.5:1 on its fill, critical >= 4.5:1 on cards, and white text on every session field. The plate
 * colours are decorative: they mark markers, bars and chart series that always sit beside a name or a number, so they
 * have no floor here (yellow and white are under 3:1 on chalk).
 */
class PaletteContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, colors: AwakenPalette) {
        listOf("card" to colors.panel, "page" to colors.void, "sunken" to colors.depth).forEach { (surface, bg) ->
            assertWithMessage("$name ink on $surface").that(contrast(colors.bone, bg)).isAtLeast(7.0)
            assertWithMessage("$name secondary on $surface").that(contrast(colors.mist, bg)).isAtLeast(4.5)
        }
        assertWithMessage("$name text on the primary button").that(contrast(colors.onAccent, colors.system)).isAtLeast(4.5)
        listOf("card" to colors.panel, "page" to colors.void).forEach { (surface, bg) ->
            assertWithMessage("$name critical on $surface").that(contrast(colors.ember, bg)).isAtLeast(4.5)
        }
        Stat.entries.forEach { stat ->
            val field = colors.field(stat)
            assertWithMessage("$name white on the $stat session field").that(contrast(AwakenPalette.OnField, field)).isAtLeast(7.0)
            assertWithMessage("$name secondary text on the $stat session field").that(contrast(AwakenPalette.OnFieldMuted, field)).isAtLeast(4.5)
        }
        assertWithMessage("$name inverse button text").that(contrast(AwakenPalette.OnButtonOnField, AwakenPalette.ButtonOnField)).isAtLeast(4.5)
    }

    @Test
    fun darkPaletteMeetsTheFloors() = check("dark", AwakenPalette.Dark)

    @Test
    fun lightPaletteMeetsTheFloors() = check("light", AwakenPalette.Light)
}
