package com.assismohanty.awaken.engine.body

import java.text.Normalizer

/**
 * The search key of a food name or a typed query (Diet v2 plan §3.5): spelling variants of one word share a key, so
 * "chapathi", "chappati" and "Chapati" all find the same food. `tools/catalog/build-catalog.mjs` repeats these rules
 * to write the keys into the bundled catalog; `CatalogTest` proves both agree.
 */
object FoodSearch {
    private val combining = Regex("[\\u0300-\\u036f]")
    private val nonWord = Regex("[^a-z0-9\\u0900-\\u097f]+")
    private val aspirated = Regex("([bcdgjkpt])h")
    private val doubled = Regex("(.)\\1+")

    /** Space-separated token keys; Devanagari letters pass through unchanged. */
    fun key(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(combining, "").replace(nonWord, " ").trim()
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ") { tokenKey(it) }

    private fun tokenKey(token: String): String {
        val singular = if (token.length > 3 && token.endsWith('s') && !token.endsWith("ss")) token.dropLast(1) else token
        return singular.replace("ph", "f").replace("w", "v").replace("aa", "a").replace("ee", "i").replace("oo", "u")
            .replace(aspirated, "\$1").replace("ah", "a").replace(doubled, "\$1")
    }

    /** Orders already-matched candidates for [query]. [limit] cuts the list after ordering. */
    fun rank(candidates: List<SearchCandidate>, query: String, signals: SearchSignals = SearchSignals(), limit: Int = Int.MAX_VALUE): List<SearchCandidate> {
        val q = key(query)
        val keyed = candidates.map { c -> Keyed(c, key(c.name), c.aliases.map(::key)) }
        return keyed.sortedWith(
            compareByDescending<Keyed> { it.candidate.id in signals.favourites }
                .thenByDescending { signals.recentUse[it.candidate.id] ?: 0 }
                .thenByDescending { q.isNotEmpty() && (it.nameKey == q || q in it.aliasKeys) }
                .thenByDescending { q.isNotEmpty() && it.nameKey.startsWith(q) }
                .thenByDescending { it.candidate.popularity }
                .thenBy { it.candidate.tierRank }
                .thenBy { it.nameKey.length }
                .thenBy { it.nameKey }
                .thenBy { it.candidate.id },
        ).take(limit).map { it.candidate }
    }

    private class Keyed(val candidate: SearchCandidate, val nameKey: String, val aliasKeys: List<String>)
}

/** One search hit before ordering. [tierRank]: 0 curated, 1 derived, 2 estimated. */
data class SearchCandidate(val id: String, val name: String, val aliases: List<String> = emptyList(), val popularity: Int = 0, val tierRank: Int = 0)

/** What the hunter does with foods: favourites, and how often each was logged lately. */
data class SearchSignals(val favourites: Set<String> = emptySet(), val recentUse: Map<String, Int> = emptyMap())
