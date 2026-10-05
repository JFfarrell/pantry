# Tasks: Recipe Import Pipeline

**Spec:** `specs/F5-recipe-import-pipeline/01_spec.md`
**Design:** `specs/F5-recipe-import-pipeline/02_design.md`

## Summary

| Task | Description | Requirement | Dependencies | Parallel | Status |
|------|-------------|-------------|--------------|----------|--------|
| T1 | Build setup: pre-F5 baselines, jsoup / kotlinx-serialization-json / lifecycle-viewmodel-compose aliases, offline resolution | R1, R2, R4 | None | No | Not Started |
| T2 | `ImportUrl`: URL picking, validation and `ValidatedUrl` | R1 | T1 | Yes (with T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T22, T26) | Not Started |
| T3 | Draft and result model: `ImportDraft`, `DraftValue`, `DraftImage`, `ImportResult`, `ImportException` and the CFC-4 re-wrap [CFC-2] [CFC-4] | R6, R9, R5, R2 | T1 | Yes (with T2, T8, T14, T26) | Not Started |
| T4 | `RecipeValues`: ISO duration and yield rules [CFC-2] | R6 | T3 | Yes (with T2, T5, T6, T7, T8, T9, T14, T16, T26) | Not Started |
| T5 | `CappedInputStream` and `openBounded`: the streaming decompressed cap and markup-density bound | R3 | T3 | Yes (with T2, T4, T8, T14, T16, T26) | Not Started |
| T6 | `BoundedHtmlReader`: media type, header charset and bounded parse | R2, R3 | T5 | Yes (with T2, T4, T8, T14, T16, T26) | Not Started |
| T7 | `JsonDepth`: string-aware JSON-LD depth pre-scan | R3 | T6 | Yes (with T2, T4, T8, T14, T16, T26) | Not Started |
| T8 | `LineText` normalisation and the bounded iterative DOM walk | R4 | T1 | Yes (with T2, T3, T4, T5, T6, T7, T14, T16, T26) | Not Started |
| T9 | JSON-LD block scan: 20-block limit, size and depth skips, `Recipe` node discovery | R3, R4 | T3, T7, T8 | Yes (with T2, T4, T14, T16, T26) | Not Started |
| T10 | JSON-LD `Recipe` mapping: instructions forms, output caps, times, yield, image string | R4, R6, R7 | T4, T9 | Yes (with T2, T14, T16, T26) | Not Started |
| T11 | `MicrodataExtractor`: microdata then RDFa | R4, R6 | T10 | Yes (with T2, T14, T15, T16, T26) | Not Started |
| T12 | `HeuristicExtractor`: heading-and-list rules and heuristic title | R4, R6 | T11 | Yes (with T2, T14, T15, T16, T26) | Not Started |
| T13 | `RecipeExtractor`: tier order, qualify rule, title carry-over, extractor-level decline | R4, R5 | T6, T12 | Yes (with T2, T14, T15, T16, T26) | Not Started |
| T14 | Harvest snapshots, `snapshots.tsv` and candidate `F5_HARVESTED` corpus rows (user action) [CFC-1] | R10 | T1 | Yes (with T2–T13, T15–T21, T23–T31) | Not Started |
| T15 | `ImageCandidateSelector`: three-tier candidate and URL resolution | R7 | T10 | Yes (with T2, T11, T12, T13, T14, T16, T17, T22, T23, T24, T25, T26, T27, T29, T30, T31) | Not Started |
| T16 | `ImageValidator` and `ImageFixtures.pngHeaderOnly`: bounds-first decode | R7 | T3 | Yes (with T2, T4–T15, T22, T26) | Not Started |
| T17 | `RecipeImporter` page path: fetch, media type, encoding, parse, extract, draft | R2, R4, R6 | T2, T13, T16 | Yes (with T14, T15, T22, T26) | Not Started |
| T18 | `RecipeImporter` image phase: select, fetch, bounded open, validate | R7, R3 | T15, T16, T17 | Yes (with T14, T22, T23, T24, T25, T26, T27, T29, T30, T31) | Not Started |
| T19 | Importer-level content bounds (characterisation) | R3 | T18 | Yes (with T14, T20, T22–T31) | Not Started |
| T20 | Importer-level provenance, decline and draft-or-error (characterisation) [CFC-2] | R4, R5, R6 | T18 | Yes (with T14, T19, T22–T31) | Not Started |
| T21 | Sentinel hygiene on every import failure path (characterisation) [CFC-4] | R9 | T19, T20 | Yes (with T14, T22–T31) | Not Started |
| T22 | `HarvestedCorpusTest`: harvested rows bound to snapshots (characterisation) [CFC-1] | R10 | T13, T14 | Yes (with T2, T15–T21, T23–T31) | Not Started |
| T23 | `ImportUiState` and `ImportViewModel` core: import, results, Cancel, Retry, Back, field edits | R1, R2, R5 | T2, T17 | Yes (with T14, T15, T18, T19, T20, T21, T22, T26, T29) | Not Started |
| T24 | `ImportViewModel` share events and notices | R1, R11 | T23 | Yes (with T14, T15, T18, T19, T20, T21, T22, T25, T26, T29) | Not Started |
| T25 | `ImportMessages`, string resources and `ImportMessagesTest` | R2, R5, R9 | T23 | Yes (with T14, T15, T18, T19, T20, T21, T22, T24, T26, T28, T29) | Not Started |
| T26 | `ShareIntents`: shared-text read and decline chooser | R1, R8 | T1 | Yes (with T2–T25, T28, T29) | Not Started |
| T27 | `ImportScreen` composables: states, live regions, field polish, 48dp, large font | R11, R5, R8 | T24, T25, T26 | Yes (with T14, T15, T18, T19, T20, T21, T22, T28, T29) | Not Started |
| T28 | `ImportFlowTest`: ViewModel over the real importer (characterisation) | R1, R2, R5 | T18, T24 | Yes (with T14, T19, T20, T21, T22, T25, T26, T27, T29, T30, T31) | Not Started |
| T29 | `AppContainer` wiring: `clock` and one `RecipeImporter` (Ask First test edit) | R2 | T17 | Yes (with T14, T15, T18–T28) | Not Started |
| T30 | `MainActivity` shows the import screen; `singleTask` and empty `taskAffinity` (Ask First test edit) | R1, R5 | T27, T29 | Yes (with T14, T15, T18, T19, T20, T21, T22, T28) | Not Started |
| T31 | Share intake: `SEND` intent-filter, `MainActivity` share reading, activity-level screen tests | R1, R2, R5, R8, R11 | T30 | Yes (with T14, T15, T18, T19, T20, T21, T22, T28) | Not Started |
| T32 | Source guards: `SingleCallSiteTest` (Ask First) and `ImportSourceGuardTest` (characterisation) [CFC-1] [CFC-4] | R2, R9, R10 | T21, T22, T28, T31 | No | Not Started |
| T33 | Feature closeout: spec Commands, release-manifest diff, protected paths, device check | R1–R11 | T1–T32 | No | Not Started |

> **Parallel** means order-independent: `Yes (with Tn)` says the task has no dependency path to or from Tn, so either may be done first. Tasks are implemented one at a time, never concurrently, and every task must leave the test source set compiling (`./gradlew :app:compileDebugUnitTestKotlin` passes) before it is ticked. Where tasks share a file (for example `ExtractionTiersTest.kt` across T8–T13), they are on one dependency chain.

**Rules for every task:**

- Main sources under `ie.pantry.recipeimport` and `ie.pantry.ui.recipeimport` contain no `Log.`, `println`, `printStackTrace` or `Instant.now(`, and every `throw`, `require(`, `check(` and `error(` message is a `$`-free literal (AD16; enforced by T32). `SingleCallSiteTest` scans comments, so F5 KDoc and comments must not quote `HttpURLConnection`, `openConnection`, `openStream`, `java.net.http`, `Jsoup.connect` or `org.jsoup.helper.HttpConnection` (design `[SEAL-11]`).
- Every type AD8 lists overrides `toString()` to print counts, sizes, the tier and enum names only. Each task that introduces such a type tests its `toString()` with a sentinel.
- Every `catch (e: Exception)` in the import pipeline is preceded by `catch (e: CancellationException) { throw e }` (AD13; spec Always Do).
- Tests follow F1–F4 style: JUnit 4, `kotlin.test`, backtick-quoted names, `TestGateways.against(server)` for network tests, no sleeps (design Testing Strategy).

## Phase 1: Build Setup (FC9 build; design Implementation Sequence step 1)

### - [ ] T1: Build setup: pre-F5 baselines, jsoup / kotlinx-serialization-json / lifecycle-viewmodel-compose aliases, offline resolution

- **Requirement:** R1, R2, R4
- **Description:** Record the baselines T33 compares against, then add the catalog versions and aliases `libs.jsoup` (1.18.3), `libs.kotlinx.serialization.json` (1.6.3, design Q4) and `libs.androidx.lifecycle.viewmodel.compose` (2.8.7) with three `implementation` lines, and prove they resolve offline. No Kotlin serialization compiler plugin is added (design Dependencies).
- **Precondition (user action):** `git status --porcelain -- app gradle` prints nothing. Only the developer runs mutating git commands (`CLAUDE.md`), so T1 halts until it does.
- **Pre-start (writes only to gitignored `.toolchain/`):**
  1. `mkdir -p .toolchain/f5-baseline && git rev-parse HEAD > .toolchain/f5-baseline/base-commit.txt`
  2. `./gradlew :app:processReleaseMainManifest && cp app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml .toolchain/f5-baseline/release-AndroidManifest.xml`
- **Files:**
  - Read: `app/build.gradle.kts` — existing alias style and `maxHeapSize = "1g"`
  - Modify: `gradle/libs.versions.toml`
  - Modify: `app/build.gradle.kts`
- **Dependencies:** None
- **Parallel:** No — every other task depends on it
- **Acceptance Criteria:**
  - GIVEN the catalog and build script
    WHEN the debug unit-test runtime classpath is resolved offline
    THEN it contains `org.jsoup:jsoup:1.18.3`, `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3` and `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`, each added through a catalog alias (F1 R1), and nothing is marked `FAILED`
  - GIVEN the three new dependencies
    WHEN the existing suite runs
    THEN it stays green (no F1–F4 regression)
- **Tests:** none — build configuration only; the existing suite and the offline classpath check under Verification are the check
- **Verification:** with `D() { ./gradlew --offline :app:dependencies --configuration debugUnitTestRuntimeClasspath; }`: each of `D | grep -q 'org.jsoup:jsoup:1.18.3'`, `D | grep -q 'kotlinx-serialization-json:1.6.3'` and `D | grep -q 'lifecycle-viewmodel-compose:2.8.7'` exits 0 and `! D | grep -q FAILED` exits 0; `grep -c 'implementation(libs.jsoup)\|implementation(libs.kotlinx.serialization.json)\|implementation(libs.androidx.lifecycle.viewmodel.compose)' app/build.gradle.kts` prints `3`; `test -s .toolchain/f5-baseline/base-commit.txt && test -s .toolchain/f5-baseline/release-AndroidManifest.xml` exits 0; `./gradlew :app:testDebugUnitTest` passes.

## Phase 2: Pure Models (FC1, FC2, FC3, FC5 `RecipeValues`; step 2)

### - [ ] T2: `ImportUrl`: URL picking, validation and `ValidatedUrl`

- **Requirement:** R1
- **Description:** Create FC1 / I1 in pure Kotlin per AD11: `ImportUrl.pick`, `ImportUrl.check`, `SCAN_LIMIT = 2048`, `PickedUrl`, `UrlCheck`, `InvalidReason` and `ValidatedUrl` (internal constructor), with a single linear pass and running bracket counters (I1 contract).
- **Files:**
  - Create: `app/src/main/java/ie/pantry/recipeimport/ImportUrl.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/ImportUrlTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T22, T26) — new files only
- **Acceptance Criteria:**
  - GIVEN `"  https://example.ie/stew  "`
    WHEN `check` is called
    THEN it is `Valid` with value exactly `https://example.ie/stew` (R1 AC1, unit side)
  - GIVEN `ftp://example.ie/a`, `intent://x#Intent;end`, `content://x/y`, `javascript:alert(1)`, `www.example.ie/stew`, `   `, `https://` and `https://user:pw@example.ie/a`
    WHEN `check` is called on each
    THEN each is `Invalid` (`NO_URL`, `NO_HOST` or `USERINFO` as AD11 assigns) and none is completed with a guessed scheme (R1 AC4)
  - GIVEN `(see https://example.ie/stew/).`, `https://example.ie/stew,` and `Try this https://example.ie/stew so good`
    WHEN `pick` is called
    THEN it returns `https://example.ie/stew/`, `https://example.ie/stew` and `https://example.ie/stew`; `xhttps://a`, `url=https://a`, `ftp://x/?u=https://evil` and `intent://` fallback text give no match; `HTTPS://example.ie/stew` and `<https://example.ie/stew>` match; `https://example.ie/Foo_(bar)` keeps its bracket; `'https://example.ie/stew'` ends at the closing `'` while `https://example.ie/grandma's-stew` keeps its `'` (R1 AC5; `[SEAL-18]`)
  - GIVEN a URL whose run ends exactly at index 2048 or runs past it, and a link that starts at index 2049
    WHEN `pick` and `check` are called
    THEN `pick` returns the full run with `overLimit = true`, `check` gives `Invalid(OVER_LIMIT)` for both, and the late link gives `pick == null` and `Invalid(NO_URL)` (R1 AC5; AD11)
  - GIVEN a 1 MB hostile text of many trailing `)` and `]`
    WHEN `pick` runs on N and 2N inputs
    THEN the 2N run takes under 4× the N run's time and finishes within a few seconds (I1 linear contract)
- **Tests:**
  - `` `check trims surrounding whitespace and returns the validated url`() ``
  - `` `check rejects every R1 rejection input before any fetch`() `` — the eight inputs, with expected reasons
  - `` `a scheme less input is rejected not prefixed`() ``
  - `` `pick returns the first url at a boundary and drops trailing punctuation`() `` — the three spec examples
  - `` `pick ignores urls that do not start at a boundary`() ``
  - `` `pick matches an upper case scheme and an angle bracketed url`() ``
  - `` `pick keeps a balanced closing bracket and drops an unbalanced one`() ``
  - `` `an apostrophe ends the url only when a quote opened it`() ``
  - `` `userinfo is an at sign before the first path query fragment or backslash`() `` — `https://example.ie/a@b` and `https://example.ie?x=@y` are `Valid`
  - `` `the picker takes the first match and does not skip past a userinfo url`() ``
  - `` `a url reaching the scan limit is over limit for pick and check`() ``
  - `` `a link starting after the scan limit is not found`() ``
  - `` `hostile bracket text is scanned in linear time`() ``
  - `` `url intake types print no url`() `` — `ValidatedUrl`, `PickedUrl`, `UrlCheck.Valid` with `Sentinels.URL` (AD8)
  - File: `app/src/test/java/ie/pantry/recipeimport/ImportUrlTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ImportUrlTest'`

### - [ ] T3: Draft and result model: `ImportDraft`, `DraftValue`, `DraftImage`, `ImportResult`, `ImportException` and the CFC-4 re-wrap [CFC-2] [CFC-4]

