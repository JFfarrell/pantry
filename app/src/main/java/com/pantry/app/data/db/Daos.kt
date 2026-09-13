package com.pantry.app.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>
)

data class PlannedMeal(
    @Embedded val entry: MealPlanEntryEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: RecipeEntity
)

@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY favourite DESC, createdAt DESC")
    fun observeAll(): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: String): Flow<RecipeWithIngredients?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RecipeWithIngredients>

    @Query("SELECT * FROM recipes WHERE sourceUrl = :url LIMIT 1")
    suspend fun findBySourceUrl(url: String): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsFor(recipeId: String)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: String)

    @Transaction
    suspend fun upsert(recipe: RecipeEntity, ingredients: List<IngredientEntity>) {
        insertRecipe(recipe)
        deleteIngredientsFor(recipe.id)
        insertIngredients(ingredients.map { it.copy(id = 0, recipeId = recipe.id) })
    }
}

@Dao
interface MealPlanDao {

    @Transaction
    @Query("SELECT * FROM meal_plan WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, slot")
    fun observeRange(from: Long, to: Long): Flow<List<PlannedMeal>>

    @Transaction
    @Query("SELECT * FROM meal_plan WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, slot")
    suspend fun getRange(from: Long, to: Long): List<PlannedMeal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MealPlanEntryEntity): Long

    @Query("DELETE FROM meal_plan WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM meal_plan WHERE epochDay BETWEEN :from AND :to")
    suspend fun clearRange(from: Long, to: Long)

    @Query("UPDATE meal_plan SET servings = :servings WHERE id = :id")
    suspend fun setServings(id: Long, servings: Int)
}

@Dao
interface ShoppingDao {

    @Query("SELECT * FROM shopping_lists ORDER BY createdAt DESC")
    fun observeLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestList(): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY aisle, position")
    fun observeItems(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND checked = 0 ORDER BY position")
    suspend fun getUncheckedItems(listId: String): List<ShoppingItemEntity>

    @Query("UPDATE shopping_items SET checked = 1 WHERE listId = :listId AND name IN (:names)")
    suspend fun checkOff(listId: String, names: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ShoppingListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: String)
}

@Dao
interface NutritionDao {

    @Query("SELECT * FROM nutrition_cache WHERE matchKey IN (:keys)")
    suspend fun getAll(keys: List<String>): List<NutritionCacheEntity>

    @Query("SELECT * FROM nutrition_cache WHERE matchKey = :key")
    suspend fun get(key: String): NutritionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: NutritionCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(entries: List<NutritionCacheEntity>)
}
