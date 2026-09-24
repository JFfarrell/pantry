package ie.pantry.data.db

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import ie.pantry.data.db.entity.NutritionBasis
import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.db.entity.SelectionSnapshot
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * R6 AC1-AC3. Creates the v1 database from the committed `1.json`, seeds [FixtureV1], applies any
 * migrations, then reopens the file through the production builder configuration and asserts row contents.
 * (T24 switches the read-back from cursors to DAOs.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationHarnessTest {

    private val name = "migration-harness.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PantryDatabase::class.java)

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }

    /** Seeds v1, runs any migrations, and returns the production-configured database on the same file. */
    private fun migratedDatabase(): PantryDatabase {
        val v1 = helper.createDatabase(name, 1)
        FixtureV1.insert(v1)
        v1.close()
        if (PantryDatabase.VERSION > 1) {
            helper.runMigrationsAndValidate(name, PantryDatabase.VERSION, true, *ALL_MIGRATIONS).close()
        }
        return TestDatabases.onDisk(name, MutableClock())
    }

    private fun PantryDatabase.row(sql: String): Map<String, Any?> =
        openHelper.writableDatabase.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst(), "no row for: $sql")
            cursor.columnNames.associateWith { column ->
                val index = cursor.getColumnIndexOrThrow(column)
                when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                    else -> cursor.getString(index)
                }
            }
        }

    @Test
    fun `harness resolves committed schema 1 and reports no pending migration`() {
        helper.createDatabase(name, 1).close()

        assertEquals(1, PantryDatabase.VERSION, "version 1 has nothing to migrate to")
        assertTrue(ALL_MIGRATIONS.isEmpty(), "no migration is pending at version 1")
    }

    @Test
    fun `fixture rows read back field by field after migration pass`() {
        val db = migratedDatabase()

        val recipe = db.row("SELECT * FROM recipe WHERE id = 1")
        assertEquals("Fixture Stew", recipe["title"])
        assertEquals("Simmer gently.", recipe["method"])
        assertEquals(4L, recipe["yieldServings"])
        assertEquals("https://fixture.example/stew", recipe["sourceUrl"])
        assertEquals(1_700_000_000_000L, recipe["fetchedAt"])
        assertEquals(1_700_000_100_000L, recipe["updatedAt"])

        val flour = db.row("SELECT * FROM recipe_ingredient WHERE id = 1")
        assertEquals(1L, flour["recipeId"])
        assertEquals(0L, flour["position"])
        assertEquals("200 g flour", flour["rawText"])
        assertEquals("flour", flour["canonicalKey"])
        assertEquals(200.0, flour["quantityAmount"])
        assertEquals("g", flour["quantityUnit"])
        assertEquals("MASS", flour["dimension"])

        val salt = db.row("SELECT * FROM recipe_ingredient WHERE id = 2")
        assertEquals("salt to taste", salt["rawText"])
        assertEquals("UNQUANTIFIED", salt["dimension"])

        assertEquals(1L, db.row("SELECT * FROM shopping_list_selection_entry")["recipeId"])

        val list = db.row("SELECT * FROM shopping_list WHERE id = 1")
        assertEquals(1_700_000_200_000L, list["createdAt"])
        assertEquals("1:", list["sourceSelection"])

        val item = db.row("SELECT * FROM shopping_list_item WHERE id = 1")
        assertEquals(1L, item["shoppingListId"])
        assertEquals("Flour", item["displayName"])
        assertEquals(200.0, item["quantityAmount"])
        assertEquals("g", item["quantityUnit"])
        assertEquals("MASS", item["dimension"])
        assertEquals(0L, item["walkOrderIndex"])

        val session = db.row("SELECT * FROM retailer_assist_session")
        assertEquals(1L, session["shoppingListId"])
        assertEquals(0L, session["positionIndex"])
        assertEquals("", session["skippedItemIds"])

        val nutrition = db.row("SELECT * FROM nutrition_cache_entry WHERE canonicalKey = 'flour'")
        assertEquals("PER_100G", nutrition["basisUnit"])
        assertEquals(364.0, nutrition["energyKcal"])
        assertEquals("Fixture dataset", nutrition["sourceAttribution"])
        assertEquals("ODbL-1.0", nutrition["licenceTag"])
        assertEquals(1_700_000_300_000L, nutrition["fetchedAt"])
        assertEquals(0L, nutrition["estimatedConversion"])
        db.close()
    }

    @Test
    fun `absent optional fixture values read back as null`() {
        val db = migratedDatabase()

        val recipe = db.row("SELECT * FROM recipe WHERE id = 1")
        assertNull(recipe["cookingTimeMinutes"])
        assertNull(recipe["thumbnailPath"])
        assertNull(recipe["pendingDeletionAt"])

        val salt = db.row("SELECT * FROM recipe_ingredient WHERE id = 2")
        assertNull(salt["quantityAmount"])
        assertNull(salt["quantityUnit"])

        assertNull(db.row("SELECT * FROM shopping_list_selection_entry")["servings"])
        assertNull(db.row("SELECT * FROM shopping_list_item WHERE id = 1")["section"])

        val nutrition = db.row("SELECT * FROM nutrition_cache_entry WHERE canonicalKey = 'flour'")
        assertNull(nutrition["proteinG"])
        assertNull(nutrition["fatG"])
        assertNull(nutrition["carbohydrateG"])
        db.close()
    }

    @Test
    fun `fixture rows read back through DAOs after migration pass`() = runTest {
        val db = migratedDatabase()

        val recipe = assertNotNull(db.recipeDao().findRecipe(1L))
        assertEquals("Fixture Stew", recipe.recipe.title)
        assertEquals("Simmer gently.", recipe.recipe.method)
        assertEquals(4, recipe.recipe.yieldServings)
        assertNull(recipe.recipe.cookingTimeMinutes)
        assertNull(recipe.recipe.thumbnailPath)
        assertEquals("https://fixture.example/stew", recipe.recipe.sourceUrl)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), recipe.recipe.fetchedAt)
        assertEquals(Instant.ofEpochMilli(1_700_000_100_000L), recipe.recipe.updatedAt)
        assertNull(recipe.recipe.pendingDeletionAt)
        val (flour, salt) = recipe.orderedIngredients
        assertEquals("200 g flour", flour.rawText)
        assertEquals(200.0, flour.quantityAmount)
        assertEquals("g", flour.quantityUnit)
        assertEquals(QuantityDimension.MASS, flour.dimension)
        assertEquals("salt to taste", salt.rawText)
        assertNull(salt.quantityAmount)
        assertNull(salt.quantityUnit)
        assertEquals(QuantityDimension.UNQUANTIFIED, salt.dimension)

        val selection = db.selectionEntryDao().observeSelection().first().single()
        assertEquals(1L, selection.entry.recipeId)
        assertNull(selection.entry.servings)

        val list = assertNotNull(db.shoppingListDao().observeList(1L).first())
        assertEquals(Instant.ofEpochMilli(1_700_000_200_000L), list.shoppingList.createdAt)
        assertEquals(listOf(SelectionSnapshot(1L, null)), list.shoppingList.sourceSelection)
        val item = list.items.single()
        assertEquals("Flour", item.displayName)
        assertEquals(200.0, item.quantityAmount)
        assertEquals("g", item.quantityUnit)
        assertNull(item.section)

        val session = assertNotNull(db.retailerAssistDao().observeSession(1L).first())
        assertEquals(0, session.positionIndex)
        assertEquals(emptyList(), session.skippedItemIds)

        val nutrition = assertNotNull(db.nutritionCacheDao().find("flour"))
        assertEquals(NutritionBasis.PER_100G, nutrition.basisUnit)
        assertEquals(364.0, nutrition.energyKcal)
        assertNull(nutrition.proteinG)
        assertNull(nutrition.fatG)
        assertNull(nutrition.carbohydrateG)
        assertEquals("Fixture dataset", nutrition.sourceAttribution)
        assertEquals(Instant.ofEpochMilli(1_700_000_300_000L), nutrition.fetchedAt)
        assertEquals(false, nutrition.estimatedConversion)
        db.close()
    }
}
