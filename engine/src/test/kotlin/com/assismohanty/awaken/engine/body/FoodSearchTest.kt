package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** GD §20 vector 214 and the folding rules the catalog script repeats. */
class FoodSearchTest {
    @Test
    fun `spelling variants of chapati share one key`() {
        val keys = listOf("chapathi", "chappati", "chapatti", "Chapati").map(FoodSearch::key).toSet()
        assertThat(keys).containsExactly("capati")
    }

    @Test
    fun `common Indian spellings fold together`() {
        assertThat(FoodSearch.key("moong dhal")).isEqualTo(FoodSearch.key("Mung Dal"))
        assertThat(FoodSearch.key("Dahl")).isEqualTo(FoodSearch.key("dal"))
        assertThat(FoodSearch.key("paneer tikka masala")).isEqualTo("panir tika masala")
    }

    @Test
    fun `plurals, punctuation and accents are dropped, Devanagari passes through`() {
        assertThat(FoodSearch.key("Eggs, boiled (café)")).isEqualTo("eg boiled cafe")
        assertThat(FoodSearch.key("glass")).isEqualTo("glas")
        assertThat(FoodSearch.key("दाल तड़का")).isEqualTo(java.text.Normalizer.normalize("दाल तड़का", java.text.Normalizer.Form.NFD))
        assertThat(FoodSearch.key("  ")).isEmpty()
    }
}
