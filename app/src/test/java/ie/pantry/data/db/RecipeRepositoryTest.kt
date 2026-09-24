package ie.pantry.data.db

import android.graphics.BitmapFactory
import android.media.ExifInterface
import ie.pantry.data.thumbnail.NoImageReason
import ie.pantry.data.thumbnail.ThumbnailOutcome
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.db.entity.Recipe
import ie.pantry.data.db.entity.RecipeIngredient
import ie.pantry.testutil.ImageFixtures
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.Sentinels
import ie.pantry.testutil.TestDatabases
import ie.pantry.testutil.assertNoSentinel
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** R8: saving a recipe with a thumbnail. A thumbnail failure never prevents the save. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipeRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = MutableClock(Instant.parse("2026-05-01T10:00:00Z"))
    private lateinit var db: PantryDatabase
    private lateinit var filesDir: File
    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        db = TestDatabases.inMemory(clock)
        filesDir = tmp.newFolder("files")
        repository = RecipeRepository(db, db.recipeDao(), ThumbnailStore(filesDir), ThumbnailProcessor())
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

    private fun ingredient(id: Long = 0, rawText: String = "200 g flour") = RecipeIngredient(
        id = id,
        recipeId = 0,
        position = 0,
        rawText = rawText,
        canonicalKey = "flour",
        quantityAmount = 200.0,
        quantityUnit = "g",
        dimension = QuantityDimension.MASS,
    )

    private fun thumbnailFiles(): List<String> = File(filesDir, "thumbnails").list().orEmpty().toList()

    private fun bounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    private fun assertStoredJpeg(result: RecipeRepository.SaveResult, expectedBounds: Pair<Int, Int>) {
        val stored = assertIs<ThumbnailOutcome.Stored>(result.thumbnail)
        val file = File(filesDir, stored.relativePath)
        val header = file.readBytes().take(3).map { it.toInt() and 0xFF }
        assertEquals(listOf(0xFF, 0xD8, 0xFF), header, "stored file must start FF D8 FF")
        assertEquals(expectedBounds, bounds(file))
    }

    @Test
    fun `saveNewRecipe stores import png bytes as downsampled jpeg`() = runTest {
        val result = repository.saveNewRecipe(recipe(), listOf(ingredient()), ImageFixtures.png(2000, 1000))

        assertStoredJpeg(result, 512 to 256)
        val stored = assertIs<ThumbnailOutcome.Stored>(result.thumbnail)
        assertEquals(stored.relativePath, assertNotNull(db.recipeDao().findRecipe(result.recipeId)).recipe.thumbnailPath)
    }

    @Test
    fun `saveNewRecipe stores picker exif jpeg bytes as downsampled jpeg`() = runTest {
        val picker = ImageFixtures.jpeg(800, 400, ExifInterface.ORIENTATION_ROTATE_90)

        val result = repository.saveNewRecipe(recipe(), listOf(ingredient()), picker)

        assertStoredJpeg(result, 256 to 512)
        val stored = assertIs<ThumbnailOutcome.Stored>(result.thumbnail)
        assertEquals(stored.relativePath, assertNotNull(db.recipeDao().findRecipe(result.recipeId)).recipe.thumbnailPath)
    }

    @Test
    fun `saveNewRecipe with undecodable bytes saves recipe with DECODE_FAILED`() = runTest {
        val result = repository.saveNewRecipe(recipe(), listOf(ingredient()), ImageFixtures.garbage())

        assertEquals(ThumbnailOutcome.NoImage(NoImageReason.DECODE_FAILED), result.thumbnail)
        assertNull(assertNotNull(db.recipeDao().findRecipe(result.recipeId)).recipe.thumbnailPath)
        assertEquals(emptyList(), thumbnailFiles())
    }

    @Test
    fun `saveNewRecipe with failing store saves recipe with WRITE_FAILED`() = runTest {
        // A store rooted at a regular file cannot create its directory, so every write fails.
        val failing = RecipeRepository(db, db.recipeDao(), ThumbnailStore(tmp.newFile("regular")), ThumbnailProcessor())

        val result = failing.saveNewRecipe(recipe(), listOf(ingredient()), ImageFixtures.png(600, 400))

        assertEquals(ThumbnailOutcome.NoImage(NoImageReason.WRITE_FAILED), result.thumbnail)
        assertNull(assertNotNull(db.recipeDao().findRecipe(result.recipeId)).recipe.thumbnailPath)
    }

    @Test
    fun `saveNewRecipe with null bytes saves recipe with NO_SOURCE`() = runTest {
        val result = repository.saveNewRecipe(recipe(), listOf(ingredient()), null)

        assertEquals(ThumbnailOutcome.NoImage(NoImageReason.NO_SOURCE), result.thumbnail)
        assertNull(assertNotNull(db.recipeDao().findRecipe(result.recipeId)).recipe.thumbnailPath)
    }

    @Test
    fun `saveNewRecipe overrides caller thumbnailPath and pendingDeletionAt`() = runTest {
        val caller = recipe().copy(thumbnailPath = "thumbnails/caller.jpg", pendingDeletionAt = Instant.ofEpochMilli(5))

        val result = repository.saveNewRecipe(caller, listOf(ingredient()), ImageFixtures.garbage())

        // findRecipe hides pending recipes, so a non-null result also proves pendingDeletionAt was cleared.
        val saved = assertNotNull(db.recipeDao().findRecipe(result.recipeId)).recipe
        assertNull(saved.thumbnailPath, "the caller's thumbnailPath must be replaced by the outcome")
        assertNull(saved.pendingDeletionAt)
    }

    @Test
    fun `saveNewRecipe deletes written file when row insert fails`() = runTest {
        // Two ingredients sharing an explicit primary key make the row insert fail after the file is written.
        val clashing = listOf(ingredient(id = 7, rawText = Sentinels.INGREDIENT), ingredient(id = 7))

        val failure = assertFailsWith<PersistenceException> {
            repository.saveNewRecipe(recipe(Sentinels.TITLE), clashing, ImageFixtures.png(600, 400))
        }

        assertEquals(PersistenceException.Category.CONSTRAINT, failure.category)
        assertNull(failure.cause, "the cause must not be chained")
        failure.assertNoSentinel()
        assertEquals(emptyList(), thumbnailFiles(), "the orphaned thumbnail file must be deleted")
    }

    private fun rawRecipeCount(): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM recipe").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun rawIngredientCount(): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM recipe_ingredient").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun `replaceThumbnail writes new file then deletes old file and bumps updatedAt`() = runTest {
        val saved = repository.saveNewRecipe(recipe(), listOf(ingredient()), ImageFixtures.png(2000, 1000))
        val oldPath = assertIs<ThumbnailOutcome.Stored>(saved.thumbnail).relativePath
        clock.advanceBy(Duration.ofHours(1))

        val outcome = repository.replaceThumbnail(saved.recipeId, ImageFixtures.png(900, 600))

        val newPath = assertIs<ThumbnailOutcome.Stored>(outcome).relativePath
        assertTrue(newPath != oldPath, "a new file must be written")
        assertTrue(File(filesDir, newPath).exists(), "the new file must exist")
        assertTrue(!File(filesDir, oldPath).exists(), "the old file must be gone")
        val row = assertNotNull(db.recipeDao().findRecipe(saved.recipeId)).recipe
        assertEquals(newPath, row.thumbnailPath)
        assertEquals(clock.instant(), row.updatedAt)
    }

    @Test
    fun `replaceThumbnail with undecodable bytes leaves existing thumbnail unchanged`() = runTest {
        val saved = repository.saveNewRecipe(recipe(), listOf(ingredient()), ImageFixtures.png(2000, 1000))
        val oldPath = assertIs<ThumbnailOutcome.Stored>(saved.thumbnail).relativePath
        val before = assertNotNull(db.recipeDao().findRecipe(saved.recipeId)).recipe

        clock.advanceBy(Duration.ofHours(1))
        val outcome = repository.replaceThumbnail(saved.recipeId, ImageFixtures.garbage())

        assertEquals(ThumbnailOutcome.NoImage(NoImageReason.DECODE_FAILED), outcome)
        assertTrue(File(filesDir, oldPath).exists(), "the existing file must survive a failed replace")
        assertEquals(before, assertNotNull(db.recipeDao().findRecipe(saved.recipeId)).recipe)
    }

    @Test
    fun `removeThumbnail clears reference and deletes file`() = runTest {
        val saved = repository.saveNewRecipe(recipe(), listOf(ingredient()), ImageFixtures.png(2000, 1000))
        val path = assertIs<ThumbnailOutcome.Stored>(saved.thumbnail).relativePath
        clock.advanceBy(Duration.ofHours(1))

        repository.removeThumbnail(saved.recipeId)

        val row = assertNotNull(db.recipeDao().findRecipe(saved.recipeId)).recipe
        assertNull(row.thumbnailPath, "an explicit no-image is stored as null")
        assertEquals(clock.instant(), row.updatedAt)
        assertTrue(!File(filesDir, path).exists(), "the file must be deleted")
    }

    @Test
    fun `deleteRecipe removes row and thumbnail file`() = runTest {
        val saved = repository.saveNewRecipe(recipe(), listOf(ingredient()), ImageFixtures.png(2000, 1000))
        val path = assertIs<ThumbnailOutcome.Stored>(saved.thumbnail).relativePath
        assertEquals(1, rawRecipeCount())
        assertEquals(1, rawIngredientCount())

        repository.deleteRecipe(saved.recipeId)

        assertEquals(0, rawRecipeCount())
        assertEquals(0, rawIngredientCount(), "ingredients cascade with the recipe")
        assertTrue(!File(filesDir, path).exists(), "the thumbnail file must be deleted")
    }
}
