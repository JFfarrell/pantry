package ie.pantry.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The oldest-version migration fixture: one row per table, written straight into the raw v1 schema with
 * some optionals deliberately absent. Every future version keeps this fixture and asserts it still reads
 * back, adding a `FixtureVn` only if it needs new columns seeded.
 */
object FixtureV1 {

    fun insert(db: SupportSQLiteDatabase) {
        insert(db, "recipe") {
            put("id", 1L)
            put("title", "Fixture Stew")
            put("method", "Simmer gently.")
            put("yieldServings", 4)
            // cookingTimeMinutes, thumbnailPath, pendingDeletionAt: absent
            put("sourceUrl", "https://fixture.example/stew")
            put("fetchedAt", 1_700_000_000_000L)
            put("updatedAt", 1_700_000_100_000L)
        }
        insert(db, "recipe_ingredient") {
            put("id", 1L)
            put("recipeId", 1L)
            put("position", 0)
            put("rawText", "200 g flour")
            put("canonicalKey", "flour")
            put("quantityAmount", 200.0)
            put("quantityUnit", "g")
            put("dimension", "MASS")
        }
        insert(db, "recipe_ingredient") {
            put("id", 2L)
            put("recipeId", 1L)
            put("position", 1)
            put("rawText", "salt to taste")
            put("canonicalKey", "salt")
            // quantityAmount, quantityUnit: absent (UNQUANTIFIED)
            put("dimension", "UNQUANTIFIED")
        }
        insert(db, "shopping_list_selection_entry") {
            put("recipeId", 1L)
            // servings: absent
        }
        insert(db, "shopping_list") {
            put("id", 1L)
            put("createdAt", 1_700_000_200_000L)
            put("sourceSelection", "1:")
        }
        insert(db, "shopping_list_item") {
            put("id", 1L)
            put("shoppingListId", 1L)
            put("canonicalKey", "flour")
            put("displayName", "Flour")
            put("quantityAmount", 200.0)
            put("quantityUnit", "g")
            put("dimension", "MASS")
            // section: absent
            put("walkOrderIndex", 0)
        }
        insert(db, "retailer_assist_session") {
            put("shoppingListId", 1L)
            put("positionIndex", 0)
            put("skippedItemIds", "")
        }
        insert(db, "nutrition_cache_entry") {
            put("canonicalKey", "flour")
            put("basisUnit", "PER_100G")
            put("energyKcal", 364.0)
            // proteinG, fatG, carbohydrateG: absent
            put("sourceAttribution", "Fixture dataset")
            put("licenceTag", "ODbL-1.0")
            put("fetchedAt", 1_700_000_300_000L)
            put("estimatedConversion", 0)
        }
    }

    private fun insert(db: SupportSQLiteDatabase, table: String, values: ContentValues.() -> Unit) {
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply(values))
    }
}
