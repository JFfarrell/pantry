package ie.pantry.testutil

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Collects [flow] into a channel on [Dispatchers.Default]. Room emits on its own executor, so awaits
 * use a real-time timeout (they escape `runTest`'s virtual clock) and a stalled flow fails fast.
 */
class FlowRecorder<T>(flow: Flow<T>, scope: CoroutineScope) {

    private val channel = Channel<T>(Channel.UNLIMITED)
    private val job = scope.launch(Dispatchers.Default) { flow.collect { channel.send(it) } }

    suspend fun awaitNext(timeout: Duration = 5.seconds): T =
        withContext(Dispatchers.Default) { withTimeout(timeout) { channel.receive() } }

    /** Discards emissions until one satisfies [predicate], and returns it. */
    suspend fun awaitUntil(timeout: Duration = 5.seconds, predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) {
            withTimeout(timeout) {
                var value = channel.receive()
                while (!predicate(value)) value = channel.receive()
                value
            }
        }

    fun close() {
        job.cancel()
    }
}
