package ie.pantry.data.db

import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.RecipeIngredient
import ie.pantry.testutil.FlowRecorder
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R4 (Flow reads) and R5 (updatedAt is bumped by every public recipe write). */
@RunWith(RobolectricTestRunner::class)
class RecipeDaoTest {

    private val start = Instant.parse("2026-03-01T09:00:00Z")
    private val clock = MutableClock(start)
    private lateinit var db: PantryDatabase
    private lateinit var dao: ie.pantry.data.db.dao.RecipeDao

    @Before
    fun setUp() {
        db = TestDatabases.inMemory(clock)
        dao = db.recipeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun recipe(title: String = "Stew") = Recipe(
        title = title,
        method = "Simmer gently.",
        yieldServings = 4,
        cookingTimeMinutes = 30,
        thumbnailPath = null,
        sourceUrl = null,
        fetchedAt = null,
    )

    private fun ingredient(position: Int, rawText: String = "200 g flour") = RecipeIngredient(
        recipeId = 0,
        position = position,
        rawText = rawText,
        canonicalKey = "flour",
        quantityAmount = 200.0,
        quantityUnit = "g",
        dimension = QuantityDimension.MASS,
    )

    private suspend fun seed(ingredients: List<RecipeIngredient> = listOf(ingredient(0))): Long =
        dao.insertRecipeWithIngredients(recipe(), ingredients)

    private suspend fun updatedAtOf(id: Long): Instant = assertNotNull(dao.findRecipe(id)).recipe.updatedAt

    private fun rawRecipeRow(id: Long): Pair<String?, Long?> =
        db.openHelper.writableDatabase
            .query("SELECT thumbnailPath, pendingDeletionAt FROM recipe WHERE id = $id")
            .use { cursor ->
                assertEquals(true, cursor.moveToFirst(), "recipe $id must exist")
                val path = if (cursor.isNull(0)) null else cursor.getString(0)
                val pending = if (cursor.isNull(1)) null else cursor.getLong(1)
                path to pending
            }

    @Test
    fun `catalogue flow emits empty list when no recipes`() = runTest {
        val recorder = FlowRecorder(dao.observeCatalogue(), backgroundScope)

        assertEquals(emptyList(), recorder.awaitNext())
    }

    @Test
    fun `recipe flow emits null for missing id`() = runTest {
        val recorder = FlowRecorder(dao.observeRecipe(404L), backgroundScope)

        assertNull(recorder.awaitNext())
    }

    @Test
    fun `recipe flow emits second value after recipe update`() = runTest {
        val id = seed()
        val recorder = FlowRecorder(dao.observeRecipe(id), backgroundScope)
        val first = assertNotNull(recorder.awaitNext())
        assertEquals("Stew", first.recipe.title)

        clock.advanceBy(Duration.ofMinutes(1))
        dao.updateRecipe(first.recipe.copy(title = "Stew v2"))

        val second = assertNotNull(recorder.awaitUntil { it?.recipe?.title == "Stew v2" })
        assertEquals("Stew v2", second.recipe.title)
    }

    @Test
    fun `findRecipe returns stored recipe by id`() = runTest {
        val id = seed(listOf(ingredient(0, "200 g flour"), ingredient(1, "1 egg")))

        val found = assertNotNull(dao.findRecipe(id))

        assertEquals(id, found.recipe.id)
        assertEquals("Stew", found.recipe.title)
        assertEquals(listOf("200 g flour", "1 egg"), found.orderedIngredients.map { it.rawText })
        assertEquals(setOf(id), found.ingredients.map { it.recipeId }.toSet())
    }

    @Test
    fun `findRecipe returns null for missing id`() = runTest {
        assertNull(dao.findRecipe(404L))
    }

    @Test
    fun `insertRecipeWithIngredients stamps updatedAt from clock`() = runTest {
        val id = dao.insertRecipeWithIngredients(recipe().copy(updatedAt = Instant.EPOCH), listOf(ingredient(0)))

        assertEquals(start, updatedAtOf(id))
    }

    @Test
    fun `updateRecipe advances updatedAt to clock time`() = runTest {
        val id = seed()
        clock.advanceBy(Duration.ofHours(2))

        dao.updateRecipe(assertNotNull(dao.findRecipe(id)).recipe.copy(title = "Renamed"))

        assertEquals(start.plus(Duration.ofHours(2)), updatedAtOf(id))
    }

    @Test
    fun `updateRecipe overwrites caller supplied stale updatedAt`() = runTest {
        val id = seed()
        clock.advanceBy(Duration.ofHours(1))
        val stale = assertNotNull(dao.findRecipe(id)).recipe.copy(updatedAt = Instant.EPOCH.plusSeconds(1))

        dao.updateRecipe(stale)

        assertEquals(start.plus(Duration.ofHours(1)), updatedAtOf(id))
    }

    @Test
    fun `updateRecipe preserves stored thumbnailPath and pendingDeletionAt`() = runTest {
        val id = dao.insertRecipeWithIngredients(
            recipe().copy(thumbnailPath = "thumbnails/keep.jpg", pendingDeletionAt = Instant.ofEpochMilli(5)),
            emptyList(),
        )

        dao.updateRecipe(
            recipe().copy(id = id, title = "New", thumbnailPath = "thumbnails/other.jpg", pendingDeletionAt = null),
        )

        assertEquals("thumbnails/keep.jpg" to 5L, rawRecipeRow(id))
    }

    @Test
    fun `updateRecipe on missing id is a no-op`() = runTest {
        val recorder = FlowRecorder(dao.observeCatalogue(), backgroundScope)
        assertEquals(emptyList(), recorder.awaitNext())

        dao.updateRecipe(recipe().copy(id = 404L))

        assertNull(dao.findRecipe(404L))
    }

    @Test
    fun `insertIngredient bumps parent updatedAt`() = runTest {
        val id = seed()
        clock.advanceBy(Duration.ofMinutes(10))

        dao.insertIngredient(ingredient(1, "1 egg").copy(recipeId = id))

        assertEquals(start.plus(Duration.ofMinutes(10)), updatedAtOf(id))
    }

    @Test
    fun `updateIngredient bumps parent updatedAt`() = runTest {
        val id = seed()
        val stored = assertNotNull(dao.findRecipe(id)).ingredients.single()
        clock.advanceBy(Duration.ofMinutes(20))

        dao.updateIngredient(stored.copy(rawText = "250 g flour"))

        assertEquals(start.plus(Duration.ofMinutes(20)), updatedAtOf(id))
    }

    @Test
    fun `deleteIngredient bumps parent updatedAt`() = runTest {
        val id = seed()
        val stored = assertNotNull(dao.findRecipe(id)).ingredients.single()
        clock.advanceBy(Duration.ofMinutes(30))

        dao.deleteIngredient(stored.id)

        assertEquals(start.plus(Duration.ofMinutes(30)), updatedAtOf(id))
        assertEquals(emptyList(), assertNotNull(dao.findRecipe(id)).ingredients)
    }

    @Test
    fun `deleteIngredient on missing id does not bump`() = runTest {
        val id = seed()
        clock.advanceBy(Duration.ofMinutes(40))

        dao.deleteIngredient(404L)

        assertEquals(start, updatedAtOf(id))
    }

    @Test
    fun `orderedIngredients sorted by position when inserted out of order`() = runTest {
        val id = seed(listOf(ingredient(2, "c"), ingredient(0, "a"), ingredient(1, "b")))

        val found = assertNotNull(dao.findRecipe(id))

        assertEquals(listOf(0, 1, 2), found.orderedIngredients.map { it.position })
        assertEquals(listOf("a", "b", "c"), found.orderedIngredients.map { it.rawText })
    }

    @Test
    fun `orderedIngredients re-sorts after position update`() = runTest {
        val id = seed(listOf(ingredient(0, "a"), ingredient(1, "b")))
        val a = assertNotNull(dao.findRecipe(id)).ingredients.single { it.rawText == "a" }

        dao.updateIngredient(a.copy(position = 5))

        val found = assertNotNull(dao.findRecipe(id))
        assertEquals(listOf("b", "a"), found.orderedIngredients.map { it.rawText })
    }
}
