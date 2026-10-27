package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.body.Muscle

/**
 * Front and back figures with the 17 catalog muscle groups as angular plates (DESIGN_SYSTEM §3, Gym). Each muscle is
 * filled with [accent] at the alpha in [intensity] (0..1; missing = unlit). Tapping a plate reports its muscle when
 * [onMuscleClick] is set; the canvas is one TalkBack node described by [description], so screens must also offer the
 * same choice as chips. Drawn from our own geometry: no third-party artwork.
 */
@Composable
fun BodyMap(
    intensity: Map<Muscle, Float>,
    description: String,
    modifier: Modifier = Modifier,
    accent: Color = AwakenTheme.palette.system,
    onMuscleClick: ((Muscle) -> Unit)? = null,
) {
    val colors = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.bodyMapHeight)
                .semantics { contentDescription = description }
                .then(
                    if (onMuscleClick == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(onMuscleClick) {
                            detectTapGestures { tap ->
                                val frame = Frame.of(size.width.toFloat(), size.height.toFloat())
                                val point = frame.toUnits(tap)
                                Geometry.regions.lastOrNull { it.contains(point) }?.let { onMuscleClick(it.muscle) }
                            }
                        }
                    },
                ),
        ) {
            val frame = Frame.of(size.width, size.height)
            val stroke = Stroke(width = dimens.hairline.toPx())
            Geometry.base.forEach { shape ->
                val path = shape.toPath(frame)
                drawPath(path, colors.panelRaised)
                drawPath(path, colors.edge, style = stroke)
            }
            Geometry.heads.forEach { center ->
                drawCircle(colors.panelRaised, Geometry.HEAD_RADIUS * frame.scale, frame.toPx(center))
                drawCircle(colors.edge, Geometry.HEAD_RADIUS * frame.scale, frame.toPx(center), style = stroke)
            }
            Geometry.regions.forEach { region -> drawRegion(region, frame, intensity[region.muscle] ?: 0f, accent, colors.edge, stroke) }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(R.string.body_front, R.string.body_back).forEach { label ->
                Text(
                    lexString(label),
                    style = AwakenTheme.type.caption,
                    color = colors.mist,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun DrawScope.drawRegion(region: Region, frame: Frame, level: Float, accent: Color, edge: Color, stroke: Stroke) {
    val path = region.toPath(frame)
    drawPath(path, accent.copy(alpha = level.coerceIn(0f, 1f).coerceAtLeast(AwakenAlpha.HEATMAP_STEPS.first())))
    drawPath(path, edge, style = stroke)
}

/** Maps the figures' unit space (Geometry.WIDTH × Geometry.HEIGHT) onto the canvas, centred, aspect kept. */
private class Frame(val scale: Float, val left: Float, val top: Float) {
    fun toPx(p: Offset) = Offset(left + p.x * scale, top + p.y * scale)
    fun toUnits(p: Offset) = Offset((p.x - left) / scale, (p.y - top) / scale)

    companion object {
        fun of(width: Float, height: Float): Frame {
            val scale = minOf(width / Geometry.WIDTH, height / Geometry.HEIGHT)
            return Frame(scale, (width - Geometry.WIDTH * scale) / 2f, (height - Geometry.HEIGHT * scale) / 2f)
        }
    }
}

private open class Plate(val points: List<Offset>) {
    fun toPath(frame: Frame) = Path().apply {
        points.forEachIndexed { i, p -> frame.toPx(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        close()
    }

    /** Even-odd ray cast. */
    fun contains(p: Offset): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}

private class Region(val muscle: Muscle, points: List<Offset>) : Plate(points)

/**
 * One figure is 100 × 182 units; the back figure sits [BACK_OFFSET] to the right. Plates are drawn for the figure's
 * left half and mirrored about x = 50 unless they straddle the midline.
 */
private object Geometry {
    const val FIGURE = 100f
    const val BACK_OFFSET = 112f
    const val WIDTH = BACK_OFFSET + FIGURE
    const val HEIGHT = 182f
    const val HEAD_RADIUS = 8f

    val heads = listOf(Offset(50f, 11f), Offset(BACK_OFFSET + 50f, 11f))

    private fun pts(vararg xy: Float) = xy.toList().chunked(2).map { (x, y) -> Offset(x, y) }
    private fun mirror(points: List<Offset>) = points.map { Offset(FIGURE - it.x, it.y) }.reversed()
    private fun shift(points: List<Offset>, dx: Float) = points.map { Offset(it.x + dx, it.y) }

    private fun both(points: List<Offset>) = listOf(points, mirror(points))

    private val silhouetteHalves = listOf(
        pts(38f, 27f, 50f, 25f, 50f, 99f, 37f, 99f, 35f, 86f, 38f, 74f, 36f, 56f, 36f, 40f), // torso
        pts(28f, 30f, 38f, 27f, 38f, 41f, 30f, 42f), // shoulder cap
        pts(28f, 40f, 37f, 40f, 36f, 57f, 28f, 58f), // upper arm
        pts(27f, 57f, 34f, 57f, 32f, 78f, 29f, 88f, 23f, 88f, 24f, 72f), // forearm
        pts(23f, 89f, 29f, 89f, 29f, 98f, 25f, 99f, 22f, 95f), // hand
        pts(36f, 96f, 49f, 96f, 48.5f, 128f, 46f, 170f, 40f, 170f, 37f, 150f, 36f, 128f, 35f, 110f), // leg
        pts(40f, 171f, 46f, 171f, 47f, 178f, 38f, 178f), // foot
    )
    private val neck = pts(45f, 18f, 55f, 18f, 56f, 26f, 44f, 26f)

    val base: List<Plate> = buildList {
        for (dx in listOf(0f, BACK_OFFSET)) {
            add(Plate(shift(neck, dx)))
            silhouetteHalves.forEach { half -> both(half).forEach { add(Plate(shift(it, dx))) } }
        }
    }

    private val front: List<Pair<Muscle, List<Offset>>> = listOf(
        Muscle.TRAPS to pts(38f, 27f, 45f, 24f, 45f, 28f),
        Muscle.SHOULDERS to pts(29f, 30f, 37f, 27.5f, 40f, 31f, 37f, 40f, 30f, 41f, 28f, 36f),
        Muscle.CHEST to pts(40f, 31f, 49.5f, 31f, 49.5f, 45f, 42f, 46f, 37.5f, 41f),
        Muscle.BICEPS to pts(29f, 42f, 36f, 41f, 36f, 55f, 32f, 57f, 29f, 54f),
        Muscle.FOREARMS to pts(28f, 58f, 33f, 58f, 31.5f, 75f, 28.5f, 86f, 24.5f, 86f, 25f, 71f),
        Muscle.ABDOMINALS to pts(42f, 47.5f, 49.5f, 46.5f, 49.5f, 77f, 43f, 75f),
        Muscle.ABDUCTORS to pts(36.5f, 80f, 41f, 78f, 40f, 92f, 36f, 94f),
        Muscle.QUADRICEPS to pts(37f, 96f, 41f, 93f, 45f, 99f, 46f, 112f, 45.5f, 126f, 39f, 128f, 36.5f, 112f),
        Muscle.ADDUCTORS to pts(45f, 90f, 49f, 89f, 48.5f, 112f, 46.5f, 110f, 45.5f, 98f),
        Muscle.CALVES to pts(38f, 135f, 44.5f, 134f, 43.5f, 158f, 40.5f, 165f, 37.5f, 150f),
    )
    private val frontCenter: List<Pair<Muscle, List<Offset>>> = listOf(
        Muscle.NECK to pts(46f, 19f, 54f, 19f, 54.5f, 25f, 45.5f, 25f),
    )
    private val back: List<Pair<Muscle, List<Offset>>> = listOf(
        Muscle.SHOULDERS to pts(29f, 30f, 38f, 28f, 38.5f, 38f, 30f, 41f, 28f, 36f),
        Muscle.TRICEPS to pts(29f, 42f, 36f, 40.5f, 36f, 56f, 32f, 57.5f, 28.5f, 54f),
        Muscle.FOREARMS to pts(28f, 58f, 33f, 58f, 31.5f, 75f, 28.5f, 86f, 24.5f, 86f, 25f, 71f),
        Muscle.MIDDLE_BACK to pts(42f, 33f, 49.5f, 44f, 49.5f, 56f, 46f, 56f, 40.5f, 40f),
        Muscle.LATS to pts(37f, 38f, 40.5f, 40.5f, 46f, 57f, 44f, 66f, 39f, 64f, 36.5f, 50f),
        Muscle.LOWER_BACK to pts(45f, 59f, 49.5f, 58f, 49.5f, 77f, 42f, 77f, 43.5f, 67f),
        Muscle.GLUTES to pts(37f, 80f, 49.5f, 80f, 49.5f, 95f, 40f, 98f, 36f, 91f),
        Muscle.HAMSTRINGS to pts(37f, 100f, 48f, 100f, 47f, 126f, 40f, 128f, 36.5f, 114f),
        Muscle.CALVES to pts(37.5f, 133f, 45f, 133f, 45f, 151f, 42f, 164f, 37.5f, 151f),
    )
    private val backCenter: List<Pair<Muscle, List<Offset>>> = listOf(
        Muscle.TRAPS to pts(50f, 20f, 57f, 26f, 62f, 29f, 50f, 44f, 38f, 29f, 43f, 26f),
    )

    val regions: List<Region> = buildList {
        front.forEach { (m, p) -> both(p).forEach { add(Region(m, it)) } }
        frontCenter.forEach { (m, p) -> add(Region(m, p)) }
        back.forEach { (m, p) -> both(p).forEach { add(Region(m, shift(it, BACK_OFFSET))) } }
        backCenter.forEach { (m, p) -> add(Region(m, shift(p, BACK_OFFSET))) }
    }

    /** Every muscle has at least one plate (tested). */
    val muscles: Set<Muscle> get() = regions.map { it.muscle }.toSet()
}

/** For tests: which muscle a tap at unit-space [x], [y] hits (null for none). */
internal fun bodyMapMuscleAt(x: Float, y: Float): Muscle? = Geometry.regions.lastOrNull { it.contains(Offset(x, y)) }?.muscle

internal fun bodyMapMuscles(): Set<Muscle> = Geometry.muscles

internal const val BODY_MAP_BACK_OFFSET = Geometry.BACK_OFFSET

@ComponentPreview
@Composable
private fun BodyMapPreview() {
    AwakenTheme {
        BodyMap(
            intensity = mapOf(
                Muscle.CHEST to 1f, Muscle.TRICEPS to 0.5f, Muscle.SHOULDERS to 0.5f,
                Muscle.QUADRICEPS to 0.72f, Muscle.LATS to 0.3f, Muscle.GLUTES to 0.3f,
            ),
            description = "Chest, triceps and shoulders trained",
        )
    }
}
