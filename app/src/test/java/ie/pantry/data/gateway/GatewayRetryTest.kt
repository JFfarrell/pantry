package ie.pantry.data.gateway

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.testutil.TestGateways
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** R7 (bounded retry and back-off), R6 AC1's whole-call bound and R1 AC4 (cancellation). */
class GatewayRetryTest {

    private lateinit var server: MockWebServer
    private val backoffSchedule = listOf(500.milliseconds, 1.seconds)

    @Before
    fun setUp() {
        server = TestGateways.server()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private class Rig(
        val gateway: ExternalDataGateway,
        val listener: TestGateways.RecordingEventListener,
        val backoff: TestGateways.RecordingBackoff,
    )

    private fun rig(vararg servers: MockWebServer = arrayOf(server), interceptor: okhttp3.Interceptor? = null): Rig {
        val listener = TestGateways.RecordingEventListener()
        val backoff = TestGateways.RecordingBackoff()
        return Rig(TestGateways.against(*servers, listener = listener, backoff = backoff, interceptor = interceptor), listener, backoff)
    }

    private fun callOf(type: CallType, gateway: ExternalDataGateway, url: String): suspend () -> GatewayResult = when (type) {
        CallType.PAGE_FETCH -> { { gateway.fetchPage(url) } }
        CallType.IMAGE_FETCH -> { { gateway.fetchImage(url) } }
        CallType.NUTRITION_LOOKUP -> { { gateway.lookupNutrition("flour") } }
    }

    private fun failure(result: GatewayResult): GatewayException = assertIs<GatewayResult.Failed>(result).error

    private suspend fun TestScope.assertRetriedThreeTimes(expected: (GatewayException) -> Unit, response: () -> MockResponse) {
        server.dispatcher = TestGateways.answerEvery(response)
        for (type in CallType.entries) {
            val rig = rig()
            val virtualStart = testScheduler.currentTime
            val error = failure(callOf(type, rig.gateway, TestGateways.urlOf(server, "/"))())

            assertEquals(3, rig.listener.callStartTimesMillis.size, "$type attempts started")
            assertEquals(backoffSchedule, rig.backoff.delays.toList(), "$type back-off")
            assertEquals(1500, testScheduler.currentTime - virtualStart, "$type virtual time")
            assertEquals(3, error.attempts, "$type")
            assertEquals(type, error.callType)
            expected(error)
        }
    }

    @Test
    fun `a dropped connection on every attempt is tried three times with the back off schedule for each call type`() =
        runTest {
            assertRetriedThreeTimes(
                { assertEquals(Category.CONNECTION_FAILED, it.category) },
            ) { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) }
        }

    @Test
    fun `a 503 on every attempt is tried three times then gives HTTP_STATUS 503 for each call type`() = runTest {
        val before = server.requestCount
        assertRetriedThreeTimes(
            {
                assertEquals(Category.HTTP_STATUS, it.category)
                assertEquals(503, it.statusCode)
            },
        ) { MockResponse().setResponseCode(503) }
        assertEquals(9, server.requestCount - before)
    }