- **Requirement:** R6, R9, R5, R2
- **Description:** Create FC3 (DM1–DM4, `Completeness`) and FC2 (DM5, DM6; AD1): `ImportException` with an internal `String`-free constructor and `init` invariants, `importError`, `fetchFailure`, `importFailure` (F4's five rules over cause chain and suppressed with a visited set) and the `ContentTooLargeException` marker. `ImportErrorHygieneTest` runs under Robolectric with `@GraphicsMode(NATIVE)` from the start, because T21 adds image-decode paths to it.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/gateway/GatewayException.kt` — `Category`, `statusCode`, and the `gatewayFailure` KDoc rules (a)–(e)
  - Read: `app/src/test/java/ie/pantry/testutil/Sentinels.kt` — `assertNoSentinel()`, `Sentinels.all`
  - Create: `app/src/main/java/ie/pantry/recipeimport/ImportDraft.kt`
  - Create: `app/src/main/java/ie/pantry/recipeimport/ImportResult.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/ImportErrorHygieneTest.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/DraftProvenanceTest.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/DeclineTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T2, T8, T14, T26) — new files only
- **Acceptance Criteria:**
  - GIVEN a jsoup `ValidationException`, a `SerializationException` and a `ZipException`, each with `SENTINEL` in its message
    WHEN `importFailure` re-wraps each
    THEN the kind is `UNREADABLE_CONTENT` by type, `causeType` is the class simple name, `cause == null`, `suppressed` is empty, the stack frames equal the cause's, nothing is logged, and `assertNoSentinel()` passes (R9 AC3) [CFC-4]
  - GIVEN a `ContentTooLargeException` at the top, wrapped in `org.jsoup.UncheckedIOException`, or in a suppressed list, and a cause cycle
    WHEN `importFailure` runs
    THEN the first three give `CONTENT_TOO_LARGE` with `causeType == null`, and the cycle terminates
  - GIVEN `ImportException`'s constructor and fields
    WHEN inspected by reflection
    THEN no constructor parameter is a `String`, and `message` matches `Import failure: kind=… gateway=… status=… cause=…` built from those fields only; `init` rejects a gateway category without `FETCH_FAILED` (and the reverse) and a status code without `HTTP_STATUS`, with constant messages [CFC-4]
  - GIVEN `ImportDraft.kt`'s property and constructor-parameter declarations (KDoc, comments and `?:` excluded)
    WHEN they are scanned
    THEN no declared type ends in `?` (R6 AC6) [CFC-2]
  - GIVEN a draft with title, ingredients and method, and each of the five partial shapes (ingredients; title and ingredients; ingredients and method; method; title and method)
    WHEN `completeness` is read
    THEN the first is `Complete` and each partial names exactly what was found, including the method-only structured draft (R5 AC3)
- **Tests:**
  - `` `import exception message holds only enum names a status and a class name`() ``
  - `` `import exception has no string constructor parameter`() ``
  - `` `gateway category is present exactly when the kind is FETCH_FAILED`() ``
  - `` `status code is present exactly when the category is HTTP_STATUS`() ``
  - `` `fetchFailure copies category status and stack frames without a cause`() ``
  - `` `importFailure finds the too large marker in the chain and suppressed exceptions`() ``
  - `` `importFailure re-wraps jsoup serialization and zip exceptions by type`() `` — five-point rule, `assertNoSentinel()`
  - `` `importFailure terminates on a cause cycle`() ``
  - `` `draft types print no content`() `` — `ImportDraft`, `DraftImage.Validated`, `DraftValue.Stated` holding sentinels
  - File: `app/src/test/java/ie/pantry/recipeimport/ImportErrorHygieneTest.kt`
  - `` `import draft declares no nullable property`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/DraftProvenanceTest.kt`
  - `` `a draft with title ingredients and method is complete`() ``
  - `` `each partial draft shape names what was found`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/DeclineTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ImportErrorHygieneTest' --tests 'ie.pantry.recipeimport.DraftProvenanceTest' --tests 'ie.pantry.recipeimport.DeclineTest'`

### - [ ] T4: `RecipeValues`: ISO duration and yield rules [CFC-2]

- **Requirement:** R6
- **Description:** Create `RecipeValues.minutes(iso)`, `cookingMinutes(total: String?, cook: String?)` (`totalTime` else `cookTime`, each through `minutes`), `servings(value: JsonElement?)` and `servings(text: String?)` per AD10 (pinned regex, half-up rounding, `Int` range), returning `DraftValue`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/recipeimport/ImportDraft.kt` — `DraftValue`
  - Create: `app/src/main/java/ie/pantry/recipeimport/RecipeValues.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/DraftProvenanceTest.kt`
- **Dependencies:** T3
- **Parallel:** Yes (with T2, T5, T6, T7, T8, T9, T14, T16, T26) — T5–T7 touch only reader files
- **Acceptance Criteria:**
  - GIVEN `totalTime` `PT1H15M`, and `cookTime` `PT40M` with no `totalTime`, with `totalTime` `PT0M` and with `totalTime` `about an hour`
    WHEN `cookingMinutes` is computed
    THEN it is 75, 40, 40 and 40 minutes (R6 AC3; Q9; `[SEAL-05]`)
  - GIVEN no time, `""`, `PT0M`, `about an hour`, `P1DT2H`, `PT29S`, `PT0.5S`, `PT1,5S` and `PT99999H`
    WHEN the cooking time is computed
    THEN each is `DraftValue.Absent`, never `Stated(0)`; `PT30S` gives 1, `PT90S` gives 2 and `pt30m` gives 30 (R6 AC4; AD10; design `[SEAL-06]`)
  - GIVEN yields `"4"`, `4`, `"Serves 4"`, `["4", "4 servings"]`, `"Serves: 4"`, `"servings: 4"`, `"4 portions"`, `1000` and `Int.MAX_VALUE`
    WHEN the yield is computed
    THEN each is `Stated` with that integer (R6 AC5)
  - GIVEN a missing yield, `"4-6"`, `"a crowd"`, `"2 dozen"`, `"12 cookies"`, `0`, `-1`, `4.0` and `Int.MAX_VALUE + 1`
    WHEN the yield is computed
    THEN each is `DraftValue.Absent` (R6 AC5; `[SEAL-06]`). **[ASSUMPTION — `4.0` is a JSON number that is not an integer, so AD10 gives absent.]**
- **Tests:**
  - `` `total time PT1H15M is 75 minutes`() ``
  - `` `cook time is used when total time is missing zero or unparseable`() ``
  - `` `missing empty zero day designated and unparseable durations are absent`() ``
  - `` `seconds round half up to whole minutes and zero is absent`() ``
  - `` `durations beyond the digit limits are absent and designators are case insensitive`() ``
  - `` `a bare integer or serves servings portions yield is stated`() ``
  - `` `ranges units words zero negatives and non integers give an absent yield`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/DraftProvenanceTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.DraftProvenanceTest'`

## Phase 3: Bounded Reader (FC4; step 3)

### - [ ] T5: `CappedInputStream` and `openBounded`: the streaming decompressed cap and markup-density bound

- **Requirement:** R3
- **Description:** Create `BoundedHtmlReader.kt` with `DECOMPRESSED_CAP_BYTES = 10 MiB`, the page `maxTagBytes = 200_000` constant, `CappedInputStream` (bounded delegate reads, `<` counting, `markSupported() == false`, counted `skip`, `ensureActive()` per read, exposed `produced`) and `openBounded(bytes, encoding, cap, maxTagBytes, job)` for `IDENTITY` and `GZIP` (AD7). The gzip helper and bomb generator its bomb test needs are built inline in `CappedInputStreamTest`; T6 later creates `ImportFixtures` with the shared versions.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/recipeimport/ImportResult.kt` — `ContentTooLargeException`
  - Read: `app/src/main/java/ie/pantry/data/gateway/GatewayResult.kt` — `BodyEncoding`
  - Create: `app/src/main/java/ie/pantry/recipeimport/BoundedHtmlReader.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/CappedInputStreamTest.kt`
- **Dependencies:** T3
- **Parallel:** Yes (with T2, T4, T8, T14, T16, T26) — new files only
- **Acceptance Criteria:**
  - GIVEN an instrumented delegate that counts bytes handed out, and a cap C
    WHEN the stream is read to exhaustion
    THEN at most `C + 1` bytes are pulled, each delegate read asks for at most `C + 1 − produced`, and crossing C throws `ContentTooLargeException` (R3 AC1)
  - GIVEN a gzip bomb whose inflated size would exhaust the 1 GiB test heap
    WHEN it is read through `openBounded`
    THEN it stops at the cap with no `OutOfMemoryError`, proving the cap is applied while streaming (R3 AC1)
  - GIVEN `maxTagBytes = 5` and `<` bytes split across reads, and separately `maxTagBytes = Long.MAX_VALUE`
    WHEN the stream is read
    THEN the sixth `<` throws the marker, and the unlimited stream never does (AD7)
  - GIVEN a cancelled job
    WHEN the next read happens
    THEN it throws `CancellationException` (AD13)
  - GIVEN a 10 MiB ordinary page read through `openBounded`
    WHEN T5 is complete
    THEN the retained heap of that read is recorded once as a manual observation in the task's completion note (design Q1; supplementary, no pass/fail threshold — the repeatable check is the density test in T13 and T28)
- **Tests:**
  - `` `bytes pulled from the delegate never exceed the cap plus one`() ``
  - `` `each read asks the delegate for at most the remaining allowance`() ``
  - `` `crossing the cap throws the content too large marker`() ``
  - `` `mark is not supported and skip is counted`() ``
  - `` `tag bytes are counted across reads and trip past the allowance`() ``
  - `` `an unlimited tag allowance never trips`() ``
  - `` `a cancelled job stops the next read`() ``
  - `` `a bomb that would exhaust the heap if inflated is stopped while streaming`() ``
  - `` `openBounded reads identity bytes and gunzips gzip bodies`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/CappedInputStreamTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.CappedInputStreamTest'`

### - [ ] T6: `BoundedHtmlReader`: media type, header charset and bounded parse

- **Requirement:** R2, R3
- **Description:** Add `BoundedHtmlReader.isHtml`, `headerCharset` (AD6: passed only when `Charset.isSupported`, `IllegalCharsetNameException` counts as false) and `parse(body, baseUri, cap, job, onStream)` with `Parser.htmlParser()` over `openBounded`; create `ImportFixtures` with `page(name)`, `gzip(bytes)` and `bomb(inflatedBytes)`, and the two Windows-1252 fixtures (each under 16 KiB).
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/recipeimport/BoundedHtmlReader.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/ImportFixtures.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/ContentBoundsTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/windows-1252-meta.html`, `…/pages/windows-1252-header.html`
- **Dependencies:** T5
- **Parallel:** Yes (with T2, T4, T8, T14, T16, T26) — edits only reader files
- **Acceptance Criteria:**
  - GIVEN `text/html`, `TEXT/HTML`, `text/html ; charset="utf-8"` and `application/xhtml+xml`, and `application/json`, `image/png`, `application/pdf`, `text/htmlx` and `null`
    WHEN `isHtml` is called
    THEN the first four are HTML and the rest are not (R2 AC3; Q5)
  - GIVEN a gzip bomb body
    WHEN `parse` runs with an `onStream` capture
    THEN it throws, `importFailure` of the throwable gives `CONTENT_TOO_LARGE`, and the captured `produced` is at most `cap + 8192` (R3 AC1)
  - GIVEN generated bodies of exactly 10 MiB and 10 MiB + 1, and of exactly 200 000 and 200 001 `<` bytes
    WHEN each is parsed
    THEN the first of each pair parses and the second gives `CONTENT_TOO_LARGE` (AD7; design Q1)
  - GIVEN Windows-1252 bytes declared by `<meta charset>`, by the header only, and a page under `charset=bogus`
    WHEN each is parsed
    THEN the first two read `½ tsp salt` and `crème fraîche`, and the third falls back to detection without an exception (AD6)
  - GIVEN a truncated gzip body
    WHEN it is parsed
    THEN `importFailure` of the throwable gives `UNREADABLE_CONTENT` (R2 AC5)
- **Tests:**
  - `` `isHtml accepts html and xhtml with any parameters and case`() ``
  - `` `isHtml rejects other types a near miss and a missing type`() ``
  - `` `a gzip bomb stops at the cap with produced at most cap plus one buffer`() ``
  - `` `a body of exactly the cap parses and one byte over is too large`() ``
  - `` `exactly 200000 tag bytes parse and one more is too large`() ``
  - `` `windows 1252 declared by meta decodes correctly`() ``
  - `` `windows 1252 declared only by the header decodes correctly`() ``
  - `` `an unknown header charset falls back to detection`() ``
  - `` `a truncated gzip body fails as unreadable content`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ContentBoundsTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ContentBoundsTest'`

### - [ ] T7: `JsonDepth`: string-aware JSON-LD depth pre-scan

- **Requirement:** R3
- **Description:** Create `JsonLdExtractor.kt` holding `JSON_LD_BLOCK_CAP_CHARS = 512 * 1024` and `JsonDepth.withinBound(text, bound = 64)` (AD2: containers only, outermost at depth 1, string- and escape-aware, stops at the first character reaching 65); add `ImportFixtures.deepJsonLd(depth, viaGraph)`.
- **Files:**
  - Create: `app/src/main/java/ie/pantry/recipeimport/JsonLdExtractor.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ImportFixtures.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ContentBoundsTest.kt`
- **Dependencies:** T6
- **Parallel:** Yes (with T2, T4, T8, T14, T16, T26) — no shared files
- **Acceptance Criteria:**
  - GIVEN blocks nested to depth 64 and 65, each also via `@graph`
    WHEN `withinBound` runs
    THEN 64 is within and 65 is not (R3 AC2; Q7)
  - GIVEN blocks nested to depth 10 000 and 1 000 000 (tested directly, no gateway; design `[SEAL-13]`, `[SEAL-14]`)
    WHEN `withinBound` runs
    THEN each returns false with no `StackOverflowError`
  - GIVEN `{"a":"[[[[…]]]]", "b":"\"{"}` where the brackets are inside strings or after escaped quotes
    WHEN `withinBound` runs
    THEN they are not counted
- **Tests:**
  - `` `depth 64 is within the bound and 65 is not`() ``
  - `` `graph nesting counts an object plus an array`() ``
  - `` `depths of ten thousand and one million are rejected without stack overflow`() ``
  - `` `brackets inside strings and after escaped quotes are not counted`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ContentBoundsTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ContentBoundsTest'`

## Phase 4: Extraction (FC5; step 4)

### - [ ] T8: `LineText` normalisation and the bounded iterative DOM walk

- **Requirement:** R4
- **Description:** Create `LineText.normalise(raw)` and `LineText.of(element)` (AD9: U+00A0 to space, ASCII whitespace collapse, trim; `<br>` and block boundaries add a space; returns null once raw text passes 4 096 characters), plus the shared iterative walk every FC5 DOM traversal uses: skips subtrees deeper than 512 (`html` at depth 1) and calls `job.ensureActive()` every 1 024 visited nodes. **[ASSUMPTION — design FC5 states the walk's rules but not its home; it lives in `LineText.kt` as an internal `BoundedDomWalk`.]**
- **Files:**
  - Create: `app/src/main/java/ie/pantry/recipeimport/LineText.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T2, T3, T4, T5, T6, T7, T14, T16, T26) — new files only
- **Acceptance Criteria:**
  - GIVEN `&nbsp;2&#160;tbsp\t <b>olive</b>\n oil ` and lines holding U+2009, U+202F, `•` and `-`
    WHEN they are extracted and normalised
    THEN the first is `2 tbsp olive oil`, the others keep those characters, and an all-whitespace line is dropped (R4 AC10; Q8) [CFC-1]
  - GIVEN an element whose raw text is exactly 4 096 characters, one of 4 097, and one of 4 090 visible characters padded by whitespace to 4 100 raw
    WHEN `LineText.of` runs
    THEN the first is kept and the other two give null (dropped, not truncated) (AD9; design `[SEAL-17]`)
  - GIVEN a chain of 510 nested `div`s under `body` (deepest at depth 512) and one of 511 (deepest at 513), and a 100 000-deep chain
    WHEN the walk runs
    THEN the depth-512 node is visited, the depth-513 node is not, and the deep chain completes with no `StackOverflowError` (FC5; design `[SEAL-15]`)
  - GIVEN a cancelled job and a 5 000-node page
    WHEN the walk runs
    THEN it throws `CancellationException` by the 1 024th node
- **Tests:**
  - `` `normalise folds nbsp collapses ascii whitespace and trims`() ``
  - `` `normalise keeps other unicode spaces and list markers`() ``
  - `` `element text decodes entities drops tags and breaks at br and block boundaries`() ``
  - `` `empty lines after normalisation are dropped`() ``
  - `` `a line whose raw text passes 4096 characters is dropped not truncated`() `` — includes the whitespace-padded case
  - `` `the walk visits depth 512 and skips depth 513`() ``
  - `` `the walk is iterative on a very deep chain`() ``
  - `` `the walk checks cancellation every 1024 nodes`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ExtractionTiersTest'`

### - [ ] T9: JSON-LD block scan: 20-block limit, size and depth skips, `Recipe` node discovery

- **Requirement:** R3, R4
- **Description:** Add `JsonLdExtractor.recipes(document): JsonLdScan` per AD2 and FC5: examine the first 20 `script` elements whose `type`, trimmed and case-insensitive, is `application/ld+json`, read with `Element.data()`; skip a block over 512 KiB, over depth 64, or failing `Json.parseToJsonElement` (`SerializationException` and `IllegalArgumentException` dropped), still counting it; walk the tree depth-bounded at 64 through arrays, objects and `@graph` for nodes whose `@type` is or includes `Recipe`; map each with a minimal `toRecipe` (title from `name`, lines from `recipeIngredient`) and drop the tree before the next block. Create `RecipeExtractor.kt` holding only `ExtractedRecipe` for now. **[ASSUMPTION — `ExtractedRecipe` lives in `RecipeExtractor.kt`; FC5 does not name its file.]**
- **Files:**
  - Read: `app/src/main/java/ie/pantry/recipeimport/LineText.kt` — `normalise`
  - Modify: `app/src/main/java/ie/pantry/recipeimport/JsonLdExtractor.kt`
  - Create: `app/src/main/java/ie/pantry/recipeimport/RecipeExtractor.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/jsonld-object.html`, `jsonld-array.html`, `jsonld-graph.html`, `jsonld-type-array.html`
- **Dependencies:** T3, T7, T8
- **Parallel:** Yes (with T2, T4, T14, T16, T26) — T4 touches only `RecipeValues.kt` and `DraftProvenanceTest.kt`
- **Acceptance Criteria:**
  - GIVEN a top-level `Recipe`, an array containing one, a `@graph` containing one, and an `@type` array including `"Recipe"`
    WHEN `recipes` runs
    THEN each yields one `ExtractedRecipe` with its title and lines (R4 AC1)
  - GIVEN a page whose first block is invalid JSON, one whose first block is 512 KiB + 1 characters, and one whose first block is depth 65, each followed by a valid `Recipe` block
    WHEN `recipes` runs
    THEN the first block is skipped without an escaping exception and the second is read; a block of exactly 512 KiB is parsed (R3 AC2; R4 AC5; AD2)
  - GIVEN 21 blocks where only the 21st carries a `Recipe`, and 20 skipped blocks followed by a valid one
    WHEN `recipes` runs
    THEN neither `Recipe` is found (R3 AC3; skipped blocks count toward 20)
  - GIVEN a parsed `JsonElement` tree nested to depth 70 with a `Recipe` at depth 66
    WHEN the depth-bounded walk runs on it directly
    THEN that `Recipe` is not visited (R3 AC2 walk bound)
- **Tests:**
  - `` `a top level Recipe object is found`() ``
  - `` `a Recipe inside an array is found`() ``
  - `` `a Recipe inside a graph is found`() ``
  - `` `an at type array including Recipe is found`() ``
  - `` `an invalid block is skipped without an escaping exception`() ``
  - `` `a block over 512 KiB is skipped and still counts toward twenty`() ``
  - `` `an over deep block is skipped and the next block is read`() ``
  - `` `the twenty first block is never read`() ``
  - `` `the script type is matched trimmed and case insensitively`() ``
  - `` `the json ld walk does not visit nodes past depth 64`() ``
  - `` `json ld scan and extracted recipe print no content`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ExtractionTiersTest'`

### - [ ] T10: JSON-LD `Recipe` mapping: instructions forms, output caps, times, yield, image string

- **Requirement:** R4, R6, R7
- **Description:** Complete `toRecipe` per AD9, AD10 and AD12 tier 1: title and lines via `parseBodyFragment` only when the string holds `&` or `<`; `recipeIngredient` else `ingredients`; `recipeInstructions` as a string split on line breaks, an array of strings, `HowToStep`/`HowToDirection` (`text` else `name`), `HowToSection`/`ItemList` flattened with section names dropped (design Q2), `HowToTip` skipped; caps of 200 lines, 200 steps, 4 096 raw characters per item and 1 000 array elements; `RecipeValues.cookingMinutes(totalTime, cookTime)` and `servings` for `recipeYield`; and the raw image string kept per `Recipe` in `JsonLdScan` (string, first array element, `ImageObject` `url` else `contentUrl`, or a same-block `@id` reference followed one hop before the tree is dropped).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/recipeimport/RecipeValues.kt` — `minutes`, `servings`
  - Modify: `app/src/main/java/ie/pantry/recipeimport/JsonLdExtractor.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/jsonld-instructions.html`, `jsonld-image-id.html`
- **Dependencies:** T4, T9
- **Parallel:** Yes (with T2, T14, T16, T26) — no shared files
- **Acceptance Criteria:**
  - GIVEN `recipeInstructions` as a multi-line string, an array of strings, `HowToStep`s, `HowToDirection`s, and two `HowToSection`s and an `ItemList` holding steps and a `HowToTip`
    WHEN mapped
    THEN steps come out in document order, one per non-empty piece, with section names and the tip dropped (AD9; design Q2)
  - GIVEN 201 ingredient lines, a 4 097-character line, a 4 096-character line, a whitespace-padded 4 100-character raw string and an array of 1 001 elements whose first 1 000 are blank
    WHEN mapped
    THEN 200 lines are kept, the 4 097 and padded strings are dropped, the 4 096 string is kept, and the 1 001st element is never examined (AD9; design Q6)
  - GIVEN `"totalTime": "PT1H15M"` and `"recipeYield": "Serves 4"`
    WHEN mapped
    THEN cooking time is `Stated(75)` and servings `Stated(4)`; with neither, both are `Absent` (R6 AC1, AC3, AC5) [CFC-2]
  - GIVEN `image` as a string, an array, an `ImageObject` with `url`, one with only `contentUrl`, an `{"@id": "#img"}` reference to a node in the same block, and a reference to a node that itself is a reference
    WHEN mapped
    THEN the first four give their URL string, the `@id` resolves one hop, and the chained reference gives no image string (AD12)
- **Tests:**
  - `` `json ld title and lines are entity decoded tag free and normalised`() ``
  - `` `ingredients is used when recipeIngredient is missing`() ``
  - `` `a string recipeInstructions splits on line breaks`() ``
  - `` `step and section instruction forms flatten in order without section names`() ``
  - `` `HowToTip is skipped`() ``
  - `` `json ld times and yield are read through RecipeValues`() ``
  - `` `only the first 200 lines and 200 steps are kept`() ``
  - `` `a json ld string longer than 4096 raw characters is dropped`() ``
  - `` `only the first 1000 array elements are examined`() ``
  - `` `the image string comes from a string an array or an ImageObject`() ``
  - `` `an at id image reference is followed one hop within its block`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ExtractionTiersTest'`

### - [ ] T11: `MicrodataExtractor`: microdata then RDFa

- **Requirement:** R4, R6
- **Description:** Create `MicrodataExtractor.recipe(document)` per FC5: the first `itemscope` whose `itemtype` has a token ending `schema.org/Recipe` (`http`/`https`, case-insensitive), with each `itemprop` owned by its nearest enclosing `itemscope` tracked on the walk's own scope stack; else RDFa (`typeof` `Recipe` under a `schema.org` `vocab`, or `schema:Recipe`); value rules for `meta`, `time`, `link`/`a` and others; properties `name`, `recipeIngredient` else `ingredients`, `recipeInstructions` (each `li`, else each `p`, else whole text), `totalTime`, `cookTime`, `recipeYield`; `image` never read.
- **Files:**
  - Create: `app/src/main/java/ie/pantry/recipeimport/MicrodataExtractor.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/microdata.html`, `rdfa-vocab.html`, `rdfa-prefix.html`, `microdata-and-rdfa.html`
- **Dependencies:** T10
- **Parallel:** Yes (with T2, T14, T15, T16, T26) — no shared files
- **Acceptance Criteria:**
  - GIVEN a microdata-only page with a nested `NutritionInformation` carrying its own `name`
    WHEN `recipe` runs
    THEN title, lines and method come from the `Recipe` scope and the nested `name` is not the title (R4 AC2)
  - GIVEN microdata `totalTime` `PT1H`, a page with only `cookTime`, a `recipeYield` of `4 servings`, and one of `4-6`
    WHEN `recipe` runs
    THEN cooking time and servings follow R6's rules, and the unparseable values are `Absent` (R4 AC2) [CFC-2]
  - GIVEN an RDFa page using `vocab="https://schema.org/"` and one using `schema:` prefixes with `meta`, `time` and `link` values
    WHEN `recipe` runs
    THEN each yields a recipe through the same rules (R4 AC3)
  - GIVEN a page carrying both a microdata `Recipe` and an RDFa `Recipe`
    WHEN `recipe` runs
    THEN the microdata one is used
- **Tests:**
  - `` `a microdata Recipe gives title lines and method`() ``
  - `` `an itemprop belongs to its nearest enclosing itemscope`() ``
  - `` `microdata times and yield follow the R6 rules`() ``
  - `` `microdata instructions read li then p then the whole text`() ``
  - `` `rdfa under a schema org vocab gives a recipe`() ``
  - `` `rdfa with schema prefixed properties gives a recipe`() ``
  - `` `rdfa meta time and link values follow the value rules`() ``
  - `` `rdfa is tried only when there is no microdata Recipe`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ExtractionTiersTest'`

### - [ ] T12: `HeuristicExtractor`: heading-and-list rules and heuristic title

- **Requirement:** R4, R6
- **Description:** Create `HeuristicExtractor.recipe(document)` per FC5: `h1`–`h6` headings containing `ingredient` (case-insensitive) or one of the whole words `method`, `directions`, `instructions`, `steps`; the first following `ul`/`ol` (or first list descendant) before the next same-or-higher heading, else the run of `p`/`div`/`li` siblings; at least 2 non-empty items; an ingredient list is required; title from the first `h1`, else `og:title`, else `<title>`; never a time or yield.
- **Files:**
  - Create: `app/src/main/java/ie/pantry/recipeimport/HeuristicExtractor.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/heuristic.html`, `heuristic-runs.html`
- **Dependencies:** T11
- **Parallel:** Yes (with T2, T14, T15, T16, T26) — no shared files
- **Acceptance Criteria:**
  - GIVEN a page with an "Ingredients" heading followed by a `ul`, and one followed by a run of `p` elements
    WHEN `recipe` runs
    THEN the list items or paragraphs are the lines (R4 AC4, AC9)
  - GIVEN a list that continues past the next same-level heading, a one-item list, and a page with only a "Method" list
    WHEN `recipe` runs
    THEN the list stops at the heading, the one-item list does not qualify, and the method-only page gives null (R4 AC9)
  - GIVEN pages with an `h1`, with only `og:title`, and with only `<title>`
    WHEN `recipe` runs
    THEN the title comes from them in that order (R4 AC9)
  - GIVEN a heuristic page whose text states "Serves 4" and "Cook for 40 minutes"
    WHEN `recipe` runs
    THEN cooking time and servings are `Absent` (R6 AC4; Q9; `[SEAL-19]`) [CFC-2]
- **Tests:**
  - `` `an ingredient heading followed by a list gives its items`() ``
  - `` `a run of block siblings gives one item each`() ``
  - `` `the list stops at the next heading of the same or higher level`() ``
  - `` `fewer than two items is not an ingredient list`() ``
  - `` `method headings match whole words only`() ``
  - `` `a method alone does not make a heuristic recipe`() ``
  - `` `the heuristic title is the first h1 else og title else title`() ``
  - `` `the heuristic tier never states cooking time or yield`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ExtractionTiersTest'`

### - [ ] T13: `RecipeExtractor`: tier order, qualify rule, title carry-over, extractor-level decline

- **Requirement:** R4, R5
- **Description:** Add `Extraction` and `RecipeExtractor.extract(document, job)` per AD3: JSON-LD (first qualifying `Recipe` in walk order), then microdata/RDFa, then heuristic; a structured recipe qualifies with one line or step, a heuristic one with 2 or more items; the earliest stated structured title is carried to a lower tier; `ensureActive()` between tiers; null when nothing qualifies. Tests parse fixtures through `BoundedHtmlReader.parse`.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/recipeimport/RecipeExtractor.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/DeclineTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/three-tier.html`, `jsonld-lines-heuristic-method.html`, `structured-no-ingredients.html`, `carried-title.html`, `malformed.html`, `invalid-jsonld-with-microdata.html`, `nothing-extracted.html`, `parse-badly.html`
- **Dependencies:** T6, T12
- **Parallel:** Yes (with T2, T14, T15, T16, T26) — no shared files
- **Acceptance Criteria:**
  - GIVEN a page carrying JSON-LD, microdata and a heuristic list, and pages carrying only each lower tier
    WHEN `extract` runs
    THEN the tier is `JSON_LD`, `MICRODATA` or `HEURISTIC` respectively (R4 AC1–AC4)
  - GIVEN a page whose only JSON-LD block is invalid and which carries microdata
    WHEN `extract` runs
    THEN the tier is `MICRODATA` (R4 AC5)
  - GIVEN a JSON-LD `Recipe` with lines but no method on a page whose heuristic tier has a method
    WHEN `extract` runs
    THEN the method stays absent (R4 AC6)
  - GIVEN a title-only JSON-LD `Recipe` on a page with a heuristic ingredient list, and on a page without one
    WHEN `extract` runs
    THEN the first gives a heuristic extraction carrying the structured title, and the second gives null (R4 AC7, AC8, AC9; Q2)
  - GIVEN a malformed page with unclosed tags and a truncated JSON-LD block but a heuristic ingredient list
    WHEN `extract` runs
    THEN it gives a heuristic extraction (R4 AC11)
  - GIVEN a recipe placed under a subtree whose root is at depth 513, and one at depth 512 (`html` at 1, `body` at 2)
    WHEN `extract` runs
    THEN the first is skipped and extraction falls through, and the second is read (FC5; design Q6)
  - GIVEN a generated page of 99 995 `<b>x</b>` pairs inside `body` (199 990 `<` bytes, under the 200 000 bound, mixing elements and text nodes)
    WHEN it is parsed and extracted
    THEN it completes and its node count (elements plus text nodes) is under 400 000 (DR1; design `[SEAL-08]`, `[SEAL-12]`)
  - GIVEN the nothing-extracted page (a `<title>`, no structured data, no ingredient list) and the parse-badly page (a 2-or-more item ingredient list, no `h1`, no `og:title`, no `<title>`, no method)
    WHEN `extract` runs
    THEN the first gives null and the second a heuristic extraction with title and method absent (R5 AC1; `[SEAL-35]`)
- **Tests:**
  - `` `json ld wins over microdata and heuristic on one page`() ``
  - `` `each lower tier records its own tier`() ``
  - `` `invalid json ld with microdata gives a microdata extraction`() ``
  - `` `a partial structured recipe takes no field from a lower tier`() ``
  - `` `a title only structured recipe falls through to the first qualifying tier`() ``
  - `` `the earliest stated structured title is carried to the lower tier`() ``
  - `` `a heuristic title is used only when no structured tier stated one`() ``
  - `` `malformed html with a truncated json ld block gives a heuristic extraction`() ``
  - `` `a recipe below depth 512 is skipped and one at 512 is read`() ``
  - `` `a maximal density page parses with fewer than 400000 nodes`() ``
  - `` `extraction checks cancellation between tiers`() ``
  - `` `extraction prints no content`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ExtractionTiersTest.kt`
  - `` `the nothing extracted page gives no extraction`() ``
  - `` `a title only structured recipe with no heuristic list gives no extraction`() ``
  - `` `the parse badly page gives a heuristic extraction with no title or method`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/DeclineTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ExtractionTiersTest' --tests 'ie.pantry.recipeimport.DeclineTest'`

## Phase 5: Harvest Dry Run (FC10 harvest; step 5)

### - [ ] T14: Harvest snapshots, `snapshots.tsv` and candidate `F5_HARVESTED` corpus rows (user action) [CFC-1]

- **Requirement:** R10
- **Description:** Commit trimmed third-party page snapshots from at least 3 hosts with their index, and append at least 20 candidate `F5_HARVESTED` ingredient rows to F3's corpus, so F3 parser defects surface before the screen work (DR4, DR5). Line text is taken from the snapshot's ingredient data normalised per Q8; expected key and quantity are written by the developer from the source line, never from engine output (F3 RK6). T22 later proves each line is byte-identical to F5's extraction.
- **Precondition (user action):** the developer saves the pages on a networked machine (the devcontainer has no network; DR4), trims each per AD17 (remove `<script>` other than `application/ld+json`, `<style>`, `<svg>`, `<noscript>` and HTML comments; a first-line comment records the trim), keeps each at or under 1 MiB, and places it at AD17's path.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt` — the 9-column row format and `CorpusOrigin.F5_HARVESTED`
  - Create: `app/src/test/resources/recipeimport/harvest/snapshots.tsv` (a header row `source_url`, `retrieved`, `path`, tab-separated, then one row per snapshot; DM11)
  - Create: `app/src/test/resources/recipeimport/harvest/<host>/<slug>.html` (one per page, at least 3 hosts)
  - Modify: `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv`
- **Dependencies:** T1
- **Parallel:** Yes (with T2–T13, T15–T21, T23–T31) — test resources only
- **Acceptance Criteria:**
  - GIVEN the extended corpus and the shipped alias table
    WHEN F3's unmodified `ParseFidelityCorpusTest` runs
    THEN every `F5_HARVESTED` row parses to its expected key and quantity (R10 AC3) [CFC-1]. A failing row is an Ask First item (spec Boundaries): fix the parser or correct the row from the source line, never from engine output
  - GIVEN the corpus and the index
    WHEN they are counted
    THEN there are at least 20 `F5_HARVESTED` `INGREDIENT` rows from at least 3 distinct hosts, each with its source URL and retrieval date, and no line repeats a `HAND_TRANSCRIBED` line (R10 AC1)
- **Tests:** none — test-data task; F3's existing `ParseFidelityCorpusTest` checks the rows, and T22's `HarvestedCorpusTest` binds them to the snapshots
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.ParseFidelityCorpusTest'`; `awk -F'\t' '$1=="F5_HARVESTED" && $4=="INGREDIENT"' app/src/test/resources/ingredient/parse_fidelity_corpus.tsv | wc -l` prints at least `20`; `tail -n +2 app/src/test/resources/recipeimport/harvest/snapshots.tsv | cut -f1 | awk -F/ '{sub(/^www\./,"",$3); print $3}' | sort -u | wc -l` prints at least `3`; `find app/src/test/resources/recipeimport/harvest -name '*.html' -size +1024k` prints nothing.

## Phase 6: Image Selection and Validation (FC6; step 6)

### - [ ] T15: `ImageCandidateSelector`: three-tier candidate and URL resolution

- **Requirement:** R7
- **Description:** Create `ImageCandidateSelector.select(document, scan)` per AD12: tier 1 the first JSON-LD `Recipe` image string (resolved inside one guard via `URI(raw)`, absolute used as is, relative resolved against `document.baseUri()`, catching `URISyntaxException` and `IllegalArgumentException`); tier 2 the first non-blank `og:image`; tier 3 the largest declared `width`×`height` `img`, else the first; HTML candidates via `absUrl`; non-`http(s)` gives `NotHttp`. `ImageSelectionTest` runs under Robolectric `@GraphicsMode(NATIVE)` for the whole class (T18 adds decode paths). **[ASSUMPTION — FC6's `Candidate` lists `Url`, `NotHttp` and `None`, but AD12 and DM3 require `NoImage(BAD_URL)`; this task adds `Candidate.BadUrl` (see Q3).]**
- **Files:**
  - Read: `app/src/main/java/ie/pantry/recipeimport/JsonLdExtractor.kt` — `JsonLdScan` image strings
  - Create: `app/src/main/java/ie/pantry/recipeimport/ImageCandidateSelector.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/ImageSelectionTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/image-jsonld.html`, `image-og.html`, `image-inline.html`, `image-base-href.html`
- **Dependencies:** T10
- **Parallel:** Yes (with T2, T11, T12, T13, T14, T16, T17, T22, T23, T24, T25, T26, T27, T29, T30, T31) — new files only
- **Acceptance Criteria:**
  - GIVEN pages offering an image only at each tier, and a page offering all three
    WHEN `select` runs
    THEN exactly one candidate comes from the first tier with a hit, including when the JSON-LD `Recipe` is title-only (R7 AC1)
  - GIVEN inline `img`s with declared sizes 100×100 and 300×200, and inline `img`s with no sizes
    WHEN `select` runs
    THEN the 300×200 image is chosen in the first case and the first in document order in the second (R7 AC1; Q10)
  - GIVEN a relative candidate on a page with `<base href>` and on a page without one
    WHEN `select` runs
    THEN it resolves against the base, else against the supplied URL (R7 AC1)
  - GIVEN a `data:` candidate, a page whose only `og:image` is blank, and a JSON-LD image string holding an unencoded space and one holding `|`
    WHEN `select` runs
    THEN they give `NotHttp`, `None` and `BadUrl` respectively, never an exception (R7 AC6; AD12)
- **Tests:**
  - `` `json ld image forms are tier one candidates`() ``
  - `` `og image is used when no json ld image is stated`() ``
  - `` `the inline tier picks the largest declared img else the first`() ``
  - `` `a relative candidate resolves against base href else the page url`() ``
  - `` `a title only json ld Recipe still supplies the tier one image`() ``
  - `` `a data uri candidate is not http`() ``
  - `` `a page whose only og image is blank has no candidate`() ``
  - `` `a malformed json ld image string is a bad url`() ``
  - `` `a candidate url prints no url`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ImageSelectionTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ImageSelectionTest'`

### - [ ] T16: `ImageValidator` and `ImageFixtures.pngHeaderOnly`: bounds-first decode

- **Requirement:** R7
- **Description:** Create `ImageValidator(maxEdgePx = 512, decodeObserver)` per FC6 / I5 (bounds first; reject non-positive bounds or over 100 000 000 pixels; one decode at `computeSampleSize`; `OutOfMemoryError` or a null bitmap gives `NOT_AN_IMAGE`; recycle; return the original bytes) and add the test-only `ImageFixtures.pngHeaderOnly(width, height)`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/thumbnail/ThumbnailProcessor.kt` — `internal fun computeSampleSize`
  - Create: `app/src/main/java/ie/pantry/recipeimport/ImageValidator.kt`
  - Modify: `app/src/test/java/ie/pantry/testutil/ImageFixtures.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/ImageValidatorTest.kt`
- **Dependencies:** T3
- **Parallel:** Yes (with T2, T4–T15, T22, T26) — no shared files
- **Acceptance Criteria:**
  - GIVEN `ImageFixtures.jpeg` and `png` bytes
    WHEN validated
    THEN each is `Validated` with the same array and the bounds dimensions (I5)
  - GIVEN `ImageFixtures.garbage()` and HTML bytes
    WHEN validated
    THEN each is `NoImage(NOT_AN_IMAGE)` with no exception (R7 AC3)
  - GIVEN `ImageFixtures.pngBomb(9_000, 9_000)` under the 1 GiB test heap
    WHEN validated
    THEN `decodeObserver` reports sample size exactly 16 and a decoded longest edge of 562, below `2 × maxEdgePx` (R7 AC4)
  - GIVEN `pngHeaderOnly` at 10 000 × 10 000, 10 001 × 10 000 and 65 535 × 65 535
    WHEN validated
    THEN the first reaches the observer, and the other two give `NOT_AN_IMAGE` with the observer never called (FC6; design Q6)
- **Tests:**
  - `` `a valid jpeg and png are validated with the original bytes and bounds dimensions`() ``
  - `` `garbage and html bytes are not an image`() ``
  - `` `a 9000 pixel png bomb decodes at sample size 16 within the test heap`() ``
  - `` `a 10000 by 10000 header reaches the decode`() ``
  - `` `headers over 100 million pixels are rejected before any decode`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ImageValidatorTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ImageValidatorTest'`

## Phase 7: Importer (FC7; step 7)

### - [ ] T17: `RecipeImporter` page path: fetch, media type, encoding, parse, extract, draft

- **Requirement:** R2, R4, R6
- **Description:** Create `ImportPipeline`, `RecipeImporter` (internal constructor with `parseDispatcher`, `imageCheck`, `seams`; public `(gateway, clock)` constructor; `internal val gateway`; the public constructor passes `ImageValidator()::validate` as `imageCheck`, design I4), `ImportPhase` and `ImportSeams` (DM9), implementing FC7's pipeline up to draft assembly: `onPhase(FETCHING_PAGE)`, one `fetchPage`, `fetchedAt = clock.instant()`, media type, encoding, then on `parseDispatcher` `seams.onParse()`, `BoundedHtmlReader.parse`, `RecipeExtractor.extract` (null gives `NOT_RECOGNISED`), all inside FC7's `try`/`catch`. Until T18 the draft's image is `NoImage(NO_CANDIDATE)` and no selection runs; T17's tests do not assert the image. Add `ImportFixtures.enqueuePage` and `importer(server, clock, imageCheck, seams)`. Every branch on `GatewayResult` and `GatewayException.Category` here is an exhaustive `when` with no `else` (spec Always Do).
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/TestGateways.kt` — `against`, `server`, `urlOf`, `FAST`
  - Create: `app/src/main/java/ie/pantry/recipeimport/RecipeImporter.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ImportFixtures.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/RecipeImporterFetchTest.kt`
- **Dependencies:** T2, T13, T16
- **Parallel:** Yes (with T14, T15, T22, T26) — no shared files
- **Acceptance Criteria:**
  - GIVEN a valid URL served by `TestGateways.against(server)`
    WHEN an import runs
    THEN the server records exactly one request for the page path and the result is `Drafted` with `sourceUrl` equal to the passed URL (R2 AC1)
  - GIVEN fixtures giving `TIMEOUT` (`NO_RESPONSE`), `HTTP_STATUS` 404 and `ADDRESS_REFUSED` (a second, non-exempt server)
    WHEN an import runs against each
    THEN each returns `Failed(FETCH_FAILED)` with that category (and 404 as `statusCode`), never a hang or a thrown exception (R2 AC2)
  - GIVEN `200` responses typed `application/json`, `image/png`, `application/pdf` and with no `Content-Type`, and one typed `application/json` with `Content-Encoding: br`
    WHEN an import runs
    THEN each is `NOT_HTML` and the `onParse` seam count is 0 (R2 AC3, AC4)
  - GIVEN an HTML response with `Content-Encoding: br`
    WHEN an import runs
    THEN it is `UNSUPPORTED_ENCODING` with seam count 0 (R2 AC4)
  - GIVEN an HTML response with `Content-Encoding: gzip` whose body is truncated, invalid, or zero bytes long, and an empty identity body
    WHEN an import runs
    THEN the three gzip cases give `UNREADABLE_CONTENT` with no draft, and the empty identity body gives `NOT_RECOGNISED` (R2 AC5). **[ASSUMPTION — the design's "gzip of an empty body" is read as a zero-byte body labelled gzip, which `GZIPInputStream` rejects; a valid gzip of zero bytes would inflate to empty and be `NOT_RECOGNISED`.]**
  - GIVEN an import against a `NO_RESPONSE` server
    WHEN the calling coroutine is cancelled
    THEN the import ends with `CancellationException`, produces no `ImportResult`, and the gateway call is cancelled (R2 AC6, importer side)
- **Tests:**
  - `` `a valid url makes exactly one page request through the gateway`() ``
  - `` `timeout 404 and refused address give FETCH_FAILED with the gateway category`() ``
  - `` `json png pdf and a missing content type are NOT_HTML with no parse`() ``
  - `` `xhtml upper case and parameterised html are accepted and a near miss is not`() ``
  - `` `brotli html is UNSUPPORTED_ENCODING with no parse`() ``
  - `` `non html with brotli reports NOT_HTML`() ``
  - `` `truncated invalid and empty gzip bodies give UNREADABLE_CONTENT`() ``
  - `` `an empty identity body is NOT_RECOGNISED`() ``
  - `` `cancelling a page fetch that never responds cancels the gateway call`() ``
  - `` `the importer reports FETCHING_PAGE first`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/RecipeImporterFetchTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.RecipeImporterFetchTest'`

### - [ ] T18: `RecipeImporter` image phase: select, fetch, bounded open, validate

- **Requirement:** R7, R3
- **Description:** Replace T17's fixed `NO_CANDIDATE` with FC7's image phase: `ImageCandidateSelector.select` inside the parse block; let the `Document` go out of scope; for a `Url` candidate call `onPhase(FETCHING_PICTURE)` and `gateway.fetchImage`, then on `parseDispatcher` `openBounded(…, maxTagBytes = Long.MAX_VALUE)` with `seams.onStream` and `imageCheck`; map `Failed` to `FETCH_FAILED`, `OTHER`, a cap breach or corrupt gzip to `UNREADABLE`, `NotHttp` to `NOT_HTTP`, `BadUrl` to `BAD_URL`, `None` to `NO_CANDIDATE`. Every branch on `GatewayResult` and `GatewayException.Category` here is an exhaustive `when` with no `else` (spec Always Do).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/recipeimport/ImageCandidateSelector.kt` — `Candidate`
  - Read: `app/src/main/java/ie/pantry/recipeimport/ImageValidator.kt` — `validate`
  - Modify: `app/src/main/java/ie/pantry/recipeimport/RecipeImporter.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ImageSelectionTest.kt`
- **Dependencies:** T15, T16, T17
- **Parallel:** Yes (with T14, T22, T23, T24, T25, T26, T27, T29, T30, T31) — T23 onward only read `RecipeImporter.kt`'s `ImportPipeline` and `ImportPhase`, which T18 does not change
- **Acceptance Criteria:**
  - GIVEN a page whose candidate serves a valid JPEG, and a page with no candidate
    WHEN each is imported with the real `ImageValidator`
    THEN the first makes one image request and carries the same bytes as `Validated`, the second makes no image request and has `NO_CANDIDATE`, and neither creates a file under the test `filesDir` (R7 AC2)
  - GIVEN the nothing-extracted page
    WHEN it is imported
    THEN it is `NOT_RECOGNISED` and no image request is made (R7 AC2)
  - GIVEN a candidate serving HTML bytes labelled `image/jpeg`
    WHEN imported
    THEN exactly one image request is made, the `onParse` seam count is unchanged from the page parse, and the draft has `NOT_AN_IMAGE` (R7 AC3)
  - GIVEN an image delivered as a gzip bomb, one with `Content-Encoding: br`, one with corrupt gzip, and a valid JPEG labelled `text/plain`
    WHEN imported
    THEN the bomb's image stream `produced` is at most `cap + 8192` and it and the corrupt gzip give `UNREADABLE`, `br` gives `UNREADABLE` with no stream opened, the JPEG is `Validated`, and every draft is produced (R7 AC5)
  - GIVEN an image 404, a `data:` candidate and a malformed JSON-LD image string
    WHEN imported
    THEN the draft is produced with `FETCH_FAILED`, `NOT_HTTP` and `BAD_URL` respectively and no lower tier is tried (R7 AC6)
  - GIVEN a microdata page with an `itemprop="image"` and no other image
    WHEN imported
    THEN no image request is made (R4 AC2)
  - GIVEN `ImageFixtures.pngBomb(9_000, 9_000)` served through `TestGateways.FAST.copy(maxBodyBytes = 1 MiB)`
    WHEN imported
    THEN `decodeObserver` shows a sample size above 1 and decoded dimensions below `2 × maxEdgePx` (R7 AC4)
  - GIVEN a `RecipeImporter` built through its public `(gateway, clock)` constructor and a page whose candidate serves a valid JPEG
    WHEN imported
    THEN the draft's image is `Validated`, proving the shipped importer wires the real `ImageValidator` (design I4)
- **Tests:**
  - `` `a valid jpeg candidate is fetched once and carried as bytes with no file written`() ``
  - `` `a page with no candidate makes no image request`() ``
  - `` `a declined page makes no image request`() ``
  - `` `html bytes labelled as jpeg give no image after one request and no parse`() ``
  - `` `a gzip bomb image and a brotli image give no image with the draft produced`() ``
  - `` `corrupt gzip on the image gives no image`() ``
  - `` `a jpeg labelled text plain is accepted`() ``
  - `` `an image 404 a data uri and a bad url give no image with no lower tier tried`() ``
  - `` `a microdata itemprop image is ignored`() ``
  - `` `the png bomb through the importer decodes at a reduced sample size`() ``
  - `` `the importer reports FETCHING_PICTURE only when an image fetch starts`() ``
  - `` `an importer built through the public constructor validates a jpeg`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ImageSelectionTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ImageSelectionTest' --tests 'ie.pantry.recipeimport.RecipeImporterFetchTest'`

### - [ ] T19: Importer-level content bounds (characterisation)

- **Requirement:** R3
- **Description:** Verification/characterisation task: add `ContentBoundsTest`'s importer-level cases over production code complete at T18 (design `[SEAL-13]`, `[SEAL-14]`). No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4; the mutation check under Verification shows the class can fail. A defect found here is fixed in the main sources before the task is ticked.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/recipeimport/ImportFixtures.kt` — `bomb`, `gzip`, `deepJsonLd`, `importer`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ContentBoundsTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/twenty-one-blocks.html`, `scripts-handlers-iframes.html`
- **Dependencies:** T18
- **Parallel:** Yes (with T14, T20, T22–T31) — no shared files
- **Acceptance Criteria:**
  - GIVEN a gzip-bomb page under F4's raw cap
    WHEN imported
    THEN it ends in `CONTENT_TOO_LARGE`, no draft, and `onStream`'s `produced` is at most `cap + 8192` (R3 AC1)
  - GIVEN gzipped bodies of exactly 10 MiB and 10 MiB + 1, and of 200 000 and 200 001 `<` bytes
    WHEN imported
    THEN the first of each pair is not `CONTENT_TOO_LARGE` and the second is
  - GIVEN JSON-LD blocks at depth 64, 65 and 1 000, each also via `@graph`, each followed by microdata
    WHEN imported
    THEN depth 64 gives a `JSON_LD` draft, and 65 and 1 000 give a `MICRODATA` draft with no escaping exception (R3 AC2)
  - GIVEN a 512 KiB block, a 512 KiB + 1 block and a flat `[0,0,…]` block over 512 KiB, each gzip-delivered so the page stays under F4's raw cap
    WHEN imported
    THEN the first is parsed, the second skipped and counted toward 20, and the flat block skipped with no `OutOfMemoryError` (AD2)
  - GIVEN 21 JSON-LD blocks where only the 21st is a `Recipe`, on a page with a heuristic list
    WHEN imported
    THEN the draft tier is `HEURISTIC` (R3 AC3)
  - GIVEN a page with `<script>` elements, inline handlers and `<iframe>`s pointing at the server, plus one image
    WHEN imported
    THEN the server records exactly the page request plus one image request (R3 AC4)
  - GIVEN an image body of 300 000 `<` bytes delivered gzipped, and an `imageCheck` that accepts any bytes
    WHEN imported
    THEN the image is not refused, so the density bound applies to the page only (AD7; design `[SEAL-05]`)
- **Tests:**
  - `` `a gzip bomb page ends in CONTENT_TOO_LARGE with produced at most cap plus one buffer`() ``
  - `` `the size and density boundaries hold through the importer`() ``
  - `` `json ld at depth 64 is parsed and 65 and 1000 are skipped through the importer`() ``
  - `` `the 512 KiB block boundary holds and a flat over cap block is skipped`() ``
  - `` `a Recipe in the twenty first block is not examined through the importer`() ``
  - `` `page scripts handlers and iframes cause no request beyond the page and one image`() ``
  - `` `a low entropy image is not refused by the page density bound`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ContentBoundsTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ContentBoundsTest'`. Mutation check (`ContentBoundsTest`): temporarily pass `maxTagBytes = Long.MAX_VALUE` for the page in `BoundedHtmlReader.parse`; `the size and density boundaries hold through the importer` fails; revert and re-run green.

### - [ ] T20: Importer-level provenance, decline and draft-or-error (characterisation) [CFC-2]

- **Requirement:** R4, R5, R6
- **Description:** Verification/characterisation task over production code complete at T18: add `@Volatile` to `MutableClock`'s private `now` (design `[DEF-03]`), then the importer-level cases of `DraftProvenanceTest` and `DeclineTest`. No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4; the mutation check under Verification shows the classes can fail.
- **Files:**
  - Modify: `app/src/test/java/ie/pantry/testutil/MutableClock.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/DraftProvenanceTest.kt`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/DeclineTest.kt`
- **Dependencies:** T18
- **Parallel:** Yes (with T14, T19, T22–T31) — no shared files
- **Acceptance Criteria:**
  - GIVEN an import of the server URL that redirects once
    WHEN the draft is built
    THEN `sourceUrl` equals the URL the test passed, not the redirect target (R6 AC2; Q13)
  - GIVEN a `MutableClock` at instant A when the import starts, set to B by the page dispatcher just before it answers and to C by the image dispatcher
    WHEN the draft is built
    THEN `fetchedAt` equals B (R6 AC2; `[SEAL-15]`)
  - GIVEN a page stating a cooking time and one that does not
    WHEN imported
    THEN the first captures it and the second is `Absent` (R6 AC1) [CFC-2]
  - GIVEN every draft-producing fixture in T9–T13 and T15
    WHEN each draft is inspected
    THEN title, method, cooking time, servings and image are each a stated value or an explicit absent state, the line list is present, and no `Stated` method list is empty (R6 AC6) [CFC-2]
  - GIVEN a heuristic page stating a time and a yield in its text
    WHEN imported
    THEN cooking time and servings are `Absent` (R6 AC4)
  - GIVEN the nothing-extracted page and the parse-badly page
    WHEN imported
    THEN the first is `Failed(NOT_RECOGNISED)` with no image request, and the second is `Drafted` with `completeness` `Partial(foundTitle = false, foundIngredients = true, foundMethod = false)` and no error (R5 AC1)
  - GIVEN the timeout, 404, non-HTML and malformed-HTML fixtures and every draft-producing fixture
    WHEN each is imported
    THEN each ends in exactly one of `Drafted` or `Failed`, the malformed page gives a draft, and none throws (R4 AC11); the draft-producing fixtures are enumerated by listing `recipeimport/pages/` (excluding the nothing-extracted, not-HTML and failure fixtures) and the listing is asserted non-empty
- **Tests:**
  - `` `sourceUrl is the validated url not the redirect target`() ``
  - `` `fetchedAt is the clock instant when the page fetch returned`() ``
  - `` `a stated cooking time is captured and a missing one is absent`() ``
  - `` `every draft producing fixture has only stated or absent optional fields`() ``
  - `` `a heuristic page stating time and yield leaves both absent`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/DraftProvenanceTest.kt`
  - `` `the nothing extracted page is NOT_RECOGNISED with no image request`() ``
  - `` `the parse badly page is a partial draft with no error`() ``
  - `` `every fixture ends in exactly one of a draft or a typed error`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/DeclineTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.DraftProvenanceTest' --tests 'ie.pantry.recipeimport.DeclineTest'`. Mutation check (`DraftProvenanceTest`): temporarily read `fetchedAt` after the image fetch; `fetchedAt is the clock instant when the page fetch returned` fails; revert and re-run green. Mutation check (`DeclineTest`): temporarily return the heuristic extraction for a one-item list; `the nothing extracted page is NOT_RECOGNISED with no image request` fails; revert and re-run green.

### - [ ] T21: Sentinel hygiene on every import failure path (characterisation) [CFC-4]

- **Requirement:** R9
- **Description:** Verification/characterisation task: add the end-to-end half of `ImportErrorHygieneTest` over production code complete at T18. No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4; the mutation check under Verification shows the class can fail. The import of `Sentinels.URL` maps its host through `RecordingDns` to an exempt `TestGateways.httpsServer()` with `trustTestCert = true`, as F4's hygiene test does. Test method names use lowercase `sentinel` only, so no stack frame carries the case-sensitive marker. The rendered-message check is in T25 (see Q5).
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/TestGateways.kt` — `httpsServer`, `RecordingDns`, `answerEvery`
  - Modify: `app/src/test/java/ie/pantry/recipeimport/ImportErrorHygieneTest.kt`
  - Create: `app/src/test/resources/recipeimport/pages/sentinel.html`, `sentinel-invalid-jsonld.html`
- **Dependencies:** T19, T20
- **Parallel:** Yes (with T14, T22–T31) — no shared files
- **Acceptance Criteria:**
  - GIVEN an import of `Sentinels.URL`, a page whose HTML, JSON-LD and lines carry `Sentinels.TITLE` and `Sentinels.INGREDIENT`, an invalid JSON-LD block carrying `Sentinels.INGREDIENT`, a truncated gzip body and a non-image image body
    WHEN every failure path is driven (invalid URL, not HTML, unsupported encoding, unreadable content, content too large, not recognised, each gateway category reachable from a fixture, invalid JSON-LD, the depth bound, gzip corruption and image decode failure)
    THEN `assertNoSentinel()` passes on every `ImportException` and on every exception observed at the importer's boundary, each has `cause == null` and empty `suppressed`, and no image failure becomes an exception (R9 AC1, AC2) [CFC-4]
  - GIVEN `ValidatedUrl`, `PickedUrl`, `UrlCheck.Valid`, `UrlCheck.Invalid`, `ImportDraft`, `DraftImage.Validated`, `DraftValue.Stated`, `ExtractedRecipe`, `Extraction`, `JsonLdScan` and `Candidate.Url`, each built from sentinel content
    WHEN `toString()` is called
    THEN no sentinel appears (AD8) [CFC-4]
- **Tests:**
  - `` `every import failure path driven by sentinel fixtures carries no sentinel`() ``
  - `` `sentinel import errors have no cause and no suppressed exception`() ``
  - `` `every content holding pipeline type prints no sentinel`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ImportErrorHygieneTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.ImportErrorHygieneTest'`. Mutation check (`ImportErrorHygieneTest`): temporarily call `initCause(cause)` on the error built in `importFailure`; `sentinel import errors have no cause and no suppressed exception` and the `assertNoSentinel()` cases fail; revert and re-run green.

### - [ ] T22: `HarvestedCorpusTest`: harvested rows bound to snapshots (characterisation) [CFC-1]

- **Requirement:** R10
- **Description:** Verification/characterisation task: create `HarvestedCorpusTest` joining each `F5_HARVESTED` row to its snapshot through `snapshots.tsv` and running `BoundedHtmlReader.parse` and `RecipeExtractor.extract` over it (no gateway, no image request). No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4; the mutation check under Verification shows the class can fail. If a row's `line` differs from the extracted line, correct the row's `line` to the extracted text only when its expected values still follow from the source line; never edit expected values from engine output (Ask First, spec Boundaries).
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt` — `load()`, `CorpusOrigin`, `LineKind`
  - Create: `app/src/test/java/ie/pantry/recipeimport/HarvestedCorpusTest.kt`
  - Modify (only if a row needs correcting): `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv`, `app/src/test/resources/recipeimport/harvest/snapshots.tsv`
- **Dependencies:** T13, T14
- **Parallel:** Yes (with T2, T15–T21, T23–T31) — no shared files
- **Acceptance Criteria:**
  - GIVEN the default corpus resource
    WHEN `HarvestedCorpusTest` loads it
    THEN it holds at least 20 `F5_HARVESTED` `INGREDIENT` rows from at least 3 hosts, each with a source URL and retrieval date, and no `line` equals a `HAND_TRANSCRIBED` line (R10 AC1) [CFC-1]
  - GIVEN each `F5_HARVESTED` row and its snapshot
    WHEN F5's extraction runs over the snapshot
    THEN the row's `line` is byte-identical to an extracted line, has no tab, line break or leading `#`, and on failure the test output lists the snapshot's extracted lines (R10 AC2)
  - GIVEN the index and the snapshot files
    WHEN they are checked
    THEN every row has exactly one index entry by `(source_url, retrieved)`, every `path` is unique and exists, and each snapshot is at most 1 MiB, starts with the AD17 trim comment and decodes as UTF-8 (AD17)
  - GIVEN the extended corpus
    WHEN F3's unmodified `ParseFidelityCorpusTest` runs
    THEN it passes (R10 AC3) [CFC-1]
- **Tests:**
  - `` `the corpus holds at least twenty harvested ingredient rows from at least three hosts`() ``
  - `` `no harvested line repeats a hand transcribed line`() ``
  - `` `every harvested row has one index entry and a snapshot`() ``
  - `` `every harvested line is byte identical to a line extracted from its snapshot`() ``
  - `` `harvested lines have no tab line break or leading hash`() ``
  - `` `snapshots are small trimmed utf 8 files with unique paths`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/HarvestedCorpusTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.HarvestedCorpusTest' --tests 'ie.pantry.domain.ingredient.ParseFidelityCorpusTest'`. Mutation check (`HarvestedCorpusTest`): temporarily add a trailing space to one `F5_HARVESTED` row's `line`; `every harvested line is byte identical to a line extracted from its snapshot` fails; revert and re-run green.

## Phase 8: Screen and Wiring (FC8, FC9; step 8)

### - [ ] T23: `ImportUiState` and `ImportViewModel` core: import, results, Cancel, Retry, Back, field edits

- **Requirement:** R1, R2, R5
- **Description:** Create DM8 (`ImportUiState`, `failureOf(ImportException)` — the one function producing `Failure` from kind, gateway category and HTTP status, which T25's status tests feed through `ImportMessages` **[ASSUMPTION — name]**, `Screen`, `Failure`, `Notice`, `NoticeKind`, `focusSeq`) and `ImportViewModel(pipeline)` implementing every FC8 state-table cell except the two share columns (T24): `check` on Import, one `importJob` with a generation counter, each new job first `join()`ing the previous one in `withContext(NonCancellable)`, Cancel setting Input at once, results mapped to Drafted / Declined / Failed with Retry exactly for `TIMEOUT`, `CONNECTION_FAILED` and `HTTP_STATUS` 500–599, Back via `onBack()`, Try another link, Import another and field edits. Create `FakePipeline` (records each `ValidatedUrl`, suspends on a `CompletableDeferred` per call, has a non-cancellable mode). Tests run with `Dispatchers.setMain(StandardTestDispatcher())`, one test per cell (`[SEAL-59]`).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/recipeimport/RecipeImporter.kt` — `ImportPipeline`, `ImportPhase`
  - Create: `app/src/main/java/ie/pantry/ui/recipeimport/ImportUiState.kt`
  - Create: `app/src/main/java/ie/pantry/ui/recipeimport/ImportViewModel.kt`
  - Create: `app/src/test/java/ie/pantry/ui/recipeimport/FakePipeline.kt`
  - Create: `app/src/test/java/ie/pantry/ui/recipeimport/ImportViewModelTest.kt`
- **Dependencies:** T2, T17
- **Parallel:** Yes (with T14, T15, T18, T19, T20, T21, T22, T26, T29) — new files only
- **Acceptance Criteria:**
  - GIVEN the Input state with field `"  https://example.ie/stew  "`
    WHEN Import is tapped
    THEN `FakePipeline` records exactly `https://example.ie/stew`, the field shows it, and the screen is In progress (R1 AC1)
  - GIVEN each of the eight R1 rejection inputs, and text with no `http(s)` match
    WHEN Import is tapped
    THEN the field error is set, the field is unchanged, the pipeline records nothing, and no no-link notice appears (R1 AC4, AC5)
  - GIVEN an import running
    WHEN Import is tapped again
    THEN no second pipeline call is made (R1 AC6)
  - GIVEN the pipeline reports `FETCHING_PICTURE`
    WHEN the state is read
    THEN the screen is `InProgress(FETCHING_PICTURE)` (Q12; `[SEAL-12]`)
  - GIVEN results `Drafted`, `Failed(NOT_RECOGNISED)` and `Failed(FETCH_FAILED)` for `TIMEOUT`, `CONNECTION_FAILED`, `ADDRESS_REFUSED` and `HTTP_STATUS` 401, 403, 404, 499, 500, 599 and 600
    WHEN each completes
    THEN the screen is Drafted, Declined with the URL, or Failed, with Retry exactly for `TIMEOUT`, `CONNECTION_FAILED`, 500 and 599 (R2 AC2; Q6)
  - GIVEN a retryable Failed screen, and the Declined screen
    WHEN Retry is tapped
    THEN the pipeline is called once more with the same `ValidatedUrl` (R2 AC2; R5 AC5)
  - GIVEN an import in the page phase, in the picture phase, and a non-cancellable fake
    WHEN Cancel is tapped
    THEN the screen is Input with the URL in the field and the "import cancelled" notice before the fake completes, a late result is discarded, and a following Import starts its pipeline call only after the earlier job has ended (R2 AC6; AD13; AD18)
  - GIVEN Declined and Drafted screens
    WHEN Try another link or Import another is tapped
    THEN the screen is Input with an empty field, `focusSeq` increments, and Import another discards the draft (R5 AC2)
  - GIVEN each screen
    WHEN Back is pressed
    THEN `onBack()` is true in In progress, Failed and Declined, and false in Input and Drafted, and the resulting state matches the FC8 Back column: In progress → Input with the "import cancelled" notice, Failed → Input with the field kept, Declined → Input with the field set to the declined URL (FC8)
  - GIVEN Failed and Input screens
    WHEN the field is edited
    THEN the text is kept verbatim and the error, Retry and notice are cleared, returning to Input (FC8)
- **Tests:**
  - `` `import with a padded url calls the pipeline once with the trimmed url`() ``
  - `` `import with each rejected input sets the field error and calls nothing`() ``
  - `` `import with no url match shows the invalid url error not the no link notice`() ``
  - `` `a successful import tap replaces the field with the picked url`() ``
  - `` `a second import tap while running starts no second import`() ``
  - `` `the picture phase is shown when the pipeline reports it`() ``
  - `` `a drafted result shows the drafted screen holding the draft`() ``
  - `` `not recognised shows the declined screen with the url`() ``
  - `` `retry is offered exactly for timeout connection failure and 500 to 599`() ``
  - `` `retry from failed imports the same url once more`() ``
  - `` `retry from declined imports the same url once more`() ``
  - `` `cancel returns to input at once with the url and the cancelled notice`() ``
  - `` `cancel during the picture phase cancels the whole import`() ``
  - `` `a late result after cancel is discarded`() ``
  - `` `a new import waits for the cancelled job to end`() ``
  - `` `import from failed behaves as from input`() ``
  - `` `back is consumed in progress failed and declined only`() ``
  - `` `try another link clears the field and requests focus`() ``
  - `` `import another discards the draft clears the field and requests focus`() ``
  - `` `a field edit in failed returns to input and clears error and retry`() ``
  - `` `a field edit in input keeps the text verbatim and clears error and notice`() ``
  - `` `ui state types print no url or draft content`() `` — `Screen.Declined`, `Screen.Drafted`, `ImportUiState` with `Sentinels.URL` (AD8)
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ImportViewModelTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.recipeimport.ImportViewModelTest'`

### - [ ] T24: `ImportViewModel` share events and notices

- **Requirement:** R1, R11
- **Description:** Add `onShareReceived(text: String?)` per the FC8 state table's two share columns (`ImportUrl.pick` on arrival, never `check`; a link-carrying share replaces the screen with Input and cancels a running job; a no-link share only sets the notice) and the notice rules (AD14, AD15: `seq` increments on every notice; any next event clears it), one test per cell.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/ui/recipeimport/ImportViewModel.kt`
  - Modify: `app/src/test/java/ie/pantry/ui/recipeimport/ImportViewModelTest.kt`
- **Dependencies:** T23
- **Parallel:** Yes (with T14, T15, T18, T19, T20, T21, T22, T25, T26, T29) — T25 only reads `ImportUiState.kt`
- **Acceptance Criteria:**
  - GIVEN Input, Failed (with Retry), Declined and Drafted screens
    WHEN a share of `Try this https://example.ie/stew so good` arrives
    THEN the field is `https://example.ie/stew`, any error and Retry are cleared, any draft is discarded, the screen is Input, the notice is `LINK_RECEIVED`, and the pipeline records nothing (R1 AC2, AC6; `[SEAL-38]`, `[SEAL-50]`)
  - GIVEN an import in progress
    WHEN a link-carrying share arrives
    THEN the job is cancelled, the screen is Input with the picked URL, and the notice is the single `LINK_RECEIVED_IMPORT_CANCELLED` (R1 AC6; `[SEAL-57]`)
  - GIVEN each screen
    WHEN a share with no link, or with null text, arrives
    THEN the notice is `NO_LINK` and nothing else changes: a running import keeps running, the field, error and Retry stay, and the Declined and Drafted screens remain (R1 AC3, AC6; `[SEAL-54]`)
  - GIVEN a share carrying an over-limit URL or a userinfo URL
    WHEN it arrives and then Import is tapped
    THEN the field holds the full picked text with no field error on arrival, and Import sets the field error with no pipeline call (R1 AC5; `[SEAL-39]`, `[SEAL-56]`)
  - GIVEN the same share delivered twice
    WHEN each arrives
    THEN the notice `seq` increases each time (`[SEAL-52]`)
  - GIVEN a notice showing
    WHEN any next event in the state table occurs
    THEN the notice is cleared (AD14; `[SEAL-53]`)
- **Tests:**
  - `` `a link share in input fills the field and announces link received`() ``
  - `` `a link share in progress cancels the import and announces the combined notice`() ``
  - `` `a link share in failed clears the error and retry and returns to input`() ``
  - `` `a link share in declined returns to input with the picked url`() ``
  - `` `a link share in drafted discards the draft and returns to input`() ``
  - `` `a no link share in input shows the no link notice and keeps the field and error`() ``
  - `` `a no link share in progress keeps the import running`() ``
  - `` `a no link share in failed keeps the error and retry`() ``
  - `` `a no link share in declined or drafted changes nothing but the notice`() ``
  - `` `a missing shared text is treated as no link`() ``
  - `` `no share calls the pipeline`() ``
  - `` `an over limit or userinfo shared url fills the field and is rejected only at import`() ``
  - `` `a repeated share bumps the notice sequence`() ``
  - `` `the next event clears the notice`() ``
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ImportViewModelTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.recipeimport.ImportViewModelTest'`

### - [ ] T25: `ImportMessages`, string resources and `ImportMessagesTest`

- **Requirement:** R2, R5, R9
- **Description:** Add every Error Handling resource to `strings.xml` verbatim (design Q3), and `ImportMessages.of(screen)`, `of(notice)` and `ALL`, which also lists the control labels of the design's Labels row (Recipe link, Import, Cancel, Retry, Try another link, Import another, Share link, the first-use hint) so they fall under the distinctness and no-`http` checks below, with an exhaustive `when` over `Failure`, `NoticeKind` and `GatewayException.Category`. The rendered-message sentinel check from the design's `ImportErrorHygieneTest` row is placed here, beside `ALL`, so T21 need not wait on the UI (see Q5).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/ui/recipeimport/ImportUiState.kt` — `Failure`, `NoticeKind`
  - Create: `app/src/main/java/ie/pantry/ui/recipeimport/ImportMessages.kt`
  - Modify: `app/src/main/res/values/strings.xml`
  - Create: `app/src/test/java/ie/pantry/ui/recipeimport/ImportMessagesTest.kt`
- **Dependencies:** T23
- **Parallel:** Yes (with T14, T15, T18, T19, T20, T21, T22, T24, T26, T28, T29) — new files and `strings.xml` only
- **Acceptance Criteria:**
  - GIVEN `HTTP_STATUS` 401, 402, 403, 404, 499, 500, 599 and 600, and each other gateway category including `RESPONSE_TOO_LARGE`
    WHEN mapped
    THEN 401 and 403 give `import_error_http_denied`, 404 `import_error_http_404`, 500 and 599 `import_error_http_5xx`, the rest `import_error_http_other`, and each category its Error Handling row, with `RESPONSE_TOO_LARGE` showing `import_error_too_large` (R2 AC2; Q6; `[SEAL-16]`)
  - GIVEN every string in `ImportMessages.ALL`, including `import_notice_no_link`, the link-received notices, the cancelled notice, the status lines, the share explainer and the draft-produced notices
    WHEN compared
    THEN `import_error_not_recognised` and `import_error_unreadable` each differ from every other; `import_error_not_html` differs from `import_error_unsupported_encoding`; `import_notice_no_link` differs from `import_error_invalid_url`; `import_notice_recipe_found` differs from every `import_notice_partly_*` (R2 AC4, AC5; R5 AC3, AC4; `[SEAL-03]`, `[SEAL-60]`)
  - GIVEN every string in `ALL`
    WHEN rendered
    THEN none has a format argument, none contains a sentinel or `<`, and none contains `http` except `import_error_invalid_url`, which names `http://` and `https://` (R1 AC4; R9 AC4) [CFC-4]
  - GIVEN the `import_` names in `R.string`
    WHEN compared with `ALL`
    THEN they are the same set
- **Tests:**
  - `` `each failure and gateway category maps to its Q6 message`() ``
  - `` `http statuses map to their message at each boundary`() ``
  - `` `the decline message differs from every other import message`() ``
  - `` `the unreadable message differs from every other import message`() ``
  - `` `not html differs from unsupported encoding and no link differs from invalid url`() ``
  - `` `recipe found differs from every partly found notice`() ``
  - `` `no import message has a format argument a sentinel a tag or a url`() `` — the invalid-URL hint is the one `http` exception
  - `` `ALL lists every import string resource`() ``
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ImportMessagesTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.recipeimport.ImportMessagesTest'`

### - [ ] T26: `ShareIntents`: shared-text read and decline chooser

- **Requirement:** R1, R8
- **Description:** Create `ShareIntents.sharedText(intent)` (`ACTION_SEND` only; `getCharSequenceExtra(EXTRA_TEXT)?.toString()`; a `RuntimeException` while unparcelling counts as missing) and `chooserFor(url, context)` (`Intent.createChooser` over `ACTION_SEND` `text/plain` with only `EXTRA_TEXT`, titled the literal `Share recipe link` (a constant in `ShareIntents`: the chooser is built outside Compose, so T26 does not wait on T25's resources **[ASSUMPTION — the design's Labels row lists the title beside the on-screen labels]**), excluding `MainActivity` via `EXTRA_EXCLUDE_COMPONENTS`) per AD4, AD18 and I7.
- **Files:**
  - Create: `app/src/main/java/ie/pantry/ui/recipeimport/ShareIntents.kt`
  - Create: `app/src/test/java/ie/pantry/ui/recipeimport/ShareIntentsTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T2–T25, T28, T29) — new files only
- **Acceptance Criteria:**
  - GIVEN an `ACTION_SEND` whose `EXTRA_TEXT` is a `SpannableString`, one with no extra, an `ACTION_VIEW` with text, and an `Intent` subclass whose `getCharSequenceExtra` throws
    WHEN `sharedText` is called
    THEN the first gives the text and the rest give null with no exception (`[DEF-12]`; DR13)
  - GIVEN `https://example.ie/not-a-recipe`
    WHEN `chooserFor` is called
    THEN it is an `ACTION_CHOOSER` wrapping an `ACTION_SEND` of type `text/plain` whose only extra is `EXTRA_TEXT` equal to the URL, and `EXTRA_EXCLUDE_COMPONENTS` holds `MainActivity` (R8 AC1; AD18)
- **Tests:**
  - `` `shared text is read as a char sequence from a spannable extra`() ``
  - `` `a missing extra gives no text`() ``
  - `` `a non send action gives no text`() ``
  - `` `an unparcelling failure gives no text`() ``
  - `` `the chooser wraps a text plain send whose only extra is the url`() ``
  - `` `the chooser excludes MainActivity`() ``
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ShareIntentsTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.recipeimport.ShareIntentsTest'`

### - [ ] T27: `ImportScreen` composables: states, live regions, field polish, 48dp, large font

- **Requirement:** R11, R5, R8
- **Description:** Create `ImportScreen(state, actions)` per FC8 and AD15: a `Scaffold` with `app_name` once in the top bar over a `verticalScroll` column; the `OutlinedTextField` labelled "Recipe link" with `[DEF-02]`'s keyboard options and hint; polite live regions for the notice (blank for one frame via `withFrameNanos`, then the constant), status, errors, draft-produced notice, decline message and share explainer; focus requested when `focusSeq` changes; `BackHandler` enabled in In progress, Failed and Declined; Share link starts `ShareIntents.chooserFor` through `LocalContext`. Tests use `createAndroidComposeRule<ComponentActivity>()` under Robolectric with state fixtures. **[ASSUMPTION — the `BackHandler` enabled-state assertion is placed here, via the host activity's `onBackPressedDispatcher.hasEnabledCallbacks()` (design `[SEAL-07]`), rather than in `ShareTargetManifestTest` (see Q5).]**
- **Files:**
  - Read: `app/src/main/java/ie/pantry/ui/recipeimport/ImportMessages.kt` — resource ids
  - Read: `app/src/main/java/ie/pantry/ui/recipeimport/ShareIntents.kt` — `chooserFor`
  - Create: `app/src/main/java/ie/pantry/ui/recipeimport/ImportScreen.kt`
  - Create: `app/src/test/java/ie/pantry/ui/recipeimport/ImportScreenTest.kt`
- **Dependencies:** T24, T25, T26
- **Parallel:** Yes (with T14, T15, T18, T19, T20, T21, T22, T28, T29) — new files only
- **Acceptance Criteria:**
  - GIVEN the Input state with and without a field error
    WHEN inspected with Compose semantics
    THEN the field has the visible label "Recipe link", the hint as supporting text, the error text in its place when invalid, `ImeAction.Go`, and Go triggers Import (R11; `[DEF-02]`)
  - GIVEN the In progress (page and picture), Failed, Declined and Drafted states and each notice
    WHEN inspected
    THEN the status, every error, every notice, the draft-produced notice, the decline message and the share explainer are polite live regions, and the picture phase reads "Fetching the picture…" (R11; `[SEAL-12]`, `[SEAL-41]`)
  - GIVEN the same notice set twice with increasing `seq`
    WHEN the frames are advanced
    THEN the text is empty for one frame and then the constant each time (AD15; `[SEAL-52]`)
  - GIVEN each state
    WHEN its controls are inspected
    THEN Cancel, Retry, Try another link, Import another, Share link and Import each have a text label or content description and touch bounds of at least 48dp (R11; `[SEAL-29]`)
  - GIVEN `Complete` and the five partial shapes
    WHEN the Drafted state renders
    THEN it shows `import_notice_recipe_found` or the matching `import_notice_partly_*` text and no draft field (R5 AC3; Q1) [CFC-2]
  - GIVEN the Declined state
    WHEN it renders
    THEN it shows the not-recognised message, the share explainer, Retry, Try another link and Share link, and no field (R5 AC1; R8 AC1)
  - GIVEN `@Config(qualifiers = "land")` in one run and `RuntimeEnvironment.setFontScale(2.0f)` before content in another
    WHEN the field, error text, Cancel, Retry, Share link, Try another link and Import another are each scrolled to
    THEN each `assertIsDisplayed()`, its bounds lie inside the viewport, and no message is truncated (R11; `[SEAL-62]`)
  - GIVEN each state
    WHEN `onBackPressedDispatcher.hasEnabledCallbacks()` is read
    THEN it is true in In progress, Failed and Declined only
- **Tests:**
  - `` `the url field has a visible label hint and associated error text`() ``
  - `` `the url field uses go as its ime action and go imports`() ``
  - `` `status errors notices and decline text are polite live regions`() ``
  - `` `the picture phase status reads fetching the picture`() ``
  - `` `a repeated notice is blanked for one frame then set again`() ``
  - `` `every control has a label and a touch target of at least 48dp`() ``
  - `` `the draft produced notice matches each completeness shape`() ``
  - `` `the decline surface shows the explainer retry try another link and share link`() ``
  - `` `the field is editable in input and failed read only in progress and hidden otherwise`() ``
  - `` `controls stay reachable and unclipped in landscape`() ``
  - `` `controls stay reachable and unclipped at 200 percent font scale`() ``
  - `` `back handling is enabled only in progress failed and declined`() ``
  - `` `the app name is shown once`() ``
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ImportScreenTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.recipeimport.ImportScreenTest'`

### - [ ] T28: `ImportFlowTest`: ViewModel over the real importer (characterisation)

- **Requirement:** R1, R2, R5
- **Description:** Verification/characterisation task: create `ImportFlowTest` running the real `ImportViewModel` over a real `RecipeImporter` and `MockWebServer`, with `Dispatchers.setMain(StandardTestDispatcher())`, an injected `parseDispatcher`, and an `awaitState(timeout) { predicate }` helper in the same file that takes the installed test dispatcher as a parameter and loops `runCurrent()`, a state check and a short real `delay` inside a real-time `withTimeout` (design `[SEAL-18]`). No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4; the mutation check under Verification shows the class can fail.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/recipeimport/ImportFixtures.kt` — `importer`, `enqueuePage`
  - Create: `app/src/test/java/ie/pantry/ui/recipeimport/ImportFlowTest.kt`
- **Dependencies:** T18, T24
- **Parallel:** Yes (with T14, T19, T20, T21, T22, T25, T26, T27, T29, T30, T31) — new file only
- **Acceptance Criteria:**
  - GIVEN the field `"  <server url>  "`, and separately each of the eight R1 rejection inputs
    WHEN Import is tapped
    THEN the server records exactly one request for the trimmed URL in the first case, and zero requests for every rejection (R1 AC1, AC4)
  - GIVEN a page that answers three 503s and then a 200
    WHEN the first import fails with Retry offered and Retry is tapped
    THEN the request count rises by exactly 1 across Retry (R2 AC2; `[SEAL-24]`)
  - GIVEN the nothing-extracted page
    WHEN the decline surface shows and Retry is tapped
    THEN the request count rises by exactly 1 (R5 AC5)
  - GIVEN a parse stalled in `seams.onParse`, and a maximal-density page
    WHEN Cancel is tapped
    THEN the state is Input at once, the importer coroutine ends cancelled, the captured page stream's `produced` stops growing, and no `Failed` result is applied (AD13; design `[SEAL-04]`)
- **Tests:**
  - `` `a padded url is imported with exactly the trimmed url`() ``
  - `` `each rejected input makes no request`() ``
  - `` `retry after a transient failure makes exactly one new page request`() ``
  - `` `retry from the decline surface makes exactly one new page request`() ``
  - `` `cancel during a stalled parse stops the stream and applies no failure`() ``
  - `` `cancel during a maximal density parse returns to input at once`() ``
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ImportFlowTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.recipeimport.ImportFlowTest'`. Mutation check (`ImportFlowTest`): temporarily remove the `ensureActive()` call from `CappedInputStream.read`; `cancel during a stalled parse stops the stream and applies no failure` fails; revert and re-run green.

### - [ ] T29: `AppContainer` wiring: `clock` and one `RecipeImporter` (Ask First test edit)

- **Requirement:** R2
- **Description:** Add the last two constructor parameters `val clock: Clock = Clock.systemUTC()` and `val recipeImporter: RecipeImporter = RecipeImporter(gateway, clock)`; `production(app, clock)` passes its `clock`. Existing 3-, 4- and 5-argument call sites compile unchanged. The `AppContainerTest` edit is additive and Ask First (design Integration Points; see Q1).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/recipeimport/RecipeImporter.kt` — public `(gateway, clock)` constructor, `internal val gateway`
  - Modify: `app/src/main/java/ie/pantry/di/AppContainer.kt`
  - Modify: `app/src/test/java/ie/pantry/di/AppContainerTest.kt`
- **Dependencies:** T17
- **Parallel:** Yes (with T14, T15, T18–T28) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the application's production container
    WHEN `container.recipeImporter` is read twice
    THEN the same instance comes back and its `gateway` is `container.gateway` (R2 AC1; `[SEAL-37]`)
  - GIVEN the existing call sites in `AppContainerTest`, `BlockingReadPantryApplication` and `RecordingPantryApplication`
    WHEN the test source set compiles
    THEN none needed a change, and no existing assertion changed
- **Tests:**
  - `` `container recipe importer is the same instance across reads`() ``
  - `` `recipe importer uses the container gateway`() ``
  - File: `app/src/test/java/ie/pantry/di/AppContainerTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.di.AppContainerTest' --tests 'ie.pantry.ui.FirstPaintNotBlockedTest'`

### - [ ] T30: `MainActivity` shows the import screen; `singleTask` and empty `taskAffinity` (Ask First test edit)

- **Requirement:** R1, R5
- **Description:** Make `ImportScreen` `MainActivity`'s content with `ImportViewModel` built by `viewModels { viewModelFactory { … container.recipeImporter … } }` inside `PantryTheme` (FC9), and add `android:launchMode="singleTask"` and `android:taskAffinity=""` to `MainActivity` (AD4; design Q7). No share reading yet (T31). `PlaceholderScreen.kt` stays, unused **[ASSUMPTION — FC9]**. Rename F2's test and update its KDoc/wording that still says "the placeholder" to name the import screen; its assertion that `Pantry` is displayed is unchanged (Ask First; see Q1).
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/ui/MainActivity.kt`
  - Modify: `app/src/main/AndroidManifest.xml`
  - Modify: `app/src/test/java/ie/pantry/ui/FirstPaintNotBlockedTest.kt`
  - Create: `app/src/test/java/ie/pantry/ui/recipeimport/ShareTargetManifestTest.kt`
- **Dependencies:** T27, T29
- **Parallel:** Yes (with T14, T15, T18, T19, T20, T21, T22, T28) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the debug manifest under Robolectric
    WHEN `MainActivity`'s `ActivityInfo` is read
    THEN `launchMode == LAUNCH_SINGLE_TASK` and `taskAffinity.isNullOrEmpty()` (AD4; R5 AC2's same-instance basis)
  - GIVEN F2's blocked dataset read
    WHEN `MainActivity` launches
    THEN the import screen renders with `Pantry` displayed exactly once and the read still blocked off the main thread (F2 R6 intent unchanged)
  - GIVEN the merged manifest
    WHEN `ManifestPolicyTest` runs
    THEN `INTERNET` is still the only `android.permission` (spec Success Criteria)
- **Tests:**
  - `` `main activity launch mode is single task`() ``
  - `` `main activity task affinity is empty`() ``
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ShareTargetManifestTest.kt`
  - `` `import screen is displayed while a dataset read is blocked`() `` — renamed from `` `placeholder is displayed while a dataset read is blocked`() ``, with the KDoc wording updated to the import screen; assertion body unchanged
  - File: `app/src/test/java/ie/pantry/ui/FirstPaintNotBlockedTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.recipeimport.ShareTargetManifestTest' --tests 'ie.pantry.ui.FirstPaintNotBlockedTest' --tests 'ie.pantry.ManifestPolicyTest'`

### - [ ] T31: Share intake: `SEND` intent-filter, `MainActivity` share reading, activity-level screen tests

- **Requirement:** R1, R2, R5, R8, R11
- **Description:** Add to `MainActivity` a second `<intent-filter>` written in this order: `<action android:name="android.intent.action.SEND" />`, `<category android:name="android.intent.category.DEFAULT" />`, `<data android:mimeType="text/plain" />` (T33's manifest diff relies on it). Read shares per AD4: in `onCreate` only when `savedInstanceState == null`, in `onNewIntent` after `setIntent(intent)`, only for `ACTION_SEND`, skipped for `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, passing `ShareIntents.sharedText(intent)` to `onShareReceived`. Create `ImportTestApplication` (in-memory database, a gateway from `TestGateways.against(server)`, a `MutableClock`, and a `RecipeImporter` built through the internal constructor with an injected `parseDispatcher`, all from a companion `var` set before launch).
- **Files:**
  - Modify: `app/src/main/AndroidManifest.xml`
  - Modify: `app/src/main/java/ie/pantry/ui/MainActivity.kt`
  - Create: `app/src/test/java/ie/pantry/ui/recipeimport/ImportTestApplication.kt`
  - Modify: `app/src/test/java/ie/pantry/ui/recipeimport/ImportScreenTest.kt`
  - Modify: `app/src/test/java/ie/pantry/ui/recipeimport/ShareTargetManifestTest.kt`
- **Dependencies:** T30
- **Parallel:** Yes (with T14, T15, T18, T19, T20, T21, T22, T28) — disjoint files
- **Acceptance Criteria:**
  - GIVEN an `ACTION_SEND` `text/plain` intent
    WHEN `PackageManager.queryIntentActivities` resolves it
    THEN it resolves to `MainActivity` (`[SEAL-14]`)
  - GIVEN `EXTRA_TEXT` `Try this <server url> so good`, delivered on a cold start and through `ActivityController.newIntent`
    WHEN `MainActivity` receives it
    THEN the field shows the URL, the "link received" live region is present, the request count is 0, and tapping Import then imports exactly that URL (R1 AC2)
  - GIVEN a share with no URL and one with no `EXTRA_TEXT`
    WHEN received
    THEN the no-link message shows and the request count is 0 (R1 AC3)
  - GIVEN a share received and then `recreate()`
    WHEN the state is inspected
    THEN it is unchanged and the share is not replayed (R1 AC6)
  - GIVEN a launcher `ACTION_MAIN` start, a start with `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` carrying a share, and a non-`SEND` intent through `onNewIntent`
    WHEN each is handled
    THEN no notice appears and the field is unchanged (AD4)
  - GIVEN an import against a `NO_RESPONSE` server
    WHEN Cancel is tapped
    THEN the input state shows at once with the URL in the field, and no error or draft (R2 AC6)
  - GIVEN the decline surface and the draft-produced state
    WHEN Try another link or Import another is tapped, a different URL entered and Import tapped
    THEN the follow-up import runs in the same activity instance (R5 AC2)
  - GIVEN the decline surface for the server's not-a-recipe URL
    WHEN Share link is tapped
    THEN `shadowOf(activity).nextStartedActivity` is a chooser over `ACTION_SEND` `text/plain` with `EXTRA_TEXT` exactly that URL, and before the tap there was no started activity and no request beyond the page fetch (R8 AC1, AC2)
- **Tests:**
  - `` `a cold start share fills the field and makes no request`() ``
  - `` `a share through onNewIntent fills the field and makes no request`() ``
  - `` `import after a share imports exactly the shared url`() ``
  - `` `a share with no link shows the no link message and makes no request`() ``
  - `` `rotation keeps the state and does not replay the share`() ``
  - `` `a launcher start shows no notice`() ``
  - `` `a start from history skips the share read`() ``
  - `` `a non send intent through onNewIntent changes nothing`() ``
  - `` `cancel against a silent server returns to input with the url`() ``
  - `` `the next import after decline or draft runs in the same activity instance`() ``
  - `` `share link starts a chooser over the declined url`() ``
  - `` `the decline itself starts nothing and sends nothing`() ``
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ImportScreenTest.kt`
  - `` `a text plain send resolves to MainActivity`() ``
  - File: `app/src/test/java/ie/pantry/ui/recipeimport/ShareTargetManifestTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.recipeimport.ImportScreenTest' --tests 'ie.pantry.ui.recipeimport.ShareTargetManifestTest' --tests 'ie.pantry.ManifestPolicyTest'`

### - [ ] T32: Source guards: `SingleCallSiteTest` (Ask First) and `ImportSourceGuardTest` (characterisation) [CFC-1] [CFC-4]

- **Requirement:** R2, R9, R10
- **Description:** Verification/characterisation task over main sources final at T31: add `Jsoup.connect` and `org.jsoup.helper.HttpConnection` to `SingleCallSiteTest`'s forbidden list (Ask First, additive; see Q1), and create `ImportSourceGuardTest` over both import packages via `RepoPaths.repoRoot()`, scanning source with comments and KDoc stripped and matching on word boundaries (AD16). The `$`-free message rule exempts exactly the token `ImportUrl.check(` and the declaration `fun check(` in `ImportUrl.kt` (design `[SEAL-10]`). No red step precedes these tests, so the skip is logged in `## TDD Exceptions` when this task is implemented in Phase 4; the mutation check under Verification shows the classes can fail.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/RepoPaths.kt` — repository-root resolution
  - Modify: `app/src/test/java/ie/pantry/data/gateway/SingleCallSiteTest.kt`
  - Create: `app/src/test/java/ie/pantry/recipeimport/ImportSourceGuardTest.kt`
- **Dependencies:** T21, T22, T28, T31
- **Parallel:** No — runs after every main-source change, including any fix made by T21 or T22
- **Acceptance Criteria:**
  - GIVEN every main source
    WHEN `SingleCallSiteTest` runs
    THEN none contains `Jsoup.connect` or `org.jsoup.helper.HttpConnection`, and its existing checks still pass (R2 AC1)
  - GIVEN the main sources under `ie.pantry.recipeimport` and `ie.pantry.ui.recipeimport`
    WHEN `ImportSourceGuardTest` scans them
    THEN none imports `okhttp3` or references `java.net.URL`, `.toURL()`, `WebView`, `IngredientEngine`, `RecipeRepository`, `ThumbnailStore` or `Dao`, `Log.`, `println`, `printStackTrace` or `Instant.now(` (R2 AC1; R10 AC4 [CFC-1]; spec Never Do)
  - GIVEN the same sources
    WHEN every `throw`, `require(`, `check(` and `error(` is inspected
    THEN each message is a `$`-free literal, a bare rethrow, or a no-argument constructor (R9 AC2) [CFC-4]
  - GIVEN the same sources
    WHEN every `catch (e: Exception)` is inspected
    THEN each is immediately preceded by a `catch (e: CancellationException) { throw e }` (AD13; spec Always Do)
  - GIVEN a source snippet whose only mention of a banned token is in a comment or KDoc, and an identifier such as `Dialog.` or `DaoLike`
    WHEN the stripper and matcher run
    THEN neither is flagged
- **Tests:**
  - `` `no main source uses another http api`() `` — modified: forbidden list gains two tokens
  - File: `app/src/test/java/ie/pantry/data/gateway/SingleCallSiteTest.kt`
  - `` `import packages use no other http api url object or web view`() ``
  - `` `import packages never reference the ingredient engine or persistence`() ``
  - `` `import packages do not log or read the system clock`() ``
  - `` `import exception messages are dollar free literals`() ``
  - `` `every catch of Exception is preceded by a cancellation rethrow`() ``
  - `` `comments are stripped and tokens match on word boundaries`() ``
  - File: `app/src/test/java/ie/pantry/recipeimport/ImportSourceGuardTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.gateway.SingleCallSiteTest' --tests 'ie.pantry.recipeimport.ImportSourceGuardTest'`. Mutation check (`ImportSourceGuardTest`): temporarily add `println("x")` to `RecipeImporter.kt`; `import packages do not log or read the system clock` fails; revert and re-run green. Mutation check (`SingleCallSiteTest`): temporarily add a `Jsoup.connect` call in `RecipeImporter.kt`; `no main source uses another http api` fails; revert and re-run green.

## Phase 9: Closeout (step 9)

### - [ ] T33: Feature closeout: spec Commands, release-manifest diff, protected paths, device check

- **Requirement:** R1–R11
- **Description:** Run the spec's Commands, compare the release merged manifest against T1's baseline, confirm no protected path changed, and record the device check.
- **Precondition (user action):** the device check below needs a physical device with a browser and TalkBack; the developer performs it and reports each pass criterion.
- **Precondition (user action, Q4):** the developer has amended F2's `01_spec.md` R6 "placeholder" wording to name the import screen and re-approved F2's spec hash, or has recorded in the completion note that it is still outstanding.
- **Files:**
  - Read: `.toolchain/f5-baseline/release-AndroidManifest.xml` — T1's pre-F5 release manifest
  - Read: `.toolchain/f5-baseline/base-commit.txt` — T1's baseline commit
- **Dependencies:** T1–T32
- **Parallel:** No
- **Acceptance Criteria:**
  - GIVEN the finished feature
    WHEN the F5 filter, the full unit suite and `lintDebug` run
    THEN all pass with no F1–F4 regression (spec Success Criteria)
  - GIVEN the merged release manifest and T1's baseline, each with all whitespace removed and then `android:launchMode="singleTask"`, `android:taskAffinity=""` and T31's `SEND` intent-filter deleted
    WHEN the two are diffed
    THEN they are identical, and the release manifest has exactly one `android.permission` entry (design step 9; Network Exposure Triage)
  - GIVEN T1's baseline commit
    WHEN `ie.pantry.data` (including `data.gateway`), `ie.pantry.domain`, `PantryApplication.kt`, `app/schemas/` and `app/src/main/assets/` are diffed against it and checked for uncommitted changes
    THEN nothing changed, except a parser or alias-table change the developer approved as an Ask First fix for a failing `F5_HARVESTED` row (T14, T22) and listed in the completion note; the diff pathspec excludes exactly the listed paths (spec Project Structure)
  - GIVEN a device with TalkBack
    WHEN the developer shares a recipe link from a browser on a cold start and while an import runs, repeats the same share, rotates, and uses Cancel and Back during an import
    THEN each cold and warm share leaves exactly one Pantry task in Recents with the field filled and no fetch started; TalkBack speaks "Link received. Tap Import to import it." on each share, including the repeat; a share during an import speaks the cancelled-and-received message once; Back during an import returns to the input state; rotation changes nothing (DR2, DR3)
- **Tests:** none — runs the existing suites, static checks and a manual device check; see Verification
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.*' --tests 'ie.pantry.ui.recipeimport.*' --tests 'ie.pantry.data.gateway.SingleCallSiteTest' --tests 'ie.pantry.domain.ingredient.ParseFidelityCorpusTest' --tests 'ie.pantry.di.AppContainerTest' --tests 'ie.pantry.ui.FirstPaintNotBlockedTest' --tests 'ie.pantry.ManifestPolicyTest'`; `./gradlew :app:testDebugUnitTest`; `./gradlew lintDebug`; with `M=app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` and `B=.toolchain/f5-baseline/release-AndroidManifest.xml`: `./gradlew :app:processReleaseMainManifest && grep -c 'uses-permission android:name="android.permission' "$M"` prints `1`; with `norm() { tr -d ' \t\r\n' < "$1" | sed -E 's#android:launchMode="singleTask"##; s#android:taskAffinity=""##; s#<intent-filter><actionandroid:name="android\.intent\.action\.SEND"/><categoryandroid:name="android\.intent\.category\.DEFAULT"/><dataandroid:mimeType="text/plain"/></intent-filter>##'; }`, `diff <(norm "$B") <(norm "$M")` prints nothing; with `P='app/src/main/java/ie/pantry/data app/src/main/java/ie/pantry/domain app/src/main/java/ie/pantry/PantryApplication.kt app/schemas app/src/main/assets'`: `git diff --quiet "$(cat .toolchain/f5-baseline/base-commit.txt)" -- $P` exits 0 and `git status --porcelain -- $P` prints nothing; `grep -rn 'Jsoup.connect\|import okhttp3\.' app/src/main/java/ie/pantry/recipeimport app/src/main/java/ie/pantry/ui/recipeimport || echo clean` and `grep -rn 'IngredientEngine' app/src/main/java/ie/pantry/recipeimport app/src/main/java/ie/pantry/ui/recipeimport || echo clean` each print `clean`. Manual, repeatable: record the device check's pass criteria and each spec Success Criteria line, ticked, with the passing test that proves it, as completion notes under T33 in `03_tasks.md`.

## Implementation Order

1. T1 — the developer confirms `app` and `gradle` are committed; T1 records the baselines and adds the three dependencies everything else needs (design step 1).
2. T2, T3, then T4 — pure Kotlin models and value rules (step 2). T2 and T3 are independent; T4 needs T3's `DraftValue`.
3. T5 → T6 → T7 — the streaming cap, the bounded parse and the JSON-LD depth pre-scan, proving R3's bounds before extraction is built on them (step 3; retires RK4).
4. T8 → T9 → T10 → T11 → T12 → T13 — extraction, the largest and least certain component, in tier order. They are sequential because they share `ExtractionTiersTest.kt` (step 4).
5. T14 — user-action harvest dry run. It can start any time after T1 and is best done during T11–T13 so F3 parser defects surface early (step 5; DR4, DR5).
6. T15, T16 — image candidate selection and bounds-first validation, independent of each other (step 6).
7. T17 → T18 → (T19, T20) → T21; T22 — the importer's page path and then its image phase, each with its own red tests. Then come the characterisation passes for bounds, provenance and decline, then sentinel hygiene over every path. T22 binds the harvested rows once extraction and T14 are done (step 7).
8. T23 → (T24, T25), T26 → T27; T28; T29 → T30 → T31; T32 last — the ViewModel comes first, then messages, share intents and the composables. The flow test follows, then container wiring, the activity switch and share intake, with the source guards last over final main sources (step 8).
9. T33 — closeout after everything, including the developer's device check (step 9).

## Implementation Deviations

> Phase-4 minor-deviation ledger — populated by the triage gate's minor path (`SKILL.md` § "Mid-implementation discovery"). Append-only during Phase 4; resolved at the Final-Check completion gate. Leave empty until a deviation is logged.

| Date | Task | What spec/design said | What was actually done | Why | Classification | Backport status |
|------|------|-----------------------|------------------------|-----|----------------|-----------------|

## TDD Exceptions

> Phase-4 TDD-cycle-skip log — appended by the calling Claude during Phase 4 when the test-first red→green→refactor cycle is skipped for a code-stack task. Append-only during Phase 4; `Resolution`-column updates (`pending` → `accepted` or `remediate`) are resolved at the Final-Check completion gate. Leave empty until a skip is logged. **Code stacks (python/java/kotlin) only.** Not applicable for generic-profile tasks. (A code task that genuinely needs *no* test at all is not a skip — use the `**Tests:** none — <reason>` override instead.)

| Date | Task | Skip Reason | Resolution |
|------|------|-------------|------------|

## Open Questions

> All questions must be resolved before proceeding to implementation.

- [x] Q1: Three test edits are Ask First under the spec's Boundaries: the additive `AppContainerTest` assertions (T29), the `FirstPaintNotBlockedTest` rename (T30) and the two `SingleCallSiteTest` forbidden tokens (T32). Does approving this task list count as that approval, or should implementation stop and ask at each of those tasks?
  - **Resolution:** Resolved (user): approving this task list pre-approves the three named Ask First test edits (T29 `AppContainerTest`, T30 `FirstPaintNotBlockedTest` rename, T32 `SingleCallSiteTest` tokens); no per-task stop.
- [x] Q2: T14 needs the developer to save, trim and index snapshots from at least 3 hosts on a networked machine (DR4), and T22 is blocked until then. When will the snapshots be supplied? Is it acceptable for T14 to append the candidate `F5_HARVESTED` rows before T22 proves they are byte-identical to F5's extraction? Doing so lets F3's `ParseFidelityCorpusTest` surface parser defects early (DR5), but a row's `line` may need correcting in T22.
  - **Resolution:** Resolved: snapshots are supplied during implementation; T14 and T22 pause for the developer. Early candidate `F5_HARVESTED` rows are acceptable; T22 may correct a row's `line` (never its expected values).
- [x] Q3: Design FC6 defines `Candidate` as `Url`, `NotHttp` or `None`, but AD12 and DM3 require a malformed JSON-LD image string to give `NoImage(BAD_URL)`. T15 assumes a fourth variant, `Candidate.BadUrl`. Confirm, or name the intended mapping.
  - **Resolution:** Resolved (user): add `Candidate.BadUrl` as T15 assumes; recorded as a design clarification of FC6 `Candidate`.
- [x] Q4: The spec's Modified Files say F2's `01_spec.md` R6 wording ("placeholder") is amended to name the import screen, but the design leaves that spec edit out of its files and no task here makes it. Should it be a separate step outside this list, with the developer re-approving F2's spec hash, or should a task be added?
  - **Resolution:** Resolved (user): the F2 `01_spec.md` R6 wording amendment is a separate step outside this task list; the developer makes it and re-approves F2's spec hash.
- [x] Q5: Three checks move away from the test class the design's Testing Strategy names, so that dependencies stay narrow. The `BackHandler` enabled-state check moves from `ShareTargetManifestTest` to `ImportScreenTest` (T27). The rendered-message sentinel and `http` checks move from `ImportErrorHygieneTest` to `ImportMessagesTest` (T25). Each type's `toString()` sentinel check is first written in its owning task's test class, and T21 sweeps the pipeline types again. Accept these placements, or keep the design's placement and add the dependencies (T21 would then wait on T25)?
  - **Resolution:** Resolved (user): the placements stand as written in this task list.

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
| 1    | 2026-10-05 | 1     | 0           | 16        | 0        | 12     | tags=d0u0c1                     |
| 2    | 2026-10-05 | 0     | 0           | 0         | 0        | 32     | converged (0 HIGH); tags=d0u0c0 |

### Sealed dispositions

- `[SEAL-01]` **T13, T3 and T31 oversized** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; a T13 split would renumber 20+ dependency and Parallel entries for no spec gain, and each task's work is a single coherent unit with one verification command; revisit at implementation if a task overruns.
- `[SEAL-02]` **R3 bounds asserted at up to three layers** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; the importer-level layer (T19) is the SEAL-13 and SEAL-14 characterisation the design mandates, and trimming it would weaken the oracle; overlap cost is small.
- `[SEAL-03]` **toString sentinel checks repeat in T21 sweep** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; T21 is the spec R9 AC1 end-to-end hygiene sweep and a repeat there is cheap.
- `[SEAL-04]` **T20 duplicates earlier coverage** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; T20 is a characterisation task whose purpose is importer-level provenance, and the duplicate cases are inexpensive.
- `[SEAL-05]` **T6 media-type and truncated-gzip tests duplicate T17** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; T6 tests the unit and T17 the importer path, so both layers stay.
- `[SEAL-06]` **Six characterisation tasks add overhead** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; characterisation tasks are explicit and each logs its TDD exception, and overlap trims were declined above.
- `[SEAL-07]` **T27 depends on T24 needlessly** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; T27 consumes the share-event state T24 finishes, so the link is harmless.
- `[SEAL-08]` **T2 AC5 wall-clock ratio may flake** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; the ratio test stays and its margin widens if it flakes.
- `[SEAL-09]` **T17 empty-gzip reading differs from design wording** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; T17 already flags it as an ASSUMPTION and the reading is technically correct.
- `[SEAL-10]` **T8 BoundedDomWalk placement assumed** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; T8 flags it ASSUMPTION and T9 to T13 already read LineText.kt.
- `[SEAL-11]` **T28 cancel tests repeat** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; the two cancel cases hit different parse states and are cheap.
- `[SEAL-12]` **T32 comment-stripping helper test** (pass 1, accepted-as-risk) — Defense: synthesizer-judged; AD16 specifies that behaviour and the test is cheap.
- `[SEAL-13]` **T20 draft-producing fixture enumeration by directory…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the sweep is a characterisation check, not a spec criterion; the implementer fixes the fixture selection (an explicit manifest) while implementing T20 in Phase 4, with no change to any AC outcome.
- `[SEAL-14]` **T28 stalled-parse test needs a real-thread parse dispatcher…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; a test-construction detail; the AC outcome (Cancel works mid-parse) is unchanged and the implementer settles the dispatcher when writing T28 in Phase 4.
- `[SEAL-15]` **T21 mutation check uses initCause on an exception built…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; only the mutation ritual is affected, not the AC; the implementer picks a mutation that attaches a cause when running T21 in Phase 4.
- `[SEAL-16]` **T21 sentinel URL lacks the port TestGateways.against exempts** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the fixture URL form is settled when T21 is implemented in Phase 4 following F4's hygiene test pattern; the AC outcome is unchanged.
- `[SEAL-17]` **T33 unquoted pathspec variable is vacuous under zsh and the…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the command is corrected (inline pathspec list or array with :(exclude) entries) when T33 is implemented in Phase 4; flagged to the user at the approval gate.
- `[SEAL-18]` **T25 control-label resources unnamed so the import_…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; resource names are settled when T25 is implemented in Phase 4; the implementer prefixes labels import_ or scopes the check.
- `[SEAL-19]` **T32 catch guard covers only catch of Exception and goes…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the guard was added in pass 1 to enforce a spec Always Do; widening or narrowing its matcher is an implementation detail for T32 in Phase 4.
- `[SEAL-20]` **T14 runs only ParseFidelityCorpusTest while two other tests…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; T33 runs the full suite and catches any failure; the implementer may add both classes to T14's command in Phase 4.
- `[SEAL-21]` **T19 and T7 depth fixtures do not state where the Recipe…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; fixture construction detail settled when T7 and T19 are implemented in Phase 4; the AC depths are unchanged.
- `[SEAL-22]` **T13 AC6 depth-512 case under-specified** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; fixture detail settled when T13 is implemented in Phase 4; the AC states the boundary.
- `[SEAL-23]` **T23 failureOf assertions omit the four non-fetch kinds** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the T25 per-kind message tests cover those kinds; the implementer adds the kind-to-Failure cases while implementing T23 in Phase 4.
- `[SEAL-24]` **T20 DeclineTest mutation cannot fail the named test** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; mutation ritual only; the implementer chooses a mutation that exercises the named test when running T20 in Phase 4.
- `[SEAL-25]` **T14 verification counts hosts from snapshots.tsv not corpus…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; T22 binds corpus rows to snapshots and enforces the counts; an awk over corpus column 2 can be added to T14's command in Phase 4.
- `[SEAL-26]` **T5 inline gzip helper duplicates T6 ImportFixtures versions** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the implementer moves the T5 helper into ImportFixtures when T6 creates it (Phase 4); a small duplication window only.
- `[SEAL-27]` **Rules bullet says no sleeps while T28 polls with a short…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; wording only; read as no fixed sleeps.
- `[SEAL-28]` **T25 description places the Category exhaustive when in…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; wording only; the Category branch lives in failureOf (T23) and the Failure branch in ImportMessages.
- `[SEAL-29]` **T3 nullable-type scan misses nullable type arguments** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; minor scan strength; tightened when T3 is implemented in Phase 4.
- `[SEAL-30]` **T29 cites 5-argument AppContainer call sites that may not…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; wording only; the AC is that existing call sites compile unchanged.
- `[SEAL-31]` **T17 and T9 list tests without matching ACs** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the tests are specified by name and design rows; the implementer adds the matching AC lines in Phase 4.
- `[SEAL-32]` **SEAL numbers cited for two different design items** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; citation hygiene only; cite by topic during implementation.
- `[SEAL-33]` **Defense on SEAL-08 reads as an instruction to the…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; wording only; its content is rationale and the flake note carries no instruction to a reader of the panel.
- `[SEAL-34]` **T32 SingleCallSiteTest mutation will not compile without an…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; mutation ritual only; use a fully-qualified call or comment when running T32 in Phase 4.
- `[SEAL-35]` **T25 does not say the chooser title is not a resource** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; T26 already states the title is a literal; the T25 set-equality is unaffected.
- `[SEAL-36]` **T31 not ordered after T18 so request-count assertions…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the implementer uses image-free fixtures in T31 in Phase 4.
- `[SEAL-37]` **T33 manifest norm depends on filter child order** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; already mitigated by T31's ordering note and a loud diff failure.
- `[SEAL-38]` **Parallel column is heavy to maintain** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the column was verified by script and is advisory for sequential implementation.
- `[SEAL-39]` **T5 heap observation has no threshold** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; design Q1 makes it a supplementary one-off record, not a gate.
- `[SEAL-40]` **T10 size-cap AC bundles five inputs under one GIVEN** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; separate fixtures per input; wording only.
- `[SEAL-41]` **T4 cookingMinutes lacks an ASSUMPTION tag** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; a sensible shared helper implementing AD10; no behavioural effect.
- `[SEAL-42]` **T17 Files omit a Read of ImageValidator.kt** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; documentation gap only.
- `[SEAL-43]` **T3 Files omit RepoPaths** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; documentation gap only.
- `[SEAL-44]` **Rules note omits two SingleCallSiteTest tokens** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; documentation gap only; the test scans them regardless.

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to implementation
- **Content Hash:** `f961cab18d764947`
- **Hash basis:** v2
