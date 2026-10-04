package ie.pantry.testutil

import ie.pantry.data.gateway.ExternalDataGateway
import ie.pantry.data.gateway.GatewayPolicy
import ie.pantry.data.gateway.GatewaySeams
import ie.pantry.data.gateway.TlsOverride
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Shared helpers for gateway tests: a loopback MockWebServer bound to the literal `127.0.0.1`, a TLS fixture, and
 * recording stand-ins for the resolver, the event listener and the back-off delay.
 *
 * The TLS keystore `app/src/test/resources/gateway/test-server.p12` was generated once with:
 * `keytool -genkeypair -alias test-server -keyalg EC -groupname secp256r1 -dname "CN=localhost"
 * -ext "SAN=dns:localhost,ip:127.0.0.1" -validity 36500 -storetype PKCS12
 * -keystore app/src/test/resources/gateway/test-server.p12 -storepass pantry-test -keypass pantry-test`
 */
object TestGateways {

    private const val KEYSTORE_RESOURCE = "/gateway/test-server.p12"
    private const val KEYSTORE_PASSWORD = "pantry-test"
    private const val ALIAS = "test-server"

    /** Small limits so failure paths run in seconds. Same shape as the default policy. */
    val FAST: GatewayPolicy = GatewayPolicy(
        connectTimeout = 1.seconds,
        readTimeout = 1.seconds,
        attemptTimeout = 2.seconds,
        maxBodyBytes = 64 * 1024L,
        maxRedirects = 5,
        retryCount = 2,
        backoff = listOf(500.milliseconds, 1.seconds),
    )

    private val loopback: InetAddress get() = InetAddress.getByName("127.0.0.1")

    /** Starts a plain-HTTP server bound to the literal `127.0.0.1`. */
    fun server(): MockWebServer = MockWebServer().apply { start(loopback, 0) }

    /**
     * Builds `<scheme>://127.0.0.1:<port><path>`. It never uses `server.url()`, whose host name `localhost` can
     * resolve to `::1`, and `::1` is not exempt.
     */
    fun urlOf(server: MockWebServer, path: String, scheme: String = "http"): String =
        "$scheme://127.0.0.1:${server.port}$path"

    /**
     * Builds a gateway over [servers], exempting each one's exact `(127.0.0.1, port)` pair. [dns] defaults to a
     * resolver with no script, so no test depends on the sandbox resolver. [nutritionEndpoint] defaults to
     * `/search` on the first server.
     */
    fun against(
        vararg servers: MockWebServer,
        policy: GatewayPolicy = FAST,
        dns: Dns = RecordingDns(emptyMap()),
        trustTestCert: Boolean = false,
        interceptor: Interceptor? = null,
        listener: EventListener = RecordingEventListener(),
        backoff: suspend (Duration) -> Unit = RecordingBackoff(),
        nutritionEndpoint: HttpUrl = urlOf(servers.first(), "/search").toHttpUrl(),
    ): ExternalDataGateway {
        val tls = if (trustTestCert) trustingTestCert().let { TlsOverride(it.socketFactory, it.trustManager) } else null
        val seams = GatewaySeams(
            dns = dns,
            exemptTargets = servers.map { InetSocketAddress(loopback, it.port) }.toSet(),
            tls = tls,
            interceptor = interceptor,
            eventListener = listener,
            nutritionEndpoint = nutritionEndpoint,
            backoffDelay = backoff,
        )
        return ExternalDataGateway(policy, seams, Dispatchers.IO)
    }

