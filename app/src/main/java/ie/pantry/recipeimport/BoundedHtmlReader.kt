package ie.pantry.recipeimport

import ie.pantry.data.gateway.BodyEncoding
import ie.pantry.data.gateway.FetchedBody
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

/** The most decompressed bytes the importer reads from one body. */
const val DECOMPRESSED_CAP_BYTES: Long = 10L * 1024 * 1024

/** The most `<` bytes a page may produce. An image body passes [Long.MAX_VALUE] instead. */
internal const val PAGE_MAX_TAG_BYTES: Long = 200_000L

/**
 * Wraps a decompressing stream and refuses to produce more than [cap] bytes. It asks its delegate for at most
 * `cap + 1 - produced` bytes per read, so the cap is applied while streaming and a bomb is never inflated whole.
 * It throws [ContentTooLargeException] once more than [cap] bytes, or more than [maxTagBytes] `<` bytes, have been
 * produced, and checks [job] for cancellation before every read.
 */
internal class CappedInputStream(
    delegate: InputStream,
    private val cap: Long,
    private val maxTagBytes: Long,
    private val job: Job,
) : FilterInputStream(delegate) {

    /** The bytes produced so far, for tests. */
    var produced: Long = 0
        private set

    private var tagBytes: Long = 0

    override fun read(): Int {
        job.ensureActive()
        val single = ByteArray(1)
        val count = read(single, 0, 1)
        return if (count <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        job.ensureActive()
        if (len == 0) return 0
        val allowance = cap + 1 - produced
        val count = `in`.read(b, off, minOf(len.toLong(), allowance).toInt())
        if (count > 0) {
            produced += count
            for (i in off until off + count) if (b[i] == LESS_THAN) tagBytes++
            if (produced > cap || tagBytes > maxTagBytes) throw ContentTooLargeException()
        }
        return count
    }

    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        val scratch = ByteArray(SKIP_BUFFER)
        var skipped = 0L
        while (skipped < n) {
            val count = read(scratch, 0, minOf(scratch.size.toLong(), n - skipped).toInt())
            if (count < 0) break
            skipped += count
        }
        return skipped
    }

    override fun markSupported(): Boolean = false

    override fun mark(readlimit: Int) = Unit

    override fun reset() {
        throw IOException("mark and reset are not supported")
    }

    private companion object {
        const val LESS_THAN: Byte = '<'.code.toByte()
        const val SKIP_BUFFER = 8192
    }
}

/**
 * Opens [bytes] as a capped stream: identity bodies read directly, gzip bodies through a [GZIPInputStream]. Any
 * other encoding is a defect in the caller, which rejects it before reading.
 */
internal fun openBounded(
    bytes: ByteArray,
    encoding: BodyEncoding,
    cap: Long,
    maxTagBytes: Long,
    job: Job,
): CappedInputStream {
    val raw = ByteArrayInputStream(bytes)
    val decoded: InputStream = when (encoding) {
        BodyEncoding.IDENTITY -> raw
        BodyEncoding.GZIP -> GZIPInputStream(raw)
        BodyEncoding.OTHER -> throw IllegalArgumentException("Unsupported encoding")
    }
    return CappedInputStream(decoded, cap, maxTagBytes, job)
}

/** Media type, charset and bounded parse of a fetched page. */
internal object BoundedHtmlReader {

    /** True for `text/html` and `application/xhtml+xml`, whatever the case or parameters. A missing type is not HTML. */
    fun isHtml(mediaType: String?): Boolean {
        val essence = mediaType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return essence == "text/html" || essence == "application/xhtml+xml"
    }

    /** The header's `charset` parameter, or null when it is absent or names no supported charset. */
    fun headerCharset(mediaType: String?): String? {
        val parameters = mediaType?.substringAfter(';', "") ?: return null
        for (parameter in parameters.split(';')) {
            val name = parameter.substringBefore('=').trim()
            if (!name.equals("charset", ignoreCase = true) || !parameter.contains('=')) continue
            val value = parameter.substringAfter('=').trim().trim('"', '\'').trim()
            if (value.isEmpty()) return null
            return try {
                if (Charset.isSupported(value)) value else null
            } catch (e: IllegalCharsetNameException) {
                null
            }
        }
        return null
    }

    /**
     * Parses [body] with jsoup over a [CappedInputStream], never executing script or loading a sub-resource.
     * [onStream] sees the stream first, so a test can read how many bytes it produced.
     *
     * @throws Exception any failure; the importer re-wraps it with [importFailure].
     */
    fun parse(
        body: FetchedBody,
        baseUri: String,
        cap: Long,
        job: Job,
        onStream: (CappedInputStream) -> Unit,
    ): Document {
        val stream = openBounded(body.bytes, body.encoding, cap, PAGE_MAX_TAG_BYTES, job)
        onStream(stream)
        return stream.use { Jsoup.parse(it, headerCharset(body.mediaType), baseUri, Parser.htmlParser()) }
    }
}
