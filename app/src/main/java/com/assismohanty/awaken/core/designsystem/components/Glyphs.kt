package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * AWAKEN's one icon set (DESIGN_SYSTEM §2.4). Drawn here, not imported: a 24-unit grid with a 2-unit safe area, soft
 * geometry, and two weights that share every outline:
 * - **Line** icons for chrome and unselected states: 1.75-unit strokes, round caps and joins.
 * - **Solid** icons for the selected nav item, stats and small accents: filled shapes whose corners are rounded by a
 *   matching 1.5-unit stroke, so they read as friendly, not sharp.
 *
 * Drawn in black and tinted at the call site via `Icon(tint = …)`.
 */
object AwakenGlyphs {

    // ---- Stats (solid: they sit in a 15% tinted circle) ----

    /** STR: a bolt of power. */
    val Strength: ImageVector by lazy {
        solid("Strength") { moveTo(13.5f, 2.75f); lineTo(5.25f, 13.25f); lineTo(11.25f, 13.25f); lineTo(10.5f, 21.25f); lineTo(18.75f, 10.75f); lineTo(12.75f, 10.75f); close() }
    }

    /** INT: an open book. */
    val Intelligence: ImageVector by lazy {
        solid("Intelligence") {
            moveTo(11.25f, 6.25f); curveTo(9.5f, 5f, 6.75f, 4.5f, 3.5f, 4.9f); lineTo(3.5f, 18.4f); curveTo(6.5f, 18.1f, 9.25f, 18.6f, 11.25f, 19.9f); close()
            moveTo(12.75f, 6.25f); curveTo(14.5f, 5f, 17.25f, 4.5f, 20.5f, 4.9f); lineTo(20.5f, 18.4f); curveTo(17.5f, 18.1f, 14.75f, 18.6f, 12.75f, 19.9f); close()
        }
    }

    /** WIL: a target, focus held. */
    val Willpower: ImageVector by lazy {
        solid("Willpower", rounded = false, evenOdd = true) {
            circle(12f, 12f, 9.25f); circle(12f, 12f, 6.75f); circle(12f, 12f, 3.75f)
        }
    }

    /** VIT: a heart. */
    val Vitality: ImageVector by lazy {
        solid("Vitality") {
            moveTo(12f, 20.25f); curveTo(12f, 20.25f, 3.25f, 15.1f, 3.25f, 9f); curveTo(3.25f, 6.3f, 5.35f, 4.25f, 7.9f, 4.25f)
            curveTo(9.65f, 4.25f, 11.15f, 5.2f, 12f, 6.6f); curveTo(12.85f, 5.2f, 14.35f, 4.25f, 16.1f, 4.25f)
            curveTo(18.65f, 4.25f, 20.75f, 6.3f, 20.75f, 9f); curveTo(20.75f, 15.1f, 12f, 20.25f, 12f, 20.25f); close()
        }
    }

    /** CHA: two people. */
    val Charisma: ImageVector by lazy {
        solid("Charisma") {
            circle(9f, 7.75f, 3.25f)
            moveTo(2.75f, 19.5f); curveTo(2.75f, 15.75f, 5.5f, 13.25f, 9f, 13.25f); curveTo(12.5f, 13.25f, 15.25f, 15.75f, 15.25f, 19.5f); close()
            circle(16.75f, 8.25f, 2.75f)
            moveTo(16.4f, 13.4f); curveTo(19.3f, 13.5f, 21.25f, 15.8f, 21.25f, 19.5f); lineTo(17.25f, 19.5f); curveTo(17.25f, 17.1f, 16.9f, 15f, 16.4f, 13.4f); close()
        }
    }

    // ---- Rewards and status ----

    val Shield: ImageVector by lazy {
        line("Shield") {
            moveTo(12f, 3.5f); lineTo(18.75f, 6f); lineTo(18.75f, 11.5f)
            curveTo(18.75f, 15.6f, 15.9f, 19f, 12f, 20.5f); curveTo(8.1f, 19f, 5.25f, 15.6f, 5.25f, 11.5f); lineTo(5.25f, 6f); close()
        }
    }

    val Portal: ImageVector by lazy {
        line("Portal") {
            circle(12f, 12f, 8.5f)
            moveTo(8f, 12f); arcToRelative(4f, 5f, 0f, true, true, 8f, 0f); arcToRelative(4f, 5f, 0f, true, true, -8f, 0f)
        }
    }

