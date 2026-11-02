package com.assismohanty.awaken.reelcounter.core.lumi

/**
 * How tired Lumi looks: [stage] 1 (fully charged) to 6 (switched off), and how far through that stage today's count
 * is ([progressInStage], 0 up to but never reaching 1; always 0 at stage 6). The UI dims Lumi slightly with
 * [progressInStage] so it fades a little with every reel, not only at the stage boundaries.
 */
data class LumiState(val stage: Int, val progressInStage: Float) {
    init {
        require(stage in LumiRules.FIRST_STAGE..LumiRules.LAST_STAGE) { "stage $stage is not in 1..6" }
        require(progressInStage in 0f..1f) { "progressInStage $progressInStage is not in 0..1" }
    }

    companion object {
        /** Lumi at the start of the day. */
        val Rested = LumiState(LumiRules.FIRST_STAGE, 0f)
    }
}

/** Every number behind Lumi's stages, in one place (GAME_DESIGN §15, PROPOSED until approved). */
object LumiRules {
    const val FIRST_STAGE = 1
    const val LAST_STAGE = 6

    /**
     * PROPOSED: without a daily limit, the reel count at which each stage starts (stage 1 at 0 … stage 6 at 120 and over).
     * Index 0 is stage 1.
     */
    val NO_LIMIT_THRESHOLDS: List<Int> = listOf(0, 10, 25, 50, 80, 120)

    /** With a limit, stages 1..5 split 0 until the limit into this many equal parts; stage 6 starts at the limit. */
    const val EVEN_PARTS = 5
}

/**
 * Lumi's stage for [reelsToday], the total across every app today (it starts again from 0 with the day).
 * With a [dailyLimit], stages 1..5 spread evenly from 0 to the limit and stage 6 is at or over it. Without one
 * (null, or a limit that is not positive), [LumiRules.NO_LIMIT_THRESHOLDS] apply. Pure, so a limit changed
 * mid-day simply re-evaluates the same count.
 */
fun lumiStage(reelsToday: Int, dailyLimit: Int?): LumiState {
    val reels = reelsToday.coerceAtLeast(0)
    return if (dailyLimit != null && dailyLimit > 0) withLimit(reels, dailyLimit) else withoutLimit(reels)
}

private fun withLimit(reels: Int, limit: Int): LumiState {
    if (reels >= limit) return LumiState(LumiRules.LAST_STAGE, 0f)
    // Long maths so a count near Int.MAX_VALUE cannot overflow; reels < limit keeps every stage in 1..5.
    val scaled = reels.toLong() * LumiRules.EVEN_PARTS
    val index = (scaled / limit).toInt()
    val intoStage = scaled - index.toLong() * limit
    return LumiState(LumiRules.FIRST_STAGE + index, (intoStage.toDouble() / limit).toFloat().coerceIn(0f, 1f))
}

private fun withoutLimit(reels: Int): LumiState {
    val starts = LumiRules.NO_LIMIT_THRESHOLDS
    val index = starts.indexOfLast { reels >= it }.coerceAtLeast(0)
    if (index == starts.lastIndex) return LumiState(LumiRules.LAST_STAGE, 0f)
    val span = starts[index + 1] - starts[index]
    return LumiState(LumiRules.FIRST_STAGE + index, ((reels - starts[index]).toFloat() / span).coerceIn(0f, 1f))
}

/**
 * The one daily limit Lumi follows: limits are set per app, so it is their sum over the apps that have one, or null
 * when no app has a limit.
 */
fun lumiDailyLimit(perAppLimits: Collection<Int>): Int? {
    val set = perAppLimits.filter { it > 0 }
    return if (set.isEmpty()) null else set.sumOf { it.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
