package ie.pantry.data.gateway

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.testutil.Sentinels
import ie.pantry.testutil.TestGateways
import ie.pantry.testutil.assertNoSentinel
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Test

/** R9 (content-free errors, CFC-4 re-wrapping rule), plus the R6/R1 model invariants of the result and policy. */
class GatewayErrorHygieneTest {

    private fun wrap(cause: Throwable, callType: CallType = CallType.PAGE_FETCH, attempts: Int = 1) =
        gatewayFailure(cause, callType, attempts)

    @Test
    fun `gatewayFailure maps the address refused marker to ADDRESS_REFUSED with no cause type`() {
        val error = wrap(AddressRefusedException())

        assertEquals(Category.ADDRESS_REFUSED, error.category)
        assertNull(error.causeType)
    }

    @Test
    fun `gatewayFailure maps the response too large marker to RESPONSE_TOO_LARGE`() {
        val error = wrap(ResponseTooLargeException())

        assertEquals(Category.RESPONSE_TOO_LARGE, error.category)
        assertNull(error.causeType)
    }

    @Test
    fun `gatewayFailure maps interrupted io and socket timeouts to TIMEOUT`() {
        assertEquals(Category.TIMEOUT, wrap(InterruptedIOException("timeout")).category)
        val socketTimeout = wrap(SocketTimeoutException("SENTINEL"))
        assertEquals(Category.TIMEOUT, socketTimeout.category)
        assertEquals("SocketTimeoutException", socketTimeout.causeType)
    }

    @Test
    fun `gatewayFailure maps every other io exception to CONNECTION_FAILED`() {
        val causes = listOf(
            UnknownHostException("SENTINEL"), ConnectException("SENTINEL"), SSLException("SENTINEL"),
            SocketException("SENTINEL"), ProtocolException("SENTINEL"), EOFException("SENTINEL"),
            UnknownServiceException("SENTINEL"), IOException("SENTINEL"),
        )
        for (cause in causes) {
            val error = wrap(cause)
            assertEquals(Category.CONNECTION_FAILED, error.category, cause.javaClass.simpleName)
            assertEquals(cause.javaClass.simpleName, error.causeType)
        }
    }

    @Test
    fun `gatewayFailure maps a non io exception to UNEXPECTED`() {
        val error = wrap(IllegalStateException("SENTINEL"))

        assertEquals(Category.UNEXPECTED, error.category)
        assertEquals("IllegalStateException", error.causeType)
    }

    @Test
    fun `gatewayFailure finds markers in the cause chain and suppressed exceptions and terminates on cycles`() {
        val nested = IOException("outer", IllegalStateException("middle", ResponseTooLargeException()))
        assertEquals(Category.RESPONSE_TOO_LARGE, wrap(nested).category)
        assertNull(wrap(nested).causeType)

        val withSuppressed = IllegalStateException("SENTINEL").apply { addSuppressed(AddressRefusedException()) }
        assertEquals(Category.ADDRESS_REFUSED, wrap(withSuppressed).category)

        val markerBeatsTimeout = SocketTimeoutException("x").apply { addSuppressed(AddressRefusedException()) }
        assertEquals(Category.ADDRESS_REFUSED, wrap(markerBeatsTimeout).category)

        val first = IOException("a")
        val second = IOException("b", first)
        first.initCause(second)
        assertEquals(Category.CONNECTION_FAILED, wrap(first).category)

        val cyclicSuppressed = IllegalStateException("c")
        cyclicSuppressed.addSuppressed(IllegalArgumentException("d").apply { addSuppressed(cyclicSuppressed) })
        assertEquals(Category.UNEXPECTED, wrap(cyclicSuppressed).category)
    }

    @Test
    fun `a re-wrapped error has no cause no suppressed exceptions and the cause stack frames`() {
        val cause = ConnectException("SENTINEL").apply {
            initCause(IOException("SENTINEL", IllegalStateException("SENTINEL")))
            addSuppressed(IllegalArgumentException("SENTINEL"))
        }

        val error = wrap(cause, CallType.IMAGE_FETCH, attempts = 3)

        assertNull(error.cause)
        assertEquals(0, error.suppressed.size)
        assertContentEquals(cause.stackTrace, error.stackTrace)
        assertEquals(CallType.IMAGE_FETCH, error.callType)
        assertEquals(3, error.attempts)
        error.assertNoSentinel()
    }

