package com.assismohanty.awaken.engine.model

/**
 * A quest title pattern (GD §6.2). The title carries the target, so a quest reads "Walk 7,000 steps" with no separate
 * target line. Tokens:
 *  - `{n}`: the target with thousands separators ("7,000").
 *  - `{dur}`: for `sec` targets: "30 seconds", "1 minute", "2 minutes", "90 seconds" (whole minutes when divisible by 60).
 *  - `{one|many}`: the first form when the target is 1, the second otherwise ("{person|people}").
 * Exactly one of `{n}` and `{dur}` per pattern. Pure and locale-independent, so a title is identical on every phone.
 */
object TitlePattern {
    const val N = "{n}"
    const val DURATION = "{dur}"
    private val AGREEMENT = Regex("""\{([^{}|]+)\|([^{}|]+)}""")

    fun render(pattern: String, target: Int): String {
        val agreed = AGREEMENT.replace(pattern) { m -> if (target == 1) m.groupValues[1] else m.groupValues[2] }
        return agreed.replace(N, grouped(target)).replace(DURATION, duration(target))
    }

    /** True when the pattern has exactly one target token. */
    fun hasOneTargetToken(pattern: String): Boolean =
        pattern.split(N).size - 1 + pattern.split(DURATION).size - 1 == 1

    /** The pattern with every token removed: what is left must be plain words. */
    fun literalText(pattern: String): String =
        AGREEMENT.replace(pattern) { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }.replace(N, "").replace(DURATION, "")

    fun grouped(value: Int): String = value.toString().reversed().chunked(3).joinToString(",").reversed()

    private fun duration(seconds: Int): String = when {
        seconds == 60 -> "1 minute"
        seconds > 60 && seconds % 60 == 0 -> "${seconds / 60} minutes"
        seconds == 1 -> "1 second"
        else -> "$seconds seconds"
    }
}
