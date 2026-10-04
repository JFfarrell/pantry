package ie.pantry.data.gateway

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.testutil.TestGateways
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/** R2 and R3 against direct URLs: scheme and address refusals happen before any bytes are sent. */
class GatewayTargetValidationTest {

    private lateinit var allowed: MockWebServer
    private lateinit var blocked: MockWebServer
    private val extra = mutableListOf<MockWebServer>()

    @Before
    fun setUp() {
        allowed = TestGateways.server().also { it.dispatcher = TestGateways.answerEvery { MockResponse().setBody("ok") } }
        blocked = TestGateways.server().also { it.dispatcher = TestGateways.answerEvery { MockResponse().setBody("no") } }
    }

    @After
    fun tearDown() {
        (listOf(allowed, blocked) + extra).forEach { runCatching { it.shutdown() } }
    }

    private fun failure(result: GatewayResult): GatewayException = assertIs<GatewayResult.Failed>(result).error

    @Test
    fun `non http schemes are refused before any connection for page and image fetch`() = runTest {
        val listener = TestGateways.RecordingEventListener()
        val gateway = TestGateways.against(allowed, listener = listener)
        val refused = listOf(
            "intent://evil#Intent;end", "content://ie.pantry.provider/x", "file:///etc/passwd",
            "javascript:alert(1)", "ftp://host/x", "data:text/plain,hi",
        )

        for (url in refused) {
            assertEquals(Category.SCHEME_REFUSED, failure(gateway.fetchPage(url)).category, url)
            assertEquals(Category.SCHEME_REFUSED, failure(gateway.fetchImage(url)).category, url)
        }

        assertEquals(0, allowed.requestCount)
        assertTrue(listener.connectStarts.isEmpty())
        assertTrue(listener.callStartTimesMillis.isEmpty())
    }

    @Test
    fun `malformed urls give INVALID_REQUEST before any connection`() = runTest {
        val listener = TestGateways.RecordingEventListener()
        val gateway = TestGateways.against(allowed, listener = listener)

        for (url in listOf("", "   ", "https://", "ht!tp://x")) {
            val page = failure(gateway.fetchPage(url))
            val image = failure(gateway.fetchImage(url))
            assertEquals(Category.INVALID_REQUEST, page.category, "'$url'")
            assertEquals(Category.INVALID_REQUEST, image.category, "'$url'")
            assertEquals(0, page.attempts)
        }
        assertEquals(Category.INVALID_REQUEST, failure(gateway.lookupNutrition("  ")).category)

        assertEquals(0, allowed.requestCount)
        assertTrue(listener.connectStarts.isEmpty())
    }

    private suspend fun assertEveryCallRefused(gateway: ExternalDataGateway, target: String) {
        val results = mapOf(
            CallType.PAGE_FETCH to gateway.fetchPage(target),
            CallType.IMAGE_FETCH to gateway.fetchImage(target),
            CallType.NUTRITION_LOOKUP to gateway.lookupNutrition("flour"),
        )
        for ((type, result) in results) {
            val error = failure(result)
            assertEquals(Category.ADDRESS_REFUSED, error.category, "$type $target")
            assertEquals(1, error.attempts, "$type $target")
            assertEquals(type, error.callType)
        }
    }

    @Test
    fun `literal blocked ipv4 urls give ADDRESS_REFUSED for every call type with no request recorded`() = runTest {
        // The blocked server is a live loopback listener on a port that is not exempt.
        val target = TestGateways.urlOf(blocked, "/")
        val gateway = TestGateways.against(allowed, nutritionEndpoint = target.toHttpUrl())

        assertEveryCallRefused(gateway, target)

        assertEquals(0, blocked.requestCount)
        for (literal in listOf("http://10.0.0.1/", "http://192.168.1.1:8080/", "http://169.254.169.254/latest")) {
            val other = TestGateways.against(allowed, nutritionEndpoint = literal.toHttpUrl())
            assertEveryCallRefused(other, literal)
        }
    }

    @Test
    fun `a literal ipv6 loopback url gives ADDRESS_REFUSED for every call type`() = runTest {
        val v6 = MockWebServer()
        val boundOk = try {
            v6.start(InetAddress.getByName("::1"), 0)
            extra += v6
            true
        } catch (e: Exception) {
            false
        }
        Assume.assumeTrue(boundOk)
        v6.dispatcher = TestGateways.answerEvery { MockResponse().setBody("no") }
        val target = "http://[::1]:${v6.port}/"
        val gateway = TestGateways.against(allowed, nutritionEndpoint = target.toHttpUrl())

        assertEveryCallRefused(gateway, target)

        assertEquals(0, v6.requestCount)
    }

