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

    private fun c(id: String, name: String, aliases: List<String> = emptyList(), pop: Int = 0, tier: Int = 0) = SearchCandidate(id, name, aliases, pop, tier)

    @Test
    fun `ranking puts favourites, then recent use, then exact, then prefix, then popularity`() {
        val list = listOf(
            c("a", "Paneer tikka", pop = 88),
            c("b", "Cheese, paneer", pop = 10),
            c("c", "Paneer", pop = 96),
            c("d", "Palak paneer", pop = 93),
            c("e", "Cottage cheese", aliases = listOf("paneer"), pop = 5),
        )

        assertThat(FoodSearch.rank(list, "paneer").map { it.id }).containsExactly("c", "e", "a", "d", "b").inOrder()
        assertThat(FoodSearch.rank(list, "paneer", SearchSignals(recentUse = mapOf("b" to 2, "d" to 5))).map { it.id }.take(2)).containsExactly("d", "b").inOrder()
        assertThat(FoodSearch.rank(list, "paneer", SearchSignals(favourites = setOf("b"), recentUse = mapOf("d" to 5))).first().id).isEqualTo("b")
    }

    @Test
    fun `ranking breaks ties by tier, then shorter name, and respects the limit`() {
        val list = listOf(c("x", "Dal makhani rich", tier = 0), c("y", "Dal makhani", tier = 2), c("z", "Dal makhani", tier = 0))

        assertThat(FoodSearch.rank(list, "dal ma").map { it.id }).containsExactly("z", "x", "y").inOrder()
        assertThat(FoodSearch.rank(list, "dal ma", limit = 2)).hasSize(2)
        assertThat(FoodSearch.rank(emptyList(), "x")).isEmpty()
    }

    @Test
    fun `ranking compares spelling variants by key`() {
        val list = listOf(c("1", "Chapati / roti", pop = 98), c("2", "Chapati sandwich", pop = 5), c("3", "Naan"))

        assertThat(FoodSearch.rank(list, "chappathi").first().id).isEqualTo("1")
    }
}
