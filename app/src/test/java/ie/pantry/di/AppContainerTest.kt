package ie.pantry.di

import androidx.test.core.app.ApplicationProvider
import ie.pantry.PantryApplication
import ie.pantry.data.reference.LoadFailed
import ie.pantry.data.reference.LoadFailure
import ie.pantry.data.reference.ReferenceDataStore
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import ie.pantry.testutil.FixtureAssetSource
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.RecordingAssetSource
import ie.pantry.testutil.TestDatabases
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R2 AC2 and AC4: one container per application, built without a device or a network. */
@RunWith(RobolectricTestRunner::class)
class AppContainerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `application container is the same instance across reads`() {
        val app = ApplicationProvider.getApplicationContext<PantryApplication>()

        assertSame(app.container, app.container)
    }

    @Test
    fun `container database and DAO properties are identical across reads`() {
        val app = ApplicationProvider.getApplicationContext<PantryApplication>()

        val first = app.container
        val second = app.container

        assertSame(first.database, second.database)
        assertSame(first.recipeDao, second.recipeDao)
        assertSame(first.selectionEntryDao, second.selectionEntryDao)
        assertSame(first.shoppingListDao, second.shoppingListDao)
        assertSame(first.retailerAssistDao, second.retailerAssistDao)
        assertSame(first.nutritionCacheDao, second.nutritionCacheDao)
        assertSame(first.recipeRepository, second.recipeRepository)
    }

    @Test
    fun `container builds on in-memory database without network`() {
        val database = TestDatabases.inMemory(MutableClock())

        val container = AppContainer(database, ThumbnailStore(tmp.newFolder()), ThumbnailProcessor())

        assertSame(database, container.database)
        assertNotNull(container.recipeRepository)
        database.close()
    }

    // ---- T15: referenceAssets / referenceData ----

    @Test
    fun `container construction opens no dataset asset`() {
        val database = TestDatabases.inMemory(MutableClock())
        val recording = RecordingAssetSource(FixtureAssetSource(emptyMap()))

        AppContainer(database, ThumbnailStore(tmp.newFolder()), ThumbnailProcessor(), recording)

        assertEquals(0, recording.openCount)
        database.close()
    }

    @Test
    fun `one lookup through the container records exactly one open`() = runTest {
        val database = TestDatabases.inMemory(MutableClock())
        val recording = RecordingAssetSource(FixtureAssetSource(emptyMap()))
        val container = AppContainer(database, ThumbnailStore(tmp.newFolder()), ThumbnailProcessor(), recording)

        container.referenceData.stapleFor("water")

        assertEquals(1, recording.openCount)
        database.close()
    }

    @Test
    fun `three-argument container returns ASSET_ABSENT from stapleFor without throwing`() = runTest {
        val database = TestDatabases.inMemory(MutableClock())

        val container = AppContainer(database, ThumbnailStore(tmp.newFolder()), ThumbnailProcessor())
        val result = container.referenceData.stapleFor("water")

        val failed = assertIs<LoadFailed>(result)
        assertEquals(LoadFailure.Category.ASSET_ABSENT, failed.failure.category)
        database.close()
    }

    @Test
    fun `container reference data store is the same instance across reads`() {
        val app = ApplicationProvider.getApplicationContext<PantryApplication>()

        assertSame(app.container.referenceData, app.container.referenceData)
        assertIs<ReferenceDataStore>(app.container.referenceData)
    }
}
