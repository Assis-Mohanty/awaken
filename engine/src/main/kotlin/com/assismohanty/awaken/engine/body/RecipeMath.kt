package com.assismohanty.awaken.engine.body

/** One ingredient: its nutrients per 100 g and how many grams go in. */
data class RecipeIngredient(val per100: Nutrients, val grams: Double)

/** A recipe as the hunter builds it. [cookedGrams] is the weight after cooking (null = the raw total). */
data class Recipe(val ingredients: List<RecipeIngredient>, val servings: Double, val cookedGrams: Double? = null) {
    init {
        require(servings > 0.0) { "servings must be positive" }
        require(cookedGrams == null || cookedGrams > 0.0) { "cookedGrams must be positive" }
    }

    val rawGrams: Double get() = ingredients.sumOf { it.grams }

    /** Everything scaled by [factor]: more or fewer servings of the same recipe. Per-serving amounts do not change. */
    fun scaled(factor: Double): Recipe {
        require(factor > 0.0) { "factor must be positive" }
        return Recipe(ingredients.map { it.copy(grams = it.grams * factor) }, servings * factor, cookedGrams?.times(factor))
    }
}

/** Recipe aggregation (Diet v2 plan section 8). A nutrient is known only when every ingredient has it, as in the catalog builder. */
object RecipeMath {
    fun totals(recipe: Recipe): Nutrients {
        val keys = Nutrient.entries.filter { n -> recipe.ingredients.isNotEmpty() && recipe.ingredients.all { n in it.per100.values } }
        return Nutrients(keys.associateWith { n -> recipe.ingredients.sumOf { it.per100[n] * it.grams / Nutrients.PER_GRAMS } })
    }

    fun perServing(recipe: Recipe): Nutrients = totals(recipe).let { t -> Nutrients(t.values.mapValues { it.value / recipe.servings }) }

    /** Per 100 g of the finished dish (the cooked weight when set, else the raw total) for logging by grams. */
    fun per100Cooked(recipe: Recipe): Nutrients {
        val weight = recipe.cookedGrams ?: recipe.rawGrams
        require(weight > 0.0) { "a recipe needs some weight" }
        return Nutrients(totals(recipe).values.mapValues { it.value * Nutrients.PER_GRAMS / weight })
    }

    /** Grams of finished dish in one serving, or null when the cooked weight is unknown. */
    fun servingGrams(recipe: Recipe): Double? = recipe.cookedGrams?.div(recipe.servings)
}
