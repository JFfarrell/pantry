package ie.pantry.recipeimport

import ie.pantry.data.gateway.ExternalDataGateway
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestGateways
import java.io.ByteArrayOutputStream
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/** Page fixtures and generated bodies for the import tests. */
object ImportFixtures {

    /** A committed page under `recipeimport/pages/`. */
    fun page(name: String): ByteArray {
        val path = "/recipeimport/pages/$name"
        val stream = checkNotNull(ImportFixtures::class.java.getResourceAsStream(path)) { "missing fixture $path" }
        return stream.use { it.readBytes() }
    }

    fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { sink ->
        GZIPOutputStream(sink).use { it.write(bytes) }
    }.toByteArray()

    /** A gzip stream that inflates to [inflatedBytes] zeros, written in chunks so it never holds them whole. */
    fun bomb(inflatedBytes: Long): ByteArray = ByteArrayOutputStream().also { sink ->
        val gzip = object : GZIPOutputStream(sink) {
            init {
                def.setLevel(Deflater.BEST_COMPRESSION)
            }
        }
        val chunk = ByteArray(1024 * 1024)
        var written = 0L
        while (written < inflatedBytes) {
            val size = minOf(chunk.size.toLong(), inflatedBytes - written).toInt()
            gzip.write(chunk, 0, size)
            written += size
        }
        gzip.close()
    }.toByteArray()

    /** A well-formed page of exactly [size] bytes: a fixed frame around filler text. */
    fun htmlOfSize(size: Int, filler: Char = 'a'): ByteArray {
        val head = "<html><body><p>".toByteArray()
        val tail = "</p></body></html>".toByteArray()
        val body = ByteArray(size)
        head.copyInto(body)
        tail.copyInto(body, size - tail.size)
        body.fill(filler.code.toByte(), head.size, size - tail.size)
        return body
    }

    /**
     * A JSON-LD block whose innermost container, a scalar-only `Recipe` object (a name and a one-step string, so it qualifies), sits at [depth] (the outermost
     * container is depth 1). With [viaGraph] the outermost container is an object holding `@graph`, so `@graph`
     * counts as one object plus one array.
     */
    fun deepJsonLd(depth: Int, viaGraph: Boolean): String {
        require(depth >= 2)
        val recipe = "{\"@type\":\"Recipe\",\"name\":\"Deep\",\"recipeInstructions\":\"Mix well\"}"
        val builder = StringBuilder()
        val arrays = if (viaGraph) {
            builder.append("{\"@graph\":")
            depth - 2
        } else {
            depth - 1
        }
        repeat(arrays) { builder.append('[') }
        builder.append(recipe)
        repeat(arrays) { builder.append(']') }
        if (viaGraph) builder.append('}')
        return builder.toString()
    }

    /**
     * Queues one page response. With [gzip] the body is gzipped and labelled `Content-Encoding: gzip`; otherwise a
     * [contentEncoding] label, if any, is sent as given over the bytes as given. A null [contentType] sends none.
     */
    fun enqueuePage(
        server: MockWebServer,
        body: ByteArray,
        contentType: String? = "text/html; charset=utf-8",
        gzip: Boolean = false,
        contentEncoding: String? = null,
    ) {
        val response = MockResponse().setBody(Buffer().write(if (gzip) gzip(body) else body))
        if (contentType != null) response.setHeader("Content-Type", contentType)
        if (gzip) response.setHeader("Content-Encoding", "gzip") else if (contentEncoding != null) response.setHeader("Content-Encoding", contentEncoding)
        server.enqueue(response)
    }

    /** An importer over [gateway] whose CPU work runs inline, so tests need no dispatcher plumbing. */
    internal fun importer(
        gateway: ExternalDataGateway,
        clock: Clock = MutableClock(),
        imageCheck: (ByteArray) -> DraftImage = { DraftImage.NoImage(NoImageCause.NOT_AN_IMAGE) },
        seams: ImportSeams = ImportSeams.PRODUCTION,
    ): RecipeImporter = RecipeImporter(gateway, clock, Dispatchers.Unconfined, imageCheck, seams)

    /** An importer over a gateway that exempts only [server]. */
    internal fun importer(
        server: MockWebServer,
        clock: Clock = MutableClock(),
        imageCheck: (ByteArray) -> DraftImage = { DraftImage.NoImage(NoImageCause.NOT_AN_IMAGE) },
        seams: ImportSeams = ImportSeams.PRODUCTION,
    ): RecipeImporter = importer(TestGateways.against(server), clock, imageCheck, seams)
}

/** A loopback server whose routes are set per test, counting requests per path. */
internal class RoutedServer(val server: MockWebServer = TestGateways.server()) : AutoCloseable {
    private val hits = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    private val routes = java.util.concurrent.ConcurrentHashMap<String, () -> MockResponse>()

    init {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                hits.computeIfAbsent(path) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
                return routes[path]?.invoke() ?: MockResponse().setResponseCode(404)
            }
        }
    }

    fun route(path: String, response: () -> MockResponse) {
        routes[path] = response
    }

    fun page(path: String, body: ByteArray, contentType: String? = "text/html; charset=utf-8", onServe: () -> Unit = {}) {
        route(path) {
            onServe()
            val response = MockResponse().setBody(Buffer().write(body))
            if (contentType != null) response.setHeader("Content-Type", contentType)
            response
        }
    }

    fun requests(path: String): Int = hits[path]?.get() ?: 0

    val totalRequests: Int get() = hits.values.sumOf { it.get() }

    fun url(path: String): ValidatedUrl = (ImportUrl.check(TestGateways.urlOf(server, path)) as UrlCheck.Valid).url

    fun absolute(path: String): String = TestGateways.urlOf(server, path)

    override fun close() {
        runCatching { server.shutdown() }
    }
}
