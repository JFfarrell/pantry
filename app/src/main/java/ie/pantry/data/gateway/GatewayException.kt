package ie.pantry.data.gateway

import java.io.IOException
import java.io.InterruptedIOException
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass

/**
 * A content-free gateway failure. Its constructor takes no `String`, so no URL, host, address, header, body or
 * search term can reach the message, whatever a call site does. The message is built from enum names and integers
 * only. It has no cause and no suppressed exceptions, and the gateway returns it inside [GatewayResult.Failed]
 * rather than throwing it.
 */
class GatewayException internal constructor(
    val category: Category,
    val callType: CallType,
    val statusCode: Int?,
    val attempts: Int,
    causeClass: KClass<out Throwable>?,
) : RuntimeException(
    gatewayMessage(category, callType, statusCode, attempts, causeClass),
    null,
    false,
    true,
) {
    /** The simple name of the top-level cause's class, or null for policy refusals and internal markers. */
    val causeType: String? = causeClass?.java?.simpleName?.takeIf { it.isNotEmpty() }

    init {
        require((statusCode != null) == (category == Category.HTTP_STATUS)) {
            if (category == Category.HTTP_STATUS) "HTTP_STATUS requires a status code" else "only HTTP_STATUS carries a status code"
        }
    }

    enum class Category {
        INVALID_REQUEST,
        SCHEME_REFUSED,
        ADDRESS_REFUSED,
        TOO_MANY_REDIRECTS,
        TIMEOUT,
        CONNECTION_FAILED,
        HTTP_STATUS,
        RESPONSE_TOO_LARGE,
        UNEXPECTED,
    }
}

private fun gatewayMessage(
    category: GatewayException.Category,
    callType: CallType,
    statusCode: Int?,
    attempts: Int,
    causeClass: KClass<out Throwable>?,
): String {
    val status = statusCode?.toString() ?: "none"
    val cause = causeClass?.java?.simpleName?.takeIf { it.isNotEmpty() } ?: "none"
    return "Gateway failure: category=$category callType=$callType status=$status attempts=$attempts cause=$cause"
}

/** Thrown inside OkHttp's stack when a connection target is refused. It never leaves the gateway. */
internal class AddressRefusedException : IOException("Address refused")

/** Thrown inside OkHttp's stack when a body exceeds the raw-byte cap. It never leaves the gateway. */
internal class ResponseTooLargeException : IOException("Response too large")

/** A refusal decided by the policy, with no cause. */
internal fun gatewayError(
    category: GatewayException.Category,
    callType: CallType,
    attempts: Int,
    statusCode: Int? = null,
): GatewayException = GatewayException(category, callType, statusCode, attempts, null)

/**
 * The CFC-4 re-wrapping rule: turns a third-party or internal exception into a content-free [GatewayException].
 * Other features copy these five rules:
 *
 * (a) The category comes only from `is`-checks on the thrown type, its cause chain and its suppressed exceptions
 *     (each visited once), and never from a message.
 * (b) `causeType` is the top-level cause's class simple name; it is null for the two internal markers.
 * (c) The cause is not chained and its suppressed exceptions are not copied.
 * (d) The cause's stack frames are copied.
 * (e) Nothing is logged.
 *
 * Classification takes the first matching rule over the whole visited set, in this order: address-refused marker,
 * response-too-large marker, interrupted I/O (timeouts), any other I/O failure, then anything else.
 */
internal fun gatewayFailure(cause: Throwable, callType: CallType, attempts: Int): GatewayException {
    val visited: MutableSet<Throwable> = Collections.newSetFromMap(IdentityHashMap())
    val pending = ArrayDeque<Throwable>()
    pending.add(cause)
    while (pending.isNotEmpty()) {
        val next = pending.removeFirst()
        if (!visited.add(next)) continue
        next.cause?.let(pending::add)
        pending.addAll(next.suppressed)
    }

    val category = when {
        visited.any { it is AddressRefusedException } -> GatewayException.Category.ADDRESS_REFUSED
        visited.any { it is ResponseTooLargeException } -> GatewayException.Category.RESPONSE_TOO_LARGE
        visited.any { it is InterruptedIOException } -> GatewayException.Category.TIMEOUT
        visited.any { it is IOException } -> GatewayException.Category.CONNECTION_FAILED
        else -> GatewayException.Category.UNEXPECTED
    }
    val isMarker = category == GatewayException.Category.ADDRESS_REFUSED ||
        category == GatewayException.Category.RESPONSE_TOO_LARGE
    val error = GatewayException(category, callType, null, attempts, if (isMarker) null else cause::class)
    error.stackTrace = cause.stackTrace
    return error
}

/** True only for `TIMEOUT`, `CONNECTION_FAILED` and `HTTP_STATUS` 500 to 599. */
internal fun GatewayException.isTransient(): Boolean = when (category) {
    GatewayException.Category.TIMEOUT, GatewayException.Category.CONNECTION_FAILED -> true
    GatewayException.Category.HTTP_STATUS -> statusCode in 500..599
    else -> false
}
