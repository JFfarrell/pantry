package ie.pantry.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.RecipeIngredient
import ie.pantry.data.db.entity.ShoppingList
import ie.pantry.data.db.entity.ShoppingListItem
import ie.pantry.data.thumbnail.NoImageReason
import ie.pantry.data.thumbnail.ThumbnailOutcome
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import ie.pantry.testutil.ImageFixtures
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.Sentinels
import ie.pantry.testutil.TestDatabases
import ie.pantry.testutil.assertNoSentinel
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/**
 * R3 AC8 / CFC-4: no user or imported content reaches an error message, log line or stack trace.
 * Each forced failure carries [Sentinels] content and is followed by [assertNoSentinel].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersistenceErrorHygieneTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dbName = "hygiene-downgrade.db"

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(dbName)
    }

    @Test
    fun `assertNoSentinel detects sentinel in nested cause and suppressed`() {
        val nested = RuntimeException("outer", IllegalStateException("inner ${Sentinels.INGREDIENT}"))
        assertFailsWith<AssertionError> { nested.assertNoSentinel() }

        val suppressed = RuntimeException("outer").apply { addSuppressed(Exception(Sentinels.URL)) }
        assertFailsWith<AssertionError> { suppressed.assertNoSentinel() }

        RuntimeException("clean", IllegalStateException("also clean")).assertNoSentinel()
    }

    @Test
    fun `persistence failure message carries only category operation and cause type`() {
        val failure = persistenceFailure("saveNewRecipe", SQLiteConstraintException(Sentinels.TITLE))

        assertEquals(
            "Persistence failure: category=CONSTRAINT operation=saveNewRecipe cause=SQLiteConstraintException",
            failure.message,
        )
        assertEquals("SQLiteConstraintException", failure.causeType)
        failure.assertNoSentinel()
    }

    @Test
    fun `persistence failure does not chain the cause`() {
        val failure = persistenceFailure("saveNewRecipe", SQLiteConstraintException(Sentinels.TITLE))

        assertNull(failure.cause)
        assertEquals(0, failure.suppressed.size)
    }

    @Test
    fun `persistence failure copies cause stack frames`() {
        val cause = SQLiteConstraintException("x")
        val frames = arrayOf(StackTraceElement("ie.pantry.Frame", "method", "Frame.kt", 42))
        cause.stackTrace = frames

        val failure = persistenceFailure("saveNewRecipe", cause)

        assertEquals(frames.toList(), failure.stackTrace.toList())
    }

    @Test
    fun `persistence failure maps constraint exception to CONSTRAINT`() {
        val failure = persistenceFailure("saveNewRecipe", SQLiteConstraintException(Sentinels.TITLE))

        assertEquals(PersistenceException.Category.CONSTRAINT, failure.category)
        assertEquals("saveNewRecipe", failure.operation)
    }

    @Test
    fun `persistence failure logs only key value fields with no sentinel`() {
        ShadowLog.clear()

        persistenceFailure("saveNewRecipe", SQLiteConstraintException(Sentinels.TITLE))

        val warnings = ShadowLog.getLogs().filter { it.tag == "PantryDb" && it.type == Log.WARN }
        assertTrue(warnings.isNotEmpty(), "the conversion site must log a warning")
        val keyValues = Regex("""^\w+=\S+( \w+=\S+)*$""")
        for (entry in warnings) {
            assertTrue(keyValues.matches(entry.msg), "log line must be key=value fields only: ${entry.msg}")
            assertTrue(Sentinels.all.none { entry.msg.contains(it) }, "sentinel leaked into a log line")
        }
    }

    @Test
    fun `downgraded database open throws content-free exception`() {
        val clock = MutableClock()
        val v1 = TestDatabases.onDisk(dbName, clock)
        v1.openHelper.writableDatabase.apply {
            execSQL(
                "INSERT INTO recipe (title, method, updatedAt) VALUES (?, ?, ?)",
                arrayOf<Any>(Sentinels.TITLE, "method", 0L),
            )
            execSQL("PRAGMA user_version = 2")
        }
        v1.close()

        val reopened = TestDatabases.onDisk(dbName, clock)
        val failure = assertFailsWith<IllegalStateException> { reopened.openHelper.writableDatabase }

        failure.assertNoSentinel()
        reopened.close()
    }

    private fun recipe(id: Long = 0, title: String = "Stew") = Recipe(
        id = id,
        title = title,
        method = "Method",
        yieldServings = null,
        cookingTimeMinutes = null,
        thumbnailPath = null,
        sourceUrl = null,
        fetchedAt = null,
    )

    private fun ingredient(recipeId: Long, rawText: String) = RecipeIngredient(
        recipeId = recipeId,
        position = 0,
        rawText = rawText,
        canonicalKey = "key",
        quantityAmount = null,
        quantityUnit = null,
        dimension = QuantityDimension.UNQUANTIFIED,
    )

    @Test
    fun `recipe dao foreign key violation carries no ingredient sentinel`() = runTest {
        val db = TestDatabases.inMemory(MutableClock())
        try {
            // Inserting an ingredient for a recipe that does not exist violates the foreign key.
            val failure = assertFailsWith<SQLiteConstraintException> {
                db.recipeDao().insertIngredient(ingredient(recipeId = 404L, rawText = Sentinels.INGREDIENT))
            }

            failure.assertNoSentinel()
        } finally {
            db.close()
        }
    }

    @Test
    fun `recipe dao primary key conflict carries no title sentinel`() = runTest {
        val db = TestDatabases.inMemory(MutableClock())
        try {
            db.recipeDao().insertRecipeWithIngredients(recipe(id = 1L, title = Sentinels.TITLE), emptyList())

            val failure = assertFailsWith<SQLiteConstraintException> {
                db.recipeDao().insertRecipeWithIngredients(recipe(id = 1L, title = Sentinels.TITLE), emptyList())
            }

            failure.assertNoSentinel()
        } finally {
            db.close()
        }
    }

    @Test
    fun `shopping list dao foreign key violation carries no item sentinel`() = runTest {
        val db = TestDatabases.inMemory(MutableClock())
        try {
            val dao = db.shoppingListDao()
            val listId = dao.insertListWithItems(
                ShoppingList(createdAt = Instant.EPOCH, sourceSelection = emptyList()),
                listOf(
                    ShoppingListItem(
                        shoppingListId = 0,
                        canonicalKey = "key",
                        displayName = Sentinels.INGREDIENT,
                        quantityAmount = null,
                        quantityUnit = null,
                        dimension = QuantityDimension.UNQUANTIFIED,
                        section = null,
                        walkOrderIndex = 0,
                    ),
                ),
            )
            val stored = assertNotNull(dao.observeList(listId).first()).items.single()

            // Re-pointing the item at a list that does not exist violates the foreign key.
            val failure = assertFailsWith<SQLiteConstraintException> { dao.updateItem(stored.copy(shoppingListId = 999L)) }

            failure.assertNoSentinel()
        } finally {
            db.close()
        }
    }

    private fun repository(db: PantryDatabase, store: ThumbnailStore = ThumbnailStore(tmp.newFolder())) =
        RecipeRepository(db, db.recipeDao(), store, ThumbnailProcessor())

    @Test
    fun `repository save on unopenable database throws content-free PersistenceException with null cause`() = runTest {
        // Room quietly reopens a closed database, so close() cannot force a failure. A directory where the
        // database file should be makes the open itself fail.
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.getDatabasePath(dbName).mkdirs(), "the directory standing in for the database file")
        val db = TestDatabases.onDisk(dbName, MutableClock())

        val failure = assertFailsWith<PersistenceException> {
            repository(db).saveNewRecipe(recipe(title = Sentinels.TITLE), listOf(ingredient(0, Sentinels.INGREDIENT)), null)
        }

        assertEquals(PersistenceException.Category.DATABASE_OPEN, failure.category)
        assertNull(failure.cause)
        failure.assertNoSentinel()
    }

    @Test
    fun `repository save on downgraded database throws content-free PersistenceException`() = runTest {
        val clock = MutableClock()
        val v1 = TestDatabases.onDisk(dbName, clock)
        v1.openHelper.writableDatabase.apply {
            execSQL(
                "INSERT INTO recipe (title, method, updatedAt) VALUES (?, ?, ?)",
                arrayOf<Any>(Sentinels.TITLE, "method", 0L),
            )
            execSQL("PRAGMA user_version = 2")
        }
        v1.close()
        val reopened = TestDatabases.onDisk(dbName, clock)

        val failure = assertFailsWith<PersistenceException> {
            repository(reopened).saveNewRecipe(recipe(title = Sentinels.TITLE), listOf(ingredient(0, Sentinels.INGREDIENT)), null)
        }

        assertEquals(PersistenceException.Category.DATABASE_OPEN, failure.category)
        assertNull(failure.cause)
        failure.assertNoSentinel()
        reopened.close()
    }

    @Test
    fun `thumbnail write failure yields NoImage with no escaping exception`() = runTest {
        val db = TestDatabases.inMemory(MutableClock())
        try {
            // A store rooted at a regular file cannot create its directory, so the write fails.
            val failingStore = ThumbnailStore(tmp.newFile("regular"))

            val result = repository(db, failingStore)
                .saveNewRecipe(recipe(title = Sentinels.TITLE), listOf(ingredient(0, Sentinels.INGREDIENT)), ImageFixtures.png(600, 400))

            assertEquals(ThumbnailOutcome.NoImage(NoImageReason.WRITE_FAILED), result.thumbnail)
        } finally {
            db.close()
        }
    }

    @Test
    fun `thumbnail write failure logs no sentinel`() = runTest {
        val db = TestDatabases.inMemory(MutableClock())
        try {
            ShadowLog.clear()
            val failingStore = ThumbnailStore(tmp.newFile("regular"))

            repository(db, failingStore)
                .saveNewRecipe(recipe(title = Sentinels.TITLE), listOf(ingredient(0, Sentinels.INGREDIENT)), ImageFixtures.png(600, 400))

            val warnings = ShadowLog.getLogs().filter { it.type == Log.WARN }
            assertTrue(warnings.isNotEmpty(), "the degradation site must log a warning")
            for (entry in warnings) {
                assertTrue(Sentinels.all.none { entry.msg.contains(it) }, "sentinel leaked into a log line: ${entry.tag}")
            }
        } finally {
            db.close()
        }
    }
}
