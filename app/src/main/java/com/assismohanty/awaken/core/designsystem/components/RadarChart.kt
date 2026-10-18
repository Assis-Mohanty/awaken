package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.StatLevel
import com.assismohanty.awaken.engine.model.Stat
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pentagon radar (DESIGN_SYSTEM §3): one axis per stat with its sigil, current polygon filled `system` @ 25 % with a
 * 1.5dp edge, optional previous polygon dashed in `mist`. Values are absolute; [maxValue] is the axis max (for stat
 * levels use `StatLevel.radarMax`). TalkBack reads every stat ("Strength level 12, …").
 */
@Composable
fun RadarChart(
    values: Map<Stat, Float>,
    maxValue: Float,
    modifier: Modifier = Modifier,
    previous: Map<Stat, Float>? = null,
    description: String = radarDescription(values),
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val sigils = Stat.entries.associateWith { rememberVectorPainter(AwakenGlyphs.stat(it)) }
    Canvas(
        modifier = modifier
            .size(dimens.radarSize)
            .semantics { contentDescription = description },
    ) {
        val sigilPx = dimens.iconSmall.toPx()
        val radius = size.minDimension / 2f - sigilPx * SIGIL_MARGIN
        fun point(index: Int, fraction: Float): Offset {
            val angle = Math.toRadians(START_ANGLE + index * (360.0 / Stat.entries.size))
            return Offset(center.x + radius * fraction * cos(angle).toFloat(), center.y + radius * fraction * sin(angle).toFloat())
        }
        fun polygon(fraction: (Stat) -> Float) = Path().apply {
            Stat.entries.forEachIndexed { i, stat ->
                val p = point(i, fraction(stat).coerceIn(0f, 1f))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        val hairline = dimens.hairline.toPx()

        GRID_STEPS.forEach { step -> drawPath(polygon { step }, colors.edge, style = Stroke(width = hairline)) }
        Stat.entries.indices.forEach { i -> drawLine(colors.edge, center, point(i, 1f), strokeWidth = hairline) }

        previous?.let { prev ->
            drawPath(
                polygon { (prev[it] ?: 0f) / maxValue },
                colors.mist,
                style = Stroke(width = hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
            )
        }
        val current = polygon { (values[it] ?: 0f) / maxValue }
        drawPath(current, colors.system.copy(alpha = AwakenAlpha.RADAR_FILL))
        drawPath(current, colors.system, style = Stroke(width = dimens.radarStroke.toPx()))

        Stat.entries.forEachIndexed { i, stat ->
            val p = point(i, 1f + SIGIL_MARGIN * sigilPx / radius * SIGIL_PUSH)
            translate(p.x - sigilPx / 2f, p.y - sigilPx / 2f) {
                with(sigils.getValue(stat)) { draw(Size(sigilPx, sigilPx), colorFilter = ColorFilter.tint(colors.stat(stat))) }
            }
        }
    }
}

/** Default TalkBack description for stat levels. */
@Composable
fun radarDescription(values: Map<Stat, Float>): String {
    val items = ArrayList<String>(Stat.entries.size)
    for (stat in Stat.entries) items += stringResource(R.string.cd_radar_item, stat.label(), (values[stat] ?: 0f).toInt())
    return items.joinToString(", ")
}

private const val START_ANGLE = -90.0
private const val SIGIL_MARGIN = 1.2f
private const val SIGIL_PUSH = 0.6f
private const val DASH = 8f
private val GRID_STEPS = listOf(0.25f, 0.5f, 0.75f, 1f)

@ComponentPreview
@Composable
private fun RadarChartPreview() {
    AwakenTheme {
        val levels = mapOf(Stat.STR to 12f, Stat.INT to 9f, Stat.WIL to 6f, Stat.VIT to 10f, Stat.CHA to 4f)
        RadarChart(
            values = levels,
            previous = mapOf(Stat.STR to 10f, Stat.INT to 9f, Stat.WIL to 5f, Stat.VIT to 7f, Stat.CHA to 4f),
            maxValue = StatLevel.radarMax(12).toFloat(),
        )
    }
}
