package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/** Validates `assets/gym/plans.json` and `exercise_overlay.json` against the exercise catalog (GD §18.1). */
class GymAssetsTest {
    private fun resource(path: String): String =
        requireNotNull(javaClass.classLoader.getResource(path)) { "missing test resource $path" }.readText()

    private fun text(element: kotlinx.serialization.json.JsonElement?): String? =
        if (element == null || element is JsonNull) null else element.jsonPrimitive.content

    private val rows: List<CatalogRow> = Json.parseToJsonElement(resource("gym/exercises.json")).jsonObject.getValue("exercises").jsonArray.map {
        val o = it.jsonObject
        fun list(key: String) = (o[key] as? JsonArray)?.map { v -> v.jsonPrimitive.content }.orEmpty()
        CatalogRow(
            id = o.getValue("id").jsonPrimitive.content,
            name = o.getValue("name").jsonPrimitive.content,
            category = text(o["category"]),
            equipment = text(o["equipment"]),
            level = text(o["level"]),
            mechanic = text(o["mechanic"]),
            primary = list("primary"),
            secondary = list("secondary"),
        )
    }
    private val overlay = ExerciseOverlay.parse(resource("gym/exercise_overlay.json"))
    private val exercises: Map<String, ExerciseInfo> = rows.associate { it.id to GymContent.info(it, overlay.exercises[it.id]) }
    private val library = PlanLibrary.parse(resource("gym/plans.json"))

    @Test
    fun `the catalog still holds 876 exercises`() {
        assertThat(rows).hasSize(876)
    }

    @Test
    fun `plans pass validation`() {
        val problems = PlanRules.validate(library, exercises)
        assertWithMessage(problems.joinToString("\n")).that(problems).isEmpty()
    }

    @Test
    fun `there are between 12 and 20 plans and every plan day has work`() {
        assertThat(library.plans.size).isIn(12..PlanLibrary.MAX_PLANS)
        library.plans.forEach { plan -> plan.days.forEach { assertWithMessage("${plan.id}/${it.name}").that(it.work).isNotEmpty() } }
    }

    @Test
    fun `the overlay only names catalog exercises and valid gear and regions`() {
        overlay.exercises.forEach { (id, entry) ->
            assertWithMessage(id).that(exercises).containsKey(id)
            entry.requires.forEach { assertWithMessage("$id requires $it").that(Gear.fromKey(it)).isNotNull() }
            entry.loads.forEach { name -> assertWithMessage("$id loads $name").that(InjuryRegion.entries.any { it.name == name }).isTrue() }
            entry.equipment?.let { assertWithMessage("$id equipment").that(Equipment.fromKey(it)).isNotEqualTo(Equipment.OTHER) }
        }
    }

    @Test
    fun `every plan exercise is usable with exactly the gear the plan lists`() {
        library.plans.forEach { plan ->
            val ids = plan.days.flatMap { it.warmup + it.work }.map { it.exerciseId }
            ids.forEach { id ->
                assertWithMessage("${plan.id}: $id").that(exercises.getValue(id).needs.usableWith(plan.gear)).isTrue()
            }
        }
    }

    @Test
    fun `a hunter with the full gym sees every plan unchanged`() {
        val fits = PlanRules.available(library, PlanProfile(gear = Gear.entries.toSet(), level = FitnessLevel.ADVANCED), exercises)
        assertThat(fits.map { it.plan.id }).containsExactlyElementsIn(library.plans.map { it.id })
        fits.forEach { assertWithMessage(it.plan.id).that(it.swapped + it.dropped).isEqualTo(0) }
    }

    @Test
    fun `body-only hunters get the home plans and nothing needing gear`() {
        val ids = PlanRules.available(library, PlanProfile(), exercises).map { it.plan.id }
        assertThat(ids).containsExactly("bw_home3", "core")
    }

    @Test
    fun `dumbbells and a bench unlock the dumbbell plans`() {
        val ids = PlanRules.available(library, PlanProfile(gear = setOf(Gear.DUMBBELLS, Gear.BENCH), level = FitnessLevel.INTERMEDIATE), exercises).map { it.plan.id }
        assertThat(ids).containsAtLeast("db_fb3", "db_ul4", "bw_home3", "core")
        assertThat(ids).containsNoneOf("ppl6", "ul4", "fb3")
    }

    @Test
    fun `beginners are not offered the intermediate plans`() {
        val ids = PlanRules.available(library, PlanProfile(gear = Gear.entries.toSet(), level = FitnessLevel.BEGINNER), exercises).map { it.plan.id }
        assertThat(ids).containsNoneOf("ppl6", "fb3", "db_ul4")
        assertThat(ids).containsAtLeast("ppl3", "ul4", "fb3_beg")
    }

    @Test
    fun `an injury never leaves a plan with an exercise that loads it`() {
        InjuryRegion.entries.filter { it != InjuryRegion.NO_INJURIES }.forEach { region ->
            val fits = PlanRules.available(library, PlanProfile(gear = Gear.entries.toSet(), injuries = setOf(region), level = FitnessLevel.ADVANCED), exercises)
            fits.forEach { fit ->
                (fit.days.flatMap { it.warmup + it.work }).forEach { item ->
                    assertWithMessage("${fit.plan.id} with $region: ${item.exerciseId}").that(exercises.getValue(item.exerciseId).hiddenBy(setOf(region))).isFalse()
                }
            }
        }
    }

    @Test
    fun `the clean-name overlay keeps the grip as the variant`() {
        val bench = exercises.getValue("fedb:Barbell_Bench_Press_-_Medium_Grip")
        assertThat(bench.name).isEqualTo("Bench press")
        assertThat(overlay.exercises.getValue(bench.id).variant).isEqualTo("Barbell · medium grip")
    }
}