    @Test
    fun `gateway exception fields hold only gateway vocabulary`() {
        val allowed = setOf<Class<*>>(
            Category::class.java, CallType::class.java, Integer::class.java, Int::class.javaPrimitiveType!!,
            String::class.java,
        )
        val fields = GatewayException::class.java.declaredFields
        assertTrue(fields.isNotEmpty())
        for (field in fields) assertTrue(field.type in allowed, "${field.name}: ${field.type}")
        assertEquals(listOf("causeType"), fields.filter { it.type == String::class.java }.map { it.name })

        val error = GatewayException(Category.HTTP_STATUS, CallType.NUTRITION_LOOKUP, 503, 3, IOException::class)
        assertEquals(
            "Gateway failure: category=HTTP_STATUS callType=NUTRITION_LOOKUP status=503 attempts=3 cause=IOException",
            error.message,
        )
        assertEquals(error.message, error.localizedMessage)
        assertEquals("ie.pantry.data.gateway.GatewayException: ${error.message}", error.toString())

        val refusal = gatewayError(Category.SCHEME_REFUSED, CallType.PAGE_FETCH, 0)
        assertEquals(
            "Gateway failure: category=SCHEME_REFUSED callType=PAGE_FETCH status=none attempts=0 cause=none",
            refusal.message,
        )
    }

    @Test
    fun `status code is present exactly when the category is HTTP_STATUS`() {
        val noStatus = assertFailsWith<IllegalArgumentException> {
            GatewayException(Category.HTTP_STATUS, CallType.PAGE_FETCH, null, 1, null)
        }
        val strayStatus = assertFailsWith<IllegalArgumentException> {
            GatewayException(Category.TIMEOUT, CallType.PAGE_FETCH, 500, 1, null)
        }
        assertEquals("HTTP_STATUS requires a status code", noStatus.message)
        assertEquals("only HTTP_STATUS carries a status code", strayStatus.message)
        assertEquals(404, gatewayError(Category.HTTP_STATUS, CallType.PAGE_FETCH, 1, 404).statusCode)
    }

    @Test
    fun `only TIMEOUT CONNECTION_FAILED and 5xx HTTP_STATUS are transient`() {
        for (category in Category.entries) {
            if (category == Category.HTTP_STATUS) continue
            val expected = category == Category.TIMEOUT || category == Category.CONNECTION_FAILED
            assertEquals(expected, gatewayError(category, CallType.PAGE_FETCH, 1).isTransient(), category.name)
        }
        for (status in listOf(300, 301, 399, 400, 404, 429, 499, 600)) {
            assertFalse(gatewayError(Category.HTTP_STATUS, CallType.PAGE_FETCH, 1, status).isTransient(), "$status")
        }
        for (status in listOf(500, 502, 503, 599)) {
            assertTrue(gatewayError(Category.HTTP_STATUS, CallType.PAGE_FETCH, 1, status).isTransient(), "$status")
        }
    }

    @Test
    fun `fetched body toString prints only size and encoding`() {
        val body = FetchedBody("SENTINEL secret".toByteArray(), BodyEncoding.GZIP, "text/html; SENTINEL")

        assertEquals("FetchedBody(size=15, encoding=GZIP)", body.toString())
    }

    @Test
    fun `body encoding is classified from the content encoding header`() {
        assertEquals(BodyEncoding.IDENTITY, BodyEncoding.of(null))
        assertEquals(BodyEncoding.IDENTITY, BodyEncoding.of("identity"))
        assertEquals(BodyEncoding.IDENTITY, BodyEncoding.of(" IDENTITY "))
        assertEquals(BodyEncoding.GZIP, BodyEncoding.of("gzip"))
        assertEquals(BodyEncoding.GZIP, BodyEncoding.of(" X-GZIP "))
        assertEquals(BodyEncoding.OTHER, BodyEncoding.of("br"))
        assertEquals(BodyEncoding.OTHER, BodyEncoding.of("gzip, br"))
        assertEquals(BodyEncoding.OTHER, BodyEncoding.of("deflate"))
    }

    @Test
    fun `default policy holds the Q2 values`() {
        val policy = GatewayPolicy.DEFAULT

        assertEquals(10.seconds, policy.connectTimeout)
        assertEquals(15.seconds, policy.readTimeout)
        assertEquals(20.seconds, policy.attemptTimeout)
        assertEquals(5_242_880L, policy.maxBodyBytes)
        assertEquals(5, policy.maxRedirects)
        assertEquals(2, policy.retryCount)
        assertEquals(listOf(500.milliseconds, 1.seconds), policy.backoff)
    }

