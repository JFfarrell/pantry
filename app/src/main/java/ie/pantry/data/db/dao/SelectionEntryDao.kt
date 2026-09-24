package ie.pantry.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ie.pantry.data.db.entity.ShoppingListSelectionEntry
import ie.pantry.data.db.relation.SelectionEntryWithRecipe
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectionEntryDao {

    /**
     * Selected recipes with their entries, ordered by recipe id. A recipe pending deletion is hidden.
     * The recipe columns are aliased `recipe_<column>` to match `SelectionEntryWithRecipe`'s embedded prefix.
     */
    @Query(
        """
        SELECT e.recipeId, e.servings,
               r.id AS recipe_id, r.title AS recipe_title, r.method AS recipe_method,
               r.yieldServings AS recipe_yieldServings, r.cookingTimeMinutes AS recipe_cookingTimeMinutes,
               r.thumbnailPath AS recipe_thumbnailPath, r.sourceUrl AS recipe_sourceUrl,
               r.fetchedAt AS recipe_fetchedAt, r.updatedAt AS recipe_updatedAt,
               r.pendingDeletionAt AS recipe_pendingDeletionAt
        FROM shopping_list_selection_entry e
        JOIN recipe r ON r.id = e.recipeId
        WHERE r.pendingDeletionAt IS NULL
        ORDER BY e.recipeId
        """,
    )
    fun observeSelection(): Flow<List<SelectionEntryWithRecipe>>

    /** Inserts the entry, or replaces the servings of the recipe's existing entry. */
    @Upsert
    suspend fun upsert(entry: ShoppingListSelectionEntry)

    /** @return rows deleted. */
    @Query("DELETE FROM shopping_list_selection_entry WHERE recipeId = :recipeId")
    suspend fun delete(recipeId: Long): Int
}
