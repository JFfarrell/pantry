# Tasks: External Data Gateway

**Spec:** `specs/F4-external-data-gateway/01_spec.md`
**Design:** `specs/F4-external-data-gateway/02_design.md`

## Summary

| Task | Description | Requirement | Dependencies | Parallel | Status |
|------|-------------|-------------|--------------|----------|--------|
| T1 | Build setup: verify DR1 artefacts are available offline, pre-F4 baselines, OkHttp/MockWebServer aliases, `RestartDurabilityTest` source guard | R10, R1 | None | No | Not Started |
| T2 | `ConnectionTargetPolicy`: scheme verdicts, blocked ranges, exemptions | R2, R3 | T1 | Yes (with T3, T4, T7) | Not Started |
| T3 | Result, error and policy model: the CFC-4 typed-error surface and re-wrapping rule [CFC-4] | R9, R6, R1 | T1 | Yes (with T2, T4) | Not Started |
| T4 | `NutritionQuery` URL builder and its unit and source checks | R8 | T1 | Yes (with T2, T3, T5, T7) | Not Started |
| T5 | Socket guard and filtering Dns in `GatewayTransport.kt` | R3 | T1, T2, T3 | Yes (with T4, T7) | Not Started |
| T6 | `GatewaySeams`, raw-byte cap interceptor and `buildGatewayClient` | R5, R1, R8 | T4, T5 | Yes (with T7) | Not Started |
| T7 | `TestGateways` helper (without `against()`) and TLS fixture | R6 | T1, T3 | Yes (with T2, T4, T5, T6) | Not Started |
| T8 | `ExternalDataGateway` core: public surface, validation order, hop bridge, single attempt; R6 failure mapping; direct-URL refusals | R1, R2, R3, R5, R6 | T6, T7 | No | Not Started |
| T9 | Raw-byte cap end to end: `Content-Length` fast path, cap mapping, close after cancel (runs after T10) | R5, R6 | T10 (serial edits to `ExternalDataGateway.kt`) | Yes (with T15) | Not Started |
| T10 | Retry loop, back-off and cancellation | R7, R6, R1 | T8 | Yes (with T15) | Not Started |
| T11 | Redirect loop with per-hop scheme validation | R2, R4, R7 | T9 (serial edits to `ExternalDataGateway.kt`) | Yes (with T15) | Not Started |
| T12 | Resolved-address refusal characterisation (mixed answer, redirect, rebinding, alternate literals) | R3 | T11 | Yes (with T13, T15, T16) | Not Started |
| T13 | Anonymous request construction characterisation | R8 | T11 | Yes (with T12, T15, T16) | Not Started |
| T14 | Sentinel hygiene on every failure path (characterisation) [CFC-4] | R9 | T12, T13 | Yes (with T15, T16) | Not Started |
| T15 | `AppContainer` wiring and single-instance / production-refusal tests | R1 | T8 | Yes (with T9–T14, T16) | Not Started |
| T16 | `INTERNET` permission, cleartext flag and `ManifestPolicyTest` | R10 | T1, T11 | Yes (with T12–T15, T17) | Not Started |
| T17 | `SingleCallSiteTest`: single call site, public surface, R9 source checks (characterisation) | R1, R9 | T12, T13, T14, T15 | Yes (with T16) | Not Started |
| T18 | Feature closeout: F4 filter, full suite, lint, merged-manifest checks | R1–R10 | T1–T17 | No | Not Started |

> **Parallel** means order-independent: `Yes (with Tn)` says the task has no dependency path to or from Tn, so either may be done first. Tasks are implemented one at a time, never concurrently, and every task must leave the test source set compiling (`./gradlew :app:compileDebugUnitTestKotlin` passes) before it is ticked.

## Phase 1: Build Setup (FC7 build, FC8 part; design Implementation Sequence step 1)

### - [ ] T1: Build setup: verify DR1 artefacts are available offline, pre-F4 baselines, OkHttp/MockWebServer aliases, `RestartDurabilityTest` source guard

- **Requirement:** R10, R1
- **Description:** Before editing anything, record the baselines T18 compares against; then verify the DR1 artefacts are available offline (Q1: the developer pre-fetches `mockwebserver` 4.12.0 into `.toolchain/gradle-home` outside this task list), add the `okhttp` version ref and the `okhttp` / `okhttp-mockwebserver` aliases (AD3), and wire `implementation(libs.okhttp)` and `testImplementation(libs.okhttp.mockwebserver)`. Adding OkHttp turns F1's `Class.forName("okhttp3.OkHttpClient")` assertion red, so the same task replaces it with AD14's source guard and keeps the `ProxySelector` and no-active-network guards. **[ASSUMPTION — AD14's `RestartDurabilityTest` edit moves from Implementation Sequence step 7 to step 1, because the suite would otherwise stay red from T1 until T16.]** Verify before editing the build files: `find .toolchain/gradle-home -path '*com.squareup.okhttp3/mockwebserver/4.12.0*' -name '*.jar' | grep -c .` prints a non-zero count. If it prints `0`, or the offline classpath check under Verification fails, halt and ask the developer to provision the artefact (Q1); do not fetch it from the task.
- **Precondition (user action, Q2):** halt until the developer has committed F3 (and the F3/F4 spec directories). Only the developer runs mutating git commands (`CLAUDE.md`), so T1 waits rather than committing; it proceeds once `git status --porcelain -- app gradle docs` prints nothing.
- **Pre-start (writes only to gitignored `.toolchain/`):**
  1. `git status --porcelain -- app gradle docs` prints nothing (F3 is committed), then `mkdir -p .toolchain/f4-baseline && git rev-parse HEAD > .toolchain/f4-baseline/base-commit.txt`. T18's protected-path checks diff against this commit, so they are meaningful only because F3 is committed at it.
  2. `./gradlew :app:processReleaseMainManifest && cp app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml .toolchain/f4-baseline/release-AndroidManifest.xml`.
  **[ASSUMPTION — baselines live under the gitignored `.toolchain/f4-baseline/`, which `./gradlew clean` does not touch.]**
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/RepoPaths.kt` — repository-root resolution for the source guard
  - Modify: `gradle/libs.versions.toml`
  - Modify: `app/build.gradle.kts`
  - Modify: `app/src/test/java/ie/pantry/data/db/RestartDurabilityTest.kt`
- **Dependencies:** None
- **Parallel:** No — every other task depends on it, so its precondition and pre-start baselines are recorded before any file under `app/` is created
- **Acceptance Criteria:**
  - GIVEN OkHttp 4.12.0 on the runtime classpath
    WHEN `RestartDurabilityTest` runs
    THEN reopening the database still makes zero `ProxySelector` selections with no active network, and no main file under `app/src/main/java/ie/pantry/data/db/` references `okhttp3` or `ie.pantry.data.gateway` (R10 AC2; Q1; AD14)
  - GIVEN the catalog and build script
    WHEN the debug unit-test runtime classpath is resolved offline
    THEN it contains `com.squareup.okhttp3:okhttp:4.12.0` and `com.squareup.okhttp3:mockwebserver:4.12.0`, both through catalog aliases sharing one version ref (F1 R1; AD3)
- **Tests:**
  - `` `reopen completes with no network access`() `` — modified: the `Class.forName` assertion is removed; the `ProxySelector` and no-active-network guards are unchanged. Red step: this test fails once the dependency lines are added
  - `` `persistence sources reference no HTTP client or gateway`() `` — new source guard over every `.kt` file under `ie/pantry/data/db/` via `RepoPaths.repoRoot()`
  - File: `app/src/test/java/ie/pantry/data/db/RestartDurabilityTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.db.RestartDurabilityTest'`; with `D() { ./gradlew --offline :app:dependencies --configuration debugUnitTestRuntimeClasspath; }`: `D | grep -c 'com.squareup.okhttp3:mockwebserver:4.12.0' && ! D | grep -q FAILED` prints a non-zero count and exits 0 (no artefact on the classpath is marked `FAILED`); `grep -c 'implementation(libs.okhttp)\|testImplementation(libs.okhttp.mockwebserver)' app/build.gradle.kts` prints `2`; `test -s .toolchain/f4-baseline/base-commit.txt && test -s .toolchain/f4-baseline/release-AndroidManifest.xml` exits 0.

## Phase 2: Pure Policy, Error Model and Nutrition URL (FC3, FC1, FC2, FC5 part; steps 2–3)

### - [ ] T2: `ConnectionTargetPolicy`: scheme verdicts, blocked ranges, exemptions

- **Requirement:** R2, R3
- **Description:** Implement FC3 / I4 in pure Kotlin (`java.net` only): `classifyTarget` (RFC 3986 scheme read; `SchemeRefused` / `NoScheme` / `HttpScheme`), byte-prefix `isBlocked` over Q4's IPv4 and IPv6 ranges with `::ffff:0:0/96` checked as IPv4, `permits` (exact address-and-port exemption), `permitsForResolution` (address-only exemption) and `STRICT`.
- **Files:**
  - Create: `app/src/main/java/ie/pantry/data/gateway/ConnectionTargetPolicy.kt`
  - Create: `app/src/test/java/ie/pantry/data/gateway/ConnectionTargetPolicyTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T3, T4, T7) — new files only, no OkHttp import
- **Acceptance Criteria:**
  - GIVEN every inside and boundary IPv4 fixture in spec R3 AC1, and the IPv6 inside and neighbour fixtures in the design's `ConnectionTargetPolicyTest` row (`[DEF-05]`)
    WHEN `isBlocked` is asked about each
    THEN every inside address is refused and every neighbour is allowed (R3 AC1)
  - GIVEN `::ffff:127.0.0.1`, `::ffff:10.0.0.1` and `::ffff:169.254.169.254` built as raw 16-byte `Inet6Address`es, and `::ffff:126.255.255.255`
    WHEN `isBlocked` is asked
    THEN the three are refused and the last is allowed (R3 AC2)
  - GIVEN `intent://`, `content://`, `file://`, `javascript:`, `ftp://`, `data:`, `HTTP://x`, `""`, whitespace and `ht!tp://x`
    WHEN `classifyTarget` is called
    THEN the first six are `SchemeRefused`, `HTTP://x` is `HttpScheme`, and the rest are `NoScheme`, with no exception (R2 AC1, AC4 scheme part)
  - GIVEN a policy exempting `(127.0.0.1, p)`
    WHEN `permits` and `permitsForResolution` are asked about `127.0.0.1` with ports `p` and `p + 1`
    THEN `permits` allows only port `p`, and `permitsForResolution` allows the address (AD8)
- **Tests:**
  - `` `ipv4 addresses inside every blocked range are refused`() `` — R3 AC1 IPv4 inside list, verbatim
  - `` `ipv4 boundary neighbours outside every blocked range are allowed`() `` — both sides of each range; only `1.0.0.0` for `0.0.0.0/8`
  - `` `ipv6 addresses inside every blocked range are refused`() `` — design IPv6 inside list
  - `` `ipv6 boundary neighbours are allowed`() `` — `::2`, `fbff:…`, `fe00::`, `fe7f:…`, `2001:4860:4860::8888`
  - `` `ipv4 mapped forms of blocked addresses are refused`() `` — raw 16-byte construction
  - `` `ipv4 mapped form of an allowed address is allowed`() `` — `::ffff:126.255.255.255`
  - `` `permits refuses a blocked address unless its exact address and port are exempt`() ``
  - `` `permitsForResolution exempts by address regardless of port`() ``
  - `` `classifyTarget refuses every non http scheme`() ``
  - `` `classifyTarget accepts http and https case insensitively`() ``
  - `` `classifyTarget reports no scheme for empty whitespace and malformed input`() ``
  - `` `policy source imports neither okhttp nor android`() `` — source check via `RepoPaths` (spec Always Do)
  - File: `app/src/test/java/ie/pantry/data/gateway/ConnectionTargetPolicyTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.ConnectionTargetPolicyTest'`

### - [ ] T3: Result, error and policy model: the CFC-4 typed-error surface and re-wrapping rule [CFC-4]

- **Requirement:** R9, R6, R1
- **Description:** Deliver the CFC-4 artifact F4 owns (AD12): `GatewayException` (AD1: `internal` constructor with no `String` parameter, `Category`, `causeType` from a `KClass`, message built from enum names and integers, `init` status-code invariant), `gatewayError`, `gatewayFailure` (I3 classification over cause chain and suppressed with a visited set; no chaining; stack frames copied; no logging), the two `IOException` markers and `isTransient`, plus `GatewayResult` / `FetchedBody` / `BodyEncoding` / `CallType` (DM1–DM6) and `GatewayPolicy` with `DEFAULT` (DM7). The KDoc on `gatewayFailure` states AD12 rules (a)–(e) as the rule F5 and F16 copy. Comments must not quote any token T17's raw-text R9 source check forbids (`Log.`, `println`, `printStackTrace`, `HttpLoggingInterceptor`, `String.format`, or a `$` in an exception-message argument). **[ASSUMPTION — the `BodyEncoding` classifier is an `internal` companion function, e.g. `BodyEncoding.of(header: String?)`; the design names the behaviour (DM3), not the function.]** **[ASSUMPTION — `GatewayPolicy` invariant tests live in `GatewayErrorHygieneTest` per Implementation Sequence step 3's "constructor invariants".]**
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/Sentinels.kt` — `assertNoSentinel()` is a `Throwable` extension (AD1)
  - Create: `app/src/main/java/ie/pantry/data/gateway/GatewayResult.kt`
  - Create: `app/src/main/java/ie/pantry/data/gateway/GatewayException.kt`
  - Create: `app/src/main/java/ie/pantry/data/gateway/GatewayPolicy.kt`
  - Create: `app/src/test/java/ie/pantry/data/gateway/GatewayErrorHygieneTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T2, T4) — new files only; no OkHttp import
