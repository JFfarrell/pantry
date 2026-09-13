package com.pantry.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MealSlot { BREAKFAST, LUNCH, DINNER }

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val imageUrl: String? = null,
    val servings: Int = 2,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    /** Wall-clock total; may exceed prep + cook when a recipe includes resting or proving. */
    val totalMinutes: Int? = null,
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val cuisine: String? = null,
    val notes: String? = null,
    val favourite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** What the recipe list and detail header show for feature 8. */
    val displayMinutes: Int?
        get() = totalMinutes ?: listOfNotNull(prepMinutes, cookMinutes).takeIf { it.isNotEmpty() }?.sum()
}

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recipeId"), Index("matchKey")]
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: String,
    val position: Int,
    val raw: String,
    val name: String,
    val matchKey: String,
    val amount: Double? = null,
    val unitName: String? = null,
    val note: String? = null,
    val optional: Boolean = false
)

@Entity(
    tableName = "meal_plan",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recipeId"), Index(value = ["epochDay", "slot"])]
)
data class MealPlanEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val slot: MealSlot,
    val recipeId: String,
    val servings: Int = 2
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [ForeignKey(
        entity = ShoppingListEntity::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("listId")]
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: String,
    val name: String,
    val displayQuantity: String,
    val aisle: String,
    /** Recipe titles this line came from, for the "why is this on my list" hint. */
    val fromRecipes: String = "",
    val checked: Boolean = false,
    val addedManually: Boolean = false,
    val position: Int = 0
)

@Entity(tableName = "nutrition_cache")
data class NutritionCacheEntity(
    @PrimaryKey val matchKey: String,
    val matchedName: String,
    val source: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fibreG: Double,
    val sugarG: Double,
    val saltG: Double,
    val fetchedAt: Long = System.currentTimeMillis()
)
