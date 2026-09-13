package com.pantry.app.repo

import com.pantry.app.data.db.IngredientEntity
import com.pantry.app.data.db.RecipeDao
import com.pantry.app.data.db.RecipeEntity
import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.domain.IngredientParser
import com.pantry.app.importer.ImportedRecipe
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RecipeRepository(private val dao: RecipeDao) {

    fun observeAll(): Flow<List<RecipeWithIngredients>> = dao.observeAll()

    fun observeById(id: String): Flow<RecipeWithIngredients?> = dao.observeById(id)

    suspend fun getByIds(ids: List<String>): List<RecipeWithIngredients> =
        if (ids.isEmpty()) emptyList() else dao.getByIds(ids)

    suspend fun existingIdFor(sourceUrl: String): String? = dao.findBySourceUrl(sourceUrl)?.id

    /** Saves an imported recipe, replacing an earlier import of the same URL. */
    suspend fun save(imported: ImportedRecipe, existingId: String? = null): String {
        val recipe = RecipeEntity(
            id = existingId ?: UUID.randomUUID().toString(),
            title = imported.title.ifBlank { "Untitled recipe" },
            description = imported.description,
            sourceUrl = imported.sourceUrl,
            sourceName = imported.sourceName,
            imageUrl = imported.imageUrl,
            servings = imported.servings ?: 2,
            prepMinutes = imported.prepMinutes,
            cookMinutes = imported.cookMinutes,
            totalMinutes = imported.totalMinutes,
            steps = imported.steps,
            tags = imported.tags,
            cuisine = imported.cuisine
        )
        dao.upsert(recipe, imported.ingredientLines.toEntities(recipe.id))
        return recipe.id
    }

    suspend fun saveEdited(recipe: RecipeEntity, ingredientLines: List<String>) {
        dao.upsert(recipe, ingredientLines.toEntities(recipe.id))
    }

    suspend fun setFavourite(recipe: RecipeEntity, favourite: Boolean) =
        dao.updateRecipe(recipe.copy(favourite = favourite))

    suspend fun setServings(recipe: RecipeEntity, servings: Int) =
        dao.updateRecipe(recipe.copy(servings = servings.coerceIn(1, 50)))

    suspend fun delete(id: String) = dao.deleteRecipe(id)

    private fun List<String>.toEntities(recipeId: String): List<IngredientEntity> =
        mapIndexedNotNull { index, line ->
            val parsed = IngredientParser.parse(line)
            if (parsed.name.isBlank()) return@mapIndexedNotNull null
            IngredientEntity(
                recipeId = recipeId,
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
}
