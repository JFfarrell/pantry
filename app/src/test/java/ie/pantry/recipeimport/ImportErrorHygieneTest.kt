package ie.pantry.recipeimport

import ie.pantry.data.gateway.CallType
import ie.pantry.data.gateway.ExternalDataGateway
import ie.pantry.data.gateway.GatewayException
import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.testutil.ImageFixtures
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.Sentinels
import ie.pantry.testutil.TestGateways
import ie.pantry.testutil.assertNoSentinel
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Instant
import java.util.zip.ZipException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.jsoup.helper.ValidationException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportErrorHygieneTest {

    private fun gatewayError(category: Category, status: Int? = null): GatewayException {
        val constructor = GatewayException::class.java.declaredConstructors.single()
        constructor.isAccessible = true
        return constructor.newInstance(category, CallType.PAGE_FETCH, status, 1, null) as GatewayException
    }

    private fun assertContentFree(error: ImportException) {
        error.assertNoSentinel()
        assertNull(error.cause)
        assertEquals(0, error.suppressed.size)
    }

    @Test
    fun `import exception message holds only enum names a status and a class name`() {
        val error = ImportException(ImportException.Kind.FETCH_FAILED, Category.HTTP_STATUS, 503, null)
        assertEquals("Import failure: kind=FETCH_FAILED gateway=HTTP_STATUS status=503 cause=none", error.message)
        val rewrapped = importFailure(ZipException(Sentinels.INGREDIENT))
        assertEquals("Import failure: kind=UNREADABLE_CONTENT gateway=none status=none cause=ZipException", rewrapped.message)
        assertEquals("ZipException", rewrapped.causeType)
        assertEquals(error.message, error.localizedMessage)
        assertTrue(error.toString().endsWith(error.message!!))
    }

    @Test
    fun `import exception has no string constructor parameter`() {
        for (constructor in ImportException::class.java.declaredConstructors) {
            assertFalse(constructor.parameterTypes.any { it == String::class.java })
        }
    }

    @Test
    fun `gateway category is present exactly when the kind is FETCH_FAILED`() {
        val withoutCategory = assertFailsWith<IllegalArgumentException> {
            ImportException(ImportException.Kind.FETCH_FAILED, null, null, null)
        }
        val withCategory = assertFailsWith<IllegalArgumentException> {
            ImportException(ImportException.Kind.NOT_HTML, Category.TIMEOUT, null, null)
        }
        assertEquals("a gateway category goes with FETCH_FAILED and with nothing else", withoutCategory.message)
        assertEquals(withoutCategory.message, withCategory.message)
    }

    @Test
    fun `status code is present exactly when the category is HTTP_STATUS`() {
        val missing = assertFailsWith<IllegalArgumentException> {
            ImportException(ImportException.Kind.FETCH_FAILED, Category.HTTP_STATUS, null, null)
        }
        val extra = assertFailsWith<IllegalArgumentException> {
            ImportException(ImportException.Kind.FETCH_FAILED, Category.TIMEOUT, 500, null)
        }
        assertEquals("a status code goes with HTTP_STATUS and with nothing else", missing.message)
        assertEquals(missing.message, extra.message)
    }

    @Test
    fun `fetchFailure copies category status and stack frames without a cause`() {
        val source = gatewayError(Category.HTTP_STATUS, 404)
        val error = fetchFailure(source)
        assertEquals(ImportException.Kind.FETCH_FAILED, error.kind)
        assertEquals(Category.HTTP_STATUS, error.gatewayCategory)
        assertEquals(404, error.statusCode)
        assertContentEquals(source.stackTrace, error.stackTrace)
        assertContentFree(error)
        val plain = fetchFailure(gatewayError(Category.TIMEOUT))
        assertNull(plain.statusCode)
        assertEquals(Category.TIMEOUT, plain.gatewayCategory)
    }

    @Test
    fun `importFailure finds the too large marker in the chain and suppressed exceptions`() {
        val top = importFailure(ContentTooLargeException())
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, top.kind)
        assertNull(top.causeType)

        val wrapped = importFailure(org.jsoup.UncheckedIOException(ContentTooLargeException()))
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, wrapped.kind)
        assertNull(wrapped.causeType)

        val carrier = RuntimeException("carrier").apply { addSuppressed(ContentTooLargeException()) }
        val suppressed = importFailure(carrier)
        assertEquals(ImportException.Kind.CONTENT_TOO_LARGE, suppressed.kind)
        assertNull(suppressed.causeType)
    }

    @Test
    fun `importFailure re-wraps jsoup serialization and zip exceptions by type`() {
        val causes = listOf(
            ValidationException(Sentinels.INGREDIENT + " SENTINEL"),
            SerializationException("SENTINEL " + Sentinels.URL),
            ZipException("SENTINEL " + Sentinels.TITLE),
        )
        for (cause in causes) {
            val error = importFailure(cause)
            assertEquals(ImportException.Kind.UNREADABLE_CONTENT, error.kind)
            assertEquals(cause.javaClass.simpleName, error.causeType)
            assertNull(error.cause)
            assertEquals(0, error.suppressed.size)
            assertContentEquals(cause.stackTrace, error.stackTrace)
            assertContentFree(error)
        }
    }

    @Test
    fun `importFailure terminates on a cause cycle`() {
        val a = IOException("a")
        val b = IOException("b", a)
        a.initCause(b)
        a.addSuppressed(a)
        val error = importFailure(a)
        assertEquals(ImportException.Kind.UNREADABLE_CONTENT, error.kind)
    }

    @Test
    fun `draft types print no content`() {
        val draft = ImportDraft(
            title = DraftValue.Stated(Sentinels.TITLE),
            ingredientLines = listOf(Sentinels.INGREDIENT),
            method = DraftValue.Stated(listOf(Sentinels.INGREDIENT)),
            cookingTime = DraftValue.Stated(5),
            servings = DraftValue.Absent,
            image = DraftImage.Validated(Sentinels.URL.toByteArray(), 1, 1),
            tier = ExtractionTier.JSON_LD,
            sourceUrl = Sentinels.URL,
            fetchedAt = Instant.EPOCH,
        )
        val texts = listOf(
            draft.toString(),
            draft.image.toString(),
            draft.title.toString(),
            DraftValue.Stated(Sentinels.TITLE).toString(),
            ImportResult.Drafted(draft).toString(),
        )
        for (text in texts) {
            assertFalse(text.contains("SENTINEL"), text)
            assertFalse(text.contains("sentinel"), text)
        }
        assertIs<DraftValue.Stated<*>>(draft.title)
    }

    // ---- T21: every failure path, driven end to end with sentinel content ----

    private val host = "sentinel-9c1e.example"
    private val toClose = mutableListOf<AutoCloseable>()

    @org.junit.After
    fun closeServers() = toClose.forEach { runCatching { it.close() } }

    private fun routed(server: okhttp3.mockwebserver.MockWebServer = TestGateways.server()) =
        RoutedServer(server).also { toClose += it }

    private fun urlAt(server: okhttp3.mockwebserver.MockWebServer, path: String, scheme: String = "http"): ValidatedUrl =
        (ImportUrl.check("$scheme://$host:${server.port}$path") as UrlCheck.Valid).url

    private fun loopback(): List<Any> = listOf(listOf(InetAddress.getByName("127.0.0.1")))

    private fun dnsFor(vararg answers: Any) = TestGateways.RecordingDns(mapOf(host to listOf(*answers)))

    private fun assertClean(error: ImportException) {
        error.assertNoSentinel()
        for (text in listOf(error.toString(), error.message.orEmpty())) {
            assertFalse(text.lowercase().contains("sentinel"), text)
        }
        assertNull(error.cause)
        assertEquals(0, error.suppressed.size)
    }

    private val sentinelPage get() = ImportFixtures.page("sentinel.html")

    private suspend fun fail(gateway: ExternalDataGateway, url: ValidatedUrl, imageCheck: (ByteArray) -> DraftImage = ImageValidator()::validate): ImportException {
        val result = ImportFixtures.importer(gateway, MutableClock(), imageCheck).importRecipe(url) {}
        return assertIs<ImportResult.Failed>(result).error
    }

    @Test
    fun `every import failure path driven by sentinel fixtures carries no sentinel`() = runTest {
        val errors = mutableListOf<ImportException>()
        val plain = routed()
        val exempt = dnsFor(*loopback().toTypedArray())
        val gateway = TestGateways.against(plain.server, dns = exempt)
        fun at(path: String) = urlAt(plain.server, path)

        // Content failures: not HTML, unsupported encoding, unreadable and too large gzip, not recognised.
        plain.page("/notHtml", sentinelPage, contentType = "application/json")
        errors += fail(gateway, at("/notHtml"))
        plain.route("/brotli") { MockResponse().setHeader("Content-Type", "text/html").setHeader("Content-Encoding", "br").setBody(Buffer().write(sentinelPage)) }
        errors += fail(gateway, at("/brotli"))
        val gzipped = ImportFixtures.gzip(sentinelPage)
        plain.route("/truncated") { MockResponse().setHeader("Content-Type", "text/html").setHeader("Content-Encoding", "gzip").setBody(Buffer().write(gzipped.copyOf(gzipped.size / 2))) }
        errors += fail(gateway, at("/truncated"))
        val bomb = ImportFixtures.bomb(16L * 1024 * 1024)
        plain.route("/bomb") { MockResponse().setHeader("Content-Type", "text/html").setHeader("Content-Encoding", "gzip").setBody(Buffer().write(bomb)) }
        errors += fail(gateway, at("/bomb"))
        plain.page("/declined", "<html><head><title>${Sentinels.TITLE}</title></head><body><p>${Sentinels.URL}</p></body></html>".toByteArray())
        errors += fail(gateway, at("/declined"))

        // Every gateway category a fixture can reach.
        plain.route("/silent") { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        errors += fail(gateway, at("/silent"))
        plain.route("/gone") { MockResponse().setResponseCode(404).setBody("SENTINEL body") }
        errors += fail(gateway, at("/gone"))
        plain.route("/broken") { MockResponse().setStatus("HTTP/1.1 500 SENTINEL").setBody("SENTINEL body") }
        errors += fail(gateway, at("/broken"))
        plain.route("/huge") { MockResponse().setBody(Buffer().writeUtf8("SENTINEL".repeat(TestGateways.FAST.maxBodyBytes.toInt() / 8 + 1))) }
        errors += fail(gateway, at("/huge"))
        plain.route("/intent") { MockResponse().setResponseCode(302).setHeader("Location", "intent://SENTINEL/x#Intent;end") }
        errors += fail(gateway, at("/intent"))
        plain.route("/loop") { MockResponse().setResponseCode(302).setHeader("Location", "/loop") }
        errors += fail(gateway, at("/loop"))
        plain.route("/badLocation") { MockResponse().setResponseCode(302).setHeader("Location", "http://[::SENTINEL") }
        errors += fail(gateway, at("/badLocation"))
        val refusing = TestGateways.against(plain.server, dns = dnsFor(listOf(InetAddress.getByName("10.0.0.1"))))
        errors += fail(refusing, urlAt(plain.server, "/anything"))
        val unknown = TestGateways.against(plain.server, dns = dnsFor(UnknownHostException("SENTINEL host")))
        errors += fail(unknown, urlAt(plain.server, "/anything"))
        val https = routed(TestGateways.httpsServer())
        https.page("/untrusted", sentinelPage)
        errors += fail(TestGateways.against(https.server, dns = exempt), urlAt(https.server, "/untrusted", "https"))
        val throwing = TestGateways.against(plain.server, dns = exempt, interceptor = TestGateways.throwingInterceptor(RuntimeException("SENTINEL")))
        errors += fail(throwing, at("/anything"))

        val kinds = errors.map { it.kind }.toSet()
        assertEquals(ImportException.Kind.values().toSet(), kinds)
        assertEquals(Category.values().toSet(), errors.mapNotNull { it.gatewayCategory }.toSet())
        for (error in errors) assertClean(error)

        // Failures that are not errors: sentinel JSON-LD, the depth bound, a trusted TLS page and an image that does not decode.
        val trusted = TestGateways.against(https.server, dns = exempt, trustTestCert = true)
        https.page("/trusted", sentinelPage)
        https.page("/SENTINEL-pic.jpg", "<html>SENTINEL not an image</html>".toByteArray(), contentType = "image/jpeg")
        val drafted = ImportFixtures.importer(trusted, MutableClock(), ImageValidator()::validate)
            .importRecipe((ImportUrl.check(TestGateways.urlOf(https.server, "/trusted", "https")) as UrlCheck.Valid).url) {}
        val draft = assertIs<ImportResult.Drafted>(drafted).draft
        assertEquals(DraftImage.NoImage(NoImageCause.NOT_AN_IMAGE), draft.image)
        assertFalse(draft.toString().lowercase().contains("sentinel"), draft.toString())
        for (name in listOf("sentinel-invalid-jsonld.html")) {
            plain.page("/$name", ImportFixtures.page(name))
            val result = ImportFixtures.importer(gateway).importRecipe(at("/$name")) {}
            assertEquals(ExtractionTier.MICRODATA, assertIs<ImportResult.Drafted>(result).draft.tier)
        }
        plain.page("/deep", "<html><head><script type=\"application/ld+json\">${ImportFixtures.deepJsonLd(1000, false)}</script></head><body>${Sentinels.INGREDIENT}</body></html>".toByteArray())
        assertClean(fail(gateway, at("/deep")))
    }

    @Test
    fun `sentinel import errors have no cause and no suppressed exception`() = runTest {
        val plain = routed()
        val gateway = TestGateways.against(plain.server, dns = dnsFor(*loopback().toTypedArray()))
        val gzipped = ImportFixtures.gzip(sentinelPage)
        plain.route("/truncated") { MockResponse().setHeader("Content-Type", "text/html").setHeader("Content-Encoding", "gzip").setBody(Buffer().write(gzipped.copyOf(gzipped.size / 2))) }
        val unreadable = fail(gateway, urlAt(plain.server, "/truncated"))
        assertEquals(ImportException.Kind.UNREADABLE_CONTENT, unreadable.kind)
        assertNull(unreadable.cause)
        assertEquals(0, unreadable.suppressed.size)
        assertEquals("EOFException", unreadable.causeType)
        plain.route("/gone") { MockResponse().setResponseCode(404).setBody("SENTINEL body") }
        val gone = fail(gateway, urlAt(plain.server, "/gone"))
        assertNull(gone.cause)
        assertEquals(0, gone.suppressed.size)
        assertEquals(404, gone.statusCode)
        assertClean(unreadable)
        assertClean(gone)
    }

    @Test
    fun `every content holding pipeline type prints no sentinel`() {
        val url = Sentinels.URL
        val picked = ImportUrl.pick("see $url now")!!
        val valid = ImportUrl.check(url) as UrlCheck.Valid
        val invalid = ImportUrl.check("https://user:SENTINEL@sentinel-9c1e.example/")
        val recipe = ExtractedRecipe(DraftValue.Stated(Sentinels.TITLE), listOf(Sentinels.INGREDIENT), listOf(Sentinels.INGREDIENT), DraftValue.Absent, DraftValue.Absent)
        val scan = JsonLdScan(listOf(JsonLdRecipe(recipe, Sentinels.URL)))
        val draft = ImportDraft(
            DraftValue.Stated(Sentinels.TITLE), listOf(Sentinels.INGREDIENT), DraftValue.Stated(listOf(Sentinels.INGREDIENT)),
            DraftValue.Absent, DraftValue.Absent, DraftImage.Validated(Sentinels.URL.toByteArray(), 1, 1),
            ExtractionTier.JSON_LD, Sentinels.URL, Instant.EPOCH,
        )
        val printed = listOf(
            valid.url, picked, valid, invalid, draft, draft.image, draft.title, recipe, Extraction(ExtractionTier.JSON_LD, recipe, scan),
            scan, Candidate.Url(Sentinels.URL), ImportResult.Drafted(draft),
        ).map { it.toString() }
        for (text in printed) {
            assertFalse(text.lowercase().contains("sentinel"), text)
            assertFalse(text.contains(Sentinels.URL), text)
        }
        assertEquals(12, printed.size)
        assertIs<UrlCheck.Invalid>(invalid)
        assertTrue(ImageFixtures.garbage().isNotEmpty())
    }
}
