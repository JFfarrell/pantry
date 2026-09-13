package com.pantry.app.nutrition

import com.pantry.app.data.db.IngredientEntity
import com.pantry.app.data.db.NutritionCacheEntity
import com.pantry.app.data.db.NutritionDao
import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.domain.GramConversion
import com.pantry.app.domain.Macros
import com.pantry.app.domain.MeasureUnit
import com.pantry.app.domain.NutritionFacts
import com.pantry.app.domain.NutritionSource
import com.pantry.app.domain.Quantity
import java.util.concurrent.TimeUnit

/** Nutrition for one ingredient line of a recipe. */
data class IngredientMacros(
    val ingredient: IngredientEntity,
    val grams: Double?,
    val facts: NutritionFacts?,
    val macros: Macros?,
    /** False when the gram weight came from a default rather than a real conversion. */
    val confidentWeight: Boolean
)

/** Feature 5: the full macro picture for a recipe. */
data class RecipeMacros(
    val perRecipe: Macros,
    val perServing: Macros,
    val servings: Int,
    val lines: List<IngredientMacros>
) {
    val covered: Int get() = lines.count { it.macros != null }
    val total: Int get() = lines.count { !it.ingredient.optional }
    /** 0..1 share of ingredients that contributed real numbers. */
    val coverage: Float get() = if (lines.isEmpty()) 0f else covered.toFloat() / lines.size
    val isEstimate: Boolean get() = coverage < 1f || lines.any { !it.confidentWeight && it.macros != null }

    companion object {
        val EMPTY = RecipeMacros(Macros.ZERO, Macros.ZERO, 1, emptyList())
    }
}

class NutritionRepository(
    private val dao: NutritionDao,
    private val staples: StaplesTable,
    private val remote: OpenFoodFactsClient
) {

    private val negativeCacheTtl = TimeUnit.DAYS.toMillis(14)

    /**
     * Bundled table first, network second. The local table is authoritative for
     * raw ingredients, which is most of a recipe; Open Food Facts fills in
     * branded and processed items it does not cover.
     */
    suspend fun factsFor(matchKey: String, displayName: String): NutritionFacts? {
        dao.get(matchKey)?.let { cached ->
            val source = runCatching { NutritionSource.valueOf(cached.source) }.getOrNull() ?: NutritionSource.NONE
            if (source != NutritionSource.NONE) return cached.toFacts(source)
            if (System.currentTimeMillis() - cached.fetchedAt < negativeCacheTtl) return null
        }

        staples.lookup(matchKey)?.let { facts ->
            dao.put(facts.toCache(matchKey))
            return facts
        }

        val remoteFacts = remote.search(displayName.ifBlank { matchKey })
        if (remoteFacts != null) {
            dao.put(remoteFacts.toCache(matchKey))
            return remoteFacts
        }

        // Remember the miss so every recipe view does not re-query the network.
        dao.put(
            NutritionCacheEntity(
                matchKey = matchKey, matchedName = displayName, source = NutritionSource.NONE.name,
                kcal = 0.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0, fibreG = 0.0, sugarG = 0.0, saltG = 0.0
            )
        )
        return null
    }

    /** Lets the user correct a bad lookup; the override is sticky. */
    suspend fun setManualFacts(matchKey: String, displayName: String, per100g: Macros) {
        dao.put(
            NutritionFacts(per100g, displayName, NutritionSource.USER).toCache(matchKey)
        )
    }

    suspend fun compute(recipe: RecipeWithIngredients, servingsOverride: Int? = null): RecipeMacros {
        val lines = recipe.ingredients.sortedBy { it.position }.map { ingredient ->
            val quantity = ingredient.toQuantity()
            val grams = GramConversion.toGrams(quantity, ingredient.name)
            val facts = factsFor(ingredient.matchKey, ingredient.name)
            IngredientMacros(
                ingredient = ingredient,
                grams = grams,
                facts = facts,
                macros = if (grams != null && facts != null) facts.per100g * (grams / 100.0) else null,
                confidentWeight = GramConversion.isConfident(quantity, ingredient.name)
            )
        }

        val perRecipe = lines.mapNotNull { it.macros }.fold(Macros.ZERO) { acc, m -> acc + m }
        val servings = (servingsOverride ?: recipe.recipe.servings).coerceAtLeast(1)
        return RecipeMacros(perRecipe, perRecipe * (1.0 / servings), servings, lines)
    }

    private fun NutritionCacheEntity.toFacts(source: NutritionSource) = NutritionFacts(
        per100g = Macros(kcal, proteinG, carbsG, fatG, fibreG, sugarG, saltG),
        matchedName = matchedName,
        source = source
    )

    private fun NutritionFacts.toCache(matchKey: String) = NutritionCacheEntity(
        matchKey = matchKey,
        matchedName = matchedName,
        source = source.name,
        kcal = per100g.kcal,
        proteinG = per100g.proteinG,
        carbsG = per100g.carbsG,
        fatG = per100g.fatG,
        fibreG = per100g.fibreG,
        sugarG = per100g.sugarG,
        saltG = per100g.saltG
    )
}

fun IngredientEntity.toQuantity(): Quantity? {
    val a = amount ?: return null
    val unit = unitName?.let { runCatching { MeasureUnit.valueOf(it) }.getOrNull() } ?: MeasureUnit.PIECE
    return Quantity(a, unit)
}
