# Design: External Data Gateway

**Spec:** `specs/F4-external-data-gateway/01_spec.md`

## Goals and Non-Goals

**Goals:**
- One `ExternalDataGateway` with exactly three `suspend` operations over one lazily built `OkHttpClient` and one `GatewayPolicy` (R1). The public surface uses no `okhttp3` types, so callers never import OkHttp (R1 AC3).
- Scheme and address checks on every hop, applied at the socket the client actually connects (R2, R3; RK1, RK2, RK8).
- Bounded redirects, a raw-byte cap measured on wire bytes, a per-attempt deadline and bounded retry, all driven by Q2's policy values (R4 to R7).
- Anonymous GETs: no cookies, cache, credentials or extra headers, and the nutrition term added through `HttpUrl.Builder` (R8).
- A content-free, `Throwable`-shaped `GatewayException` and its re-wrapping function, which together are the typed-error surface that CFC-4's Enforcement assigns to F4. F5 and F16 consume it (R9; CFC-4).
- The `INTERNET` permission and `android:usesCleartextTraffic="true"` (R10 AC1), plus F1's two no-network tests changed to their Q1 form (R10).

**Non-Goals:**
- Decompressing content, capping decompressed size, parsing HTML, choosing or decoding images, and interpreting nutrition payloads. These belong to C1 (F5) and C6 (F9, F16) (spec Never Do).
- A shared cross-component re-wrap helper. Under spec Q7, F1's `persistenceFailure` and F3's typed outcomes stay as they are.
- A pre-flight connectivity check that would skip the retry budget when the device is offline (`[DEF-02]`). It needs `ACCESS_NETWORK_STATE`, which is Ask First (AD15).
- Connection reuse across calls, HTTP caching, cookie handling and `Retry-After` (Q3, R8, AD9).
- Blocking ranges beyond Q4's list, such as NAT64 `64:ff9b::/96`, 6to4 `2002::/16` and IPv4-compatible `::/96` (Ask First; DR8).

## Architecture Decisions

A bare `Q<n>` in this section and the sections after it is the spec's Open Question of that number. This design's own questions are cited as "design Q1" to "design Q4" (see Open Questions).

