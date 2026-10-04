package ie.pantry.data.gateway

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The one gateway for outbound HTTP. Three `suspend` operations share one lazily built client and one
 * [GatewayPolicy]. Each returns a [GatewayResult] and never throws, except [kotlinx.coroutines.CancellationException]
 * when the caller is cancelled. The public surface uses no OkHttp type.
 */
class ExternalDataGateway internal constructor(
    internal val policy: GatewayPolicy,
    private val seams: GatewaySeams,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val targetPolicy = ConnectionTargetPolicy(seams.exemptTargets)
    private val hopScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Built once, on first use, off the caller's thread. Construction does no I/O. */
    internal val client: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        buildGatewayClient(policy, targetPolicy, seams)
    }

    /** GET a user-supplied recipe URL. */
    suspend fun fetchPage(url: String): GatewayResult = fetchUrl(CallType.PAGE_FETCH, url)

    /** GET a page-supplied image URL. Same policy as [fetchPage]: an image is not a special case. */
    suspend fun fetchImage(url: String): GatewayResult = fetchUrl(CallType.IMAGE_FETCH, url)

    /** GET the nutrition endpoint with [term] as the single search parameter. */
    suspend fun lookupNutrition(term: String): GatewayResult {
        if (term.isBlank()) return refused(GatewayException.Category.INVALID_REQUEST, CallType.NUTRITION_LOOKUP)
        return guarded(CallType.NUTRITION_LOOKUP) {
            call(CallType.NUTRITION_LOOKUP, NutritionQuery.url(seams.nutritionEndpoint, term))
        }
    }

    private suspend fun fetchUrl(callType: CallType, raw: String): GatewayResult {
        when (targetPolicy.classifyTarget(raw)) {
            TargetVerdict.SchemeRefused -> return refused(GatewayException.Category.SCHEME_REFUSED, callType)
            TargetVerdict.NoScheme -> return refused(GatewayException.Category.INVALID_REQUEST, callType)
            TargetVerdict.HttpScheme -> Unit
        }
        val url = raw.toHttpUrlOrNull() ?: return refused(GatewayException.Category.INVALID_REQUEST, callType)
        return guarded(callType) { call(callType, url) }
    }

    /** The one defensive catch: cancellation is rethrown ahead of it, so a cancelled caller is never swallowed. */
    private suspend fun guarded(callType: CallType, block: suspend () -> GatewayResult): GatewayResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        GatewayResult.Failed(gatewayFailure(e, callType, attempts = 0))
    }

    private fun refused(category: GatewayException.Category, callType: CallType): GatewayResult =
        GatewayResult.Failed(gatewayError(category, callType, attempts = 0))

    /**
     * The attempt loop: at most `retryCount + 1` attempts, each with its own deadline. Only a transient failure is
     * retried, after the back-off delay for that attempt. [GatewayException.attempts] counts attempts started.
     */
    private suspend fun call(callType: CallType, url: HttpUrl): GatewayResult {
        var attempts = 0
        while (true) {
            attempts++
            val result = attempt(callType, url, attempts)
            val error = (result as? GatewayResult.Failed)?.error
            if (error == null || !error.isTransient() || attempts > policy.retryCount) return result
            seams.backoffDelay(policy.backoff[attempts - 1])
        }
    }

    /**
     * One attempt: a pass through the whole redirect chain from the initial URL, under one deadline of
     * `attemptTimeout` that spans every hop. Each redirect is validated before it is followed.
     */
    private suspend fun attempt(callType: CallType, initial: HttpUrl, attempts: Int): GatewayResult {
        val deadline = System.nanoTime() + policy.attemptTimeout.inWholeNanoseconds
        var current = initial
        var redirectsFollowed = 0
        while (true) {
            val remaining = (deadline - System.nanoTime()).coerceAtLeast(1L)
            when (val outcome = hop(current, remaining)) {
                is HopOutcome.Body -> return GatewayResult.Fetched(outcome.body)
                is HopOutcome.Status -> return fail(GatewayException.Category.HTTP_STATUS, callType, attempts, outcome.code)
                HopOutcome.TooLarge -> return fail(GatewayException.Category.RESPONSE_TOO_LARGE, callType, attempts)
                is HopOutcome.Threw -> return GatewayResult.Failed(gatewayFailure(outcome.error, callType, attempts))
                is HopOutcome.Redirect -> {
                    val location = outcome.location
                        ?: return fail(GatewayException.Category.HTTP_STATUS, callType, attempts, outcome.code)
                    if (targetPolicy.classifyTarget(location) == TargetVerdict.SchemeRefused) {
                        return fail(GatewayException.Category.SCHEME_REFUSED, callType, attempts)
                    }
                    val next = current.resolve(location)
                        ?: return fail(GatewayException.Category.INVALID_REQUEST, callType, attempts)
                    if (redirectsFollowed == policy.maxRedirects) {
                        return fail(GatewayException.Category.TOO_MANY_REDIRECTS, callType, attempts)
                    }
                    redirectsFollowed++
                    current = next
                }
            }
        }
    }

    private fun fail(
        category: GatewayException.Category,
        callType: CallType,
        attempts: Int,
        statusCode: Int? = null,
    ): GatewayResult = GatewayResult.Failed(gatewayError(category, callType, attempts, statusCode))

    /**
     * Runs the blocking request on the gateway's own scope and resumes the caller with its outcome. A cancelled
     * caller resumes at once and the OkHttp call is cancelled, even if the blocked thread is still inside a lookup
     * that cannot be interrupted. The resume is guarded by `isActive`: the caller's cancellation and the hop's
     * completion are the only two parties that can resume this continuation, so the guard closes the race between
     * them.
     */
    private suspend fun hop(url: HttpUrl, remainingNanos: Long): HopOutcome =
        suspendCancellableCoroutine { continuation ->
            val inFlight = AtomicReference<Call?>()
            continuation.invokeOnCancellation { inFlight.get()?.cancel() }
            hopScope.launch {
                val outcome = executeHop(url, remainingNanos, inFlight) { !continuation.isActive }
                if (continuation.isActive) continuation.resume(outcome)
            }
        }

    /** The blocking body of a hop. It never throws. */
    private fun executeHop(
        url: HttpUrl,
        remainingNanos: Long,
        inFlight: AtomicReference<Call?>,
        callerGone: () -> Boolean,
    ): HopOutcome {
        var call: Call? = null
        return try {
            val created = client.newCall(request(url))
            call = created
            inFlight.set(created)
            if (callerGone()) created.cancel()
            created.timeout().timeout(remainingNanos, TimeUnit.NANOSECONDS)
            created.execute().use { response ->
                // A response whose body is not fully read is closed only after the call is cancelled, so the
                // client cannot drain more bytes while closing it.
                try {
                    if (response.isSuccessful) {
                        val declared = response.body?.contentLength() ?: -1L
                        if (declared > policy.maxBodyBytes) {
                            // Fast path: an over-cap declared length is refused before any body byte is read.
                            // The byte-counting interceptor stays the authority for every other case.
                            created.cancel()
                            HopOutcome.TooLarge
                        } else {
                            val bytes = response.body?.source()?.readByteArray() ?: ByteArray(0)
                            HopOutcome.Body(
                                FetchedBody(
                                    bytes,
                                    BodyEncoding.of(response.header("Content-Encoding")),
                                    response.header("Content-Type"),
                                ),
                            )
                        }
                    } else {
                        created.cancel()
                        if (response.code in REDIRECT_CODES) {
                            HopOutcome.Redirect(response.code, response.header("Location"))
                        } else {
                            HopOutcome.Status(response.code)
                        }
                    }
                } catch (e: Throwable) {
                    created.cancel()
                    throw e
                }
            }
        } catch (e: Exception) {
            call?.cancel()
            HopOutcome.Threw(e)
        }
    }

    /** A GET with only `Accept-Encoding: gzip` set. Setting it explicitly turns transparent decompression off. */
    private fun request(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .get()
        .header("Accept-Encoding", "gzip")
        .build()

    private sealed interface HopOutcome {
        class Body(val body: FetchedBody) : HopOutcome
        class Status(val code: Int) : HopOutcome
        class Redirect(val code: Int, val location: String?) : HopOutcome
        object TooLarge : HopOutcome
        class Threw(val error: Exception) : HopOutcome
    }

    companion object {
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        /** The only public construction path: default policy, production seams and the IO dispatcher. */
        fun create(): ExternalDataGateway =
            ExternalDataGateway(GatewayPolicy.DEFAULT, GatewaySeams.PRODUCTION, Dispatchers.IO)
    }
}
