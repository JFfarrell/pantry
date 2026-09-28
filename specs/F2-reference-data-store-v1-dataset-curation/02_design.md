# Design: Reference Data Store & v1 Dataset Curation

**Spec:** `specs/F2-reference-data-store-v1-dataset-curation/01_spec.md`

## Goals and Non-Goals

**Goals:**
- Ship the four curated datasets as plain UTF-8 JSON assets under `app/src/main/assets/reference/`, parsed strictly into typed, read-only entries (R1, R3, R4, R5).
- Give every lookup three outcomes that cannot be confused: found, absent, and load failure (R1, R2).
- Load each dataset lazily, off the main thread, once per process, and cache a failed load for the life of the process (R1, R6).
- Expose one store through `AppContainer`. Construction opens no asset, and `PantryApplication` gets an overridable container-creation hook that the first-paint Compose test uses (R6).
- Make the store read-only by construction and check that by test (R7).
- Keep the merged release manifest at the F1 baseline, with no new permission, component or HTTP client (Network Exposure Triage, branch (a)).

Requirement coverage (component IDs are `FC<n>`, as in F1's design, to avoid clashing with ARCHITECTURE's `C1`–`C10`):

| Requirement | Components |
|---|---|
| R1 | FC1 (result and failure types), FC2 (strict parser), FC3 (loader), FC6 (assets), FC7 (fixture matrix) |
| R2 | FC1 (`Lookup`, tables), FC4 (store lookups), FC7 |
| R3 | FC6 (staples asset, provenance), FC7 (`ShippedDatasetsTest`, `ProvenanceRecordTest`) |
| R4 | FC1 (`SectionOrderTable` invariants), FC2 (load-time checks), FC6, FC7 |
| R5 | FC6, FC7 |
| R6 | FC4 (load-once scope), FC5 (container default, application hook), FC7 (threading and Compose tests) |
| R7 | FC1 (immutable tables), FC4, FC7 (`ReadOnlySurfaceTest`) |

**CFC participation:** none. `blueprint/03_PLAN.md` → `## Cross-Feature Contracts` lists these participants: CFC-1 (F3, F5, F6, F11), CFC-2 (F3, F5–F11, F16, F17), CFC-3 (F7, F10–F15) and CFC-4 (F1, F3–F9, F11, F12, F14–F16). F2 is not in any list, so no F2 criterion or test carries a `[CFC-N]` tag (spec Never Do). F2's absence rule (R2) is its own PLAN criterion. The content-free failure rule comes from spec R1 and Q7.

**Non-Goals:**
- Canonical-key derivation: case-folding, stripping, singularising, and resolving free text through the alias table. That is C4 (F3). F2 does an exact-match lookup on the string the caller passes (Q3). The surface-form check exists only in test code.
- How F8, F9 and F11 render a `LoadFailed` (spec `[SEAL-03]`).
- Background pre-warm at launch (Q8, Ask First).
- A `schemaVersion` field in any asset (Q2).
- Per-item weight or density data, and fibre, sugar or salt (Q5).
- A separate listing API over sections or entries beyond the read-only `entries`, `sections` and `mappings` views that the shipped-content tests need (AD2).
- Detecting duplicate field names inside one JSON object (spec RK8, accepted).
- Any runtime cross-dataset validation. The alias-target and substitution cross-check is test-only (AD11).

## Architecture Decisions

