package com.assismohanty.awaken.core.data.catalog

/**
 * Offline name search for the bundled catalogs. Every query word must appear in the name; results rank by how well the
 * name starts: whole query at the start, then the first word starting a name word, then every word starting a name word,
 * then anywhere. Ties go to the shorter name, so "Cheese, paneer" beats a 9-word recipe.
 */
object CatalogSearch {
    private val nonWord = Regex("[^a-z0-9]+")

    fun normalize(text: String): String = text.lowercase().replace(nonWord, " ").trim()

    /** Query words, with a plural "s" dropped so "eggs" finds "Egg, whole". */
    fun tokens(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }.map { if (it.length > 3 && it.endsWith('s')) it.dropLast(1) else it }

    /** Rank of [normalizedName] for [tokens]; null when it doesn't match. Lower is better. */
    fun score(normalizedName: String, tokens: List<String>): Int? {
        if (tokens.isEmpty()) return null
        if (tokens.any { it !in normalizedName }) return null
        val words = normalizedName.split(' ')
        return when {
            normalizedName.startsWith(tokens.joinToString(" ")) -> 0
            words.firstOrNull()?.startsWith(tokens.first()) == true -> 1
            tokens.all { token -> words.any { it.startsWith(token) } } -> 2
            else -> 3
        }
    }

    /** Best [limit] matches of [query] over [items], whose normalized names are [normalizedNames] (same order). */
    fun <T> search(items: List<T>, normalizedNames: List<String>, query: String, limit: Int): List<T> {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return emptyList()
        return items.indices
            .mapNotNull { i -> score(normalizedNames[i], tokens)?.let { Triple(i, it, normalizedNames[i].length) } }
            .sortedWith(compareBy({ it.second }, { it.third }, { normalizedNames[it.first] }))
            .take(limit)
            .map { items[it.first] }
    }
}
