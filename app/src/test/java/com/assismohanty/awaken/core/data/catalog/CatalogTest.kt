package com.assismohanty.awaken.core.data.catalog

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled catalogs parse, are complete enough to use, and search finds everyday foods and lifts. */
@RunWith(AndroidJUnit4::class)
class CatalogTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val foods = FoodCatalog(context, Dispatchers.Unconfined)
    private val exercises = ExerciseCatalog(context, Dispatchers.Unconfined)

    @Test
    fun theFoodCatalogLoadsWithEnergyOnEveryFood() = runTest {
        assertThat(foods.size()).isGreaterThan(10_000)
        val paneer = foods.byId("usda:2705740")
        assertThat(paneer?.name).isEqualTo("Cheese, paneer")
        assertThat(paneer?.per100?.get(Nutrient.KCAL)).isGreaterThan(0.0)
    }

    @Test
    fun searchFindsEverydayFoodsFirst() = runTest {
        assertThat(foods.search("paneer").map { it.name }).contains("Cheese, paneer")
        assertThat(foods.search("roti").map { it.name }).contains("Bread, chapati or roti, plain, commercially prepared")
        assertThat(foods.search("idli").first().name).isEqualTo("Idli")
        assertThat(foods.search("boiled eggs").map { it.name }).contains("Egg, whole, boiled or poached")
        assertThat(foods.search("   ")).isEmpty()
    }

    @Test
    fun theExerciseCatalogLoadsWithMuscles() = runTest {
        val all = exercises.all()

        assertThat(all.size).isGreaterThan(800)
        assertThat(all.count { it.primary.isEmpty() }).isEqualTo(0)
        val bench = all.first { it.name == "Barbell Bench Press - Medium Grip" }
        assertThat(bench.equipment).isEqualTo(Equipment.BARBELL)
        assertThat(bench.primary).containsExactly(Muscle.CHEST)
        assertThat(bench.secondary).containsAtLeast(Muscle.SHOULDERS, Muscle.TRICEPS)
        assertThat(bench.instructions).isNotEmpty()
    }

    @Test
    fun rankingPrefersTheStartOfTheNameThenShorterNames() {
        val names = listOf("Palak Paneer with rice and extra cream", "Paneer tikka", "Cheese, paneer")
        val normalized = names.map(CatalogSearch::normalize)

        assertThat(CatalogSearch.search(names, normalized, "paneer", 10))
            .containsExactly("Paneer tikka", "Cheese, paneer", "Palak Paneer with rice and extra cream").inOrder()
    }

    @Test
    fun snapshotJsonRoundTrips() {
        val nutrients = Nutrients(mapOf(Nutrient.KCAL to 250.0, Nutrient.SODIUM to 12.5))
        val portions = listOf(Portion("1 bowl", 180.0))

        assertThat(FoodCatalog.nutrientsFromJson(FoodCatalog.nutrientsToJson(nutrients))).isEqualTo(nutrients)
        assertThat(FoodCatalog.portionsFromJson(FoodCatalog.portionsToJson(portions))).isEqualTo(portions)
        assertThat(FoodCatalog.nutrientsFromJson("not json")).isEqualTo(Nutrients())
    }
}