    @Test
    fun `a transient failure then 200 succeeds with exactly two requests`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("second"))
        val rig = rig()

        val fetched = assertIs<GatewayResult.Fetched>(rig.gateway.fetchPage(TestGateways.urlOf(server, "/")))

        assertEquals("second", String(fetched.body.bytes))
        assertEquals(2, server.requestCount)
        assertEquals(listOf(500.milliseconds), rig.backoff.delays.toList())
    }

    @Test
    fun `a timeout is retried under the same bound`() = runTest {
        server.dispatcher = TestGateways.answerEvery { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val attemptBound = TestGateways.FAST.attemptTimeout.inWholeMilliseconds
        for (type in CallType.entries) {
            val rig = rig()
            val started = System.currentTimeMillis()

            val error = failure(callOf(type, rig.gateway, TestGateways.urlOf(server, "/"))())

            val elapsed = System.currentTimeMillis() - started
            assertEquals(Category.TIMEOUT, error.category, "$type")
            assertEquals(3, error.attempts)
            assertEquals(3, rig.listener.callStartTimesMillis.size)
            assertEquals(backoffSchedule, rig.backoff.delays.toList())
            assertTrue(elapsed <= 3 * attemptBound + 1_000, "$type took $elapsed ms")
        }
    }

    @Test
    fun `non retryable outcomes return after one attempt with no back off`() = runTest {
        val blocked = TestGateways.server().also { it.dispatcher = TestGateways.answerEvery { MockResponse().setBody("no") } }
        try {
            // HTTP statuses that are not 5xx.
            for (status in listOf(400, 404, 429)) {
                server.dispatcher = TestGateways.answerEvery { MockResponse().setResponseCode(status) }
                val before = server.requestCount
                for (type in CallType.entries) {
                    val rig = rig()
                    val error = failure(callOf(type, rig.gateway, TestGateways.urlOf(server, "/"))())
                    assertEquals(status, error.statusCode, "$type $status")
                    assertEquals(1, error.attempts)
                    assertTrue(rig.backoff.delays.isEmpty(), "$type $status")
                }
                assertEquals(3, server.requestCount - before, "one request per call for $status")
            }

            // Refusals decided before any connection.
            val pre = rig()
            assertEquals(Category.SCHEME_REFUSED, failure(pre.gateway.fetchPage("ftp://host/x")).category)
            assertEquals(Category.INVALID_REQUEST, failure(pre.gateway.fetchImage("ht!tp://x")).category)
            assertEquals(Category.INVALID_REQUEST, failure(pre.gateway.lookupNutrition(" ")).category)
            assertTrue(pre.backoff.delays.isEmpty())
            assertTrue(pre.listener.callStartTimesMillis.isEmpty())

            // An address the policy refuses, on a live listener that is not exempt.
            val refused = TestGateways.against(
                server,
                listener = TestGateways.RecordingEventListener(),
                backoff = TestGateways.RecordingBackoff(),
                nutritionEndpoint = TestGateways.urlOf(blocked, "/search").toHttpUrl(),
            )
            for (type in CallType.entries) {
                val error = failure(callOf(type, refused, TestGateways.urlOf(blocked, "/"))())
                assertEquals(Category.ADDRESS_REFUSED, error.category, "$type")
                assertEquals(1, error.attempts)
            }
            assertEquals(0, blocked.requestCount)

            // A chunked over-cap body.
            val cap = TestGateways.FAST.maxBodyBytes.toInt()
            server.dispatcher = TestGateways.answerEvery {
                MockResponse().setChunkedBody(Buffer().write(ByteArray(cap + 1)), 4096)
            }
            val before = server.requestCount
            for (type in CallType.entries) {
                val rig = rig()
                val error = failure(callOf(type, rig.gateway, TestGateways.urlOf(server, "/"))())
                assertEquals(Category.RESPONSE_TOO_LARGE, error.category, "$type")
                assertEquals(1, error.attempts)
                assertTrue(rig.backoff.delays.isEmpty())
            }
            assertEquals(3, server.requestCount - before)

            // UNEXPECTED is not transient.
            server.dispatcher = TestGateways.answerEvery { MockResponse().setBody("unused") }
            for (type in CallType.entries) {
                val rig = rig(interceptor = TestGateways.throwingInterceptor(IllegalStateException("boom")))
                val error = failure(callOf(type, rig.gateway, TestGateways.urlOf(server, "/"))())
                assertEquals(Category.UNEXPECTED, error.category, "$type")
                assertEquals(1, error.attempts)
                assertEquals(1, rig.listener.callStartTimesMillis.size, "$type")
                assertTrue(rig.backoff.delays.isEmpty())
            }
        } finally {
            blocked.shutdown()
        }
    }

    @Test
    fun `the cap applies per attempt so an at cap body after a 503 retry succeeds`() = runTest {
        val cap = TestGateways.FAST.maxBodyBytes.toInt()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(cap) { 1 })))
        val rig = rig()

        val fetched = assertIs<GatewayResult.Fetched>(rig.gateway.fetchPage(TestGateways.urlOf(server, "/")))

        assertEquals(cap, fetched.body.bytes.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cancelling the caller cancels the in flight call and starts no further attempt`() = runTest {
        server.dispatcher = TestGateways.answerEvery { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val rig = rig()
        var result: GatewayResult? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            result = rig.gateway.fetchPage(TestGateways.urlOf(server, "/"))
        }

        rig.listener.awaitCallStart(1)
        val cancelledAt = System.currentTimeMillis()
        val joinStart = System.nanoTime()
        job.cancel()
        job.join()
        val joinMillis = (System.nanoTime() - joinStart) / 1_000_000

        assertTrue(joinMillis < 1_000, "join took $joinMillis ms")
        assertTrue(job.isCancelled)
        assertNull(result)
        withContext(Dispatchers.Default) {
            val deadline = System.currentTimeMillis() + 500
            while (rig.listener.callFailedTimesMillis.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        }
        assertFalse(rig.listener.callFailedTimesMillis.isEmpty(), "callFailed must follow the cancel promptly")
        assertTrue(rig.listener.callFailedTimesMillis.first() - cancelledAt <= 500)
        withContext(Dispatchers.Default) { Thread.sleep(TestGateways.FAST.attemptTimeout.inWholeMilliseconds + 100) }
        assertEquals(1, rig.listener.callStartTimesMillis.size)
        assertTrue(rig.backoff.delays.isEmpty())
    }

    @Test
    fun `TOO_MANY_REDIRECTS is not retried`() = runTest {
        server.dispatcher = TestGateways.answerEvery { MockResponse().setResponseCode(302).setHeader("Location", "/loop") }
        val rig = rig()

        val error = failure(rig.gateway.fetchPage(TestGateways.urlOf(server, "/loop")))

        assertEquals(Category.TOO_MANY_REDIRECTS, error.category)
        assertEquals(6, server.requestCount)
        assertTrue(rig.backoff.delays.isEmpty())
        assertEquals(1, error.attempts)
    }

    @Test
    fun `a transient failure after a redirect restarts the next attempt from the initial url`() = runTest {
        for (second in listOf(
            MockResponse().setResponseCode(503),
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )) {
            val local = TestGateways.server()
            try {
                local.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/mid"))
                local.enqueue(second)
                local.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/mid"))
                local.enqueue(MockResponse().setBody("done"))
                val rig = rig(local)

                val fetched = assertIs<GatewayResult.Fetched>(rig.gateway.fetchPage(TestGateways.urlOf(local, "/start")))

                assertEquals("done", String(fetched.body.bytes))
                val paths = List(4) { local.takeRequest().path }
                assertEquals("/start", paths[0])
                assertEquals("/start", paths[2], "the second attempt starts from the initial URL")
                assertEquals("/mid", paths[3])
                assertEquals(listOf(500.milliseconds), rig.backoff.delays.toList())
            } finally {
                local.shutdown()
            }
        }
    }
}
