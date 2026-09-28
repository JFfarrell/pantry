package ie.pantry.testutil

import ie.pantry.data.reference.AssetSource
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * A test-only [AssetSource] backed by an in-memory map of path to bytes. A path with no mapping throws
 * [FileNotFoundException], the same contract as a real missing asset.
 */
class FixtureAssetSource(private val map: Map<String, ByteArray>) : AssetSource {
    override fun open(path: String): InputStream =
        map[path]?.let { TrackingInputStream(ByteArrayInputStream(it)) }
            ?: throw FileNotFoundException("asset not found")
}

/** An [InputStream] wrapper that records whether [close] was called, for tests that need to observe it. */
class TrackingInputStream(private val delegate: InputStream) : InputStream() {
    var closed: Boolean = false
        private set

    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
    override fun close() {
        closed = true
        delegate.close()
    }
}

/** An [InputStream] whose read throws [IOException] partway through, to force `ASSET_UNREADABLE`. */
class FailingMidReadInputStream(private val goodBytes: Int) : InputStream() {
    private var read = 0
    override fun read(): Int {
        if (read >= goodBytes) throw IOException("forced mid-read failure")
        read++
        return 'x'.code
    }
}

/** Wraps a delegate [AssetSource], recording every open: its path, thread and count. Thread-safe. */
class RecordingAssetSource(private val delegate: AssetSource) : AssetSource {
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private val threads = Collections.synchronizedList(mutableListOf<Thread>())
    private val count = AtomicInteger()

    val openedPaths: List<String> get() = ArrayList(paths)
    val openThreads: List<Thread> get() = ArrayList(threads)
    val openCount: Int get() = count.get()

    override fun open(path: String): InputStream {
        paths += path
        threads += Thread.currentThread()
        count.incrementAndGet()
        return delegate.open(path)
    }
}

/**
 * Blocks every open until [release] is called, or 30 s pass. Used to prove a caller regains control
 * before a background read completes.
 */
class BlockingAssetSource : AssetSource {
    private val startedLatch = java.util.concurrent.CountDownLatch(1)
    private val releaseLatch = java.util.concurrent.CountDownLatch(1)
    private val count = AtomicInteger()
    private val threads = Collections.synchronizedList(mutableListOf<Thread>())
    @Volatile var completed: Boolean = false
        private set
    @Volatile var timedOut: Boolean = false
        private set

    val openCount: Int get() = count.get()
    val openThreads: List<Thread> get() = ArrayList(threads)

    fun awaitStarted(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean = startedLatch.await(timeout, unit)

    fun release() {
        releaseLatch.countDown()
    }

    override fun open(path: String): InputStream {
        count.incrementAndGet()
        threads += Thread.currentThread()
        startedLatch.countDown()
        val reached = releaseLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        if (!reached) {
            timedOut = true
            throw FileNotFoundException("BlockingAssetSource timed out")
        }
        completed = true
        throw FileNotFoundException("BlockingAssetSource never resolves to real content")
    }
}

/**
 * Holds every open at a gate until [release] lets them all through to [delegate]. Used to prove that
 * concurrent first lookups share one load.
 */
class GatedAssetSource(private val delegate: AssetSource) : AssetSource {
    private val releaseLatch = java.util.concurrent.CountDownLatch(1)
    private val count = AtomicInteger()
    private val blocked = AtomicInteger()
    private val threads = Collections.synchronizedList(mutableListOf<Thread>())
    @Volatile var completed: Boolean = false
        private set
    @Volatile var timedOut: Boolean = false
        private set

    val openCount: Int get() = count.get()
    val openThreads: List<Thread> get() = ArrayList(threads)
    val blockedAtGate: Int get() = blocked.get()

    fun awaitBlocked(atLeast: Int, timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (blocked.get() < atLeast) {
            if (System.nanoTime() > deadline) return false
            Thread.sleep(5)
        }
        return true
    }

    fun release() {
        releaseLatch.countDown()
    }

    override fun open(path: String): InputStream {
        count.incrementAndGet()
        threads += Thread.currentThread()
        blocked.incrementAndGet()
        val reached = releaseLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        if (!reached) {
            timedOut = true
            throw java.io.IOException("GatedAssetSource timed out")
        }
        val stream = delegate.open(path)
        completed = true
        return stream
    }
}
