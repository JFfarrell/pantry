package ie.pantry.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.RecipeIngredient

data class RecipeWithIngredients(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<RecipeIngredient>,
) {
    /** Room's `@Relation` cannot order its collection, so ingredients are sorted by `position` here. */
    val orderedIngredients: List<RecipeIngredient>
        get() = ingredients.sortedBy { it.position }
}