    /** Streaks (solid). */
    val Flame: ImageVector by lazy {
        solid("Flame") {
            moveTo(12f, 21.25f); curveTo(8.2f, 21.25f, 5.5f, 18.6f, 5.5f, 15.1f); curveTo(5.5f, 11.6f, 8.1f, 9.4f, 9.4f, 6.6f)
            curveTo(10.1f, 8.3f, 10.9f, 9.3f, 11.9f, 9.8f); curveTo(12.1f, 7.3f, 13.2f, 4.9f, 15.3f, 2.9f)
            curveTo(15.7f, 6.6f, 18.5f, 9.5f, 18.5f, 14.8f); curveTo(18.5f, 18.5f, 15.8f, 21.25f, 12f, 21.25f); close()
        }
    }

    /** Gold. */
    val Coin: ImageVector by lazy {
        line("Coin") { circle(12f, 12f, 8.5f); circle(12f, 12f, 4.75f) }
    }

    val VerifiedSeal: ImageVector by lazy {
        line("Verified") { circle(12f, 12f, 8.5f); moveTo(8.5f, 12.2f); lineTo(11f, 14.7f); lineTo(15.7f, 9.6f) }
    }

    val Hourglass: ImageVector by lazy {
        line("Timed") {
            moveTo(6.5f, 3.5f); lineTo(17.5f, 3.5f); moveTo(6.5f, 20.5f); lineTo(17.5f, 20.5f)
            moveTo(8f, 3.5f); lineTo(8f, 6.5f); curveTo(8f, 8.8f, 12f, 10.4f, 12f, 12f); curveTo(12f, 13.6f, 8f, 15.2f, 8f, 17.5f); lineTo(8f, 20.5f)
            moveTo(16f, 3.5f); lineTo(16f, 6.5f); curveTo(16f, 8.8f, 12f, 10.4f, 12f, 12f); curveTo(12f, 13.6f, 16f, 15.2f, 16f, 17.5f); lineTo(16f, 20.5f)
        }
    }