- **Acceptance Criteria:**
  - GIVEN a nested `IOException("SENTINEL")` → `ConnectException("SENTINEL")` chain, a throwable whose suppressed list holds an `AddressRefusedException`, and a cause cycle
    WHEN `gatewayFailure` re-wraps each
    THEN the category follows I3's first-match order, `causeType` is the top-level class simple name (null for the markers), `cause == null`, `suppressed` is empty, the stack frames equal the cause's, the cycle terminates, and `assertNoSentinel()` passes [CFC-4]
  - GIVEN `GatewayException`'s declared fields
    WHEN they are inspected by reflection
    THEN every field type is in `{Category, CallType, Integer, int, String}` and the only `String` field is `causeType`; `message` is `Gateway failure: category=… callType=… status=… attempts=… cause=…` built from those fields only (R9 AC3) [CFC-4]
  - GIVEN a `GatewayException` with `HTTP_STATUS` and no status code, or another category with a status code
    WHEN it is constructed
    THEN `init` fails with a constant-literal message (DM4)
  - GIVEN each category and status code
    WHEN `isTransient()` is called
    THEN it is true only for `TIMEOUT`, `CONNECTION_FAILED` and `HTTP_STATUS` 500–599 (AD6; `[SEAL-06]`)
  - GIVEN `GatewayPolicy.DEFAULT` and policies that violate DM7's constraints
    WHEN they are read or constructed
    THEN `DEFAULT` holds Q2's values (10 s, 15 s, 20 s, 5 242 880 bytes, 5 redirects, 2 retries, `[500 ms, 1 s]`) and each violation fails with a constant message
- **Tests:**
  - `` `gatewayFailure maps the address refused marker to ADDRESS_REFUSED with no cause type`() ``
  - `` `gatewayFailure maps the response too large marker to RESPONSE_TOO_LARGE`() ``
  - `` `gatewayFailure maps interrupted io and socket timeouts to TIMEOUT`() ``
  - `` `gatewayFailure maps every other io exception to CONNECTION_FAILED`() `` — `UnknownHostException`, `ConnectException`, `SSLException`, `SocketException`, `ProtocolException`, `EOFException`, `UnknownServiceException`
  - `` `gatewayFailure maps a non io exception to UNEXPECTED`() ``
  - `` `gatewayFailure finds markers in the cause chain and suppressed exceptions and terminates on cycles`() ``
  - `` `a re-wrapped error has no cause no suppressed exceptions and the cause stack frames`() `` — includes `assertNoSentinel()`
  - `` `gateway exception fields hold only gateway vocabulary`() ``
  - `` `status code is present exactly when the category is HTTP_STATUS`() ``
  - `` `only TIMEOUT CONNECTION_FAILED and 5xx HTTP_STATUS are transient`() ``
  - `` `fetched body toString prints only size and encoding`() ``
  - `` `body encoding is classified from the content encoding header`() `` — absent/`identity` → `IDENTITY`; `gzip`/` X-GZIP ` → `GZIP`; `br`, `gzip, br` → `OTHER`
  - `` `default policy holds the Q2 values`() ``
  - `` `policy rejects invariant violations with constant messages`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayErrorHygieneTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.GatewayErrorHygieneTest'`; `grep -c 'import okhttp3\|import android' app/src/main/java/ie/pantry/data/gateway/GatewayException.kt app/src/main/java/ie/pantry/data/gateway/GatewayResult.kt app/src/main/java/ie/pantry/data/gateway/GatewayPolicy.kt` prints three `<path>:0` lines, one per file (grep's exit status 1 on zero matches is expected and harmless).

### - [ ] T4: `NutritionQuery` URL builder and its unit and source checks

- **Requirement:** R8
- **Description:** Create `internal object NutritionQuery` (I6, AD11) with `ENDPOINT = https://world.openfoodfacts.org/cgi/search.pl`, the constant parameters `search_simple=1`, `action=process`, `json=1`, `page_size=5`, the term parameter `search_terms`, and `url(endpoint, term)` built only with `HttpUrl.Builder.addQueryParameter`. The source check matches raw text (comments are not stripped, because `//` also occurs inside the `ENDPOINT` string), so comments in `NutritionQuery.kt` must not quote any forbidden token. It comes before the transport because `GatewaySeams.PRODUCTION` (T6) defaults to `NutritionQuery.ENDPOINT`.
- **Files:**
  - Create: `app/src/main/java/ie/pantry/data/gateway/NutritionQuery.kt`
  - Create: `app/src/test/java/ie/pantry/data/gateway/GatewayRequestInspectionTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T2, T3, T5, T7) — new files only
- **Acceptance Criteria:**
  - GIVEN the term `crème fraîche & salt/pepper?#x=1`
    WHEN `NutritionQuery.url(ENDPOINT, term)` is built
    THEN `queryParameter("search_terms")` equals the term exactly, `queryParameterNames` equals the constant name set, the path equals the endpoint's and there is no fragment (R8 AC1 unit part; I6)
  - GIVEN `NutritionQuery.kt`'s source
    WHEN it is inspected
    THEN it contains `addQueryParameter` and none of `"$`, `${`, `+ term`, `.plus(` or `String.format` (R8 AC2)
- **Tests:**
  - `` `nutrition url carries the term as one search_terms parameter`() ``
  - `` `nutrition url query parameter names are the compile time constant set`() ``
  - `` `production nutrition endpoint is the Open Food Facts search url over https`() ``
  - `` `nutrition query source builds the url with addQueryParameter and no string interpolation`() `` — via `RepoPaths`
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayRequestInspectionTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.GatewayRequestInspectionTest'`

## Phase 3: Transport and Seams (FC4, FC6; step 4)

### - [ ] T5: Socket guard and filtering Dns in `GatewayTransport.kt`

- **Requirement:** R3
- **Description:** Create `GatewayTransport.kt` with `GuardedSocket` (overrides `connect(SocketAddress, Int)`; refuses unresolved endpoints and `!permits(address, port)` with `AddressRefusedException` before `super.connect`), `GuardedSocketFactory` (every `createSocket` overload returns a `GuardedSocket`; host/port overloads connect through the guard) and `TargetFilteringDns` (drops addresses failing `permitsForResolution`; throws `AddressRefusedException` when none remain) (AD5). This retires DR9 before any orchestration exists. `GatewayTransportTest` starts its own live server with `MockWebServer().start(InetAddress.getByName("127.0.0.1"), 0)` (T6 reuses it), so it does not need T7's `TestGateways`. Comments must not quote any token T17's raw-text R9 source check forbids (see T3).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/gateway/ConnectionTargetPolicy.kt` — `permits`, `permitsForResolution`, `STRICT`
  - Read: `app/src/main/java/ie/pantry/data/gateway/GatewayException.kt` — `AddressRefusedException`
  - Create: `app/src/main/java/ie/pantry/data/gateway/GatewayTransport.kt`
  - Create: `app/src/test/java/ie/pantry/data/gateway/GatewayTransportTest.kt`
- **Dependencies:** T1, T2, T3
- **Parallel:** Yes (with T4, T7) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `GuardedSocket(ConnectionTargetPolicy.STRICT)` and the first inside fixture of every blocked range, with `127.0.0.1` on a live `MockWebServer`'s port
    WHEN `connect(InetSocketAddress(blocked, port), timeout)` is called, with no Dns involved
    THEN it throws `AddressRefusedException` and the live server records 0 requests (R3 AC6 connect-time check; DR9)
  - GIVEN an unresolved `InetSocketAddress`
    WHEN `GuardedSocket.connect` is called
    THEN it throws `AddressRefusedException` (I5)
  - GIVEN a `TargetFilteringDns` whose delegate answers `[10.0.0.1, 93.184.216.34]`, and one answering only blocked addresses
    WHEN `lookup` is called
    THEN the first returns only `93.184.216.34`, and the second throws `AddressRefusedException` (R3 AC4 route choice; AD5)
- **Tests:**
  - `` `guarded socket refuses the first fixture of every blocked range before connecting`() ``
  - `` `guarded socket refuses a non exempt loopback port while the live server records no request`() ``
  - `` `guarded socket refuses an unresolved address`() ``
  - `` `guarded socket connects to an exempt address and port`() ``
  - `` `every socket factory overload returns a guarded socket`() ``
  - `` `target filtering dns keeps only allowed addresses from a mixed answer`() ``
  - `` `target filtering dns throws address refused when every answer is blocked`() ``
  - `` `target filtering dns exempts by address only`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayTransportTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.GatewayTransportTest'`

