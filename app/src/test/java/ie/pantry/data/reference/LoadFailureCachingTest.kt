package ie.pantry.data.reference

import ie.pantry.testutil.FixtureAssetSource
import ie.pantry.testutil.RecordingAssetSource
import ie.pantry.testutil.Sentinels
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * R1 AC8, Q7: a failed load is cached like a success, opened exactly once, and logs exactly one
 * content-free line. R6 AC4: a successful load never logs.
 */
@RunWith(RobolectricTestRunner::class)
class LoadFailureCachingTest {

    @After
    fun tearDown() {
        ShadowLog.clear()
    }

    private fun logLines() = ShadowLog.getLogsForTag("PantryRef").map { it.msg }

    @Test
    fun `absent asset returns an equal LoadFailed twice with one open`() = runTest {
        val recording = RecordingAssetSource(FixtureAssetSource(emptyMap()))
        val store = ReferenceDataStore(recording)

        val first = store.staples()
        val second = store.staples()

        assertEquals(first, second)
        assertIs<LoadFailed>(first)
        assertEquals(1, recording.openCount)
    }

    @Test
    fun `invalid asset returns an equal LoadFailed twice with one open`() = runTest {
        val bytes = "{\"entries\": [{\"key\": \"${Sentinels.INGREDIENT}\"".toByteArray()
        val recording = RecordingAssetSource(FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to bytes)))
        val store = ReferenceDataStore(recording)

        val first = store.staples()
        val second = store.staples()

        assertEquals(first, second)
        assertEquals(1, recording.openCount)
    }

    @Test
    fun `failed load writes exactly one content-free log line`() = runTest {
        val store = ReferenceDataStore(FixtureAssetSource(emptyMap()))

        store.staples()
        store.staples()

        val lines = logLines()
        assertEquals(1, lines.size)
        assertEquals("dataset=STAPLES category=ASSET_ABSENT", lines.single())
    }

    @Test
    fun `failed load log and failure carry no sentinel`() = runTest {
        val bytes = "{\"entries\": [{\"key\": \"${Sentinels.INGREDIENT}\"".toByteArray()
        val store = ReferenceDataStore(FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to bytes)))

        val result = store.staples()

        val failed = assertIs<LoadFailed>(result)
        assertFalse(failed.failure.toString().contains(Sentinels.INGREDIENT))
        assertTrue(logLines().none { it.contains(Sentinels.INGREDIENT) })
    }

    @Test
    fun `successful load writes no log line`() = runTest {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/reference/fixtures/staples_valid.json")).readBytes()
        val store = ReferenceDataStore(FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to bytes)))

        val result = store.staples()

        assertIs<LoadResult.Ready<StaplesTable>>(result)
        assertEquals(emptyList(), logLines())
    }
}
