package ie.pantry.recipeimport

import ie.pantry.data.gateway.GatewayException
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass

sealed interface ImportResult {
    data class Drafted(val draft: ImportDraft) : ImportResult

    data class Failed(val error: ImportException) : ImportResult
}

/**
 * A content-free import failure. Its constructor takes no `String`, so no URL, HTML, title or line can reach the
 * message. It has no cause and no suppressed exceptions, and the importer returns it inside [ImportResult.Failed].
 */
class ImportException internal constructor(
    val kind: Kind,
    val gatewayCategory: GatewayException.Category?,
    val statusCode: Int?,
    causeClass: KClass<out Throwable>?,
) : RuntimeException(importMessage(kind, gatewayCategory, statusCode, causeClass), null, false, true) {
    val causeType: String? = causeClass?.java?.simpleName?.takeIf { it.isNotEmpty() }

    init {
        require((gatewayCategory != null) == (kind == Kind.FETCH_FAILED)) {
            "a gateway category goes with FETCH_FAILED and with nothing else"
        }
        require((statusCode != null) == (gatewayCategory == GatewayException.Category.HTTP_STATUS)) {
            "a status code goes with HTTP_STATUS and with nothing else"
        }
    }

    enum class Kind { FETCH_FAILED, NOT_HTML, UNSUPPORTED_ENCODING, UNREADABLE_CONTENT, CONTENT_TOO_LARGE, NOT_RECOGNISED }
}

private fun importMessage(
    kind: ImportException.Kind,
    category: GatewayException.Category?,
    statusCode: Int?,
    causeClass: KClass<out Throwable>?,
): String {
    val cause = causeClass?.java?.simpleName?.takeIf { it.isNotEmpty() } ?: "none"
    return "Import failure: kind=$kind gateway=${category ?: "none"} status=${statusCode ?: "none"} cause=$cause"
}

/** Thrown inside the reader when a body passes a cap. It never leaves the importer. */
internal class ContentTooLargeException : IOException("Content too large")

/** A decision with no cause: not HTML, unsupported encoding, not recognised. */
internal fun importError(kind: ImportException.Kind): ImportException = ImportException(kind, null, null, null)

/** Copies the gateway failure's category, status code and stack frames, never the exception itself. */
internal fun fetchFailure(error: GatewayException): ImportException {
    val failure = ImportException(ImportException.Kind.FETCH_FAILED, error.category, error.statusCode, null)
    failure.stackTrace = error.stackTrace
    return failure
}

/**
 * The CFC-4 re-wrapping rule, copied from `gatewayFailure`:
 *
 * (a) The kind comes only from `is`-checks on the thrown type, its cause chain and its suppressed exceptions
 *     (each visited once), and never from a message.
 * (b) `causeType` is the top-level cause's class simple name; it is null for the too-large marker.
 * (c) The cause is not chained and its suppressed exceptions are not copied.
 * (d) The cause's stack frames are copied.
 * (e) Nothing is logged.
 */
internal fun importFailure(cause: Throwable): ImportException {
    val visited: MutableSet<Throwable> = Collections.newSetFromMap(IdentityHashMap())
    val pending = ArrayDeque<Throwable>()
    pending.add(cause)
    while (pending.isNotEmpty()) {
        val next = pending.removeFirst()
        if (!visited.add(next)) continue
        next.cause?.let(pending::add)
        pending.addAll(next.suppressed)
    }
    val tooLarge = visited.any { it is ContentTooLargeException }
    val failure = if (tooLarge) {
        ImportException(ImportException.Kind.CONTENT_TOO_LARGE, null, null, null)
    } else {
        ImportException(ImportException.Kind.UNREADABLE_CONTENT, null, null, cause::class)
    }
    failure.stackTrace = cause.stackTrace
    return failure
}
