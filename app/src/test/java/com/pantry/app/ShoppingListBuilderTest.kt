package com.pantry.app

import com.pantry.app.data.db.IngredientEntity
import com.pantry.app.data.db.RecipeEntity
import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.domain.IngredientParser
import com.pantry.app.shopping.ShoppingListBuilder
import com.pantry.app.shopping.ShoppingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListBuilderTest {

    private fun recipe(title: String, servings: Int, vararg lines: String): RecipeWithIngredients {
        val entity = RecipeEntity(title = title, servings = servings)
        val ingredients = lines.mapIndexed { index, line ->
            val parsed = IngredientParser.parse(line)
            IngredientEntity(
                recipeId = entity.id,
                position = index,
                raw = parsed.raw,
                name = parsed.name,
                matchKey = parsed.matchKey,
                amount = parsed.quantity?.amount,
                unitName = parsed.quantity?.unit?.name,
                note = parsed.note,
                optional = parsed.optional
            )
        }
        return RecipeWithIngredients(entity, ingredients)
    }

    @Test
    fun `merges the same ingredient across two recipes`() {
        val lines = ShoppingListBuilder.build(
            listOf(
                ShoppingSource(recipe("Ragu", 4, "400 g onions")),
                ShoppingSource(recipe("Soup", 4, "200 g onion"))
            )
        )
        val onions = lines.single { it.name.contains("onion", ignoreCase = true) }
        assertEquals("600 g", onions.displayQuantity)
        assertEquals(2, onions.fromRecipes.size)
    }

    @Test
    fun `converts units within the same kind before adding`() {
        val lines = ShoppingListBuilder.build(
            listOf(
                ShoppingSource(recipe("A", 2, "1 kg potatoes")),
                ShoppingSource(recipe("B", 2, "500 g potatoes"))
            )
        )
        assertEquals("1.5 kg", lines.single().displayQuantity)
    }

    @Test
    fun `incomparable units are kept apart rather than fudged`() {
        val lines = ShoppingListBuilder.build(
            listOf(
                ShoppingSource(recipe("A", 2, "400 g tomatoes")),
                ShoppingSource(recipe("B", 2, "2 tbsp tomatoes"))
            )
        )
        assertTrue(lines.single().displayQuantity.contains("+"))
    }

    @Test
    fun `scaling servings scales the quantities`() {
        val base = recipe("Curry", 2, "300 g chicken")
        val lines = ShoppingListBuilder.build(listOf(ShoppingSource(base, servings = 6)))
        assertEquals("900 g", lines.single().displayQuantity)
    }

    @Test
    fun `unquantified ingredients are listed without a made-up amount`() {
        val lines = ShoppingListBuilder.build(listOf(ShoppingSource(recipe("A", 2, "olive oil"))))
        assertEquals("as needed", lines.single().displayQuantity)
    }
}