    @Test
    fun `policy rejects invariant violations with constant messages`() {
        val base = GatewayPolicy.DEFAULT
        fun message(build: () -> GatewayPolicy): String? = assertFailsWith<IllegalArgumentException> { build() }.message

        assertEquals("connectTimeout must be positive", message { base.copy(connectTimeout = Duration.ZERO) })
        assertEquals("readTimeout must be positive", message { base.copy(readTimeout = (-1).seconds) })
        assertEquals("attemptTimeout must be at least connectTimeout", message { base.copy(attemptTimeout = 5.seconds) })
        assertEquals("maxBodyBytes must be positive", message { base.copy(maxBodyBytes = 0) })
        assertEquals("maxRedirects must not be negative", message { base.copy(maxRedirects = -1) })
        assertEquals("retryCount must not be negative", message { base.copy(retryCount = -1, backoff = emptyList()) })
        assertEquals("backoff must have one entry per retry", message { base.copy(backoff = listOf(1.seconds)) })
        assertEquals("backoff delays must not be negative", message { base.copy(backoff = listOf(500.milliseconds, (-1).seconds)) })
    }

    // ---- end to end: sentinel-bearing fixtures through the real gateway (T14) ----

    private val host = "sentinel-9c1e.example"
    private val sentinelPath = "/SENTINEL-path?q=SENTINEL"

    private fun addr(literal: String): java.net.InetAddress = java.net.InetAddress.getByName(literal)

    private fun errorOf(result: GatewayResult): GatewayException = assertIs<GatewayResult.Failed>(result).error

    private fun assertClean(error: GatewayException) {
        error.assertNoSentinel()
        val lowered = Sentinels.all.map { it.lowercase() }
        for (text in listOf(error.toString(), error.message.orEmpty())) {
            for (sentinel in lowered) assertFalse(text.lowercase().contains(sentinel), "'$sentinel' in '$text'")
        }
        assertNull(error.cause)
        assertEquals(0, error.suppressed.size)
    }

    /** Runs each call type against [page] and collects the errors; the nutrition lookup uses [endpoint]. */
    private suspend fun errorsFor(
        gateway: ExternalDataGateway,
        page: String,
        nutrition: Boolean = true,
    ): List<GatewayException> {
        val errors = mutableListOf(errorOf(gateway.fetchPage(page)), errorOf(gateway.fetchImage(page)))
        if (nutrition) errors += errorOf(gateway.lookupNutrition(Sentinels.INGREDIENT))
        return errors
    }

