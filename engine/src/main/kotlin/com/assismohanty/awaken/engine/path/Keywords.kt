package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.model.Stat

/** Whole-word, case-insensitive phrase matching shared by the two keyword tables. */
private fun phrasePattern(phrase: String): Regex =
    Regex("""(?<![\p{L}\p{N}])${Regex.escape(phrase)}(?![\p{L}\p{N}])""", setOf(RegexOption.IGNORE_CASE))

/**
 * V6 equipment check (GD §6.6.4; ENGINE_DECISIONS #60): a blueprint that names equipment the hunter doesn't own is
 * dropped. Keyed by the template equipment vocabulary, so "owned" means the same thing as in quest generation.
 */
object EquipmentKeywords {
    val TABLE: Map<String, List<String>> = mapOf(
        "dumbbells" to listOf("dumbbell", "dumbbells"),
        "kettlebell" to listOf("kettlebell", "kettlebells"),
        "pullup_bar" to listOf("pull-up bar", "pullup bar", "chin-up bar"),
        "resistance_band" to listOf("resistance band", "resistance bands", "exercise band", "exercise bands"),
        "jump_rope" to listOf("jump rope", "skipping rope", "skip rope"),
        "gym_access" to listOf("gym", "barbell", "treadmill", "squat rack", "bench press"),
        "bicycle" to listOf("bicycle", "bike", "cycling"),
    )

    private val patterns: Map<String, List<Regex>> = TABLE.mapValues { (_, words) -> words.map(::phrasePattern) }

    /** The first equipment id [text] mentions that isn't in [owned], or null. */
    fun unownedMention(text: String, owned: Set<String>): String? =
        patterns.entries.firstOrNull { (id, regexes) -> id !in owned && regexes.any { it.containsMatchIn(text) } }?.key
}

/**
 * Suggests a Path's primary stat from its goal text (GD §6.6.1: "a keyword table in :engine, not AI";
 * ENGINE_DECISIONS #71). Most keyword hits wins; ties go by [Stat] order; no hit returns null and the hunter picks.
 */
object StatSuggester {
    val TABLE: Map<Stat, List<String>> = mapOf(
        Stat.STR to listOf(
            "push-up", "push-ups", "pushup", "pushups", "pull-up", "pull-ups", "squat", "squats", "deadlift",
            "bench", "lift", "lifting", "strength", "muscle", "gym", "calisthenics",
        ),
        Stat.INT to listOf(
            "code", "coding", "program", "programming", "codeforces", "leetcode", "algorithm", "algorithms", "math",
            "exam", "study", "learn", "language", "japanese", "spanish", "read", "reading", "book", "books", "chess",
            "physics",
        ),
        Stat.WIL to listOf(
            "meditate", "meditation", "discipline", "habit", "habits", "quit", "journal", "journaling", "cold shower",
            "focus", "phone", "social media", "procrastination",
        ),
        Stat.VIT to listOf(
            "run", "running", "marathon", "5k", "10k", "walk", "walking", "steps", "sleep", "swim", "swimming",
            "cycle", "yoga", "stretch", "stretching", "mobility", "cardio",
        ),
        Stat.CHA to listOf(
            "speak", "speaking", "public speaking", "presentation", "presentations", "confidence", "social", "network",
            "networking", "friends", "conversation", "dating", "writing", "guitar", "singing",
        ),
    )

    private val patterns: Map<Stat, List<Regex>> = TABLE.mapValues { (_, words) -> words.map(::phrasePattern) }

    fun suggest(goal: String): Stat? {
        val hits = Stat.entries.associateWith { stat -> patterns.getValue(stat).count { it.containsMatchIn(goal) } }
        val best = hits.values.maxOrNull() ?: 0
        if (best == 0) return null
        return Stat.entries.first { hits.getValue(it) == best }
    }
}
