package ie.pantry.data.gateway

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.testutil.TestGateways
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** R5 AC4, R6 and R1 AC1: how each kind of transport outcome maps to a [GatewayResult], for every call type. */
class GatewayFailureMappingTest {

    private lateinit var server: MockWebServer
    private val toClose = mutableListOf<MockWebServer>()

    @Before
    fun setUp() {
        server = TestGateways.server()
        toClose += server
    }

    @After
    fun tearDown() {
        toClose.forEach { runCatching { it.shutdown() } }
    }

    /** One suspend call per call type against [base], the nutrition lookup going to the gateway's endpoint. */
    private fun calls(gateway: ExternalDataGateway, base: String): Map<CallType, suspend () -> GatewayResult> = mapOf(
        CallType.PAGE_FETCH to { gateway.fetchPage(base) },
        CallType.IMAGE_FETCH to { gateway.fetchImage(base) },
        CallType.NUTRITION_LOOKUP to { gateway.lookupNutrition("flour") },
    )

    private fun failure(result: GatewayResult): GatewayException = assertIs<GatewayResult.Failed>(result).error

    private fun respondWith(response: () -> MockResponse) {
        server.dispatcher = TestGateways.answerEvery(response)
    }

    @Test
    fun `a 200 response returns its body bytes for each call type`() = runTest {
        respondWith { MockResponse().setBody("hello") }
        val gateway = TestGateways.against(server)

        for ((type, call) in calls(gateway, TestGateways.urlOf(server, "/p"))) {
            val fetched = assertIs<GatewayResult.Fetched>(call(), type.name)
            assertEquals("hello", String(fetched.body.bytes), type.name)
        }
    }

