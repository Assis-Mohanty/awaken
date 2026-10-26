package com.assismohanty.awaken.core.data.catalog

import android.content.Context
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.MuscleTargets
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An exercise from the bundled catalog or the hunter's own ([custom]). */
data class Exercise(
    val id: String,
    val name: String,
    val equipment: Equipment,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
    val category: String? = null,
    val level: String? = null,
    val force: String? = null,
    val mechanic: String? = null,
    val instructions: List<String> = emptyList(),
    val custom: Boolean = false,
) {
    val targets: MuscleTargets get() = MuscleTargets(primary, secondary)
}

/** The bundled free-exercise-db catalog (Unlicense; tools/catalog/README.md). Loaded once, off the main thread. */
@Singleton
class ExerciseCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: List<Exercise>? = null

    suspend fun all(): List<Exercise> = cached ?: mutex.withLock {
        cached ?: withContext(ioDispatcher) {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            parse(text)
        }.also { cached = it }
    }

    companion object {
        const val ASSET_PATH = "gym/exercises.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): List<Exercise> = json.decodeFromString(CatalogFile.serializer(), text).exercises.map { e ->
            Exercise(
                id = e.id,
                name = e.name,
                equipment = Equipment.fromKey(e.equipment),
                primary = e.primary.mapNotNull(Muscle::fromKey).toSet(),
                secondary = e.secondary.mapNotNull(Muscle::fromKey).toSet(),
                category = e.category,
                level = e.level,
                force = e.force,
                mechanic = e.mechanic,
                instructions = e.instructions,
            )
        }
    }

    @Serializable
    private data class CatalogFile(val version: Int, val exercises: List<ExerciseRow>)

    @Serializable
    private data class ExerciseRow(
        val id: String,
        val name: String,
        val category: String? = null,
        val equipment: String? = null,
        val level: String? = null,
        val force: String? = null,
        val mechanic: String? = null,
        val primary: List<String> = emptyList(),
        val secondary: List<String> = emptyList(),
        val instructions: List<String> = emptyList(),
    )
}
