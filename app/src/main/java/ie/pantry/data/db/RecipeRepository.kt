package ie.pantry.data.db

import android.database.sqlite.SQLiteException
import android.util.Log
import ie.pantry.data.db.dao.RecipeDao
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.RecipeIngredient
import ie.pantry.data.thumbnail.NoImageReason
import ie.pantry.data.thumbnail.ThumbnailOutcome
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LOG_TAG = "PantryThumb"

/**
 * Composes the recipe rows and the thumbnail files. A thumbnail failure never prevents a save; a row
 * failure never leaves an orphaned thumbnail file.
 */
class RecipeRepository(
    private val db: PantryDatabase,
    private val recipeDao: RecipeDao,
    private val store: ThumbnailStore,
    private val processor: ThumbnailProcessor,
) {
    data class SaveResult(val recipeId: Long, val thumbnail: ThumbnailOutcome)

    /**
     * Saves a recipe with its ingredients and, if [thumbnailBytes] is non-null, a thumbnail processed through
     * the single decode/downsample/re-encode path (source-agnostic: an import fetch or the photo picker).
     * `thumbnailPath` comes from the outcome, overriding any caller value, and `pendingDeletionAt` is forced null.
     * @throws PersistenceException if the row write fails; any thumbnail file already written is deleted first.
     */
    suspend fun saveNewRecipe(
        recipe: Recipe,
        ingredients: List<RecipeIngredient>,
        thumbnailBytes: ByteArray?,
    ): SaveResult {
        val outcome = storeThumbnail(thumbnailBytes)
        val relativePath = (outcome as? ThumbnailOutcome.Stored)?.relativePath
        try {
            val id = recipeDao.insertRecipeWithIngredients(
                recipe.copy(thumbnailPath = relativePath, pendingDeletionAt = null),
                ingredients,
            )
            return SaveResult(id, outcome)
        } catch (failure: SQLiteException) {
            relativePath?.let(::deleteQuietly)
            throw persistenceFailure(SAVE_NEW_RECIPE, failure)
        } catch (failure: IllegalStateException) {
            relativePath?.let(::deleteQuietly)
            throw persistenceFailure(SAVE_NEW_RECIPE, failure)
        }
    }

    /**
     * Writes a new file, points the recipe row at it (bumping `updatedAt`), then deletes the old file.
     * On a decode or write failure the existing thumbnail is left unchanged and the `NoImage` outcome is returned.
     * [recipeId] must name an existing recipe.
     * @throws PersistenceException if the row write fails; the new file is deleted first.
     */
    suspend fun replaceThumbnail(recipeId: Long, thumbnailBytes: ByteArray): ThumbnailOutcome {
        val outcome = storeThumbnail(thumbnailBytes)
        val newPath = (outcome as? ThumbnailOutcome.Stored)?.relativePath ?: return outcome
        val oldPath = try {
            val previous = recipeDao.thumbnailPathOf(recipeId)
            recipeDao.setThumbnailPath(recipeId, newPath)
            previous
        } catch (failure: SQLiteException) {
            deleteQuietly(newPath)
            throw persistenceFailure(REPLACE_THUMBNAIL, failure)
        } catch (failure: IllegalStateException) {
            deleteQuietly(newPath)
            throw persistenceFailure(REPLACE_THUMBNAIL, failure)
        }
        oldPath?.let(::deleteQuietly)
        return outcome
    }

    /**
     * Clears the thumbnail reference (an explicit no-image, bumping `updatedAt`) and then deletes the file.
     * @throws PersistenceException if the row write fails; the file is left in place.
     */
    suspend fun removeThumbnail(recipeId: Long) {
        val oldPath = persisting(REMOVE_THUMBNAIL) {
            val previous = recipeDao.thumbnailPathOf(recipeId)
            recipeDao.setThumbnailPath(recipeId, null)
            previous
        }
        oldPath?.let(::deleteQuietly)
    }

    /**
     * Hard delete: the row (its ingredients and selection entry cascade), then its thumbnail file.
     * @throws PersistenceException if the row delete fails; the file is left in place.
     */
    suspend fun deleteRecipe(recipeId: Long) {
        val oldPath = persisting(DELETE_RECIPE) {
            val previous = recipeDao.thumbnailPathOf(recipeId)
            recipeDao.deleteRecipeRow(recipeId)
            previous
        }
        oldPath?.let(::deleteQuietly)
    }

    /** Runs [block], converting a database failure into a content-free [PersistenceException]. */
    private inline fun <T> persisting(operation: String, block: () -> T): T =
        try {
            block()
        } catch (failure: SQLiteException) {
            throw persistenceFailure(operation, failure)
        } catch (failure: IllegalStateException) {
            throw persistenceFailure(operation, failure)
        }

    /** Processes and stores the bytes off the main thread. Every failure becomes a [ThumbnailOutcome.NoImage]. */
    private suspend fun storeThumbnail(bytes: ByteArray?): ThumbnailOutcome {
        if (bytes == null) return ThumbnailOutcome.NoImage(NoImageReason.NO_SOURCE)
        return withContext(Dispatchers.IO) {
            val jpeg = processor.process(bytes)
                ?: return@withContext ThumbnailOutcome.NoImage(NoImageReason.DECODE_FAILED)
            try {
                ThumbnailOutcome.Stored(store.write(jpeg))
            } catch (_: IOException) {
                Log.w(LOG_TAG, "category=IO operation=$SAVE_NEW_RECIPE reason=write_failed")
                ThumbnailOutcome.NoImage(NoImageReason.WRITE_FAILED)
            }
        }
    }

    /** Deletes a file this repository wrote. A failed delete is logged by category only and never thrown. */
    private fun deleteQuietly(relativePath: String) {
        if (!store.delete(relativePath)) {
            Log.w(LOG_TAG, "category=IO operation=deleteThumbnail reason=delete_failed")
        }
    }

    private companion object {
        const val SAVE_NEW_RECIPE = "saveNewRecipe"
        const val REPLACE_THUMBNAIL = "replaceThumbnail"
        const val REMOVE_THUMBNAIL = "removeThumbnail"
        const val DELETE_RECIPE = "deleteRecipe"
    }
}
