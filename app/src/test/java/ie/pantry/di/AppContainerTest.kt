package ie.pantry.di

import androidx.test.core.app.ApplicationProvider
import ie.pantry.PantryApplication
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import kotlin.test.assertNotNull
import kotlin.test.assertSame
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
}