    @Test
    fun `a hostname resolving to a blocked address gives ADDRESS_REFUSED for every call type`() = runTest {
        val dns = TestGateways.RecordingDns(mapOf("blocked.test" to listOf(listOf(InetAddress.getByName("127.0.0.1")))))
        val target = "http://blocked.test:${blocked.port}/"
        val gateway = TestGateways.against(allowed, dns = dns, nutritionEndpoint = target.toHttpUrl())

        assertEveryCallRefused(gateway, target)

        assertEquals(0, blocked.requestCount)
        assertTrue(dns.lookups.isNotEmpty())
    }

    // ---- redirects (T11) ----

    private fun callsOf(gateway: ExternalDataGateway, url: String): Map<CallType, suspend () -> GatewayResult> = mapOf(
        CallType.PAGE_FETCH to { gateway.fetchPage(url) },
        CallType.IMAGE_FETCH to { gateway.fetchImage(url) },
        CallType.NUTRITION_LOOKUP to { gateway.lookupNutrition("flour") },
    )

    private fun redirectTo(location: String, code: Int = 302) =
        MockResponse().setResponseCode(code).setHeader("Location", location)

    private fun dispatching(handler: (RecordedRequest, Int) -> MockResponse): Dispatcher = object : Dispatcher() {
        private val count = AtomicInteger()
        override fun dispatch(request: RecordedRequest): MockResponse = handler(request, count.getAndIncrement())
    }

    @Test
    fun `a redirect to an intent or content scheme gives SCHEME_REFUSED for page and image fetch`() = runTest {
        val second = TestGateways.server().also { extra += it }
        for ((first, location) in listOf(allowed to "intent://evil#Intent;end", second to "content://ie.pantry.provider/x")) {
            first.dispatcher = TestGateways.answerEvery { redirectTo(location) }
            val gateway = TestGateways.against(allowed, second)
            val before = first.requestCount
            val url = TestGateways.urlOf(first, "/start")

            assertEquals(Category.SCHEME_REFUSED, failure(gateway.fetchPage(url)).category, location)
            assertEquals(Category.SCHEME_REFUSED, failure(gateway.fetchImage(url)).category, location)

            assertEquals(2, first.requestCount - before, "one request per call, none to the refused target")
        }
    }