    private suspend fun collectEveryCategory(): List<GatewayException> {
        val errors = mutableListOf<GatewayException>()
        val server = TestGateways.server()
        val blocked = TestGateways.server().also { it.dispatcher = TestGateways.answerEvery { MockResponse().setBody("no") } }
        val https = TestGateways.httpsServer()
        try {
            val base = TestGateways.urlOf(server, sentinelPath)
            val serverEndpoint = TestGateways.urlOf(server, "/SENTINEL-search").toHttpUrl()

            // INVALID_REQUEST: a malformed initial URL and an unparseable Location.
            val plain = TestGateways.against(server, nutritionEndpoint = serverEndpoint)
            errors += errorsFor(plain, "ht!tp://SENTINEL host", nutrition = false)
            server.dispatcher = TestGateways.answerEvery {
                MockResponse().setResponseCode(302).setHeader("Location", "http://[::SENTINEL")
            }
            errors += errorsFor(plain, base)

            // SCHEME_REFUSED: initially and as a Location.
            errors += errorsFor(plain, "intent://SENTINEL/x#Intent;end", nutrition = false)
            server.dispatcher = TestGateways.answerEvery {
                MockResponse().setResponseCode(302).setHeader("Location", "intent://SENTINEL/x#Intent;end")
            }
            errors += errorsFor(plain, base)

            // ADDRESS_REFUSED: a sentinel host mapped to a blocked address, and a literal blocked URL.
            val dns = TestGateways.RecordingDns(mapOf(host to listOf(listOf(addr("10.0.0.1")))))
            val mapped = TestGateways.against(server, dns = dns, nutritionEndpoint = "http://$host/SENTINEL-search".toHttpUrl())
            errors += errorsFor(mapped, "http://$host$sentinelPath")
            val literal = TestGateways.against(server, nutritionEndpoint = "http://10.0.0.1/SENTINEL-search".toHttpUrl())
            errors += errorsFor(literal, "http://10.0.0.1$sentinelPath")

            // TOO_MANY_REDIRECTS: a sentinel Location chain.
            server.dispatcher = TestGateways.answerEvery {
                MockResponse().setResponseCode(302).setHeader("Location", "/SENTINEL-loop")
            }
            errors += errorsFor(plain, base)

            // TIMEOUT: the sentinel host mapped to the exempt loopback address of a server that never answers.
            server.dispatcher = TestGateways.answerEvery { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
            val timeoutDns = TestGateways.RecordingDns(mapOf(host to listOf(listOf(addr("127.0.0.1")))))
            val timing = TestGateways.against(
                server,
                dns = timeoutDns,
                nutritionEndpoint = "http://$host:${server.port}/SENTINEL-search".toHttpUrl(),
            )
            errors += errorsFor(timing, "http://$host:${server.port}$sentinelPath")

            // CONNECTION_FAILED: a resolver that throws, and an untrusted TLS server.
            val unknownDns = TestGateways.RecordingDns(mapOf(host to listOf(java.net.UnknownHostException("SENTINEL host"))))
            val unknown = TestGateways.against(server, dns = unknownDns, nutritionEndpoint = "http://$host/SENTINEL-search".toHttpUrl())
            errors += errorsFor(unknown, "http://$host$sentinelPath")
            https.dispatcher = TestGateways.answerEvery { MockResponse().setBody("SENTINEL") }
            val untrusted = TestGateways.against(https, nutritionEndpoint = TestGateways.urlOf(https, "/SENTINEL-search", "https").toHttpUrl())
            errors += errorsFor(untrusted, TestGateways.urlOf(https, sentinelPath, "https"))

            // HTTP_STATUS: a 500 with a sentinel reason phrase and body, and a 404 with a sentinel body.
            server.dispatcher = TestGateways.answerEvery { MockResponse().setStatus("HTTP/1.1 500 SENTINEL").setBody("SENTINEL body") }
            errors += errorsFor(plain, base)
            server.dispatcher = TestGateways.answerEvery { MockResponse().setResponseCode(404).setBody("SENTINEL body") }
            errors += errorsFor(plain, base)

            // RESPONSE_TOO_LARGE: an over-cap body of sentinel bytes.
            val big = "SENTINEL".repeat(TestGateways.FAST.maxBodyBytes.toInt() / 8 + 1)
            server.dispatcher = TestGateways.answerEvery { MockResponse().setBody(Buffer().writeUtf8(big)) }
            errors += errorsFor(plain, base)

            // UNEXPECTED: an interceptor that throws a sentinel message.
            server.dispatcher = TestGateways.answerEvery { MockResponse().setBody("unused") }
            val throwing = TestGateways.against(
                server,
                interceptor = TestGateways.throwingInterceptor(RuntimeException("SENTINEL")),
                nutritionEndpoint = serverEndpoint,
            )
            errors += errorsFor(throwing, base)
        } finally {
            listOf(server, blocked, https).forEach { runCatching { it.shutdown() } }
        }
        return errors
    }

    @Test
    fun `every failure category produced from sentinel fixtures carries no sentinel for each call type`() = runTest {
        val errors = collectEveryCategory()

        assertEquals(GatewayException.Category.entries.toSet(), errors.map { it.category }.toSet())
        assertEquals(CallType.entries.toSet(), errors.map { it.callType }.toSet())
        errors.forEach(::assertClean)
    }

    @Test
    fun `sentinel errors carry no cause and no suppressed exception`() = runTest {
        val server = TestGateways.server()
        try {
            server.dispatcher = TestGateways.answerEvery { MockResponse().setBody("unused") }
            val throwing = TestGateways.against(
                server,
                interceptor = TestGateways.throwingInterceptor(
                    IllegalStateException("SENTINEL", java.io.IOException("SENTINEL")).apply { addSuppressed(RuntimeException("SENTINEL")) },
                ),
            )
            val unknownDns = TestGateways.RecordingDns(mapOf(host to listOf(java.net.UnknownHostException("SENTINEL host"))))
            val unknown = TestGateways.against(server, dns = unknownDns)

            for (error in listOf(
                errorOf(throwing.fetchPage(TestGateways.urlOf(server, sentinelPath))),
                errorOf(unknown.fetchImage("http://$host$sentinelPath")),
            )) {
                assertClean(error)
                assertNull(error.cause)
                assertTrue(error.suppressed.isEmpty())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `an unresolvable sentinel host is reduced to its exception class name`() = runTest {
        val dns = TestGateways.RecordingDns(mapOf(host to listOf(java.net.UnknownHostException("SENTINEL host"))))
        val server = TestGateways.server()
        try {
            val gateway = TestGateways.against(server, dns = dns)

            val error = errorOf(gateway.fetchPage("http://$host$sentinelPath"))

            assertEquals(GatewayException.Category.CONNECTION_FAILED, error.category)
            assertEquals("UnknownHostException", error.causeType)
            assertClean(error)
        } finally {
            server.shutdown()
        }
    }
}
