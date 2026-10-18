package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.model.Stat

/**
 * Custom line glyphs (DESIGN_SYSTEM §2.4), 24-unit grid, sharp joins to match the chamfered geometry. Drawn in black and
 * tinted at the call site via `Icon(tint = …)`.
 */
object AwakenGlyphs {
    val Strength: ImageVector by lazy {
        glyph("Strength") {
            moveTo(5f, 13f); lineTo(12f, 6f); lineTo(19f, 13f)
            moveTo(5f, 19f); lineTo(12f, 12f); lineTo(19f, 19f)
        }
    }

    val Intelligence: ImageVector by lazy {
        glyph("Intelligence") {
            moveTo(12f, 3f); lineTo(20f, 12f); lineTo(12f, 21f); lineTo(4f, 12f); close()
            moveTo(12f, 9f); lineTo(15f, 12f); lineTo(12f, 15f); lineTo(9f, 12f); close()
        }
    }

    val Willpower: ImageVector by lazy {
        glyph("Willpower") {
            // Concentric target: focus held under pressure.
            moveTo(12f, 3f); lineTo(20f, 7.5f); lineTo(20f, 16.5f); lineTo(12f, 21f); lineTo(4f, 16.5f); lineTo(4f, 7.5f); close()
            moveTo(12f, 8f); lineTo(15.5f, 10f); lineTo(15.5f, 14f); lineTo(12f, 16f); lineTo(8.5f, 14f); lineTo(8.5f, 10f); close()
        }
    }

    val Vitality: ImageVector by lazy {
        glyph("Vitality") {
            // Leaf with a central vein.
            moveTo(12f, 21f); curveTo(5f, 16f, 5f, 8f, 12f, 3f); curveTo(19f, 8f, 19f, 16f, 12f, 21f); close()
            moveTo(12f, 7f); lineTo(12f, 21f)
        }
    }

    val Charisma: ImageVector by lazy {
        glyph("Charisma") {
            // Two linked rings: connection.
            moveTo(3.5f, 12f); arcToRelative(5f, 5f, 0f, true, true, 10f, 0f); arcToRelative(5f, 5f, 0f, true, true, -10f, 0f)
            moveTo(10.5f, 12f); arcToRelative(5f, 5f, 0f, true, true, 10f, 0f); arcToRelative(5f, 5f, 0f, true, true, -10f, 0f)
        }
    }

    val Shield: ImageVector by lazy {
        glyph("Shield") {
            moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 12f)
            curveTo(19f, 16.5f, 16f, 19.5f, 12f, 21f); curveTo(8f, 19.5f, 5f, 16.5f, 5f, 12f)
            lineTo(5f, 6f); close()
        }
    }

    val Fragment: ImageVector by lazy {
        glyph("Fragment") {
            moveTo(12f, 2.5f); lineTo(17.5f, 9f); lineTo(14f, 21.5f); lineTo(10f, 21.5f); lineTo(6.5f, 9f); close()
            moveTo(6.5f, 9f); lineTo(17.5f, 9f)
            moveTo(12f, 9f); lineTo(12f, 21.5f)
        }
    }

    val Portal: ImageVector by lazy {
        glyph("Portal") {
            moveTo(4f, 12f); arcToRelative(8f, 9f, 0f, true, true, 16f, 0f); arcToRelative(8f, 9f, 0f, true, true, -16f, 0f)
            moveTo(8f, 12f); arcToRelative(4f, 5f, 0f, true, true, 8f, 0f); arcToRelative(4f, 5f, 0f, true, true, -8f, 0f)
        }
    }

    val Flame: ImageVector by lazy {
        glyph("Flame") {
            moveTo(12f, 2.5f); curveTo(16f, 7f, 18.5f, 10.5f, 18.5f, 14.5f)
            curveTo(18.5f, 18.5f, 15.5f, 21.5f, 12f, 21.5f); curveTo(8.5f, 21.5f, 5.5f, 18.5f, 5.5f, 14.5f)
            curveTo(5.5f, 11.5f, 7.5f, 9.5f, 9f, 8f); curveTo(9f, 10.5f, 10f, 12f, 11.5f, 12.5f)
            curveTo(11f, 9f, 11.5f, 5.5f, 12f, 2.5f); close()
        }
    }

    val Coin: ImageVector by lazy {
        glyph("Coin") {
            moveTo(12f, 3f); lineTo(20f, 12f); lineTo(12f, 21f); lineTo(4f, 12f); close()
            moveTo(12f, 7.5f); lineTo(16f, 12f); lineTo(12f, 16.5f); lineTo(8f, 12f); close()
        }
    }

    val VerifiedSeal: ImageVector by lazy {
        glyph("Verified") {
            moveTo(12f, 2.5f); lineTo(20f, 7f); lineTo(20f, 17f); lineTo(12f, 21.5f); lineTo(4f, 17f); lineTo(4f, 7f); close()
            moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16.5f, 9f)
        }
    }

    val Hourglass: ImageVector by lazy {
        glyph("Timed") {
            moveTo(6f, 3f); lineTo(18f, 3f); moveTo(6f, 21f); lineTo(18f, 21f)
            moveTo(7f, 3f); lineTo(17f, 21f); moveTo(17f, 3f); lineTo(7f, 21f)
        }
    }

    val Check: ImageVector by lazy {
        glyph("Check") {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
        }
    }

    fun stat(stat: Stat): ImageVector = when (stat) {
        Stat.STR -> Strength
        Stat.INT -> Intelligence
        Stat.WIL -> Willpower
        Stat.VIT -> Vitality
        Stat.CHA -> Charisma
    }

    private const val GRID = 24f
    private const val STROKE = 1.75f

    private fun glyph(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = GRID, viewportHeight = GRID)
            .path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
                pathBuilder = pathBuilder,
            )
            .build()
}

@ComponentPreview
@Composable
private fun GlyphsPreview() {
    AwakenTheme {
        val colors = AwakenTheme.colors
        Row {
            Stat.entries.forEach { stat ->
                Icon(AwakenGlyphs.stat(stat), contentDescription = null, tint = colors.stat(stat), modifier = Modifier.size(AwakenTheme.dimens.sigil))
            }
            listOf(AwakenGlyphs.Shield, AwakenGlyphs.Fragment, AwakenGlyphs.Portal, AwakenGlyphs.Flame, AwakenGlyphs.Coin, AwakenGlyphs.VerifiedSeal, AwakenGlyphs.Hourglass)
                .forEach { Icon(it, contentDescription = null, tint = colors.system, modifier = Modifier.size(AwakenTheme.dimens.sigil)) }
        }
    }
}