| Decision | Choice | Alternatives Rejected | Rationale | Consequences |
|----------|--------|-----------------------|-----------|--------------|
| AD1 — Shape of the gateway error (Decision Point 7; `[SEAL-04]`, `[SEAL-05]`) | **One `Throwable` class with a category enum.** `class GatewayException : RuntimeException` holds `category: Category`, `callType: CallType`, `statusCode: Int?`, `attempts: Int` and `causeType: String?`. Its constructor is `internal` and takes **no `String` parameter**. `causeType` is computed from a `KClass<out Throwable>?` argument as `simpleName`, and the message is built inside the class from enum names and integers. `init` checks `(statusCode != null) == (category == HTTP_STATUS)` with a constant message. This follows the same content-free pattern as F1's `PersistenceException`, but without its `operation: String` field or its log line, and the category enum is the same idea as F2's `LoadFailure.Category` | A sealed interface with one subtype per category: this gives nine types where one `when (e.category)` already gives exhaustiveness, and `statusCode` would be the only field any subtype adds. A non-`Throwable` data class plus a new non-`Throwable` overload of `assertNoSentinel()`: F5 and F16 would then need two hygiene helpers, and a caller that wants to `throw` a gateway failure (for example from a `Flow`) would have to wrap it in yet another exception type, which is the content-leak route CFC-4 closes. `kotlin.Result`: ruled out by the spec's Always Do | `assertNoSentinel()` (`fun Throwable.assertNoSentinel()` in `ie.pantry.testutil`, confirmed in `Sentinels.kt`) applies directly, with no test-utility change. Because the constructor has no `String` parameter, no URL, host or body can reach the message, whatever a later edit does at a call site (`[DEF-06]`) | The exception is **returned** inside `GatewayResult.Failed`, never thrown by the gateway. It has no `cause` and no suppressed exceptions. The stack frames of the re-wrapped cause are copied (AD12) |
| AD2 — Success shape and gzip (Decision Point 5; RK4; `[SEAL-02]`) | **A fully buffered `ByteArray` of the raw body bytes, at most the cap.** `FetchedBody(bytes, encoding: BodyEncoding, mediaType: String?)`. Every request sets `Accept-Encoding: gzip` **explicitly**. This is the same header and value OkHttp's `BridgeInterceptor` sends by default, so the wire request is unchanged. But because the header is caller-set, OkHttp turns **transparent decompression off** (verified in 4.12.0 `BridgeInterceptor`, lines 66 to 71). So `bytes` are exactly the de-chunked body bytes read off the wire, and `encoding` tells C1 whether to gunzip them under its own streaming cap. `mediaType` is the raw `Content-Type` value **[ASSUMPTION — design Q4]** | A capped stream (`Source` or `InputStream`): this exposes OkHttp or Okio types, or an open connection, to F5. The caller would then own closing it and could hold a connection open past the gateway's deadline. Transparent gzip left on, with the cap counted in a network interceptor: the gateway would then have to read the **decompressed** stream fully to buffer it, which is the decompression bomb RK4 warns about. Sending `Accept-Encoding: identity`: this changes a default header (Ask First) and is not reliably honoured by servers | The cap applies to wire bytes (R5 AC4), and C1 alone caps decompressed bytes as they stream (ARCHITECTURE C1). No OkHttp type crosses the package boundary (R1 AC3) | F5 must handle `BodyEncoding.GZIP` (gunzip under its streaming cap) and `OTHER` (treat as unreadable). `FetchedBody.toString()` is overridden to print only the size and the encoding, so a body never reaches a log through `toString` |
| AD3 — OkHttp version (Decision Point 1) | **`com.squareup.okhttp3:okhttp:4.12.0`** plus **`com.squareup.okhttp3:mockwebserver:4.12.0`** (test). They share one catalog version ref | 5.x with `mockwebserver3`: 5.x publishes an Android-specific artifact whose platform initialisation is reported to use `androidx.startup` manifest metadata, which could add a manifest entry that R10 AC1's component check would have to allow for **[ASSUMPTION — not verified offline]**. 4.12.0's `okhttp` is a plain JAR with no manifest, so it cannot add a component. 4.12.0 is also already in this devcontainer's offline Gradle cache (`.toolchain/gradle-home/.../okhttp/4.12.0`, with `okio` 3.6.0) | Stable and widely documented. It works with Kotlin 2.1.0 and `minSdk 26`. Every internal behaviour this design depends on was checked against the 4.12.0 sources: literal-IP bypass of `Dns` (`RouteSelector` line 163), `socketFactory.createSocket()` for direct routes (`RealConnection` line 287), `retryOnConnectionFailure` gating route fallback (`RetryAndFollowUpInterceptor` line 151), and `maxIdleConnections == 0` closing connections at once (`RealConnectionPool` line 108) | `mockwebserver` 4.12.0 is **not** in the offline cache (DR1). A later move to 5.x must re-check the four behaviours above and R10 AC1 |
| AD4 — How redirects are followed (Decision Point 2) | **By the gateway's own loop.** The client has `followRedirects(false)` and `followSslRedirects(false)`. Codes 301, 302, 303, 307 and 308 are redirects, and every other 3xx is `HTTP_STATUS`. For a redirect: a missing `Location` gives `HTTP_STATUS(code)`. Otherwise `ConnectionTargetPolicy.classifyTarget(location)` gives `SCHEME_REFUSED` for an explicit non-`http(s)` scheme; else `currentUrl.resolve(location)` returning `null` gives `INVALID_REQUEST`; else, if `redirectsFollowed == maxRedirects`, the result is `TOO_MANY_REDIRECTS`; else the loop follows the redirect with a new GET | A network interceptor inside OkHttp's own redirect following: OkHttp's `RetryAndFollowUpInterceptor` silently returns the 3xx response itself when `Location` has a non-`http(s)` scheme (`HttpUrl.resolve` returns `null`), so the caller would see `HTTP_STATUS`. That is exactly what R2 AC2 forbids | The scheme is checked **before** `HttpUrl` parsing, so `intent://…` and `content://…` give `SCHEME_REFUSED` and `http://[::bad` gives `INVALID_REQUEST` (R2 AC2, R4 AC3). Every hop goes through the same socket guard (AD5) | 5 redirects allowed means at most 6 requests per attempt. R4 AC2's server sees exactly `maxRedirects + 1 = 6` requests (`[SEAL-07]`). Every redirect is re-issued as a GET with no body |
| AD5 — Where the address check runs (Decision Point 3; RK1, RK8; R3 AC4 design choice; `[DEF-03]`) | **Two layers, with the socket guard authoritative.** (1) **`GuardedSocketFactory`** (`client.socketFactory`) creates `GuardedSocket`s. `GuardedSocket.connect(endpoint, timeout)` requires a resolved `InetSocketAddress` and asks `ConnectionTargetPolicy.permits(address, port)`, **before** `super.connect`. A refusal throws `AddressRefusedException`, so no SYN is sent. TLS sockets wrap this raw socket, so `https` is covered too. (2) **`TargetFilteringDns`** wraps the injected resolver and drops every blocked address, throwing `AddressRefusedException` when none is left. **Mixed answers (R3 AC4): the client connects only to the allowed addresses** | `EventListener.connectStart`: it is a notification hook, and a throw from it would be an ad hoc control-flow trick on a listener. Dns filtering alone: OkHttp 4.12 **skips `Dns` for literal-IP hosts** (`RouteSelector` line 163), so `http://127.0.0.1/` would bypass it. Socket guard alone: with `retryOnConnectionFailure(false)` (AD10), OkHttp does not fall back to the next route after a refused address, so a mixed answer whose first address is blocked would fail instead of using the allowed one | The check runs on the `InetAddress` passed to `connect`, which is by definition the address connected to. There is no separate lookup, so rebinding has no gap to exploit (R3 AC6). Decimal, octal and hex IPv4 literals (`http://2130706433/`) are not canonicalised by `HttpUrl`. They reach `Dns.lookup` or `InetAddress.getByName`, and whatever address they resolve to is checked there and at the socket (RK8). An IPv4-mapped address is checked as IPv4 (R3 AC2). IPv4-compatible and other embedded-IPv4 forms outside Q4's list are not blocked (Non-Goals; DR8) | The Dns filter is an optimisation that picks the route, and security does not depend on it. `GatewayTransportTest` proves the socket guard alone refuses every blocked address, with no Dns involved (DR9) |
| AD6 — How retry and redirects compose (`[DEF-01]`, `[SEAL-06]`) | **An attempt is one pass through the whole redirect chain, starting from the initial URL.** Each attempt has its own deadline of `attemptTimeout` (20 s), which spans all its hops: each hop's `call.timeout()` is set to the time left. A transient outcome on **any** hop (`TIMEOUT`, `CONNECTION_FAILED`, `HTTP_STATUS` 5xx) ends the attempt, and after the back-off the next attempt restarts from the initial URL. The budget is per call: at most `retryCount + 1` attempts. Every other category (`INVALID_REQUEST`, `SCHEME_REFUSED`, `ADDRESS_REFUSED`, `TOO_MANY_REDIRECTS`, `RESPONSE_TOO_LARGE`, `UNEXPECTED`, and 4xx or other non-5xx `HTTP_STATUS`) returns at once | Retry per hop, resuming mid-chain: this needs a second budget, and R6 AC1's whole-call formula would no longer bound a chain of six slow hops. A per-hop timeout of 20 s: a 6-hop chain could then take 120 s for one attempt | R6 AC1's bound, (retryCount + 1) × attemptTimeout + Σback-off + 1 s, holds whatever the chain length: 3 × 20 + 1.5 + 1 = 62.5 s (`[SEAL-03]`). `UNEXPECTED` is not transient because Terms defines transient as closed (`[SEAL-06]`); `GatewayRetryTest` has an explicit fixture for it | Worst-case requests per call: 3 × 6 = 18. `GatewayException.attempts` is the number of attempts started (0 when the call fails before any connection) |
| AD7 — Execution, cancellation and back-off (Decision Point 6; R1 AC4; RK7) | **A blocking `call.execute()` on an injected `ioDispatcher` (`Dispatchers.IO` in production), bridged with `suspendCancellableCoroutine`.** `invokeOnCancellation { call.cancel() }` is registered, and the hop body runs in the gateway's own `hopScope` (a `CoroutineScope(SupervisorJob() + ioDispatcher)`). The hop body catches `Exception` and always resumes with a `HopOutcome`. It never throws. A cancelled caller resumes immediately with `CancellationException`, even if the blocked thread is still inside a DNS lookup that cannot be interrupted. After `call.cancel()`, OkHttp refuses to connect or read. The attempt loop runs on the **caller's** context. Back-off is `seams.backoffDelay(d)`, which defaults to `delay(d)`, so under `runTest` it uses virtual time. The gateway catches `CancellationException` nowhere, except for an explicit rethrow ahead of its one defensive `catch (e: Exception)`. The bridge resumes the continuation with `if (continuation.isActive) continuation.resume(outcome)`: the only two parties that can ever resume this continuation are the caller's own cancellation (via `suspendCancellableCoroutine`'s built-in mechanism) and `executeHop`'s completion on `hopScope`'s IO thread, so `isActive` is exactly the guard that closes the race between them — without it, a cancellation that wins the race leaves `executeHop`'s later `resume` call to throw `IllegalStateException` on the IO thread | `call.enqueue`: in 4.12, `AsyncCall.run` renames the worker thread to `"OkHttp <redacted URL>"`, which contains the host, and **rethrows** any non-`IOException` on the dispatcher thread, which crashes the app (`RealCall` lines 513 and 527 to 534). `withContext(io) { execute() }` with `runInterruptible`: a blocking socket read ignores thread interrupts, so cancellation would hang until the read timed out. A coroutine `withTimeout` for the attempt deadline: under `runTest` its virtual clock would jump to the timeout while real I/O is still pending | Cancellation propagates as normal coroutine cancellation, and no retry starts after it (R1 AC4). The back-off is on virtual time while the network I/O is real (R7 AC1). Timeouts are enforced by OkHttp on the wall clock (R6 AC1) | `ioDispatcher` is a constructor parameter, which is F2's `ReferenceDataStore` pattern. Tests pass the real `Dispatchers.IO`, because MockWebServer I/O is real |
| AD8 — Test seam for loopback and resolver (Decision Point 4; RK3) | **`internal class GatewaySeams`**: an injected `Dns`, `exemptTargets: Set<InetSocketAddress>` (address **and** port), an optional TLS trust override, an optional application `Interceptor`, an `EventListener`, the nutrition endpoint and `backoffDelay`. `GatewaySeams.PRODUCTION` leaves `exemptTargets`, `tls` and `interceptor` absent; its `dns`, `eventListener`, `nutritionEndpoint` and `backoffDelay` have concrete production defaults (DM8). `ExternalDataGateway`'s primary constructor is `internal`. The only public construction path is `ExternalDataGateway.create()`, which passes `GatewayPolicy.DEFAULT`, `GatewaySeams.PRODUCTION` and `Dispatchers.IO`. `AppContainer.production` calls `create()` | A public test constructor, or a Boolean "allow loopback" flag: either could be reached from production code. Exempting a whole address (`127.0.0.1`): any other service on that address would then be open to tests. Exempting by address and port opens exactly one MockWebServer | Tests reach MockWebServer through one exact `(127.0.0.1, port)` pair. A second, non-exempt server on the same address stays blocked, which gives the refused-address fixtures a live listener that must record zero requests | Guarded three ways: `AppContainerTest` asserts that the production container refuses `http://127.0.0.1:<port>/` and that the server records 0 requests; `SingleCallSiteTest` asserts that `GatewaySeams(` is constructed only in `GatewaySeams.kt` among main sources; and `TargetFilteringDns` applies exemptions by address only, while `GuardedSocket` enforces address and port |
| AD9 — Enforcing the raw-byte cap (R5) | Three parts. (1) `Content-Length` greater than the cap gives `RESPONSE_TOO_LARGE` before any body read. (2) A **network interceptor**, `RawByteCapInterceptor`, wraps the body in `CappedSource`, which asks upstream for at most `cap + 1 − consumed` bytes and throws `ResponseTooLargeException` once more than `cap` bytes have been consumed. Being a network interceptor, it sits below the gzip bridge, so it counts wire bytes even if a later change re-enables transparent gzip. (3) Every response whose body is not fully read (too large, 3xx, non-2xx) is closed **after `call.cancel()`**, so OkHttp's 100 ms discard-to-reuse read (`DISCARD_STREAM_TIMEOUT_MILLIS`) cannot pull more bytes. Connection pooling is off: `ConnectionPool(0, 1, SECONDS)` | Checking the size after `readByteArray()`: this would buffer an unbounded body first. Capping in the application-layer read loop: Okio's buffered source reads 8 KiB segments ahead, so the gateway would pull more than cap + 1 bytes from the codec | The gateway never takes more than cap + 1 body bytes from the HTTP codec (R5 AC2). The connection is closed after an over-cap body (R5 AC5). Socket-level read-ahead can go past this by at most one Okio segment. That is inherent to any buffered client, and DR4 records it | A `Content-Length` that falsely claims less than the cap cannot give `RESPONSE_TOO_LARGE` over HTTP/1.1, because OkHttp reads only the declared bytes. See design Q2 |
| AD10 — Client configuration (R1, R8; Q6; RK2) | Built once, **lazily** (`by lazy(SYNCHRONIZED)`, first touched on `ioDispatcher`) by `buildGatewayClient(policy, targetPolicy, seams)`: `proxy(Proxy.NO_PROXY)`, `cookieJar(CookieJar.NO_COOKIES)`, `cache(null)`, `followRedirects(false)`, `followSslRedirects(false)`, `retryOnConnectionFailure(false)`, `connectionPool(ConnectionPool(0, 1, SECONDS))`, `connectTimeout(10 s)`, `readTimeout(15 s)`, `writeTimeout(15 s)`, `callTimeout(0)` (the deadline is set per call, AD6), `dns(TargetFilteringDns)`, `socketFactory(GuardedSocketFactory)` and `addNetworkInterceptor(RawByteCapInterceptor)`. The authenticator stays `Authenticator.NONE`. No logging interceptor | Eager construction in `AppContainer`: `OkHttpClient.Builder().build()` loads the platform trust store, and the container may be first read on the main thread (F1 `FirstPaintNotBlockedTest`). `retryOnConnectionFailure(true)`: OkHttp would then re-send requests silently, which breaks R7's exact attempt counts | One client, one policy (R1 AC2). Every connection is direct, so every address is checked (Q6). Nothing persists between calls (R8 AC3). OkHttp never derives an `Authorization` header from URL userinfo (R8 AC4) | `writeTimeout` is not a Q2 value, so it reuses the read timeout, which is within the 20 s ceiling **[ASSUMPTION]**. Construction does no I/O, like `AppContainer`'s existing KDoc rule |
| AD11 — Nutrition request (Q5; R8 AC1, AC2) | `NutritionQuery.url(endpoint, term)` builds the URL with `endpoint.newBuilder().addQueryParameter(TERM_PARAM, term)` plus constant parameters. Production endpoint: `https://world.openfoodfacts.org/cgi/search.pl`, with constant parameters `search_simple=1`, `action=process`, `json=1` and `page_size=5`, and the term in `search_terms` **[ASSUMPTION — design Q3]**. A blank term gives `INVALID_REQUEST` before any request. The term is sent exactly as given, with no trimming | Search-a-licious (`search.openfoodfacts.org`), which OFF's current documentation recommends for full-text search: its API documentation could not be reached from this environment (connection refused), so its parameter names are unconfirmed. API v2 `/api/v2/search`: the documentation says it is filter-based, with no full-text term | `addQueryParameter` percent-encodes `& # / ? =`, so the term is exactly one parameter (R8 AC1). A source check forbids `"$`, `+ term` and `String.format` in `NutritionQuery.kt` (R8 AC2) | Moving to search-a-licious later changes only `NutritionQuery`'s constants. F16 owns the choice of payload fields. OFF asks callers for a custom `User-Agent` (design Q3) |
| AD12 — CFC-4 typed-error surface and re-wrapping rule (CFC-4 Enforcement; Q7) | **Surface:** `ie.pantry.data.gateway.GatewayException` and its `Category`, `CallType` and `GatewayResult.Failed` (public, in `GatewayException.kt` and `GatewayResult.kt`). **Re-wrapping function:** `internal fun gatewayFailure(cause: Throwable, callType: CallType, attempts: Int): GatewayException` in `GatewayException.kt`. **The rule it implements:** (a) the category comes only from `is`-checks on the thrown type, its cause chain and its suppressed exceptions (with a visited set), and never from a message; (b) `causeType` is the top-level class's `simpleName`; (c) the cause is **not** chained and its suppressed exceptions are not copied; (d) the cause's `stackTrace` frames are copied; (e) nothing is logged | A public helper in `ie.pantry.common` that every feature calls: spec Q7 settled that F4 ships the gateway error plus a gateway-internal re-wrap function | F5 and F16 receive `GatewayResult.Failed(error)` and branch on `error.category`. They may store or rethrow the `GatewayException` unchanged, because it is content-free and has no cause chain. F5 applies the same five-point rule to jsoup exceptions in its own package, and its own sentinel tests call the same `assertNoSentinel()` | The KDoc on `gatewayFailure` states rules (a) to (e) as the CFC-4 rule other features copy. Integration Points shows how each consumer uses it |
| AD13 — Package and file split (spec Project Structure `[ASSUMPTION]`) | Package `ie.pantry.data.gateway`. On top of the spec's four main files, three are added: `GatewayException.kt` (so most of the CFC-4 surface has its own file; `GatewayResult.Failed` in `GatewayResult.kt` carries the rest, per AD12's Surface list), `GatewayTransport.kt` (every OkHttp customisation) and `GatewaySeams.kt`. The nutrition URL goes in `NutritionQuery.kt`, so R8 AC2's source check covers exactly one small file. A shared test helper, `ie.pantry.testutil.TestGateways`, is added in the same style as F3's AD14 `testutil` helpers, so F5 and F16 can build a gateway against MockWebServer | Everything in `ExternalDataGateway.kt`: the orchestrator, the transport hooks and the error model change for different reasons | Each file has one reason to change | This refines the spec's New Files. The spec's test file names are kept, and `GatewayTransportTest` is added |
| AD14 — F1 baseline and manifest (R10; Q1) | `ManifestPolicyTest`: the requested `android.permission.*` set equals `{INTERNET}`, backup stays off, and no merged component class starts with `okhttp3.`. `RestartDurabilityTest`: the `Class.forName("okhttp3.OkHttpClient")` assertion is replaced by a **source-inspection guard**: no main file under `ie/pantry/data/db/` references `okhttp3` or `ie.pantry.data.gateway`. The `ProxySelector` and no-active-network guards are kept. The manifest also gets `android:usesCleartextTraffic="true"` (R10 AC1; design Q1 confirmed), and `ManifestPolicyTest` asserts it on the merged manifest | Keeping a classpath assertion: impossible once OkHttp ships. A `Socket.setSocketImplFactory` guard: it can be set only once per JVM, so it would break every later socket test in the same Gradle test JVM | The persistence layer cannot reach an HTTP client through its own sources, which is what the removed assertion was protecting. Without cleartext permission, `targetSdk 35` rejects every `http://` fetch with `UnknownServiceException` on a device, while JVM tests still pass (DR2) | F1's spec text is not reopened (Q1) |
| AD15 — Offline behaviour (`[DEF-02]`; RK6) | No pre-flight connectivity check. An offline device goes through the whole retry budget, which is bounded by AD6's 62.5 s worst case (DNS failures usually fail fast) | A `ConnectivityManager` check: it needs `ACCESS_NETWORK_STATE`, which is Ask First, and it would contradict R10 AC1 | Keeps R10's single-permission rule | F5 may show a cancellable progress state. Cancelling ends the call immediately (AD7) |

