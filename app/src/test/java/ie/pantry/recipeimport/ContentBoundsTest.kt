package ie.pantry.recipeimport

import ie.pantry.data.gateway.BodyEncoding
import ie.pantry.data.gateway.FetchedBody
import ie.pantry.testutil.TestGateways
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.jsoup.nodes.Document
import org.junit.After
import org.junit.Before

class ContentBoundsTest {

    private lateinit var server: MockWebServer
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private val routes = ConcurrentHashMap<String, () -> MockResponse>()
    private val streams = mutableListOf<CappedInputStream>()
    private val seams = ImportSeams(onParse = {}, onStream = { streams += it })

    @Before
    fun setUp() {
        server = TestGateways.server()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
                return routes[path]?.invoke() ?: MockResponse().setResponseCode(404)
            }
        }
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun parse(
        bytes: ByteArray,
        mediaType: String? = "text/html",
        encoding: BodyEncoding = BodyEncoding.IDENTITY,
        cap: Long = DECOMPRESSED_CAP_BYTES,
        onStream: (CappedInputStream) -> Unit = {},
    ): Document = BoundedHtmlReader.parse(FetchedBody(bytes, encoding, mediaType), "https://example.ie/", cap, Job(), onStream)

    private fun failureOf(block: () -> Unit): ImportException {
        try {
            block()
        } catch (e: Exception) {
            return importFailure(e)
        }
        throw AssertionError("expected the parse to fail")
    }

    @Test
    fun `isHtml accepts html and xhtml with any parameters and case`() {
        for (type in listOf("text/html", "TEXT/HTML", "text/html ; charset=\"utf-8\"", "application/xhtml+xml")) {
            assertTrue(BoundedHtmlReader.isHtml(type), type)
        }
    }

    @Test
    fun `isHtml rejects other types a near miss and a missing type`() {
        for (type in listOf("application/json", "image/png", "application/pdf", "text/htmlx", null)) {
            assertFalse(BoundedHtmlReader.isHtml(type), type.toString())
        }
    }

    @Test
    fun `a gzip bomb stops at the cap with produced at most cap plus one buffer`() {
        val bomb = ImportFixtures.bomb(16L * 1024 * 1024)
        var stream: CappedInputStream? = null
        val error = failureOf { parse(bomb, encoding = BodyEncoding.GZIP, onStream = { stream = it }) }
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, error.kind)
        assertTrue(stream!!.produced <= DECOMPRESSED_CAP_BYTES + 8192, "produced ${stream!!.produced}")
    }

    @Test
    fun `a body of exactly the cap parses and one byte over is too large`() {
        val size = DECOMPRESSED_CAP_BYTES.toInt()
        assertNotNull(parse(ImportFixtures.htmlOfSize(size)).body())
        val error = failureOf { parse(ImportFixtures.htmlOfSize(size + 1)) }
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, error.kind)
        val gzipped = failureOf { parse(ImportFixtures.gzip(ImportFixtures.htmlOfSize(size + 1)), encoding = BodyEncoding.GZIP) }
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, gzipped.kind)
    }

    @Test
    fun `exactly 200000 tag bytes parse and one more is too large`() {
        fun tags(count: Int): ByteArray = ImportFixtures.gzip(ByteArray(count) { '<'.code.toByte() })
        assertNotNull(parse(tags(200_000), encoding = BodyEncoding.GZIP))
        val error = failureOf { parse(tags(200_001), encoding = BodyEncoding.GZIP) }
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, error.kind)
    }

    @Test
    fun `windows 1252 declared by meta decodes correctly`() {
        val text = parse(ImportFixtures.page("windows-1252-meta.html")).text()
        assertTrue(text.contains("½ tsp salt"), text)
        assertTrue(text.contains("crème fraîche"), text)
    }

    @Test
    fun `windows 1252 declared only by the header decodes correctly`() {
        val text = parse(ImportFixtures.page("windows-1252-header.html"), mediaType = "text/html; charset=windows-1252").text()
        assertTrue(text.contains("½ tsp salt"), text)
        assertTrue(text.contains("crème fraîche"), text)
    }

    @Test
    fun `an unknown header charset falls back to detection`() {
        val text = parse(ImportFixtures.page("windows-1252-meta.html"), mediaType = "text/html; charset=bogus").text()
        assertTrue(text.contains("½ tsp salt"), text)
        assertEquals(null, BoundedHtmlReader.headerCharset("text/html; charset=bogus"))
        assertEquals(null, BoundedHtmlReader.headerCharset("text/html; charset=\"bad name!\""))
        assertEquals("utf-8", BoundedHtmlReader.headerCharset("text/html ; charset=\"utf-8\""))
    }

    @Test
    fun `a truncated gzip body fails as unreadable content`() {
        val gzipped = ImportFixtures.gzip(ImportFixtures.page("windows-1252-meta.html"))
        val truncated = gzipped.copyOf(gzipped.size / 2)
        val error = failureOf { parse(truncated, encoding = BodyEncoding.GZIP) }
        assertEquals(ImportException.Kind.UNREADABLE_CONTENT, error.kind)
    }

    @Test
    fun `depth 64 is within the bound and 65 is not`() {
        assertTrue(JsonDepth.withinBound(ImportFixtures.deepJsonLd(64, viaGraph = false)))
        assertFalse(JsonDepth.withinBound(ImportFixtures.deepJsonLd(65, viaGraph = false)))
    }

    @Test
    fun `graph nesting counts an object plus an array`() {
        assertTrue(JsonDepth.withinBound(ImportFixtures.deepJsonLd(64, viaGraph = true)))
        assertFalse(JsonDepth.withinBound(ImportFixtures.deepJsonLd(65, viaGraph = true)))
        assertTrue(JsonDepth.withinBound("""{"@graph":[{"@type":"Recipe"}]}""", bound = 3))
        assertFalse(JsonDepth.withinBound("""{"@graph":[{"@type":"Recipe"}]}""", bound = 2))
    }

    @Test
    fun `depths of ten thousand and one million are rejected without stack overflow`() {
        for (depth in listOf(10_000, 1_000_000)) {
            assertFalse(JsonDepth.withinBound(ImportFixtures.deepJsonLd(depth, viaGraph = false)), "depth $depth")
            assertFalse(JsonDepth.withinBound(ImportFixtures.deepJsonLd(depth, viaGraph = true)), "graph depth $depth")
        }
    }

    @Test
    fun `brackets inside strings and after escaped quotes are not counted`() {
        val inString = "{\"a\":\"" + "[".repeat(100) + "\",\"b\":\"\\\"{\"}"
        assertTrue(JsonDepth.withinBound(inString), inString.take(40))
        val escaped = "{\"b\":\"\\\"" + "[".repeat(100) + "\"}"
        assertTrue(JsonDepth.withinBound(escaped))
        assertFalse(JsonDepth.withinBound("{\"a\":" + "[".repeat(70) + "]".repeat(70) + "}"))
    }

    // ---- T19: importer-level bounds ----

    private fun serve(path: String, body: ByteArray, contentType: String = "text/html", gzip: Boolean = false) {
        val wire = if (gzip) ImportFixtures.gzip(body) else body
        routes[path] = {
            val response = MockResponse().setHeader("Content-Type", contentType).setBody(Buffer().write(wire))
            if (gzip) response.setHeader("Content-Encoding", "gzip")
            response
        }
    }

    private fun serveRaw(path: String, gzipped: ByteArray) {
        routes[path] = {
            MockResponse().setHeader("Content-Type", "text/html").setHeader("Content-Encoding", "gzip").setBody(Buffer().write(gzipped))
        }
    }

    private val acceptAnyImage: (ByteArray) -> DraftImage = { DraftImage.Validated(it, 1, 1) }

    private suspend fun import(path: String = "/page"): ImportResult {
        val importer = ImportFixtures.importer(server, imageCheck = acceptAnyImage, seams = seams)
        val url = (ImportUrl.check(TestGateways.urlOf(server, path)) as UrlCheck.Valid).url
        return importer.importRecipe(url) {}
    }

    private fun kindOf(result: ImportResult): ImportException.Kind? = (result as? ImportResult.Failed)?.error?.kind

    private fun ld(json: String) = "<script type=\"application/ld+json\">$json</script>"

    private val microdata =
        """<div itemscope itemtype="https://schema.org/Recipe"><h1 itemprop="name">Micro</h1><span itemprop="recipeIngredient">micro lamb</span></div>"""

    private fun page(vararg parts: String) = "<html><head>${parts.joinToString("")}</head><body></body></html>".toByteArray()

    @Test
    fun `a gzip bomb page ends in CONTENT_TOO_LARGE with produced at most cap plus one buffer`() = runTest {
        val bomb = ImportFixtures.bomb(16L * 1024 * 1024)
        assertTrue(bomb.size < 64 * 1024, "the bomb fits the raw cap")
        routes["/page"] = {
            MockResponse().setHeader("Content-Type", "text/html").setHeader("Content-Encoding", "gzip").setBody(Buffer().write(bomb))
        }
        val result = import()
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, kindOf(result))
        assertTrue(streams.single().produced <= DECOMPRESSED_CAP_BYTES + 8192, "produced ${streams.single().produced}")
    }

    @Test
    fun `the size and density boundaries hold through the importer`() = runTest {
        val size = DECOMPRESSED_CAP_BYTES.toInt()
        serve("/page", ImportFixtures.htmlOfSize(size), gzip = true)
        assertTrue(kindOf(import()) != ImportException.Kind.CONTENT_TOO_LARGE, "10 MiB exactly is allowed")
        serve("/page", ImportFixtures.htmlOfSize(size + 1), gzip = true)
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, kindOf(import()))

        serve("/page", ByteArray(200_000) { '<'.code.toByte() }, gzip = true)
        assertTrue(kindOf(import()) != ImportException.Kind.CONTENT_TOO_LARGE, "200000 tag bytes are allowed")
        serve("/page", ByteArray(200_001) { '<'.code.toByte() }, gzip = true)
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, kindOf(import()))
    }

    @Test
    fun `json ld at depth 64 is parsed and 65 and 1000 are skipped through the importer`() = runTest {
        for (viaGraph in listOf(false, true)) {
            serve("/page", page(ld(ImportFixtures.deepJsonLd(64, viaGraph)), microdata))
            assertEquals(ExtractionTier.JSON_LD, assertIs<ImportResult.Drafted>(import()).draft.tier, "depth 64 graph=$viaGraph")
            for (depth in listOf(65, 1000)) {
                serve("/page", page(ld(ImportFixtures.deepJsonLd(depth, viaGraph)), microdata))
                val draft = assertIs<ImportResult.Drafted>(import(), "depth $depth graph=$viaGraph").draft
                assertEquals(ExtractionTier.MICRODATA, draft.tier, "depth $depth graph=$viaGraph")
            }
        }
    }

    @Test
    fun `the 512 KiB block boundary holds and a flat over cap block is skipped`() = runTest {
        val head = """{"@type":"Recipe","name":"Big","recipeIngredient":["json lamb"],"pad":""""
        fun block(length: Int) = head + "a".repeat(length - head.length - 2) + "\"}"
        serve("/page", page(ld(block(JSON_LD_BLOCK_CAP_CHARS)), microdata), gzip = true)
        assertEquals(ExtractionTier.JSON_LD, assertIs<ImportResult.Drafted>(import()).draft.tier)

        serve("/page", page(ld(block(JSON_LD_BLOCK_CAP_CHARS + 1)), microdata), gzip = true)
        assertEquals(ExtractionTier.MICRODATA, assertIs<ImportResult.Drafted>(import()).draft.tier)

        val flat = "[" + "0,".repeat(JSON_LD_BLOCK_CAP_CHARS / 2 + 10) + "0]"
        assertTrue(flat.length > JSON_LD_BLOCK_CAP_CHARS)
        serve("/page", page(ld(flat), microdata), gzip = true)
        assertEquals(ExtractionTier.MICRODATA, assertIs<ImportResult.Drafted>(import()).draft.tier)
    }

    @Test
    fun `a Recipe in the twenty first block is not examined through the importer`() = runTest {
        serve("/page", ImportFixtures.page("twenty-one-blocks.html"))
        val draft = assertIs<ImportResult.Drafted>(import()).draft
        assertEquals(ExtractionTier.HEURISTIC, draft.tier)
        assertEquals(listOf("heuristic lamb", "heuristic onion"), draft.ingredientLines)
    }

    @Test
    fun `page scripts handlers and iframes cause no request beyond the page and one image`() = runTest {
        serve("/page", ImportFixtures.page("scripts-handlers-iframes.html"))
        serve("/pic.jpg", ByteArray(100) { 1 }, contentType = "image/jpeg")
        val draft = assertIs<ImportResult.Drafted>(import()).draft
        assertIs<DraftImage.Validated>(draft.image)
        assertEquals(setOf("/page", "/pic.jpg"), hits.keys.toSet())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a low entropy image is not refused by the page density bound`() = runTest {
        serve("/page", page(ld("""{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"],"image":"/pic.jpg"}""")))
        routes["/pic.jpg"] = {
            MockResponse().setHeader("Content-Type", "image/jpeg").setHeader("Content-Encoding", "gzip")
                .setBody(Buffer().write(ImportFixtures.gzip(ByteArray(300_000) { '<'.code.toByte() })))
        }
        val draft = assertIs<ImportResult.Drafted>(import()).draft
        assertIs<DraftImage.Validated>(draft.image)
        assertEquals(300_000, (draft.image as DraftImage.Validated).bytes.size)
    }
}
