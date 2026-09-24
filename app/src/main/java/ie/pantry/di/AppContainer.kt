package ie.pantry.di

import android.app.Application
import ie.pantry.data.db.PantryDatabase
import ie.pantry.data.db.RecipeRepository
import ie.pantry.data.db.dao.NutritionCacheDao
import ie.pantry.data.db.dao.RecipeDao
import ie.pantry.data.db.dao.RetailerAssistDao
import ie.pantry.data.db.dao.SelectionEntryDao
import ie.pantry.data.db.dao.ShoppingListDao
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import java.time.Clock

/**
 * The app's dependencies, wired by hand: no DI framework. Construction does no database I/O (Room opens the
 * file lazily) and each property is read once, so a container holds exactly one instance of each.
 */
class AppContainer(
    val database: PantryDatabase,
    thumbnailStore: ThumbnailStore,
    thumbnailProcessor: ThumbnailProcessor,
) {
    val recipeDao: RecipeDao = database.recipeDao()
    val selectionEntryDao: SelectionEntryDao = database.selectionEntryDao()
    val shoppingListDao: ShoppingListDao = database.shoppingListDao()
    val retailerAssistDao: RetailerAssistDao = database.retailerAssistDao()
    val nutritionCacheDao: NutritionCacheDao = database.nutritionCacheDao()
    val recipeRepository: RecipeRepository =
        RecipeRepository(database, recipeDao, thumbnailStore, thumbnailProcessor)

    companion object {
        fun production(app: Application, clock: Clock = Clock.systemUTC()): AppContainer =
            AppContainer(
                database = PantryDatabase.create(app, clock),
                thumbnailStore = ThumbnailStore(app.filesDir),
                thumbnailProcessor = ThumbnailProcessor(),
            )
    }
}
