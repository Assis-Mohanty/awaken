package com.assismohanty.awaken.engine.path

/**
 * V2 string cleaning (GD §6.6.4; ENGINE_DECISIONS #59): remove URLs, strip control characters, collapse whitespace,
 * trim, then truncate by Unicode code points so an emoji or a CJK character is never cut in half.
 */
object PathText {
    const val TITLE_MAX = 48
    const val DESCRIPTION_MAX = 280
    const val RATIONALE_MAX = 140
    const val SUMMARY_MAX = 200
    const val EXIT_CRITERION_MAX = 140

    private val url = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val control = Regex("""\p{Cc}|\p{Cf}""")
    private val whitespace = Regex("""\s+""")

    fun clean(raw: String, max: Int): String {
        val collapsed = raw
            .replace(url, " ")
            .replace(control, " ")
            .replace(whitespace, " ")
            .trim()
        return truncate(collapsed, max).trim()
    }

    private fun truncate(text: String, max: Int): String {
        if (text.codePointCount(0, text.length) <= max) return text
        return text.substring(0, text.offsetByCodePoints(0, max))
    }

    /**
     * V7 key (ENGINE_DECISIONS #61): lowercase, Unicode letters and digits only, so "Graph Hunt!" and "graph-hunt"
     * collide and non-Latin titles still compare.
     */
    fun duplicateKey(title: String): String =
        buildString { title.lowercase().codePoints().filter { Character.isLetterOrDigit(it) }.forEach { appendCodePoint(it) } }
}
