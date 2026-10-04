package ie.pantry.data.gateway

/** The outcome of a gateway call. The gateway returns a [Failed] value; it never throws one. */
sealed interface GatewayResult {
    data class Fetched(val body: FetchedBody) : GatewayResult
    data class Failed(val error: GatewayException) : GatewayResult
}

/** Which of the three gateway operations a call was. It labels errors only and never selects a policy. */
enum class CallType { PAGE_FETCH, IMAGE_FETCH, NUTRITION_LOOKUP }

/** How the bytes of a [FetchedBody] are still encoded, from the `Content-Encoding` header. */
enum class BodyEncoding {
    /** The header was absent or `identity`. */
    IDENTITY,

    /** The header was `gzip` or `x-gzip`. The caller decompresses under its own streaming cap. */
    GZIP,

    /** Anything else, including a list of encodings. The caller treats the body as unreadable. */
    OTHER;

    internal companion object {
        /** Classifies a raw `Content-Encoding` header value, trimmed and case-insensitive. */
        fun of(header: String?): BodyEncoding = when (header?.trim()?.lowercase()) {
            null, "identity" -> IDENTITY
            "gzip", "x-gzip" -> GZIP
            else -> OTHER
        }
    }
}

/**
 * The raw, de-chunked body bytes read off the wire, at most the policy's byte cap, still content-encoded.
 * [mediaType] is the raw `Content-Type` value, or null when the response had none.
 */
class FetchedBody internal constructor(val bytes: ByteArray, val encoding: BodyEncoding, val mediaType: String?) {
    /** Prints only the size and the encoding, so a body never reaches a log through this method. */
    override fun toString(): String = "FetchedBody(size=${bytes.size}, encoding=$encoding)"
}
