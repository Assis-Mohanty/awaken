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
}