    val Check: ImageVector by lazy {
        line("Check", width = CHECK_STROKE) { moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f) }
    }

    /** Level: a step up. */
    val Level: ImageVector by lazy {
        line("Level") { circle(12f, 12f, 8.75f); moveTo(8.25f, 13.75f); lineTo(12f, 10f); lineTo(15.75f, 13.75f) }
    }

    val Trophy: ImageVector by lazy {
        line("Trophy") {
            moveTo(7.5f, 4f); lineTo(16.5f, 4f); lineTo(16.5f, 9.75f); curveTo(16.5f, 12.25f, 14.5f, 14.25f, 12f, 14.25f); curveTo(9.5f, 14.25f, 7.5f, 12.25f, 7.5f, 9.75f); close()
            moveTo(7.5f, 6f); lineTo(5.5f, 6f); curveTo(4.7f, 6f, 4f, 6.7f, 4f, 7.5f); curveTo(4f, 9.4f, 5.6f, 11f, 7.6f, 11f)
            moveTo(16.5f, 6f); lineTo(18.5f, 6f); curveTo(19.3f, 6f, 20f, 6.7f, 20f, 7.5f); curveTo(20f, 9.4f, 18.4f, 11f, 16.4f, 11f)
            moveTo(12f, 14.25f); lineTo(12f, 19.5f); moveTo(8.5f, 19.75f); lineTo(15.5f, 19.75f)
        }
    }

    // ---- Navigation (line = unselected, solid = selected) ----

    /** Today tab. */
    val Home: ImageVector by lazy { line("Home") { house() } }
    val HomeSolid: ImageVector by lazy { solid("HomeSolid", evenOdd = true) { house(); roundRect(10.25f, 14.5f, 3.5f, 5.5f, 1f) } }

    /** Focus tab: a target, one ring and a dot. */
    val Focus: ImageVector by lazy { line("Focus") { circle(12f, 12f, 8.5f); circle(12f, 12f, 3.5f) } }
    val FocusSolid: ImageVector by lazy {
        solid("FocusSolid", rounded = false, evenOdd = true) { circle(12f, 12f, 9.25f); circle(12f, 12f, 6.25f); circle(12f, 12f, 3.5f) }
    }

    /** Gym tab. */
    val Dumbbell: ImageVector by lazy {
        line("Dumbbell") {
            roundRect(5.25f, 6.75f, 3.25f, 10.5f, 1.25f); roundRect(15.5f, 6.75f, 3.25f, 10.5f, 1.25f)
            moveTo(8.5f, 12f); lineTo(15.5f, 12f); moveTo(2.75f, 10f); lineTo(2.75f, 14f); moveTo(21.25f, 10f); lineTo(21.25f, 14f)
        }
    }
    val DumbbellSolid: ImageVector by lazy {
        solid("DumbbellSolid") {
            roundRect(5.25f, 6.75f, 3.25f, 10.5f, 1.25f); roundRect(15.5f, 6.75f, 3.25f, 10.5f, 1.25f)
            roundRect(8.5f, 11.1f, 7f, 1.8f, 0.4f); roundRect(2.1f, 9.6f, 1.3f, 4.8f, 0.6f); roundRect(20.6f, 9.6f, 1.3f, 4.8f, 0.6f)
        }
    }

    /** Diet tab: a bowl with a leaf. */
    val Bowl: ImageVector by lazy { line("Bowl") { bowl(); leaf() } }
    val BowlSolid: ImageVector by lazy { solid("BowlSolid") { bowl(); leaf() } }

    /** Progress tab: three rising bars. */
    val Bars: ImageVector by lazy { line("Bars") { bars() } }
    val BarsSolid: ImageVector by lazy { solid("BarsSolid") { bars() } }

    /** Quest list. */
    val Directive: ImageVector by lazy {
        line("Directive") {
            moveTo(4f, 6.5f); lineTo(5.75f, 8.25f); lineTo(8.75f, 5f)
            moveTo(11.75f, 6.5f); lineTo(20f, 6.5f); moveTo(11.75f, 12f); lineTo(20f, 12f); moveTo(11.75f, 17.5f); lineTo(20f, 17.5f)
            circle(6.25f, 12f, 1.5f); circle(6.25f, 17.5f, 1.5f)
        }
    }

    /** Status radar. */
    val Pentagon: ImageVector by lazy {
        line("Status") {
            star(points = 5, outer = 9f, inner = 9f * cos(PI / 5).toFloat(), cy = 12.6f)
            star(points = 5, outer = 4.5f, inner = 4.5f * cos(PI / 5).toFloat(), cy = 12.6f)
        }
    }

    /** Vault: a safe. */
    val Vault: ImageVector by lazy {
        line("Vault") {
            roundRect(3.75f, 4f, 16.5f, 15f, 3f); circle(12f, 11.5f, 3.5f)
            moveTo(12f, 8f); lineTo(12f, 9.25f); moveTo(7f, 19f); lineTo(7f, 20.75f); moveTo(17f, 19f); lineTo(17f, 20.75f)
        }
    }

    /** Paths: a winding route to a flag. */
    val Path: ImageVector by lazy {
        line("Path") {
            moveTo(5f, 20.25f); curveTo(5f, 15.5f, 15f, 17f, 15f, 12.25f); curveTo(15f, 10.25f, 14f, 9.5f, 14f, 8.5f)
            moveTo(14f, 8.5f); lineTo(14f, 3.25f); lineTo(19.5f, 3.25f); lineTo(18f, 5.25f); lineTo(19.5f, 7.25f); lineTo(14f, 7.25f)
        }
    }

    val Settings: ImageVector by lazy { line("Settings") { gear(); circle(12f, 12f, 3f) } }

    val Bell: ImageVector by lazy {
        line("Bell") {
            moveTo(6f, 16.75f); lineTo(6f, 11f); curveTo(6f, 7.7f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.7f, 18f, 11f); lineTo(18f, 16.75f); lineTo(19.5f, 18.25f); lineTo(4.5f, 18.25f); close()
            moveTo(10f, 20.75f); curveTo(10.5f, 21.4f, 11.2f, 21.75f, 12f, 21.75f); curveTo(12.8f, 21.75f, 13.5f, 21.4f, 14f, 20.75f)
            moveTo(12f, 3f); lineTo(12f, 5f)
        }
    }

    val Star: ImageVector by lazy { line("Star") { star(points = 5, outer = 9.5f, inner = 4.2f, cy = 12.75f) } }

    val Gate: ImageVector by lazy {
        line("Gate") {
            moveTo(5f, 20.5f); lineTo(5f, 11f); curveTo(5f, 7.1f, 8.1f, 4f, 12f, 4f); curveTo(15.9f, 4f, 19f, 7.1f, 19f, 11f); lineTo(19f, 20.5f)
            moveTo(3.5f, 20.5f); lineTo(20.5f, 20.5f)
            moveTo(9.5f, 20.5f); lineTo(9.5f, 14f); curveTo(9.5f, 12.6f, 10.6f, 11.5f, 12f, 11.5f); curveTo(13.4f, 11.5f, 14.5f, 12.6f, 14.5f, 14f); lineTo(14.5f, 20.5f)
        }
    }

    // ---- Chrome ----

    val Close: ImageVector by lazy { line("Close") { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) } }

    val Minus: ImageVector by lazy { line("Minus") { moveTo(5f, 12f); lineTo(19f, 12f) } }

    val Plus: ImageVector by lazy { line("Plus") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }

    val Info: ImageVector by lazy {
        line("Info") { circle(12f, 12f, 8.75f); moveTo(12f, 11f); lineTo(12f, 16.25f); moveTo(12f, 7.75f); lineTo(12f, 7.9f) }
    }

    val ChevronLeft: ImageVector by lazy { line("ChevronLeft") { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) } }
    val ChevronDown: ImageVector by lazy { line("ChevronDown") { moveTo(5.5f, 9f); lineTo(12f, 15.5f); lineTo(18.5f, 9f) } }
    val ChevronRight: ImageVector by lazy { line("ChevronRight") { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) } }

    /** Back navigation. */
    val ArrowLeft: ImageVector by lazy { line("ArrowLeft") { moveTo(19.5f, 12f); lineTo(4.75f, 12f); moveTo(11f, 5.75f); lineTo(4.75f, 12f); lineTo(11f, 18.25f) } }
    val ArrowUp: ImageVector by lazy { line("ArrowUp") { moveTo(12f, 19f); lineTo(12f, 5f); moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f) } }
    val ArrowDown: ImageVector by lazy { line("ArrowDown") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f) } }

    val More: ImageVector by lazy { solid("More", rounded = false) { circle(5.75f, 12f, 1.75f); circle(12f, 12f, 1.75f); circle(18.25f, 12f, 1.75f) } }

    val Search: ImageVector by lazy { line("Search") { circle(10.5f, 10.5f, 6.5f); moveTo(15.4f, 15.4f); lineTo(20f, 20f) } }

    val Pencil: ImageVector by lazy {
        line("Pencil") {
            moveTo(4.25f, 19.75f); lineTo(4.9f, 16.2f); lineTo(15.6f, 5.5f); curveTo(16.4f, 4.7f, 17.7f, 4.7f, 18.5f, 5.5f)
            curveTo(19.3f, 6.3f, 19.3f, 7.6f, 18.5f, 8.4f); lineTo(7.8f, 19.1f); close()
            moveTo(13.75f, 7.35f); lineTo(16.65f, 10.25f)
        }
    }

    val Trash: ImageVector by lazy {
        line("Trash") {
            moveTo(4.5f, 7f); lineTo(19.5f, 7f)
            moveTo(9.5f, 7f); lineTo(9.5f, 5f); curveTo(9.5f, 4.45f, 9.95f, 4f, 10.5f, 4f); lineTo(13.5f, 4f); curveTo(14.05f, 4f, 14.5f, 4.45f, 14.5f, 5f); lineTo(14.5f, 7f)
            moveTo(6.5f, 7f); lineTo(7.4f, 19f); curveTo(7.45f, 19.6f, 7.9f, 20f, 8.5f, 20f); lineTo(15.5f, 20f); curveTo(16.1f, 20f, 16.55f, 19.6f, 16.6f, 19f); lineTo(17.5f, 7f)
            moveTo(10.5f, 11f); lineTo(10.5f, 16f); moveTo(13.5f, 11f); lineTo(13.5f, 16f)
        }
    }

    val Lock: ImageVector by lazy {
        line("Lock") {
            roundRect(5f, 10.5f, 14f, 10f, 2.5f)
            moveTo(8f, 10.5f); lineTo(8f, 8f); curveTo(8f, 5.8f, 9.8f, 4f, 12f, 4f); curveTo(14.2f, 4f, 16f, 5.8f, 16f, 8f); lineTo(16f, 10.5f)
            moveTo(12f, 14.5f); lineTo(12f, 16.5f)
        }
    }

    val Calendar: ImageVector by lazy {
        line("Calendar") { roundRect(4f, 5.5f, 16f, 14.75f, 3f); moveTo(4f, 10f); lineTo(20f, 10f); moveTo(8.5f, 3.5f); lineTo(8.5f, 7f); moveTo(15.5f, 3.5f); lineTo(15.5f, 7f) }
    }

    val Timer: ImageVector by lazy {
        line("Timer") { circle(12f, 13.5f, 7.5f); moveTo(12f, 13.5f); lineTo(12f, 10f); moveTo(10f, 3f); lineTo(14f, 3f); moveTo(17.8f, 7.7f); lineTo(19f, 6.5f) }
    }

    val Drop: ImageVector by lazy {
        line("Drop") {
            moveTo(12f, 3.5f); curveTo(12f, 3.5f, 5.5f, 10.5f, 5.5f, 14.5f); curveTo(5.5f, 18.1f, 8.4f, 21f, 12f, 21f)
            curveTo(15.6f, 21f, 18.5f, 18.1f, 18.5f, 14.5f); curveTo(18.5f, 10.5f, 12f, 3.5f, 12f, 3.5f); close()
        }
    }

    /** Screen time. */
    val Phone: ImageVector by lazy { line("Phone") { roundRect(6.75f, 2.75f, 10.5f, 18.5f, 2.75f); moveTo(10.5f, 18f); lineTo(13.5f, 18f) } }

    /** Insights (solid). */
    val Sparkle: ImageVector by lazy {
        solid("Sparkle") { moveTo(12f, 3f); quadTo(13f, 11f, 21f, 12f); quadTo(13f, 13f, 12f, 21f); quadTo(11f, 13f, 3f, 12f); quadTo(11f, 11f, 12f, 3f); close() }
    }

    // ---- Time of day ----

    val Sunrise: ImageVector by lazy {
        line("Sunrise") {
            moveTo(3f, 18.5f); lineTo(21f, 18.5f)
            moveTo(7f, 18.5f); arcTo(5f, 5f, 0f, false, true, 17f, 18.5f)
            moveTo(12f, 6f); lineTo(12f, 9f); moveTo(5f, 10.5f); lineTo(6.75f, 12.25f); moveTo(19f, 10.5f); lineTo(17.25f, 12.25f)
        }
    }

    val Sun: ImageVector by lazy {
        line("Sun") {
            circle(12f, 12f, 4f)
            moveTo(12f, 2.75f); lineTo(12f, 4.5f); moveTo(12f, 19.5f); lineTo(12f, 21.25f)
            moveTo(2.75f, 12f); lineTo(4.5f, 12f); moveTo(19.5f, 12f); lineTo(21.25f, 12f)
            moveTo(5.45f, 5.45f); lineTo(6.7f, 6.7f); moveTo(17.3f, 17.3f); lineTo(18.55f, 18.55f)
            moveTo(5.45f, 18.55f); lineTo(6.7f, 17.3f); moveTo(17.3f, 6.7f); lineTo(18.55f, 5.45f)
        }
    }

    val Moon: ImageVector by lazy {
        line("Moon") { moveTo(19.75f, 14.5f); arcTo(8f, 8f, 0f, true, true, 9.5f, 4.25f); arcTo(6.5f, 6.5f, 0f, false, false, 19.75f, 14.5f); close() }
    }

    val Clock: ImageVector by lazy { line("Clock") { circle(12f, 12f, 8.75f); moveTo(12f, 7.25f); lineTo(12f, 12f); lineTo(15.25f, 14f) } }

    fun stat(stat: Stat): ImageVector = when (stat) {
        Stat.STR -> Strength
        Stat.INT -> Intelligence
        Stat.WIL -> Willpower
        Stat.VIT -> Vitality
        Stat.CHA -> Charisma
    }

    /** Every glyph, for the catalog and the preview grid. */
    val all: List<ImageVector> by lazy {
        listOf(
            Strength, Intelligence, Willpower, Vitality, Charisma, Flame, Sparkle, More,
            Home, HomeSolid, Dumbbell, DumbbellSolid, Bowl, BowlSolid, Bars, BarsSolid,
            Directive, Pentagon, Vault, Path, Settings, Bell, Level, Trophy,
            Shield, Portal, Coin, VerifiedSeal, Hourglass, Check, Gate,
            Star, Close, Plus, Info,
            ChevronLeft, ChevronDown, ChevronRight, ArrowLeft, ArrowUp, ArrowDown, Search, Pencil,
            Trash, Lock, Calendar, Timer, Drop, Phone, Sunrise, Sun, Moon, Clock,
        )
    }

    // ---- Construction ----

    private const val GRID = 24f
    private const val STROKE = 1.75f
    private const val CHECK_STROKE = 2f
    /** Softens a solid shape's corners; adds 0.75 units outside the path, which the geometry above allows for. */
    private const val SOLID_ROUNDING = 1.5f

    private fun builder(name: String) =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = GRID, viewportHeight = GRID)

    private fun line(name: String, width: Float = STROKE, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        builder(name)
            .path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = pathBuilder,
            )
            .build()

    private fun solid(name: String, rounded: Boolean = true, evenOdd: Boolean = false, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        builder(name)
            .path(
                fill = SolidColor(Color.Black),
                stroke = if (rounded) SolidColor(Color.Black) else null,
                strokeLineWidth = if (rounded) SOLID_ROUNDING else 0f,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                pathBuilder = pathBuilder,
            )
            .build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    @Suppress("LongParameterList")
    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        lineTo(x + w - r, y); arcTo(r, r, 0f, false, true, x + w, y + r)
        lineTo(x + w, y + h - r); arcTo(r, r, 0f, false, true, x + w - r, y + h)
        lineTo(x + r, y + h); arcTo(r, r, 0f, false, true, x, y + h - r)
        lineTo(x, y + r); arcTo(r, r, 0f, false, true, x + r, y)
        close()
    }

    /** A regular star (or polygon when inner = outer·cos(π/n)) pointing up, centred on (12, [cy]). */
    private fun PathBuilder.star(points: Int, outer: Float, inner: Float, cy: Float) {
        val steps = points * 2
        for (i in 0 until steps) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / points
            val x = 12f + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** Eight flat-topped teeth around a 7.25-unit body. */
    private fun PathBuilder.gear() {
        val teeth = 8
        val outer = 9.25f
        val body = 7.25f
        val step = 2 * PI / teeth
        for (k in 0 until teeth) {
            val a = k * step
            val pts = listOf(body to a - step * 0.5 + step * 0.12, outer to a - step * 0.2, outer to a + step * 0.2, body to a + step * 0.5 - step * 0.12)
            pts.forEachIndexed { i, (r, angle) ->
                val x = 12f + r * cos(angle).toFloat()
                val y = 12f + r * sin(angle).toFloat()
                if (k == 0 && i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        close()
    }

    private fun PathBuilder.house() {
        moveTo(4.25f, 10.4f); lineTo(11.1f, 4.6f); curveTo(11.6f, 4.15f, 12.4f, 4.15f, 12.9f, 4.6f); lineTo(19.75f, 10.4f)
        lineTo(19.75f, 18.5f); curveTo(19.75f, 19.33f, 19.08f, 20f, 18.25f, 20f); lineTo(5.75f, 20f)
        curveTo(4.92f, 20f, 4.25f, 19.33f, 4.25f, 18.5f); close()
    }

    private fun PathBuilder.bowl() {
        moveTo(3.5f, 11.5f); lineTo(20.5f, 11.5f); curveTo(20.5f, 16.2f, 16.7f, 20f, 12f, 20f); curveTo(7.3f, 20f, 3.5f, 16.2f, 3.5f, 11.5f); close()
    }

    private fun PathBuilder.leaf() {
        moveTo(12.5f, 8.75f); curveTo(12.5f, 5.5f, 15f, 3.5f, 18.5f, 3.5f); curveTo(18.5f, 6.75f, 16f, 8.75f, 12.5f, 8.75f); close()
    }

    private fun PathBuilder.bars() {
        roundRect(4f, 12.5f, 4f, 7.5f, 1.25f); roundRect(10f, 8f, 4f, 12f, 1.25f); roundRect(16f, 4f, 4f, 16f, 1.25f)
    }
}

@ComponentPreview
@Composable
private fun GlyphsPreview() {
    AwakenTheme {
        val colors = AwakenTheme.colors
        FlowRow(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AwakenGlyphs.all.forEach { Icon(it, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.icon)) }
        }
    }
}
