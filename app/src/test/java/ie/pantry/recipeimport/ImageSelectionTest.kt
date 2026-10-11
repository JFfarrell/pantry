package ie.pantry.recipeimport

import ie.pantry.testutil.ImageFixtures
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.Sentinels
import ie.pantry.testutil.TestGateways
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Before
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageSelectionTest {

    private val pageUrl = "https://example.ie/recipes/stew"

    private lateinit var server: MockWebServer
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private val routes = ConcurrentHashMap<String, () -> MockResponse>()
    private val parses = AtomicInteger()
    private val streams = mutableListOf<CappedInputStream>()
    private val seams = ImportSeams(onParse = { parses.incrementAndGet() }, onStream = { streams += it })

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

    private fun select(html: String): Candidate {
        val document = Jsoup.parse(html, pageUrl)
        return ImageCandidateSelector.select(document, JsonLdExtractor.recipes(document))
    }

    private fun selectFixture(name: String): Candidate = select(String(ImportFixtures.page(name), Charsets.UTF_8))

    private fun url(candidate: Candidate): String = assertIs<Candidate.Url>(candidate).value

    private fun ld(json: String) = """<script type="application/ld+json">$json</script>"""

    private fun recipeWithImage(image: String) =
        ld("""{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"],"image":$image}""")

    @Test
    fun `json ld image forms are tier one candidates`() {
        assertEquals("https://example.ie/a.jpg", url(select(recipeWithImage("\"https://example.ie/a.jpg\""))))
        assertEquals("https://example.ie/b.jpg", url(select(recipeWithImage("[\"https://example.ie/b.jpg\"]"))))
        assertEquals("https://example.ie/c.jpg", url(select(recipeWithImage("""{"@type":"ImageObject","url":"https://example.ie/c.jpg"}"""))))
        assertEquals("https://example.ie/d.jpg", url(select(recipeWithImage("""{"contentUrl":"https://example.ie/d.jpg"}"""))))
        assertEquals("https://example.ie/json.jpg", url(selectFixture("image-jsonld.html")))
    }

    @Test
    fun `og image is used when no json ld image is stated`() {
        assertEquals("https://example.ie/og.jpg", url(selectFixture("image-og.html")))
        val withoutImage = ld("""{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"]}""") +
            """<meta property="OG:Image" content="https://example.ie/og.jpg"><img src="/inline.jpg">"""
        assertEquals("https://example.ie/og.jpg", url(select(withoutImage)))
    }

    @Test
    fun `the inline tier picks the largest declared img else the first`() {
        assertEquals("https://example.ie/large.jpg", url(selectFixture("image-inline.html")))
        assertEquals("https://example.ie/a.jpg", url(select("""<img src="/a.jpg"><img src="/b.jpg" width="5">""")))
        assertEquals("https://example.ie/first.jpg", url(select("""<img src="/first.jpg" width="50" height="50"><img src="/second.jpg" width="50" height="50">""")))
        assertEquals("https://example.ie/skip.jpg", url(select("""<img src=" "><img src="/skip.jpg">""")))
    }

    @Test
    fun `a relative candidate resolves against base href else the page url`() {
        assertEquals("https://cdn.example.ie/assets/stew.jpg", url(selectFixture("image-base-href.html")))
        assertEquals("https://example.ie/recipes/pic.jpg", url(select("""<img src="pic.jpg">""")))
        assertEquals("https://example.ie/recipes/pic.jpg", url(select(recipeWithImage("\"pic.jpg\""))))
        assertEquals("https://example.ie/root.jpg", url(select(recipeWithImage("\"/root.jpg\""))))
        assertEquals("https://example.ie/proto.jpg", url(select(recipeWithImage("\"//example.ie/proto.jpg\""))))
    }

    @Test
    fun `a title only json ld Recipe still supplies the tier one image`() {
        val html = ld("""{"@type":"Recipe","name":"Only a title","image":"https://example.ie/tier1.jpg"}""") +
            """<meta property="og:image" content="https://example.ie/og.jpg">"""
        assertEquals("https://example.ie/tier1.jpg", url(select(html)))
    }

    @Test
    fun `a data uri candidate is not http`() {
        assertIs<Candidate.NotHttp>(select("""<meta property="og:image" content="data:image/png;base64,AAAA">"""))
        assertIs<Candidate.NotHttp>(select(recipeWithImage("\"data:image/png;base64,AAAA\"")))
        assertIs<Candidate.NotHttp>(select("""<img src="data:image/png;base64,AAAA">"""))
        assertIs<Candidate.NotHttp>(select(recipeWithImage("\"ftp://example.ie/a.jpg\"")))
    }

    @Test
    fun `a page whose only og image is blank has no candidate`() {
        assertIs<Candidate.None>(select("""<meta property="og:image" content="   ">"""))
        assertIs<Candidate.None>(select("""<p>No images at all</p>"""))
        assertIs<Candidate.None>(select(recipeWithImage("\"  \"")))
    }

    @Test
    fun `a malformed json ld image string is a bad url`() {
        assertIs<Candidate.BadUrl>(select(recipeWithImage("\"https://example.ie/a b.jpg\"")))
        assertIs<Candidate.BadUrl>(select(recipeWithImage("\"https://example.ie/a|b.jpg\"")))
    }

    @Test
    fun `a candidate url prints no url`() {
        val candidate = select(recipeWithImage("\"${Sentinels.URL}/pic.jpg\""))
        assertIs<Candidate.Url>(candidate)
        assertFalse(candidate.toString().contains("sentinel"), candidate.toString())
        assertFalse(candidate.toString().contains("SENTINEL"), candidate.toString())
    }

    // ---- T18: the importer's image phase ----

    private fun serverUrl(path: String) = TestGateways.urlOf(server, path)

    private fun validated(path: String) = (ImportUrl.check(serverUrl(path)) as UrlCheck.Valid).url

    private fun requests(path: String): Int = hits[path]?.get() ?: 0

    /** A page whose JSON-LD `Recipe` names [image] and carries one ingredient line. */
    private fun pageWithImage(image: String): ByteArray =
        "<html><head>${recipeWithImage("\"$image\"")}</head><body></body></html>".toByteArray()

    private fun servePage(body: ByteArray, path: String = "/stew") {
        routes[path] = {
            MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(Buffer().write(body))
        }
    }

    private fun serveImage(
        path: String,
        body: ByteArray,
        contentType: String? = "image/jpeg",
        encoding: String? = null,
    ) {
        routes[path] = {
            val response = MockResponse().setBody(Buffer().write(body))
            if (contentType != null) response.setHeader("Content-Type", contentType)
            if (encoding != null) response.setHeader("Content-Encoding", encoding)
            response
        }
    }

    private fun realImporter(
        gatewayPolicy: ie.pantry.data.gateway.GatewayPolicy = TestGateways.FAST,
        validator: ImageValidator = ImageValidator(),
    ) = ImportFixtures.importer(
        TestGateways.against(server, policy = gatewayPolicy),
        MutableClock(),
        validator::validate,
        seams,
    )

    private suspend fun draftOf(importer: RecipeImporter, path: String = "/stew"): ImportDraft =
        assertIs<ImportResult.Drafted>(importer.importRecipe(validated(path)) {}).draft

    @Test
    fun `a valid jpeg candidate is fetched once and carried as bytes with no file written`() = runTest {
        val jpeg = ImageFixtures.jpeg(40, 30)
        servePage(pageWithImage(serverUrl("/pic.jpg")))
        serveImage("/pic.jpg", jpeg)
        val filesDir = RuntimeEnvironment.getApplication().filesDir

        val draft = draftOf(realImporter())

        assertEquals(1, requests("/pic.jpg"))
        val image = assertIs<DraftImage.Validated>(draft.image)
        assertContentEquals(jpeg, image.bytes)
        assertEquals(40 to 30, image.widthPx to image.heightPx)
        assertTrue(filesDir.listFiles().orEmpty().isEmpty(), "files under filesDir: ${filesDir.listFiles()?.toList()}")
    }

    @Test
    fun `a page with no candidate makes no image request`() = runTest {
        servePage(ImportFixtures.page("heuristic.html"))
        val draft = draftOf(realImporter())
        assertEquals(DraftImage.NoImage(NoImageCause.NO_CANDIDATE), draft.image)
        assertEquals(1, hits.values.sumOf { it.get() })
    }

    @Test
    fun `a declined page makes no image request`() = runTest {
        servePage("""<html><head><title>No recipe</title><meta property="og:image" content="${serverUrl("/pic.jpg")}"></head></html>""".toByteArray())
        serveImage("/pic.jpg", ImageFixtures.jpeg(10, 10))
        val result = realImporter().importRecipe(validated("/stew")) {}
        assertEquals(ImportException.Kind.NOT_RECOGNISED, assertIs<ImportResult.Failed>(result).error.kind)
        assertEquals(0, requests("/pic.jpg"))
    }

    @Test
    fun `html bytes labelled as jpeg give no image after one request and no parse`() = runTest {
        servePage(pageWithImage(serverUrl("/pic.jpg")))
        serveImage("/pic.jpg", "<html><body>not a picture</body></html>".toByteArray(), contentType = "image/jpeg")
        val draft = draftOf(realImporter())
        assertEquals(1, requests("/pic.jpg"))
        assertEquals(1, parses.get(), "only the page is parsed")
        assertEquals(DraftImage.NoImage(NoImageCause.NOT_AN_IMAGE), draft.image)
    }

    @Test
    fun `a gzip bomb image and a brotli image give no image with the draft produced`() = runTest {
        servePage(pageWithImage(serverUrl("/pic.jpg")))
        serveImage("/pic.jpg", ImportFixtures.bomb(16L * 1024 * 1024), encoding = "gzip")
        val bombed = draftOf(realImporter())
        assertEquals(DraftImage.NoImage(NoImageCause.UNREADABLE), bombed.image)
        assertEquals(2, streams.size, "page stream, then image stream")
        assertTrue(streams[1].produced <= DECOMPRESSED_CAP_BYTES + 8192, "produced ${streams[1].produced}")

        streams.clear()
        serveImage("/pic.jpg", ImageFixtures.jpeg(10, 10), encoding = "br")
        val brotli = draftOf(realImporter())
        assertEquals(DraftImage.NoImage(NoImageCause.UNREADABLE), brotli.image)
        assertEquals(1, streams.size, "no image stream is opened for br")
    }

    @Test
    fun `corrupt gzip on the image gives no image`() = runTest {
        servePage(pageWithImage(serverUrl("/pic.jpg")))
        val gzipped = ImportFixtures.gzip(ImageFixtures.jpeg(10, 10))
        serveImage("/pic.jpg", gzipped.copyOf(gzipped.size / 2), encoding = "gzip")
        assertEquals(DraftImage.NoImage(NoImageCause.UNREADABLE), draftOf(realImporter()).image)
        serveImage("/pic.jpg", ByteArray(40) { 3 }, encoding = "gzip")
        assertEquals(DraftImage.NoImage(NoImageCause.UNREADABLE), draftOf(realImporter()).image)
    }

    @Test
    fun `a jpeg labelled text plain is accepted`() = runTest {
        servePage(pageWithImage(serverUrl("/pic.jpg")))
        serveImage("/pic.jpg", ImageFixtures.jpeg(20, 20), contentType = "text/plain")
        assertIs<DraftImage.Validated>(draftOf(realImporter()).image)
        serveImage("/pic.jpg", ImageFixtures.jpeg(20, 20), contentType = null)
        assertIs<DraftImage.Validated>(draftOf(realImporter()).image)
    }

    @Test
    fun `an image 404 a data uri and a bad url give no image with no lower tier tried`() = runTest {
        val lower = """<meta property="og:image" content="${serverUrl("/og.jpg")}"><img src="${serverUrl("/inline.jpg")}">"""
        serveImage("/og.jpg", ImageFixtures.jpeg(10, 10))
        serveImage("/inline.jpg", ImageFixtures.jpeg(10, 10))
        fun page(image: String) = "<html><head>${recipeWithImage("\"$image\"")}$lower</head></html>".toByteArray()

        servePage(page(serverUrl("/missing.jpg")))
        assertEquals(DraftImage.NoImage(NoImageCause.FETCH_FAILED), draftOf(realImporter()).image)
        servePage(page("data:image/png;base64,AAAA"))
        assertEquals(DraftImage.NoImage(NoImageCause.NOT_HTTP), draftOf(realImporter()).image)
        servePage(page(serverUrl("/a b.jpg")))
        assertEquals(DraftImage.NoImage(NoImageCause.BAD_URL), draftOf(realImporter()).image)
        assertEquals(0, requests("/og.jpg"))
        assertEquals(0, requests("/inline.jpg"))
    }

    @Test
    fun `a microdata itemprop image is ignored`() = runTest {
        servePage(
            """<div itemscope itemtype="https://schema.org/Recipe"><h1 itemprop="name">Stew</h1><span itemprop="recipeIngredient">1 egg</span><meta itemprop="image" content="${serverUrl("/pic.jpg")}"><link itemprop="image" href="${serverUrl("/pic2.jpg")}"></div>""".toByteArray(),
        )
        serveImage("/pic.jpg", ImageFixtures.jpeg(10, 10))
        val draft = draftOf(realImporter())
        assertEquals(ExtractionTier.MICRODATA, draft.tier)
        assertEquals(DraftImage.NoImage(NoImageCause.NO_CANDIDATE), draft.image)
        assertEquals(0, requests("/pic.jpg") + requests("/pic2.jpg"))
    }

    @Test
    fun `the png bomb through the importer decodes at a reduced sample size`() = runTest {
        servePage(pageWithImage(serverUrl("/bomb.png")))
        val bomb = ImageFixtures.pngBomb(9_000, 9_000)
        assertTrue(bomb.size < 1024 * 1024, "the bomb is ${bomb.size} bytes")
        serveImage("/bomb.png", bomb, contentType = "image/png")
        var sampleSize = 0
        var decodedWidth = Int.MAX_VALUE
        val validator = ImageValidator(maxEdgePx = 512) { sample, width, _ -> sampleSize = sample; decodedWidth = width }
        val draft = draftOf(realImporter(TestGateways.FAST.copy(maxBodyBytes = 1024L * 1024), validator))
        assertTrue(sampleSize > 1, "sample size $sampleSize")
        assertTrue(decodedWidth < 2 * 512, "decoded width $decodedWidth")
        assertIs<DraftImage.Validated>(draft.image)
    }

    @Test
    fun `the importer reports FETCHING_PICTURE only when an image fetch starts`() = runTest {
        servePage(pageWithImage(serverUrl("/pic.jpg")))
        serveImage("/pic.jpg", ImageFixtures.jpeg(10, 10))
        val withImage = mutableListOf<ImportPhase>()
        realImporter().importRecipe(validated("/stew")) { withImage += it }
        assertEquals(listOf(ImportPhase.FETCHING_PAGE, ImportPhase.FETCHING_PICTURE), withImage)

        servePage(ImportFixtures.page("heuristic.html"), path = "/plain")
        val without = mutableListOf<ImportPhase>()
        realImporter().importRecipe(validated("/plain")) { without += it }
        assertEquals(listOf(ImportPhase.FETCHING_PAGE), without)
    }

    @Test
    fun `an importer built through the public constructor validates a jpeg`() = runTest {
        servePage(pageWithImage(serverUrl("/pic.jpg")))
        serveImage("/pic.jpg", ImageFixtures.jpeg(30, 20))
        val importer = RecipeImporter(TestGateways.against(server), MutableClock())
        assertIs<DraftImage.Validated>(draftOf(importer).image)
    }
}
