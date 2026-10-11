package ie.pantry.recipeimport

import ie.pantry.data.gateway.BodyEncoding
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class CappedInputStreamTest {

    /** Hands out [size] filler bytes and records how many were pulled and the largest single request. */
    private class CountingSource(private val size: Long, private val fill: Byte = 'a'.code.toByte()) : InputStream() {
        var pulled = 0L
        var largestRequest = 0
        var lastRequests = mutableListOf<Int>()

        override fun read(): Int {
            if (pulled >= size) return -1
            pulled++
            return fill.toInt()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            largestRequest = maxOf(largestRequest, len)
            lastRequests += len
            if (pulled >= size) return -1
            val count = minOf(len.toLong(), size - pulled).toInt()
            b.fill(fill, off, off + count)
            pulled += count
            return count
        }
    }

    private fun drain(stream: InputStream, chunk: Int = 4096) {
        val buffer = ByteArray(chunk)
        while (stream.read(buffer) >= 0) Unit
    }

    @Test
    fun `bytes pulled from the delegate never exceed the cap plus one`() {
        val source = CountingSource(size = 10_000)
        val stream = CappedInputStream(source, cap = 100, maxTagBytes = Long.MAX_VALUE, job = Job())
        assertFailsWith<ContentTooLargeException> { drain(stream) }
        assertTrue(source.pulled <= 101, "pulled ${source.pulled}")
        assertEquals(101, stream.produced)
    }

    @Test
    fun `each read asks the delegate for at most the remaining allowance`() {
        val source = CountingSource(size = 10_000)
        val stream = CappedInputStream(source, cap = 100, maxTagBytes = Long.MAX_VALUE, job = Job())
        assertFailsWith<ContentTooLargeException> { drain(stream, chunk = 64) }
        assertEquals(listOf(64, 37), source.lastRequests)
    }

    @Test
    fun `crossing the cap throws the content too large marker`() {
        val exact = CappedInputStream(CountingSource(100), cap = 100, maxTagBytes = Long.MAX_VALUE, job = Job())
        drain(exact)
        assertEquals(100, exact.produced)
        val over = CappedInputStream(CountingSource(101), cap = 100, maxTagBytes = Long.MAX_VALUE, job = Job())
        assertFailsWith<ContentTooLargeException> { drain(over) }
    }

    @Test
    fun `mark is not supported and skip is counted`() {
        val stream = CappedInputStream(CountingSource(1_000), cap = 100, maxTagBytes = Long.MAX_VALUE, job = Job())
        assertFalse(stream.markSupported())
        assertEquals(60, stream.skip(60))
        assertEquals(60, stream.produced)
        assertFailsWith<ContentTooLargeException> { stream.skip(500) }
        assertEquals(101, stream.produced)
    }

    @Test
    fun `tag bytes are counted across reads and trip past the allowance`() {
        val lessThan = '<'.code.toByte()
        val stream = CappedInputStream(CountingSource(100, fill = lessThan), cap = 1_000, maxTagBytes = 5, job = Job())
        val buffer = ByteArray(2)
        assertEquals(2, stream.read(buffer))
        assertEquals(2, stream.read(buffer))
        assertFailsWith<ContentTooLargeException> { stream.read(buffer) }
        val single = CappedInputStream(CountingSource(100, fill = lessThan), cap = 1_000, maxTagBytes = 2, job = Job())
        single.read()
        single.read()
        assertFailsWith<ContentTooLargeException> { single.read() }
    }

    @Test
    fun `an unlimited tag allowance never trips`() {
        val lessThan = '<'.code.toByte()
        val stream = CappedInputStream(CountingSource(50_000, fill = lessThan), cap = 1_000_000, maxTagBytes = Long.MAX_VALUE, job = Job())
        drain(stream)
        assertEquals(50_000, stream.produced)
    }

    @Test
    fun `a cancelled job stops the next read`() {
        val job = Job()
        val stream = CappedInputStream(CountingSource(1_000), cap = 100, maxTagBytes = Long.MAX_VALUE, job = job)
        assertEquals(10, stream.read(ByteArray(10)))
        job.cancel()
        assertFailsWith<CancellationException> { stream.read(ByteArray(10)) }
        assertFailsWith<CancellationException> { stream.read() }
    }

    @Test
    fun `a bomb that would exhaust the heap if inflated is stopped while streaming`() {
        // 1.5 GiB of zeros: inflating it whole needs more than the 1 GiB test heap, so only a streaming cap survives.
        val inflated = 1536L * 1024 * 1024
        val bomb = ByteArrayOutputStream().also { sink ->
            val gzip = object : GZIPOutputStream(sink) {
                init {
                    def.setLevel(Deflater.BEST_SPEED)
                }
            }
            val zeros = ByteArray(1024 * 1024)
            var written = 0L
            while (written < inflated) {
                gzip.write(zeros)
                written += zeros.size
            }
            gzip.close()
        }.toByteArray()
        assertTrue(bomb.size < 8 * 1024 * 1024, "the bomb is ${bomb.size} bytes")
        val stream = openBounded(bomb, BodyEncoding.GZIP, DECOMPRESSED_CAP_BYTES, Long.MAX_VALUE, Job())
        assertFailsWith<ContentTooLargeException> { drain(stream, chunk = 65_536) }
        assertEquals(DECOMPRESSED_CAP_BYTES + 1, stream.produced)
    }

    @Test
    fun `openBounded reads identity bytes and gunzips gzip bodies`() {
        val text = "hello recipe".toByteArray()
        val identity = openBounded(text, BodyEncoding.IDENTITY, 1_000, Long.MAX_VALUE, Job())
        assertContentEquals(text, identity.readBytes())
        val gzipped = ByteArrayOutputStream().also { GZIPOutputStream(it).use { out -> out.write(text) } }.toByteArray()
        val gunzipped = openBounded(gzipped, BodyEncoding.GZIP, 1_000, Long.MAX_VALUE, Job())
        assertContentEquals(text, gunzipped.readBytes())
        assertFailsWith<IllegalArgumentException> { openBounded(text, BodyEncoding.OTHER, 1_000, Long.MAX_VALUE, Job()) }
        assertTrue(ByteArrayInputStream(text).markSupported())
    }
}
