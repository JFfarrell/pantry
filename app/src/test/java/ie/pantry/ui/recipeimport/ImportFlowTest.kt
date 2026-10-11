package ie.pantry.ui.recipeimport

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.recipeimport.CappedInputStream
import ie.pantry.recipeimport.DraftImage
import ie.pantry.recipeimport.ImportFixtures
import ie.pantry.recipeimport.ImportPhase
import ie.pantry.recipeimport.ImportPipeline
import ie.pantry.recipeimport.ImportResult
import ie.pantry.recipeimport.ImportSeams
import ie.pantry.recipeimport.RecipeImporter
import ie.pantry.recipeimport.RoutedServer
import ie.pantry.recipeimport.ValidatedUrl
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestGateways
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The real ViewModel over the real importer and a MockWebServer: R1 AC1/AC4, R2 AC2 Retry, R5 AC5, and cancellation. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportFlowTest {

    private val dispatcher = StandardTestDispatcher()
    private val routed = RoutedServer()
    private val parseThread = Executors.newSingleThreadExecutor()
    private val parseDispatcher: CoroutineDispatcher = parseThread.asCoroutineDispatcher()
    private val streams = mutableListOf<CappedInputStream>()
    private val parseStarted = CountDownLatch(1)
    private val releaseParse = CountDownLatch(1)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        releaseParse.countDown()
        Dispatchers.resetMain()
        parseThread.shutdownNow()
        routed.close()
    }

    /** Records how every call ended, so a test can tell a cancelled importer from one that finished. */
    private class Tracing(private val inner: ImportPipeline) : ImportPipeline {
        @Volatile var cancelled = false
        @Volatile var finished = false
        @Volatile var result: ImportResult? = null

        override suspend fun importRecipe(url: ValidatedUrl, onPhase: (ImportPhase) -> Unit): ImportResult = try {
            inner.importRecipe(url, onPhase).also { result = it }
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            finished = true
        }
    }

    private fun importer(stall: Boolean = false): RecipeImporter = RecipeImporter(
        TestGateways.against(routed.server),
        MutableClock(),
        parseDispatcher,
        { DraftImage.NoImage(ie.pantry.recipeimport.NoImageCause.NOT_AN_IMAGE) },
        ImportSeams(
            onParse = {
                parseStarted.countDown()
                if (stall) check(releaseParse.await(30, TimeUnit.SECONDS)) { "the stalled parse was never released" }
            },
            onStream = { streams += it },
        ),
    )

    /** Polls the installed test dispatcher (advancing its virtual clock) and the state, in real time, until [predicate] holds or [timeoutMillis] passes. */
    private fun awaitState(
        viewModel: ImportViewModel,
        timeoutMillis: Long = 15_000,
        predicate: (ImportUiState) -> Boolean,
    ) = runBlocking {
        withTimeout(timeoutMillis) {
            while (true) {
                // Virtual time moves too, so the gateway's back-off delay (run on this dispatcher) elapses.
                dispatcher.scheduler.advanceTimeBy(50)
                dispatcher.scheduler.runCurrent()
                if (predicate(viewModel.state.value)) break
                delay(10)
            }
        }
    }

    private fun idle() = dispatcher.scheduler.runCurrent()

    private val heuristic get() = ImportFixtures.page("heuristic.html")

    @Test
    fun `a padded url is imported with exactly the trimmed url`() {
        routed.page("/stew", heuristic)
        val viewModel = ImportViewModel(importer())
        viewModel.onFieldChange("  ${routed.absolute("/stew")}  ")
        viewModel.onImport()
        awaitState(viewModel) { it.screen is Screen.Drafted }
        assertEquals(1, routed.requests("/stew"))
        assertEquals(1, routed.totalRequests)
        assertEquals(routed.absolute("/stew"), viewModel.state.value.field)
    }

    @Test
    fun `each rejected input makes no request`() {
        val viewModel = ImportViewModel(importer())
        val rejected = listOf(
            "ftp://example.ie/a", "intent://x#Intent;end", "content://x/y", "javascript:alert(1)",
            "www.example.ie/stew", "   ", "https://", "https://user:pw@example.ie/a",
        )
        for (input in rejected) {
            viewModel.onFieldChange(input)
            viewModel.onImport()
            idle()
            assertEquals(Screen.Input(fieldError = true), viewModel.state.value.screen, input)
        }
        assertEquals(0, routed.server.requestCount)
    }

    @Test
    fun `retry after a transient failure makes exactly one new page request`() {
        val requests = java.util.concurrent.atomic.AtomicInteger()
        routed.route("/stew") {
            if (requests.incrementAndGet() <= 3) {
                MockResponse().setResponseCode(503)
            } else {
                MockResponse().setHeader("Content-Type", "text/html").setBody(Buffer().write(heuristic))
            }
        }
        val viewModel = ImportViewModel(importer())
        viewModel.onFieldChange(routed.absolute("/stew"))
        viewModel.onImport()
        awaitState(viewModel) { it.screen is Screen.Failed }
        val failed = viewModel.state.value.screen as Screen.Failed
        assertTrue(failed.retryable)
        assertEquals(Failure.HTTP_5XX, failed.message)
        val before = routed.server.requestCount
        assertEquals(3, before, "F4 retries a 503 internally, three requests in all")

        viewModel.onRetry()
        awaitState(viewModel) { it.screen is Screen.Drafted }
        assertEquals(1, routed.server.requestCount - before)
    }

    @Test
    fun `retry from the decline surface makes exactly one new page request`() {
        routed.page("/nothing", ImportFixtures.page("nothing-extracted.html"))
        val viewModel = ImportViewModel(importer())
        viewModel.onFieldChange(routed.absolute("/nothing"))
        viewModel.onImport()
        awaitState(viewModel) { it.screen is Screen.Declined }
        assertEquals(1, routed.requests("/nothing"))

        viewModel.onRetry()
        awaitState(viewModel) { it.screen is Screen.Declined && routed.requests("/nothing") == 2 }
        assertEquals(2, routed.requests("/nothing"))
        assertEquals(2, routed.totalRequests)
    }

    @Test
    fun `cancel during a stalled parse stops the stream and applies no failure`() {
        routed.page("/stew", heuristic)
        val tracing = Tracing(importer(stall = true))
        val viewModel = ImportViewModel(tracing)
        viewModel.onFieldChange(routed.absolute("/stew"))
        viewModel.onImport()
        awaitState(viewModel) { parseStarted.count == 0L }
        assertIs<Screen.InProgress>(viewModel.state.value.screen)

        viewModel.onCancel()
        assertEquals(Screen.Input(), viewModel.state.value.screen, "Input at once, while the parse is still stalled")
        assertFalse(tracing.finished)

        releaseParse.countDown()
        awaitState(viewModel) { tracing.finished }
        assertTrue(tracing.cancelled, "the importer coroutine ends cancelled")
        assertNull(tracing.result, "no result, so no Failed applied")
        assertEquals(Screen.Input(), viewModel.state.value.screen)
        assertEquals(0L, streams.single().produced, "the capped stream stopped at its first read")
    }

    @Test
    fun `cancel during a maximal density parse returns to input at once`() {
        val dense = ("<html><body>" + "<b>x</b>".repeat(99_995) + "</body></html>").toByteArray()
        val wire = ImportFixtures.gzip(dense)
        assertTrue(wire.size < 64 * 1024, "the dense page fits the raw cap gzipped")
        routed.route("/dense") {
            MockResponse().setHeader("Content-Type", "text/html").setHeader("Content-Encoding", "gzip").setBody(Buffer().write(wire))
        }
        val tracing = Tracing(importer())
        val viewModel = ImportViewModel(tracing)
        viewModel.onFieldChange(routed.absolute("/dense"))
        viewModel.onImport()
        awaitState(viewModel) { parseStarted.count == 0L }
        viewModel.onCancel()
        assertEquals(Screen.Input(), viewModel.state.value.screen)
        awaitState(viewModel) { tracing.finished }
        assertEquals(Screen.Input(), viewModel.state.value.screen, "a late result or failure is never applied")
        assertEquals(NoticeKind.IMPORT_CANCELLED, viewModel.state.value.notice?.kind)
    }
}