## Component Design

### FC1 — Result and error model

**Responsibility:** Define the gateway's success value, its content-free error, and the CFC-4 re-wrapping function.

**Location:** `app/src/main/java/ie/pantry/data/gateway/GatewayResult.kt`, `app/src/main/java/ie/pantry/data/gateway/GatewayException.kt`

**Key classes/functions:**
- `GatewayResult` (sealed interface), with `GatewayResult.Fetched(body)` and `GatewayResult.Failed(error)` (DM1).
- `FetchedBody`, `BodyEncoding` and `CallType` (DM2, DM3, DM6).
- `GatewayException` with `Category` (DM4, DM5; AD1).
- `internal fun gatewayError(category, callType, attempts, statusCode = null)`: a top-level function for refusals decided by the policy, with no cause.
- `internal fun gatewayFailure(cause, callType, attempts)`: a top-level function that re-wraps a third-party or internal exception (AD12).
- `internal class AddressRefusedException : IOException` and `internal class ResponseTooLargeException : IOException`: internal markers with constant messages. They are thrown inside OkHttp's stack and never leave the gateway.
- `internal fun GatewayException.isTransient(): Boolean`: true for `TIMEOUT`, `CONNECTION_FAILED` and `HTTP_STATUS` 500–599 (AD6).

### FC2 — Policy values

**Responsibility:** Hold Q2's policy values as one immutable value shared by all three call types.

**Location:** `app/src/main/java/ie/pantry/data/gateway/GatewayPolicy.kt`

**Key classes/functions:**
- `data class GatewayPolicy` (DM7), with `GatewayPolicy.DEFAULT` holding Q2's values. `init` checks invariants with constant messages.

### FC3 — Connection-target policy

**Responsibility:** Decide, in pure Kotlin, whether a URL's scheme and a socket address may be connected to.

**Location:** `app/src/main/java/ie/pantry/data/gateway/ConnectionTargetPolicy.kt`

**Key classes/functions:**
- `internal class ConnectionTargetPolicy(exemptTargets: Set<InetSocketAddress> = emptySet())`, with `STRICT` holding no exemptions. It is `internal` so that only the gateway can pair it with exemptions (AD8).
- `classifyTarget(raw: String): TargetVerdict`: reads an RFC 3986 scheme (`ALPHA *(ALPHA / DIGIT / "+" / "-" / ".") ":"` before any `/ ? #`). The result is `SchemeRefused` for any scheme other than `http`/`https` (case-insensitive), `NoScheme` when there is none, and `HttpScheme` otherwise.
- `isBlocked(address: InetAddress): Boolean`: a byte-prefix CIDR match. A 16-byte `::ffff:0:0/96` address is checked as its embedded IPv4 address. IPv4 blocks: `0.0.0.0/8`, `10.0.0.0/8`, `100.64.0.0/10`, `127.0.0.0/8`, `169.254.0.0/16`, `172.16.0.0/12`, `192.168.0.0/16`, `224.0.0.0/4`. IPv6 blocks: `::/128`, `::1/128`, `fc00::/7`, `fe80::/10`, `fec0::/10`, `ff00::/8`.
- `permits(address: InetAddress, port: Int): Boolean`: `!isBlocked(address) || InetSocketAddress(address, port) in exemptTargets`.
- `permitsForResolution(address: InetAddress): Boolean`: the Dns-level pre-filter, which exempts by address only (AD8).
- Imports only `java.net`. No `okhttp3` and no `android` import (spec Always Do).

### FC4 — Transport

**Responsibility:** Build the one OkHttp client with the policy's timeouts, connection guards and byte cap installed.

**Location:** `app/src/main/java/ie/pantry/data/gateway/GatewayTransport.kt`

**Key classes/functions:**
- `internal fun buildGatewayClient(policy, targetPolicy, seams): OkHttpClient`, a top-level function (AD10).
- `internal class TargetFilteringDns(delegate: Dns, policy: ConnectionTargetPolicy) : Dns` (AD5).
- `internal class GuardedSocketFactory(policy) : SocketFactory`. Every `createSocket(...)` overload returns a `GuardedSocket`. The overloads that take a host and port connect through the guarded `connect`.
- `internal class GuardedSocket(policy) : Socket()`, which overrides `connect(SocketAddress, Int)` (AD5). An unresolved address is refused.
- `internal class RawByteCapInterceptor(cap: Long) : Interceptor` and `internal class CappedSource(delegate: Source, cap: Long) : ForwardingSource` (AD9).

### FC5 — Gateway orchestrator

**Responsibility:** Run each call type through validation, the attempt loop, the redirect loop and the hop, and return a `GatewayResult`.

**Location:** `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt`, `app/src/main/java/ie/pantry/data/gateway/NutritionQuery.kt`

**Key classes/functions:**
- `ExternalDataGateway`: `fetchPage`, `fetchImage`, `lookupNutrition` and `companion fun create()` (I1).
- `private suspend fun call(callType, initialUrl: HttpUrl): GatewayResult` is the attempt loop (AD6). `private suspend fun attempt(...)` is the redirect loop (AD4). `private suspend fun hop(url, remainingNanos): HopOutcome` is the cancellable bridge (AD7). `private fun executeHop(call): HopOutcome` is the blocking body that runs on `ioDispatcher`.
- `private fun request(url: HttpUrl): Request` builds a GET with only `Accept-Encoding: gzip` set (AD2).
- `internal object NutritionQuery` holds `ENDPOINT`, the parameter constants and `url(endpoint, term): HttpUrl` (AD11).
- `internal val policy: GatewayPolicy` and `internal val client: OkHttpClient` (lazy) are exposed for R1 AC2's inspection.

