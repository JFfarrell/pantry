package ie.pantry.data.reference

import android.os.Looper
import ie.pantry.testutil.BlockingAssetSource
import ie.pantry.testutil.FixtureAssetSource
import ie.pantry.testutil.GatedAssetSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R6: off-main loading, load-once under concurrency, no reopen after load. Real dispatchers throughout. */
@RunWith(RobolectricTestRunner::class)
class ReferenceLoadThreadingTest {

    @Test
    fun `first lookup from the main thread opens the asset off the main thread`() {
        runBlocking {
        val blocking = BlockingAssetSource()
        val store = ReferenceDataStore(blocking)

        val deferred = CoroutineScope(Dispatchers.Unconfined).async { store.stapleFor("water") }

        assertTrue(blocking.awaitStarted(5, TimeUnit.SECONDS))
        assertFalse(deferred.isCompleted, "the caller must not have a result yet: the read is still blocked")
        assertTrue(blocking.openThreads.none { it == Looper.getMainLooper().thread }, "the open must not run on the main thread")

            blocking.release()
            deferred.await()
        }
    }

    @Test
    fun `main-thread caller regains control before the blocked read completes`() {
        runBlocking {
        val blocking = BlockingAssetSource()
        val store = ReferenceDataStore(blocking)

        val deferred = CoroutineScope(Dispatchers.Unconfined).async { store.stapleFor("water") }
        val started = blocking.awaitStarted(5, TimeUnit.SECONDS)

        assertTrue(started, "the read must have started")
        assertFalse(blocking.completed, "the read must still be blocked when the caller regains control")

            blocking.release()
            deferred.await()
        }
    }

    /** Runs [lookup] 32 times concurrently on [dispatcher], each incrementing [started] just before the store call. */
    private fun runConcurrentLookups(
        dispatcher: CoroutineDispatcher,
        started: AtomicInteger,
        lookup: suspend () -> LookupResult<StaplesTable>,
    ): List<LookupResult<StaplesTable>> = runBlocking {
        val scope = CoroutineScope(dispatcher)
        val deferreds = List(32) {
            scope.async {
                started.incrementAndGet()
                lookup()
            }
        }
        withTimeout(20_000) { deferreds.map { it.await() } }
    }

    @Test
    fun `thirty-two concurrent first lookups open the asset once and share one table`() {
        val gated = GatedAssetSource(FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to fixture("staples_valid.json"))))
        val store = ReferenceDataStore(gated)
        val started = AtomicInteger()
        val pool = Executors.newFixedThreadPool(32)
        try {
            val dispatcher = pool.asCoroutineDispatcher()
            val resultsFuture = Executors.newSingleThreadExecutor().submit<List<LookupResult<StaplesTable>>> {
                runConcurrentLookups(dispatcher, started) {
                    when (val result = store.staples()) {
                        is LoadResult.Ready -> Lookup.Found(result.table)
                        is LoadFailed -> result
                    }
                }
            }
            assertTrue(gated.awaitBlocked(1, 10, TimeUnit.SECONDS), "at least one caller must reach the gate")
            gated.release()
            val results = resultsFuture.get(20, TimeUnit.SECONDS)

            assertEquals(32, started.get())
            assertEquals(1, gated.openCount, "open count")
            val tables = results.map { assertIs<Lookup.Found<StaplesTable>>(it).value }
            for (table in tables) assertSame(tables.first(), table)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrency check fails on the open count when every caller loads separately`() {
        val gated = GatedAssetSource(FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to fixture("staples_valid.json"))))
        val started = AtomicInteger()
        val pool = Executors.newFixedThreadPool(32)

        val error = assertFailsWith<AssertionError> {
            try {
                val dispatcher = pool.asCoroutineDispatcher()
                // Negative control: each call runs loadDataset directly, bypassing the shared store, so
                // every one of the 32 callers opens the source independently instead of sharing one load.
                val resultsFuture = Executors.newSingleThreadExecutor().submit<List<LookupResult<StaplesTable>>> {
                    runConcurrentLookups(dispatcher, started) {
                        when (val result = loadDataset(gated, ReferenceDataset.STAPLES, DatasetParser::parseStaples)) {
                            is LoadResult.Ready -> Lookup.Found(result.table)
                            is LoadFailed -> result
                        }
                    }
                }

                assertTrue(gated.awaitBlocked(32, 10, TimeUnit.SECONDS), "all 32 independent callers must reach the gate")
                gated.release()
                val results = resultsFuture.get(20, TimeUnit.SECONDS)
                assertEquals(32, results.size)

                assertEquals(1, gated.openCount, "open count")
            } finally {
                pool.shutdownNow()
            }
        }

        assertTrue(error.message.orEmpty().contains("open count"), "error message was: ${error.message}")
    }

    @Test
    fun `lookups after load never reopen the asset`() = runBlocking {
        val recording = ie.pantry.testutil.RecordingAssetSource(
            FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to fixture("staples_valid.json"))),
        )
        val store = ReferenceDataStore(recording)

        store.staples()
        store.staples()
        store.stapleFor("water")

        assertEquals(1, recording.openCount)
    }

    @Test
    fun `cancelled first caller does not cancel the shared load`() {
        runBlocking {
        val blocking = BlockingAssetSource()
        val store = ReferenceDataStore(blocking)
        val scope = CoroutineScope(Dispatchers.Unconfined)

        val first = scope.async { store.stapleFor("water") }
        blocking.awaitStarted(5, TimeUnit.SECONDS)
        first.cancel()
        scope.cancel()

        val secondScope = CoroutineScope(Dispatchers.Unconfined)
        val second = secondScope.async { store.stapleFor("water") }
        blocking.release()

            assertEquals(1, blocking.openCount)
            second.await()
        }
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/reference/fixtures/$name")) { "missing fixture $name" }.readBytes()
}
