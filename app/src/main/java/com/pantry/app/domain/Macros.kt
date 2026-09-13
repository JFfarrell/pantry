package com.pantry.app.domain

/** Nutrition totals. All grams except [kcal]. */
data class Macros(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fibreG: Double = 0.0,
    val sugarG: Double = 0.0,
    val saltG: Double = 0.0
) {
    operator fun plus(other: Macros) = Macros(
        kcal + other.kcal, proteinG + other.proteinG, carbsG + other.carbsG,
        fatG + other.fatG, fibreG + other.fibreG, sugarG + other.sugarG, saltG + other.saltG
    )

    operator fun times(factor: Double) = Macros(
        kcal * factor, proteinG * factor, carbsG * factor, fatG * factor,
        fibreG * factor, sugarG * factor, saltG * factor
    )

    /** Share of energy from protein / carbs / fat, normalised to sum to 1. */
    fun energySplit(): Triple<Double, Double, Double> {
        val p = proteinG * 4; val c = carbsG * 4; val f = fatG * 9
        val total = p + c + f
        return if (total <= 0.0) Triple(0.0, 0.0, 0.0)
        else Triple(p / total, c / total, f / total)
    }

    companion object {
        val ZERO = Macros()
    }
}

/** Per-100g nutrition for a single ingredient, however it was sourced. */
data class NutritionFacts(
    val per100g: Macros,
    val matchedName: String,
    val source: NutritionSource
)

enum class NutritionSource { OPEN_FOOD_FACTS, BUNDLED_TABLE, USER, NONE }

/**
 * Recipe quantities are not always masses, so converting to grams needs a
 * density (for volumes) or a typical item weight (for counts). Both tables are
 * approximate by nature; unmatched ingredients fall back to water density and
 * a generic 100 g item, and the UI flags the resulting estimate as partial.
 */
object GramConversion {

    /** g per ml. */
    private val densities = mapOf(
        "oil" to 0.92, "olive oil" to 0.92, "vegetable oil" to 0.92, "butter" to 0.91,
        "honey" to 1.42, "syrup" to 1.37, "golden syrup" to 1.43, "treacle" to 1.43,
        "milk" to 1.03, "cream" to 1.01, "double cream" to 1.01, "yoghurt" to 1.03,
        "flour" to 0.53, "plain flour" to 0.53, "self raising flour" to 0.53,
        "sugar" to 0.85, "caster sugar" to 0.80, "icing sugar" to 0.56, "brown sugar" to 0.83,
        "rice" to 0.85, "oat" to 0.41, "oats" to 0.41, "breadcrumb" to 0.35,
        "salt" to 1.20, "cocoa" to 0.45, "cornflour" to 0.62, "water" to 1.0, "stock" to 1.0
    )

    /** Typical edible weight in grams of one item. */
    private val pieceWeights = mapOf(
        "onion" to 150.0, "red onion" to 140.0, "shallot" to 40.0, "garlic clove" to 5.0,
        "garlic" to 5.0, "carrot" to 80.0, "potato" to 180.0, "sweet potato" to 200.0,
        "tomato" to 110.0, "cherry tomato" to 15.0, "pepper" to 160.0, "red pepper" to 160.0,
        "courgette" to 200.0, "aubergine" to 300.0, "cucumber" to 300.0, "celery" to 40.0,
        "leek" to 120.0, "mushroom" to 20.0, "chilli" to 15.0, "lemon" to 90.0, "lime" to 60.0,
        "orange" to 140.0, "apple" to 150.0, "banana" to 120.0, "avocado" to 150.0,
        "egg" to 55.0, "chicken breast" to 175.0, "chicken thigh" to 90.0,
        "sausage" to 60.0, "bacon" to 25.0, "slice of bread" to 38.0, "bread" to 38.0,
        "tortilla" to 60.0, "can" to 400.0, "tin" to 400.0, "pack" to 250.0,
        "bunch" to 30.0, "sprig" to 3.0, "handful" to 30.0, "pinch" to 0.4
    )

    private const val DEFAULT_DENSITY = 1.0
    private const val DEFAULT_PIECE_GRAMS = 100.0

    /**
     * Best-effort gram weight for [quantity] of [ingredientName].
     * Returns null when there is no quantity at all ("salt and pepper").
     */
    fun toGrams(quantity: Quantity?, ingredientName: String): Double? {
        if (quantity == null) return null
        val key = IngredientParser.normaliseName(ingredientName)
        return when (quantity.kind) {
            MeasureKind.MASS -> quantity.base
            MeasureKind.VOLUME -> quantity.base * lookup(densities, key, DEFAULT_DENSITY)
            MeasureKind.COUNT -> {
                val unitWeight = when (quantity.unit) {
                    MeasureUnit.CLOVE -> 5.0
                    MeasureUnit.PINCH -> 0.4
                    MeasureUnit.SLICE -> lookup(pieceWeights, key, 30.0)
                    MeasureUnit.CAN, MeasureUnit.PACK -> lookup(pieceWeights, key, 400.0)
                    MeasureUnit.BUNCH, MeasureUnit.HANDFUL -> lookup(pieceWeights, key, 30.0)
                    else -> lookup(pieceWeights, key, DEFAULT_PIECE_GRAMS)
                }
                quantity.amount * unitWeight
            }
        }
    }

    /** True when the gram figure came from a real table entry rather than a default. */
    fun isConfident(quantity: Quantity?, ingredientName: String): Boolean {
        if (quantity == null) return false
        if (quantity.kind == MeasureKind.MASS) return true
        val key = IngredientParser.normaliseName(ingredientName)
        val table: Map<String, Double> = if (quantity.kind == MeasureKind.VOLUME) densities else pieceWeights
        return TextMatch.bestMatch(table, key) != null
    }

    private fun <T> lookup(table: Map<String, T>, key: String, default: T): T =
        TextMatch.bestMatch(table, key)?.value ?: default
}
