package ie.pantry.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** The recipe chosen for the next shopping list: one entry per recipe. */
@Entity(
    tableName = "shopping_list_selection_entry",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ShoppingListSelectionEntry(
    @PrimaryKey val recipeId: Long,
    /** Absent means no stated yield and no user value; the shopping-list merge applies 1x. */
    val servings: Int?,
)
