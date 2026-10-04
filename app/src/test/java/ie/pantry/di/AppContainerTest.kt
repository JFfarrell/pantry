package ie.pantry.di

import androidx.test.core.app.ApplicationProvider
import ie.pantry.PantryApplication
import ie.pantry.data.gateway.GatewayException
import ie.pantry.data.gateway.GatewayPolicy
import ie.pantry.data.gateway.GatewayResult
import ie.pantry.data.reference.LoadFailed
import ie.pantry.data.reference.LoadFailure
import ie.pantry.data.reference.ReferenceDataStore
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import ie.pantry.testutil.FixtureAssetSource
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.RecordingAssetSource
import ie.pantry.testutil.TestDatabases
import ie.pantry.testutil.TestGateways
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
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

    // ---- F4 T15: gateway ----

    @Test
    fun `container gateway is the same instance across reads`() {
        val app = ApplicationProvider.getApplicationContext<PantryApplication>()

        assertSame(app.container.gateway, app.container.gateway)
    }

    @Test
    fun `gateway client is built once and shared across reads`() {
        val gateway = ApplicationProvider.getApplicationContext<PantryApplication>().container.gateway

        assertSame(gateway.client, gateway.client)
    }

    @Test
    fun `production gateway uses the default policy`() {
        val gateway = ApplicationProvider.getApplicationContext<PantryApplication>().container.gateway

        assertEquals(GatewayPolicy.DEFAULT, gateway.policy)
    }

    @Test
    fun `production container refuses a loopback url with no request recorded`() = runTest {
        val server = TestGateways.server()
        try {
            server.dispatcher = TestGateways.answerEvery { MockResponse().setBody("reached") }
            val gateway = ApplicationProvider.getApplicationContext<PantryApplication>().container.gateway

            val result = gateway.fetchPage(TestGateways.urlOf(server, "/"))

            val error = assertIs<GatewayResult.Failed>(result).error
            assertEquals(GatewayException.Category.ADDRESS_REFUSED, error.category)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
