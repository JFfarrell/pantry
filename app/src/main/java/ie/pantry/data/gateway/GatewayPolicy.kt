package ie.pantry.data.gateway

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The limits shared by all three call types. Invariant violations fail with constant messages. */
data class GatewayPolicy(
    val connectTimeout: Duration = 10.seconds,
    val readTimeout: Duration = 15.seconds,
    val attemptTimeout: Duration = 20.seconds,
    val maxBodyBytes: Long = 5_242_880L,
    val maxRedirects: Int = 5,
    val retryCount: Int = 2,
    val backoff: List<Duration> = listOf(500.milliseconds, 1.seconds),
) {
    init {
        require(connectTimeout > Duration.ZERO) { "connectTimeout must be positive" }
        require(readTimeout > Duration.ZERO) { "readTimeout must be positive" }
        require(attemptTimeout >= connectTimeout) { "attemptTimeout must be at least connectTimeout" }
        require(maxBodyBytes > 0) { "maxBodyBytes must be positive" }
        require(maxRedirects >= 0) { "maxRedirects must not be negative" }
        require(retryCount >= 0) { "retryCount must not be negative" }
        require(backoff.size == retryCount) { "backoff must have one entry per retry" }
        require(backoff.all { it >= Duration.ZERO }) { "backoff delays must not be negative" }
    }

    companion object {
        val DEFAULT: GatewayPolicy = GatewayPolicy()
    }
}
