package ie.pantry.data.db

import ie.pantry.data.db.dao.RecipeDao
import ie.pantry.data.db.dao.SelectionEntryDao
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.ShoppingListSelectionEntry
import ie.pantry.testutil.FlowRecorder
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R4: `observeSelection` emits after every write, hides entries whose recipe is pending deletion. */
@RunWith(RobolectricTestRunner::class)
class SelectionEntryDaoTest {

    private val clock = MutableClock()
    private lateinit var db: PantryDatabase
    private lateinit var recipes: RecipeDao
    private lateinit var selection: SelectionEntryDao

    @Before
    fun setUp() {
        db = TestDatabases.inMemory(clock)
        recipes = db.recipeDao()
        selection = db.selectionEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedRecipe(title: String): Long = recipes.insertRecipeWithIngredients(
        Recipe(
            title = title,
            method = "Method",
            yieldServings = null,
            cookingTimeMinutes = null,
            thumbnailPath = null,
            sourceUrl = null,
            fetchedAt = null,
        ),
        emptyList(),
    )

    private fun entryCount(): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM shopping_list_selection_entry").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun `selection flow emits empty list when no entries`() = runTest {
        val recorder = FlowRecorder(selection.observeSelection(), backgroundScope)

        assertEquals(emptyList(), recorder.awaitNext())
    }

    @Test
    fun `selection flow emits after insert`() = runTest {
        val soup = seedRecipe("Soup")
        val stew = seedRecipe("Stew")
        val recorder = FlowRecorder(selection.observeSelection(), backgroundScope)
        assertEquals(emptyList(), recorder.awaitNext())

        selection.upsert(ShoppingListSelectionEntry(soup, servings = null))
        selection.upsert(ShoppingListSelectionEntry(stew, servings = 3))

        val both = recorder.awaitUntil { it.size == 2 }
        assertEquals(listOf(soup, stew), both.map { it.entry.recipeId })
        assertEquals(listOf("Soup", "Stew"), both.map { it.recipe.title })
        // An absent servings value must stay absent, never become a default (R3 AC3).
        assertNull(both[0].entry.servings)
        assertEquals(3, both[1].entry.servings)
    }

    @Test
    fun `selection flow emits after update`() = runTest {
        val id = seedRecipe("Soup")
        selection.upsert(ShoppingListSelectionEntry(id, servings = 4))
        val recorder = FlowRecorder(selection.observeSelection(), backgroundScope)
        assertEquals(4, recorder.awaitNext().single().entry.servings)

        selection.upsert(ShoppingListSelectionEntry(id, servings = 6))

        assertEquals(6, recorder.awaitUntil { it.singleOrNull()?.entry?.servings == 6 }.single().entry.servings)
        assertEquals(1, entryCount())
    }

    @Test
    fun `selection flow emits after delete`() = runTest {
        val id = seedRecipe("Soup")
        selection.upsert(ShoppingListSelectionEntry(id, servings = 4))
        val recorder = FlowRecorder(selection.observeSelection(), backgroundScope)
        assertEquals(1, recorder.awaitNext().size)

        assertEquals(1, selection.delete(id))

        assertEquals(emptyList(), recorder.awaitUntil { it.isEmpty() })
    }

    @Test
    fun `selection flow hides entry while recipe pending deletion and restores on clear`() = runTest {
        val id = seedRecipe("Soup")
        selection.upsert(ShoppingListSelectionEntry(id, servings = 4))
        val recorder = FlowRecorder(selection.observeSelection(), backgroundScope)
        assertEquals(1, recorder.awaitNext().size)

        recipes.setPendingDeletion(id, clock.instant())
        assertEquals(emptyList(), recorder.awaitUntil { it.isEmpty() })

        recipes.clearPendingDeletion(id)
        assertEquals(listOf(id), recorder.awaitUntil { it.isNotEmpty() }.map { it.entry.recipeId })
        assertEquals(1, entryCount(), "the entry row survives the soft delete")
    }

    @Test
    fun `hard deleting recipe cascades its selection entry`() = runTest {
        val id = seedRecipe("Soup")
        selection.upsert(ShoppingListSelectionEntry(id, servings = 4))
        assertEquals(1, entryCount())

        recipes.deleteRecipeRow(id)

        assertEquals(0, entryCount())
    }
}
