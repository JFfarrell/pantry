package ie.pantry.data.db

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import ie.pantry.data.db.entity.NutritionBasis
import ie.pantry.data.db.entity.NutritionCacheEntry
import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.RecipeIngredient
import ie.pantry.data.db.entity.RetailerAssistSession
import ie.pantry.data.db.entity.SelectionSnapshot
import ie.pantry.data.db.entity.ShoppingList
import ie.pantry.data.db.entity.ShoppingListItem
import ie.pantry.data.db.entity.ShoppingListSelectionEntry
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import java.io.IOException
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * R7: data survives closing and reopening the database file, with no network involved. One row per
 * entity, some optionals deliberately absent.
 */
@RunWith(RobolectricTestRunner::class)
class RestartDurabilityTest {

    private val name = "restart-durability.db"
    private val start = Instant.parse("2026-04-01T08:00:00Z")
    private val fetchedAt = Instant.parse("2026-02-01T00:00:00Z")

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }

    private data class Ids(val recipeId: Long, val listId: Long)

    private suspend fun populate(db: PantryDatabase): Ids {
        val recipeId = db.recipeDao().insertRecipeWithIngredients(
            Recipe(
                title = "Restart Stew",
                method = "Simmer.",
                yieldServings = 4,
                cookingTimeMinutes = null,
                thumbnailPath = null,
                sourceUrl = null,
                fetchedAt = null,
            ),
            listOf(
                RecipeIngredient(
                    recipeId = 0, position = 0, rawText = "200 g flour", canonicalKey = "flour",
                    quantityAmount = 200.0, quantityUnit = "g", dimension = QuantityDimension.MASS,
                ),
                RecipeIngredient(
                    recipeId = 0, position = 1, rawText = "salt to taste", canonicalKey = "salt",
                    quantityAmount = null, quantityUnit = null, dimension = QuantityDimension.UNQUANTIFIED,
                ),
            ),
        )
        db.selectionEntryDao().upsert(ShoppingListSelectionEntry(recipeId, servings = null))
        val listId = db.shoppingListDao().insertListWithItems(
            ShoppingList(createdAt = start, sourceSelection = listOf(SelectionSnapshot(recipeId, null))),
            listOf(
                ShoppingListItem(
                    shoppingListId = 0, canonicalKey = "flour", displayName = "Flour", quantityAmount = 200.0,
                    quantityUnit = "g", dimension = QuantityDimension.MASS, section = null, walkOrderIndex = 0,
                ),
            ),
        )
        db.retailerAssistDao().upsert(RetailerAssistSession(listId, positionIndex = 3, skippedItemIds = listOf(9L)))
        db.nutritionCacheDao().upsert(
            NutritionCacheEntry(
                canonicalKey = "flour", basisUnit = NutritionBasis.PER_100G, energyKcal = 364.0,
                proteinG = null, fatG = null, carbohydrateG = null, sourceAttribution = "Example",
                licenceTag = "ODbL-1.0", fetchedAt = fetchedAt, estimatedConversion = true,
            ),
        )
        return Ids(recipeId, listId)
    }

    /** Populates a fresh on-disk database, closes it, and returns a new instance on the same file. */
    private suspend fun populateAndReopen(): Pair<PantryDatabase, Ids> {
        val first = TestDatabases.onDisk(name, MutableClock(start))
        val ids = populate(first)
        first.close()
        return TestDatabases.onDisk(name, MutableClock(start)) to ids
    }

    @Test
    fun `every entity row reads back unchanged after reopen`() = runTest {
        val (db, ids) = populateAndReopen()

        val recipe = assertNotNull(db.recipeDao().findRecipe(ids.recipeId))
        assertEquals("Restart Stew", recipe.recipe.title)
        assertEquals("Simmer.", recipe.recipe.method)
        assertEquals(4, recipe.recipe.yieldServings)
        assertEquals(start, recipe.recipe.updatedAt)
        assertEquals(listOf("200 g flour", "salt to taste"), recipe.orderedIngredients.map { it.rawText })
        assertEquals(200.0, recipe.orderedIngredients[0].quantityAmount)
        assertEquals("g", recipe.orderedIngredients[0].quantityUnit)
        assertEquals(QuantityDimension.MASS, recipe.orderedIngredients[0].dimension)
        assertEquals(QuantityDimension.UNQUANTIFIED, recipe.orderedIngredients[1].dimension)

        val selection = db.selectionEntryDao().observeSelection().first().single()
        assertEquals(ids.recipeId, selection.entry.recipeId)
        assertEquals("Restart Stew", selection.recipe.title)

        val list = assertNotNull(db.shoppingListDao().observeList(ids.listId).first())
        assertEquals(start, list.shoppingList.createdAt)
        assertEquals(listOf(SelectionSnapshot(ids.recipeId, null)), list.shoppingList.sourceSelection)
        assertEquals("Flour", list.items.single().displayName)
        assertEquals(200.0, list.items.single().quantityAmount)

        val session = assertNotNull(db.retailerAssistDao().observeSession(ids.listId).first())
        assertEquals(3, session.positionIndex)
        assertEquals(listOf(9L), session.skippedItemIds)

        val nutrition = assertNotNull(db.nutritionCacheDao().find("flour"))
        assertEquals(NutritionBasis.PER_100G, nutrition.basisUnit)
        assertEquals(364.0, nutrition.energyKcal)
        assertEquals("ODbL-1.0", nutrition.licenceTag)
        assertEquals(fetchedAt, nutrition.fetchedAt)
        assertEquals(true, nutrition.estimatedConversion)
        db.close()
    }

    @Test
    fun `absent optional values stay null after reopen`() = runTest {
        val (db, ids) = populateAndReopen()

        val recipe = assertNotNull(db.recipeDao().findRecipe(ids.recipeId))
        assertNull(recipe.recipe.cookingTimeMinutes)
        assertNull(recipe.recipe.thumbnailPath)
        assertNull(recipe.recipe.sourceUrl)
        assertNull(recipe.recipe.fetchedAt)
        assertNull(recipe.recipe.pendingDeletionAt)
        assertNull(recipe.orderedIngredients[1].quantityAmount)
        assertNull(recipe.orderedIngredients[1].quantityUnit)
        assertNull(db.selectionEntryDao().observeSelection().first().single().entry.servings)
        assertNull(assertNotNull(db.shoppingListDao().observeList(ids.listId).first()).items.single().section)
        val nutrition = assertNotNull(db.nutritionCacheDao().find("flour"))
        assertNull(nutrition.proteinG)
        assertNull(nutrition.fatG)
        assertNull(nutrition.carbohydrateG)
        db.close()
    }

    @Test
    fun `reopen completes with no network access`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(connectivity).setActiveNetworkInfo(null)
        assertNull(connectivity.activeNetworkInfo, "the test environment must have no active network")

        val selections = AtomicInteger()
        val previous = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<java.net.Proxy> {
                selections.incrementAndGet()
                throw IOException("network access attempted")
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        })
        try {
            assertFailsWith<ClassNotFoundException>("no HTTP client may be on the classpath") {
                Class.forName("okhttp3.OkHttpClient")
            }

            val (db, ids) = populateAndReopen()

            assertNotNull(db.recipeDao().findRecipe(ids.recipeId))
            assertEquals(0, selections.get(), "the persistence layer must never try to reach the network")
            db.close()
        } finally {
            ProxySelector.setDefault(previous)
        }
    }
}