### - [ ] T6: `GatewaySeams`, raw-byte cap interceptor and `buildGatewayClient`

- **Requirement:** R5, R1, R8
- **Description:** Create `GatewaySeams` (DM8; every parameter a `val`) with `PRODUCTION` and `TlsOverride` (FC6). Add `CappedSource` and `RawByteCapInterceptor` (AD9 part 2) and `buildGatewayClient(policy, targets, seams)` exactly as AD10 (no proxy, no cookies, no cache, no redirects, no retry-on-failure, `ConnectionPool(0, 1, SECONDS)`, Q2 timeouts, write = read, `callTimeout(0)`, filtering Dns, guarded socket factory, one cap network interceptor, optional TLS override / application interceptor / event listener from seams, no logging interceptor). Comments must not quote any token T17's raw-text R9 source check forbids (see T3).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/gateway/NutritionQuery.kt` — `ENDPOINT` default for `PRODUCTION`
  - Read: `app/src/main/java/ie/pantry/data/gateway/GatewayPolicy.kt` — timeout and cap fields
  - Create: `app/src/main/java/ie/pantry/data/gateway/GatewaySeams.kt`
  - Modify: `app/src/main/java/ie/pantry/data/gateway/GatewayTransport.kt`
  - Modify: `app/src/test/java/ie/pantry/data/gateway/GatewayTransportTest.kt`
- **Dependencies:** T4, T5
- **Parallel:** Yes (with T7) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a `CappedSource` with cap `c` over a counting upstream that offers more than `c + 1` bytes
    WHEN it is read to exhaustion
    THEN the upstream delivers at most `c + 1` bytes and the source throws `ResponseTooLargeException` on byte `c + 1`; a body of exactly `c` bytes is read intact (R5 AC2 codec-boundary bound; DR4)
  - GIVEN `buildGatewayClient(GatewayPolicy.DEFAULT, STRICT, GatewaySeams.PRODUCTION)`
    WHEN its configuration is inspected
    THEN `proxy == Proxy.NO_PROXY`, `cookieJar == CookieJar.NO_COOKIES`, `cache == null`, both redirect flags and `retryOnConnectionFailure` are false, timeouts are 10 s / 15 s / 15 s with `callTimeoutMillis == 0`, `dns` is a `TargetFilteringDns`, `socketFactory` is a `GuardedSocketFactory`, `networkInterceptors` holds exactly one `RawByteCapInterceptor`, and no interceptor is a logging interceptor (R1 AC2; R8 AC3; Q6; RK2)
  - GIVEN `GatewaySeams.PRODUCTION`
    WHEN its fields are read
    THEN `exemptTargets` is empty, `tls` and `interceptor` are null, `dns == Dns.SYSTEM`, `eventListener == EventListener.NONE` and `nutritionEndpoint == NutritionQuery.ENDPOINT` (RK3; DM8)
- **Tests:**
  - `` `capped source pulls at most cap plus one bytes and throws past the cap`() ``
  - `` `capped source passes a body of exactly the cap`() ``
  - `` `built client is direct cookie less cache less and never follows redirects or retries`() ``
  - `` `built client uses the policy timeouts and no call timeout`() ``
  - `` `built client installs the guarded socket factory the filtering dns and exactly one cap interceptor`() ``
  - `` `production seams carry no exemptions no tls override and no interceptor`() ``
  - `` `production seams use the system resolver the Open Food Facts endpoint and no event listener`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayTransportTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.GatewayTransportTest'`

## Phase 4: Shared Test Helper (FC9; step 5)

### - [ ] T7: `TestGateways` helper (without `against()`) and TLS fixture