**Hop body (`executeHop`), in order:** `call.timeout().timeout(remainingNanos, NANOSECONDS)`, then `call.execute().use { resp -> … }`. A 2xx response: if `body.contentLength() > cap`, cancel and return `TooLarge`; this pre-check is an intentional fast path that duplicates AD9's interceptor-level enforcement, so an over-cap declared length is refused before any body byte is read, while `CappedSource` remains the authoritative cap for every other case. Otherwise `readByteArray()` through `CappedSource` and return `Body`. A redirect code: cancel and return `Redirect(code, location)`. Anything else: cancel and return `Status(code)`. `catch (e: Exception)`: cancel and return `Threw(e)`. `HopOutcome` is internal and never leaves FC5.

### FC6 — Test seams

**Responsibility:** Carry the test-only substitutions, and make sure production never selects them.

**Location:** `app/src/main/java/ie/pantry/data/gateway/GatewaySeams.kt`

**Key classes/functions:**
- `internal class GatewaySeams` (DM8), with `GatewaySeams.PRODUCTION`.
- `internal class TlsOverride(val socketFactory: SSLSocketFactory, val trustManager: X509TrustManager)`.

### FC7 — Wiring and build

**Responsibility:** Declare the dependency and the permission, and put one gateway instance in the container.

**Location:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/ie/pantry/di/AppContainer.kt`

**Key classes/functions:**
- `AppContainer` gets a constructor parameter `gateway: ExternalDataGateway = ExternalDataGateway.create()`, exposed as `val gateway`. `AppContainer.production` passes `ExternalDataGateway.create()` explicitly.

### FC8 — F1 baseline test changes

**Responsibility:** Change F1's two no-network tests to the Q1 form, so they keep proving what they still can.

**Location:** `app/src/test/java/ie/pantry/ManifestPolicyTest.kt`, `app/src/test/java/ie/pantry/data/db/RestartDurabilityTest.kt` (AD14)

### FC9 — Gateway tests and shared test helper

**Responsibility:** Prove R1 to R10, and give F5 and F16 one way to build a gateway against MockWebServer.

**Location:** `app/src/test/java/ie/pantry/data/gateway/`, `app/src/test/java/ie/pantry/testutil/TestGateways.kt`, `app/src/test/resources/gateway/test-server.p12`

**Key classes/functions:**
- `TestGateways.server()` starts a `MockWebServer` bound to the literal `127.0.0.1`. `TestGateways.urlOf(server, path)` builds `http://127.0.0.1:<port><path>`, never `server.url()`: `server.url()` uses the host name `localhost`, which can resolve to `::1`, and `::1` is not exempt.
- `TestGateways.against(server, policy = FAST, dns = …, trustTestCert = false, interceptor = null, listener = …, backoff = …)` builds `ExternalDataGateway(policy, GatewaySeams(exemptTargets = setOf(InetSocketAddress(InetAddress.getByName("127.0.0.1"), server.port)), …), Dispatchers.IO)`.
- `TestGateways.FAST` is `GatewayPolicy(connectTimeout = 1 s, readTimeout = 1 s, attemptTimeout = 2 s, maxBodyBytes = 64 KiB, maxRedirects = 5, retryCount = 2, backoff = [500 ms, 1 s])`.
- `TestGateways.httpsServer()` loads the PKCS12 keystore into a server `SSLSocketFactory`. `TestGateways.trustingTestCert()` builds a trust manager from an in-memory `KeyStore` holding **only** the fixture certificate as a certificate entry, because a PKIX trust manager ignores private-key entries.
- `RecordingDns` (a scripted resolver per hostname, returning a sequence of answers per lookup), `RecordingEventListener` (records `connectStart` socket addresses and `callStart` wall times, and exposes `suspend fun awaitCallStart(n: Int)`, backed by a per-index `CompletableDeferred`, so a test waits deterministically for the nth `callStart` instead of racing it) and `RecordingBackoff` (records the delays, then calls `delay`).

## Data Models

| Model | Field | Type | Constraints | Description |
|-------|-------|------|-------------|-------------|
| DM1 `GatewayResult.Fetched` | body | FetchedBody | Required | Success |
| DM1 `GatewayResult.Failed` | error | GatewayException | Required | Failure (never thrown by the gateway) |
| DM2 `FetchedBody` | bytes | ByteArray | `size <= policy.maxBodyBytes`; a fresh array per call; empty for 204 | Raw de-chunked body bytes as read off the wire, still content-encoded (AD2) |
| DM2 `FetchedBody` | encoding | BodyEncoding | Required | Classified from `Content-Encoding` |
| DM2 `FetchedBody` | mediaType | String? | Null when the response has no `Content-Type` | Raw header value, passed on uninterpreted **[ASSUMPTION — design Q4]** |
| DM3 `BodyEncoding` | — | enum | `IDENTITY` (header absent or `identity`), `GZIP` (`gzip` or `x-gzip`), `OTHER` (anything else, including lists); trimmed and case-insensitive | Tells C1 how to decode |
| DM4 `GatewayException` | category | Category | Required | DM5 |
| DM4 `GatewayException` | callType | CallType | Required | DM6 |
| DM4 `GatewayException` | statusCode | Int? | Non-null iff `category == HTTP_STATUS` (`init`, constant message) | The numeric status only, never the reason phrase |
| DM4 `GatewayException` | attempts | Int | `0..retryCount + 1` | Attempts started. 0 means the call failed before connecting |
| DM4 `GatewayException` | causeType | String? | A class `simpleName`, taken from a `KClass` argument. Null for policy refusals and internal markers | e.g. `SSLHandshakeException` |
| DM4 `GatewayException` | message | String (inherited) | Built only from the fields above: `Gateway failure: category=… callType=… status=… attempts=… cause=…` | No URL, host, address, header, body or term (R9 AC3) |
| DM5 `GatewayException.Category` | — | enum | `INVALID_REQUEST`, `SCHEME_REFUSED`, `ADDRESS_REFUSED`, `TOO_MANY_REDIRECTS`, `TIMEOUT`, `CONNECTION_FAILED`, `HTTP_STATUS`, `RESPONSE_TOO_LARGE`, `UNEXPECTED` | Exactly the spec's Terms set |
| DM6 `CallType` | — | enum | `PAGE_FETCH`, `IMAGE_FETCH`, `NUTRITION_LOOKUP` | Labels errors only. It never selects a policy |
| DM7 `GatewayPolicy` | connectTimeout | kotlin.time.Duration | `> 0`; DEFAULT 10 s | OkHttp connect timeout |
| DM7 `GatewayPolicy` | readTimeout | Duration | `> 0`; DEFAULT 15 s | OkHttp read timeout (also used as the write timeout, AD10) |
| DM7 `GatewayPolicy` | attemptTimeout | Duration | `>= connectTimeout`; DEFAULT 20 s | Per-attempt deadline across all hops (AD6) |
| DM7 `GatewayPolicy` | maxBodyBytes | Long | `> 0`; DEFAULT 5 MiB (`5_242_880`) | Raw-byte cap |
| DM7 `GatewayPolicy` | maxRedirects | Int | `>= 0`; DEFAULT 5 | Redirects followed per attempt |
| DM7 `GatewayPolicy` | retryCount | Int | `>= 0`; DEFAULT 2 | Attempts = retryCount + 1 |
| DM7 `GatewayPolicy` | backoff | List<Duration> | `size == retryCount`; DEFAULT [500 ms, 1 s] | `backoff[i]` is the delay before attempt `i + 2` |
| DM8 `GatewaySeams` | dns | okhttp3.Dns | DEFAULT `Dns.SYSTEM` | Resolver, wrapped by `TargetFilteringDns` |
| DM8 `GatewaySeams` | exemptTargets | Set<InetSocketAddress> | Empty in `PRODUCTION` | Exact (address, port) pairs let through the loopback block (AD8) |
| DM8 `GatewaySeams` | tls | TlsOverride? | Null in `PRODUCTION` | Trust for the test certificate |
| DM8 `GatewaySeams` | interceptor | okhttp3.Interceptor? | Null in `PRODUCTION` | Injects `UNEXPECTED` failures (R6 AC4) |
| DM8 `GatewaySeams` | eventListener | okhttp3.EventListener | `EventListener.NONE` in `PRODUCTION` | Records connection attempts in tests |
| DM8 `GatewaySeams` | nutritionEndpoint | okhttp3.HttpUrl | `NutritionQuery.ENDPOINT` in `PRODUCTION` | Pointed at MockWebServer in tests |
| DM8 `GatewaySeams` | backoffDelay | suspend (Duration) -> Unit | DEFAULT `{ delay(it) }` | Recorded under virtual time in tests |

Every `GatewaySeams` constructor parameter is a `val`, so `GatewaySeams.PRODUCTION` cannot be mutated after construction. RK3's guarantee that production never selects a test seam depends on this.

**Relationships:**
- `ExternalDataGateway` has one `GatewayPolicy`, one `ConnectionTargetPolicy`, one `GatewaySeams` and one `OkHttpClient`. All three call types share them.
- `GatewayResult.Failed` has one `GatewayException`, and the `GatewayException` has no cause.

**Persistence:** none. The gateway keeps no cookie jar, no HTTP cache and no pooled connections (AD10). F16 owns caching nutrition results in F1's `NutritionCacheEntry`.

## Interfaces

