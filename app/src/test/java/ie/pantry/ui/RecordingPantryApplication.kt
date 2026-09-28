package ie.pantry.ui

import ie.pantry.PantryApplication
import ie.pantry.data.db.PantryDatabase
import ie.pantry.data.reference.ReferenceDataset
import ie.pantry.data.thumbnail.ThumbnailProcessor
import ie.pantry.data.thumbnail.ThumbnailStore
import ie.pantry.di.AppContainer
import ie.pantry.testutil.FixtureAssetSource
import ie.pantry.testutil.RecordingAssetSource
import java.time.Clock

/**
 * Test-only application registered with `@Config(application = ...)`. Its container wraps a
 * [FixtureAssetSource] holding a valid staples fixture in a [RecordingAssetSource], so a test can assert
 * exactly how many times (and when) the dataset asset is opened.
 */
class RecordingPantryApplication : PantryApplication() {

    val recordingSource = RecordingAssetSource(
        FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to validStaplesFixtureBytes())),
    )

    override fun createContainer(): AppContainer =
        AppContainer(
            PantryDatabase.createInMemory(this, Clock.systemUTC()),
            ThumbnailStore(filesDir),
            ThumbnailProcessor(),
            recordingSource,
        )

    companion object {
        private fun validStaplesFixtureBytes(): ByteArray =
            checkNotNull(
                RecordingPantryApplication::class.java.getResourceAsStream("/reference/fixtures/staples_valid.json"),
            ) { "missing fixture staples_valid.json" }.readBytes()
    }
}
