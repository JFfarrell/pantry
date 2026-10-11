package ie.pantry.recipeimport

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.testutil.TestGateways
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test

/** R2 AC1 to AC6, importer side: one request, the media-type and encoding gates, and the gateway failures. */
class RecipeImporterFetchTest {

    private lateinit var server: MockWebServer
    private val toClose = mutableListOf<MockWebServer>()
    private val parses = AtomicInteger()
    private val seams = ImportSeams(onParse = { parses.incrementAndGet() }, onStream = {})

    @Before
    fun setUp() {
        server = TestGateways.server()
        toClose += server
    }

    @After
    fun tearDown() {
        toClose.forEach { runCatching { it.shutdown() } }
    }

    private fun url(path: String = "/stew"): ValidatedUrl =
        (ImportUrl.check(TestGateways.urlOf(server, path)) as UrlCheck.Valid).url

    private suspend fun import(importer: RecipeImporter, target: ValidatedUrl = url()): ImportResult =
        importer.importRecipe(target) {}

    private fun failure(result: ImportResult): ImportException = assertIs<ImportResult.Failed>(result).error

    private val heuristic get() = ImportFixtures.page("heuristic.html")

    @Test
    fun `a valid url makes exactly one page request through the gateway`() = runTest {
        ImportFixtures.enqueuePage(server, heuristic)
        val target = url("/stew")

        val result = import(ImportFixtures.importer(server, seams = seams), target)

        assertEquals(1, server.requestCount)
        assertEquals("/stew", server.takeRequest().path)
        val draft = assertIs<ImportResult.Drafted>(result).draft
        assertEquals(target.value, draft.sourceUrl)
        assertEquals(1, parses.get())
    }

    @Test
    fun `timeout 404 and refused address give FETCH_FAILED with the gateway category`() = runTest {
        server.dispatcher = TestGateways.answerEvery { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val timeout = failure(import(ImportFixtures.importer(server)))
        assertEquals(ImportException.Kind.FETCH_FAILED, timeout.kind)
        assertEquals(Category.TIMEOUT, timeout.gatewayCategory)

        server.dispatcher = TestGateways.answerEvery { MockResponse().setResponseCode(404) }
        val missing = failure(import(ImportFixtures.importer(server)))
        assertEquals(Category.HTTP_STATUS, missing.gatewayCategory)
        assertEquals(404, missing.statusCode)

        val other = TestGateways.server().also { toClose += it }
        val refused = failure(import(ImportFixtures.importer(server), (ImportUrl.check(TestGateways.urlOf(other, "/")) as UrlCheck.Valid).url))
        assertEquals(Category.ADDRESS_REFUSED, refused.gatewayCategory)
        assertEquals(0, other.requestCount)
        assertEquals(0, parses.get())
    }

    @Test
    fun `json png pdf and a missing content type are NOT_HTML with no parse`() = runTest {
        for (type in listOf("application/json", "image/png", "application/pdf", null)) {
            ImportFixtures.enqueuePage(server, heuristic, contentType = type)
            val error = failure(import(ImportFixtures.importer(server, seams = seams)))
            assertEquals(ImportException.Kind.NOT_HTML, error.kind, type.toString())
        }
        assertEquals(0, parses.get())
    }

    @Test
    fun `xhtml upper case and parameterised html are accepted and a near miss is not`() = runTest {
        for (type in listOf("application/xhtml+xml", "TEXT/HTML", "text/html ; charset=\"utf-8\"")) {
            ImportFixtures.enqueuePage(server, heuristic, contentType = type)
            assertIs<ImportResult.Drafted>(import(ImportFixtures.importer(server, seams = seams)), type)
        }
        assertEquals(3, parses.get())
        ImportFixtures.enqueuePage(server, heuristic, contentType = "text/htmlx")
        assertEquals(ImportException.Kind.NOT_HTML, failure(import(ImportFixtures.importer(server, seams = seams))).kind)
        assertEquals(3, parses.get())
    }

    @Test
    fun `brotli html is UNSUPPORTED_ENCODING with no parse`() = runTest {
        ImportFixtures.enqueuePage(server, heuristic, contentEncoding = "br")
        assertEquals(ImportException.Kind.UNSUPPORTED_ENCODING, failure(import(ImportFixtures.importer(server, seams = seams))).kind)
        assertEquals(0, parses.get())
    }

    @Test
    fun `non html with brotli reports NOT_HTML`() = runTest {
        ImportFixtures.enqueuePage(server, heuristic, contentType = "application/json", contentEncoding = "br")
        assertEquals(ImportException.Kind.NOT_HTML, failure(import(ImportFixtures.importer(server, seams = seams))).kind)
        assertEquals(0, parses.get())
    }

    @Test
    fun `truncated invalid and empty gzip bodies give UNREADABLE_CONTENT`() = runTest {
        val valid = ImportFixtures.gzip(heuristic)
        val bodies = listOf(valid.copyOf(valid.size / 2), ByteArray(64) { 7 }, ByteArray(0))
        for (body in bodies) {
            ImportFixtures.enqueuePage(server, body, contentEncoding = "gzip")
            val result = import(ImportFixtures.importer(server, seams = seams))
            assertEquals(ImportException.Kind.UNREADABLE_CONTENT, failure(result).kind, "body of ${body.size} bytes")
        }
    }

    @Test
    fun `an empty identity body is NOT_RECOGNISED`() = runTest {
        ImportFixtures.enqueuePage(server, ByteArray(0))
        assertEquals(ImportException.Kind.NOT_RECOGNISED, failure(import(ImportFixtures.importer(server, seams = seams))).kind)
    }

    @Test
    fun `cancelling a page fetch that never responds cancels the gateway call`() = runTest {
        server.dispatcher = TestGateways.answerEvery { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val listener = TestGateways.RecordingEventListener()
        val importer = ImportFixtures.importer(TestGateways.against(server, listener = listener))
        var result: ImportResult? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) { result = import(importer) }

        listener.awaitCallStart(1)
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertNull(result)
        withContext(Dispatchers.Default) {
            val deadline = System.currentTimeMillis() + 500
            while (listener.callFailedTimesMillis.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        }
        assertFalse(listener.callFailedTimesMillis.isEmpty(), "callFailed must follow the cancel")
        assertEquals(0, parses.get())
    }

    @Test
    fun `the importer reports FETCHING_PAGE first`() = runTest {
        ImportFixtures.enqueuePage(server, heuristic)
        val phases = mutableListOf<ImportPhase>()
        ImportFixtures.importer(server).importRecipe(url()) { phases += it }
        assertEquals(listOf(ImportPhase.FETCHING_PAGE), phases)
    }
}