```kotlin
package ie.pantry.data.gateway

// ---- I1: the public surface (ExternalDataGateway.kt) ----
class ExternalDataGateway internal constructor(
    internal val policy: GatewayPolicy,
    private val seams: GatewaySeams,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val targetPolicy = ConnectionTargetPolicy(seams.exemptTargets)   // STRICT-equivalent in production
    internal val client: OkHttpClient      // by lazy(SYNCHRONIZED) { buildGatewayClient(policy, targetPolicy, seams) }

    /** GET a user-supplied recipe URL. */
    suspend fun fetchPage(url: String): GatewayResult

    /** GET a page-supplied image URL. Same policy as fetchPage (ARCHITECTURE C7: not a special case). */
    suspend fun fetchImage(url: String): GatewayResult

    /** GET the nutrition endpoint with [term] as the single search parameter. */
    suspend fun lookupNutrition(term: String): GatewayResult

    companion object {
        /** The only public construction path. It uses DEFAULT policy, PRODUCTION seams and Dispatchers.IO. */
        fun create(): ExternalDataGateway
    }
}

// ---- I2: result and error (GatewayResult.kt, GatewayException.kt) ----
sealed interface GatewayResult {
    data class Fetched(val body: FetchedBody) : GatewayResult
    data class Failed(val error: GatewayException) : GatewayResult
}

class FetchedBody internal constructor(val bytes: ByteArray, val encoding: BodyEncoding, val mediaType: String?) {
    override fun toString(): String   // "FetchedBody(size=<n>, encoding=<ENCODING>)" only
}

class GatewayException internal constructor(
    val category: Category,
    val callType: CallType,
    val statusCode: Int?,
    val attempts: Int,
    causeClass: KClass<out Throwable>?,
) : RuntimeException(/* built from the fields above */) {
    val causeType: String?
    enum class Category { INVALID_REQUEST, SCHEME_REFUSED, ADDRESS_REFUSED, TOO_MANY_REDIRECTS,
        TIMEOUT, CONNECTION_FAILED, HTTP_STATUS, RESPONSE_TOO_LARGE, UNEXPECTED }
}

// ---- I3: CFC-4 re-wrapping function (GatewayException.kt) ----
internal fun gatewayFailure(cause: Throwable, callType: CallType, attempts: Int): GatewayException
internal fun gatewayError(category: Category, callType: CallType, attempts: Int, statusCode: Int? = null): GatewayException

// ---- I4: connection-target policy (ConnectionTargetPolicy.kt; pure Kotlin) ----
internal class ConnectionTargetPolicy(private val exemptTargets: Set<InetSocketAddress> = emptySet()) {
    fun classifyTarget(raw: String): TargetVerdict      // SchemeRefused | NoScheme | HttpScheme
    fun isBlocked(address: InetAddress): Boolean
    fun permits(address: InetAddress, port: Int): Boolean
    fun permitsForResolution(address: InetAddress): Boolean
    companion object { val STRICT: ConnectionTargetPolicy }
}

// ---- I5: transport (GatewayTransport.kt) ----
internal fun buildGatewayClient(policy: GatewayPolicy, targets: ConnectionTargetPolicy, seams: GatewaySeams): OkHttpClient

// ---- I6: nutrition URL (NutritionQuery.kt) ----
internal object NutritionQuery {
    val ENDPOINT: HttpUrl                                 // https://world.openfoodfacts.org/cgi/search.pl
    fun url(endpoint: HttpUrl, term: String): HttpUrl     // endpoint.newBuilder().addQueryParameter(...)...
}
```

