package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/** Validates `assets/gym/benchmarks.json` (GD §18.3): every table has a source and licence, and the numbers make sense. */
class BenchmarkAssetsTest {
    private fun resource(path: String): String =
        requireNotNull(javaClass.classLoader.getResource(path)) { "missing test resource $path" }.readText()

    private val data = BenchmarkData.parse(resource("gym/benchmarks.json"))
    private val catalogIds = Json.parseToJsonElement(resource("gym/exercises.json")).jsonObject.getValue("exercises").jsonArray
        .map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()

    @Test
    fun `squat bench and deadlift have a table for each sex with 99 rising values`() {
        listOf("squat", "bench", "deadlift").forEach { key ->
            listOf("M", "F").forEach { sex ->
                val table = data.quantiles(key, sex)
                assertWithMessage("$key $sex").that(table).isNotNull()
                assertThat(table!!.quantiles).hasSize(99)
                assertThat(table.quantiles).isInOrder()
                assertThat(table.quantiles.first()).isGreaterThan(0.0)
                assertThat(table.n).isGreaterThan(10_000)
                assertThat(table.source).contains("OpenPowerlifting")
                assertThat(table.licence).contains("Public domain")
                assertThat(table.population).isNotEmpty()
            }
        }
    }

    @Test
    fun `the tables are plausible for competitive raw lifters`() {
        // Median ratios to body weight: men squat about 2.2, bench 1.4, deadlift 2.5; women a little under 1.6, 0.9, 1.9.
        assertThat(Benchmarks.ratioAt(50.0, data.quantiles("squat", "M")!!.quantiles)).isWithin(0.3).of(2.2)
        assertThat(Benchmarks.ratioAt(50.0, data.quantiles("bench", "M")!!.quantiles)).isWithin(0.3).of(1.4)
        assertThat(Benchmarks.ratioAt(50.0, data.quantiles("deadlift", "F")!!.quantiles)).isWithin(0.3).of(1.9)
        // Men lift more per kilo than women at every level of every lift.
        listOf("squat", "bench", "deadlift").forEach { key ->
            val m = data.quantiles(key, "M")!!.quantiles
            val f = data.quantiles(key, "F")!!.quantiles
            listOf(10, 50, 90).forEach { assertThat(m[it]).isGreaterThan(f[it]) }
        }
    }

    @Test
    fun `standards have a source and licence and points never fall as the value rises`() {
        assertThat(data.standards).isNotEmpty()
        data.standards.forEach { table ->
            val at = "${table.key} ${table.sex} ${table.ageFrom}-${table.ageTo}"
            assertWithMessage(at).that(table.rows).isNotEmpty()
            assertWithMessage(at).that(table.source).contains("army.mil")
            assertWithMessage(at).that(table.licence).contains("public domain")
            table.rows.zipWithNext().forEach { (a, b) ->
                assertWithMessage(at).that(b[0]).isGreaterThan(a[0])
                assertWithMessage(at).that(b[1]).isAtLeast(a[1])
            }
            assertThat(table.rows.first()[1]).isAtLeast(60.0)
            assertThat(table.rows.last()[1]).isEqualTo(100.0)
        }
        // Every age from 17 up has exactly one table per sex and key.
        listOf("pushup", "plank").forEach { key ->
            listOf("M", "F").forEach { sex ->
                (17..70).forEach { age -> assertWithMessage("$key $sex $age").that(data.standards.count { it.key == key && it.sex == sex && age in it.ageFrom..it.ageTo }).isEqualTo(1) }
            }
        }
    }

    @Test
    fun `known Army values come through`() {
        // 22-26 men: 14 hand-release push-ups is 60 points, 61 is 100. Plank: 1:25 is 60, 3:35 is 100.
        val pushup = data.standard("pushup", "M", 24)!!
        assertThat(Benchmarks.points(14.0, pushup)).isEqualTo(60)
        assertThat(Benchmarks.points(13.0, pushup)).isEqualTo(0)
        assertThat(Benchmarks.points(61.0, pushup)).isEqualTo(100)
        val plank = data.standard("plank", "F", 24)!!
        assertThat(Benchmarks.points(85.0, plank)).isEqualTo(60)
        assertThat(Benchmarks.points(215.0, plank)).isEqualTo(100)
        // No age set uses the 22-26 band.
        assertThat(data.standard("plank", "M", null)!!.ageFrom).isEqualTo(22)
    }

    @Test
    fun `every mapped exercise exists and maps to a table that exists`() {
        data.exercises.forEach { (id, key) ->
            assertWithMessage(id).that(catalogIds).contains(id)
            val known = data.percentile.any { it.key == key } || data.standards.any { it.key == key }
            assertWithMessage("$id -> $key").that(known).isTrue()
        }
        assertThat(data.keyFor("fedb:Barbell_Squat")).isEqualTo("squat")
        assertThat(data.keyFor("fedb:Barbell_Bench_Press_-_Medium_Grip")).isEqualTo("bench")
        assertThat(data.keyFor("fedb:Barbell_Curl")).isNull()
    }
}
