package ie.pantry.data.gateway

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** R3's connect-time check and route choice, proven on the transport pieces alone (DR9): no gateway, no client. */
class GatewayTransportTest {

    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private val server = MockWebServer()

    @Before
    fun startServer() {
        server.start(loopback, 0)
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun answering(vararg addresses: InetAddress): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = addresses.toList()
    }

    private val firstInsideEachRange = listOf(
        "0.0.0.0", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.1.1",
        "224.0.0.1", "::", "::1", "fc00::1", "fe80::1", "fec0::1", "ff02::1",
    )

    @Test
    fun `guarded socket refuses the first fixture of every blocked range before connecting`() {
        for (literal in firstInsideEachRange) {
            GuardedSocket(ConnectionTargetPolicy.STRICT).use { socket ->
                assertFailsWith<AddressRefusedException>(literal) {
                    socket.connect(InetSocketAddress(InetAddress.getByName(literal), server.port), 1_000)
                }
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `guarded socket refuses a non exempt loopback port while the live server records no request`() {
        val exemptElsewhere = ConnectionTargetPolicy(setOf(InetSocketAddress(loopback, server.port + 1)))

        GuardedSocket(exemptElsewhere).use { socket ->
            assertFailsWith<AddressRefusedException> {
                socket.connect(InetSocketAddress(loopback, server.port), 1_000)
            }
            assertTrue(!socket.isConnected)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `guarded socket refuses an unresolved address`() {
        GuardedSocket(ConnectionTargetPolicy.STRICT).use { socket ->
            assertFailsWith<AddressRefusedException> {
                socket.connect(InetSocketAddress.createUnresolved("example.invalid", 80), 1_000)
            }
        }
    }

    @Test
    fun `guarded socket connects to an exempt address and port`() {
        val policy = ConnectionTargetPolicy(setOf(InetSocketAddress(loopback, server.port)))

        GuardedSocket(policy).use { socket ->
            socket.connect(InetSocketAddress(loopback, server.port), 1_000)
            assertTrue(socket.isConnected)
        }
    }

    @Test
    fun `every socket factory overload returns a guarded socket`() {
        val policy = ConnectionTargetPolicy(setOf(InetSocketAddress(loopback, server.port)))
        val factory = GuardedSocketFactory(policy)
        val sockets = listOf(
            factory.createSocket(),
            factory.createSocket("127.0.0.1", server.port),
            factory.createSocket("127.0.0.1", server.port, loopback, 0),
            factory.createSocket(loopback, server.port),
            factory.createSocket(loopback, server.port, loopback, 0),
        )
        try {
            for (socket in sockets) assertIs<GuardedSocket>(socket)
        } finally {
            sockets.forEach { it.close() }
        }

        val strict = GuardedSocketFactory(ConnectionTargetPolicy.STRICT)
        assertFailsWith<AddressRefusedException> { strict.createSocket("127.0.0.1", server.port) }
        assertFailsWith<AddressRefusedException> { strict.createSocket(loopback, server.port) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `target filtering dns keeps only allowed addresses from a mixed answer`() {
        val blocked = InetAddress.getByName("10.0.0.1")
        val allowed = InetAddress.getByName("93.184.216.34")
        val dns = TargetFilteringDns(answering(blocked, allowed), ConnectionTargetPolicy.STRICT)

        assertContentEquals(listOf(allowed), dns.lookup("example.test"))
    }

    @Test
    fun `target filtering dns throws address refused when every answer is blocked`() {
        val dns = TargetFilteringDns(
            answering(InetAddress.getByName("10.0.0.1"), InetAddress.getByName("::1")),
            ConnectionTargetPolicy.STRICT,
        )

        assertFailsWith<AddressRefusedException> { dns.lookup("example.test") }
    }

    @Test
    fun `target filtering dns exempts by address only`() {
        val exempting = ConnectionTargetPolicy(setOf(InetSocketAddress(loopback, 4000)))
        val dns = TargetFilteringDns(answering(loopback, InetAddress.getByName("10.0.0.1")), exempting)

        assertContentEquals(listOf(loopback), dns.lookup("example.test"))
    }

    /** An upstream that offers [total] bytes and counts how many it actually hands out. */
    private class CountingSource(total: Int, delegate: Buffer = Buffer().write(ByteArray(total) { 7 })) :
        ForwardingSource(delegate) {
        var delivered = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            val n = super.read(sink, byteCount)
            if (n > 0) delivered += n
            return n
        }
    }

    private fun readAll(source: Source): ByteArray = source.buffer().readByteArray()

    @Test
    fun `capped source pulls at most cap plus one bytes and throws past the cap`() {
        for (cap in listOf(1L, 10L, 8_192L, 20_000L)) {
            val upstream = CountingSource(total = 100_000)

            assertFailsWith<ResponseTooLargeException>("cap $cap") { readAll(CappedSource(upstream, cap)) }

            assertTrue(upstream.delivered <= cap + 1, "cap $cap delivered ${upstream.delivered}")
        }
    }

    @Test
    fun `capped source passes a body of exactly the cap`() {
        val upstream = CountingSource(total = 5_000)

        val bytes = readAll(CappedSource(upstream, 5_000))

        assertEquals(5_000, bytes.size)
        assertEquals(5_000L, upstream.delivered)
    }

    private fun builtClient() = buildGatewayClient(GatewayPolicy.DEFAULT, ConnectionTargetPolicy.STRICT, GatewaySeams.PRODUCTION)

    @Test
    fun `built client is direct cookie less cache less and never follows redirects or retries`() {
        val client = builtClient()

        assertEquals(Proxy.NO_PROXY, client.proxy)
        assertEquals(CookieJar.NO_COOKIES, client.cookieJar)
        assertNull(client.cache)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
        assertEquals(okhttp3.Authenticator.NONE, client.authenticator)
        assertEquals(0, client.connectionPool.connectionCount())
    }

    @Test
    fun `built client uses the policy timeouts and no call timeout`() {
        val client = builtClient()

        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
        assertEquals(0, client.callTimeoutMillis)
    }

    @Test
    fun `built client installs the guarded socket factory the filtering dns and exactly one cap interceptor`() {
        val client = builtClient()

        assertIs<GuardedSocketFactory>(client.socketFactory)
        assertIs<TargetFilteringDns>(client.dns)
        assertEquals(1, client.networkInterceptors.size)
        assertIs<RawByteCapInterceptor>(client.networkInterceptors.single())
        assertTrue(client.interceptors.isEmpty())
        val names = (client.interceptors + client.networkInterceptors).map { it.javaClass.simpleName }
        assertTrue(names.none { "Logging" in it }, names.toString())
    }

    @Test
    fun `production seams carry no exemptions no tls override and no interceptor`() {
        val seams = GatewaySeams.PRODUCTION

        assertTrue(seams.exemptTargets.isEmpty())
        assertNull(seams.tls)
        assertNull(seams.interceptor)
    }

    @Test
    fun `production seams use the system resolver the Open Food Facts endpoint and no event listener`() {
        val seams = GatewaySeams.PRODUCTION

        assertEquals(Dns.SYSTEM, seams.dns)
        assertEquals(EventListener.NONE, seams.eventListener)
        assertEquals(NutritionQuery.ENDPOINT, seams.nutritionEndpoint)
        assertNotNull(seams.backoffDelay)
    }
}
