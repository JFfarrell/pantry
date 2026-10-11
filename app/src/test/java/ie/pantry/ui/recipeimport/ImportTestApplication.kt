package ie.pantry.ui.recipeimport

import ie.pantry.PantryApplication
import ie.pantry.data.db.PantryDatabase
import ie.pantry.data.gateway.ExternalDataGateway
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import ie.pantry.di.AppContainer
import ie.pantry.recipeimport.DraftImage
import ie.pantry.recipeimport.ImportSeams
import ie.pantry.recipeimport.NoImageCause
import ie.pantry.recipeimport.RecipeImporter
import ie.pantry.testutil.MutableClock
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Test-only application, registered with `@Config(application = ...)`. Its container is built over an in-memory
 * database, a gateway the test supplies and a [RecipeImporter] with injected seams, all read from [Companion]
 * variables that the test sets before it launches the activity. The container is lazy, so it is first read after
 * the test has set them.
 */
class ImportTestApplication : PantryApplication() {

    override fun createContainer(): AppContainer {
        val gateway = checkNotNull(gateway) { "set ImportTestApplication.gateway before launching the activity" }
        val importer = RecipeImporter(gateway, clock, parseDispatcher, imageCheck, seams)
        return AppContainer(
            PantryDatabase.createInMemory(this, clock),
            ThumbnailStore(filesDir),
            ThumbnailProcessor(),
            gateway = gateway,
            clock = clock,
            recipeImporter = importer,
        )
    }

    companion object {
        var gateway: ExternalDataGateway? = null
        var clock: Clock = MutableClock()
        var parseDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        var imageCheck: (ByteArray) -> DraftImage = { DraftImage.NoImage(NoImageCause.NOT_AN_IMAGE) }
        internal var seams: ImportSeams = ImportSeams.PRODUCTION
    }
}
