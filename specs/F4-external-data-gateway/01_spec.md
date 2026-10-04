# Feature: External Data Gateway

**PLAN feature identifier:** `F4`

## Objective

**From PLAN F4:** `blueprint/03_PLAN.md` → Feature Breakdown → `### F4: External Data Gateway` (Description and Acceptance Criteria), plus `## Cross-Feature Contracts` → CFC-4; component C7 per `blueprint/02_ARCHITECTURE.md` → `### C7 — External Data Gateway`.

F4 is where the app first gets the `INTERNET` permission and an HTTP client, which reverses the no-network baseline that F1's `ManifestPolicyTest` and `RestartDurabilityTest` assert today, and it builds the nutrition call type even though nothing will call it until F16 connects C6 to it after the MVP.

## Requirements

**Terms.** A *call type* is one of the gateway's three operations: *page fetch* (GET a user-supplied recipe URL), *image fetch* (GET a page-supplied image URL) and *nutrition lookup* (GET the external food-data endpoint with one ingredient term). A *hop* is one HTTP request the gateway makes: the initial request, or the request that follows one redirect. A *connection target* is a hop's URL together with the IP address the client actually connects to for it. A *blocked address* is one in any of the ranges in PLAN F4 and ARCHITECTURE C7 — loopback (`127.0.0.0/8`, `::1`), link-local (`169.254.0.0/16`, `fe80::/10`) or private (RFC 1918 `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, and unique-local `fc00::/7`) — plus the ranges Q4 adds beyond PLAN's list: `0.0.0.0/8` and the unspecified addresses `0.0.0.0`/`::`, CGNAT `100.64.0.0/10`, multicast (`224.0.0.0/4`, `ff00::/8`), and deprecated site-local `fec0::/10`. The *raw-byte cap* is the maximum number of response-body bytes the gateway reads off the wire for one call, before any decompression. A *transient failure* is a connection-level failure or a 5xx response. A *gateway error* is the gateway's typed failure value. Each gateway error has exactly one *category*: `INVALID_REQUEST`, `SCHEME_REFUSED`, `ADDRESS_REFUSED`, `TOO_MANY_REDIRECTS`, `TIMEOUT`, `CONNECTION_FAILED`, `HTTP_STATUS` (which also carries the numeric status code), `RESPONSE_TOO_LARGE` or `UNEXPECTED` **[ASSUMPTION — category names and set; the Design phase fixes the type shape]**. The *policy values* are the per-attempt timeout, the raw-byte cap, the redirect limit, the retry count and the back-off schedule (Q2).

### R1: One gateway, three call types, one policy

As the developer building F5 and later F16, I want every outbound HTTP call in the app to go through one gateway that applies one set of policies to all three call types, so that the "no Pantry data leaves the device" claim and the SSRF controls can be audited by reading one component (ARCHITECTURE C7 Key Concerns).

**Acceptance Criteria:**

- GIVEN the gateway's public surface
  WHEN it is inspected
  THEN it exposes exactly three `suspend` operations (page fetch, image fetch and nutrition lookup). Each returns either the response body within the raw-byte cap or a gateway error, never `null` and never a thrown exception (R6), except coroutine cancellation (this requirement's last criterion).

- GIVEN a production `AppContainer`
  WHEN its gateway is read twice, and the three call types' effective policy values are inspected
  THEN the same gateway instance comes back both times, it holds one HTTP client instance, and all three call types use identical policy values.

- GIVEN every Kotlin source file under `app/src/main/java/`
  WHEN a source-inspection test reads them (resolving the repository root the way F2's `ReadOnlySurfaceTest` does, through `ie.pantry.testutil` `RepoPaths`)
  THEN only files in the gateway package import `okhttp3.`, and no main source file anywhere references `HttpURLConnection`, `URL.openConnection`, `URL.openStream` or `java.net.http`.

- GIVEN a gateway call in progress on a coroutine
  WHEN the calling coroutine is cancelled
  THEN the in-flight HTTP call is cancelled and the cancellation propagates to the caller as normal coroutine cancellation. It is not turned into a gateway error, and no further retry attempt starts **[ASSUMPTION — cancellation is the one exception allowed to leave the gateway; Kotlin structured concurrency requires it]**.

### R2: `http(s)`-only on every hop

As a Pantry user who taps "share" on a link from anywhere, I want the app to connect only to `http` or `https` targets, including every redirect it follows, so that a hostile page cannot pass its response to an app-invoking scheme (ARCHITECTURE C7 Key Concerns; R10).

**Acceptance Criteria:**

- GIVEN initial URLs with the schemes `intent://`, `content://`, `file://`, `javascript:`, `ftp://` and `data:`, each passed to the page fetch and to the image fetch
  WHEN each call is made
  THEN each returns a `SCHEME_REFUSED` gateway error and no connection is opened (a test server records zero requests).

- GIVEN a test server that answers the first request with a 302 whose `Location` is `intent://evil#Intent;end`, and a second server that redirects to `content://ie.pantry.provider/x`
  WHEN a page fetch and an image fetch follow each redirect
  THEN each call returns a `SCHEME_REFUSED` gateway error (not `HTTP_STATUS`), and no request is made to the refused target.

- GIVEN a redirect chain `http` → `https` → `http`, where every hop targets an allowed address
  WHEN a page fetch follows it within the redirect limit
  THEN the call succeeds. Both schemes are accepted on every hop, and downgrading from `https` to `http` is not refused (ARCHITECTURE C1: both schemes are accepted, and there is no certificate pinning).

- GIVEN a string that does not parse as an absolute `http(s)` URL (empty, whitespace, `https://`, `ht!tp://x`)
  WHEN the page fetch or image fetch is called with it
  THEN the call returns an `INVALID_REQUEST` gateway error and no connection is opened.

### R3: Resolved-address checks on both IP families

As a Pantry user, I want the app to refuse any connection whose resolved address is on my own device or local network, whether the URL points there directly or through a redirect chain, so that a shared link cannot make my phone probe my router or my other devices (PLAN F4; ARCHITECTURE C7 Key Concerns).

**Acceptance Criteria:**

- GIVEN, for each blocked range, one address inside it (`127.0.0.1`, `127.255.255.254`, `::1`, `169.254.169.254`, `fe80::1`, `10.0.0.1`, `172.16.0.1`, `172.31.255.255`, `192.168.1.1`, `fc00::1`, `fd12:3456::1`, `0.0.0.0`, `0.0.0.1`, `::`, `100.64.0.1`, `100.127.255.254`, `224.0.0.1`, `ff02::1`, `fec0::1`), and one address just outside each IPv4 range boundary — both sides for every range except `0.0.0.0/8`, which is the lowest possible IPv4 block and so has no lower neighbour (`126.255.255.255`/`128.0.0.0` for loopback, `169.253.255.255`/`169.255.0.0` for link-local, `9.255.255.255`/`11.0.0.0` for `10.0.0.0/8`, `172.15.255.255`/`172.32.0.0` for `172.16.0.0/12`, `192.167.255.255`/`192.169.0.0` for `192.168.0.0/16`, `100.63.255.255`/`100.128.0.0` for CGNAT, `223.255.255.255`/`240.0.0.0` for multicast) and the one upper side for `0.0.0.0/8` (`1.0.0.0`)
  WHEN the address policy is asked about each address
  THEN every address inside a blocked range is refused and every boundary address outside one is allowed. This is a pure JVM unit test.

- GIVEN IPv4-mapped IPv6 forms of blocked IPv4 addresses (`::ffff:127.0.0.1`, `::ffff:10.0.0.1`, `::ffff:169.254.169.254`)
  WHEN the address policy is asked about each
  THEN each is refused, because it is the same blocked IPv4 address written in IPv6 form **[ASSUMPTION — PLAN's "on both IP families" read as including mapped forms]**.

- GIVEN a direct URL whose host is a literal blocked address (`http://127.0.0.1:<port>/`, `http://[::1]:<port>/`), and a direct URL whose hostname a test resolver maps to a blocked address
  WHEN a page fetch, an image fetch and a nutrition lookup are made to each (the nutrition lookup through a test endpoint configured to resolve to a blocked address)
  THEN each returns an `ADDRESS_REFUSED` gateway error, no bytes are sent to that address, and the call is not retried.

- GIVEN a hostname that the test resolver maps to both a blocked and an allowed address
  WHEN a page fetch is made to it
  THEN the client never connects to the blocked address. Whether it connects only to the allowed address or refuses the call is a Design decision, but no test records a connection attempt to the blocked address.

- GIVEN a redirect chain whose first hop resolves to an allowed address and whose second hop (a `Location` header naming another host) resolves to a blocked IPv4 address, and the same chain ending on a blocked IPv6 address
  WHEN a page fetch and an image fetch follow each chain
  THEN each returns an `ADDRESS_REFUSED` gateway error, and no connection is made to the blocked address.

- GIVEN a test resolver that returns an allowed address for a hostname on its first lookup and a blocked address on every later lookup (DNS rebinding)
  WHEN a page fetch is made to that hostname
  THEN the address checked is the one the client actually connects to for that hop, not the result of a separate earlier lookup, and the connection to the blocked address is refused (RK1).

### R4: Bounded redirect following

As the developer, I want the gateway to follow at most a fixed number of redirects, so that a redirect loop or a long chain ends in a typed error rather than an unbounded series of requests (PLAN F4 Description, "redirect limits").

**Acceptance Criteria:**

- GIVEN a test server that redirects exactly the redirect limit's number of times and then returns 200
  WHEN a page fetch is made
  THEN the call succeeds, and every hop's connection target was validated under R2 and R3.

- GIVEN a test server that redirects one more time than the redirect limit allows, and a server that redirects to itself forever
  WHEN a page fetch, an image fetch and a nutrition lookup are each made to them
  THEN each returns a `TOO_MANY_REDIRECTS` gateway error, the server records exactly the redirect limit + 1 requests, and the call is not retried.

- GIVEN a 3xx response with no `Location` header, or with a `Location` that does not parse as a URL
  WHEN any call type receives it
  THEN the call returns a gateway error, never an exception: `HTTP_STATUS` carrying the 3xx code for a missing `Location`, and `INVALID_REQUEST` for a `Location` that does not parse as a URL (such as `http://[::bad`). A relative or protocol-relative `Location` (`/recipe/1`, `//host/x`) is resolved against the current hop's URL and then checked like any other hop. Only 301, 302, 303, 307 and 308 are redirects; any other 3xx is an `HTTP_STATUS` error. A `Location` that parses with a non-`http(s)` scheme is covered by R2 (`SCHEME_REFUSED`).

### R5: Transport-level raw-byte cap, identical across call types

As the developer building F5, I want every response body read off the wire to be capped at the same size for all three call types, so that a hostile server cannot exhaust memory through a page, an image or a nutrition response, and the image fetch is not a special case (ARCHITECTURE C7 Boundary).

**Acceptance Criteria:**

- GIVEN, for each of the three call types, a test server returning a 200 body of exactly the raw-byte cap, and a 200 body one byte over the cap
  WHEN each call is made
  THEN the at-cap body is returned intact with its byte count equal to the cap, and the over-cap body returns a `RESPONSE_TOO_LARGE` gateway error. The threshold is the same for all three call types.

- GIVEN an over-cap body served with a `Content-Length` that states its true size, and an over-cap body served chunked with no `Content-Length`
  WHEN each is fetched
  THEN each returns `RESPONSE_TOO_LARGE`. The gateway never reads more than the cap + 1 bytes of either body, and the declared `Content-Length` over the cap is refused before the body is read.

- GIVEN a response whose `Content-Length` declares a size under the cap while the server writes more body bytes than that, so the bytes it sends total more than the cap
  WHEN it is fetched
  THEN the call succeeds, with a body that is exactly the declared `Content-Length` number of bytes, no more than the cap + 1 body bytes are consumed off the wire, and the connection is closed after the call and never reused for a later request (asserted as in this requirement's last criterion). A compliant HTTP/1.1 client stops reading at the declared length, so it cannot see the extra bytes and cannot report `RESPONSE_TOO_LARGE` for this response.

- GIVEN a gzip-encoded response whose compressed size is under the cap
  WHEN it is fetched
  THEN the cap is measured on the compressed bytes read off the wire. Capping decompressed content as it streams is C1's job, and so F5's (ARCHITECTURE C7 Boundary, "Outside"). How the gateway hands compressed or encoded content to its caller is a Decision Point, below.

- GIVEN an over-cap response
  WHEN the call returns
  THEN the response body and its connection are closed. A test asserts this by checking that the test server sees the connection closed, or the client's connection pool shows no leaked connection.

### R6: Every failure is a distinct typed error, never a hang or an escaping exception

As a Pantry user importing a recipe, I want a slow, broken or unreachable site to give me a clear error within a bounded time rather than a frozen screen or a crash, so that I can try another URL or type the recipe in myself (SCOPE G8; ARCHITECTURE C7 Key Concerns).

**Acceptance Criteria:**

- GIVEN, for each call type, a test server that accepts the connection and never sends response headers, and one that sends headers and then stalls partway through the body
  WHEN each call is made
  THEN each returns a `TIMEOUT` gateway error. Each individual attempt ends within the per-attempt timeout plus a tolerance of 1 s. The whole call, including any retries (R7), ends within (retry count + 1) × per-attempt timeout + the sum of the back-off delays + 1 s. It never hangs.

- GIVEN, for each call type, test responses with the status codes 204, 304, 400, 403, 404, 410, 429, 500, 502 and 503 (no `Location` header on any of them)
  WHEN each call is made
  THEN 204 succeeds with an empty body **[ASSUMPTION — any 2xx is a success, returned with its body]**, and every non-2xx status returns an `HTTP_STATUS` gateway error that carries that numeric status code (5xx only after R7's retries are used up).

- GIVEN a timeout, a non-2xx response and an oversized response
  WHEN their gateway errors are compared
  THEN the three categories are distinct (`TIMEOUT`, `HTTP_STATUS`, `RESPONSE_TOO_LARGE`), so a caller can tell them apart with an exhaustive `when` and no string matching.

- GIVEN connection-level failures (connection refused, connection reset mid-response, an unresolvable host, a TLS handshake failure against a test server with an untrusted certificate) and an unexpected runtime exception raised from inside the HTTP client (injected through a test interceptor)
  WHEN each call type meets them
  THEN each call returns a gateway error (`CONNECTION_FAILED` for the connection-level failures and `UNEXPECTED` for the injected runtime exception), and no exception other than coroutine cancellation (R1) leaves the gateway.

### R7: Bounded retry with back-off for transient failures only

As a Pantry user on a patchy mobile connection, I want a brief network blip or a server's momentary 5xx to be retried a few times before I see an error, but never endlessly and never for errors that retrying cannot fix, so that imports succeed more often without the app getting stuck (PLAN F4).

**Acceptance Criteria:**

- GIVEN a test server that fails with a transient failure (a dropped connection, and separately a 503) on every request
  WHEN a page fetch, an image fetch and a nutrition lookup are each made
  THEN each call is attempted exactly retry count + 1 times, the delay between consecutive attempts follows the back-off schedule (asserted under virtual time, not wall-clock sleeps), and the call then returns a gateway error (`CONNECTION_FAILED` or `HTTP_STATUS` 503) rather than retrying forever.

- GIVEN a test server that fails transiently on the first attempt and returns 200 on the second
  WHEN a page fetch is made
  THEN the call succeeds with the second attempt's body, and exactly two requests are recorded.

- GIVEN non-retryable outcomes: a 4xx response (400, 404, 429), `SCHEME_REFUSED`, `ADDRESS_REFUSED`, `TOO_MANY_REDIRECTS`, `RESPONSE_TOO_LARGE` and `INVALID_REQUEST`
  WHEN each call type meets them
  THEN each gateway error is returned right after the first attempt, with no retry and no back-off delay (the test server records exactly one request, or none for errors raised before any connection; `TOO_MANY_REDIRECTS` is the exception, with the redirect limit + 1 requests of R4 AC2, all within that single attempt).

- GIVEN a per-attempt timeout
  WHEN it happens
  THEN it is treated as a connection-level transient failure and retried under the same bound **[ASSUMPTION — Q3]**. The per-attempt timeout bounds each attempt, not the retried call as a whole (PLAN F4).

### R8: Anonymous request construction

As a Pantry user, I want the app's requests to carry nothing about me or my data beyond the address being fetched or the one ingredient being looked up, so that no Pantry data leaves my device (ARCHITECTURE Data Flow, second invariant; SCOPE G6).

**Acceptance Criteria:**

- GIVEN a test server recording every request, and one call of each call type (page fetch of a user-supplied URL, image fetch of a page-supplied image URL, nutrition lookup of the term `crème fraîche & salt/pepper?#x=1`)
  WHEN the recorded requests are inspected
  THEN every request is a `GET` with no body; none carries an `Authorization`, `Proxy-Authorization` or `Cookie` header, an API key, or any header or parameter holding Pantry-owned data; the page and image fetches' request-target and `Host` equal the supplied URL as the URL parser normalises it, with any fragment and userinfo removed; and the nutrition lookup's query holds the ingredient term as a single URL-encoded parameter whose decoded value equals the term exactly. The term's `&`, `#`, `/`, `?` and `=` characters add no further parameter, fragment or path segment, and every other query parameter is a compile-time constant.

- GIVEN the nutrition lookup's source code
  WHEN it is inspected
  THEN its URL is built with a URL-builder API (OkHttp `HttpUrl.Builder` or equivalent) and never by string concatenation or interpolation of the term (ARCHITECTURE C7 Boundary).

- GIVEN a server that sets a cookie on its first response and then redirects, and a second call to the same server
  WHEN both calls are made
  THEN no later request carries that cookie. The gateway keeps no cookie jar and no HTTP disk cache.

- GIVEN a user-supplied URL with userinfo (`https://user:secret@host/path`)
  WHEN it is page-fetched
  THEN the request carries no `Authorization` header derived from it **[ASSUMPTION — Q8: the URL is passed through as-is, never converted into credentials]**.

- GIVEN an empty or whitespace-only ingredient term
  WHEN the nutrition lookup is called
  THEN it returns an `INVALID_REQUEST` gateway error and no request is sent.

### R9: No user or imported content in any error

As a Pantry user, I want the URLs I import and the pages I fetch never to appear in an error message or crash report, so that Android vitals never carries my content (ARCHITECTURE Data Flow, third invariant; PLAN CFC-4).

**Acceptance Criteria:**

- GIVEN every failure path in R2 to R8, driven with content-bearing fixtures: `Sentinels.URL` (and a URL whose host, path and query each contain the `SENTINEL` marker) as the requested URL, a `Location` header containing the marker, a response body and a response reason phrase containing it, a nutrition term containing `Sentinels.INGREDIENT`, and the unresolvable sentinel host (an exception whose own message names the host)
  WHEN each gateway error is produced
  THEN no typed error produced here embeds the requested URL, response body, or any fragment of fetched content in its message. Every gateway error's `toString()` and any throwable it exposes pass `assertNoSentinel()`, and a third-party library exception is re-wrapped before it can propagate unchanged. [CFC-4]

- GIVEN the gateway's sources and every error it can produce
  WHEN they are inspected and exercised
  THEN no exception or error message this feature constructs contains raw user-supplied or imported content, and any third-party exception carrying such content is re-wrapped into a typed error before propagating. [CFC-4]

- GIVEN a gateway error
  WHEN its fields are inspected
  THEN it holds only the gateway's own vocabulary: a category, the call type, an optional numeric status code, an optional attempt count and an optional exception class simple-name. It holds no URL, host, resolved address, header value, body fragment or term. No third-party exception is kept as a `cause` or a suppressed exception. Stack frames may be copied, as F1's `persistenceFailure` does **[ASSUMPTION — mirrors F1's `PersistenceException` pattern]**.

- GIVEN the gateway's main sources
  WHEN they are inspected
  THEN no `require`, `check`, `error(...)` or thrown exception has a message that interpolates anything other than an enum name, an integer or a class simple name, and the gateway writes no log line holding a URL, host, header, body or term (no OkHttp logging interceptor is installed).

### R10: Network permission and the F1 no-network baseline

As the developer, I want the app to declare exactly the one permission the gateway needs, and F1's no-network tests changed so that they keep proving what they can still prove, so that the full suite stays green and the permission set stays minimal (PLAN F1; F1 spec R1, R7).

**Acceptance Criteria:**

- GIVEN the merged release and debug manifests
  WHEN they are inspected
  THEN `android.permission.INTERNET` is the only `android.permission.*` entry requested, `android:allowBackup="false"` is unchanged, and the merged release manifest has the same components and the same exported components as F1's baseline (F2 spec, Network Exposure Triage (3)). OkHttp adds no activity, service, receiver or provider. The application element also sets `android:usesCleartextTraffic="true"`, because `targetSdk 35` blocks `http` by default and R2 accepts `http` on every hop; a merged-manifest assertion (Robolectric) checks this, so JVM tests cannot pass while `http` fails on a device. Fetched content is untrusted whatever the transport (ARCHITECTURE C1), so this adds no new trust.

- GIVEN F1's `ManifestPolicyTest` and `RestartDurabilityTest`
  WHEN the full unit-test suite runs after F4
  THEN both pass. `ManifestPolicyTest` asserts that `INTERNET` is the only `android.permission.*` requested, and `RestartDurabilityTest` still proves that reopening the database makes no network attempt (its `ProxySelector` and no-active-network guards), with its "OkHttp is not on the classpath" assertion removed or replaced by an equivalent no-socket guard **[ASSUMPTION — Q1]**.

## Project Structure

```
pantry/                                               (repository root)
├── gradle/libs.versions.toml                         MODIFIED: OkHttp + MockWebServer aliases
└── app/
    ├── build.gradle.kts                              MODIFIED: implementation/testImplementation lines
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                   MODIFIED: INTERNET permission (R10)
        │   └── java/ie/pantry/
        │       ├── data/gateway/                     NEW: the gateway (C7)
        │       └── di/AppContainer.kt                MODIFIED: one gateway instance
        └── test/java/ie/pantry/
            ├── data/gateway/                         NEW: gateway tests (plain JVM + MockWebServer)
            ├── data/db/RestartDurabilityTest.kt      MODIFIED (Q1)
            ├── di/AppContainerTest.kt                MODIFIED: single-instance assertion (R1)
            └── ManifestPolicyTest.kt                 MODIFIED (Q1)
```

**[ASSUMPTION — the Design phase fixes the package and file split.]** The package is `ie.pantry.data.gateway`, beside the existing `ie.pantry.data.db`, `ie.pantry.data.reference` and `ie.pantry.data.thumbnail`, because ARCHITECTURE's System Overview puts network-sourced data in the data layer. No HTTP client exists in the project today: `gradle/libs.versions.toml` and `app/build.gradle.kts` declare no OkHttp, Ktor or Retrofit, and the manifest requests no permission.

### New Files

- `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt`: the three `suspend` call types over one shared client (R1, R5 to R8).
- `app/src/main/java/ie/pantry/data/gateway/GatewayResult.kt`: the success value and the content-free gateway error with its categories (R6, R9).
- `app/src/main/java/ie/pantry/data/gateway/ConnectionTargetPolicy.kt`: the scheme check and the blocked-address ranges, pure Kotlin with no OkHttp dependency, so it can be unit-tested on its own (R2, R3).
- `app/src/main/java/ie/pantry/data/gateway/GatewayPolicy.kt`: the policy values as constants (Q2).
- `app/src/test/java/ie/pantry/data/gateway/ConnectionTargetPolicyTest.kt`: R2's scheme table and R3's address tables.
- `app/src/test/java/ie/pantry/data/gateway/GatewayTargetValidationTest.kt`: R2, R3 and R4 against direct URLs and redirect chains.
- `app/src/test/java/ie/pantry/data/gateway/GatewayFailureMappingTest.kt`: R5 and R6.
- `app/src/test/java/ie/pantry/data/gateway/GatewayRetryTest.kt`: R7, under virtual time.
- `app/src/test/java/ie/pantry/data/gateway/GatewayRequestInspectionTest.kt`: R8.
- `app/src/test/java/ie/pantry/data/gateway/GatewayErrorHygieneTest.kt`: R9, using the existing `ie.pantry.testutil.Sentinels` and `assertNoSentinel()`.
- `app/src/test/java/ie/pantry/data/gateway/SingleCallSiteTest.kt`: R1's source inspection and R9's source checks.

### Modified Files

- `gradle/libs.versions.toml`: an `okhttp` version and library alias, plus the matching MockWebServer test library.
- `app/build.gradle.kts`: `implementation(libs.okhttp)` and `testImplementation(...)` for MockWebServer. Both come through catalog aliases, which F1 R1 requires.
- `app/src/main/AndroidManifest.xml`: `<uses-permission android:name="android.permission.INTERNET" />` and `android:usesCleartextTraffic="true"` on the application element (R10).
- `app/src/main/java/ie/pantry/di/AppContainer.kt`: builds and exposes the one gateway instance (R1).
- `app/src/test/java/ie/pantry/di/AppContainerTest.kt`: asserts the single gateway instance (R1).
- `app/src/test/java/ie/pantry/ManifestPolicyTest.kt` and `app/src/test/java/ie/pantry/data/db/RestartDurabilityTest.kt`: R10 (Q1).

No change to `PantryDatabase`, its entities, `app/schemas/`, `app/src/main/assets/` or any F2 or F3 source.

## Commands

```bash
# F4's own tests, plus the F1 tests it modifies
./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.*' --tests 'ie.pantry.ManifestPolicyTest' --tests 'ie.pantry.data.db.RestartDurabilityTest' --tests 'ie.pantry.di.AppContainerTest'

# Full suite (no F1/F2/F3 regressions)
./gradlew :app:testDebugUnitTest

# Static analysis
./gradlew lintDebug

# Merged release manifest: exactly one android.permission (INTERNET)
./gradlew :app:processReleaseMainManifest && grep -c 'uses-permission android:name="android.permission' app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml

# Quick single-call-site cross-check (R1 is asserted by SingleCallSiteTest)
grep -rln 'import okhttp3\.' app/src/main/java | grep -v '/data/gateway/' || echo "clean"
```

## Boundaries

### Always Do

- Validate the scheme and the resolved address on every hop, using the address the client actually connects to (R2, R3).
- Return a gateway error for every failure. Use a sealed type or a category enum with exhaustive `when`, with no nullable failure and no `kotlin.Result`, in the style of F2's `LoadResult` and F3's typed outcomes.
- Build every gateway error from the gateway's own vocabulary: categories, call type, status code, counts and exception class simple-names only. Re-wrap every third-party exception without chaining it, following F1's `PersistenceException` pattern (R9).
- Let coroutine cancellation propagate. Never catch and convert `CancellationException` (R1).
- Build the nutrition lookup's URL with `HttpUrl.Builder` or an equivalent, adding the term as one query parameter (R8).
- Keep `ConnectionTargetPolicy` pure Kotlin with no Android import, and keep gateway tests plain JVM (MockWebServer, `kotlinx-coroutines-test` virtual time) with no Robolectric, except where F1's manifest test already uses it.
- Reference both new dependencies through `gradle/libs.versions.toml` aliases (F1 R1).
- Write tests in the F1 to F3 style: JUnit 4, `kotlin.test` assertions and backtick-quoted names.

### Ask First

- The edits to F1's `ManifestPolicyTest` and `RestartDurabilityTest` (Q1, resolved: F1's spec text itself is not reopened).
- Any policy value different from the values Q2 settles.
- Blocking any address range beyond what Q4 already settled (loopback, link-local, private, unspecified, CGNAT, multicast, deprecated site-local).
- Adding any dependency beyond OkHttp and its MockWebServer test artifact, including an OkHttp logging interceptor, Retrofit or a JSON library.
- Adding an HTTP disk cache, a cookie jar, a custom `User-Agent` that identifies the user or device, or any request header beyond what OkHttp sends by default.
- Adding any Android permission other than `INTERNET` (for example `ACCESS_NETWORK_STATE`), or a `networkSecurityConfig`, beyond `usesCleartextTraffic="true"`.
- Exposing any gateway operation other than the three call types.

### Never Do

- Never send a credential, an API key, a cookie or any Pantry-owned data in a request (R8).
- Never concatenate or interpolate the ingredient term into a URL string (R8).
- Never put a URL, host, resolved address, header value, body fragment or ingredient term in an exception message, a gateway error or a log line, and never chain a third-party exception as a cause (R9).
- Never retry a 4xx, a refused scheme, a refused address, a redirect overflow, an oversized response or an invalid request (R7).
- Never let a test-only bypass of the loopback block (needed to reach MockWebServer on `127.0.0.1`) be reachable from `AppContainer.production` (RK3).
- Never parse page structure, cap decompressed content as it streams, choose an image's source URL, decode or validate image bytes, or interpret a nutrition payload. Those belong to C1 (F5) and C6 (F9, F16) (ARCHITECTURE C7 Boundary, "Outside").
- Never route the retailer walkthrough through the gateway. C8 hands off to Custom Tabs and is not an HTTP client (ARCHITECTURE C7, C8).
- Never add certificate pinning, and never refuse `http` in favour of `https` for the page or image fetch (ARCHITECTURE C1 Key Concerns).
- Never tag an F4 criterion with CFC-1, CFC-2 or CFC-3. PLAN's participant lists for those contracts do not include F4, so F4's only tag is CFC-4 (R9).

### Network Exposure Triage

**Branch (a) — no new surface.** Introduces no new domain, route or port; it adds no new inbound surface. Checked: (1) inbound listeners: F4 creates no `ServerSocket`, bound port or listener in shipped code. MockWebServer binds a loopback port only inside JVM unit tests, and it is a `testImplementation` dependency, so it is not in the APK. (2) Domains and DNS: no domain, DNS record, hostname or public endpoint is registered or routed. (3) Exported components: the merged release manifest keeps F1's baseline components and exported components unchanged, and OkHttp contributes none (R10 AC1). (4) Permissions: the changes are `android.permission.INTERNET` and `android:usesCleartextTraffic="true"` (R10 AC1; needed so `http` links work on a device), which let the app originate outbound connections and exposes nothing inbound. (5) Outbound traffic: the app only opens outbound `http(s)` connections, to caller-supplied or page-supplied URLs and to the one constant nutrition endpoint, over the internet access the device already has. Each connection is limited by R2 and R3 (so no local or private network is reachable) and by R8 (anonymous requests).

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: F1's approved spec states that the release manifest declares no `android.permission.INTERNET` (F1 R1, manifest criterion). F1's `RestartDurabilityTest` asserts `Class.forName("okhttp3.OkHttpClient")` throws, and `ManifestPolicyTest` asserts no `android.permission.*` is requested. F4 has to break all three. **[ASSUMPTION]** F4 edits both test files as described in R10's second criterion. F1's spec text is left as a record of F1's own scope and is not re-opened, because F1's own Network Exposure Triage already says that C7 is introduced by F4, not by F1. Confirm this, or route an F1 amendment through its re-approval flow.
  - **Resolution:** Confirmed as written by the developer (2026-09-29). F4 edits F1's two test files only; F1's spec.md is not reopened.
- [x] Q2: What are the policy values? **[ASSUMPTION]** Per-attempt timeout: 10 s to connect and 15 s to read, with a 20 s ceiling on each attempt. Raw-byte cap: 5 MiB, the same for every call type. Redirect limit: 5 hops. Retry count: 2 (3 attempts in total). Back-off: 500 ms, then 1 s.
  - **Resolution:** Confirmed as written by the developer (2026-09-29).
- [x] Q3: Which failures count as connection-level and so are retried? **[ASSUMPTION]** Connect and read timeouts, DNS resolution failures, connection refused or reset, and TLS handshake failures are retried. A 429 is a 4xx and is not retried, and `Retry-After` is not honoured.
  - **Resolution:** Confirmed as written by the developer (2026-09-29).
- [x] Q4: Should ranges beyond PLAN F4's list also be blocked: `0.0.0.0/8` and `::` (unspecified addresses, which reach the device itself on some stacks), `100.64.0.0/10` (CGNAT), multicast, and deprecated site-local `fec0::/10`? 
  - **Resolution:** Developer elected to block these too (2026-09-29), overriding the [ASSUMPTION] default: `0.0.0.0/8`, `::`, CGNAT `100.64.0.0/10`, multicast ranges, and deprecated site-local `fec0::/10` are all refused alongside PLAN's loopback/link-local/private list and its IPv4-mapped IPv6 forms.
- [x] Q5: What exactly is the nutrition endpoint? ARCHITECTURE names Open Food Facts (anonymous REST, HTTPS). The base URL, path and constant query parameters are not specified. **[ASSUMPTION]** One constant `https` base URL with a fixed search path and constant parameters, confirmed against Open Food Facts' current API documentation in the Design phase. F4 only sends the request; payload shape and field selection belong to F16 and C6.
  - **Resolution:** Developer confirmed Open Food Facts (2026-09-29) — free, open, no signup or API key required. The exact base URL/path/fixed query parameters are confirmed against Open Food Facts' current API documentation during the Design phase, as the [ASSUMPTION] already proposed.
- [x] Q6: How are device-level HTTP proxies handled? When a proxy is set, the proxy resolves the target hostname, so R3's address check cannot see the target's address. **[ASSUMPTION]** The client is built with `Proxy.NO_PROXY`, so every connection is direct and every address is checked (RK2).
  - **Resolution:** Confirmed as written by the developer (2026-09-29).
- [x] Q7: PLAN CFC-4's Enforcement says "F4 defines the typed-error surface and its re-wrapping rule that the rest re-use." Does this require a shared, cross-component re-wrap helper, or does it mean the gateway error that F5 and F16 receive and pass on? **[ASSUMPTION]** The latter. F4 ships the gateway error and a gateway-internal re-wrap function. F1 and F3 already have their own content-free failure types, and F4 does not refactor them.
  - **Resolution:** Confirmed as written by the developer (2026-09-29).
- [x] Q8: Should a user-supplied URL with userinfo (`user:secret@`) be refused, or passed through as-is? **[ASSUMPTION]** Passed through as-is, which is ARCHITECTURE C7's wording, with no `Authorization` header derived from it (R8). F5's URL validation may still reject such URLs before they reach the gateway.
  - **Resolution:** Confirmed as written by the developer (2026-09-29).

## Decision Points

- OkHttp major version (4.12.x or 5.x) and the matching MockWebServer artifact (`mockwebserver` or `mockwebserver3`). This includes whether 5.x adds any manifest metadata that R10's component check has to allow for.
- How redirects are followed: by the gateway itself (`followRedirects(false)` plus a loop that validates each hop), or by OkHttp with a network interceptor that validates each hop. The choice has to satisfy R2's criterion that a non-`http(s)` `Location` gives `SCHEME_REFUSED` rather than `HTTP_STATUS`.
- Where the address check runs: a custom `Dns` that filters resolved addresses, an `EventListener.connectStart` check, or a custom `SocketFactory`. R3's rebinding criterion requires that the checked address is the one connected to.
- The test seam that lets MockWebServer on loopback be reached in non-SSRF tests: an injected `Dns` or address policy passed only through a non-production constructor. RK3 requires that `AppContainer.production` cannot select it.
- The shape of a successful result: a fully buffered `ByteArray` of at most the cap, or a capped stream. Also whether OkHttp's transparent gzip is disabled, so that the gateway returns the raw encoded bytes plus the `Content-Encoding` for C1 to decompress under its own streaming cap, or left on with the raw cap counted at the network layer. The choice has to line up with F5's decompressed-content criterion.
- How back-off delays are scheduled (coroutine `delay`, so they are testable under `kotlinx-coroutines-test` virtual time) and which dispatcher blocking I/O runs on (an injectable `CoroutineDispatcher`, `Dispatchers.IO` in production).
- The shape of the gateway error: a sealed interface with one subtype per category, or one data class with a category enum like F2's `LoadFailure`. Either way it must support R9 AC1's `assertNoSentinel()` call (currently a `Throwable`-only test helper), so the choice needs either a `Throwable`-shaped gateway error or a non-`Throwable` overload of the helper.

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| RK1 | DNS rebinding or check-then-connect gap: the address checked comes from a different lookup than the one connected to, so a hostile DNS server passes the check and then points at `192.168.1.1` | Med | High | R3's rebinding criterion; the address-check Decision Point requires checking the addresses the client actually connects to |
| RK2 | A device HTTP proxy (set by the user or an MDM profile) makes the proxy resolve the target, so the address check is never applied to the real target | Low | High | Q6: direct connections only (`Proxy.NO_PROXY`); a test asserts the production client's proxy setting |
| RK3 | The test-only loopback bypass needed to reach MockWebServer leaks into production and silently disables the SSRF block | Med | High | Never Do boundary; the bypass is reachable only through a non-production constructor; a test asserts that the production container refuses `http://127.0.0.1/` |
| RK4 | OkHttp's transparent gzip makes the "raw" cap measure decompressed bytes, or hands C1 compressed bytes it cannot read, so the C7/C1 cap split does not match F5 | Med | Med | R5's gzip criterion pins where the cap is measured; the result-shape Decision Point must be settled with F5's decompressed-content criterion in view |
| RK5 | A third-party exception message (such as `UnknownHostException` naming the host, an SSL exception naming the certificate subject, or OkHttp's `ProtocolException` text) reaches a caller or a crash report | High | High | R9: errors carry only the gateway's vocabulary, causes are never chained, and sentinel tests cover an unresolvable sentinel host and a sentinel `Location` and reason phrase |
| RK6 | Retries on an offline device make the user wait roughly (retry count + 1) × timeout before seeing an error, which reads as a hang | Med | Med | R6's whole-call bound; under Q2's settled values, R6 AC1's own formula gives a worst case of (2+1) × 20s + 1.5s + 1s = 62.5s, close to but slightly over a minute; Q3 keeps non-transient failures from being retried |
| RK7 | `CancellationException` is caught by a blanket `catch (e: Exception)` and turned into `UNEXPECTED`, which breaks structured concurrency and keeps retrying after the user leaves the screen | Med | Med | R1's cancellation criterion; the Always Do boundary |
| RK8 | Alternative address forms (IPv4-mapped IPv6, or decimal or octal IPv4 literals such as `http://2130706433/`) get past a check that compares strings instead of parsed `InetAddress` values | Med | High | R3 checks parsed resolved addresses and tests the mapped forms; the check runs on the connected `InetAddress`, not on the URL text |
| RK9 | F1's approved spec text contradicts the shipped manifest after F4, and a later consistency check or panel flags the drift | High | Low | Q1 records the decision; R10 keeps F1's tests meaningful rather than deleting them |

## Success Criteria

- [ ] One gateway instance, holding one HTTP client, serves all three call types with identical policy values, and no other main source file makes an HTTP call.
- [ ] Every hop is refused unless it is `http(s)`, including redirects to `intent://` and `content://`, which give `SCHEME_REFUSED`.
- [ ] Every connection to a loopback, link-local, private, unspecified, CGNAT, multicast or deprecated site-local address on IPv4 or IPv6 (including IPv4-mapped forms) is refused with `ADDRESS_REFUSED`, for both direct URLs and redirect chains, and for the address actually connected to.
- [ ] Redirect chains longer than the redirect limit give `TOO_MANY_REDIRECTS`.
- [ ] A body over the raw-byte cap gives `RESPONSE_TOO_LARGE` at the same threshold for all three call types, whether its `Content-Length` states its true size or it is sent chunked; a body whose `Content-Length` falsely declares less than the cap instead succeeds with exactly the declared number of bytes, with no more than the cap + 1 body bytes consumed and the connection closed, never reused.
- [ ] Timeout, non-2xx and oversized responses give distinct gateway errors within the stated time bound, and no exception other than coroutine cancellation leaves the gateway.
- [ ] Transient failures are retried exactly the retry count with the back-off schedule, and non-retryable failures are not retried.
- [ ] Recorded requests carry no credential, cookie, API key or Pantry-owned data, and the nutrition term is a single URL-encoded parameter built with a URL builder.
- [ ] No gateway error or exception contains a URL, host, body fragment or term, as checked by sentinel tests on every failure path.
- [ ] `INTERNET` is the only Android permission requested, and F1's manifest and restart-durability tests pass in their Q1 form.
- [ ] All tests pass.
- [ ] No regressions in existing functionality: the full `./gradlew :app:testDebugUnitTest` suite for F1, F2 and F3 stays green.

## Panel Review

<!-- Populated by the skill across panel-review passes. archive_pass.py manages
     Trajectory and Sealed dispositions automatically; the synthesizer populates
     Latest pass detail per pass.

     Disposition vocabulary: Addressed / Deferred → <TARGET.md> / Sealed /
     Accepted as risk / User input needed / Halt and re-scope. Sealed and
     Accepted as risk must include "Defense: <reason>" in Notes. Severity tags
     in Latest pass detail are bracketed: [HIGH] / [MED] / [LOW], optionally
     [REGRESSION].

     See SKILL.md "Panel Review section format" for the normative spec. -->

### Trajectory

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes                                       |
|------|------------|-------|-------------|-----------|----------|--------|---------------------------------------------|
| 1    | 2026-09-30 | 1     | 0           | 4         | 4        | 2      | —                                           |
| 2    | 2026-09-30 | 1     | 0           | 4         | 2        | 1      | —                                           |
| 3    | 2026-09-30 | 0     | 0           | 0         | 0        | 6      | converged (0 HIGH)                          |
| 4    | 2026-09-30 | 0     | 0           | 0         | 0        | 4      | converged (0 HIGH); upstream-panel dd3e6425 |
| 5    | 2026-10-04 | 2     | 0           | 8         | 14       | 1      | upstream-panel 67b211d0                     |

### Sealed dispositions

- `[SEAL-01]` **The already-approved ARCHITECTURE C1 https→http downgrade…** (pass 1, accepted-as-risk) — Defense: this is an ARCHITECTURE-level decision F4 inherits, not something F4 introduces or can change; ARCHITECTURE C1's own Key Concerns already accepts no cert pinning and both schemes.
- `[SEAL-02]` **The "shape of a successful result" Decision Point is…** (pass 1, accepted-as-risk) — Defense: this coupling is already explicitly documented in both the Decision Point itself and RK4's mitigation; there is nothing new to defer, and no sibling-spec target (F5 hasn't been drafted) is a valid deferral destination.
- `[SEAL-03]` **RK6's reworded mitigation figure (62.5s) was independently…** (pass 2, user-directed) — Defense: independently re-verified twice now (pass 1 synthesizer, pass 2 pragmatist) against R6 AC1's stated formula and Q2's settled values; the arithmetic is settled.
- `[SEAL-04]` **R9 AC1's `assertNoSentinel()` call and its `Throwable`-only…** (pass 3, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) both conjuncts are already independently testable as written, and the exact mechanism is properly a Design-phase decision per the existing Decision Points bullet — not worth a fourth pass to pre-resolve now.
- `[SEAL-05]` **R9 AC1's "any throwable it exposes" clause isn't…** (pass 3, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) the Decision Points section sits immediately below the requirements in the same document; this is a convenience cross-reference, not a missing commitment.
- `[SEAL-06]` **R7 states or tests retry behaviour for every gateway-error…** (pass 3, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) Terms' closed "transient failure" definition (connection-level failure or 5xx response) already excludes `UNEXPECTED` by construction — it's R6 AC4's category for an injected runtime exception, neither a connection-level failure nor an HTTP status — so it's non-retryable by the same closed-definition logic already governing every other category. Design/Tasks can add the explicit fixture without a spec change.
- `[SEAL-07]` **Q2's "Redirect limit: 5 hops" conflicts with the Terms…** (pass 3, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) R4 AC1/AC2's concrete fixtures are unambiguous and are what a developer actually implements against; Q2 is a descriptive shorthand summary, not itself an acceptance criterion, and doesn't override R4's governing text.
- `[SEAL-08]` **R3 AC1's fixture table (~33 addresses in one GIVEN) reads…** (pass 3, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) how to organize a large fixture table across test methods is a Design/Tasks-phase test-structure decision, not a spec-level correctness question — the spec states what must be tested, not how the test file is organized.
- `[SEAL-09]` **The Objective's second sentence is dense, hurting…** (pass 3, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) purely a readability preference on an already-sanctioned thin PLAN-driven Objective; no commitment is unclear.
- `[SEAL-10]` **R5's new falsely-low-Content-Length bullet requires the…** (pass 4, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) the spec states the observable requirement (closed, never reused); the detection mechanism is an implementation/Design-phase concern, same treatment as R5's other mechanism-level questions already left to Design (e.g. "where the address check runs").
- `[SEAL-11]` **"no more than the cap + 1 body bytes are consumed" is…** (pass 4, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) the stated bound is not false for this fixture (a declared-under-cap body is always ≤ cap < cap+1), just looser than the tightest possible bound; the same sentence already states the exact expectation via "exactly the declared Content-Length number of bytes," which is the operative, testable claim.
- `[SEAL-12]` **The new bullet doesn't restate "for each of the three call…** (pass 4, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) R5's own framing (its Objective sentence and every other AC) already establishes per-call-type universality as the section's standing convention; the new bullet inherits it by being under the same requirement, same as AC1's sibling fixtures don't each re-state it either.
- `[SEAL-13]` **"(asserted as in this requirement's last criterion)" is a…** (pass 4, accepted-as-risk) — Defense: (synthesizer-judged, not user-confirmed) the referenced criterion's check (test server sees the connection closed, or the pool shows no leaked connection) is a generic connection-state assertion with no dependency on why the call ended, so it transfers directly; tightening the cross-reference wording is a copy-edit, not a substance gap.
- `[SEAL-14]` **CONNECTION_FAILED conflates offline, dead site, TLS and…** (pass 5, user-directed) — Defense: user-directed (2026-10-04): keep one coarse category; a content-free sub-reason is an additive change F5 can request later.

### Deferred dispositions

- `[DEF-01]` **R6/R7 never state whether the retry budget applies once per…** → 02_design.md (pass 1) — Routed because: this is an architectural question (how the redirect-following loop and the retry loop compose) that needs the actual mechanism design, not a one-line spec reword.
- `[DEF-02]` **A fully offline device pays the full retry budget (~62s)…** → 02_design.md (pass 1) — Routed because: correct as specified, but a pre-flight connectivity check is an implementation-level optimization for Design to consider, not a change to R7's acceptance criteria.
- `[DEF-03]` **RK8's named attack vector (non-dotted IPv4 literal, e.g.…** → 02_design.md (pass 1) — Routed because: whether OkHttp's `HttpUrl` parser even accepts a non-dotted host before DNS resolution is implementation-specific and I can't verify it with confidence right now (Self-Check b) — writing a concrete AC fixture without that answer risks introducing a new inaccuracy. Design should pin the parser behaviour down and add the test then.
- `[DEF-04]` **R9 AC1's exhaustive sentinel-testing across R2-R8 is a…** → 03_tasks.md (pass 1) — Routed because: this is a task-sizing/scoping question, not a spec correctness defect.
- `[DEF-05]` **R3 AC1 scopes boundary-pair fixtures to IPv4 only; the six…** → 02_design.md (pass 2) — Routed because: IPv6 CIDR boundary arithmetic is more failure-prone to get right without tooling than the IPv4 case I just got wrong twice over this pass — Design should pin the exact IPv6 boundary fixtures down with the actual address-parsing library in view.
- `[DEF-06]` **R9 AC4's static-inspection check can only catch message…** → 02_design.md (pass 2) — Routed because: this is a technique-level blind spot in how the check is implemented, not a spec-text gap — Design should note it when designing the actual source-inspection test.
- `[DEF-07]` **RK2 and RK3 SSRF-control tests have no AC (NO_PROXY,…** → 02_design.md (pass 5) — Routed because: Design AD8 and AD10 already specify AppContainerTest and the no-proxy client.
- `[DEF-08]` **Test-only TLS trust seam not named as a hazard** → 02_design.md (pass 5) — Routed because: Design AD8 owns the seam and its guards.
- `[DEF-09]` **R1 AC3 denylist cannot back the single-call-site claim** → 02_design.md (pass 5) — Routed because: Design SingleCallSiteTest defines the exact check.
- `[DEF-10]` **R6 never-hangs bound untestable for blocking DNS** → 02_design.md (pass 5) — Routed because: Design AD7 bridge resumes on caller cancellation; fixture detail belongs to design.
- `[DEF-11]` **R3 AC6 DNS rebinding expected outcome and assertion unstated** → 02_design.md (pass 5) — Routed because: Design AD5 states single lookup per hop and the socket-level check.
- `[DEF-12]` **R5 AC2/AC3 wire-level bound unobservable with buffered reads** → 02_design.md (pass 5) — Routed because: Design AD9 and DR4 pin the measurement point.
- `[DEF-13]` **Raw-byte cap scope across redirect hops and retries unstated** → 02_design.md (pass 5) — Routed because: Design AD9 caps per response; add the fixture in tasks.
- `[DEF-14]` **R10 AC2 ProxySelector guard replacement undefined** → 02_design.md (pass 5) — Routed because: Design AD14 defines the Q1-form tests.
- `[DEF-15]` **Pasted-URL whitespace and scheme-less input unspecified** → 02_design.md (pass 5) — Routed because: Design AD2/AD11 state no trimming; F5 owns input normalisation.
- `[DEF-16]` **No user-facing guidance for ADDRESS_REFUSED on self-hosted…** → 02_design.md (pass 5) — Routed because: Wording is F5's; design Integration Points names the consumer rule.
- `[DEF-17]` **Per-attempt timeout vs Q2 three values and tolerance…** → 02_design.md (pass 5) — Routed because: Design AD6 fixes the formula at 62.5 s.
- `[DEF-18]` **R3 AC3 ::1 fixture needs an IPv6 loopback listener** → 03_tasks.md (pass 5) — Routed because: Add an assume/skip rule in the test task.
- `[DEF-19]` **62.5 s offline wait has no cancel affordance requirement…** → 02_design.md (pass 5) — Routed because: Already routed as DEF-02 and SEAL-03; F5's spec picks it up.
- `[DEF-20]` **Default User-Agent sent to Open Food Facts** → 02_design.md (pass 5) — Routed because: Resolved under design Q3.

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `8e6111bc22dc4b02`
- **Hash basis:** v2