| Decision | Choice | Alternatives Rejected | Rationale | Consequences |
|----------|--------|-----------------------|-----------|--------------|
| AD1 — Shape of absence and load failure (spec Decision Point; `[SEAL-12]`) | Three sealed types in `Lookup.kt`. **`Lookup<T>`** is `Lookup.Found(value)` or `Lookup.Absent`: the two-way result of querying a loaded table. **`LookupResult<T>`** is the three-way result of a store lookup; its subtypes are `Lookup.Found`, `Lookup.Absent` and `LoadFailed(failure)`. **`LoadResult<T>`** is `LoadResult.Ready(table)` or `LoadFailed(failure)`. `LoadFailed` is one data class that implements both `LoadResult<Nothing>` and `LookupResult<Nothing>` | A nullable return: it can carry only two outcomes, so a broken asset would read as "not in the table", which R1 forbids. A single three-variant type returned by table lookups too: a pure-Kotlin engine holding a loaded table would then have to handle a failure branch that cannot happen there. `kotlin.Result`: its failure side is a `Throwable`, and it has no absence case | R1 needs a load failure that can be told apart from a miss. R2 needs a miss that can be told apart from any found value. Each `when` is exhaustive with no `else`, so the compiler makes every caller handle each outcome | `Lookup.Absent` is a `data object`, so it never equals `Found(<zero entry>)`, `Found("")` or `Found(<queried variant>)` (R2 AC1–AC4). No lookup throws for a miss (R2 AC5) |
| AD2 — Suspend store versus snapshot (spec Decision Point) | **Both, layered.** `ReferenceDataStore` exposes one `suspend` accessor per dataset that returns `LoadResult<XTable>`, a loaded immutable snapshot. The snapshot answers `lookup(key): Lookup<T>` synchronously. The store also offers `suspend` shortcuts (`stapleFor`, `canonicalKeyForVariant`, `seasonalityFor`, `sectionFor`) that return `LookupResult<T>`. The table classes, entry types and result types live in files that import neither `android.*` nor `org.json` | Suspend-only lookups: F3, F8 and F9 are pure-Kotlin engines, and making every alias or staple probe `suspend` would push coroutines into their inner loops. Snapshot-only: every UI-layer caller would repeat the unwrap-then-lookup code | Engines take a table as a plain parameter and stay free of `android.*` (spec Decision Point). Callers that do I/O use the shortcuts | Tables have `internal` constructors, so tests in the single `:app` module (F3's JVM tests included) can build fixture tables without Robolectric. `ReadOnlySurfaceTest` asserts by source inspection that the pure files stay free of `android.*` and `org.json` Consumer guidance: engines take a table (for example `referenceData.staples()`) and query it synchronously; UI and ViewModel callers use the suspend shortcuts Engines receive tables only; a `LoadFailed` is unwrapped in one place, by the caller of `staples()` and its siblings, so no engine ever sees one. A consumer's test builds a store over fixture tables through the internal constructors, or over a fake `AssetSource` |
| AD3 — Typed load failure (Decision Point; `[SEAL-08]`) | `LoadFailure(dataset, category, array, entryIndex, field)`. Every field comes from the loader's own vocabulary: an enum, a constant array name (`"entries"`, `"sections"`, `"mappings"`), a zero-based `Int` position, or a constant schema field name. There are ten categories: `ASSET_ABSENT`, `ASSET_UNREADABLE`, `INVALID_JSON`, `MISSING_FIELD`, `UNEXPECTED_FIELD`, `WRONG_TYPE`, `INVALID_ENUM_VALUE`, `INVALID_VALUE`, `DUPLICATE_KEY`, `INTERNAL_ERROR` (see Error Handling for what triggers each) | Carrying the `JSONException` message or cause: verified against AOSP `org.json` (LineageOS `lineage-21.0` libcore, Android 14), where `JSONTokener.syntaxError` builds `message + " at character " + pos + " of " + in` and so embeds the **entire asset text**, and `JSON.typeMismatch` embeds the offending value. An exception type instead of a value: R1 AC8 requires that no exception escapes | The failure names the dataset and the entry position (R1 AC4, AC7) without echoing any asset content. A duplicate key reports the position of the second occurrence, never the key. Document-level failures (trailing content, malformed UTF-8, a syntax error) carry no entry position because there is no entry; they still name the dataset, which is how R1 AC7's "entry's position" is met wherever an entry exists. `UNEXPECTED_FIELD` is stricter than the spec's list of failures: the prior build's CSV carried columns (fibre, sugar, salt) that Q5 deliberately dropped, so a leftover or misspelt column in a curated asset must fail loudly rather than be ignored. The shipped-content tests load the real assets, so such a slip fails a test long before it reaches a device. `UNEXPECTED_FIELD` reports the entry position but not the unknown name, because that name is asset text | `LoadFailure` is a data class, so its `toString()` is content-free too. Tests assert this with a sentinel planted in the fixtures |
| AD4 — Asset-source seam (Decision Point; `[SEAL-13]`) | `fun interface AssetSource { fun open(path: String): InputStream }`. `AndroidAssetSource(assets: AssetManager)` is the production implementation. `AssetSource.UNAVAILABLE` throws `FileNotFoundException` with a constant message. `AppContainer` gets a fourth constructor parameter, `referenceAssets: AssetSource = AssetSource.UNAVAILABLE`. Only `AppContainer.production(app)` passes `AndroidAssetSource(app.assets)`. Test sources (fixture, recording, blocking) live in `testutil` | `AssetManager` directly: it cannot count opens or block a read. A `Context` parameter: the store would then read the real assets in every test that builds a container | F1's positional calls `AppContainer(db, store, processor)` keep compiling and never read reference data. With the default source, a lookup returns `LoadFailed(ASSET_ABSENT)` and nothing throws (spec Modified Files). The source is a **load-time input**: the store reads it at most once per dataset, and only inside the first load. After that, the cached `LoadResult` is final, so no call on the source or the store can add, remove or replace an entry (R7 AC1) | Getting `app.assets` in `production` does not open a file, so R6 AC1 still holds for the production path `AssetSource.kt` is a pure file (an interface with no `android.*` import) and belongs to the purity list alongside FC1's five files The zero-opens test has a positive control (one lookup records exactly one open), and `AndroidAssetSource` holds only the `AssetManager` and opens nothing at construction, confirmed by review and by `AppContainerTest` building the production container |
| AD5 — Container-creation hook (Decision Point; R6 AC5; `[SEAL-14]`, `[SEAL-16]`) | `PantryApplication` becomes `open`. `container` stays a final `val … by lazy(SYNCHRONIZED) { createContainer() }`. A new `protected open fun createContainer(): AppContainer = AppContainer.production(this)` is the only override point. A test-only `BlockingReadPantryApplication : PantryApplication()` (under `app/src/test/java/ie/pantry/ui/`) overrides it to return `AppContainer(PantryDatabase.createInMemory(this, Clock.systemUTC()), ThumbnailStore(filesDir), ThumbnailProcessor(), blockingSource)` and exposes `blockingSource`. The Compose test registers it with `@Config(application = BlockingReadPantryApplication::class)` | Making `container` itself `open`: that invites overriding the lazy once-only rule. A static or global test hook in main code: it would ship in the release APK. A `production(…, referenceAssets)` parameter: it widens the production factory just for a test | The smallest change F1's class allows. Verified shape today: `class PantryApplication : Application()` with `val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer.production(this) }` | Robolectric creates a fresh application per test, so the per-instance `blockingSource` needs no static state. Waits are bounded (AD15), so a regression fails as an assertion instead of hanging |
| AD6 — Load-once, concurrency and failure caching (Q7; R1 AC8; R6 AC2–AC4) | The store owns `scope = CoroutineScope(SupervisorJob() + ioDispatcher)`, with `ioDispatcher` defaulting to `Dispatchers.IO`. For each dataset the constructor creates `scope.async(start = CoroutineStart.LAZY) { loadAndLog(dataset, parser) }`. Every accessor calls `await()`. The load body (`loadDataset`) catches every `Exception` except `CancellationException`, lets any `Error` through, and returns a `LoadResult`. A `LoadFailed` is cached exactly like `Ready`: the same `Deferred` holds it for the life of the process, and one `Log.w` line is written when the load completes | `Mutex` plus a nullable field with `withContext(IO)` in the caller: if the first caller is cancelled mid-read, the result is thrown away and the next caller opens the asset again, which breaks "opened exactly once". `lazy {}` running the load on the caller's thread: that blocks the main thread (R6 AC2). Retrying on failure: settled against by Q7 | `CoroutineStart.LAZY` means construction creates the coroutine without running it, so nothing is opened (R6 AC1). `Deferred.start` is idempotent and thread-safe, so concurrent first calls share one load (R6 AC3). A caller that is cancelled stops only its own `await`; the load still finishes and is cached. The main-thread caller suspends in `await` while the read runs on IO (R6 AC2) | The scope is never cancelled. An idle store holds no thread. The blocked-read test releases its latch in `@After`. A `LoadFailed` result means a packaging defect, and it stays until the process restarts, as Q7 intends **`Error` policy.** An `Error` in the load body (`StackOverflowError`, `OutOfMemoryError`) is not caught: it fails the cached `Deferred` and every later `await` rethrows it. This is an accepted residual, because the assets are static and developer-curated; the guard is `ShippedDatasetsTest`, which caps each asset at 1 MB and its JSON nesting depth at 4, so a runaway asset fails a test before it ships. `no exception escapes` in this design means no `Exception`. The store's scope is never cancelled, and test containers do not close it, by design |
| AD7 — Collection protection and R7 verification (Decision Points) | **Unmodifiable wrappers over private copies**, built once in each table's constructor: `Collections.unmodifiableList(ArrayList(src))` and `Collections.unmodifiableSet(LinkedHashSet(src))`. For nested collections, the table re-freezes `SeasonalityEntry.inSeasonMonths` and `substitutions` with `entry.copy(…)`, so a table built from caller-owned mutable lists is still protected. `ReadOnlySurfaceTest` uses **Java reflection** to check every public type in the package: declared fields are `final`, there is no `set*` method, and there is no public or internal mutator on the store or tables. It then **casts** every returned collection to its `Mutable*` counterpart, tries `add`, `remove` and `clear`, and asserts `UnsupportedOperationException`, or that the collection's own contents are unchanged after the attempt | Copy-on-every-return: it allocates on hot lookup paths (F11 walks the whole list), and the protection would depend on each accessor remembering to copy. Kotlin `toList()`/`toSet()`: these return a `java.util.ArrayList`/`LinkedHashSet` that a cast can mutate. `kotlin-reflect`, which would be needed to tell `List` from `MutableList` in property types: it is not in the catalog, so adding it is an Ask First item | Wrapping once costs nothing per lookup, and a downcast write throws (R7 AC4) | Java reflection cannot tell `List` from `MutableList`, because both erase to `java.util.List`. The "never a `Mutable*` type" clause of R7 AC2 is therefore confirmed at review, and the runtime downcast test catches any mutable instance in practice `StaplesEntry.basis` reuses `ie.pantry.data.db.entity.NutritionBasis`, which must stay a plain import-free enum (moving it to a neutral package is the escape route); the purity check also fails on any `ie.pantry.data.db` import other than `NutritionBasis`. Reflection cannot see Kotlin `internal` members reliably (their names are mangled), so the no-mutator reflection check is scoped to public and non-mangled members; internal members are covered by the source-inspection denylist, scoped to `src/main/java/ie/pantry/data/reference/`, and by review The trigger for moving `NutritionBasis` to a neutral package is a second consumer of `data.reference` outside F9 that must stay free of `data.db` |
| AD8 — Asset layout and dataset invariants (Decision Points) | **One file per dataset.** `section_order.json` holds both the `sections` array and the `mappings` array (spec R1 Terms). Each file's top level is a JSON **object** with named arrays, and the exact layouts are in Data Models. Display-order indices must be **unique and non-negative, not contiguous**. A key maps to **exactly one** section, so a repeated mapping key is `DUPLICATE_KEY`. Alias targets **may not chain**: a target must not also be a variant, which is checked in the shipped-content test | A separate mapping file: it breaks the spec's "second array in the same asset" wording and adds a cross-file consistency hazard. Contiguous `0..n-1` indices: inserting a department would force renumbering the whole list, a noisy diff for a data edit (spec RK7). A key in several sections: F11 needs one position per entry. Alias chains: C4 consults the table once (ARCHITECTURE C4), so a chained target would come back as a non-canonical key | Uniqueness is all that F11's ordering and R4 AC2 need. An object at the top level leaves room for the two arrays and follows one pattern across all four files | The layout is fixed now. Changing it later is an Ask First item (spec Boundaries, Q2) |
| AD9 — Strict parsing with `org.json` (Q1; R1 AC7; `[SEAL-10]`, `[SEAL-11]`) | Q1 is reconciled as follows. The parser uses **`JSONObject.has(name)` and `JSONObject.get(name)` / `JSONArray.get(i)`** (all throwing or non-coercing) followed by an **explicit Kotlin `is` check** on the value. It never calls an `opt*` accessor, the coercing typed getters `getString`, `getInt` or `getDouble`, or `getJSONArray` (avoided so every value goes through the same `is` check). `get(name)` is itself a throwing accessor (it throws on a missing name), so Q1's rule of using only throwing accessors is met; what this design adds is the explicit type check R1 AC7 needs, because the typed getters coerce. The document is read with `JSONTokener(text).nextValue()`, and then **`tokener.nextClean() == '\u0000'`** is required, so trailing content fails. Bytes are decoded as UTF-8 with `CodingErrorAction.REPORT`. The rules for values: a string must be `is String`. A number (nutrients) must be `is Int`, `is Long` or `is Double` and finite. A whole number (months, `walkIndex`) must be **`is Int` only**: `3.0` parses to `Double` and is `WRONG_TYPE`. `JSONObject.NULL` is `WRONG_TYPE`. An enum is matched against `NutritionBasis.entries` by exact `name`; an unknown name is `INVALID_ENUM_VALUE` | Using `getString`/`getInt`/`getDouble`: verified in the AOSP source, `getString` returns `String.valueOf(value)`, so a number and even JSON `null` (`"null"`) become text. `getInt` uses `JSON.toInteger`, which truncates `Number.intValue()` and parses numeric strings. `NutritionBasis.valueOf(s)`: its `IllegalArgumentException` message names the value, which is asset content | Only the `is` check detects the coercions that R1 AC7 forbids. `get`/`has` never default a value, which meets Q1's intent (never `opt*`) | Accepted leniency, verified in `JSONTokener`: `/* */`, `//` and `#` comments are skipped (also after the closing bracket); single-quoted strings, unquoted names and unquoted string values are accepted; `=`/`=>` work as name separators and `;` as a separator; `0x` hex and leading-`0` octal integers are accepted (`010` → 8, while `09` falls through to `Double` and is rejected as `WRONG_TYPE`); `null`, `true` and `false` match case-insensitively; a UTF-8 BOM is stripped. Duplicate names inside one object keep the last value (`readObject` calls `put`). The parser cannot see these. They fall in RK8's parser-leniency class, and curation review plus the shipped-content tests are the mitigation (DR6). An array hole (`[a,,b]`, `[a,]`) stores a Java `null`, which `JSONArray.get(i)` rejects, so it is `INVALID_JSON`. A `NaN` literal in an object value fails `JSONObject.put`'s `checkDouble`, so it is `INVALID_JSON` too A literal NUL character after the closing bracket ends `nextClean()` and is therefore accepted; it is added to the accepted leniency and pinned by a characterisation fixture |
| AD10 — Load-time versus test-time validation (Decision Point) | **At load time:** structure, presence, types, enum names, duplicate lookup keys (in all five key spaces), and the value rules each type promises. These are nutrients finite and ≥ 0; months in 1..12, distinct and non-empty; `walkIndex` ≥ 0 and unique; each mapping's `section` naming a listed section. **In tests only:** curation policy. That covers the count band, the canonical surface form of keys, every staples key being mapped, set equality with `expected_sections.txt`, at least one substitution, no self-substitution, no alias mapping to itself, no alias chains, and the cross-dataset resolution checks (AD11) | Everything at load time: a curation-policy slip (for example 159 staples) would then become a process-long `LoadFailed` for all users. Everything in tests only: a table would have to represent a mapping to an unlisted section, which `sectionFor` cannot return honestly | The loader enforces what the Kotlin types claim. Policy that F17's data edits may legitimately move stays in `ShippedDatasetsTest`, as spec Always Do asks | R4 AC1–AC3 are guaranteed by the loader and also asserted in `ShippedDatasetsTest` The parser is the sole source of failure categories and positions: the tables' constructor `require` checks are a backstop the fixture matrix never triggers, and each table-constructor invariant is driven with a violating list, and the test asserts the equivalent parser input is rejected first as its own category, so an `INTERNAL_ERROR` without a position cannot appear. Multi-fault precedence is deterministic: array order, then required-field order, and within one entry missing before unexpected before type before value; tests assert the exact category, entry index and field under that order. A nutrient is rejected when it is not finite or is below 0.0, so `-0.0` (equal to `0.0`) is accepted. `ShippedDatasetsTest` also caps each nutrient at a plausible upper bound (energy at most 1000 kcal and each macro at most 100 g per 100 g) so a typo like `1e308` fails a test; this is policy, not a load-time rule Cross-entry checks (duplicate key, `walkIndex` uniqueness, a mapping to an unlisted section) run after each entry's own field checks and compare against earlier entries; the order is file order, with `sections` parsed before `mappings`. `NaN` and infinities never reach the nutrient range check: `org.json` rejects them when the value is stored (`JSONObject.put` runs `checkDouble`), so `NaN` and `1e999` fail as `INVALID_JSON` with no position, and the finite check is a backstop |
| AD11 — Cross-dataset resolution check (panel concern; `[SEAL-09]`) | In `ShippedDatasetsTest`, with `known = staplesKeys ∪ seasonalityKeys ∪ sectionMappingKeys`: every alias `canonicalKey` is in `known` and is not itself an alias variant, and every seasonality substitution is in `staplesKeys ∪ seasonalityKeys`. On failure the test lists the offending keys (test output, not app logs) | A load-time check: it would make loading aliases depend on loading staples, breaking per-dataset load-once isolation. No check at all: a typo in an alias target silently misses in F3 (spec RK1) | Cheap, and it catches silent misses at the one place where all four shipped files are loaded | Does not replace F3's own reconciliation of keys with its normalisation rule (Q3, `[SEAL-02]`) |
| AD12 — Dependencies (Q9; `[SEAL-15]`) | **No new runtime dependency and no coroutines alias.** `kotlinx-coroutines-core` is already on the main compile classpath transitively. A local observation of the generated (untracked) build output `app/build/intermediates/lint_report_lint_model/debug/…/debug-artifact-dependencies.xml`, reproducible with `./gradlew :app:dependencies --configuration releaseCompileClasspath`, lists `kotlinx-coroutines-core-jvm:1.7.3` and `kotlinx-coroutines-android:1.7.3`, and F1's `RecipeRepository` already imports `kotlinx.coroutines.Dispatchers`/`withContext` in main code. Two aliases are added (Q9, settled): `androidx-compose-ui-test-junit4` → `androidx.compose.ui:ui-test-junit4` as `testImplementation`, and `androidx-compose-ui-test-manifest` → `androidx.compose.ui:ui-test-manifest` as `debugImplementation`. Both are versionless, managed by `composeBom = "2024.12.01"`, which resolves Compose UI to **1.7.6** (verified from the resolved `androidx.compose.ui:ui-android:1.7.6`). `platform(libs.androidx.compose.bom)` is added to `testImplementation` and `debugImplementation` too | An explicit `kotlinx-coroutines-core`/`-android` alias: it would be an Ask First catalog addition, and F1 already relies on the same transitive path. `kotlinx.serialization`, Moshi or Gson: Q1 chose `org.json` | Coroutine APIs used (`CoroutineScope`, `SupervisorJob`, `async(start = LAZY)`, `Deferred.await`, `Dispatchers.IO`) all exist in 1.7.3 | Test runtime resolves coroutines at **1.9.0** (pulled up by `kotlinx-coroutines-test`, verified in the unit-test lint model), while the APK ships 1.7.3. This skew already existed in F1 (DR4). The test deps never reach the release manifest (AD14) |
| AD13 — Provenance record (R3 AC4; `[SEAL-07]`, `[SEAL-17]`) | `docs/reference-data-provenance.md` has one `## ` section per dataset, with the exact headings `## Staples`, `## Aliases`, `## Seasonality`, `## Section order`. Each section contains a line `- **Source:** …` and a line `- **Retrieved:** YYYY-MM-DD`, an ISO 8601 calendar date that must parse with `java.time.LocalDate.parse`. `ProvenanceRecordTest` finds the file by walking up from `File("").absoluteFile` (Gradle runs unit tests in `app/`, as F1's `SchemaShapeTest` relying on `File("schemas/…")` shows) to the first directory holding `settings.gradle.kts`, then resolving `docs/reference-data-provenance.md` | A hard-coded `File("../docs/…")`: it breaks when an IDE runs tests with the working directory at the repository root. Copying the doc into test resources: two copies drift apart | The upward search works from `app/` and from the root. A fixed heading and field syntax makes the check mechanical | The spot-check size (≥ 20 database entries), the no-ODbL statement, the tesco.ie department snapshot, the developer-named seasonal calendar source and the developer's seed and display-order review are confirmed at review, not by the test (spec R3 AC4, R4 AC5) `ReadOnlySurfaceTest` resolves source files through the same repo-root search as `ProvenanceRecordTest` |
| AD14 — No new manifest surface (Network Exposure Triage) | F2 adds no `<activity>`, `<service>`, `<receiver>`, `<provider>`, `<uses-permission>` or `meta-data`, and `app/src/main/AndroidManifest.xml` does not change. There is **no `androidx.startup.Initializer`**, because loading is first-use only (Q8), and no `implementation` dependency is added. The release-merged manifest check is the spec's Commands grep block, run after `assembleRelease` | Adding a startup `Initializer` for a pre-warm: it would mean a new `meta-data` on the existing `InitializationProvider`, and it is Ask First (Q8) | Verified baseline in `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`: one each of activity (`ie.pantry.ui.MainActivity`), service (`androidx.room.MultiInstanceInvalidationService`), provider (`androidx.startup.InitializationProvider`) and receiver (`androidx.profileinstaller.ProfileInstallReceiver`). Two carry `android:exported="true"` (the activity and the `DUMP`-guarded receiver). The only `<uses-permission>` is `ie.pantry.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, so the `android.permission` grep prints 0 | `ui-test-manifest` adds an activity to the **debug**-merged manifest only [ASSUMPTION: an exported `androidx.activity.ComponentActivity` test host, no permission]. F1's `ManifestPolicyTest` checks only permissions on the debug manifest and must still pass. The release baseline is unchanged |
| AD15 — First-paint Compose test construction (R6 AC5) | `createEmptyComposeRule()` plus an explicit `ActivityScenario.launch(MainActivity::class.java)` **after** the blocked read has started. The sequence: (1) start `container.referenceData.stapleFor("water")` in a `CoroutineScope(SupervisorJob() + Dispatchers.Default)`; (2) assert `blockingSource.awaitStarted(5, SECONDS)`; (3) launch `MainActivity`; (4) `onNodeWithText(<app_name>).assertIsDisplayed()`; (5) assert `!blockingSource.completed`. In `@After`: `release()`, close the scenario, cancel the scope, close the in-memory database. `BlockingAssetSource.open` waits on its release latch for at most 30 s and then throws `FileNotFoundException`, so a missing release cannot hang the JVM | `createAndroidComposeRule<MainActivity>()`: it launches the activity before the test body, so the read would start after first paint and prove less. JUnit `@Test(timeout = …)`: it runs the body on another thread, which conflicts with Robolectric's main-thread model | This is a real cold start with a read already in flight. The test cannot pass unless the seam was exercised, because of step (2) | Needs `ui-test-junit4`. The versionless catalog aliases keep it aligned with the BOM [ASSUMPTION: Compose's idling does not wait on a `Dispatchers.IO` coroutine blocked in `open`; retired by the first run of this test, fallback in DR2] **Hardened assertions.** `BlockingAssetSource` records every `open` (call count and calling thread) and a `timedOut` flag that is set only when its 30 s wait expires; `completed` is true only when a read finished after `release()`. `GatedAssetSource` releases only through its gate and sets `completed` on that path only. Step (2) asserts exactly one open, on a non-main thread, before `MainActivity` launches (zero opens before step (1)). At the end the test asserts `!completed`, `!timedOut` and that no open happened on the main thread, and the launch is bounded by a 10 s wall clock. This test does not detect a background pre-warm at launch, because a read on the IO dispatcher never blocks first paint; the companion test `launching MainActivity opens no dataset asset` (a recording source, no lookup issued, zero opens after Compose idle, bounded to 10 s) detects a pre-warm or any startup read on any thread (Q8) `GatedAssetSource` has the same 30 s cap as `BlockingAssetSource` and throws on expiry, so a missing release cannot park an IO thread. `!completed` is trivially true while nothing releases and is not coverage; the informative assertions are the open count, the thread and `!timedOut` |

## Component Design

### FC1 — Reference model (pure Kotlin)

**Responsibility:** Define the entry types, the result and failure types, and the immutable tables that answer lookups.

**Location:** `app/src/main/java/ie/pantry/data/reference/` — `ReferenceEntries.kt`, `Lookup.kt`, `LoadFailure.kt`, `ReferenceDataset.kt`, `ReferenceTables.kt`. None of these files imports `android.*` or `org.json` (AD2).

**Key classes/functions:**
- `StaplesEntry`, `AliasEntry`, `SeasonalityEntry`, `SectionOrderEntry`, `SectionMapping`: data classes with `val` properties only (DM1–DM5). `StaplesEntry.basis` reuses `ie.pantry.data.db.entity.NutritionBasis`, a plain enum with no imports (Q5, verified).
- `Lookup`, `LookupResult`, `LoadResult`, `LoadFailed` — AD1 (I1).
- `LoadFailure` and its nested `Category` enum (DM6). `ReferenceDataset` enum (DM7), with the asset path.
- `StaplesTable`, `AliasTable`, `SeasonalityTable`, `SectionOrderTable`: each class has an `internal` constructor that copies its input, checks its invariants with `require(…)` (constant messages), freezes collections (AD7), builds a private `HashMap` index, and answers `lookup(...)` (I2).

### FC2 — Strict parser

**Responsibility:** Turn one asset's bytes into a `LoadResult` of its table, applying AD9's strict rules and AD10's load-time invariants.

**Location:** `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt`

**Key classes/functions:**
- `internal object DatasetParser` with `parseStaples`, `parseAliases`, `parseSeasonality`, `parseSectionOrder` (I4).
- Private helpers: `decodeUtf8Strict(bytes)`, `readDocument(text)` (runs `nextValue` and then the `nextClean() == '\u0000'` check), `requireFields(obj, required, position)` (reports `MISSING_FIELD` and then `UNEXPECTED_FIELD`), `string(obj, field, pos)`, `number(obj, field, pos)`, `wholeNumber(value, field, pos)`, `basis(obj, pos)`.
- `private class ParseAbort(val failure: LoadFailure) : Exception(null, null, false, false)`, a stackless unwind carrier that is caught inside the parser and never leaves it.

### FC3 — Asset loading

**Responsibility:** Open one dataset's asset through the injected `AssetSource`, read its bytes, and map I/O failures to `LoadFailed`.

**Location:** `app/src/main/java/ie/pantry/data/reference/AssetSource.kt` (pure interface and `UNAVAILABLE`), `AndroidAssetSource.kt` (imports `android.content.res.AssetManager`), `DatasetLoader.kt`

**Key classes/functions:**
- `AssetSource` (I3). `AndroidAssetSource(assets)` delegates to `AssetManager.open(path)`.
- `internal fun <T : Any> loadDataset(source, dataset, parse): LoadResult<T>` — a top-level blocking function, called only on the store's IO dispatcher (I5). It is the single catch site. `FileNotFoundException` becomes `ASSET_ABSENT`, any other `IOException` becomes `ASSET_UNREADABLE`, and any other `Exception` from `parse` becomes `INTERNAL_ERROR`. A `CancellationException` is rethrown.

### FC4 — Reference data store

**Responsibility:** Hold one lazily started, load-once result per dataset and answer store-level lookups.

**Location:** `app/src/main/java/ie/pantry/data/reference/ReferenceDataStore.kt` (imports `android.util.Log`)

**Key classes/functions:**
- `class ReferenceDataStore(source: AssetSource, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)` (I6). Four `Deferred<LoadResult<…>>` are created with `CoroutineStart.LAZY` (AD6).
- `private fun <T : Any> loadAndLog(dataset, parse)`: calls `loadDataset`, which never throws an `Exception`, and on `LoadFailed` writes one `Log.w("PantryRef", "dataset=<DATASET> category=<CATEGORY>")`.

### FC5 — App wiring

**Responsibility:** Construct and expose one `ReferenceDataStore` per container, and let tests replace the container.

**Location:** `app/src/main/java/ie/pantry/di/AppContainer.kt`, `app/src/main/java/ie/pantry/PantryApplication.kt` (both modified)

**Key classes/functions:**
- `AppContainer`: a new parameter `referenceAssets: AssetSource = AssetSource.UNAVAILABLE` and `val referenceData: ReferenceDataStore = ReferenceDataStore(referenceAssets)`. `production(app, clock)` passes `AndroidAssetSource(app.assets)` (I7).
- `open class PantryApplication` with `protected open fun createContainer(): AppContainer` (I8, AD5).

### FC6 — Curated datasets and provenance

**Responsibility:** Provide the v1 content of the four datasets and the record of where it came from.

**Location:** `app/src/main/assets/reference/{staples,aliases,seasonality,section_order}.json`, `docs/reference-data-provenance.md`, `app/src/test/resources/reference/expected_sections.txt`

**Key contents:**
- `staples.json`: 160–180 entries, all `PER_100G` (Q4, Q5). The seed is the prior build's 166-row CSV (Q4), with the fibre, sugar and salt columns dropped and at least 20 figures spot-checked against a recognised public nutrition composition database the developer names.
- `section_order.json`: the tesco.ie department set in the developer's chosen display order (Q6), plus a mapping for every staples key.
- `seasonality.json`: about 40 Republic-of-Ireland keys with months from the developer-named seasonal calendar, and substitutions for out-of-season entries (Q10). The count is a target, not a test.
- `aliases.json`: a handful of developer-chosen exceptions (R5 [ASSUMPTION], Q10).
- `expected_sections.txt`: one section name per line, UTF-8. Blank lines are ignored and names are compared exactly after trimming line ends.

### FC7 — Verification harness

**Responsibility:** Prove R1–R7 with fixture-driven behaviour tests, shipped-content tests, threading tests, the Compose first-paint test and the R7 inspection tests.

**Location:** `app/src/test/java/ie/pantry/data/reference/`, `app/src/test/java/ie/pantry/ui/`, `app/src/test/java/ie/pantry/testutil/`, `app/src/test/resources/reference/`, plus `gradle/libs.versions.toml` and `app/build.gradle.kts` (the test dependencies from AD12). See Testing Strategy and File Structure.

## Data Models

IDs: DM1 `StaplesEntry`, DM2 `AliasEntry`, DM3 `SeasonalityEntry`, DM4 `SectionOrderEntry`, DM5 `SectionMapping`, DM6 `LoadFailure`, DM7 `ReferenceDataset`. No field is nullable except where noted, and no field has a Kotlin default value. Every value comes from the asset or the load fails (R1 AC4).

| Model | Field | Type | Constraints | Description |
|-------|-------|------|-------------|-------------|
| StaplesEntry | key | String | Required. Unique in the table (`DUPLICATE_KEY`). Surface form checked in tests (R3 AC3) | Canonical ingredient key |
| StaplesEntry | basis | NutritionBasis | Required. Exact enum name, else `INVALID_ENUM_VALUE`. Every v1 row is `PER_100G` (test) | Measurement basis (F1's `ie.pantry.data.db.entity.NutritionBasis`) |
| StaplesEntry | energyKcal | Double | Required, JSON number, finite, ≥ 0 | Energy per basis |
| StaplesEntry | proteinG | Double | Required, JSON number, finite, ≥ 0 | Protein per basis |
| StaplesEntry | fatG | Double | Required, JSON number, finite, ≥ 0 | Fat per basis |
| StaplesEntry | carbohydrateG | Double | Required, JSON number, finite, ≥ 0 | Carbohydrate per basis |
| AliasEntry | variant | String | Required. Unique (`DUPLICATE_KEY`). Surface form, not equal to `canonicalKey`, and not used as another entry's target (tests) | Already-normalised raw variant (Q3) |
| AliasEntry | canonicalKey | String | Required. Surface form, and resolves to a known key (AD11, tests) | Target key |
| SeasonalityEntry | key | String | Required. Unique (`DUPLICATE_KEY`) | Canonical key |
| SeasonalityEntry | inSeasonMonths | Set<Int> | Required JSON array of `Int`. Non-empty, each value 1..12, no repeats (`INVALID_VALUE`). Unmodifiable, in file order | Republic-of-Ireland in-season months (developer-named seasonal calendar) |
| SeasonalityEntry | substitutions | List<String> | Required JSON array of strings (may be `[]`). Unmodifiable, in preference order. Each entry is in surface form, not the entry's own key, and resolves (tests) | Out-of-season substitutes |
| SectionOrderEntry | name | String | Required. Unique among sections (`DUPLICATE_KEY`) | tesco.ie department name |
| SectionOrderEntry | walkIndex | Int | Required JSON integer (`Int` only). ≥ 0. Unique across sections (`INVALID_VALUE`). Need not be contiguous (AD8) | Display position for a click-and-collect list, not a physical store walk. Lower means earlier |
| SectionMapping | key | String | Required. Unique among mappings (`DUPLICATE_KEY`) | Canonical key |
| SectionMapping | section | String | Required. Names a listed `SectionOrderEntry.name`, else `INVALID_VALUE` | The key's one section |
| LoadFailure | dataset | ReferenceDataset | Non-null | Which dataset failed |
| LoadFailure | category | LoadFailure.Category | Non-null | See Error Handling |
| LoadFailure | array | String? | Nullable: null for failures at document or asset level. Constant `"entries"`, `"sections"` or `"mappings"` otherwise | Which top-level array |
| LoadFailure | entryIndex | Int? | Nullable: null at document or asset level. Zero-based. For `DUPLICATE_KEY` it is the second occurrence | Entry position |
| LoadFailure | field | String? | Nullable: null when not field-specific, and always null for `UNEXPECTED_FIELD`. Otherwise a constant schema field name | Offending field |
| ReferenceDataset | assetPath | String | Constant | `reference/staples.json`, `reference/aliases.json`, `reference/seasonality.json`, `reference/section_order.json` |

`ReferenceDataset` values are `STAPLES`, `ALIASES`, `SEASONALITY` and `SECTION_ORDER`. `LoadFailure.Category` is defined in Error Handling.

**Asset JSON layout** (AD8). Every file is a JSON object whose only allowed top-level fields are the ones shown; any other field is `UNEXPECTED_FIELD`. Every entry object has exactly the fields shown, all required.

```json
// reference/staples.json
{ "entries": [ { "key": "water", "basis": "PER_100G", "energyKcal": 0, "proteinG": 0, "fatG": 0, "carbohydrateG": 0 } ] }

// reference/aliases.json
{ "entries": [ { "variant": "courgettes", "canonicalKey": "courgette" } ] }

// reference/seasonality.json
{ "entries": [ { "key": "strawberry", "inSeasonMonths": [6, 7, 8], "substitutions": ["raspberry"] } ] }

// reference/section_order.json
{
  "sections": [ { "name": "Fresh Food", "walkIndex": 0 }, { "name": "Bakery", "walkIndex": 10 } ],
  "mappings": [ { "key": "water", "section": "Drinks" } ]
}
```

(The comments above are only annotations for this document. The shipped assets contain no comments, and the values shown are illustrations, not curated content.)

**Relationships:**
- `SectionMapping.section` → `SectionOrderEntry.name` (many-to-one, enforced at load).
- `AliasEntry.canonicalKey` and `SeasonalityEntry.substitutions[*]` → the known-key set (AD11, test-only).
- Every staples key → one `SectionMapping` (R4 AC4, test-only).

**Persistence:** none at runtime. Assets are read-only APK content, versioned in git (Q2). No Room table, no change to `PantryDatabase` or `app/schemas/` (R1 AC9).

## Interfaces

```kotlin
package ie.pantry.data.reference

// I1 — Lookup.kt
/** Three-way result of a store lookup: Lookup.Found, Lookup.Absent or LoadFailed. */
sealed interface LookupResult<out T>

/** Two-way result of querying a loaded table: found or explicitly absent. */
sealed interface Lookup<out T> : LookupResult<T> {
    data class Found<out T>(val value: T) : Lookup<T>
    data object Absent : Lookup<Nothing>
}

sealed interface LoadResult<out T> {
    data class Ready<out T>(val table: T) : LoadResult<T>
}

/** A dataset that could not be loaded. It is both a load result and a store-lookup result (AD1). */
data class LoadFailed(val failure: LoadFailure) : LoadResult<Nothing>, LookupResult<Nothing>
```

```kotlin
// I2 — ReferenceTables.kt (internal constructors; inputs copied and frozen, AD7)
class StaplesTable internal constructor(entries: List<StaplesEntry>) {
    /** Unmodifiable, file order. */
    val entries: List<StaplesEntry>
    /** Exact match on [key]; never throws; a miss is [Lookup.Absent]. */
    fun lookup(key: String): Lookup<StaplesEntry>
}

class AliasTable internal constructor(entries: List<AliasEntry>) {
    val entries: List<AliasEntry>
    /** Exact match on the already-normalised [variant] (Q3); Found carries the mapped canonical key. */
    fun lookup(variant: String): Lookup<String>
}

class SeasonalityTable internal constructor(entries: List<SeasonalityEntry>) {
    val entries: List<SeasonalityEntry>
    fun lookup(key: String): Lookup<SeasonalityEntry>
}

class SectionOrderTable internal constructor(sections: List<SectionOrderEntry>, mappings: List<SectionMapping>) {
    /** Unmodifiable, file order (not re-sorted). */
    val sections: List<SectionOrderEntry>
    val mappings: List<SectionMapping>
    /** The section [key] maps to, with its display-order index; Absent when unmapped (F11's terminal bucket). */
    fun sectionFor(key: String): Lookup<SectionOrderEntry>
}
```

```kotlin
// I3 — AssetSource.kt
fun interface AssetSource {
    /**
     * Opens the asset at [path] (e.g. `reference/staples.json`). Blocking; called only off the main thread.
     * @throws java.io.FileNotFoundException when the asset does not exist
     * @throws java.io.IOException on any other read failure
     */
    fun open(path: String): java.io.InputStream

    companion object {
        /** AppContainer's default: opens nothing, every open throws FileNotFoundException (constant message). */
        val UNAVAILABLE: AssetSource
    }
}

// AndroidAssetSource.kt
class AndroidAssetSource(private val assets: android.content.res.AssetManager) : AssetSource
```

```kotlin
// I4 — DatasetParser.kt
internal object DatasetParser {
    fun parseStaples(bytes: ByteArray): LoadResult<StaplesTable>
    fun parseAliases(bytes: ByteArray): LoadResult<AliasTable>
    fun parseSeasonality(bytes: ByteArray): LoadResult<SeasonalityTable>
    fun parseSectionOrder(bytes: ByteArray): LoadResult<SectionOrderTable>
}

// I5 — DatasetLoader.kt
/** Opens [dataset]'s asset via [source], reads all bytes, closes the stream and applies [parse]. Never throws Exception. */
internal fun <T : Any> loadDataset(
    source: AssetSource,
    dataset: ReferenceDataset,
    parse: (ByteArray) -> LoadResult<T>,
): LoadResult<T>
```

```kotlin
// I6 — ReferenceDataStore.kt
class ReferenceDataStore(
    source: AssetSource,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) {
    suspend fun staples(): LoadResult<StaplesTable>
    suspend fun aliases(): LoadResult<AliasTable>
    suspend fun seasonality(): LoadResult<SeasonalityTable>
    suspend fun sectionOrder(): LoadResult<SectionOrderTable>

    suspend fun stapleFor(key: String): LookupResult<StaplesEntry>
    suspend fun canonicalKeyForVariant(variant: String): LookupResult<String>
    suspend fun seasonalityFor(key: String): LookupResult<SeasonalityEntry>
    suspend fun sectionFor(key: String): LookupResult<SectionOrderEntry>
}
```

```kotlin
// I7 — ie.pantry.di.AppContainer (modified)
class AppContainer(
    val database: PantryDatabase,
    thumbnailStore: ThumbnailStore,
    thumbnailProcessor: ThumbnailProcessor,
    referenceAssets: AssetSource = AssetSource.UNAVAILABLE,
) {
    // ...F1 properties unchanged...
    val referenceData: ReferenceDataStore = ReferenceDataStore(referenceAssets)

    companion object {
        fun production(app: Application, clock: Clock = Clock.systemUTC()): AppContainer  // passes AndroidAssetSource(app.assets)
    }
}

// I8 — ie.pantry.PantryApplication (modified)
open class PantryApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { createContainer() }
    protected open fun createContainer(): AppContainer = AppContainer.production(this)
}
```

**Contracts:**
- **Preconditions:** none on lookups. Any string is a valid query, including `""`. The store may be called from any thread, including main.
- **Postconditions:** the first call to any accessor of a dataset starts that dataset's single load. Every later call, concurrent or not, returns the same `LoadResult` instance for the life of the store (R6 AC3, AC4; R1 AC8). A `Ready` table never changes. `lookup` is O(1) through a private `HashMap`.
- **Side effects:** at most one `AssetSource.open` per dataset per store. At most one `Log.w` per dataset, on failure only. There are no writes of any kind (R7 AC3).
- **Nullability:** no public function returns `null`. Absence is `Lookup.Absent` and failure is `LoadFailed`. The only nullable fields are `LoadFailure.array`, `entryIndex` and `field`, which are null for failures at document or asset level.
- **Threading:** `loadDataset` and `DatasetParser` block and run only on `ioDispatcher`. Table `lookup` is pure and safe from any thread without locking (ARCHITECTURE C2 Key Concerns). `container` construction and `ReferenceDataStore` construction do no I/O (R6 AC1).
- **Exceptions:** no store or table function throws for a miss or a load failure. An `Error` (for example `OutOfMemoryError`) and a caller's own `CancellationException` still propagate.

## Error Handling

- **Strategy:** sealed-class outcomes (AD1). Load failures are values (`LoadFailed`), never exceptions that cross the store boundary. Inside the parser a private stackless `ParseAbort` unwinds to one catch site. `JSONException`, `IOException` and every other `Exception` are caught and replaced with a content-free `LoadFailure`. Their message and cause are **never** kept, logged or chained (AD3; the AOSP `syntaxError` embeds the full asset text).
- **Custom exceptions / error types:** `LoadFailed(LoadFailure)`, `LoadFailure.Category`, and the private `ParseAbort` (never escapes `DatasetParser`).

| Category | Raised when | Position carried |
|---|---|---|
| `ASSET_ABSENT` | `AssetSource.open` throws `FileNotFoundException`. This includes `AssetSource.UNAVAILABLE`, AppContainer's default | none |
| `ASSET_UNREADABLE` | Any other `IOException` from open or read (spec R1 treats this as "absent or invalid"; a separate category keeps the log line truthful) | none |
| `INVALID_JSON` | Malformed UTF-8 (`CharacterCodingException` from the strict decoder, which runs inside `DatasetParser` after the read completes, so it is never confused with an I/O failure); any `JSONException` from the tokener (syntax error, `NaN` in an object value); trailing content after the closing brace (`nextClean() != '\u0000'`); an array hole (`JSONArray.get(i)` throws on a Java `null`) | `array`/`entryIndex` only for the array-hole case; null for every tokener error, because the tokener reports no position |
| `MISSING_FIELD` | `!obj.has(field)` for a required field or top-level array | `array`, `entryIndex`, `field` |
| `UNEXPECTED_FIELD` | An entry or the document has a name outside its allowed set | `array`, `entryIndex` (`field` = null) |
| `WRONG_TYPE` | Value fails its `is` check: string vs number vs boolean vs array vs object; `JSONObject.NULL`; whole-number field holding a `Double` (`3.0`) or `Long`; top-level value not a JSON object | `array`, `entryIndex`, `field` (null for the top level) |
| `INVALID_ENUM_VALUE` | `basis` string not exactly a `NutritionBasis` name (case-sensitive) | `array`, `entryIndex`, `field = "basis"` |
| `INVALID_VALUE` | A nutrient that is not finite or is < 0; a month outside 1..12, repeated, or an empty `inSeasonMonths`; `walkIndex` < 0 or equal to an earlier section's; a mapping's `section` not in `sections` | `array`, `entryIndex`, `field` |
| `DUPLICATE_KEY` | A repeated staples `key`, alias `variant`, seasonality `key`, section `name` or mapping `key` | `array`, `entryIndex` of the second occurrence, `field` (the key field's name) |
| `INTERNAL_ERROR` | Any other non-cancellation `Exception` raised inside `loadDataset`, including from `parse` (a defect in F2's own code). It is caught so R1 AC8's "no exception escapes" still holds | none |

- **Logging:** `android.util.Log.w` with tag `"PantryRef"`, **once per failed load** (not per lookup), in exactly the form `dataset=<ReferenceDataset.name> category=<Category.name>`. Nothing else is logged: no entry position, no field, no cause type, no asset text (R1 AC8 says the only log line carries the dataset name and a failure category). No logging on success.
- **Unhandled paths:** only `Error` subclasses and a cancelled caller's own `CancellationException`. A cancelled caller does not cancel the shared load (AD6).
- **User-facing errors:** none in F2. Callers fall back to their existing "no data" states (Q7). How they render is up to each consumer's spec (`[SEAL-03]`).

## Testing Strategy

- **Framework:** JUnit 4 with `@RunWith(RobolectricTestRunner::class)` (`sdk=35` from `app/src/test/resources/robolectric.properties`), `kotlin.test` assertions, and `kotlinx-coroutines-test` `runTest` for suspending code. The Compose test adds `androidx.compose.ui:ui-test-junit4` (AD12). Every F2 test runs under Robolectric, because the Android-stub `org.json` in `android.jar` is not usable on a plain JVM. Robolectric supplies the framework implementation [ASSUMPTION: Robolectric's `android-all` for SDK 35 carries the AOSP `org.json` whose behaviour AD9 cites from the Android 14 source; retired by the fixture matrix and the leniency characterisation fixture below].
- **Test location:** `app/src/test/java/ie/pantry/…`, mirroring main. Fixtures are in `app/src/test/resources/reference/fixtures/` and are read from the classpath (a refinement of the spec's `reference/*.json`: fixtures get their own subdirectory so they are not mixed with `expected_sections.txt`).
- **Real-time tests:** `ReferenceLoadThreadingTest` and `LoadFailureCachingTest` use `runBlocking` with `withTimeout` on real `Dispatchers.Default` coroutines, not `runTest`, because `runTest`'s virtual clock would fire a timeout instantly. The 32-caller concurrency test waits on a source-side counter (exactly one open blocked at the gate) with a bounded poll, not a timed settle, before the gate releases the read. Its body is parameterised over a lookup lambda, and the negative control runs the same body with a lambda that calls the load body once per call and must fail.
- **Fixture coverage:** the strict-parse matrix is table-driven and covers every dataset (`staples`, `aliases`, `seasonality`, `section_order`) for `MISSING_FIELD`, `UNEXPECTED_FIELD`, `WRONG_TYPE`, `INVALID_VALUE` and the document-level failures, plus `INVALID_ENUM_VALUE` where a dataset has an enum. Characterisation fixtures pin the `org.json` behaviour this design relies on (`NaN`, an array hole, trailing content, octal `010`, a NUL after the closing bracket); a fixture asserting the loader's actual result replaces any AD9 claim it cannot confirm, and DR1's retirement covers them. Every matrix row states an expected (array, entryIndex, field) tuple, including top-level and element-level failures (a missing or unexpected top-level array, `entries` not an array, a non-Int month element, a non-string substitution). The matrix also holds rows for boundary inputs: a zero-byte or whitespace-only asset and a top-level `null` or scalar (`INVALID_JSON` or `WRONG_TYPE` as the parser reports them), a `Long`-range month (`WRONG_TYPE`), months 1 and 12, `walkIndex` 0, `-0.0` and `1e308` (all accepted at load time), and one characterisation row per leniency class AD9 relies on (comments, single quotes, unquoted names, octal `010`, NUL after the closing bracket, `NaN`, `1e999`). The `expected_sections` comparison is a helper taking the file text, so `ShippedDatasetsTest` and a matrix row can feed it a stale or extra-line text and prove the set comparison can fail, and `seasonality_one_plus_extra` gives a known length that partly covers consistently dropped entries.
- **Sentinel coverage:** `Sentinels.INGREDIENT` is planted in string values of the `DUPLICATE_KEY`, `UNEXPECTED_FIELD`, bad-enum and `WRONG_TYPE` fixtures and in a forced `INTERNAL_ERROR`; tests assert on `LoadFailure.toString()` and captured `Log` output (the `INTERNAL_ERROR` case is asserted on `toString()` only, because the store's parsers are fixed). `Sentinels.INGREDIENT` has spaces and mixed case, so it is planted only in parser fixtures, never in shipped-content tests.
- **Mocking approach:** no mocking library. Hand-written test doubles in `app/src/test/java/ie/pantry/testutil/AssetSources.kt`:
  - `FixtureAssetSource(map: Map<String, String>)`: maps an asset path to a classpath resource. A path that is not mapped throws `FileNotFoundException`.
  - `RecordingAssetSource(delegate)`: records each opened path and `Thread.currentThread()`, thread-safely.
  - `BlockingAssetSource`: `started` and `release` latches. `open` records its thread, counts down `started`, then waits on `release` for at most 30 s, then throws `FileNotFoundException`. It exposes `awaitStarted(timeout)`, `completed` and `release()`.
  - `GatedAssetSource(delegate)`: holds every open until `gate.countDown()`, so that concurrent callers pile up behind the first load.
- **Naming convention:** backtick-quoted descriptive names, as in F1 (e.g. `` `staples miss is absent and distinct from zero-valued water`() ``).

**Test classes and what each must cover:**

| Test class | Covers | Notes |
|---|---|---|
| `DatasetLoaderTest` | R1 AC4–AC7, R5 AC5, `[SEAL-19]` | Runs the fixture matrix below through `loadDataset(FixtureAssetSource, …)`. Each failure case asserts `category`, `dataset`, `array`, `entryIndex` and `field` exactly. R1 AC4 also asserts that the result is **not** `Ready`, so no defaulted `0`/`""` entry can exist |
| `ReferenceLookupTest` | R2 AC1–AC5, R1 AC6 | One test per dataset for found and absent (`water` with zero values vs a miss: `assertNotEquals`, plus an exhaustive `when` that shows no nutrient is readable from `Absent`). Alias `Absent` is distinct from `Found("")` and `Found(<variant>)`. Seasonality `Absent` is distinct from `Found(entry with empty substitutions)`. Section `Absent` is distinct from `Found(first section)`. A load failure through the store is `LoadFailed`, never `Absent` |
| `LoadFailureCachingTest` | R1 AC8 | For both an absent asset and an invalid one: two lookups both return an equal `LoadFailed`, `RecordingAssetSource` shows one open, `ShadowLog.getLogsForTag("PantryRef")` has exactly one entry equal to `dataset=STAPLES category=…`, and neither the log nor `LoadFailure.toString()` contains `Sentinels.INGREDIENT` (planted in the fixture) |
| `ReferenceLoadThreadingTest` | R6 AC2–AC4 | **Off main:** with `BlockingAssetSource`, `CoroutineScope(Dispatchers.Unconfined).async { store.stapleFor("water") }` runs on the test (main) thread up to its first suspension. The test asserts the returned `Deferred` is not completed while `awaitStarted(5 s)` is true (the caller regained control before the read completed), and that the recorded open thread is not the test's main thread (`Looper.getMainLooper().thread`). It then releases the source and awaits with `withTimeout(5 s)`. **Once under concurrency:** 32 coroutines on `Dispatchers.Default` against a `GatedAssetSource`; open count is 1 and all 32 `Ready.table` are the same instance (`assertSame`). **Never again:** further calls after loading leave the open count at 1 |
| `ReadOnlySurfaceTest` | R7 AC1–AC4, AD2 purity | Reflection: every declared field of the entry types, tables, results, `LoadFailure` and `ReferenceDataStore` is `final`, and no method is named `set*`, `add*`, `remove*`, `put*`, `clear*` or `replace*`. Downcast: each `entries`/`sections`/`mappings`/`inSeasonMonths`/`substitutions` cast to `MutableList`/`MutableSet` and hit with `add`/`remove`/`clear` throws `UnsupportedOperationException`, and a later lookup is unchanged. It also checks that a table built from caller-owned mutable lists is unaffected when those lists are mutated afterwards. Source inspection (reads `src/main/java/ie/pantry/data/reference/*.kt` from the `app/` working directory): no file contains `FileOutputStream`, `openFileOutput`, `SharedPreferences`, `.edit(`, `writeText`, `writeBytes`, `@Insert`, `@Update` or `Dao` (R7 AC3). The six pure files (FC1's five plus `AssetSource.kt`) contain no `import android.` and no `import org.json` |
| `ShippedDatasetsTest` | R1 AC1, AC3; R3 AC1–AC3; R4 AC1–AC5; R5 AC1–AC4; AD11 | Loads the real assets through `AndroidAssetSource(context.assets)` (Robolectric exposes the merged debug-variant assets, as F1's `MigrationTestHelper` reading debug assets already relies on). The four files must exist under `src/main/assets/reference/` and decode as strict UTF-8. Loaded counts must equal the raw array lengths. Plus the count band, the `PER_100G` basis, key surface form, section completeness and set equality with `expected_sections.txt`, the seasonality and alias policy rules from AD10, and the AD11 cross-checks. No fixed count is asserted for seasonality or aliases |
| `ProvenanceRecordTest` | R3 AC4 | Finds the repo root (AD13). The file exists, has the four exact `## ` headings, and each section has a `- **Retrieved:** YYYY-MM-DD` line that parses with `LocalDate.parse` |
| `AppContainerTest` (modified) | R6 AC1, spec Modified Files | Keeps the three F1 tests. New: `AppContainer(db, store, processor, recording)` records zero opens after construction. A container built with F1's three-argument call returns `LoadFailed(ASSET_ABSENT)` from `stapleFor` and does not throw |
| `FirstPaintNotBlockedTest` (blocked read) | R6 AC5 | AD15's sequence, with `BlockingReadPantryApplication`. It proves the seam works and that the placeholder is displayed while a dataset read is in flight and blocked. It fails if a read on the main thread is added to Application or Activity startup, because the placeholder would then never be displayed; it cannot detect a startup read on a background thread, which never blocks the main thread, so the companion test below covers that |
| `FirstPaintNotBlockedTest` (companion: `launching MainActivity opens no dataset asset`) | R6 AC1, Q8 | With a recording source and no lookup issued, launch `MainActivity`, wait for Compose to go idle (bounded to a 10 s wall clock), and assert the source recorded zero opens. This detects a launch-time pre-warm or any startup read on any thread |

**Strict-parse fixture matrix** (`[DEF-02]`: tasks.md sizes it; each row is one fixture and one assertion):

| Fixture (`reference/fixtures/…`) | Expected |
|---|---|
| `staples_valid.json` (includes `water`, all zeros) | `Ready`, count = array length |
| `staples_empty.json` (`{"entries": []}`) | `Ready`, 0 entries |
| `staples_missing_energy.json` | `MISSING_FIELD`, `entries`, index 1, `energyKcal` |
| `staples_duplicate_key.json` | `DUPLICATE_KEY`, `entries`, index 2, `key` |
| `staples_energy_as_string.json` (`"energyKcal": "52"`) | `WRONG_TYPE`, `energyKcal` |
| `staples_key_as_number.json` | `WRONG_TYPE`, `key` |
| `staples_protein_null.json` | `WRONG_TYPE`, `proteinG` |
| `staples_bad_basis.json` (`"per_100g"`) | `INVALID_ENUM_VALUE`, `basis` |
| `staples_negative_fat.json` | `INVALID_VALUE`, `fatG` |
| `staples_unexpected_field.json` (`"fibreG"`) | `UNEXPECTED_FIELD`, `field` = null |
| `staples_trailing_content.json` (`{…} {}`) | `INVALID_JSON` |
| `staples_array_hole.json` (`[{…},]`) | `INVALID_JSON`, index 1 |
| `invalid_json.json` (truncated, holds `Sentinels.INGREDIENT`) | `INVALID_JSON`, and the sentinel is absent from the failure and the log |
| invalid UTF-8 bytes (generated in the test) | `INVALID_JSON` |
| top-level array (`[ … ]`) | `WRONG_TYPE`, no position |
| unmapped path (asset absent) | `ASSET_ABSENT` |
| `seasonality_fractional_month.json` (`3.0`) | `WRONG_TYPE`, `inSeasonMonths` |
| `seasonality_month_13.json`, `…_repeated_month.json`, `…_empty_months.json` | `INVALID_VALUE`, `inSeasonMonths` |
| `seasonality_duplicate_key.json`, `aliases_duplicate_variant.json` | `DUPLICATE_KEY` |
| `seasonality_one.json` / `seasonality_one_plus_extra.json` | R5 AC5: the extra key is `Absent` in the first and `Found` in the second |
| `section_order_fractional_index.json` (`2.5`) | `WRONG_TYPE`, `walkIndex` |
| `section_order_index_collision.json` | `INVALID_VALUE`, `sections`, `walkIndex` |
| `section_order_unlisted_section.json` | `INVALID_VALUE`, `mappings`, `section` |
| `section_order_duplicate_name.json`, `…_duplicate_mapping.json` | `DUPLICATE_KEY` (`sections` / `mappings`) |
| `lenient_comment.json` (a `//` comment inside a valid staples file) | `Ready`: a characterisation test that pins the accepted leniency (AD9) so a platform change is noticed |

- **Coverage expectations:** every public function in `ie.pantry.data.reference` has at least one happy-path and one failure- or absence-path test. Every `LoadFailure.Category` except `ASSET_UNREADABLE` and `INTERNAL_ERROR` is produced by at least one fixture. `ASSET_UNREADABLE` is covered by a source whose stream throws `IOException` mid-read. `INTERNAL_ERROR` is covered by a `parse` lambda that throws `IllegalStateException`, passed directly to `loadDataset`. Every GIVEN/WHEN/THEN in spec R1–R7 maps to a row in the table above. R1 AC2 (the release APK listing) and the release-manifest triage are covered by the spec's Commands block, not by a unit test. R1 AC9 is already asserted by F1's `SchemaShapeTest` (version 1, exactly seven tables) and by `git status app/schemas` at review.
- **Fixtures / test data:** fixtures stay small (two to five entries) and never mirror the shipped content, so F17 data edits do not break behaviour tests (spec Always Do). Each test builds its own store, and no store is shared between tests. `Sentinels` (F1's `testutil/Sentinels.kt`) supplies the leak probe.

## File Structure

```
pantry/                                                   (repository root)
├── docs/
│   └── reference-data-provenance.md                      — NEW (FC6): per-dataset Source/Retrieved, nutrition-database spot-check, no-ODbL statement, tesco.ie snapshot, developer-named seasonal calendar source, developer review
├── gradle/libs.versions.toml                             — MODIFIED (FC7): + androidx-compose-ui-test-junit4, androidx-compose-ui-test-manifest (versionless, BOM-managed)
└── app/
    ├── build.gradle.kts                                  — MODIFIED (FC7): testImplementation(platform(bom)), testImplementation(ui-test-junit4), debugImplementation(platform(bom)), debugImplementation(ui-test-manifest)
    └── src/
        ├── main/
        │   ├── assets/reference/                         — NEW directory (FC6)
        │   │   ├── staples.json
        │   │   ├── aliases.json
        │   │   ├── seasonality.json
        │   │   └── section_order.json
        │   └── java/ie/pantry/
        │       ├── PantryApplication.kt                  — MODIFIED (FC5): `open class`, `protected open fun createContainer()`
        │       ├── di/AppContainer.kt                    — MODIFIED (FC5): `referenceAssets` param (default UNAVAILABLE), `referenceData` val, production passes AndroidAssetSource(app.assets)
        │       └── data/reference/                       — NEW package
        │           ├── ReferenceEntries.kt               — FC1 (pure): DM1–DM5
        │           ├── Lookup.kt                         — FC1 (pure): Lookup, LookupResult, LoadResult, LoadFailed
        │           ├── LoadFailure.kt                    — FC1 (pure): LoadFailure + Category
        │           ├── ReferenceDataset.kt               — FC1 (pure): dataset enum + asset paths
        │           ├── ReferenceTables.kt                — FC1 (pure): the four tables
        │           ├── DatasetParser.kt                  — FC2 (org.json)
        │           ├── AssetSource.kt                    — FC3 (pure): interface + UNAVAILABLE
        │           ├── AndroidAssetSource.kt             — FC3 (android.content.res.AssetManager)
        │           ├── DatasetLoader.kt                  — FC3: loadDataset
        │           └── ReferenceDataStore.kt             — FC4 (android.util.Log)
        └── test/
            ├── java/ie/pantry/
            │   ├── data/reference/
            │   │   ├── DatasetLoaderTest.kt              — fixture matrix
            │   │   ├── ReferenceLookupTest.kt            — R2
            │   │   ├── LoadFailureCachingTest.kt         — R1 AC8
            │   │   ├── ReferenceLoadThreadingTest.kt     — R6 AC2–AC4
            │   │   ├── ReadOnlySurfaceTest.kt            — R7 + purity
            │   │   ├── ShippedDatasetsTest.kt            — R1 AC1/AC3, R3–R5, AD11
            │   │   └── ProvenanceRecordTest.kt           — R3 AC4
            │   ├── di/AppContainerTest.kt                — MODIFIED: zero opens on construction, default source
            │   ├── ui/
            │   │   ├── FirstPaintNotBlockedTest.kt       — R6 AC5
            │   │   └── BlockingReadPantryApplication.kt  — test Application subclass (AD5)
            │   └── testutil/
            │       ├── AssetSources.kt                   — Fixture/Recording/Blocking/Gated sources
            │       └── RepoPaths.kt                      — repo-root search (AD13)
            └── resources/reference/
                ├── expected_sections.txt                 — tesco.ie department snapshot (R4 AC5)
                └── fixtures/*.json                       — strict-parse fixture matrix
```

No change to `AndroidManifest.xml`, `PantryDatabase`, entities, DAOs or `app/schemas/`. The spec's Project Structure proposed `ReferenceEntries.kt`, `Lookup.kt`, `DatasetLoader.kt` and `ReferenceDataStore.kt`. This design keeps those names and adds `LoadFailure.kt`, `ReferenceDataset.kt`, `ReferenceTables.kt`, `DatasetParser.kt`, `AssetSource.kt` and `AndroidAssetSource.kt`, so that the pure files are separate from the ones that import `android.*` or `org.json` (AD2). The spec lists five test files. This design adds `LoadFailureCachingTest`, `ReadOnlySurfaceTest` and `ProvenanceRecordTest`, which split out criteria the spec assigned to those five.

## Dependencies

| Package | Purpose |
|---------|---------|
| `org.json` (Android framework; Robolectric `android-all` in tests) | Parsing the assets (Q1). No catalog entry |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` 1.7.3 (transitive, already on the main classpath) | Store scope, `Deferred`, `Dispatchers.IO` (AD6, AD12). No new alias |
| `androidx.compose.ui:ui-test-junit4` (BOM `2024.12.01` → 1.7.6), `testImplementation` | `createEmptyComposeRule`, `onNodeWithText`, `assertIsDisplayed` (Q9) |
| `androidx.compose.ui:ui-test-manifest` (BOM → 1.7.6), `debugImplementation` | Q9 as settled. It registers the Compose test host activity in the debug manifest. `FirstPaintNotBlockedTest` itself launches `MainActivity` |
| `androidx.test:core` 1.6.1 (existing) | `ActivityScenario`, `ApplicationProvider` |
| `org.robolectric:robolectric` 4.14.1 (existing) | `ShadowLog`, `@Config(application = …)` |

No JSON library, no `kotlin-reflect`, no HTTP client, no `implementation` addition. The two Compose test aliases are the Ask First items the user already approved (Q9).

## Integration Points

| Existing Module | Direction | Change Required | Details |
|-----------------|-----------|-----------------|---------|
| `ie.pantry.di.AppContainer` | Calls into F2 | Yes: new defaulted constructor parameter and new `val referenceData` | Load-bearing: every F1 test constructs it positionally, and the default keeps those calls compiling and I/O-free (AD4) |
| `ie.pantry.PantryApplication` | Calls into F2 via the container | Yes: `open class`, `protected open fun createContainer()` | Load-bearing: the manifest names it as the Application. The `lazy(SYNCHRONIZED)` once-only rule is kept, and `container` stays final |
| `ie.pantry.data.db.entity.NutritionBasis` | F2 calls into it | No | Reused as `StaplesEntry.basis` (Q5). A plain enum, no imports (verified) |
| `ie.pantry.ui.MainActivity` / `PlaceholderScreen` | Exercised by the Compose test | No | The test asserts on `R.string.app_name` ("Pantry") shown by `PlaceholderScreen` |
| `app/src/test/java/ie/pantry/testutil/Sentinels.kt` | Used by F2 tests | No | Leak probe for fixture content |
| `ie.pantry.ManifestPolicyTest`, `SchemaShapeTest` | Regression guards | No | Must stay green after the debug manifest gains the `ui-test-manifest` activity (AD14), and must confirm schema version 1 (R1 AC9) |
| F3 (C4) | Will call `AliasTable.lookup` / `canonicalKeyForVariant` | No F2 change | F3's tests load the shipped `aliases.json`/`staples.json` to reconcile keys (Q3) |
| F9 (C6), F8 (C10), F11 (C5) | Will call staples, seasonality and section lookups | No F2 change | Each must handle `Lookup.Absent` and `LoadFailed` separately (R2 intro; `[SEAL-03]`) |
| F17 | Edits the assets | No F2 change | A coverage increase is a JSON edit that `ShippedDatasetsTest` re-checks. No schema migration (R5 AC5) |

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| DR1 | Robolectric's SDK 35 `org.json` differs from the Android 14 source AD9 cites (for example a stricter or looser tokener), so a fixture's category differs from the matrix | Low | Low | The fixture matrix and the `lenient_comment.json` characterisation fixture run first (Implementation Sequence step 2). Any difference is fixed in the matrix, not by adding a JSON library (Ask First) |
| DR2 | `ui-test-junit4` under Robolectric waits for idle on the blocked read, or `ActivityScenario` plus `createEmptyComposeRule` does not find the Compose hierarchy, so `FirstPaintNotBlockedTest` hangs or fails for tooling reasons | Med | Med | Step 1 builds a smoke version (launch `MainActivity` under `BlockingReadPantryApplication` and assert the placeholder) before any store code. The blocking source's 30 s cap turns a hang into a failure. Fallback: start the blocked read on a plain `Thread` rather than a coroutine, which Compose idling cannot track |
| DR3 | `ui-test-junit4`'s transitive `androidx.test`/Espresso versions conflict with `androidx.test:core` 1.6.1 or Robolectric 4.14.1 | Med | Low | Resolved at step 1 with `./gradlew :app:testDebugUnitTest`. Gradle picks the highest version. If a pin is needed, that is a catalog change, raised as Ask First |
| DR4 | Coroutines skew: main compiles against 1.7.3, while unit tests run 1.9.0 (both verified in the build outputs), so behaviour under test is not exactly what ships | Low | Low | F2 uses only long-stable APIs (AD12). Already present in F1. A pinned coroutines alias remains available as a future Ask First item |
| DR5 | Curation (about 170 staples, about 40 seasonality keys, the section list and its mappings) blocks the store work or produces one large, hard-to-review data diff | High | Med | Phasing (Implementation Sequence): the store is built against fixtures and a small real seed first, and curation follows in reviewable per-dataset steps. The 160–180 band assertion is added in the curation step, so earlier steps stay green (`[DEF-01]`) |
| DR6 | `org.json` leniency admits a silently wrong value: a leading-zero month `010` parses as octal 8, an unquoted string value parses, or a duplicate field name keeps the last value | Low | Med | Accepted as spec RK8's parser-leniency class. Every value is also checked against its domain (AD10), and the shipped-content tests plus the developer review in the provenance record catch implausible values. Assets are developer-authored only |
| DR7 | Robolectric does not expose `app/src/main/assets/reference/` via `context.assets`, so `ShippedDatasetsTest` cannot load the real files | Low | Med | Evidence against: F1's migration harness already reads debug-variant merged assets under Robolectric with `isIncludeAndroidResources = true`. Fallback: a test-only file-backed `AssetSource` reading `src/main/assets/` relative to `app/` |
| DR8 | A later feature adds a startup read (for example a ViewModel that looks up a staple in `onCreate`), on the main thread or as a launch-time pre-warm | Med | Med | The companion test (launch with a recording source, no lookup, zero opens after idle) fails on a startup read on any thread, and the blocked-read test fails on one that blocks the main thread. Store lookups are `suspend` and run on IO, so a correct caller cannot block main |

## Implementation Sequence

1. **Tooling and seam first (FC5 hook + FC7 dependencies).** Add the two catalog aliases and `build.gradle.kts` lines, make `PantryApplication` `open` with `createContainer()`, and add `BlockingReadPantryApplication` plus a smoke `FirstPaintNotBlockedTest` that only asserts the placeholder is displayed under the test application. This step has the most uncertainty (DR2, DR3), so it is proven before anything depends on it. The smoke test uses F1's existing three-argument `AppContainer` (no asset source yet) and a plain blocked-thread probe, so it needs neither `AssetSource` nor the `referenceAssets` parameter, which arrive in steps 3 and 4; DR2 (idling) is retired only by the full blocked-read test in step 4, not by this smoke test. This step also greps the debug-merged manifest to record the component delta that `ui-test-manifest` adds (debug only). Exit: the full `./gradlew :app:testDebugUnitTest` is green, including `ManifestPolicyTest`.
2. **FC1 model and FC2 parser with the fixture matrix.** These are pure types and strict parsing, with no Android wiring. This retires DR1. Can run in parallel with step 1 once the file layout is agreed. tasks.md splits the fixture matrix into independently checkable rows (`[DEF-02]`).
3. **FC3 loader and FC4 store**, with `ReferenceLookupTest`, `LoadFailureCachingTest` and `ReferenceLoadThreadingTest`. Needs step 2.
4. **FC5 container wiring and the full blocked-read test.** Add `AppContainer.referenceAssets`/`referenceData` and the `AppContainerTest` additions, then finish `FirstPaintNotBlockedTest` (AD15) with its companion test. Also add `ReadOnlySurfaceTest`. Needs steps 1 and 3.
5. **FC6 small real seed.** Write all four assets with a small real seed: about 10 staples and their mappings, the **complete** section list (it is small, and R4 needs it whole), a few seasonality keys including one with substitutions, and a few aliases. Write `expected_sections.txt`, a provenance skeleton with all four headings and dates, `ShippedDatasetsTest` **without** the 160–180 band, and `ProvenanceRecordTest`. This proves the end-to-end path on real data before the large curation effort.
6. **FC6 full curation**, in per-dataset reviewable steps: staples to 160–180 from the prior CSV with the nutrition-database spot-check (the band assertion lands here), mappings for every staples key, the seasonality slice with months from the developer-named seasonal calendar (about 40 keys), aliases, and the provenance record completed with the developer's review. Only the assets and `ShippedDatasetsTest` change in this step.
7. **Closeout.** Run `./gradlew :app:testDebugUnitTest lintDebug assembleDebug assembleRelease`, the APK `unzip -l … | grep assets/reference/` listing (it prints 4 lines), and the release-merged-manifest greps (1/1/1/1, 2 exported, 0 `android.permission`) from the spec's Commands. Confirm `git status app/schemas` is clean.

## Open Questions

> All questions must be resolved before proceeding to the next phase.

No open questions. Every spec Decision Point and every concern routed to Design is decided in AD1–AD15. The remaining unverifiable items are tagged **[ASSUMPTION]** and assigned to a Risk with a retirement step.

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

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes                                |
|------|------------|-------|-------------|-----------|----------|--------|--------------------------------------|
| 1    | 2026-09-25 | 1     | 0           | 27        | 0        | 2      | tags=d0u0c1                          |
| 2    | 2026-09-25 | 1     | 1           | 21        | 0        | 5      | tags=d0u0c1                          |
| 3    | 2026-09-25 | 0     | 0           | 0         | 0        | 16     | converged (0 HIGH); tags=d0u0c0      |
| 4    | 2026-09-27 | 1     | 0           | 2         | 0        | 0      | tags=d0u0c1; upstream-panel 20d3d3ec |
| 5    | 2026-09-27 | 0     | 0           | 0         | 0        | 1      | converged (0 HIGH); tags=d0u0c0      |

### Sealed dispositions

- `[SEAL-01]` **sectionFor breaks the lookup naming and the shortcut shares…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - naming polish with no behavioural effect; the Tasks phase (tasks.md) can rename at implementation time.
- `[SEAL-02]` **Store scope is uncancellable so test containers leak a scope** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - AD6 now states test containers do not close it, by design; the scope holds no thread when idle.
- `[SEAL-03]` **No runtime size or depth cap; caps exist only in…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged - AD6 already records this as an accepted residual guarded by the shipped-content tests; the assets are static and developer-curated, so a runtime cap adds code for a case a test catches before release.
- `[SEAL-04]` **Several Consequences cells run statements together without…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged - editorial; the table cells are dense by convention and no reader is misled.
- `[SEAL-05]` **FC4 and AD6 name the load body differently** (pass 2, accepted-as-risk) — Defense: synthesizer-judged - loadAndLog is the wrapper that logs and loadDataset is the body it calls, so they are two functions, not one thing with two names; tasks.md fixes the final names.
- `[SEAL-06]` **Error policy is untested and the depth cap needs a…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged - the residual is documented in AD6; the depth definition (nesting levels of objects and arrays) is a test detail fixed in tasks.md.
- `[SEAL-07]` **ui-test-manifest host-activity assumption is unverified** (pass 2, accepted-as-risk) — Defense: synthesizer-judged - already tagged [ASSUMPTION] and retired by the debug-manifest delta recorded at Implementation Sequence step 1.
- `[SEAL-08]` **Fixture matrix table lacks the boundary and per-dataset…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the prose already commits to those rows and their expected tuples, and sizing the matrix into checkable rows is routed to tasks.md ([DEF-02] in the approved spec); tasks.md carries them.
- `[SEAL-09]` **Some matrix rows omit the full tuple and the top-level…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - tasks.md writes each row's exact expected tuple, and the parser's actual result for the top-level null, scalar and empty cases is pinned by the characterisation fixtures (DR1), which replace any hedged expectation.
- `[SEAL-10]` **Companion test has no way to show its recording source…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the recording-source test Application and a positive control (one lookup, exactly one open) are test construction detail that tasks.md specifies; the AD4 positive control already applies to the container-construction test.
- `[SEAL-11]` **Companion test's zero-opens-after-idle negative is racy for…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - it is a best-effort detector whose residual limit (a pre-warm that has not opened by idle) is recorded here; tasks.md adds a bounded drain step and a test-first red run, which exposes a racy test immediately, and Q8 already forbids a pre-warm.
- `[SEAL-12]` **Implementation Sequence step 2 runs the fixture matrix…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the sequencing is a high-level order; tasks.md points the step-2 rows at parser bytes and adds the loader rows in step 3.
- `[SEAL-13]` **32-caller test waits for one blocked open, not for all…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the negative control already catches a reload-per-caller bug and tasks.md adds a caller-started count to the main body when it writes the test.
- `[SEAL-14]` **Concurrency negative control is under-specified…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - how a must-fail control is expressed is a test-writing detail for tasks.md; the design commits to the control's existence and its per-call-load lambda.
- `[SEAL-15]` **Curation-policy checks have no negative controls** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - tasks.md extracts each policy check as a helper over tables and feeds it one violating fixture, as the design already does for the expected_sections helper.
- `[SEAL-16]` **AD10 precedence is ambiguous for two faults on different…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the stated order (missing, then unexpected, then type, then value, per entry in schema order) resolves it and tasks.md adds a multi-fault row pinning it.
- `[SEAL-17]` **GatedAssetSource's surface is fully described in AD15 but…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - editorial; AD15 is the authoritative description and tasks.md lists the final surface.
- `[SEAL-18]` **AD2 guidance implies staples() returns a table rather than…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - editorial; AD2 elsewhere states callers unwrap LoadResult, and tasks.md and the code make the type explicit.
- `[SEAL-19]` **AD10 array order for section_order.json is ambiguous…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the same AD10 sentence says sections parse before mappings, so parser order is what is meant; tasks.md pins it with a fixture.
- `[SEAL-20]` **AD7 purity check and AD4 positive control are not listed in…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - both are stated in their AD rows, which tasks.md reads with the Testing table.
- `[SEAL-21]` **ProvenanceRecordTest does not assert the per-section Source…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the record's exact shape is fixed with the file in the curation tasks in tasks.md; the criterion in the spec is met by the retrieval-date assertion.
- `[SEAL-22]` **NaN as an array element or top-level scalar is WRONG_TYPE…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - a characterisation row for NaN in an array is added when tasks.md builds the matrix; the behaviour is already stated as a Double, which is WRONG_TYPE.
- `[SEAL-23]` **ui-test-manifest debug-manifest exported test host remains…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - already tagged [ASSUMPTION] and retired by the debug-manifest delta recorded at Implementation Sequence step 1; no new evidence.
- `[SEAL-24]` **The `walkIndex` field/identifier name still reads as a…** (pass 5, accepted-as-risk) — Defense: the user explicitly instructed that the `walkIndex` code identifier stay unchanged (only prose describing its meaning was corrected); AD8 already marks any future rename as an Ask First item, and both panelists confirm this is cosmetic with no behavioural or contract effect.

### Deferred dispositions

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `ab261c9ce032c87d`
- **Hash basis:** v2