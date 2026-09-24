package ie.pantry.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Line order within the recipe, >= 0. */
    val position: Int,
    val rawText: String,
    val canonicalKey: String,
    /** Null iff [dimension] is [QuantityDimension.UNQUANTIFIED]. */
    val quantityAmount: Double?,
    /** Null iff [dimension] is [QuantityDimension.UNQUANTIFIED]. */
    val quantityUnit: String?,
    val dimension: QuantityDimension,
)