    /** An application interceptor that throws [error] from inside the client. */
    fun throwingInterceptor(error: Throwable): Interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = throw error
    }

    /** Starts a TLS server, bound to `127.0.0.1`, using the fixture keystore. */
    fun httpsServer(): MockWebServer {
        val keyStore = loadKeyStore()
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, KEYSTORE_PASSWORD.toCharArray()) }.keyManagers
        val context = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
        return MockWebServer().apply {
            useHttps(context.socketFactory, false)
            start(loopback, 0)
        }
    }

    /** A client-side TLS pair that trusts only the fixture certificate. */
    class TestTls(val socketFactory: SSLSocketFactory, val trustManager: X509TrustManager)

    /**
     * Trusts only the fixture certificate. A PKIX trust manager ignores private-key entries, so the certificate
     * goes into a fresh in-memory key store as a certificate entry.
     */
    fun trustingTestCert(): TestTls {
        val certificate = loadKeyStore().getCertificate(ALIAS)
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry(ALIAS, certificate)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(trustStore) }
        val trustManager = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        return TestTls(context.socketFactory, trustManager)
    }

    private fun loadKeyStore(): KeyStore {
        val stream = checkNotNull(TestGateways::class.java.getResourceAsStream(KEYSTORE_RESOURCE)) {
            "missing $KEYSTORE_RESOURCE"
        }
        return KeyStore.getInstance("PKCS12").apply { stream.use { load(it, KEYSTORE_PASSWORD.toCharArray()) } }
    }

    /**
     * Answers every request with a fresh [response]. It overrides both `dispatch` and `peek`: MockWebServer reads
     * the connection-level socket policy, including disconnect-at-start, from `peek()` before reading a request,
     * so a dispatcher that overrides only `dispatch` would never disconnect at start.
     */
    fun answerEvery(response: () -> MockResponse): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = response()
        override fun peek(): MockResponse = response()
    }

    /**
     * A scripted resolver. Each hostname has a list of answers, one per lookup, where an answer is a
     * `List<InetAddress>` or a `Throwable` to throw. Once a script is exhausted its last answer repeats. A hostname
     * with no script throws [UnknownHostException], so it never reaches the platform resolver.
     */
    class RecordingDns(private val scripts: Map<String, List<Any>>) : Dns {
        private val counts = HashMap<String, Int>()
        val lookups = CopyOnWriteArrayList<String>()

        @Synchronized
        override fun lookup(hostname: String): List<InetAddress> {
            lookups += hostname
            val script = scripts[hostname] ?: throw UnknownHostException("no script")
            val index = counts.getOrDefault(hostname, 0)
            counts[hostname] = index + 1
            return when (val answer = script[minOf(index, script.size - 1)]) {
                is Throwable -> throw answer
                is List<*> -> answer.filterIsInstance<InetAddress>()
                else -> error("a DNS script answer is an address list or a Throwable")
            }
        }
    }

    /** Records `callStart`, `connectStart`, `connectionReleased` and `callFailed` for every call. */
    class RecordingEventListener : EventListener() {
        val callStartTimesMillis = CopyOnWriteArrayList<Long>()
        val connectStarts = CopyOnWriteArrayList<InetSocketAddress>()
        val connectionReleasedCount get() = released.size
        val callFailedTimesMillis = CopyOnWriteArrayList<Long>()

        private val released = CopyOnWriteArrayList<Long>()
        private val callStartSignals = ArrayList<CompletableDeferred<Unit>>()

        @Synchronized
        private fun signal(index: Int): CompletableDeferred<Unit> {
            while (callStartSignals.size <= index) callStartSignals += CompletableDeferred()
            return callStartSignals[index]
        }

        override fun callStart(call: Call) {
            val index = callStartTimesMillis.size
            callStartTimesMillis += System.currentTimeMillis()
            signal(index).complete(Unit)
        }

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: java.net.Proxy) {
            connectStarts += inetSocketAddress
        }

        override fun connectionReleased(call: Call, connection: okhttp3.Connection) {
            released += System.currentTimeMillis()
        }

        override fun callFailed(call: Call, ioe: java.io.IOException) {
            callFailedTimesMillis += System.currentTimeMillis()
        }

        /** Suspends until the n-th (1-based) `callStart` has happened, without racing it. */
        suspend fun awaitCallStart(n: Int) {
            signal(n - 1).await()
        }
    }

    /** Records each delay, then delays for real, which is virtual under `runTest`. */
    class RecordingBackoff : suspend (Duration) -> Unit {
        val delays = CopyOnWriteArrayList<Duration>()

        override suspend fun invoke(duration: Duration) {
            delays += duration
            delay(duration)
        }
    }
}
