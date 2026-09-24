package ie.pantry.data.db.relation

import androidx.room.Embedded
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.ShoppingListSelectionEntry

/** JOIN projection: a selection entry with its recipe, whose columns are prefixed `recipe_`. */
data class SelectionEntryWithRecipe(
    @Embedded val entry: ShoppingListSelectionEntry,
    @Embedded(prefix = "recipe_") val recipe: Recipe,
)
