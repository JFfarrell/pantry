package com.pantry.app.shopping

import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.data.db.ShoppingItemEntity
import com.pantry.app.domain.MeasureKind
import com.pantry.app.domain.Quantity
import com.pantry.app.nutrition.toQuantity

/** A recipe plus how many servings of it you actually want to shop for. */
data class ShoppingSource(
    val recipe: RecipeWithIngredients,
    val servings: Int = recipe.recipe.servings
) {
    /** Scale applied to every quantity: 6 servings of a 4-serving recipe is 1.5x. */
    val scale: Double get() = servings.toDouble() / recipe.recipe.servings.coerceAtLeast(1)
}

data class AggregatedLine(
    val name: String,
    val displayQuantity: String,
    val aisle: String,
    val fromRecipes: List<String>
)

/**
 * Features 3 and 4: turn one recipe -- or a whole week of them -- into a single
 * shopping list, merging duplicate ingredients and adding their quantities.
 *
 * Quantities only merge when they are physically comparable. 500 g of tomatoes
 * plus 2 tbsp of tomato puree stays as two figures rather than being forced
 * into one wrong number.
 */
object ShoppingListBuilder {

    fun build(sources: List<ShoppingSource>): List<AggregatedLine> {
        data class Line(val name: String, val quantity: Quantity?, val recipeTitle: String)

        val flattened = sources.flatMap { source ->
            source.recipe.ingredients
                .filterNot { it.optional }
                .map { ingredient ->
                    val scaled = ingredient.toQuantity()?.let { q ->
                        Quantity(q.amount * source.scale, q.unit)
                    }
                    Triple(ingredient.matchKey, Line(ingredient.name, scaled, source.recipe.recipe.title), ingredient.position)
                }
        }

        return flattened
            .groupBy { it.first }
            .map { (_, entries) ->
                // Prefer the shortest name in the group: "tomatoes" over
                // "ripe vine tomatoes, halved".
                val name = entries.map { it.second.name }.minByOrNull { it.length } ?: entries.first().second.name
                val quantities = entries.mapNotNull { it.second.quantity }

                val buckets = mutableListOf<Quantity>()
                quantities.forEach { q ->
                    val idx = buckets.indexOfFirst { it.canCombineWith(q) }
                    if (idx >= 0) buckets[idx] = buckets[idx] + q else buckets += q
                }

                val display = when {
                    buckets.isEmpty() -> "as needed"
                    else -> buckets
                        .sortedBy { kindOrder(it) }
                        .joinToString(" + ") { it.humanised().toString() }
                }

                AggregatedLine(
                    name = name,
                    displayQuantity = display,
                    aisle = Aisles.classify(name),
                    fromRecipes = entries.map { it.second.recipeTitle }.distinct()
                )
            }
            .sortedWith(compareBy({ Aisles.walkingOrder(it.aisle) }, { it.name.lowercase() }))
    }

    fun toEntities(listId: String, lines: List<AggregatedLine>): List<ShoppingItemEntity> =
        lines.mapIndexed { index, line ->
            ShoppingItemEntity(
                listId = listId,
                name = line.name,
                displayQuantity = line.displayQuantity,
                aisle = line.aisle,
                fromRecipes = line.fromRecipes.joinToString(", "),
                position = index
            )
        }

    private fun kindOrder(q: Quantity) = when (q.kind) {
        MeasureKind.MASS -> 0
        MeasureKind.VOLUME -> 1
        MeasureKind.COUNT -> 2
    }
}