**Contracts:**
- **I1 preconditions:** none. Every `String`, including an empty one, is accepted, and a malformed one gives a `Failed` value.
- **I1 postconditions:** returns `Fetched` or `Failed`. It never returns `null` and never throws, except `CancellationException` when the caller is cancelled (R1 AC1, AC4). `Fetched.body.bytes.size <= policy.maxBodyBytes`. Validation order for `fetchPage`/`fetchImage`: `classifyTarget(url)` (`SchemeRefused` → `SCHEME_REFUSED`, `NoScheme` → `INVALID_REQUEST`), then `url.toHttpUrlOrNull()` (`null` → `INVALID_REQUEST`), then the attempt loop. For `lookupNutrition`: `term.isBlank()` → `INVALID_REQUEST`, then `NutritionQuery.url(seams.nutritionEndpoint, term)`, then the attempt loop. Pre-connection failures carry `attempts = 0`.
- **I1 side effects:** outbound GETs only. No logging, no persistent state, and no work left running after cancellation, except a blocked DNS lookup finishing on its IO thread (AD7).
- **I1 thread-safety:** safe for concurrent calls. The client is built lazily and exactly once.
- **I2:** `GatewayException.toString()`, `message`, `localizedMessage` and `stackTraceToString()` contain only enum names, integers, a class simple-name and stack frames. `cause == null`, and `suppressed` is empty.
- **I3:** `gatewayFailure` classifies by the first match over `{cause} ∪ cause chain ∪ suppressed`, visiting each throwable once: `AddressRefusedException` → `ADDRESS_REFUSED`; `ResponseTooLargeException` → `RESPONSE_TOO_LARGE`; `InterruptedIOException` (including `SocketTimeoutException` and OkHttp's call-timeout `InterruptedIOException`) → `TIMEOUT`; any other `IOException` (`UnknownHostException`, `ConnectException`, `SSLException`, `SocketException`, `ProtocolException`, `EOFException`, `UnknownServiceException`) → `CONNECTION_FAILED`; anything else → `UNEXPECTED`. The markers are checked before `IOException` because they subclass it. `causeType` is `null` for the two markers and `cause::class` otherwise. The stack frames are copied from `cause`.
- **I4:** `classifyTarget` is total and never throws. `isBlocked` works on `address.address` bytes, so it does not depend on how the address was written (RK8). `permits` is the socket-level check. `permitsForResolution` ignores ports.
- **I5:** the returned client satisfies AD10. `GuardedSocket.connect` throws `AddressRefusedException` without opening a connection when `permits` is false or the endpoint is unresolved.
- **I6:** `url(e, t).queryParameter("search_terms") == t`, and `url(e, t).queryParameterNames` equals the constant name set.

## Error Handling

- **Strategy:** a sealed result value (`GatewayResult`) whose failure side is a `Throwable`-shaped, content-free `GatewayException` (AD1). Expected failures never leave the gateway as thrown exceptions.

| Condition | Category | Retried | Where decided |
|---|---|---|---|
| Blank term; URL with no scheme or unparseable; `Location` unparseable | `INVALID_REQUEST` | No | FC5 before the loop, FC5 redirect step |
| Explicit non-`http(s)` scheme, initial or `Location` | `SCHEME_REFUSED` | No | FC3 `classifyTarget` |
| Resolved or literal address blocked (Dns filter leaves nothing, or the socket guard refuses) | `ADDRESS_REFUSED` | No | FC4, mapped by I3 |
| A redirect received after `maxRedirects` redirects already followed | `TOO_MANY_REDIRECTS` | No | FC5 |
| Connect or read timeout, or the attempt deadline passes | `TIMEOUT` | Yes | OkHttp, mapped by I3; FC5 also returns it when no time is left before a hop |
| DNS failure, refused, reset, TLS failure, protocol error, cleartext not permitted | `CONNECTION_FAILED` | Yes | OkHttp, mapped by I3 |
| 5xx | `HTTP_STATUS` (code) | Yes | FC5 |
| Any other non-2xx (4xx including 429, 304, 300, 3xx with no `Location`) | `HTTP_STATUS` (code) | No | FC5 |
| `Content-Length` over the cap, or more than cap bytes read | `RESPONSE_TOO_LARGE` | No | FC5, FC4 `CappedSource` |
| Any other `Exception` from OkHttp, an interceptor or a gateway defect | `UNEXPECTED` | No | I3; each public op's defensive `catch (e: Exception)` after `catch (e: CancellationException) { throw e }` |
| Coroutine cancellation | propagates as `CancellationException` | No further attempt | AD7 |
| `Error` (for example OOM) | not caught | — | Same rule as F3's AD12 |

- **Custom exceptions / error types:** `GatewayException` (public, AD1), plus the internal markers `AddressRefusedException` and `ResponseTooLargeException`. Both markers have constant messages, subclass `IOException` (not `ConnectException`, so OkHttp's `connectSocket` does not wrap them with the socket address), and are always converted by I3.
- **Logging:** none. The gateway has no logger, and no OkHttp logging interceptor is installed (R9 AC4). OkHttp's own `Platform.log` messages come only from leaked responses (every response is closed by `use`) and asynchronous callbacks (not used, AD7). DR3 records the remaining exposure.
- **User-facing errors:** none from F4. F5's UI (C9) maps `category` to a plain message, and uses `statusCode` only for text such as "the site returned 404".
- **CFC-4:** every `GatewayException` is built by `gatewayError` or `gatewayFailure`. Neither chains a cause, and the constructor accepts no free text. The third-party exceptions that carry content in their messages (`UnknownHostException` with the host, `SSLPeerUnverifiedException` with certificate subjects, OkHttp `ConnectException` "Failed to connect to <address>", `UnknownServiceException` "CLEARTEXT … to <host>") are reduced to a class simple-name.

## Testing Strategy

- **Framework:** JUnit 4 (`junit:junit:4.13.2`), `kotlin.test` assertions, `kotlinx-coroutines-test` 1.9.0 (`runTest` with an explicit `timeout = 30.seconds` where real I/O is involved), and `okhttp3.mockwebserver` 4.12.0. Robolectric only in the existing Robolectric classes that FC8 and `AppContainerTest` modify.
- **Test location:** `app/src/test/java/ie/pantry/data/gateway/` mirrors the main package. The shared helper is `app/src/test/java/ie/pantry/testutil/TestGateways.kt`, and the TLS fixture is `app/src/test/resources/gateway/test-server.p12`.
- **Mocking approach:** no mocking library. Real `MockWebServer` instances, and seams injected through `GatewaySeams` (`RecordingDns`, `RecordingEventListener`, `RecordingBackoff`, a throwing `Interceptor`). Blocked-address fixtures use a **second, non-exempt `MockWebServer`** on loopback, which must record zero requests, so "no bytes sent" is observed on a live listener.
- **Coverage expectations:** every GIVEN/WHEN/THEN in R1 to R10 is asserted by the class named below. Every `Category` is produced at least once for each call type, except where the spec's own criterion names fewer call types. Every `ConnectionTargetPolicy` range has an inside fixture and, where one exists, an outside neighbour. Every public function in I1 and I4 has a happy-path and a failure-path test.
- **Fixtures / test data:** one `MockWebServer` per test (a `@Before`/`@After` pair). Gateways come from `TestGateways.against(server)` with the `FAST` policy. The `DEFAULT` cap is exercised once per call type in `GatewayFailureMappingTest`. The TLS keystore is generated once, and the command is recorded in the file's KDoc: `keytool -genkeypair -alias test-server -keyalg EC -groupname secp256r1 -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1" -validity 36500 -storetype PKCS12 -keystore app/src/test/resources/gateway/test-server.p12 -storepass pantry-test -keypass pantry-test`. Sentinels come from the existing `ie.pantry.testutil.Sentinels`. The TLS fixture is an EC P-256 certificate, and OkHttp's default `ConnectionSpec` (`MODERN_TLS`) is expected to negotiate with it; this is verified by the first HTTPS test to run, and a handshake failure is asserted to surface as `CONNECTION_FAILED` and not as a trust failure. Chains with two distinct HTTPS hops, each with its own trust override, are not covered by any fixture in this design.
- **Naming convention:** backtick-quoted descriptive names, as in F1 to F3.

| Test class | Runner | Covers | Notes |
|---|---|---|---|
| `ConnectionTargetPolicyTest` | plain JVM | R2 AC1 (scheme table), AC4 (scheme part); R3 AC1, AC2 | One test per family, not one 33-row GIVEN (`[SEAL-08]`). **IPv4:** the spec's inside and boundary fixtures, verbatim. **IPv6 (`[DEF-05]`)**: inside `::`, `::1`, `fc00::`, `fc00::1`, `fd12:3456::1`, `fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, `fe80::`, `fe80::1`, `febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, `fec0::`, `fec0::1`, `feff:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, `ff00::`, `ff02::1`, `ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff`. Allowed neighbours: `::2`, `fbff:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, `fe00::`, `fe7f:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, `2001:4860:4860::8888`. `fe80::/10`, `fec0::/10` and `ff00::/8` are contiguous (`fe80::` to the end of the space), so no allowed neighbour exists above them. **Mapped:** the three spec forms are refused, and `::ffff:126.255.255.255` is allowed. Mapped forms are also built as a raw 16-byte `Inet6Address.getByAddress(null, bytes, null)`, because `InetAddress.getByName` turns them into `Inet4Address`. `permits` with and without an exemption. `classifyTarget`: `HTTP://x` → `HttpScheme`; `ht!tp://x`, `""`, whitespace → `NoScheme`; `javascript:` and `data:` → `SchemeRefused`. A source check finds no `import okhttp3` and no `import android` |
| `GatewayTransportTest` | plain JVM | R3 AC6 (connect-time check); R5 AC2 (cap+1); AD10 configuration | `GuardedSocket(ConnectionTargetPolicy.STRICT).connect(InetSocketAddress(blocked, port))` throws `AddressRefusedException` for every blocked range's first fixture. For `127.0.0.1`, the port is a live `MockWebServer`'s port, and the server records 0 requests. An unresolved address is refused. `CappedSource` over a counting upstream: the upstream delivers at most cap + 1 bytes, and the source throws on byte cap + 1. `TargetFilteringDns` over the mixed answer returns only the allowed address, and throws `AddressRefusedException` when every address is blocked. The built client's `proxy == NO_PROXY`, `cookieJar == NO_COOKIES`, `cache == null`, both redirect flags false, `retryOnConnectionFailure == false`, and `networkInterceptors` contains exactly one `RawByteCapInterceptor` |
| `GatewayTargetValidationTest` | plain JVM | R2 AC1–AC4; R3 AC3–AC6; R4 AC1–AC3 | The initial-scheme table for the page and image fetch, with the server count and the listener's `connectStart` count both 0. The 302 to `intent://evil#Intent;end` and to `content://…` give `SCHEME_REFUSED`. The `http→https→http` chain uses two servers, one with `useHttps` and `trustingTestCert()`. Literal `127.0.0.1` and `[::1]` URLs on the non-exempt server's port, and a `RecordingDns` hostname mapped to `127.0.0.1` on that port. The Dns pre-filter passes it, because it exempts by address only, and the socket guard refuses it on the port. All three call types. Mixed answer: `RecordingDns` returns `[10.0.0.1, exempt 127.0.0.1]`, the call succeeds, and `connectStart` never sees `10.0.0.1`. Redirects to blocked IPv4 and IPv6 targets. **Rebinding:** lookup 1 gives the exempt `127.0.0.1`, and every later lookup gives `[10.0.0.1, ::1]`. Call 1 succeeds. Call 2 (a fresh lookup, since there is no pooled connection, AD9, AD10) gives `ADDRESS_REFUSED`, and `connectStart` never sees `10.0.0.1` or `::1`. That the check sits at the socket actually connected is proved separately, with no Dns involved, in `GatewayTransportTest`. **`[DEF-03]`:** `http://2130706433:<p>/`, `http://017700000001:<p>/` and `http://0x7f000001:<p>/` against the non-exempt server's port give `ADDRESS_REFUSED` or `CONNECTION_FAILED` (depending on whether the JVM resolver accepts the form), never a success, and the server records 0 requests. R4: exactly 5 redirects then 200 succeeds; 6 redirects and a self-loop give `TOO_MANY_REDIRECTS` with `requestCount == 6` for all three call types; a missing `Location` gives `HTTP_STATUS` 302; `http://[::bad` gives `INVALID_REQUEST`; a relative (`/x`) and a protocol-relative (`//host/x`) `Location` are resolved against the current hop and then checked like any hop; a 300 or 304 with a `Location` gives `HTTP_STATUS` |
| `GatewayFailureMappingTest` | plain JVM | R5 AC1–AC5; R6 AC1–AC4 | For each call type: a body at the cap and at cap + 1, for both `FAST` and `DEFAULT`. A true over-cap `Content-Length`, a chunked over-cap body (`setChunkedBody`), and the false-`Content-Length` fixture (asserted per design Q2's resolution). A gzip body: `bytes` equals the compressed bytes and `encoding == GZIP`. After an over-cap call, `client.connectionPool.connectionCount() == 0` and the listener saw `connectionReleased`. Timeouts use `SocketPolicy.NO_RESPONSE` and `throttleBody(1024, 1, HOURS)`, with wall time per attempt ≤ attemptTimeout + 1 s and per call ≤ 3 × attemptTimeout + 1 s (back-off is virtual). The status table 204, 304, 400, 403, 404, 410, 429, 500, 502 and 503. Connection failures: `DISCONNECT_AT_START`, `DISCONNECT_DURING_RESPONSE_BODY`, a closed port, a `RecordingDns` that throws `UnknownHostException`, and the HTTPS server **without** `trustingTestCert()`. A throwing interceptor gives `UNEXPECTED`. The distinctness test uses an exhaustive `when` |
| `GatewayRetryTest` | plain JVM | R7 AC1–AC4; R1 AC4 | `RecordingBackoff` records `[500 ms, 1 s]`, and `testScheduler.currentTime == 1500` after 3 attempts, for a dropped connection and for 503, for each call type. Attempts are counted as `callStart` events, because MockWebServer does not count a request it disconnects before reading (`DISCONNECT_AT_START`). The 503 case also asserts `requestCount == 3`. Fail then 200 gives exactly 2 requests. Non-retryable table: 400, 404, 429, `SCHEME_REFUSED`, `ADDRESS_REFUSED`, `TOO_MANY_REDIRECTS`, `RESPONSE_TOO_LARGE`, `INVALID_REQUEST` and `UNEXPECTED` (`[SEAL-06]`) give 1 or 0 requests and an empty back-off record, except `TOO_MANY_REDIRECTS`, which gives `requestCount == 6` within one attempt (R7 AC3, R4 AC2) and an empty back-off record. A timeout is retried (3 `callStart`s). **Cancellation:** a `NO_RESPONSE` server; the test calls `RecordingEventListener.awaitCallStart(1)` and then cancels the calling `Job`, so the cancel is never sent before the first call has started. `join` returns within 1 s wall time, the job is cancelled (not completed with `Failed`), `callFailed` is recorded (OkHttp saw `cancel()`), and no second `callStart` follows within `attemptTimeout`. **Redirect then transient failure:** a 302 on the first hop to a second-hop `503` or dropped connection, then a 200 on the retry, asserts that the next attempt re-issues the request from the initial URL, not the redirected location (AD6) |
| `GatewayRequestInspectionTest` | plain JVM | R8 AC1–AC5 | The recorded request: method `GET`, body size 0, and the header-name set equals `{Host, Connection, Accept-Encoding, User-Agent}` with `Accept-Encoding: gzip`, which is OkHttp's default set (AD2). No `Authorization`, `Proxy-Authorization` or `Cookie`. `requestUrl` and the recorded `Host` equal the supplied URL as parsed by `HttpUrl`, with the fragment and any userinfo dropped (R8 AC1). Term `crème fraîche & salt/pepper?#x=1`: `requestUrl.queryParameter("search_terms")` equals it, the name set equals `NutritionQuery`'s constants, and the path equals the endpoint's. Cookie then redirect, then a second call: no request carries `Cookie`. Userinfo URL: no `Authorization`. A blank term gives 0 requests. **R8 AC2** source check on `NutritionQuery.kt`: contains `addQueryParameter`, and has no `"$`, `${`, `+ term`, `.plus(` or `String.format` |
| `GatewayErrorHygieneTest` | plain JVM | R9 AC1–AC3 | Every failure path in R2 to R8 is driven with `Sentinels.URL`, a URL with `SENTINEL` in its host, path and query, a sentinel `Location`, a sentinel body and reason phrase (`setStatus("HTTP/1.1 500 SENTINEL")`), `Sentinels.INGREDIENT` as the term, and a `RecordingDns` that throws `UnknownHostException("SENTINEL host")`. Every `error.assertNoSentinel()` passes, and `error.cause == null` and `error.suppressed.isEmpty()`. Reflection: `GatewayException`'s declared fields have types in `{Category, CallType, Integer, int, String (causeType only)}`. Unit tests of `gatewayFailure` over a nested `IOException(SENTINEL)` → `ConnectException(SENTINEL)` chain, and over a suppressed `AddressRefusedException` |
| `SingleCallSiteTest` | plain JVM | R1 AC1, AC3; R9 AC4; RK3 | Uses `RepoPaths.repoRoot()`. Only files under `ie/pantry/data/gateway/` contain `import okhttp3.`. No main file contains `HttpURLConnection`, `openConnection`, `openStream` or `java.net.http`. `GatewaySeams(` appears in no main file other than `GatewaySeams.kt`, and a direct `ExternalDataGateway(` constructor call (not `.create()`) appears in no main file other than `ExternalDataGateway.kt`, closing the gateway-constructor path alongside the seams path. Reflection finds exactly three public methods on `ExternalDataGateway` whose last parameter is `kotlin.coroutines.Continuation`, named `fetchPage`, `fetchImage` and `lookupNutrition`. No public method or field signature in the package mentions an `okhttp3` type. Members whose JVM name contains `$` are skipped, because they are Kotlin `internal` members compiled with mangled public names (for example `getClient$app_debug`). **R9 AC4:** gateway main sources contain no `Log.`, `println`, `printStackTrace`, `HttpLoggingInterceptor` or `String.format`, and every `throw`, `require(`, `check(` and `error(` message is a `$`-free string literal (deliberately stricter than R9 AC4, which also allows enum names, integers and class simple names). The limitation of the static check (a message built through a variable or helper is missed, `[DEF-06]`) is documented in the KDoc. It is closed structurally by AD1's `String`-free constructor and dynamically by `GatewayErrorHygieneTest` |
| `AppContainerTest` (modified) | Robolectric | R1 AC2; RK3 | `app.container.gateway` is the same instance on each read; `gateway.client` is the same instance on each read; `gateway.policy == GatewayPolicy.DEFAULT`. The production container's `fetchPage("http://127.0.0.1:<port>/")` against a live server gives `ADDRESS_REFUSED` with `requestCount == 0` |
| `ManifestPolicyTest`, `RestartDurabilityTest` (modified) | Robolectric | R10 AC1, AC2 | AD14. `ManifestPolicyTest` also asserts `FLAG_USES_CLEARTEXT_TRAFFIC` (design Q1, confirmed) |

## File Structure

```
pantry/                                                         (repository root)
├── gradle/libs.versions.toml                                   — MODIFIED (FC7): okhttp = "4.12.0"; okhttp, okhttp-mockwebserver aliases
└── app/
    ├── build.gradle.kts                                        — MODIFIED (FC7): implementation(libs.okhttp), testImplementation(libs.okhttp.mockwebserver)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                             — MODIFIED (FC7): INTERNET permission; usesCleartextTraffic (design Q1)
        │   └── java/ie/pantry/
        │       ├── data/gateway/                               — NEW package (C7)
        │       │   ├── ExternalDataGateway.kt                  — FC5: I1, attempt/redirect loops, hop bridge, HopOutcome, create()
        │       │   ├── NutritionQuery.kt                       — FC5: ENDPOINT, parameter constants, url() (AD11)
        │       │   ├── GatewayResult.kt                        — FC1: GatewayResult, FetchedBody, BodyEncoding, CallType
        │       │   ├── GatewayException.kt                     — FC1: CFC-4 surface — GatewayException, Category, gatewayError, gatewayFailure (re-wrap rule), markers, isTransient
        │       │   ├── GatewayPolicy.kt                        — FC2: GatewayPolicy, DEFAULT
        │       │   ├── ConnectionTargetPolicy.kt               — FC3: scheme verdicts, blocked ranges, exemptions (pure Kotlin)
        │       │   ├── GatewayTransport.kt                     — FC4: buildGatewayClient, TargetFilteringDns, GuardedSocketFactory, GuardedSocket, RawByteCapInterceptor, CappedSource
        │       │   └── GatewaySeams.kt                         — FC6: GatewaySeams, PRODUCTION, TlsOverride
        │       └── di/AppContainer.kt                          — MODIFIED (FC7): gateway constructor param + val; production passes create()
        └── test/
            ├── java/ie/pantry/
            │   ├── data/gateway/                               — NEW (FC9)
            │   │   ├── ConnectionTargetPolicyTest.kt           — R2, R3 tables
            │   │   ├── GatewayTransportTest.kt                 — socket guard, Dns filter, CappedSource, client config
            │   │   ├── GatewayTargetValidationTest.kt          — R2, R3, R4
            │   │   ├── GatewayFailureMappingTest.kt            — R5, R6
            │   │   ├── GatewayRetryTest.kt                     — R7, R1 AC4
            │   │   ├── GatewayRequestInspectionTest.kt         — R8
            │   │   ├── GatewayErrorHygieneTest.kt              — R9 AC1–AC3 (CFC-4)
            │   │   └── SingleCallSiteTest.kt                   — R1 AC1/AC3, R9 AC4, RK3
            │   ├── testutil/TestGateways.kt                    — NEW (FC9): against(), FAST, TLS helpers, RecordingDns/EventListener/Backoff
            │   ├── data/db/RestartDurabilityTest.kt            — MODIFIED (FC8): classpath assertion → source guard
            │   ├── di/AppContainerTest.kt                      — MODIFIED (FC9): single gateway, production refuses loopback
            │   └── ManifestPolicyTest.kt                       — MODIFIED (FC8): INTERNET-only; no okhttp3 components
            └── resources/gateway/test-server.p12               — NEW (FC9): self-signed localhost/127.0.0.1 PKCS12
```

These files are not changed: `PantryDatabase`, the entities, `app/schemas/`, `app/src/main/assets/`, and every F2 and F3 source.

## Dependencies

| Package | Purpose |
|---------|---------|
| `com.squareup.okhttp3:okhttp:4.12.0` (`implementation`, alias `libs.okhttp`) | The HTTP client that ARCHITECTURE Technology Choices names (AD3). It brings `com.squareup.okio:okio` 3.6.0 transitively |
| `com.squareup.okhttp3:mockwebserver:4.12.0` (`testImplementation`, alias `libs.okhttp.mockwebserver`) | Loopback test server. Same version ref as `okhttp` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` (existing, transitive through `room-ktx`, as F2's `ReferenceDataStore` already uses) | `suspendCancellableCoroutine`, `Dispatchers.IO`, `delay` |
| `kotlinx-coroutines-test` 1.9.0, `junit` 4.13.2, `kotlin-test-junit` (existing) | Virtual time and assertions |

Both new aliases go in `gradle/libs.versions.toml` (F1 R1). `okhttp-tls` (for `HeldCertificate`) was rejected: it would be a third new artifact (Ask First), and a JDK-`keytool` PKCS12 fixture gives the same TLS coverage. Licence: Apache 2.0, the same as the rest of the stack (ARCHITECTURE External Dependencies).

## Integration Points

| Existing Module | Direction | Change Required | Details |
|-----------------|-----------|-----------------|---------|
| `ie.pantry.di.AppContainer` | Called by / holds | Yes (FC7) | New last constructor parameter `gateway: ExternalDataGateway = ExternalDataGateway.create()` and `val gateway`. Existing 3- and 4-argument call sites in tests still compile. `production(...)` passes `create()` explicitly. Construction does no I/O (AD10) |
| `ie.pantry.PantryApplication` | Indirect | No | `createContainer()` still calls `AppContainer.production(this)` |
| `app/src/main/AndroidManifest.xml` | Config | Yes (FC7) | `<uses-permission android:name="android.permission.INTERNET" />`. `android:usesCleartextTraffic="true"` (R10 AC1). `allowBackup="false"` is unchanged |
| `ManifestPolicyTest`, `RestartDurabilityTest` (F1) | Test edit | Yes (FC8; Q1) | AD14. This touches load-bearing F1 guards, so both keep every assertion that is still true |
| `ie.pantry.testutil.Sentinels` / `assertNoSentinel()` / `RepoPaths` | Test-only calls into | No | Used as they are (AD1) |
| F5 recipe import (C1, future) | Calls into | No (F5's spec) | Calls `container.gateway.fetchPage(url)` and then `fetchImage(imageUrl)`. On `Fetched`, it decodes `bytes` according to `encoding` under its own decompressed cap, and checks `mediaType`. On `Failed`, it branches on `error.category` with an exhaustive `when` and may keep `error` as its own typed error's payload or cause, which is safe because it is content-free (AD12). Its tests build gateways with `TestGateways.against(server)` and assert hygiene with `assertNoSentinel()`. It applies AD12's rules (a) to (e) to jsoup exceptions |
| F16 nutrition lookup (C6, future) | Calls into | No (F16's spec) | Calls `lookupNutrition(term)` and interprets `bytes` itself. Handles `Failed` the same way as F5. Owns caching in `NutritionCacheDao` |
| F9 (C6, future) | None | No | Offline estimates. F9 makes no gateway calls |
| C8 retailer assist (F14/F15, future) | None | No | Custom Tabs handoff. It must not use the gateway (spec Never Do) |

The load-bearing touch is the manifest. It is guarded by `ManifestPolicyTest` and by the closeout comparison of the release merged manifest (Implementation Sequence step 1 and step 8).

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| DR1 | `mockwebserver` 4.12.0 is not in `.toolchain/gradle-home`, and this devcontainer has no route to Maven Central, so the gateway test suite cannot compile here | High | Med | Implementation Sequence step 1 resolves it first: fetch the artifact on a networked machine, or pre-populate `.toolchain/gradle-home` or `.toolchain/m2`, before any test is written |
| DR2 | Cleartext traffic is blocked by default at `targetSdk 35`, so every `http://` fetch on a device fails with `UnknownServiceException` (`CONNECTION_FAILED`), while JVM tests pass | High (if unaddressed) | High | Design Q1; AD14; a `ManifestPolicyTest` assertion |
| DR3 | An OkHttp-internal log line (a leaked-response warning via `Platform.log`) contains a host | Low | Med | Every response goes through `use` (AD9, FC5). No async calls (AD7). No logging interceptor. `SingleCallSiteTest` source checks |
| DR4 | Socket-level read-ahead pulls up to one Okio segment (8 KiB) past cap + 1 before `CappedSource` stops it | High | Low | Memory stays bounded by cap + 8 KiB. R5 AC2 is asserted at the codec boundary (`GatewayTransportTest`), which is the strongest point a buffered client can guarantee |
| DR5 | Cancellation during a blocking DNS lookup leaves an IO thread busy until the OS resolver returns | Med | Low | AD7 resumes the caller at once, and `call.cancel()` stops any connect after the lookup. The thread count is bounded by `Dispatchers.IO` |
| DR6 | OFF rate-limits search (documented at 10 requests/min/IP) or rejects the default `okhttp/4.12.0` User-Agent | Med | Med | Design Q3. OFF's architecture row already says "degrade, do not fail", and F16 caches |
| DR7 | A future OkHttp upgrade changes a behaviour AD3 depends on (Dns bypass, pool semantics, `BridgeInterceptor` gzip rule) | Low | High | The socket guard does not depend on Dns (AD5). `GatewayTransportTest` and `GatewayFailureMappingTest`'s gzip test fail loudly if the gzip or pool behaviour changes |
| DR8 | Embedded-IPv4 forms outside Q4's list (NAT64 `64:ff9b::/96`, 6to4 `2002::/16`) reach a private IPv4 address on some networks | Low | Med | Not blocked, because that is Ask First. Recorded here for a PLAN-level decision |
| DR9 | A Dns-filter bug hides a hole in the socket guard, because integration tests pass through both layers | Low | High | `GatewayTransportTest` checks `GuardedSocket` directly with no Dns. Literal-IP integration tests bypass Dns by OkHttp's own design (AD5) |
| DR10 | A false `Content-Length` below the cap gives a truncated success instead of `RESPONSE_TOO_LARGE` | Med | Low | Design Q2. Memory stays bounded by the declared length, and the connection is never reused (pool off), so the unread bytes cannot corrupt a later response |

## Implementation Sequence

1. **Build setup.** Resolve DR1. Capture the pre-F4 `processReleaseMainManifest` output for later comparison. Add the catalog aliases and the `build.gradle.kts` lines. Everything else needs the dependency.
2. **FC3 `ConnectionTargetPolicy`** with `ConnectionTargetPolicyTest`. This is pure Kotlin with no dependencies, and it carries the highest correctness risk (the range arithmetic, `[DEF-05]`), so it goes first. It can run in parallel with step 3.
3. **FC1 and FC2 models** (`GatewayResult.kt`, `GatewayException.kt`, `GatewayPolicy.kt`) with the unit half of `GatewayErrorHygieneTest` (`gatewayFailure` classification, field reflection, constructor invariants).
4. **FC4 transport and FC6 seams** with `GatewayTransportTest`. This retires DR9 and DR4 before any orchestration exists. Needs steps 2 and 3.
5. **FC9 test helper** (`TestGateways.kt`, `test-server.p12`). Needs step 4's `GatewaySeams`.
6. **FC5 orchestrator.** First the hop bridge and the attempt loop without redirects (`GatewayFailureMappingTest`, `GatewayRetryTest`, including cancellation), then the redirect loop (`GatewayTargetValidationTest`), then `NutritionQuery` (`GatewayRequestInspectionTest`), then the rest of `GatewayErrorHygieneTest`. Cancellation and timing (AD7) are the most uncertain parts, so they are built and tested before redirects.
7. **FC7 wiring and FC8 F1 edits.** `AppContainer`, the manifest (with design Q1 resolved), `AppContainerTest`, `ManifestPolicyTest`, `RestartDurabilityTest` and `SingleCallSiteTest`. Needs step 6.
8. **Closeout.** The spec's Commands: the F4 test filter, the full `./gradlew :app:testDebugUnitTest`, `./gradlew lintDebug`, the merged release manifest `grep` (exactly one `android.permission`), and a diff of the release manifest's `<activity|service|receiver|provider>` lines against step 1's capture (no change).

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: Cleartext traffic. `targetSdk 35` blocks `http://` by default, so R2 AC3 and ARCHITECTURE C1's "both schemes are accepted" fail on a device unless the manifest allows cleartext. **[ASSUMPTION]** Add `android:usesCleartextTraffic="true"` to `<application>` (app-wide; the gateway is the only HTTP client, R1 AC3), and assert it in `ManifestPolicyTest`. The spec's R10 AC1 and Modified Files now name it too (spec pass 5). This is not a permission or a component, so R10 AC1 still holds. Confirm, or choose a `networkSecurityConfig` that permits cleartext (same effect, one more resource file).
  - **Resolution:** Confirmed as written by the developer (2026-09-30), after re-confirming the underlying ARCHITECTURE C1 decision to accept both schemes (no certificate pinning, all fetched content already treated as untrusted regardless of transport). `android:usesCleartextTraffic="true"` on `<application>`, asserted in `ManifestPolicyTest`.
- [x] Q2: R5 AC2's fixture "a `Content-Length` that falsely states a size under the cap" cannot give `RESPONSE_TOO_LARGE` with OkHttp or any compliant HTTP/1.1 client. The client reads exactly the declared bytes, and the surplus is never read. **[ASSUMPTION]** For that one fixture, the test asserts that the call returns `Fetched` with exactly the declared bytes, that no more than cap + 1 body bytes were consumed, and that the connection was closed. This needs an R5 AC2 amendment through spec re-approval. Confirm, or name another observable outcome.
  - **Resolution:** Confirmed as written by the developer (2026-09-30). `specs/F4-external-data-gateway/01_spec.md` R5 AC has been amended and re-approved (content hash `dd3e6425d49d4d2b`) to split the falsely-low-`Content-Length` fixture into its own criterion with exactly this outcome; the spec's Success Criteria section was updated to match. This design's AD2/AD9 and the `GatewayFailureMappingTest` row already anticipated this resolution.
- [x] Q3: Nutrition endpoint and User-Agent (spec Q5). Open Food Facts' current documentation recommends Search-a-licious (`search.openfoodfacts.org`) for full-text search and calls `/cgi/search.pl` legacy, but Search-a-licious's own API docs were unreachable from this environment. OFF also asks for a custom `User-Agent` of the form `AppName/Version (contact)`. **[ASSUMPTION]** Use `https://world.openfoodfacts.org/cgi/search.pl` with constant `search_simple=1&action=process&json=1&page_size=5` and the term in `search_terms`, and keep OkHttp's default User-Agent (a custom header is Ask First). Confirm the endpoint. Also decide whether a constant, app-identifying `User-Agent: Pantry/<versionName>`, which identifies neither the user nor the device, is approved.
  - **Resolution:** Developer confirmed (2026-09-30): use `/cgi/search.pl` as written — documented and confirmed working now, and switching to Search-a-licious later only touches `NutritionQuery.kt` per AD13's file split. No custom `User-Agent`; OkHttp's default is kept, matching AD11's `[ASSUMPTION]` as written.
- [x] Q4: Fields of the success value. R1 AC1 specifies only "the response body". AD2 adds `encoding`, which the gzip Decision Point requires, and `mediaType`, because C1 rejects non-HTML **[ASSUMPTION]**. The final URL after redirects is **not** included, but C1 may need it to resolve a relative image URL against the page's real address. Confirm `mediaType`, and say whether `finalUrl: String` should be added now or left to F5 to request (it would be an additive field).
  - **Resolution:** Developer confirmed (2026-09-30): keep `mediaType` as written, and leave `finalUrl` out for now — simpler today, and adding it later is a purely additive field change when F5 actually needs it, not a breaking one.

## Panel Review

<!-- Populated by the skill across panel-review passes. archive_pass.py manages
     Trajectory and Sealed dispositions automatically; the synthesizer populates
     Latest pass detail per pass.

     Disposition vocabulary: Addressed / Deferred → tasks.md / Sealed /
     Accepted as risk / User input needed / Halt and re-scope. Sealed and
     Accepted as risk must include "Defense: <reason>" in Notes. Severity tags
     in Latest pass detail are bracketed: [HIGH] / [MED] / [LOW], optionally
     [REGRESSION].

     See SKILL.md "Panel Review section format" for the normative spec. -->

### Trajectory

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes       |
|------|------------|-------|-------------|-----------|----------|--------|-------------|
| 1    | 2026-10-04 | 1     | 0           | 11        | 0        | 1      | tags=d0u0c1 |

### Sealed dispositions

- `[SEAL-01]` **IPv4-compatible IPv6 literals (`::a.b.c.d`) fall through…** (pass 1, accepted-as-risk) — Defense: the form is inside the blocking-range boundary the developer already decided at spec Q4 (Ask First; disclosed as DR8 and in Non-Goals). Accepting it as a disclosed gap follows that user decision and is synthesizer-judged within it, not a new sign-off.

### Deferred dispositions

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `624e59d94585966f`
- **Hash basis:** v2
