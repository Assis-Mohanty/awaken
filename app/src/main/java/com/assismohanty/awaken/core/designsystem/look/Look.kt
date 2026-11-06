package com.assismohanty.awaken.core.designsystem.look

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.assismohanty.awaken.core.designsystem.theme.AwakenDimens
import com.assismohanty.awaken.core.designsystem.theme.AwakenPalette
import com.assismohanty.awaken.core.designsystem.theme.AwakenShapes
import com.assismohanty.awaken.core.designsystem.theme.AwakenType

/** Which look is installed. Plates is the only one at launch; others would be free choices in Settings, never a Store item. */
enum class LookId { Plates }

/**
 * A look is the set of swappable parts (to_build_next.md): the tokens (colours, fonts, corner radii) here, and the
 * signature components (big number, today's progress, session dial, chart style) as they are added in `core/designsystem`.
 *
 * Feature screens never name a look and never read this: they use `AwakenTheme` tokens and the design-system
 * components, which read the installed look. `FeatureBoundaryTest` enforces it.
 */
@Immutable
interface Look {
    val id: LookId
    val light: AwakenPalette
    val dark: AwakenPalette
    val type: AwakenType
    val shapes: AwakenShapes
    val dimens: AwakenDimens
}

/** The Plates look: chalk and steel, five plate colours for the five stats, round forms. */
object PlatesLook : Look {
    override val id: LookId = LookId.Plates
    override val light: AwakenPalette = AwakenPalette.Light
    override val dark: AwakenPalette = AwakenPalette.Dark
    override val type: AwakenType = AwakenType()
    override val shapes: AwakenShapes = AwakenShapes()
    override val dimens: AwakenDimens = AwakenDimens()
}

val LocalLook = staticCompositionLocalOf<Look> { PlatesLook }
