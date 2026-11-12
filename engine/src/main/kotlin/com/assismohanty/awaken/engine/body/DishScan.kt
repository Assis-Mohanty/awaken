package com.assismohanty.awaken.engine.body

/**
 * The photo scanner's rules (GD 20.6, docs/diet/PLAN.md section 5). The model only names dishes and scores them; a dish
 * label points at foods in our database, and every number on the diary comes from those foods, never from the model. Pure.
 */
object DishScan {
    /** Below this top score the scanner says it is not sure and shows no guesses. */
    const val SCORE_FLOOR = 0.15
    const val MAX_SUGGESTIONS = 3

    /** A model output: one dish label and its score (0 to 1). */
    data class Guess(val label: String, val score: Double)

    sealed interface Suggestions {
        /** Up to [MAX_SUGGESTIONS] food ids, best first, each from a label the model scored. */
        data class Foods(val foodIds: List<String>) : Suggestions
        data object Unsure : Suggestions
    }

    /**
     * The best labels first, each label's foods in the order `labels.json` lists them, duplicates dropped, at most three.
     * A label with no foods in [labelFoods] is skipped. Unsure when the best score is under the floor or nothing maps.
     */
    fun suggest(guesses: List<Guess>, labelFoods: Map<String, List<String>>): Suggestions {
        val ranked = guesses.sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return Suggestions.Unsure
        if (best.score < SCORE_FLOOR) return Suggestions.Unsure
        val ids = ranked.flatMap { labelFoods[it.label].orEmpty() }.distinct().take(MAX_SUGGESTIONS)
        return if (ids.isEmpty()) Suggestions.Unsure else Suggestions.Foods(ids)
    }

    /** The go / no-go test (GD 20.6): the model ships only if every one of these holds over the 200 test photos. */
    object Ship {
        const val MIN_HIT_RATE_IN_SET = 0.70
        const val MIN_HIT_RATE_OVERALL = 0.55
        const val MEDIAN_UNDER_MILLIS = 300L
        const val SLOWEST_UNDER_MILLIS = 1_000L
        const val MAX_CONFIDENT_OUT_OF_SET = 0.30
    }

    /** One test photo: the food it really shows, the model's guesses, and how long the model took. */
    data class TestPhoto(val expectedFoodId: String, val guesses: List<Guess>, val millis: Long)

    data class Verdict(
        val photos: Int,
        val inSet: Int,
        val hitRateInSet: Double?,
        val hitRateOverall: Double?,
        val medianMillis: Long?,
        val slowestMillis: Long?,
        /** Of the photos whose food no label points at, the share that still got guesses. */
        val confidentOutOfSet: Double?,
        val ships: Boolean,
    )

    /** A hit is the right food among the (at most three) suggestions the hunter would see. */
    fun judge(photos: List<TestPhoto>, labelFoods: Map<String, List<String>>): Verdict {
        val known = labelFoods.values.flatten().toSet()
        fun hit(p: TestPhoto) = (suggest(p.guesses, labelFoods) as? Suggestions.Foods)?.foodIds?.contains(p.expectedFoodId) == true
        fun confident(p: TestPhoto) = suggest(p.guesses, labelFoods) is Suggestions.Foods
        fun rate(list: List<TestPhoto>, test: (TestPhoto) -> Boolean) = if (list.isEmpty()) null else list.count(test).toDouble() / list.size

        val inSet = photos.filter { it.expectedFoodId in known }
        val outOfSet = photos.filter { it.expectedFoodId !in known }
        val times = photos.map { it.millis }.sorted()
        val median = if (times.isEmpty()) null else if (times.size % 2 == 1) times[times.size / 2] else (times[times.size / 2 - 1] + times[times.size / 2]) / 2
        val inSetRate = rate(inSet, ::hit)
        val overall = rate(photos, ::hit)
        val confidentOut = rate(outOfSet, ::confident)
        val ships = inSetRate != null && inSetRate >= Ship.MIN_HIT_RATE_IN_SET &&
            overall != null && overall >= Ship.MIN_HIT_RATE_OVERALL &&
            median != null && median < Ship.MEDIAN_UNDER_MILLIS &&
            times.last() < Ship.SLOWEST_UNDER_MILLIS &&
            (confidentOut ?: 0.0) <= Ship.MAX_CONFIDENT_OUT_OF_SET
        return Verdict(photos.size, inSet.size, inSetRate, overall, median, times.lastOrNull(), confidentOut, ships)
    }
}
