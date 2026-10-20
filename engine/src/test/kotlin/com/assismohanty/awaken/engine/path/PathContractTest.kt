package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.path.PathFixtures.path
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/** Every fixture and every request the engine builds is checked against the /contracts JSON Schemas. */
class PathContractTest {

    private val names = listOf(
        "path_plan.request.schema.json",
        "path_plan.response.schema.json",
        "path_week.request.schema.json",
        "path_week.response.schema.json",
    )
    private val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) { builder ->
        builder.schemas(names.associate { BASE + it to resource("/contracts/$it") })
    }

    private fun schema(name: String): Schema = registry.getSchema(SchemaLocation.of(BASE + name))

    private fun errors(schemaName: String, json: String) = schema(schemaName).validate(json, InputFormat.JSON)

    /**
     * Fixtures that break the contract on purpose: they prove the engine validator copes with output the proxy would
     * already have rejected (defence in depth, ARCH §3.8 step 9).
     */
    private val outsideContract = setOf("v19_unknown_unit.json", "plan_too_short.json")

    @Test
    fun `every fixture matches its response schema unless it is deliberately outside the contract`() {
        val fixtures = PathFixtures.all()
        assertThat(fixtures).containsAtLeastElementsIn(outsideContract)
        fixtures.forEach { name ->
            val schemaName = if (name.startsWith("plan_")) "path_plan.response.schema.json" else "path_week.response.schema.json"
            val found = errors(schemaName, PathFixtures.text(name))
            if (name in outsideContract) {
                assertWithMessage(name).that(found).isNotEmpty()
            } else {
                assertWithMessage("$name: $found").that(found).isEmpty()
            }
        }
    }

    @Test
    fun `requests built by PathWire match the request schemas`() {
        val json = Json { encodeDefaults = true }
        val constraints = PathWire.constraints(setOf("pullup_bar", "dumbbells"), setOf("impact"))
        val plan = PathWire.planRequest(REQUEST_ID, path, constraints)
        assertThat(errors("path_plan.request.schema.json", json.encodeToString(PathPlanRequest.serializer(), plan))).isEmpty()

        val week = PathWire.weekRequest(
            requestId = REQUEST_ID,
            path = path.copy(secondaryStat = null, notes = "x".repeat(400)),
            milestone = Milestone(0, "Foundations", "Lock in BFS.", "Solve 10 problems."),
            signal = ProgressionSignal.HOLD,
            lastWeek = List(20) { WeekOutcome("A very long quest title that the model wrote once upon a time", SlotOutcome.COMPLETED, Feedback.RIGHT) },
            constraints = constraints,
        )
        assertThat(week.lastWeek).hasSize(PathWire.LAST_WEEK_MAX)
        assertThat(errors("path_week.request.schema.json", json.encodeToString(PathWeekRequest.serializer(), week))).isEmpty()
    }

    @Test
    fun `the request schema rejects out-of-range user input`() {
        val tooLong = """{"requestId":"$REQUEST_ID","path":{"goal":"${"g".repeat(121)}","currentLevel":"","notes":"",
            "primaryStat":"INT","activeDays":["MON"],"questsPerDay":3,"weeklyMinutes":20},
            "constraints":{"equipment":[],"injuryTags":[]}}"""
        assertThat(errors("path_plan.request.schema.json", tooLong).map { it.keyword })
            .containsAtLeast("maxLength", "maximum", "minimum")
    }

    @Test
    fun `the week response schema rejects unknown enums, extra fields and HEALTH verification`() {
        val bad = """{"promptVersion":"week_quests.v1","suggestAdvance":false,"blueprints":[{"title":"T","description":"D",
            "stat":"LUCK","unit":"KM","target":1,"effort":"NORMAL","estMinutes":5,"mode":"HEALTH_STEPS","tags":["gym"],
            "rationale":"","xp":9999}]}"""
        assertThat(errors("path_week.response.schema.json", bad)).hasSize(4)
    }

    private fun resource(path: String): String = requireNotNull(javaClass.getResource(path)) { "missing $path" }.readText()

    private companion object {
        const val BASE = "https://awaken.app/contracts/"
        const val REQUEST_ID = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b"
    }
}