    @Test
    fun `an http to https to http chain succeeds`() = runTest {
        val https = TestGateways.httpsServer().also { extra += it }
        allowed.dispatcher = dispatching { request, _ ->
            when (request.path) {
                "/start" -> redirectTo(TestGateways.urlOf(https, "/mid", "https"))
                else -> MockResponse().setBody("done")
            }
        }
        https.dispatcher = TestGateways.answerEvery { redirectTo(TestGateways.urlOf(allowed, "/end")) }
        val gateway = TestGateways.against(allowed, https, trustTestCert = true)

        val fetched = assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(allowed, "/start")))

        assertEquals("done", String(fetched.body.bytes))
        assertEquals(1, https.requestCount)
    }

    @Test
    fun `exactly the redirect limit then 200 succeeds`() = runTest {
        for (type in CallType.entries) {
            allowed.dispatcher = dispatching { _, n -> if (n < 5) redirectTo("/next") else MockResponse().setBody("end") }
            val listener = TestGateways.RecordingEventListener()
            val gateway = TestGateways.against(allowed, listener = listener)

            val fetched = assertIs<GatewayResult.Fetched>(callsOf(gateway, TestGateways.urlOf(allowed, "/s"))[type]!!(), "$type")

            assertEquals("end", String(fetched.body.bytes))
            assertEquals(6, listener.connectStarts.size, "$type: one connection, so one guard check, per hop")
        }
    }

    @Test
    fun `one redirect over the limit and a self loop give TOO_MANY_REDIRECTS with six requests for each call type`() =
        runTest {
            for (kind in listOf("six", "loop")) {
                for (type in CallType.entries) {
                    allowed.dispatcher = if (kind == "six") {
                        dispatching { _, n -> if (n < 6) redirectTo("/next") else MockResponse().setBody("end") }
                    } else {
                        TestGateways.answerEvery { redirectTo("/loop") }
                    }
                    val listener = TestGateways.RecordingEventListener()
                    val backoff = TestGateways.RecordingBackoff()
                    val gateway = TestGateways.against(allowed, listener = listener, backoff = backoff)
                    val before = allowed.requestCount

                    val error = failure(callsOf(gateway, TestGateways.urlOf(allowed, "/loop"))[type]!!())

                    assertEquals(Category.TOO_MANY_REDIRECTS, error.category, "$kind $type")
                    assertEquals(6, allowed.requestCount - before, "$kind $type")
                    assertEquals(6, listener.callStartTimesMillis.size, "$kind $type: one call per hop")
                    assertTrue(backoff.delays.isEmpty(), "$kind $type is not retried")
                    assertEquals(1, error.attempts)
                }
            }
        }

    @Test
    fun `a redirect with no location gives HTTP_STATUS with its code`() = runTest {
        allowed.dispatcher = TestGateways.answerEvery { MockResponse().setResponseCode(302) }
        val gateway = TestGateways.against(allowed)

        for ((type, call) in callsOf(gateway, TestGateways.urlOf(allowed, "/"))) {
            val error = failure(call())
            assertEquals(Category.HTTP_STATUS, error.category, "$type")
            assertEquals(302, error.statusCode)
        }
    }

    @Test
    fun `an unparseable location gives INVALID_REQUEST`() = runTest {
        allowed.dispatcher = TestGateways.answerEvery { redirectTo("http://[::bad") }
        val gateway = TestGateways.against(allowed)

        for ((type, call) in callsOf(gateway, TestGateways.urlOf(allowed, "/"))) {
            assertEquals(Category.INVALID_REQUEST, failure(call()).category, "$type")
        }
    }

    @Test
    fun `relative and protocol relative locations are resolved against the current hop`() = runTest {
        val gateway = TestGateways.against(allowed)

        allowed.dispatcher = dispatching { _, n -> if (n == 0) redirectTo("/recipe/1") else MockResponse().setBody("ok") }
        assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(allowed, "/dir/page")))
        allowed.takeRequest()
        // MockWebServer rebuilds `requestUrl` with its own host name, so the Host header carries the evidence.
        val resolved = allowed.takeRequest()
        assertEquals("/recipe/1", resolved.path)
        assertEquals("127.0.0.1:${allowed.port}", resolved.getHeader("Host"))

        val exempt = "//127.0.0.1:${allowed.port}/x"
        allowed.dispatcher = dispatching { _, n -> if (n == 0) redirectTo(exempt) else MockResponse().setBody("ok") }
        assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(allowed, "/dir/page")))
        allowed.takeRequest()
        assertEquals("/x", allowed.takeRequest().path)

        allowed.dispatcher = TestGateways.answerEvery { redirectTo("//10.0.0.1/x") }
        val before = allowed.requestCount
        val error = failure(gateway.fetchPage(TestGateways.urlOf(allowed, "/dir/page")))
        assertEquals(Category.ADDRESS_REFUSED, error.category)
        assertEquals(1, error.attempts)
        assertEquals(1, allowed.requestCount - before)
    }

    @Test
    fun `a non redirect 3xx with a location gives HTTP_STATUS`() = runTest {
        val gateway = TestGateways.against(allowed)
        for (status in listOf(300, 304)) {
            allowed.dispatcher = TestGateways.answerEvery { redirectTo("/elsewhere", status) }
            for ((type, call) in callsOf(gateway, TestGateways.urlOf(allowed, "/"))) {
                val error = failure(call())
                assertEquals(Category.HTTP_STATUS, error.category, "$type $status")
                assertEquals(status, error.statusCode)
            }
        }
    }

    @Test
    fun `the cap applies per response across redirect hops`() = runTest {
        val cap = TestGateways.FAST.maxBodyBytes.toInt()
        allowed.dispatcher = dispatching { _, n ->
            if (n == 0) redirectTo("/next").setBody(Buffer().write(ByteArray(cap - 1))) else MockResponse().setBody(Buffer().write(ByteArray(cap)))
        }
        val gateway = TestGateways.against(allowed)

        val fetched = assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(allowed, "/start")))

        assertEquals(cap, fetched.body.bytes.size)
    }

    @Test
    fun `a redirect with a large unread body is followed promptly and leaves no pooled connection`() = runTest {
        val cap = TestGateways.FAST.maxBodyBytes.toInt()
        allowed.dispatcher = dispatching { request, _ ->
            if (request.path == "/first") {
                redirectTo("/second").setBody(Buffer().write(ByteArray(cap * 4))).throttleBody(1024, 3, TimeUnit.SECONDS)
            } else {
                MockResponse().setBody("final")
            }
        }
        val listener = TestGateways.RecordingEventListener()
        val gateway = TestGateways.against(allowed, listener = listener)
        gateway.client
        val started = System.nanoTime()

        val result = gateway.fetchPage(TestGateways.urlOf(allowed, "/first"))

        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertEquals("final", String(assertIs<GatewayResult.Fetched>(result).body.bytes))
        assertTrue(elapsedMillis < 500, "took $elapsedMillis ms")
        assertEquals(0, gateway.client.connectionPool.connectionCount())
        assertTrue(listener.connectionReleasedCount >= 1)
    }

    // ---- resolved-address characterisation (T12) ----

    private fun addr(literal: String): InetAddress = InetAddress.getByName(literal)

    @Test
    fun `a mixed answer connects only to the allowed address`() = runTest {
        allowed.dispatcher = TestGateways.answerEvery { MockResponse().setBody("ok") }
        val dns = TestGateways.RecordingDns(mapOf("mixed.test" to listOf(listOf(addr("10.0.0.1"), addr("127.0.0.1")))))
        val listener = TestGateways.RecordingEventListener()
        val gateway = TestGateways.against(allowed, dns = dns, listener = listener)

        val fetched = gateway.fetchPage("http://mixed.test:${allowed.port}/")

        assertIs<GatewayResult.Fetched>(fetched)
        assertTrue(listener.connectStarts.isNotEmpty())
        assertTrue(listener.connectStarts.none { it.address == addr("10.0.0.1") }, listener.connectStarts.toString())
    }

    @Test
    fun `a redirect to a blocked ipv4 or ipv6 host gives ADDRESS_REFUSED for page and image fetch`() = runTest {
        val dns = TestGateways.RecordingDns(
            mapOf("blocked4.test" to listOf(listOf(addr("10.0.0.1"))), "blocked6.test" to listOf(listOf(addr("::1")))),
        )
        for (host in listOf("blocked4.test", "blocked6.test")) {
            allowed.dispatcher = TestGateways.answerEvery { redirectTo("http://$host:80/x") }
            val listener = TestGateways.RecordingEventListener()
            val gateway = TestGateways.against(allowed, dns = dns, listener = listener)
            val url = TestGateways.urlOf(allowed, "/start")

            assertEquals(Category.ADDRESS_REFUSED, failure(gateway.fetchPage(url)).category, host)
            assertEquals(Category.ADDRESS_REFUSED, failure(gateway.fetchImage(url)).category, host)

            val connected = listener.connectStarts.map { it.address }
            assertTrue(addr("10.0.0.1") !in connected && addr("::1") !in connected, connected.toString())
        }
    }

    @Test
    fun `dns rebinding after the first lookup is refused at connect`() = runTest {
        allowed.dispatcher = TestGateways.answerEvery { MockResponse().setBody("ok") }
        val dns = TestGateways.RecordingDns(
            mapOf("rebind.test" to listOf(listOf(addr("127.0.0.1")), listOf(addr("10.0.0.1"), addr("::1")))),
        )
        val listener = TestGateways.RecordingEventListener()
        val gateway = TestGateways.against(allowed, dns = dns, listener = listener)
        val url = "http://rebind.test:${allowed.port}/"

        assertIs<GatewayResult.Fetched>(gateway.fetchPage(url))
        assertEquals(Category.ADDRESS_REFUSED, failure(gateway.fetchPage(url)).category)

        val connected = listener.connectStarts.map { it.address }
        assertTrue(addr("10.0.0.1") !in connected && addr("::1") !in connected, connected.toString())
    }

    @Test
    fun `decimal octal and hex ipv4 literals never reach the blocked target`() = runTest {
        val gateway = TestGateways.against(allowed)

        for (host in listOf("2130706433", "017700000001", "0x7f000001")) {
            val result = gateway.fetchPage("http://$host:${blocked.port}/")

            val category = failure(result).category
            assertTrue(category == Category.ADDRESS_REFUSED || category == Category.CONNECTION_FAILED, "$host: $category")
        }
        assertEquals(0, blocked.requestCount)
    }
}
