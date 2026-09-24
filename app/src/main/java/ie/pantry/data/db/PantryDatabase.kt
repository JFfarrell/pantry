package ie.pantry.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ie.pantry.data.db.dao.NutritionCacheDao
import ie.pantry.data.db.dao.RecipeDao
import ie.pantry.data.db.dao.RetailerAssistDao
import ie.pantry.data.db.dao.SelectionEntryDao
import ie.pantry.data.db.dao.ShoppingListDao
import ie.pantry.data.db.entity.NutritionCacheEntry
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.RecipeIngredient
import ie.pantry.data.db.entity.RetailerAssistSession
import ie.pantry.data.db.entity.ShoppingList
import ie.pantry.data.db.entity.ShoppingListItem
import ie.pantry.data.db.entity.ShoppingListSelectionEntry
import java.time.Clock

@Database(
    entities = [
        Recipe::class,
        RecipeIngredient::class,
        ShoppingListSelectionEntry::class,
        ShoppingList::class,
        ShoppingListItem::class,
        RetailerAssistSession::class,
        NutritionCacheEntry::class,
    ],
    version = PantryDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PantryDatabase : RoomDatabase() {

    /** The time source DAO writes stamp `updatedAt` from. Always initialised by the factories below. */
    lateinit var clock: Clock
        private set

    abstract fun recipeDao(): RecipeDao

    abstract fun selectionEntryDao(): SelectionEntryDao

    abstract fun shoppingListDao(): ShoppingListDao

    abstract fun retailerAssistDao(): RetailerAssistDao

    abstract fun nutritionCacheDao(): NutritionCacheDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "pantry.db"

        /** On-disk database. A missing migration or a downgrade throws on first access and never wipes data. */
        fun create(context: Context, clock: Clock): PantryDatabase = createAt(context, FILE_NAME, clock)

        /** On-disk database in a named file; the seam for tests that need more than one database file. */
        internal fun createAt(context: Context, fileName: String, clock: Clock): PantryDatabase =
            Room.databaseBuilder(context.applicationContext, PantryDatabase::class.java, fileName)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                .also { it.clock = clock }

        /** In-memory database for Robolectric and container tests. */
        fun createInMemory(context: Context, clock: Clock): PantryDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, PantryDatabase::class.java)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                .also { it.clock = clock }
    }
}
