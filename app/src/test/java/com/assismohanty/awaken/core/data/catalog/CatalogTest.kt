package com.assismohanty.awaken.core.data.catalog

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.catalog.db.CatalogDatabase
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.FoodSearch
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled catalogs open, are complete enough to use, and search finds everyday foods and lifts. */
@RunWith(AndroidJUnit4::class)
class CatalogTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val foods = FoodCatalog(context, Dispatchers.Unconfined)
    private val exercises = ExerciseCatalog(context, Dispatchers.Unconfined)

    @Test
    fun theFoodCatalogOpensWithEnergyOnEveryFood() = runTest {
        assertThat(foods.size()).isGreaterThan(16_000)
        val paneer = foods.byId("usda:2705740")
        assertThat(paneer?.name).isEqualTo("Cheese, paneer")
        assertThat(paneer?.per100?.get(Nutrient.KCAL)).isGreaterThan(0.0)
    }

    @Test
    fun everySourceNamesItsLicenceAndAttribution() = runTest {
        val sources = foods.sources()

        assertThat(sources.map { it.id }).containsExactly("awaken", "cofid", "usda_fndds", "usda_sr")
        assertThat(sources.filter { it.licence.isBlank() || it.attribution.isBlank() }).isEmpty()
        assertThat(sources.first { it.id == "cofid" }.licence).contains("Open Government Licence v3.0")
    }

    @Test
    fun searchFindsEverydayFoodsFirst() = runTest {
        assertThat(foods.search("paneer").first().name).isEqualTo("Paneer")
        assertThat(foods.search("paneer").map { it.name }).contains("Cheese, paneer")
        assertThat(foods.search("roti").first().name).isEqualTo("Chapati / roti")
        assertThat(foods.search("idli").first().name).isEqualTo("Idli")
        assertThat(foods.search("boiled eggs").map { it.name }).containsAtLeast("Boiled egg", "Egg, whole, boiled or poached")
        assertThat(foods.search("   ")).isEmpty()
    }

    @Test
    fun spellingVariantsAndHindiFindTheSameFood() = runTest {
        val chapati = foods.search("chapathi").first().name
        assertThat(foods.search("Chappati").first().name).isEqualTo(chapati)
        assertThat(foods.search("dahl").first().name).isEqualTo("Dal tadka")
        assertThat(foods.search("दाल").first().name).isEqualTo("Dal tadka")
        assertThat(foods.search("biriyani").map { it.name }).contains("Chicken biryani")
    }

    @Test
    fun indianFoodsCarryHouseholdPortions() = runTest {
        val dal = foods.search("dal tadka").first()
        assertThat(dal.source).isEqualTo(FoodSource.COFID)
        assertThat(dal.portions.first()).isEqualTo(Portion("1 katori", 150.0))
        val butterChicken = foods.byId("in:butter-chicken")
        assertThat(butterChicken?.source).isEqualTo(FoodSource.AWAKEN)
        assertThat(butterChicken?.per100?.get(Nutrient.PROTEIN)).isGreaterThan(5.0)
    }

    @Test
    fun theSearchKeysInTheDatabaseMatchTheEngineRules() {
        val db = Room.databaseBuilder(context, CatalogDatabase::class.java, "key-check.db").createFromAsset(CatalogDatabase.ASSET_PATH).build()
        try {
            db.openHelper.readableDatabase.query("SELECT name, aliases, keys FROM food_fts").use { cursor ->
                var rows = 0
                while (cursor.moveToNext()) {
                    val expected = FoodSearch.key(cursor.getString(0) + " " + cursor.getString(1).replace('|', ' ')).split(' ').toSet()
                    val actual = cursor.getString(2).split(' ').toSet()
                    assertThat(actual).isEqualTo(expected)
                    rows++
                }
                assertThat(rows).isGreaterThan(16_000)
            }
        } finally {
            db.close()
        }
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
