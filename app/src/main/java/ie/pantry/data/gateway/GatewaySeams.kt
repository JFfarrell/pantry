package ie.pantry.data.gateway

import java.net.InetSocketAddress
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration
import kotlinx.coroutines.delay
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.Interceptor

/** Trust for a test certificate. Never set in production. */
internal class TlsOverride(val socketFactory: SSLSocketFactory, val trustManager: X509TrustManager)

/**
 * The test-only substitutions of the gateway. Every parameter is a `val`, so [PRODUCTION] cannot be mutated after
 * construction, and production never selects a test seam. [exemptTargets] holds exact (address, port) pairs let
 * through the blocked-range check, which is how a test reaches one loopback server.
 */
internal class GatewaySeams(
    val dns: Dns = Dns.SYSTEM,
    val exemptTargets: Set<InetSocketAddress> = emptySet(),
    val tls: TlsOverride? = null,
    val interceptor: Interceptor? = null,
    val eventListener: EventListener = EventListener.NONE,
    val nutritionEndpoint: HttpUrl = NutritionQuery.ENDPOINT,
    val backoffDelay: suspend (Duration) -> Unit = { delay(it) },
) {
    companion object {
        val PRODUCTION: GatewaySeams = GatewaySeams()
    }
}
