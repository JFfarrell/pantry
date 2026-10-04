package ie.pantry.data.gateway

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.testutil.RepoPaths
import ie.pantry.testutil.TestGateways
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** R8: the nutrition request's shape, inspected without any network. */
class GatewayRequestInspectionTest {

    private val constantNames = setOf("search_simple", "action", "json", "page_size", "search_terms")

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = TestGateways.server()
        server.dispatcher = TestGateways.answerEvery { MockResponse().setBody("ok") }
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private val nutritionTerm = "crème fraîche & salt/pepper?#x=1"

    private suspend fun callEach(gateway: ExternalDataGateway, url: String) {
        assertIs<GatewayResult.Fetched>(gateway.fetchPage(url))
        assertIs<GatewayResult.Fetched>(gateway.fetchImage(url))
        assertIs<GatewayResult.Fetched>(gateway.lookupNutrition(nutritionTerm))
    }

    @Test
    fun `nutrition url carries the term as one search_terms parameter`() {
        val term = "crème fraîche & salt/pepper?#x=1"

        val url = NutritionQuery.url(NutritionQuery.ENDPOINT, term)

        assertEquals(term, url.queryParameter("search_terms"))
        assertEquals(1, url.queryParameterValues("search_terms").size)
        assertEquals(NutritionQuery.ENDPOINT.encodedPath, url.encodedPath)
        assertNull(url.fragment)
    }

    @Test
    fun `nutrition url query parameter names are the compile time constant set`() {
        val url = NutritionQuery.url(NutritionQuery.ENDPOINT, "a&b=c&search_terms=evil&json=0")

        assertEquals(constantNames, url.queryParameterNames)
        assertEquals("1", url.queryParameter("search_simple"))
        assertEquals("process", url.queryParameter("action"))
        assertEquals("1", url.queryParameter("json"))
        assertEquals("5", url.queryParameter("page_size"))
        assertEquals("a&b=c&search_terms=evil&json=0", url.queryParameter("search_terms"))
    }

    @Test
    fun `production nutrition endpoint is the Open Food Facts search url over https`() {
        val endpoint = NutritionQuery.ENDPOINT

        assertEquals("https://world.openfoodfacts.org/cgi/search.pl", endpoint.toString())
        assertTrue(endpoint.isHttps)
        assertNull(endpoint.query)
    }

    @Test
    fun `nutrition query source builds the url with addQueryParameter and no string interpolation`() {
        val source = File(RepoPaths.repoRoot(), "app/src/main/java/ie/pantry/data/gateway/NutritionQuery.kt").readText()

        assertTrue("addQueryParameter" in source)
        for (forbidden in listOf("\"$", "\${", "+ term", ".plus(", "String.format")) {
            assertFalse(forbidden in source, "NutritionQuery.kt must not contain $forbidden")
        }
    }

    // ---- recorded requests (T13) ----

    @Test
    fun `every call type sends an anonymous get with only the default headers`() = runTest {
        callEach(TestGateways.against(server), TestGateways.urlOf(server, "/page"))

        repeat(3) {
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals(0L, request.bodySize)
            assertEquals(setOf("host", "connection", "accept-encoding", "user-agent"), request.headers.names().map { it.lowercase() }.toSet())
            assertEquals("gzip", request.getHeader("Accept-Encoding"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Proxy-Authorization"))
            assertNull(request.getHeader("Cookie"))
        }
    }

    @Test
    fun `page and image fetch request target and host equal the parsed url without fragment or userinfo`() = runTest {
        val gateway = TestGateways.against(server)
        val url = "http://127.0.0.1:${server.port}/a/b?x=1&y=two#frag"

        gateway.fetchPage(url)
        gateway.fetchImage(url)

        repeat(2) {
            val request = server.takeRequest()
            assertEquals("/a/b?x=1&y=two", request.path)
            assertEquals("127.0.0.1:${server.port}", request.getHeader("Host"))
        }
    }

    @Test
    fun `nutrition lookup sends the term as one decoded search_terms parameter`() = runTest {
        val gateway = TestGateways.against(server)

        assertIs<GatewayResult.Fetched>(gateway.lookupNutrition(nutritionTerm))

        val url = server.takeRequest().requestUrl!!
        assertEquals(nutritionTerm, url.queryParameter("search_terms"))
        assertEquals(constantNames, url.queryParameterNames)
        assertEquals("/search", url.encodedPath)
        assertNull(url.fragment)
    }

    @Test
    fun `a cookie set before a redirect is never sent on a later request or call`() = runTest {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                if (request.path == "/start") {
                    MockResponse().setResponseCode(302).setHeader("Location", "/next").setHeader("Set-Cookie", "id=1; Path=/")
                } else {
                    MockResponse().setBody("ok").setHeader("Set-Cookie", "later=2; Path=/")
                }
        }
        val gateway = TestGateways.against(server)

        assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(server, "/start")))
        assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(server, "/start")))

        repeat(server.requestCount) { assertNull(server.takeRequest().getHeader("Cookie")) }
    }

    @Test
    fun `a userinfo url produces no authorization header`() = runTest {
        val gateway = TestGateways.against(server)

        assertIs<GatewayResult.Fetched>(gateway.fetchPage("http://user:secret@127.0.0.1:${server.port}/path"))

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertEquals("127.0.0.1:${server.port}", request.getHeader("Host"))
        assertEquals("/path", request.path)
    }

    @Test
    fun `a blank or whitespace term gives INVALID_REQUEST with no request sent`() = runTest {
        val gateway = TestGateways.against(server)

        for (term in listOf("", "   ", "\t\n")) {
            val error = assertIs<GatewayResult.Failed>(gateway.lookupNutrition(term)).error
            assertEquals(Category.INVALID_REQUEST, error.category)
            assertEquals(0, error.attempts)
        }
        assertEquals(0, server.requestCount)
    }
}
