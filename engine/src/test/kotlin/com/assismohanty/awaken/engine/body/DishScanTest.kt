package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.body.DishScan.Guess
import com.assismohanty.awaken.engine.body.DishScan.Suggestions
import com.assismohanty.awaken.engine.body.DishScan.TestPhoto
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DishScanTest {
    private val labels = mapOf(
        "dal" to listOf("in:dal_tadka", "in:dal_fry"),
        "rice" to listOf("in:rice_plain"),
        "roti" to listOf("in:roti"),
        "idli" to emptyList(),
    )

    @Test
    fun bestLabelsFirstFlattenedToThreeDistinctFoods() {
        val s = DishScan.suggest(listOf(Guess("rice", 0.2), Guess("dal", 0.6), Guess("roti", 0.1)), labels)
        assertThat((s as Suggestions.Foods).foodIds).containsExactly("in:dal_tadka", "in:dal_fry", "in:rice_plain").inOrder()
    }

    @Test
    fun underTheFloorOrNothingMappedIsUnsure() {
        assertThat(DishScan.suggest(listOf(Guess("dal", 0.14)), labels)).isEqualTo(Suggestions.Unsure)
        assertThat(DishScan.suggest(listOf(Guess("dal", 0.15)), labels)).isInstanceOf(Suggestions.Foods::class.java)
        assertThat(DishScan.suggest(listOf(Guess("idli", 0.9), Guess("pizza", 0.5)), labels)).isEqualTo(Suggestions.Unsure)
        assertThat(DishScan.suggest(emptyList(), labels)).isEqualTo(Suggestions.Unsure)
    }

    @Test
    fun aLabelWithNoFoodsIsSkippedNotCounted() {
        val s = DishScan.suggest(listOf(Guess("idli", 0.9), Guess("roti", 0.05)), labels)
        assertThat((s as Suggestions.Foods).foodIds).containsExactly("in:roti")
    }

    private fun photo(expected: String, label: String, score: Double = 0.8, ms: Long = 100) = TestPhoto(expected, listOf(Guess(label, score)), ms)

    @Test
    fun aModelThatMeetsEveryLimitShips() {
        // 7 of 10 in-set hits (70 %), 2 out-of-set photos of which none get guesses: 7 of 12 overall (58 %).
        val photos = List(7) { photo("in:roti", "roti") } + List(3) { photo("in:roti", "rice") } +
            List(2) { photo("in:pizza", "rice", score = 0.1) }
        val v = DishScan.judge(photos, labels)
        assertThat(v.inSet).isEqualTo(10)
        assertThat(v.hitRateInSet).isWithin(1e-9).of(0.7)
        assertThat(v.confidentOutOfSet).isEqualTo(0.0)
        assertThat(v.ships).isTrue()
    }

    @Test
    fun eachLimitAloneStopsIt() {
        val good = List(10) { photo("in:roti", "roti") }
        assertThat(DishScan.judge(good, labels).ships).isTrue()
        assertThat(DishScan.judge(List(6) { photo("in:roti", "roti") } + List(4) { photo("in:roti", "rice") }, labels).ships).isFalse()
        assertThat(DishScan.judge(good.map { it.copy(millis = 300) }, labels).ships).isFalse()
        assertThat(DishScan.judge(good + photo("in:roti", "roti", ms = 1_000), labels).ships).isFalse()
        // 4 confident guesses on 10 photos of foods outside the label set (40 %).
        val outside = List(4) { photo("in:pizza", "rice") } + List(6) { photo("in:pizza", "rice", score = 0.1) }
        assertThat(DishScan.judge(List(30) { photo("in:roti", "roti") } + outside, labels).ships).isFalse()
    }

    @Test
    fun noPhotosNeverShips() {
        val v = DishScan.judge(emptyList(), labels)
        assertThat(v.ships).isFalse()
        assertThat(v.medianMillis).isNull()
    }
}
