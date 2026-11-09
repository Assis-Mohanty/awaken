package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the overlay says about one exercise on top of the catalog. Every field is optional. */
@Serializable
data class OverlayEntry(
    /** A cleaner display name ("Bench press"); the catalog's name is kept as [variant] when this is set and [variant] is not. */
    val clean: String? = null,
    val variant: String? = null,
    /** Gear the catalog does not mention (a pull-up bar for a "body only" pull-up). Values are [Gear.key]. */
    val requires: List<String> = emptyList(),
    /** Replaces the catalog's equipment (the catalog has none for about 80 bodyweight moves). A catalog key such as `body only`. */
    val equipment: String? = null,
    /** Regions this exercise loads beyond what its muscles say. Values are [InjuryRegion] names. */
    val loads: List<String> = emptyList(),
    /** `time` for a hold (plank); reps otherwise. */
    val measure: String? = null,
)

@Serializable
data class ExerciseOverlay(val version: Int = 1, val exercises: Map<String, OverlayEntry> = emptyMap()) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ExerciseOverlay = json.decodeFromString(serializer(), text)
    }
}

/** One catalog row as the engine reads it (the same fields as `exercises.json`). */
data class CatalogRow(
    val id: String,
    val name: String,
    val category: String?,
    val equipment: String?,
    val level: String?,
    val mechanic: String?,
    val primary: List<String>,
    val secondary: List<String>,
)

object GymContent {
    /** Joins a catalog row with its overlay entry. Unknown gear or region names in the overlay are ignored. */
    fun info(row: CatalogRow, overlay: OverlayEntry?): ExerciseInfo = ExerciseInfo(
        id = row.id,
        name = overlay?.clean ?: row.name,
        equipment = Equipment.fromKey(overlay?.equipment ?: row.equipment),
        primary = row.primary.mapNotNull(Muscle::fromKey).toSet(),
        secondary = row.secondary.mapNotNull(Muscle::fromKey).toSet(),
        level = ExerciseLevel.fromKey(row.level),
        mechanic = row.mechanic,
        category = row.category,
        requires = overlay?.requires.orEmpty().mapNotNull(Gear::fromKey).toSet(),
        loads = overlay?.loads.orEmpty().mapNotNull { name -> InjuryRegion.entries.firstOrNull { it.name == name } }.toSet(),
        variant = overlay?.variant,
    )
}