    @Test
    fun `a 200 response carries identity encoding and the raw content type`() = runTest {
        respondWith { MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody("<p>hi</p>") }
        val gateway = TestGateways.against(server)

        val fetched = assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(server, "/")))

        assertEquals(BodyEncoding.IDENTITY, fetched.body.encoding)
        assertEquals("text/html; charset=utf-8", fetched.body.mediaType)
    }

    @Test
    fun `a response without a content type has a null media type`() = runTest {
        respondWith { MockResponse().setBody("x") }
        val gateway = TestGateways.against(server)

        val fetched = assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(server, "/")))

        assertNull(fetched.body.mediaType)
    }

    @Test
    fun `a gzip body is returned as its compressed wire bytes with GZIP encoding`() = runTest {
        val compressed = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write("compress me ".repeat(50).toByteArray()) }
        }.toByteArray()
        respondWith { MockResponse().setHeader("Content-Encoding", "gzip").setBody(Buffer().write(compressed)) }
        val gateway = TestGateways.against(server)

        val fetched = assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(server, "/")))

        assertEquals(BodyEncoding.GZIP, fetched.body.encoding)
        assertContentEquals(compressed, fetched.body.bytes)
    }

    @Test
    fun `an https page fetch with the test certificate trusted succeeds`() = runTest {
        val https = TestGateways.httpsServer().also { toClose += it }
        https.dispatcher = TestGateways.answerEvery { MockResponse().setBody("secure") }
        val gateway = TestGateways.against(https, trustTestCert = true)

        val fetched = assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(https, "/", "https")))

        assertEquals("secure", String(fetched.body.bytes))
    }

    private suspend fun assertTimeoutWithinBound(response: () -> MockResponse) {
        respondWith(response)
        for (type in CallType.entries) {
            val listener = TestGateways.RecordingEventListener()
            val gateway = TestGateways.against(server, listener = listener)
            val result = calls(gateway, TestGateways.urlOf(server, "/"))[type]!!()

            val error = failure(result)
            assertEquals(Category.TIMEOUT, error.category, type.name)
            assertEquals(type, error.callType)
            val elapsed = listener.callFailedTimesMillis.first() - listener.callStartTimesMillis.first()
            assertTrue(elapsed <= TestGateways.FAST.attemptTimeout.inWholeMilliseconds + 1_000, "$type took $elapsed ms")
        }
    }

    @Test
    fun `a server that never sends headers gives TIMEOUT within the attempt bound for each call type`() = runTest {
        assertTimeoutWithinBound { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
    }

    @Test
    fun `a body that stalls midway gives TIMEOUT within the attempt bound for each call type`() = runTest {
        assertTimeoutWithinBound {
            MockResponse().setBody(Buffer().write(ByteArray(2048))).throttleBody(1024, 3, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `204 succeeds with an empty body for each call type`() = runTest {
        respondWith { MockResponse().setResponseCode(204) }
        val gateway = TestGateways.against(server)

        for ((type, call) in calls(gateway, TestGateways.urlOf(server, "/"))) {
            val fetched = assertIs<GatewayResult.Fetched>(call(), type.name)
            assertEquals(0, fetched.body.bytes.size, type.name)
        }
    }

    @Test
    fun `every non 2xx status gives HTTP_STATUS carrying its code for each call type`() = runTest {
        val gateway = TestGateways.against(server)
        for (status in listOf(304, 400, 403, 404, 410, 429, 500, 502, 503)) {
            respondWith { MockResponse().setResponseCode(status) }
            for ((type, call) in calls(gateway, TestGateways.urlOf(server, "/"))) {
                val error = failure(call())
                assertEquals(Category.HTTP_STATUS, error.category, "$type $status")
                assertEquals(status, error.statusCode, "$type $status")
                assertEquals(type, error.callType)
            }
        }
    }

    @Test
    fun `connection level failures give CONNECTION_FAILED for each call type`() = runTest {
        // Dropped connection before any response, and mid-body over a 1 KiB body.
        for (policy in listOf(SocketPolicy.DISCONNECT_AT_START, SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)) {
            respondWith { MockResponse().setBody(Buffer().write(ByteArray(1024))).setSocketPolicy(policy) }
            val gateway = TestGateways.against(server)
            for ((type, call) in calls(gateway, TestGateways.urlOf(server, "/"))) {
                assertEquals(Category.CONNECTION_FAILED, failure(call()).category, "$type $policy")
            }
        }

        // A closed port that stays exempt, so the refusal comes from the OS and not from the guard.
        val closed = TestGateways.server()
        val closedGateway = TestGateways.against(closed)
        closed.shutdown()
        for ((type, call) in calls(closedGateway, TestGateways.urlOf(closed, "/"))) {
            assertEquals(Category.CONNECTION_FAILED, failure(call()).category, "closed $type")
        }

        // A resolver that throws UnknownHostException (the default RecordingDns has no script).
        val unknownHost = TestGateways.against(
            server,
            nutritionEndpoint = "http://unknown.test:9/search".toHttpUrl(),
        )
        for ((type, call) in calls(unknownHost, "http://unknown.test:9/")) {
            assertEquals(Category.CONNECTION_FAILED, failure(call()).category, "dns $type")
        }

        // The TLS server without the fixture certificate trusted: a handshake failure, not a trust category.
        val https = TestGateways.httpsServer().also { toClose += it }
        https.dispatcher = TestGateways.answerEvery { MockResponse().setBody("secure") }
        val untrusted = TestGateways.against(
            https,
            nutritionEndpoint = TestGateways.urlOf(https, "/search", "https").toHttpUrl(),
        )
        for ((type, call) in calls(untrusted, TestGateways.urlOf(https, "/", "https"))) {
            assertEquals(Category.CONNECTION_FAILED, failure(call()).category, "tls $type")
        }
    }

    @Test
    fun `an exception thrown inside the client gives UNEXPECTED for each call type`() = runTest {
        respondWith { MockResponse().setBody("unused") }
        val gateway = TestGateways.against(
            server,
            interceptor = TestGateways.throwingInterceptor(IllegalStateException("boom")),
        )

        for ((type, call) in calls(gateway, TestGateways.urlOf(server, "/"))) {
            val error = failure(call())
            assertEquals(Category.UNEXPECTED, error.category, type.name)
            assertEquals("IllegalStateException", error.causeType)
        }
    }

    private val cap = TestGateways.FAST.maxBodyBytes.toInt()

    private fun body(size: Int): Buffer = Buffer().write(ByteArray(size) { 5 })

    private suspend fun assertTooLargeAtOnce(response: () -> MockResponse) {
        respondWith(response)
        for (type in CallType.entries) {
            // Built per call type so that its lazy client exists before the stopwatch starts.
            val gateway = TestGateways.against(server)
            gateway.client
            val started = System.nanoTime()
            val error = failure(calls(gateway, TestGateways.urlOf(server, "/"))[type]!!())
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000

            assertEquals(Category.RESPONSE_TOO_LARGE, error.category, type.name)
            assertTrue(elapsedMillis < 500, "$type took $elapsedMillis ms")
            assertEquals(1, error.attempts)
            assertEquals(0, gateway.client.connectionPool.connectionCount())
        }
    }

    @Test
    fun `a declared over cap content length with a body that never arrives gives RESPONSE_TOO_LARGE at once for each call type`() =
        runTest {
            assertTooLargeAtOnce { MockResponse().setBody(Buffer()).setHeader("Content-Length", cap + 1) }
        }

    @Test
    fun `a declared over cap content length with a dropped body gives RESPONSE_TOO_LARGE at once for each call type`() =
        runTest {
            assertTooLargeAtOnce {
                MockResponse().setBody(body(1024)).setHeader("Content-Length", cap + 1)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            }
        }

    @Test
    fun `a body of exactly the cap is returned intact for each call type under FAST and DEFAULT`() = runTest {
        for (policy in listOf(TestGateways.FAST, GatewayPolicy.DEFAULT)) {
            val size = policy.maxBodyBytes.toInt()
            respondWith { MockResponse().setBody(body(size)) }
            val gateway = TestGateways.against(server, policy = policy)
            for ((type, call) in calls(gateway, TestGateways.urlOf(server, "/"))) {
                val fetched = assertIs<GatewayResult.Fetched>(call(), "$type $size")
                assertEquals(size, fetched.body.bytes.size)
            }
        }
    }

    @Test
    fun `a body one byte over the cap gives RESPONSE_TOO_LARGE for each call type under FAST and DEFAULT`() = runTest {
        for (policy in listOf(TestGateways.FAST, GatewayPolicy.DEFAULT)) {
            val size = policy.maxBodyBytes.toInt() + 1
            respondWith { MockResponse().setBody(body(size)) }
            val gateway = TestGateways.against(server, policy = policy)
            for ((type, call) in calls(gateway, TestGateways.urlOf(server, "/"))) {
                assertEquals(Category.RESPONSE_TOO_LARGE, failure(call()).category, "$type $size")
            }
        }
    }

    @Test
    fun `a fully sent over cap body with a true content length gives RESPONSE_TOO_LARGE`() = runTest {
        respondWith { MockResponse().setBody(body(cap * 2)) }
        val gateway = TestGateways.against(server)

        assertEquals(Category.RESPONSE_TOO_LARGE, failure(gateway.fetchPage(TestGateways.urlOf(server, "/"))).category)
    }

    @Test
    fun `a chunked over cap body gives RESPONSE_TOO_LARGE`() = runTest {
        respondWith { MockResponse().setChunkedBody(body(cap * 2), 4096) }
        val gateway = TestGateways.against(server)

        assertEquals(Category.RESPONSE_TOO_LARGE, failure(gateway.fetchPage(TestGateways.urlOf(server, "/"))).category)
    }

    @Test
    fun `a falsely low content length succeeds with exactly the declared bytes and a closed connection`() = runTest {
        respondWith { MockResponse().setBody(body(cap + 1_000)).setHeader("Content-Length", 1_000) }
        val listener = TestGateways.RecordingEventListener()
        val gateway = TestGateways.against(server, listener = listener)

        val fetched = assertIs<GatewayResult.Fetched>(gateway.fetchPage(TestGateways.urlOf(server, "/")))

        assertEquals(1_000, fetched.body.bytes.size)
        assertEquals(0, gateway.client.connectionPool.connectionCount())
        assertTrue(listener.connectionReleasedCount >= 1)
    }

    @Test
    fun `an over cap call leaves no pooled connection`() = runTest {
        respondWith { MockResponse().setBody(body(cap * 2)) }
        val listener = TestGateways.RecordingEventListener()
        val gateway = TestGateways.against(server, listener = listener)

        failure(gateway.fetchPage(TestGateways.urlOf(server, "/")))

        assertEquals(0, gateway.client.connectionPool.connectionCount())
        assertTrue(listener.connectionReleasedCount >= 1)
    }

    private fun describe(error: GatewayException): String = when (error.category) {
        Category.INVALID_REQUEST -> "invalid"
        Category.SCHEME_REFUSED -> "scheme"
        Category.ADDRESS_REFUSED -> "address"
        Category.TOO_MANY_REDIRECTS -> "redirects"
        Category.TIMEOUT -> "timeout"
        Category.CONNECTION_FAILED -> "connection"
        Category.HTTP_STATUS -> "status"
        Category.RESPONSE_TOO_LARGE -> "large"
        Category.UNEXPECTED -> "unexpected"
    }

    @Test
    fun `timeout non 2xx and oversized errors are distinct categories`() = runTest {
        val gateway = TestGateways.against(server)
        val url = TestGateways.urlOf(server, "/")

        respondWith { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val timeout = failure(gateway.fetchPage(url))
        respondWith { MockResponse().setResponseCode(404) }
        val notFound = failure(gateway.fetchPage(url))
        respondWith { MockResponse().setBody(body(cap + 1)) }
        val large = failure(gateway.fetchPage(url))

        assertEquals(listOf("timeout", "status", "large"), listOf(timeout, notFound, large).map(::describe))
    }

    @Test
    fun `a non 2xx response with a large unread body returns promptly and leaves no pooled connection`() = runTest {
        respondWith { MockResponse().setResponseCode(404).setBody(body(cap * 4)).throttleBody(1024, 3, TimeUnit.SECONDS) }
        val listener = TestGateways.RecordingEventListener()
        val gateway = TestGateways.against(server, listener = listener)
        gateway.client
        val started = System.nanoTime()

        val error = failure(gateway.fetchPage(TestGateways.urlOf(server, "/")))

        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertEquals(404, error.statusCode)
        assertTrue(elapsedMillis < 500, "took $elapsedMillis ms")
        assertEquals(0, gateway.client.connectionPool.connectionCount())
        assertTrue(listener.connectionReleasedCount >= 1)
    }
}
