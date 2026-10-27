package com.assismohanty.awaken.core.designsystem.body

import android.graphics.Region
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import com.assismohanty.awaken.engine.body.Muscle

/** Which side of the figure is shown. */
enum class BodySide { FRONT, BACK }

/**
 * The parsed body map (DESIGN_SYSTEM §3, Gym): [BodyPaths] turned into Compose paths once, in the source's 724 × 1448
 * viewport. Each tappable region maps to one or more [Muscle]s; regions that cover two catalog groups (upper back = lats
 * and middle back, glutes = glutes and abductors) show the higher of the two loads and report both.
 */
internal object BodyGeometry {

    class MuscleRegion(val muscles: List<Muscle>, val path: Path, val bounds: Rect, private val hit: Region) {
        fun contains(x: Float, y: Float): Boolean = hit.contains(x.toInt(), y.toInt())
    }

    /** One side: its silhouette outline, non-tappable base parts, muscle regions, and the bounds all of them cover. */
    class Figure(val outline: Path, val base: List<Path>, val regions: List<MuscleRegion>) {
        val bounds: Rect = (base.map { it.getBounds() } + regions.map { it.bounds } + outline.getBounds())
            .reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
    }

    val front: Figure by lazy { build(BodyPaths.FRONT_OUTLINE, BodyPaths.front) }
    val back: Figure by lazy { build(BodyPaths.BACK_OUTLINE, BodyPaths.back) }

    fun figure(side: BodySide): Figure = if (side == BodySide.FRONT) front else back

    /** The catalog muscles that share a region with [muscle] (itself included), for the sheet. */
    fun regionMuscles(muscle: Muscle): List<Muscle> = SLUG_MUSCLES.values.firstOrNull { muscle in it } ?: listOf(muscle)

    /** Where each muscle can be found, so a picker can switch to the side that shows it. */
    fun sideOf(muscle: Muscle): BodySide = if (front.regions.any { muscle in it.muscles }) BodySide.FRONT else BodySide.BACK

    private val SLUG_MUSCLES: Map<String, List<Muscle>> = mapOf(
        "chest" to listOf(Muscle.CHEST),
        "abs" to listOf(Muscle.ABDOMINALS),
        "obliques" to listOf(Muscle.ABDOMINALS),
        "biceps" to listOf(Muscle.BICEPS),
        "triceps" to listOf(Muscle.TRICEPS),
        "forearm" to listOf(Muscle.FOREARMS),
        "deltoids" to listOf(Muscle.SHOULDERS),
        "trapezius" to listOf(Muscle.TRAPS),
        "neck" to listOf(Muscle.NECK),
        "upper-back" to listOf(Muscle.LATS, Muscle.MIDDLE_BACK),
        "lower-back" to listOf(Muscle.LOWER_BACK),
        "gluteal" to listOf(Muscle.GLUTES, Muscle.ABDUCTORS),
        "adductors" to listOf(Muscle.ADDUCTORS),
        "quadriceps" to listOf(Muscle.QUADRICEPS),
        "hamstring" to listOf(Muscle.HAMSTRINGS),
        "calves" to listOf(Muscle.CALVES),
        "tibialis" to listOf(Muscle.CALVES),
    )

    /** Drawn as part of the silhouette, never tappable. Hair is left out: no face, no hairstyle. */
    private val BASE_SLUGS = setOf("head", "hands", "feet", "ankles", "knees")

    private fun build(outline: String, regions: List<BodyPaths.Region>): Figure {
        val clip = Region(0, 0, BodyPaths.WIDTH.toInt(), BodyPaths.HEIGHT.toInt())
        val base = regions.filter { it.slug in BASE_SLUGS }.map { parse(it.paths) }
        val muscles = regions.mapNotNull { region ->
            val owners = SLUG_MUSCLES[region.slug] ?: return@mapNotNull null
            val path = parse(region.paths)
            val hit = Region().apply { setPath(path.asAndroidPath(), clip) }
            MuscleRegion(owners, path, path.getBounds(), hit)
        }
        return Figure(parse(listOf(outline)), base, muscles)
    }

    private fun parse(data: List<String>): Path = Path().apply {
        data.forEach { addPath(PathParser().parsePathString(it).toPath()) }
    }
}
