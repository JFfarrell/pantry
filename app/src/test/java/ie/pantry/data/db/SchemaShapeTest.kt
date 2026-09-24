package ie.pantry.data.db

import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R3 AC1-AC4: the exported schema and the live database have the committed shape. */
@RunWith(RobolectricTestRunner::class)
class SchemaShapeTest {

    private lateinit var db: PantryDatabase

    @Before
    fun setUp() {
        db = TestDatabases.inMemory(MutableClock())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun rows(sql: String): List<Map<String, String?>> =
        db.openHelper.writableDatabase.query(sql).use { cursor ->
            val result = mutableListOf<Map<String, String?>>()
            while (cursor.moveToNext()) {
                result += cursor.columnNames.associateWith { name ->
                    val index = cursor.getColumnIndexOrThrow(name)
                    if (cursor.isNull(index)) null else cursor.getString(index)
                }
            }
            result
        }

    @Test
    fun `exported schema is version 1 with exactly seven tables`() {
        val json = JSONObject(File("schemas/ie.pantry.data.db.PantryDatabase/1.json").readText())
        val database = json.getJSONObject("database")
        val entities = database.getJSONArray("entities")

        val tableNames = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }

        assertEquals(1, database.getInt("version"))
        assertEquals(
            setOf(
                "recipe",
                "recipe_ingredient",
                "shopping_list_selection_entry",
                "shopping_list",
                "shopping_list_item",
                "retailer_assist_session",
                "nutrition_cache_entry",
            ),
            tableNames.toSet(),
        )
        assertEquals(7, tableNames.size)
    }

    @Test
    fun `child tables declare indexed foreign keys to their parents`() {
        val children = listOf(
            Triple("recipe_ingredient", "recipe", "recipeId"),
            Triple("shopping_list_item", "shopping_list", "shoppingListId"),
        )
        for ((child, parent, column) in children) {
            val foreignKey = rows("PRAGMA foreign_key_list($child)").single()
            assertEquals(parent, foreignKey["table"], "$child parent table")
            assertEquals(column, foreignKey["from"], "$child FK column")
            assertEquals("CASCADE", foreignKey["on_delete"], "$child delete rule")

            val indexed = rows("PRAGMA index_list($child)").any { index ->
                rows("PRAGMA index_info(${index["name"]})").firstOrNull()?.get("name") == column
            }
            assertTrue(indexed, "$child.$column must be indexed")
        }
    }

    @Test
    fun `selection and session tables declare foreign keys to their parents`() {
        val cases = listOf(
            Triple("shopping_list_selection_entry", "recipe", "recipeId"),
            Triple("retailer_assist_session", "shopping_list", "shoppingListId"),
        )
        for ((table, parent, column) in cases) {
            val foreignKey = rows("PRAGMA foreign_key_list($table)").single()
            assertEquals(parent, foreignKey["table"], "$table parent table")
            assertEquals(column, foreignKey["from"], "$table FK column")
            assertEquals("id", foreignKey["to"], "$table parent column")
            assertEquals("CASCADE", foreignKey["on_delete"], "$table delete rule")
        }
    }

    @Test
    fun `optional columns are nullable with no default`() {
        val optionalColumns = mapOf(
            "recipe" to listOf(
                "yieldServings", "cookingTimeMinutes", "thumbnailPath", "sourceUrl", "fetchedAt", "pendingDeletionAt",
            ),
            "recipe_ingredient" to listOf("quantityAmount", "quantityUnit"),
            "shopping_list_selection_entry" to listOf("servings"),
            "shopping_list_item" to listOf("quantityAmount", "quantityUnit", "section"),
            "nutrition_cache_entry" to listOf("energyKcal", "proteinG", "fatG", "carbohydrateG"),
        )
        for ((table, columns) in optionalColumns) {
            val info = rows("PRAGMA table_info($table)").associateBy { it["name"] }
            for (column in columns) {
                val row = info[column] ?: error("$table has no column $column")
                assertEquals("0", row["notnull"], "$table.$column must be nullable")
                assertNull(row["dflt_value"], "$table.$column must have no column default")
            }
        }
    }

    @Test
    fun `recipe pendingDeletionAt is nullable with no default`() {
        val column = rows("PRAGMA table_info(recipe)").single { it["name"] == "pendingDeletionAt" }

        assertEquals("0", column["notnull"])
        assertNull(column["dflt_value"])
    }

    @Test
    fun `foreign keys pragma is enabled`() {
        assertEquals("1", rows("PRAGMA foreign_keys").single()["foreign_keys"])
    }
}