- **Requirement:** R6
- **Description:** Create `ie.pantry.testutil.TestGateways` with `server()` (bound to literal `127.0.0.1`), `urlOf(server, path, scheme = "http")` (builds `<scheme>://127.0.0.1:<port><path>`, so `urlOf(httpsServer, "/", "https")` addresses the TLS server; never `server.url()`), `FAST`, `httpsServer()`, `trustingTestCert()` (in-memory `KeyStore` holding only the certificate entry; returns a plain `SSLSocketFactory` and `X509TrustManager` pair, which T8 wraps into `TlsOverride`, so T7 does not depend on T6), `answerEvery(response: () -> MockResponse)` (a `Dispatcher` that overrides **both** `dispatch` and `peek` to return a fresh copy of the same response: MockWebServer 4.12.0 reads the connection-level `SocketPolicy`, including `DISCONNECT_AT_START`, from `Dispatcher.peek()` before reading a request, so a dispatcher that overrides only `dispatch` never disconnects at start), `RecordingDns` (a scripted answer per hostname per lookup, where a scripted answer is either an address list or a `Throwable` to throw; once a hostname's script is exhausted, every later lookup repeats its last scripted answer; a hostname with no script throws `UnknownHostException`, so it never reaches the platform resolver), `RecordingEventListener` (recording `callStart`, `connectStart` (with its socket address), `connectionReleased` and `callFailed` per call, with wall-clock times for `callStart` and `callFailed` only, and `awaitCallStart(n)` backed by per-index `CompletableDeferred`; T8 to T12 assert on all four) and `RecordingBackoff` (records each delay, then calls `delay`, which is virtual under `runTest`); run `mkdir -p app/src/test/resources/gateway`, then generate `test-server.p12` with the keytool command in the design's Testing Strategy, recorded in `TestGateways`' KDoc. **[ASSUMPTION — `against()` is added in T8, not here: it constructs `ExternalDataGateway`, which does not exist until step 6.]**
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/gateway/GatewayPolicy.kt` — `FAST` is a `GatewayPolicy`
  - Create: `app/src/test/java/ie/pantry/testutil/TestGateways.kt`
  - Create: `app/src/test/resources/gateway/test-server.p12`
- **Dependencies:** T1, T3
- **Parallel:** Yes (with T2, T4, T5, T6) — test-only new files
- **Acceptance Criteria:**
  - GIVEN the generated keystore
    WHEN it is listed with `keytool`
    THEN it holds one EC P-256 `PrivateKeyEntry` aliased `test-server` with SAN `dns:localhost, ip:127.0.0.1`
  - GIVEN the test source set
    WHEN it compiles
    THEN `TestGateways` builds with no main-source change and no new dependency
- **Tests:** none — test-only helper with no behaviour of its own; exercised from T8 onward. T8's `` `an https page fetch with the test certificate trusted succeeds`() `` verifies the fixture negotiates under `MODERN_TLS` (the untrusted-certificate case cannot, because it fails whether or not the handshake would otherwise succeed)
- **Verification:** `./gradlew :app:compileDebugUnitTestKotlin`; with `k() { keytool -list -v -keystore app/src/test/resources/gateway/test-server.p12 -storepass pantry-test; }`: `k | grep -c 'PrivateKeyEntry'` prints `1`, `k | grep -c 'secp256r1\|prime256v1'` prints a non-zero count, and `k | grep -c 'IPAddress: 127.0.0.1'` prints a non-zero count.

## Phase 5: Gateway Orchestrator (FC5, FC9; step 6)

### - [ ] T8: `ExternalDataGateway` core: public surface, validation order, hop bridge, single attempt; R6 failure mapping; direct-URL refusals

- **Requirement:** R1, R2, R3, R5, R6
- **Description:** Create `ExternalDataGateway` (I1): `internal` primary constructor, `create()`, `internal val policy`, lazy `internal val client`, the three public `suspend` ops with I1's validation order (`classifyTarget`, then `toHttpUrlOrNull`, then the attempt; a blank nutrition term gives `INVALID_REQUEST`), `request(url)` (GET, only `Accept-Encoding: gzip`, AD2), and `executeHop` with the attempt deadline set through `call.timeout()` (AD6) and no redirect following: a plain 2xx body read into `FetchedBody` with the raw wire bytes, `BodyEncoding` and `mediaType` (AD2); every non-2xx, including every 3xx, gives `HTTP_STATUS` with its code until T11; a thrown exception goes through `gatewayFailure`. **Split with T10:** T8 builds the `suspendCancellableCoroutine` hop bridge that runs `executeHop` on `hopScope` / `ioDispatcher` with the `isActive` resume guard (AD7), because no call can run without it, and makes exactly one attempt per call. T10 adds the retry loop, the back-off through `seams.backoffDelay` (AD6), `invokeOnCancellation { call.cancel() }`, the `CancellationException` rethrow ahead of the defensive `catch` and the no-attempt-after-cancel rule (AD7). Add `TestGateways.against(...)` as FC9 describes, taking one or more servers and exempting each one's exact `(127.0.0.1, port)` pair, so a two-server redirect chain (T11 AC3) is reachable, and add `TestGateways.throwingInterceptor(...)` for the `UNEXPECTED` fixture. `against()`'s `dns` defaults to a fresh `RecordingDns` with no script, so every hostname lookup not scripted by the test throws `UnknownHostException` and no test depends on the sandbox resolver; its `backoff` defaults to a fresh `RecordingBackoff`, and every T8 test runs under `runTest`, so once T10 adds retries the back-off of transient cases is virtual and never sleeps in real time. Comments in `ExternalDataGateway.kt` must not quote any token T17's raw-text R9 source check forbids (see T3). No `TestGateways` class, function or lambda name contains the case-sensitive `SENTINEL` marker, so no stack frame from it carries the marker (T14 relies on this). **[ASSUMPTION — `against()` takes `vararg servers: MockWebServer` in place of FC9's single `server` parameter, so FC9's `against(server)` call form still compiles.]** **[ASSUMPTION — `against()` also takes `nutritionEndpoint: HttpUrl`, defaulting to `urlOf(<first server>, "/search")`, so nutrition lookups reach the test server (DM8) and this task's direct-URL refusal cases can point them at a blocked target; FC9's parameter list omits it.]** **IPv6 loopback rule (`[DEF-18]`):** the literal `http://[::1]:<port>/` case attempts to bind its own `MockWebServer` to `::1`, records `boundOk` (false if the bind throws), and then calls `Assume.assumeTrue(boundOk)` (JUnit reports it skipped, not passed, when the bind failed). IPv6 refusal stays covered unconditionally by T5's `GuardedSocket` test and by T12's Dns-mapped `::1` cases, which need no IPv6 listener.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/gateway/GatewayTransport.kt` — `buildGatewayClient`
  - Read: `app/src/main/java/ie/pantry/data/gateway/GatewaySeams.kt` — seam fields
  - Create: `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt`
  - Create: `app/src/test/java/ie/pantry/data/gateway/GatewayFailureMappingTest.kt`
  - Create: `app/src/test/java/ie/pantry/data/gateway/GatewayTargetValidationTest.kt`
  - Modify: `app/src/test/java/ie/pantry/testutil/TestGateways.kt`
- **Dependencies:** T6, T7
- **Parallel:** No — T9 to T18 all depend on it
- **Acceptance Criteria:**
  - GIVEN each call type with a server whose dispatcher answers every request with `SocketPolicy.NO_RESPONSE`, and one whose dispatcher answers every request with a 2 KiB body sent with `throttleBody(1024, 3, SECONDS)` (the first 1 KiB arrives, then the server thread sleeps 3 s, above `FAST`'s 1 s read timeout). **[ASSUMPTION — 3 s, not the design row's `throttleBody(1024, 1, HOURS)`: `MockWebServer.shutdown()` waits 5 s for its threads and gives up with an `IOException` on an hour-long sleep, so the sleep must sit between the 1 s read timeout and the 5 s shutdown wait; logged as a pending row in `## Implementation Deviations`.]**
    WHEN each call is made with `FAST`
    THEN each returns `TIMEOUT`, and each attempt ends within `attemptTimeout + 1 s` wall time, measured between the listener's `callStart` and `callFailed` records (R6 AC1, per-attempt part; T10 adds the whole-call bound)
  - GIVEN each call type with a server whose dispatcher answers every request with one of 204, 304, 400, 403, 404, 410, 429, 500, 502 and 503, with no `Location`
    WHEN each call is made
    THEN 204 returns `Fetched` with an empty body and every other status returns `HTTP_STATUS` with that code; because every request gets the same answer, the 5xx cases still hold once T10 retries them (R6 AC2)
  - GIVEN a 200 body with a `Content-Type` and no `Content-Encoding`, and a gzip body whose compressed size is under the cap
    WHEN each is page-fetched
    THEN the first returns its bytes with `encoding == IDENTITY` and `mediaType` equal to the raw header value, and the second returns the compressed wire bytes with `encoding == GZIP` (R5 AC4; AD2)
  - GIVEN `httpsServer()` answering 200 with a body, and a gateway built with `against(httpsServer, trustTestCert = true)`
    WHEN `fetchPage(urlOf(httpsServer, "/", "https"))` is called
    THEN it returns `Fetched` with that body, proving the EC P-256 fixture negotiates under OkHttp's default `MODERN_TLS` spec (design Testing Strategy, Fixtures)
  - GIVEN each call type with `DISCONNECT_AT_START` and `DISCONNECT_DURING_RESPONSE_BODY` over a non-empty (1 KiB) body (each from `TestGateways.answerEvery`, which returns the same response from both `dispatch` and `peek`, so the cases still hold once T10 retries them; attempts are counted by the listener's `callStart` records, and `requestCount` is not asserted for a dropped connection), a closed port (an exempt server whose gateway is built with `against(server)` before `server.shutdown()`, so the port stays exempt and the refusal comes from the OS, not the guard), a `RecordingDns` throwing `UnknownHostException`, the HTTPS server without `trustingTestCert()`, and `throwingInterceptor` throwing `IllegalStateException`
    WHEN each call is made
    THEN the first five return `CONNECTION_FAILED` (the closed port never `ADDRESS_REFUSED`), the last returns `UNEXPECTED`, and no exception leaves the gateway (R6 AC4; R1 AC1)
  - GIVEN initial URLs with `intent://`, `content://`, `file://`, `javascript:`, `ftp://` and `data:` schemes, and `""`, whitespace, `https://` and `ht!tp://x`
    WHEN the page fetch and image fetch are called
    THEN the first set gives `SCHEME_REFUSED`, the second `INVALID_REQUEST`, and both the server's request count and the listener's `connectStart` count are 0 (R2 AC1, AC4)
  - GIVEN `http://127.0.0.1:<p>/` and `http://[::1]:<p>/` on a non-exempt port, and a `RecordingDns` hostname mapped to `127.0.0.1` on that port
    WHEN a page fetch, an image fetch and a nutrition lookup (via `nutritionEndpoint` pointed at each target) are made
    THEN each returns `ADDRESS_REFUSED` with `attempts == 1`, and the blocked server records 0 requests (R3 AC3; T10's non-retryable table proves it is not retried)
- **Tests:**
  - `` `a 200 response returns its body bytes for each call type`() ``
  - `` `a 200 response carries identity encoding and the raw content type`() ``
  - `` `a gzip body is returned as its compressed wire bytes with GZIP encoding`() ``
  - `` `an https page fetch with the test certificate trusted succeeds`() ``
  - `` `a server that never sends headers gives TIMEOUT within the attempt bound for each call type`() ``
  - `` `a body that stalls midway gives TIMEOUT within the attempt bound for each call type`() ``
  - `` `204 succeeds with an empty body for each call type`() ``
  - `` `every non 2xx status gives HTTP_STATUS carrying its code for each call type`() ``
  - `` `connection level failures give CONNECTION_FAILED for each call type`() ``
  - `` `an exception thrown inside the client gives UNEXPECTED for each call type`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayFailureMappingTest.kt`
  - `` `non http schemes are refused before any connection for page and image fetch`() ``
  - `` `malformed urls give INVALID_REQUEST before any connection`() ``
  - `` `literal blocked ipv4 urls give ADDRESS_REFUSED for every call type with no request recorded`() ``
  - `` `a literal ipv6 loopback url gives ADDRESS_REFUSED for every call type`() `` — skipped by `Assume` when `::1` cannot be bound
  - `` `a hostname resolving to a blocked address gives ADDRESS_REFUSED for every call type`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayTargetValidationTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.GatewayFailureMappingTest' --tests 'ie.pantry.data.gateway.GatewayTargetValidationTest'`; the test report lists `a literal ipv6 loopback url gives ADDRESS_REFUSED for every call type` as passed or skipped, never failed.

### - [ ] T9: Raw-byte cap end to end: `Content-Length` fast path, cap mapping, close after cancel (runs after T10)

- **Requirement:** R5, R6
- **Description:** (runs after T10) Add AD9's `Content-Length > cap` fast path to `executeHop` (cancel the call, return a `TooLarge` outcome, mapped to `RESPONSE_TOO_LARGE`), and close every response whose body is not fully read only after `call.cancel()` (AD9 part 3). Everything else about the cap already holds from T6 and T8: 2xx bodies pass through `RawByteCapInterceptor`'s `CappedSource`, and its `ResponseTooLargeException` already maps to `RESPONSE_TOO_LARGE` through `gatewayFailure` (T3). **Red tests:** the two fast-path tests (headers declaring cap + 1 with a body that never arrives, or a connection that drops mid-body) are red against T10's code, which waits for the body and returns `TIMEOUT` or `CONNECTION_FAILED`. **Regression guards:** every other test below passes already at T10 and is kept to pin the cap behaviour end to end.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt`
  - Modify: `app/src/test/java/ie/pantry/data/gateway/GatewayFailureMappingTest.kt`
- **Dependencies:** T10 (serial edits to `ExternalDataGateway.kt`)
- **Parallel:** Yes (with T15) — disjoint files
- **Acceptance Criteria:**
  - GIVEN, for each call type under `FAST`, a server whose response sends headers declaring `Content-Length: cap + 1` and then no body bytes while keeping the connection open, and one that declares `Content-Length: cap + 1` over a 1 KiB body sent with `DISCONNECT_DURING_RESPONSE_BODY` (`setBody` first, then `setHeader("Content-Length", cap + 1)`, because `setBody` overwrites the header)
    WHEN each call is made, with `gateway.client` read once before the stopwatch starts (so lazy client construction is not timed) and wall time measured around the gateway call
    THEN each returns `RESPONSE_TOO_LARGE` (not `TIMEOUT` and not `CONNECTION_FAILED`; the primary assertion), and does so within 500 ms, well inside `FAST`'s 1 s read timeout, so the declared length is refused before any body read (R5 AC2, fast path) — red
  - GIVEN each call type under both `FAST` and `DEFAULT`, a 200 body of exactly the cap and one of cap + 1
    WHEN each call is made
    THEN the at-cap body is returned with `bytes.size == cap`, and the over-cap body returns `RESPONSE_TOO_LARGE` (R5 AC1) — guard
  - GIVEN a fully sent over-cap body with a true `Content-Length`, and one sent with `setChunkedBody`
    WHEN each is fetched
    THEN each returns `RESPONSE_TOO_LARGE` (R5 AC2; the cap + 1 codec-byte bound is proved at the unit level by T6's `CappedSource` tests, not re-asserted here) — guard
  - GIVEN a `Content-Length` under the cap with more bytes written than declared, totalling more than the cap
    WHEN it is fetched
    THEN the call returns `Fetched` with exactly the declared bytes, and afterwards `client.connectionPool.connectionCount() == 0` and the listener recorded `connectionReleased`, so the connection is closed and never reused (R5 AC3; design Q2) — guard
  - GIVEN an over-cap call
    WHEN it returns
    THEN `client.connectionPool.connectionCount() == 0` and the listener recorded `connectionReleased` (R5 AC5) — guard
  - GIVEN a timeout, a 404 and an over-cap response
    WHEN their categories are matched in an exhaustive `when (error.category)`
    THEN they are `TIMEOUT`, `HTTP_STATUS` and `RESPONSE_TOO_LARGE`, with no string matching (R6 AC3) — guard
- **Tests:**
  - `` `a declared over cap content length with a body that never arrives gives RESPONSE_TOO_LARGE at once for each call type`() `` — red
  - `` `a declared over cap content length with a dropped body gives RESPONSE_TOO_LARGE at once for each call type`() `` — red
  - `` `a body of exactly the cap is returned intact for each call type under FAST and DEFAULT`() `` — guard
  - `` `a body one byte over the cap gives RESPONSE_TOO_LARGE for each call type under FAST and DEFAULT`() `` — guard
  - `` `a fully sent over cap body with a true content length gives RESPONSE_TOO_LARGE`() `` — guard
  - `` `a chunked over cap body gives RESPONSE_TOO_LARGE`() `` — guard
  - `` `a falsely low content length succeeds with exactly the declared bytes and a closed connection`() `` — guard
  - `` `an over cap call leaves no pooled connection`() `` — guard
  - `` `timeout non 2xx and oversized errors are distinct categories`() `` — guard
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayFailureMappingTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.*'`. Mutation check (`GatewayFailureMappingTest`): temporarily remove the `Content-Length > cap` pre-check from `executeHop`; `a declared over cap content length with a body that never arrives gives RESPONSE_TOO_LARGE at once for each call type` fails (it returns `TIMEOUT`); revert and re-run green.

### - [ ] T10: Retry loop, back-off and cancellation

- **Requirement:** R7, R6, R1
- **Description:** Wrap T8's single attempt in AD6's attempt loop: at most `retryCount + 1` attempts, each with its own `attemptTimeout` deadline, `isTransient()` deciding whether to retry, back-off through `seams.backoffDelay`, and `attempts` counting attempts started. Add AD7's cancellation behaviour to T8's hop bridge: `invokeOnCancellation { call.cancel() }`, the `CancellationException` rethrow ahead of each public op's defensive `catch (e: Exception)`, and no attempt after cancellation. Every test below except the non-retryable table is red against T8's single-attempt code (one `callStart`, an empty back-off record, an uncancelled OkHttp call); the non-retryable table already passes at T8 and is a regression guard. Includes the per-attempt cap fixture (`[DEF-13]`, attempt half), which needs only T6's `CappedSource`, not T9. Proven under `runTest` virtual time with real MockWebServer I/O.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/TestGateways.kt` — `RecordingBackoff`, `RecordingEventListener.awaitCallStart`
  - Create: `app/src/test/java/ie/pantry/data/gateway/GatewayRetryTest.kt`
  - Modify: `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt`
- **Dependencies:** T8
- **Parallel:** Yes (with T15) — disjoint files
- **Acceptance Criteria:**
  - GIVEN each call type with a server that drops every connection (`DISCONNECT_AT_START` through `TestGateways.answerEvery`, so `peek` carries the policy) and one that returns 503 every time
    WHEN each call is made
    THEN there are exactly 3 `callStart`s (attempts are counted by `callStart`; `requestCount` is not asserted for the dropped connection, and the 503 case also has `requestCount == 3`), `RecordingBackoff` holds `[500 ms, 1 s]`, `testScheduler.currentTime == 1500`, and the result is `CONNECTION_FAILED` or `HTTP_STATUS` 503 with `attempts == 3` (R7 AC1)
  - GIVEN a transient failure then a 200
    WHEN a page fetch is made
    THEN it returns the second body and `requestCount == 2` (R7 AC2)
  - GIVEN each call type with a server whose dispatcher answers every request with `SocketPolicy.NO_RESPONSE`
    WHEN each call is made with `FAST`
    THEN there are exactly 3 `callStart`s, the result is `TIMEOUT` with `attempts == 3`, `RecordingBackoff` holds `[500 ms, 1 s]`, and the whole call ends within `3 × attemptTimeout + 1 s` wall time (back-off is virtual) (R7 AC4; R6 AC1, whole-call part)
  - GIVEN 400, 404, 429, `SCHEME_REFUSED`, `ADDRESS_REFUSED`, `RESPONSE_TOO_LARGE` (a chunked over-cap body, which T6's `CappedSource` already refuses), `INVALID_REQUEST` and `UNEXPECTED` outcomes for each call type the outcome applies to
    WHEN each call is made
    THEN each returns after one request (or none for pre-connection errors) with an empty back-off record (R7 AC3; `[SEAL-06]`)
  - GIVEN a 503 on the first attempt, then a 200 body of exactly the cap
    WHEN a page fetch is made
    THEN it succeeds with `bytes.size == cap`, because the cap applies per attempt (AD9; `[DEF-13]`, attempt half)
  - GIVEN a `NO_RESPONSE` server
    WHEN the test awaits `awaitCallStart(1)` and cancels the calling `Job`
    THEN `join` returns within 1 s wall time, the job is cancelled with no `GatewayResult` returned, `callFailed` is recorded within 500 ms of the cancel (sooner than `FAST`'s 1 s read timeout could cause it), and no second `callStart` occurs within `attemptTimeout` (R1 AC4; RK7)
- **Tests:**
  - `` `a dropped connection on every attempt is tried three times with the back off schedule for each call type`() ``
  - `` `a 503 on every attempt is tried three times then gives HTTP_STATUS 503 for each call type`() ``
  - `` `a transient failure then 200 succeeds with exactly two requests`() ``
  - `` `a timeout is retried under the same bound`() `` — 3 `callStart`s within `3 × attemptTimeout + 1 s` (R7 AC4; R6 AC1)
  - `` `non retryable outcomes return after one attempt with no back off`() ``
  - `` `the cap applies per attempt so an at cap body after a 503 retry succeeds`() `` — `[DEF-13]`
  - `` `cancelling the caller cancels the in flight call and starts no further attempt`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayRetryTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.*'`

### - [ ] T11: Redirect loop with per-hop scheme validation

- **Requirement:** R2, R4, R7
- **Description:** Add AD4's redirect loop inside one attempt: only 301/302/303/307/308 are redirects; missing `Location` → `HTTP_STATUS(code)`; `classifyTarget(location)` → `SCHEME_REFUSED`; `currentUrl.resolve(location) == null` → `INVALID_REQUEST`; `redirectsFollowed == maxRedirects` → `TOO_MANY_REDIRECTS`; otherwise re-issue a GET within the remaining attempt deadline (AD6). A transient outcome on any hop restarts the next attempt from the initial URL. The initial-URL scheme and malformed-URL cases (R2 AC1, AC4) are T8's. Each 3xx hop's unread body is closed after its call is cancelled (AD9 part 3, from T9); two large-body tests pin this for a non-2xx and a redirect response.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt`
  - Modify: `app/src/test/java/ie/pantry/data/gateway/GatewayTargetValidationTest.kt`
  - Modify: `app/src/test/java/ie/pantry/data/gateway/GatewayRetryTest.kt`
  - Modify: `app/src/test/java/ie/pantry/data/gateway/GatewayFailureMappingTest.kt`
- **Dependencies:** T9 (serial edits to `ExternalDataGateway.kt`)
- **Parallel:** Yes (with T15) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a 302 to `intent://evil#Intent;end` and a 302 to `content://ie.pantry.provider/x`
    WHEN a page fetch and an image fetch follow each
    THEN each gives `SCHEME_REFUSED`, not `HTTP_STATUS`, and the refused target gets no request (R2 AC2)
  - GIVEN an `http → https → http` chain over two servers, one with `useHttps` and `trustingTestCert()`
    WHEN a page fetch follows it
    THEN it succeeds (R2 AC3)
  - GIVEN exactly 5 redirects then 200, 6 redirects, and a self-loop
    WHEN each call type follows them
    THEN the first succeeds with exactly 6 `connectStart` records, one per hop because pooling is off (R4 AC1: each hop opened its own connection, so each passed the socket guard); the others give `TOO_MANY_REDIRECTS` with `requestCount == 6`, no retry and an empty back-off record (R4 AC1, AC2; R7 AC3)
  - GIVEN a 302 with no `Location`, a `Location` of `http://[::bad`, and a 300 and 304 with a `Location`
    WHEN any call type receives them
    THEN they give `HTTP_STATUS` 302, `INVALID_REQUEST`, and `HTTP_STATUS` 300 / 304 respectively, never an exception (R4 AC3)
  - GIVEN a first hop at `urlOf(server, "/dir/page")` answering 302 with `Location: /recipe/1` then 200; a 302 with the protocol-relative `Location: //127.0.0.1:<exemptPort>/x` then 200; and a 302 with `Location: //10.0.0.1/x`
    WHEN a page fetch follows each
    THEN the relative case succeeds and the server's second recorded `requestUrl` is `http://127.0.0.1:<exemptPort>/recipe/1`; the exempt protocol-relative case succeeds with the second request's path `/x`; and `//10.0.0.1/x` gives `ADDRESS_REFUSED` from the socket guard, with `requestCount == 1` on the first server and no retry (a literal IP bypasses Dns, and OkHttp fires `connectStart` before `GuardedSocket.connect` refuses, so `connectStart` is not asserted here) (R4 AC3: resolved against the current hop, then checked like any hop)
  - GIVEN a 302 on the first hop to a second hop that returns 503 (and separately drops the connection), then a 200 on the retry
    WHEN a page fetch is made
    THEN the second attempt's first request is to the initial URL, not the redirect target (AD6; `[DEF-01]`)
  - GIVEN a redirect hop followed by a 200 body of exactly the cap
    WHEN a page fetch is made
    THEN it succeeds, because the cap applies per response (AD9; `[DEF-13]`, hop half)
  - GIVEN, under `FAST`, a 404 whose body is 4 × the cap sent with `throttleBody(1024, 3, SECONDS)`, and a 302 with a `Location` on the exempt server and the same throttled large body, followed by a 200
    WHEN a page fetch is made against each, with `gateway.client` read once before the stopwatch starts
    THEN the first returns `HTTP_STATUS` 404 and the second returns `Fetched` with the 200 body, each within 500 ms (no drain of the unread body), and afterwards `client.connectionPool.connectionCount() == 0` and the listener recorded `connectionReleased` (AD9 part 3: cancel, then close)
- **Tests:**
  - `` `a redirect to an intent or content scheme gives SCHEME_REFUSED for page and image fetch`() ``
  - `` `an http to https to http chain succeeds`() ``
  - `` `exactly the redirect limit then 200 succeeds`() ``
  - `` `one redirect over the limit and a self loop give TOO_MANY_REDIRECTS with six requests for each call type`() ``
  - `` `a redirect with no location gives HTTP_STATUS with its code`() ``
  - `` `an unparseable location gives INVALID_REQUEST`() ``
  - `` `relative and protocol relative locations are resolved against the current hop`() ``
  - `` `a non redirect 3xx with a location gives HTTP_STATUS`() ``
  - `` `the cap applies per response across redirect hops`() ``
  - `` `a redirect with a large unread body is followed promptly and leaves no pooled connection`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayTargetValidationTest.kt`
  - `` `TOO_MANY_REDIRECTS is not retried`() `` — six requests, empty back-off record
  - `` `a transient failure after a redirect restarts the next attempt from the initial url`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayRetryTest.kt`
  - `` `a non 2xx response with a large unread body returns promptly and leaves no pooled connection`() `` — guard for T9's cancel-then-close
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayFailureMappingTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.*'`

### - [ ] T12: Resolved-address refusal characterisation (mixed answer, redirect, rebinding, alternate literals)

- **Requirement:** R3
- **Description:** Verification/characterisation task: add R3's remaining integration cases to `GatewayTargetValidationTest` over production code already complete at T11, using a second, non-exempt `MockWebServer` on loopback as the live blocked target that must record 0 requests. The direct-URL refusal cases (R3 AC3) and the `[DEF-18]` IPv6 loopback rule are T8's. No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4, and the mutation check under Verification shows the class can fail. If a test exposes a defect, fix it in `ExternalDataGateway.kt` or `GatewayTransport.kt` before ticking the task.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt` — validation order and hop flow
  - Read: `app/src/test/java/ie/pantry/testutil/TestGateways.kt` — `RecordingDns` (including `against()`'s unscripted default), `RecordingEventListener`
  - Modify: `app/src/test/java/ie/pantry/data/gateway/GatewayTargetValidationTest.kt`
- **Dependencies:** T11
- **Parallel:** Yes (with T13, T15, T16) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `RecordingDns` answering `[10.0.0.1, exempt 127.0.0.1]`
    WHEN a page fetch is made
    THEN it succeeds and `connectStart` never records `10.0.0.1` (R3 AC4; AD5)
  - GIVEN a first hop on the exempt server that redirects to a hostname `RecordingDns` maps to `10.0.0.1`, and one mapped to `::1`
    WHEN a page fetch and an image fetch follow each
    THEN each returns `ADDRESS_REFUSED` and `connectStart` never records the blocked address (R3 AC5)
  - GIVEN `RecordingDns` answering the exempt `127.0.0.1` on lookup 1 and `[10.0.0.1, ::1]` on every later lookup
    WHEN two page fetches are made
    THEN call 1 succeeds, call 2 returns `ADDRESS_REFUSED`, and `connectStart` never records `10.0.0.1` or `::1` (R3 AC6; RK1)
  - GIVEN `http://2130706433:<p>/`, `http://017700000001:<p>/` and `http://0x7f000001:<p>/` on the non-exempt port
    WHEN each is page-fetched
    THEN each returns `ADDRESS_REFUSED` or `CONNECTION_FAILED`, never `Fetched`, and the server records 0 requests (RK8; `[DEF-03]`). A form not parsed as a literal reaches `against()`'s default `RecordingDns` (T8), which throws `UnknownHostException` for an unscripted host, so the case never depends on the sandbox resolver
- **Tests:**
  - `` `a mixed answer connects only to the allowed address`() ``
  - `` `a redirect to a blocked ipv4 or ipv6 host gives ADDRESS_REFUSED for page and image fetch`() ``
  - `` `dns rebinding after the first lookup is refused at connect`() ``
  - `` `decimal octal and hex ipv4 literals never reach the blocked target`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayTargetValidationTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.GatewayTargetValidationTest'`. Mutation check (`GatewayTargetValidationTest`): temporarily make `TargetFilteringDns.lookup` return the delegate's answer unfiltered; `a mixed answer connects only to the allowed address` fails because `connectStart` records `10.0.0.1`; revert and re-run green.

### - [ ] T13: Anonymous request construction characterisation

- **Requirement:** R8
- **Description:** Verification/characterisation task: add R8's recorded-request cases to `GatewayRequestInspectionTest` against MockWebServer, with `nutritionEndpoint` pointed at the server, over production code already complete at T11. No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4, and the mutation check under Verification shows the class can fail. A defect found here is fixed in `ExternalDataGateway.kt` before ticking the task.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt` — `request(url)` and `lookupNutrition` validation
  - Modify: `app/src/test/java/ie/pantry/data/gateway/GatewayRequestInspectionTest.kt`
- **Dependencies:** T11
- **Parallel:** Yes (with T12, T15, T16) — disjoint files
- **Acceptance Criteria:**
  - GIVEN one call of each type, including a nutrition lookup of `crème fraîche & salt/pepper?#x=1`
    WHEN the recorded requests are inspected
    THEN each is a `GET` with body size 0, its header-name set equals `{Host, Connection, Accept-Encoding, User-Agent}` with `Accept-Encoding: gzip`, it has no `Authorization`, `Proxy-Authorization` or `Cookie`; page and image `requestUrl` and `Host` equal the `HttpUrl`-parsed supplied URL without fragment or userinfo; and the nutrition `search_terms` decodes to the term exactly, with every other parameter a `NutritionQuery` constant (R8 AC1)
  - GIVEN a server that sets a cookie and redirects, and a second call to it
    WHEN both calls are made
    THEN no later request carries `Cookie` (R8 AC3)
  - GIVEN `http://user:secret@127.0.0.1:<port>/path` on the exempt server (the spec's `https://` example exercises the same no-derivation rule; plain `http` avoids the TLS seam)
    WHEN it is page-fetched
    THEN the request carries no `Authorization` header (R8 AC4)
  - GIVEN an empty and a whitespace-only term
    WHEN the nutrition lookup is called
    THEN each returns `INVALID_REQUEST` with `attempts == 0` and the server records 0 requests (R8 AC5)
- **Tests:**
  - `` `every call type sends an anonymous get with only the default headers`() ``
  - `` `page and image fetch request target and host equal the parsed url without fragment or userinfo`() ``
  - `` `nutrition lookup sends the term as one decoded search_terms parameter`() ``
  - `` `a cookie set before a redirect is never sent on a later request or call`() ``
  - `` `a userinfo url produces no authorization header`() ``
  - `` `a blank or whitespace term gives INVALID_REQUEST with no request sent`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayRequestInspectionTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.GatewayRequestInspectionTest'`. Mutation check (`GatewayRequestInspectionTest`): temporarily add `.header("Cookie", "x=1")` to `request(url)`; `every call type sends an anonymous get with only the default headers` fails; revert and re-run green.

### - [ ] T14: Sentinel hygiene on every failure path (characterisation) [CFC-4]

- **Requirement:** R9
- **Description:** Verification/characterisation task: add the end-to-end half of `GatewayErrorHygieneTest` over production code already complete at T11. No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4, and the mutation check under Verification shows the class can fail. **[ASSUMPTION — sizing for `[DEF-04]`: "every failure path in R2 to R8" is covered as one sentinel-bearing fixture per (category × applicable call type) cell, listed below, rather than re-running every AC fixture with sentinels.]** Because `HttpUrl` lowercases hosts, the test also scans each error's `toString()` and `message` with `ignoreCase = true` against `Sentinels.all`, in addition to `assertNoSentinel()`. `stackTraceToString()` is checked only by the case-sensitive `assertNoSentinel()`, because stack frames carry test method and lambda names. The throwing interceptor and throwing `RecordingDns` come from `TestGateways`, whose class, function and lambda names contain no case-sensitive `SENTINEL` marker (T8); this test's own method names use lowercase `sentinel` only, so no stack frame carries the marker. If a test exposes a defect, fix it in the gateway main sources before ticking the task.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/Sentinels.kt` — `Sentinels.TITLE`, `INGREDIENT`, `URL`, `all`, and `assertNoSentinel()`
  - Read: `app/src/test/java/ie/pantry/testutil/TestGateways.kt` — `throwingInterceptor`, `RecordingDns`, `answerEvery`
  - Modify: `app/src/test/java/ie/pantry/data/gateway/GatewayErrorHygieneTest.kt`
- **Dependencies:** T12, T13
- **Parallel:** Yes (with T15, T16) — disjoint files
- **Acceptance Criteria:**
  - GIVEN each call type that a category applies to: `INVALID_REQUEST` (malformed URL holding `SENTINEL`; unparseable sentinel `Location`), `SCHEME_REFUSED` (`intent://SENTINEL…` initial and as `Location`), `ADDRESS_REFUSED` (sentinel host mapped to a blocked address; literal blocked URL with sentinel path and query), `TOO_MANY_REDIRECTS` (sentinel `Location` chain), `TIMEOUT` (a URL with `Sentinels.URL`'s host and a sentinel path, whose host `RecordingDns` maps to the exempt `127.0.0.1` of a `NO_RESPONSE` server on that port), `CONNECTION_FAILED` (`RecordingDns` throwing `UnknownHostException("SENTINEL host")`; untrusted TLS), `HTTP_STATUS` (`setStatus("HTTP/1.1 500 SENTINEL")` with a sentinel body; 404 with a sentinel body), `RESPONSE_TOO_LARGE` (over-cap sentinel body), `UNEXPECTED` (interceptor throwing `RuntimeException("SENTINEL")`), and `Sentinels.INGREDIENT` as the nutrition term on every nutrition-lookup cell; every server-side fixture is served through `TestGateways.answerEvery`, so the retried categories (`TIMEOUT`, `CONNECTION_FAILED`, `HTTP_STATUS` 500) get the same sentinel response on every attempt
    WHEN each gateway error is produced
    THEN `error.assertNoSentinel()` passes, the case-insensitive scan of `toString()` and `message` finds no sentinel, `error.cause == null` and `error.suppressed` is empty (R9 AC1, AC2) [CFC-4]
- **Tests:**
  - `` `every failure category produced from sentinel fixtures carries no sentinel for each call type`() ``
  - `` `sentinel errors carry no cause and no suppressed exception`() ``
  - `` `an unresolvable sentinel host is reduced to its exception class name`() `` — `causeType == "UnknownHostException"`
  - File: `app/src/test/java/ie/pantry/data/gateway/GatewayErrorHygieneTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.GatewayErrorHygieneTest'`. Mutation check (`GatewayErrorHygieneTest`): temporarily call `initCause(cause)` on the error built in `gatewayFailure`; `sentinel errors carry no cause and no suppressed exception` and the `assertNoSentinel()` cases fail; revert and re-run green.

## Phase 6: Wiring, Manifest and Static Guards (FC7, FC8, FC9; step 7)

### - [ ] T15: `AppContainer` wiring and single-instance / production-refusal tests

- **Requirement:** R1
- **Description:** Add the last constructor parameter `gateway: ExternalDataGateway = ExternalDataGateway.create()` and `val gateway` to `AppContainer`; `production(...)` passes `create()` explicitly. Construction does no I/O (the client is lazy, AD10). Existing 3- and 4-argument call sites stay unchanged.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/gateway/ExternalDataGateway.kt` — `create()`, `policy`, `client`
  - Modify: `app/src/main/java/ie/pantry/di/AppContainer.kt`
  - Modify: `app/src/test/java/ie/pantry/di/AppContainerTest.kt`
- **Dependencies:** T8
- **Parallel:** Yes (with T9–T14, T16) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the application's production container
    WHEN `container.gateway` and `gateway.client` are each read twice
    THEN the same instances come back, and `gateway.policy == GatewayPolicy.DEFAULT` for all three call types (R1 AC2)
  - GIVEN the production container and a live `MockWebServer` on `127.0.0.1:<port>`
    WHEN `gateway.fetchPage("http://127.0.0.1:<port>/")` is called
    THEN it returns `ADDRESS_REFUSED` and the server records 0 requests (RK3; AD8)
- **Tests:**
  - `` `container gateway is the same instance across reads`() ``
  - `` `gateway client is built once and shared across reads`() ``
  - `` `production gateway uses the default policy`() ``
  - `` `production container refuses a loopback url with no request recorded`() ``
  - File: `app/src/test/java/ie/pantry/di/AppContainerTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.di.AppContainerTest'`

### - [ ] T16: `INTERNET` permission, cleartext flag and `ManifestPolicyTest`

- **Requirement:** R10
- **Description:** Add `<uses-permission android:name="android.permission.INTERNET" />` and `android:usesCleartextTraffic="true"` on `<application>` (design Q1); change `ManifestPolicyTest` to its Q1 form (AD14), keeping the backup assertion.
- **Files:**
  - Modify: `app/src/main/AndroidManifest.xml`
  - Modify: `app/src/test/java/ie/pantry/ManifestPolicyTest.kt`
- **Dependencies:** T1, T11
- **Parallel:** Yes (with T12–T15, T17) — disjoint files; the permission lands once the orchestrator that uses it is complete (design Implementation Sequence step 7 needs step 6, whose production code ends at T11)
- **Acceptance Criteria:**
  - GIVEN the debug-merged manifest under Robolectric
    WHEN its requested permissions, flags and components are read
    THEN the only `android.permission.*` entry is `INTERNET` (the androidx `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` stays allowed), `FLAG_ALLOW_BACKUP` is absent, `FLAG_USES_CLEARTEXT_TRAFFIC` is set, and no activity, service, receiver or provider class starts with `okhttp3.` (R10 AC1, AC2)
- **Tests:**
  - `` `debug manifest disallows backup`() `` — unchanged
  - `` `debug manifest requests only the INTERNET android permission`() `` — replaces `` `debug manifest requests no android permissions`() ``
  - `` `debug manifest allows cleartext traffic`() ``
  - `` `merged manifest declares no okhttp3 component`() ``
  - File: `app/src/test/java/ie/pantry/ManifestPolicyTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ManifestPolicyTest'`

### - [ ] T17: `SingleCallSiteTest`: single call site, public surface, R9 source checks (characterisation)

- **Requirement:** R1, R9
- **Description:** Verification/characterisation task: create `SingleCallSiteTest` (design row) using `RepoPaths.repoRoot()` and reflection over main sources already final at T15, with KDoc recording the static check's blind spot (`[DEF-06]`) and how AD1's `String`-free constructor and T14 close it. No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4, and the mutation check under Verification shows the class can fail.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/RepoPaths.kt` — repository-root resolution
  - Read: `app/src/main/java/ie/pantry/di/AppContainer.kt` — must call `create()`, never the constructor
  - Create: `app/src/test/java/ie/pantry/data/gateway/SingleCallSiteTest.kt`
- **Dependencies:** T12, T13, T14, T15
- **Parallel:** Yes (with T16) — new test file only
- **Acceptance Criteria:**
  - GIVEN every `.kt` file under `app/src/main/java/`
    WHEN it is read
    THEN only files under `ie/pantry/data/gateway/` contain `import okhttp3.`; none contains `HttpURLConnection`, `openConnection`, `openStream` or `java.net.http`; `GatewaySeams(` appears only in `GatewaySeams.kt`; and `ExternalDataGateway(` appears only in `ExternalDataGateway.kt` (R1 AC3; RK3)
  - GIVEN the explicit list of Kotlin-public declarations in `ie.pantry.data.gateway`: `ExternalDataGateway` and `ExternalDataGateway.Companion`, `GatewayResult`, `GatewayResult.Fetched`, `GatewayResult.Failed`, `FetchedBody`, `BodyEncoding`, `CallType`, `GatewayException`, `GatewayException.Category`, and `GatewayPolicy` with its companion (the top-level functions `gatewayError`, `gatewayFailure`, `isTransient` and `buildGatewayClient` are `internal` and are not inspected)
    WHEN only these fixed classes' JVM-public methods and fields are inspected by reflection, skipping members whose JVM name contains `$` (Kotlin `internal` members of public classes, such as `getClient$app_debug`)
    THEN exactly three public methods of `ExternalDataGateway` take a trailing `Continuation` (`fetchPage`, `fetchImage`, `lookupNutrition`), and no inspected method or field signature mentions an `okhttp3` type (R1 AC1). Kotlin-`internal` declarations are excluded even though they compile to JVM-public members: `GatewaySeams` (`getDns()`, `getNutritionEndpoint()`, `getInterceptor()`, `getEventListener()`), `TlsOverride`, `buildGatewayClient` (returns `OkHttpClient`), `TargetFilteringDns`, `GuardedSocketFactory`, `GuardedSocket`, `RawByteCapInterceptor`, `CappedSource`, `ConnectionTargetPolicy`, `TargetVerdict` **[ASSUMPTION — `internal`, like the `ConnectionTargetPolicy` that returns it]**, `HopOutcome`, `NutritionQuery` and the two `IOException` markers; their `okhttp3` use is confined to the gateway package by the `import okhttp3.` source check above. **[ASSUMPTION — `GatewayPolicy` is Kotlin-public: design FC2/DM7 give it no visibility modifier. Its fields are `Duration`, `Long`, `Int` and `List<Duration>`, so it carries no `okhttp3` type either way.]**
  - GIVEN the gateway main sources
    WHEN they are read
    THEN none contains `Log.`, `println`, `printStackTrace`, `HttpLoggingInterceptor` or `String.format`, and no `throw`, `require(`, `check(` or `error(` argument contains `$`; where a message argument is present it is a `$`-free string literal, while a bare rethrow (`throw e`) and a no-argument exception constructor are allowed (R9 AC4). The check matches raw text, so comments in the gateway main sources must not quote any forbidden token
- **Tests:**
  - `` `only gateway package files import okhttp3`() ``
  - `` `no main source uses another http api`() ``
  - `` `gateway seams and the gateway constructor are reached only from their own files`() ``
  - `` `the gateway exposes exactly three public suspend operations`() ``
  - `` `no public signature in the gateway package mentions an okhttp3 type`() ``
  - `` `gateway sources have no logging and no interpolated exception messages`() ``
  - File: `app/src/test/java/ie/pantry/data/gateway/SingleCallSiteTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.SingleCallSiteTest'`; `grep -rln 'import okhttp3\.' app/src/main/java | grep -v '/data/gateway/' || echo clean` prints `clean`. Mutation check (`SingleCallSiteTest`): temporarily add `println("x")` to `ExternalDataGateway.kt`; `gateway sources have no logging and no interpolated exception messages` fails; revert and re-run green.

## Phase 7: Closeout (step 8)

### - [ ] T18: Feature closeout: F4 filter, full suite, lint, merged-manifest checks

- **Requirement:** R1–R10
- **Description:** Run the spec's Commands, compare the release merged manifest against T1's baseline, and confirm no out-of-scope file changed.
- **Files:**
  - Read: `.toolchain/f4-baseline/release-AndroidManifest.xml` — T1's pre-F4 release manifest
  - Read: `.toolchain/f4-baseline/base-commit.txt` — T1's baseline commit
- **Dependencies:** T1–T17
- **Parallel:** No
- **Acceptance Criteria:**
  - GIVEN the finished feature
    WHEN the F4 filter, the full unit suite and `lintDebug` run
    THEN all pass with no F1, F2 or F3 regression (spec Success Criteria)
  - GIVEN the merged release manifest and T1's baseline, each normalised by deleting the `<uses-permission android:name="android.permission.INTERNET" />` element and the `android:usesCleartextTraffic="true"` attribute by regex and then stripping all whitespace and `>` characters
    WHEN the two normalised texts are diffed
    THEN they are identical, so every activity, service, receiver and provider block, including `android:exported`, is unchanged, and the merged release manifest has exactly one `android.permission` entry (R10 AC1)
  - GIVEN the baseline commit recorded by T1 (with F3 committed at it)
    WHEN `PantryDatabase`, entities, `app/schemas/`, `app/src/main/assets/` and every F2/F3 source are diffed against it and checked for uncommitted or untracked changes
    THEN nothing changed (spec Project Structure)
- **Tests:** none — runs the existing suites and static checks; see Verification
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.*' --tests 'ie.pantry.ManifestPolicyTest' --tests 'ie.pantry.data.db.RestartDurabilityTest' --tests 'ie.pantry.di.AppContainerTest'`; `./gradlew :app:testDebugUnitTest`; `./gradlew lintDebug`; with `M=app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` and `B=.toolchain/f4-baseline/release-AndroidManifest.xml`: `./gradlew :app:processReleaseMainManifest && grep -c 'uses-permission android:name="android.permission' "$M"` prints `1`; with `norm() { sed -E 's#<uses-permission android:name="android\.permission\.INTERNET"[[:space:]]*/>##; s#android:usesCleartextTraffic="true"##' "$1" | tr -d ' \t\r\n>'; }`, `diff <(norm "$B") <(norm "$M")` prints nothing; `git diff --quiet "$(cat .toolchain/f4-baseline/base-commit.txt)" -- app/schemas app/src/main/assets app/src/main/java/ie/pantry/data/db app/src/main/java/ie/pantry/data/reference app/src/main/java/ie/pantry/data/thumbnail app/src/main/java/ie/pantry/domain` exits 0, and `git status --porcelain -- app/schemas app/src/main/assets app/src/main/java/ie/pantry/data/db app/src/main/java/ie/pantry/data/reference app/src/main/java/ie/pantry/data/thumbnail app/src/main/java/ie/pantry/domain` prints nothing; `grep -rln 'import okhttp3\.' app/src/main/java | grep -v '/data/gateway/' || echo clean` prints `clean`. Manual, repeatable: record each spec Success Criteria line, ticked, with the passing test that proves it, as a checked list in T18's completion notes in `03_tasks.md`.

## Implementation Order

1. T1 — the developer commits F3 first (Q2) so the base commit and release-manifest baseline are clean before any file under `app/` is created; T1 unblocks every other task.
2. T2, T3, T4, T7 — T2 (highest range-arithmetic risk, `[DEF-05]`) and T3 (the CFC-4 surface) are pure Kotlin needing only T1; T4 needs only T1 and T7 needs T1 and T3; T4 precedes transport because `GatewaySeams.PRODUCTION` references `NutritionQuery.ENDPOINT`. **[ASSUMPTION — `NutritionQuery` moves ahead of design step 6 for this reason.]**
3. T5 — socket guard and Dns filter, proved with no Dns in the loop before any orchestration (DR9).
4. T6 — seams, cap source and the fully configured client.
5. T8 — the orchestrator core: validation order, hop bridge, one attempt, failure mapping and direct-URL refusals; T15 can be done any time after it.
6. T10 → T9 → T11 — retry and cancellation first (AD7, the most uncertain part; its `[DEF-13]` at-cap-after-503 case needs only T6's `CappedSource`), then the cap fast path, then redirects, so cancellation and timing are proven before redirects as design step 6 requires; all three edit `ExternalDataGateway.kt`, so they are sequential. T16 can be done any time after T11 (design step 7 follows the completed orchestrator).
7. T12, T13 — order-independent characterisation tasks over a complete orchestrator.
8. T14, then T17 — characterisation tasks; T14 needs every failure path, and T17 follows T14 (so any main-source fix T14 makes lands before the static checks) and needs every main source final (including T15's `AppContainer`).
9. T18 — closeout after everything.

## Implementation Deviations

> Phase-4 minor-deviation ledger — populated by the triage gate's minor path (`SKILL.md` § "Mid-implementation discovery"). Append-only during Phase 4; resolved at the Final-Check completion gate. Leave empty until a deviation is logged.

| Date | Task | What spec/design said | What was actually done | Why | Classification | Backport status |
|------|------|-----------------------|------------------------|-----|----------------|-----------------|
| 2026-10-04 | T8 | Design Testing Strategy, `GatewayFailureMappingTest` row: stalled-body timeout fixture uses `throttleBody(1024, 1, HOURS)` | Planned: `throttleBody(1024, 3, SECONDS)` | `MockWebServer.shutdown()` waits 5 s for its threads and fails with an `IOException` on an hour-long sleep; 3 s still exceeds `FAST`'s 1 s read timeout | minor | pending — design backport at the Phase 4 completion gate |

## TDD Exceptions

> Phase-4 TDD-cycle-skip log — appended by the calling Claude during Phase 4 when the test-first red→green→refactor cycle is skipped for a code-stack task. Append-only during Phase 4; `Resolution`-column updates (`pending` → `accepted` or `remediate`) are resolved at the Final-Check completion gate. Leave empty until a skip is logged. **Code stacks (python/java/kotlin) only.** Not applicable for generic-profile tasks. (A code task that genuinely needs *no* test at all is not a skip — use the `**Tests:** none — <reason>` override instead.)

| Date | Task | Skip Reason | Resolution |
|------|------|-------------|------------|

## Open Questions

> All questions must be resolved before proceeding to implementation.

- [x] Q1: DR1 — `com.squareup.okhttp3:mockwebserver:4.12.0` is not in `.toolchain/gradle-home` (only `okhttp` 4.12.0 is), and this devcontainer cannot reach Maven Central, so T1's offline resolution check cannot pass here. How will it be provisioned before T1: fetched on a networked machine into `.toolchain/gradle-home` / `.toolchain/m2`, or T1 run on a networked machine?
  - **Resolution:** Developer confirmed (2026-10-04): pre-fetch `mockwebserver:4.12.0` into `.toolchain/gradle-home` outside this task list, before T1 runs; T1 then verifies with `--offline`.
- [x] Q2: T1's clean-tree check (`git status --porcelain -- app gradle docs` prints nothing) cannot pass while F3 is uncommitted, and only the developer runs mutating git commands (`CLAUDE.md`). Who commits F3 before T1, and does T1 wait for it?
  - **Resolution:** Developer confirmed (2026-10-04): they will commit F3 and the F3/F4 specs before implementation. T1's precondition halts until the clean-tree check passes; T1 never commits. T1 pre-start item 1 then records `base-commit.txt`, which T18's protected-path checks rely on.

## Panel Review


<!-- Terminal Phase: must NOT contain a ### Deferred dispositions sub-section. archive_pass.py rejects --terminal archives with Deferred rows; validate_blueprint.py hard-fails for PLAN.md specifically. -->
<!-- Populated by the skill across panel-review passes. archive_pass.py manages
     Trajectory and Sealed dispositions automatically; the synthesizer populates
     Latest pass detail per pass.

     This is the last artifact phase before implementation; concerns cannot be
     deferred forward. Disposition vocabulary: Addressed / Sealed / Accepted as
     risk / User input needed / Halt and re-scope. Sealed and Accepted as risk
     must include "Defense: <reason>" in Notes. Severity tags in Latest pass
     detail are bracketed: [HIGH] / [MED] / [LOW], optionally [REGRESSION].

     See SKILL.md "Panel Review section format" for the normative spec. -->

### Trajectory

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes                           |
|------|------------|-------|-------------|-----------|----------|--------|---------------------------------|
| 1    | 2026-10-04 | 2     | 0           | 22        | 0        | 5      | tags=d0u0c0                     |
| 2    | 2026-10-04 | 2     | 0           | 25        | 0        | 5      | tags=d0u0c0                     |
| 3    | 2026-10-04 | 0     | 1           | 17        | 0        | 9      | converged (0 HIGH); tags=d0u0c0 |

### Sealed dispositions

- `[SEAL-01]` **T11 packs seven blocks and eleven tests into one task** (pass 1, accepted-as-risk) — Defense: the redirect chain is serial over one file, so a split adds overhead, and moving the initial-URL cases to T8 already shrinks it.
- `[SEAL-02]` **T14 depends on test-only tasks T12 and T13** (pass 1, accepted-as-risk) — Defense: the edge only serialises work and guards against production fixes made by T12 and T13.
- `[SEAL-03]` **T2 and T3 source-import checks differ in form** (pass 1, accepted-as-risk) — Defense: the difference is harmless because T17's SingleCallSiteTest enforces the import rule.
- `[SEAL-04]` **T18 repeats the okhttp3 import grep that T17 asserts** (pass 1, accepted-as-risk) — Defense: the duplicate is a cheap independent closeout check.
- `[SEAL-05]` **T4 is a small object that could fold into T6** (pass 1, accepted-as-risk) — Defense: keeping T4 separate preserves its own red step.
- `[SEAL-06]` **T13 depends on T11 but needs only T8** (pass 2, accepted-as-risk) — Defense: the T14 edge and serial ordering already cover it, and the extra edge only delays a test task that runs after the orchestrator is complete.
- `[SEAL-07]` **AD7 late-resume ISE claim is false for kotlinx.coroutines** (pass 2, accepted-as-risk) — Defense: no task asserts the ISE; the wording sits in the approved design and is a rationale nuance with no effect on any task.
- `[SEAL-08]` **500 ms wall-clock cancel threshold may flake** (pass 2, accepted-as-risk) — Defense: the threshold is a ceiling, 500 ms against a 1 s read timeout, and the test only needs cancellation observed before the read timeout fires.
- `[SEAL-09]` **T3 invariant tests in GatewayErrorHygieneTest** (pass 2, accepted-as-risk) — Defense: a separate file adds a test class for three small tests, and the file still verifies the typed-error surface.
- `[SEAL-10]` **T18 manifest normalisation assumes one-line INTERNET element** (pass 2, accepted-as-risk) — Defense: the spec Commands grep makes the same assumption and the check fails loudly rather than silently passing.
- `[SEAL-11]` **answerEvery may be redundant with setFailFast** (pass 3, accepted-as-risk) — Defense: critic and delivery-manager confirmed answerEvery's dispatch and peek override is correct and consistent across T7, T8 and T10; revisit after T1 if setFailFast feeds peek.
- `[SEAL-12]` **T9 mutation check duplicates its two red tests** (pass 3, accepted-as-risk) — Defense: the mutation check names a test the removed pre-check really breaks, so the redundancy is harmless.
- `[SEAL-13]` **Mutation checks on T12, T13, T14 and T17 need manual edit…** (pass 3, accepted-as-risk) — Defense: characterisation tasks have no red step, so these checks are the only non-vacuity evidence; they can be trimmed at Phase 4 discretion.
- `[SEAL-14]` **T8 is the largest task** (pass 3, accepted-as-risk) — Defense: T8 is the gating task, and splitting it would add structural churn at the terminal phase.
- `[SEAL-15]` **The [::1] bind-and-Assume rule is not needed to prove…** (pass 3, accepted-as-risk) — Defense: the rule follows DEF-18 as routed; revisit only if that routing is reopened.
- `[SEAL-16]` **T16 manifest component test duplicates T18's diff** (pass 3, accepted-as-risk) — Defense: the test is design-mandated by AD14.
- `[SEAL-17]` **Three UnknownHostException cases could use a one-line Dns…** (pass 3, accepted-as-risk) — Defense: a minor helper simplification that is not worth a structural change at the terminal phase.
- `[SEAL-18]` **T8 NO_RESPONSE timing test is a subset of T10's** (pass 3, accepted-as-risk) — Defense: T8's test is the spec-required R6 AC1 per-attempt bound at its red time, and the duplicate I/O is bounded.
- `[SEAL-19]` **T18 manual Success Criteria record has no automated check** (pass 3, accepted-as-risk) — Defense: a manual completion record is this phase's convention.

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to implementation
- **Content Hash:** `89020785804c8374`
- **Hash basis:** v2
