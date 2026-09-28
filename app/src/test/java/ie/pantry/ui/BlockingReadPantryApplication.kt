package ie.pantry.ui

import ie.pantry.PantryApplication
import ie.pantry.data.db.PantryDatabase
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import ie.pantry.di.AppContainer
import ie.pantry.testutil.BlockingAssetSource
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test-only application registered with `@Config(application = ...)`. It builds the container over an
 * in-memory database and a [BlockingAssetSource] that never resolves, and counts how often the
 * container-creation hook runs.
 */
class BlockingReadPantryApplication : PantryApplication() {

    val createContainerCalls = AtomicInteger()
    val blockingSource = BlockingAssetSource()

    override fun createContainer(): AppContainer {
        createContainerCalls.incrementAndGet()
        return AppContainer(
            PantryDatabase.createInMemory(this, Clock.systemUTC()),
            ThumbnailStore(filesDir),
            ThumbnailProcessor(),
            blockingSource,
        )
    }
}
