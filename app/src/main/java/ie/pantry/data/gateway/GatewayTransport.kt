package ie.pantry.data.gateway

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.time.toJavaDuration
import okhttp3.CookieJar
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer

/**
 * Builds the one client: direct connections, no cookies, no cache, no redirect following, no silent retry, no
 * pooled connections, the policy's timeouts, and the connection guards and byte cap installed. The per-call
 * deadline is set on each call, so the client-wide call timeout stays off.
 */
internal fun buildGatewayClient(policy: GatewayPolicy, targets: ConnectionTargetPolicy, seams: GatewaySeams): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .connectTimeout(policy.connectTimeout.toJavaDuration())
        .readTimeout(policy.readTimeout.toJavaDuration())
        .writeTimeout(policy.readTimeout.toJavaDuration())
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .dns(TargetFilteringDns(seams.dns, targets))
        .socketFactory(GuardedSocketFactory(targets))
        .eventListener(seams.eventListener)
        .addNetworkInterceptor(RawByteCapInterceptor(policy.maxBodyBytes))
    seams.tls?.let { builder.sslSocketFactory(it.socketFactory, it.trustManager) }
    seams.interceptor?.let { builder.addInterceptor(it) }
    return builder.build()
}

/**
 * Counts wire bytes: asks upstream for at most `cap + 1 - consumed` bytes, and throws
 * [ResponseTooLargeException] once more than `cap` bytes have been consumed.
 */
internal class CappedSource(delegate: Source, private val cap: Long) : ForwardingSource(delegate) {
    private var consumed = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val allowed = cap + 1 - consumed
        if (allowed <= 0) throw ResponseTooLargeException()
        val read = super.read(sink, minOf(byteCount, allowed))
        if (read > 0) consumed += read
        if (consumed > cap) throw ResponseTooLargeException()
        return read
    }
}

/**
 * A network interceptor, so it sits below any content decoding and counts wire bytes whatever a later change does
 * to transparent decompression.
 */
internal class RawByteCapInterceptor(private val cap: Long) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val capped = CappedSource(body.source(), cap).buffer()
        return response.newBuilder()
            .body(capped.asResponseBody(body.contentType(), body.contentLength()))
            .build()
    }
}

/**
 * A socket that asks [ConnectionTargetPolicy.permits] about the address it is about to connect to, before any
 * connection is attempted. The checked address is by definition the connected address, so a resolver cannot
 * answer one thing to the check and another to the connection. TLS sockets wrap this raw socket, so https is
 * covered as well.
 */
internal class GuardedSocket(private val policy: ConnectionTargetPolicy) : Socket() {

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val target = endpoint as? InetSocketAddress
        val address = target?.address
        if (target == null || target.isUnresolved || address == null || !policy.permits(address, target.port)) {
            throw AddressRefusedException()
        }
        super.connect(endpoint, timeout)
    }
}

/** Every socket it creates is a [GuardedSocket]; the overloads that name a host and port connect through the guard. */
internal class GuardedSocketFactory(private val policy: ConnectionTargetPolicy) : SocketFactory() {

    override fun createSocket(): Socket = GuardedSocket(policy)

    override fun createSocket(host: String?, port: Int): Socket =
        connected(InetSocketAddress(host, port), localAddress = null, localPort = 0)

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        connected(InetSocketAddress(host, port), localHost, localPort)

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        connected(InetSocketAddress(host, port), localAddress = null, localPort = 0)

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        connected(InetSocketAddress(address, port), localAddress, localPort)

    private fun connected(target: InetSocketAddress, localAddress: InetAddress?, localPort: Int): Socket {
        val socket = GuardedSocket(policy)
        try {
            if (localAddress != null) socket.bind(InetSocketAddress(localAddress, localPort))
            socket.connect(target, 0)
        } catch (e: Exception) {
            socket.close()
            throw e
        }
        return socket
    }
}

/**
 * Wraps a resolver and drops every address the policy refuses at resolution time, so the client connects only to
 * allowed addresses of a mixed answer. This only picks the route; the socket guard is what enforces the policy.
 */
internal class TargetFilteringDns(private val delegate: Dns, private val policy: ConnectionTargetPolicy) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val allowed = delegate.lookup(hostname).filter { policy.permitsForResolution(it) }
        if (allowed.isEmpty()) throw AddressRefusedException()
        return allowed
    }
}
