package ie.pantry.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ie.pantry.data.db.PantryDatabase
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.RecipeIngredient
import ie.pantry.data.db.relation.RecipeWithIngredients
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Recipe and ingredient access.
 *
 * Every public write sets the recipe's `updatedAt` from `db.clock`, whatever value the caller passed,
 * and a child write bumps its parent in the same transaction. The `raw*` operations are protected so
 * they are reachable only through those wrappers. Any new query that reads recipe rows must keep
 * `pendingDeletionAt IS NULL`.
 */
@Dao
abstract class RecipeDao(private val db: PantryDatabase) {

    @Query("SELECT * FROM recipe WHERE pendingDeletionAt IS NULL ORDER BY title COLLATE NOCASE, id")
    abstract fun observeCatalogue(): Flow<List<Recipe>>

    /** Emits null while the recipe is pending deletion or absent. */
    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id AND pendingDeletionAt IS NULL")
    abstract fun observeRecipe(id: Long): Flow<RecipeWithIngredients?>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id AND pendingDeletionAt IS NULL")
    abstract suspend fun findRecipe(id: Long): RecipeWithIngredients?

    /** @return the new recipe id. Stamps `updatedAt` from the clock and assigns the id to each ingredient. */
    @Transaction
    open suspend fun insertRecipeWithIngredients(recipe: Recipe, ingredients: List<RecipeIngredient>): Long {
        val id = rawInsertRecipe(recipe.copy(updatedAt = db.clock.instant()))
        if (ingredients.isNotEmpty()) rawInsertIngredients(ingredients.map { it.copy(recipeId = id) })
        return id
    }

    /**
     * Writes the content fields, preserves the stored `thumbnailPath` and `pendingDeletionAt`, and bumps
     * `updatedAt`. A missing id is a no-op.
     */
    @Transaction
    open suspend fun updateRecipe(recipe: Recipe) {
        val stored = rawFind(recipe.id) ?: return
        rawUpdateRecipe(
            recipe.copy(
                thumbnailPath = stored.thumbnailPath,
                pendingDeletionAt = stored.pendingDeletionAt,
                updatedAt = db.clock.instant(),
            ),
        )
    }

    /** Bumps the parent recipe's `updatedAt`. */
    @Transaction
    open suspend fun insertIngredient(ingredient: RecipeIngredient): Long {
        val id = rawInsertIngredient(ingredient)
        bump(ingredient.recipeId, db.clock.instant())
        return id
    }

    /** Bumps the parent recipe's `updatedAt`; the ingredient keeps its stored parent. A missing id is a no-op. */
    @Transaction
    open suspend fun updateIngredient(ingredient: RecipeIngredient) {
        val parent = parentOf(ingredient.id) ?: return
        rawUpdateIngredient(ingredient.copy(recipeId = parent))
        bump(parent, db.clock.instant())
    }

    /** Bumps the parent recipe's `updatedAt`. A missing id is a no-op with no bump. */
    @Transaction
    open suspend fun deleteIngredient(ingredientId: Long) {
        val parent = parentOf(ingredientId) ?: return
        rawDeleteIngredient(ingredientId)
        bump(parent, db.clock.instant())
    }

    /** Soft delete: writes only `pendingDeletionAt`, with no `updatedAt` bump. @return rows affected. */
    @Query("UPDATE recipe SET pendingDeletionAt = :at WHERE id = :id")
    abstract suspend fun setPendingDeletion(id: Long, at: Instant): Int

    /** Restores a soft-deleted recipe: writes only `pendingDeletionAt`, with no `updatedAt` bump. */
    @Query("UPDATE recipe SET pendingDeletionAt = NULL WHERE id = :id")
    abstract suspend fun clearPendingDeletion(id: Long): Int

    /** Recovery sweep: unconditionally clears every leftover marker, with no `updatedAt` bump. @return rows cleared. */
    @Query("UPDATE recipe SET pendingDeletionAt = NULL WHERE pendingDeletionAt IS NOT NULL")
    abstract suspend fun clearAllPendingDeletions(): Int

    /** Repository-only: sets the stored thumbnail reference and bumps `updatedAt`. A missing id is a no-op. */
    @Transaction
    open suspend fun setThumbnailPath(id: Long, relativePath: String?) {
        if (setThumbnailPathRaw(id, relativePath) > 0) bump(id, db.clock.instant())
    }

    /** Repository-only: the stored thumbnail path, or null when unset or the id is missing. */
    @Query("SELECT thumbnailPath FROM recipe WHERE id = :id")
    abstract suspend fun thumbnailPathOf(id: Long): String?

    /** Repository-only hard delete; the ingredients cascade. @return rows deleted. */
    @Query("DELETE FROM recipe WHERE id = :id")
    abstract suspend fun deleteRecipeRow(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun rawInsertRecipe(recipe: Recipe): Long

    @Update
    protected abstract suspend fun rawUpdateRecipe(recipe: Recipe): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun rawInsertIngredients(ingredients: List<RecipeIngredient>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun rawInsertIngredient(ingredient: RecipeIngredient): Long

    @Update
    protected abstract suspend fun rawUpdateIngredient(ingredient: RecipeIngredient): Int

    @Query("SELECT recipeId FROM recipe_ingredient WHERE id = :id")
    protected abstract suspend fun parentOf(id: Long): Long?

    @Query("DELETE FROM recipe_ingredient WHERE id = :id")
    protected abstract suspend fun rawDeleteIngredient(id: Long): Int

    @Query("SELECT * FROM recipe WHERE id = :id")
    protected abstract suspend fun rawFind(id: Long): Recipe?

    @Query("UPDATE recipe SET thumbnailPath = :path WHERE id = :id")
    protected abstract suspend fun setThumbnailPathRaw(id: Long, path: String?): Int

    @Query("UPDATE recipe SET updatedAt = :at WHERE id = :id")
    protected abstract suspend fun bump(id: Long, at: Instant): Int
}
