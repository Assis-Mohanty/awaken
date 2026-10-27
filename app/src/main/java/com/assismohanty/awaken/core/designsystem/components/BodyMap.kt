package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.body.BodyGeometry
import com.assismohanty.awaken.core.designsystem.body.BodySide
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.model.Stat
import kotlin.math.roundToInt

/**
 * The muscle map (DESIGN_SYSTEM §3, Gym): one stylised V-taper figure, front or back via a segmented control. The body is
 * the divider tone with a muted-ink outline and seams (visible on any card), no face and no skin tone; trained muscles fill in [accent] at the
 * alpha in [intensity] (0..1, the five heat steps; missing = unlit), animating in over 300ms.
 *
 * Paths come from react-native-body-highlighter (MIT, see `core/designsystem/body/BodyPaths.kt`). Each muscle region is its own TalkBack node
 * (name and trained / not trained); when [onMuscleClick] is set a tap or a TalkBack activation reports the region's
 * muscle (for shared regions, the more-loaded one; [BodyGeometry.regionMuscles] lists both). [description] summarises
 * the whole map.
 */
@Composable
fun BodyMap(
    intensity: Map<Muscle, Float>,
    description: String,
    modifier: Modifier = Modifier,
    accent: Color = AwakenTheme.colors.stat(Stat.STR),
    onMuscleClick: ((Muscle) -> Unit)? = null,
    initialSide: BodySide = BodySide.FRONT,
) {
    var side by rememberSaveable { mutableStateOf(initialSide) }
    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SegmentedControl(
            options = listOf(lexString(R.string.body_front), lexString(R.string.body_back)),
            selected = side.ordinal,
            onSelect = { side = BodySide.entries[it] },
            modifier = Modifier.fillMaxWidth(),
        )
        BodyFigure(side, intensity, accent, onMuscleClick)
    }
}

@Composable
private fun BodyFigure(side: BodySide, intensity: Map<Muscle, Float>, accent: Color, onMuscleClick: ((Muscle) -> Unit)?) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val figure = BodyGeometry.figure(side)
    val fill = remember(intensity, side) { Animatable(if (motion.reduceMotion) 1f else 0f) }
    LaunchedEffect(intensity, side) { fill.animateTo(1f, tween(motion.duration(FILL_MS), easing = motion.standardEasing)) }
    // Both sides share one frame, cropped to the figures, so switching never rescales.
    val frame = FRAME
    val height = AwakenTheme.dimens.bodyMapHeight
    val width = height * (frame.width / frame.height)
    val trained = lexString(R.string.body_muscle_trained)
    val untrained = lexString(R.string.body_muscle_untrained)
    BoxWithConstraints(modifier = Modifier.width(width).height(height)) {
        val density = LocalDensity.current
        val scale = with(density) { minOf(maxWidth.toPx() / frame.width, maxHeight.toPx() / frame.height) }
        val left = with(density) { (maxWidth.toPx() - frame.width * scale) / 2f } - frame.left * scale
        val top = with(density) { (maxHeight.toPx() - frame.height * scale) / 2f } - frame.top * scale
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onMuscleClick == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(side, onMuscleClick) {
                            detectTapGestures { tap ->
                                val x = (tap.x - left) / scale
                                val y = (tap.y - top) / scale
                                figure.regions.lastOrNull { it.contains(x, y) }?.let { onMuscleClick(it.primary(intensity)) }
                            }
                        }
                    },
                ),
        ) {
            // Strokes are set in dp and divided by the path scale so they keep their on-screen width.
            val outline = Stroke(width = OUTLINE.toPx() / scale)
            val seam = Stroke(width = SEAM.toPx() / scale)
            val bodyFill = colors.divider
            val outlineInk = colors.textMuted
            val seamInk = colors.textMuted.copy(alpha = SEAM_ALPHA)
            translate(left = left, top = top) {
                withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                    drawPath(figure.outline, bodyFill)
                    figure.base.forEach {
                        drawPath(it, bodyFill)
                        drawPath(it, seamInk, style = seam)
                    }
                    figure.regions.forEach { region ->
                        val level = region.muscles.maxOf { intensity[it] ?: 0f } * fill.value
                        drawPath(region.path, bodyFill)
                        if (level > 0f) drawPath(region.path, accent.copy(alpha = level.coerceIn(0f, 1f)))
                        drawPath(region.path, seamInk, style = seam)
                    }
                    drawPath(figure.outline, outlineInk, style = outline)
                }
            }
        }
        // One TalkBack node per muscle region, over its bounds; semantics only, so taps still reach the canvas.
        figure.regions.forEach { region ->
            val names = region.muscles.map { lexString(it.nameRes()) }.joinToString()
            val lit = region.muscles.any { (intensity[it] ?: 0f) > 0f }
            val bounds = region.bounds
            val nodeWidth = with(density) { (bounds.width * scale).toDp() }
            val nodeHeight = with(density) { (bounds.height * scale).toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((left + bounds.left * scale).roundToInt(), (top + bounds.top * scale).roundToInt()) }
                    .size(nodeWidth, nodeHeight)
                    .semantics {
                        contentDescription = names
                        stateDescription = if (lit) trained else untrained
                        if (onMuscleClick != null) {
                            role = Role.Button
                            onClick { onMuscleClick(region.primary(intensity)); true }
                        }
                    },
            )
        }
    }
}

/** The muscle a region reports: its more-loaded catalog group (first one when equal). */
private fun BodyGeometry.MuscleRegion.primary(intensity: Map<Muscle, Float>): Muscle = muscles.maxBy { intensity[it] ?: 0f }

/** The silhouette outline and the seams between muscles, in the muted ink so the figure reads on any card. */
private val OUTLINE = 1.25.dp
private val SEAM = 0.75.dp
private const val SEAM_ALPHA = 0.55f
private const val FILL_MS = 300

/** The union of both sides' bounds plus a little air, in source units. */
private val FRAME: Rect by lazy {
    val f = BodyGeometry.front.bounds
    val b = BodyGeometry.back.bounds
    Rect(minOf(f.left, b.left), minOf(f.top, b.top), maxOf(f.right, b.right), maxOf(f.bottom, b.bottom)).inflate(FRAME_AIR)
}
private const val FRAME_AIR = 12f

@ComponentPreview
@Composable
private fun BodyMapPreview() {
    AwakenTheme(reduceMotion = true) {
        BodyMap(
            intensity = mapOf(
                Muscle.CHEST to 1f, Muscle.TRICEPS to 0.56f, Muscle.SHOULDERS to 0.56f, Muscle.ABDOMINALS to 0.38f,
                Muscle.QUADRICEPS to 0.78f, Muscle.LATS to 0.38f, Muscle.GLUTES to 0.38f, Muscle.BICEPS to 0.2f,
            ),
            description = "Chest, triceps and shoulders trained",
        )
    }
}

@ComponentPreview
@Composable
private fun BodyMapBackPreview() {
    AwakenTheme(reduceMotion = true) {
        BodyMap(
            intensity = mapOf(
                Muscle.LATS to 1f, Muscle.TRAPS to 0.56f, Muscle.TRICEPS to 0.38f, Muscle.GLUTES to 0.78f,
                Muscle.HAMSTRINGS to 0.56f, Muscle.CALVES to 0.2f, Muscle.LOWER_BACK to 0.38f,
            ),
            description = "Back muscles trained",
            initialSide = BodySide.BACK,
        )
    }
}
