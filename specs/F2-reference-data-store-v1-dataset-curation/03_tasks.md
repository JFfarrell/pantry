# Tasks: Reference Data Store & v1 Dataset Curation

**Spec:** `specs/F2-reference-data-store-v1-dataset-curation/01_spec.md`
**Design:** `specs/F2-reference-data-store-v1-dataset-curation/02_design.md`

## Summary

| Task | Description | Requirement | Dependencies | Parallel | Status |
|------|-------------|-------------|--------------|----------|--------|
| T1 | Compose UI-test catalog aliases and test/debug build lines | R6 | None | Yes (with T3–T11) | Done |
| T2 | Open `PantryApplication` hook, test application and smoke first-paint test | R6 | T1 | Yes (with T3–T11) | Done |
| T3 | Result, failure, dataset and entry types | R1, R2 | None | Yes (with T1, T2) | Done |
| T4 | Immutable tables with found/absent lookups | R2, R4, R7 | T3 | Yes (with T1, T2) | Done |
| T5 | `DatasetParser` core and staples entry-level matrix rows | R1 | T4 | Yes (with T1, T2, T21) | Done |
| T6 | Document-level and top-level structure matrix rows | R1 | T5 | Yes (with T1, T2, T21) | Done |
| T7 | Boundary and `org.json` characterisation matrix rows | R1 | T6 | Yes (with T1, T2, T21) | Done |
| T8 | Alias parsing and its matrix rows | R1, R5 | T7 | Yes (with T1, T2, T21) | Done |
| T9 | Seasonality parsing and its matrix rows | R1, R5 | T8 | Yes (with T1, T2, T21) | Done |
| T10 | Section-order parsing and its matrix rows | R1, R4 | T9 | Yes (with T1, T2, T21) | Done |
| T11 | Multi-fault precedence rows and table-invariant parity | R1 | T10 | Yes (with T1, T2, T21) | Done |
| T12 | `AssetSource` seam, `loadDataset` and loader rows | R1 | T11 | Yes (with T18, T19, T20, T21) | Done |
| T13 | `ReferenceDataStore`, failure caching and store-level lookups | R1, R2, R6 | T12 | Yes (with T18–T21) | Done |
| T14 | Blocking and gated sources and `ReferenceLoadThreadingTest` | R6 | T13 | Yes (with T15, T17–T23) | Done |
| T15 | `AppContainer` wiring and `AppContainerTest` additions | R6 | T13 | Yes (with T14, T17–T23) | Done |
| T16 | Full blocked-read first-paint test and companion zero-opens test | R6 | T2, T14, T15 | Yes (with T17–T23) | Done |
| T17 | `ReadOnlySurfaceTest` and repo-root helper | R7 | T13 | Yes (with T14, T15, T16, T18–T21) | Done |
| T18 | Staples and alias seed assets and provenance skeleton | R3, R5 | T11 | Yes (with T12–T17, T21) | Done |
| T19 | Complete section list, seed mappings and `expected_sections.txt` | R4 | T18 | Yes (with T12–T17, T21) | Done |
| T20 | Seasonality seed slice | R5 | T19 | Yes (with T12–T17, T21) | Done |
| T21 | Curation-policy helpers with negative controls | R3, R4, R5 | T4 | Yes (with T5–T20) | Done |
| T22 | Shipped-content tests and `ProvenanceRecordTest` | R1, R3, R4, R5 | T12, T17, T20, T21 | Yes (with T14, T15, T16) | Done |
| T23 | Staples full set from the prior CSV, with a mapping for every key | R3, R4 | T22 | Yes (with T14, T15, T16) | Done |
| T24 | Staples 160–180 count-band assertion | R3 | T23 | Yes (with T25, T26, T27) | Done |
| T25 | Nutrition database spot-check record | R3 | T23 | Yes (with T24, T26, T27) | Done |
| T26 | Seasonality slice to about 40 Irish keys | R5 | T23 | Yes (with T24, T25) | Done |
| T27 | Final alias seed | R5 | T23, T26 | Yes (with T24, T25) | Done |
| T28 | Provenance record completion and developer review | R3, R4, R5 | T24, T25, T26, T27 | No | Done |
| T29 | Feature closeout: full suite, lint, release APK and manifest checks | R1, R6, R7 | T1–T28 | No | Done |

## Phase 1: Tooling and Test Seam (FC5 hook, FC7 dependencies)

### - [x] T1: Compose UI-test catalog aliases and test/debug build lines

- **Requirement:** R6
- **Description:** Add the two versionless catalog aliases `androidx-compose-ui-test-junit4` and `androidx-compose-ui-test-manifest` (AD12) and the four `app/build.gradle.kts` lines `testImplementation(platform(libs.androidx.compose.bom))`, `testImplementation(libs.androidx.compose.ui.test.junit4)`, `debugImplementation(platform(libs.androidx.compose.bom))` and `debugImplementation(libs.androidx.compose.ui.test.manifest)`, then record the debug-merged manifest's component delta that `ui-test-manifest` adds as a completion note on this task (retires the AD14 `[ASSUMPTION]`, design `[SEAL-07]`/`[SEAL-23]`).
- **Files:**
  - Read: `specs/F2-reference-data-store-v1-dataset-curation/02_design.md` — AD12 (aliases, BOM management), AD14 (release baseline), DR3 (transitive `androidx.test` conflict)
  - Modify: `gradle/libs.versions.toml`
  - Modify: `app/build.gradle.kts`
- **Dependencies:** None
- **Parallel:** Yes (with T3–T11) — touches only the catalog and build script; design Implementation Sequence lets step 2 run beside step 1
- **Acceptance Criteria:**
  - GIVEN the catalog and the app build script
    WHEN they are inspected
    THEN the two Compose test aliases are versionless (BOM-managed), are used only as `testImplementation`/`debugImplementation`, and no `implementation` line, JSON library, `kotlin-reflect` or HTTP client is added (spec Boundaries, Q1, Q9)
  - GIVEN the full unit-test suite after the dependency change
    WHEN `./gradlew :app:testDebugUnitTest` runs
    THEN every F1 test passes, including `ManifestPolicyTest` against the debug manifest that now carries the test host activity (DR3 retired)
  - GIVEN the release-merged manifest after the change
    WHEN the spec's Commands greps run
    THEN it still holds one activity, one service, one provider and one receiver, two `android:exported="true"` and zero `android.permission` entries (Network Exposure Triage branch (a))
- **Tests:** Not applicable — build configuration only; the F1 suite is the regression check (see Verification).
- **Verification:** `./gradlew :app:testDebugUnitTest :app:processReleaseMainManifest :app:processDebugMainManifest` succeeds; `for p in 'androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }' 'androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }'; do grep -qF "$p" gradle/libs.versions.toml || echo "missing: $p"; done` prints nothing; `for p in 'testImplementation(platform(libs.androidx.compose.bom))' 'testImplementation(libs.androidx.compose.ui.test.junit4)' 'debugImplementation(platform(libs.androidx.compose.bom))' 'debugImplementation(libs.androidx.compose.ui.test.manifest)'; do grep -qF "$p" app/build.gradle.kts || echo "missing: $p"; done` prints nothing; `grep -c '^    implementation(' app/build.gradle.kts` prints `6` (unchanged from F1); `grep -nE '"[^"]+:[^"]+:[0-9][^"]*"' app/build.gradle.kts` and `grep -niE 'okhttp|ktor|retrofit|serialization|moshi|gson|kotlin-reflect' gradle/libs.versions.toml app/build.gradle.kts` print nothing; with `M=app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`, `for tag in activity service provider receiver; do echo "$tag: $(grep -c "<$tag" "$M")"; done` prints 1 for each, `grep -c 'android:exported="true"' "$M"` prints `2`, `grep -c 'uses-permission android:name="android.permission' "$M"` prints `0`; with `D=app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`, record `grep -c '<activity' "$D"`, `grep -o 'android:name="[^"]*Activity"' "$D"` and `grep -c 'android:exported="true"' "$D"` in a completion note (expected **[ASSUMPTION]**: a second, exported `androidx.activity.ComponentActivity` and no new permission).
- **Completion note (2026-09-26):** Debug-merged manifest delta from `ui-test-manifest`: exactly one added component, `androidx.activity.ComponentActivity` (`android:exported="true"`, no permission, no intent filter), present only in the debug variant. The release-merged manifest is unchanged from the F1 baseline (one activity, service, provider and receiver; two `android:exported="true"`; zero `android.permission` entries). `ManifestPolicyTest` and the full F1 suite stayed green. Retires the AD14 assumption.

### - [x] T2: Open `PantryApplication` hook, test application and smoke first-paint test

- **Requirement:** R6
- **Description:** Make `PantryApplication` an `open class` whose final `container` stays `by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { createContainer() }` with a new `protected open fun createContainer(): AppContainer = AppContainer.production(this)` (I8, AD5), add a test-only `BlockingReadPantryApplication` that overrides the hook with F1's three-argument `AppContainer(PantryDatabase.createInMemory(this, Clock.systemUTC()), ThumbnailStore(filesDir), ThumbnailProcessor())` and counts hook calls, and add a smoke `FirstPaintNotBlockedTest` (registered with `@Config(application = BlockingReadPantryApplication::class)`, `createEmptyComposeRule()` plus `ActivityScenario.launch(MainActivity::class.java)`) that proves the placeholder is displayed under the test application while a plain probe thread is blocked on a latch (bounded to 30 s, released in `@After`).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/di/AppContainer.kt` — three-argument constructor and `production(app, clock)`
  - Read: `app/src/main/java/ie/pantry/ui/PlaceholderScreen.kt` — shows `R.string.app_name` ("Pantry")
  - Modify: `app/src/main/java/ie/pantry/PantryApplication.kt`
  - Create: `app/src/test/java/ie/pantry/ui/BlockingReadPantryApplication.kt`
  - Create: `app/src/test/java/ie/pantry/ui/FirstPaintNotBlockedTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T3–T11) — disjoint files; the smoke test needs no `AssetSource` (design Implementation Sequence step 1)
- **Acceptance Criteria:**
  - GIVEN `BlockingReadPantryApplication` registered through `@Config(application = …)`
    WHEN `container` is read twice
    THEN `createContainer()` has run exactly once and both reads return the same instance, so the lazy once-only rule survives the hook (R6 AC5 seam; F1 `AppContainerTest` still green)
  - GIVEN the test application and a probe thread blocked on a latch
    WHEN `MainActivity` is launched under Robolectric with `createEmptyComposeRule()`
    THEN the node with text "Pantry" is displayed while the probe thread is still blocked (DR2 tooling smoke; DR2's idling risk is retired only by T16)
- **Tests:**
  - `` `test application supplies the container through createContainer exactly once`() `` (in `FirstPaintNotBlockedTest.kt`)
  - `` `smoke placeholder is displayed under the test application while a probe thread is blocked`() ``
  - File: `app/src/test/java/ie/pantry/ui/FirstPaintNotBlockedTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.FirstPaintNotBlockedTest" --tests "*.AppContainerTest"`; `grep -c '^open class PantryApplication' app/src/main/java/ie/pantry/PantryApplication.kt` and `grep -c 'protected open fun createContainer' app/src/main/java/ie/pantry/PantryApplication.kt` each print `1`.

## Phase 2: Reference Model and Strict Parser (FC1, FC2)

Every matrix row below is one fixture (under `app/src/test/resources/reference/fixtures/`, or bytes generated in the test where noted) and one named test asserting the exact `(category, dataset, array, entryIndex, field)` tuple through one shared assertion helper that also asserts `LoadFailure.toString()` contains no `Sentinels.all` value (`[DEF-02]`). In T5–T11 the rows call `DatasetParser.parseX(bytes)` directly on classpath bytes: `AssetSource` and `loadDataset` do not exist until T12 (design `[SEAL-12]`). A tuple written `(—, —, —)` means `array`, `entryIndex` and `field` are all null. `Sentinels.INGREDIENT` is planted in a string value of every `DUPLICATE_KEY`, `UNEXPECTED_FIELD`, `INVALID_ENUM_VALUE` and `WRONG_TYPE` fixture and in `invalid_json.json`, except the fixtures with no string value to hold it (`top_level_scalar`, `top_level_null`, `top_level_nan`, and `staples_entry_not_object.json`, whose only entry is the number 42, so an extra string field would trigger `UNEXPECTED_FIELD` first); for `staples_key_as_number.json` the key is the offending value and `basis` must stay valid, so the sentinel goes in the `key` string of a second, valid sibling entry; for a fixture such as `staples_energy_as_string.json` the sentinel goes in the entry's key string, not in the offending value.

**Characterisation rule (DR1).** Rows marked *characterisation* pin `org.json` behaviour the design reads from the AOSP source. If Robolectric's `org.json` gives a different result, update that row's expected tuple to the observed one and log an Implementation Deviation; never add a JSON library (Ask First). Rows that assert a spec R1 rule (no coercion, no default) are not characterisation: a different result there is a parser defect to fix.

### - [x] T3: Result, failure, dataset and entry types

- **Requirement:** R1, R2
- **Description:** Create the pure FC1 types with no `android.*` or `org.json` import: `LookupResult`, `Lookup.Found`/`Lookup.Absent` (a `data object`), `LoadResult.Ready` and `LoadFailed` (implementing both `LoadResult<Nothing>` and `LookupResult<Nothing>`) per I1; `LoadFailure(dataset, category, array, entryIndex, field)` with its nested ten-value `Category` enum (DM6, AD3); `ReferenceDataset` with its four asset paths (DM7); and the `val`-only, default-free data classes `StaplesEntry` (with `basis: NutritionBasis` from `ie.pantry.data.db.entity`), `AliasEntry`, `SeasonalityEntry`, `SectionOrderEntry` and `SectionMapping` (DM1–DM5).  Every F2 test class runs under `@RunWith(RobolectricTestRunner::class)` (design Testing Strategy), because the store logs through `android.util.Log` and `org.json` is a stub on a plain JVM.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/entity/NutritionBasis.kt` — `PER_100G`, `PER_100ML`, no imports
  - Create: `app/src/main/java/ie/pantry/data/reference/Lookup.kt`
  - Create: `app/src/main/java/ie/pantry/data/reference/LoadFailure.kt`
  - Create: `app/src/main/java/ie/pantry/data/reference/ReferenceDataset.kt`
  - Create: `app/src/main/java/ie/pantry/data/reference/ReferenceEntries.kt`
  - Create: `app/src/test/java/ie/pantry/data/reference/ReferenceLookupTest.kt`
- **Dependencies:** None
- **Parallel:** Yes (with T1, T2) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `Lookup.Absent` and any `Lookup.Found` value, including `Found("")` and a found zero-valued entry
    WHEN they are compared
    THEN they are never equal, and `LoadFailed` is an instance of both `LoadResult` and `LookupResult` (AD1; R1 AC6, R2 AC5)
  - GIVEN `LoadFailure.Category` and `ReferenceDataset`
    WHEN their values are listed
    THEN they are exactly the ten AD3 categories and the four datasets with paths `reference/staples.json`, `reference/aliases.json`, `reference/seasonality.json` and `reference/section_order.json`
- **Tests:**
  - `` `absent is never equal to a found value of empty string or zero-valued entry`() ``
  - `` `load failed is both a load result and a lookup result`() ``
  - `` `load failure categories are exactly the ten design categories`() ``
  - `` `reference dataset asset paths are the four reference json paths`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/ReferenceLookupTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ReferenceLookupTest"`; `grep -lE '^import (android\.|org\.json)' app/src/main/java/ie/pantry/data/reference/{Lookup,LoadFailure,ReferenceDataset,ReferenceEntries}.kt` prints nothing.

### - [x] T4: Immutable tables with found/absent lookups

- **Requirement:** R2, R4, R7
- **Description:** Create `StaplesTable`, `AliasTable`, `SeasonalityTable` and `SectionOrderTable` in `ReferenceTables.kt` (pure) with `internal` constructors that copy their input, re-freeze nested `inSeasonMonths`/`substitutions` with `entry.copy(…)`, wrap every exposed collection as `Collections.unmodifiableList(ArrayList(src))`/`Collections.unmodifiableSet(LinkedHashSet(src))` (AD7), check invariants with constant-message `require(…)` (unique keys, months 1..12, unique non-negative `walkIndex`, every mapping names a listed section), build a private `HashMap` index, and answer `lookup`/`sectionFor` with `Lookup.Found` or `Lookup.Absent`, keeping the I2 names.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/reference/ReferenceEntries.kt` — entry types from T3
  - Create: `app/src/main/java/ie/pantry/data/reference/ReferenceTables.kt`
  - Modify: `app/src/test/java/ie/pantry/data/reference/ReferenceLookupTest.kt`
- **Dependencies:** T3
- **Parallel:** Yes (with T1, T2) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a staples table holding `water` with every nutrient `0.0`
    WHEN it is queried for `water` and then for a key it does not hold
    THEN the first is `Found` with the zero values, the second is `Absent`, the two are not equal, and an exhaustive `when` shows no nutrient is readable from `Absent` (R2 AC1)
  - GIVEN an alias table mapping one variant to a canonical key
    WHEN it is queried for that variant and for an unknown variant
    THEN the first is `Found(<canonical key>)` and the second is `Absent`, which is not equal to `Found("")` or to `Found(<queried variant>)` (R2 AC2)
  - GIVEN a seasonality table with one entry naming no substitutions, and a section-order table whose first section has the lowest `walkIndex`
    WHEN each is queried for its hit and for a miss
    THEN the hit is `Found` (empty substitution list; first section and its index) and the miss is `Absent`, distinct from those found values (R2 AC3, AC4)
  - GIVEN any table and any query string, including `""`
    WHEN `lookup` or `sectionFor` is called
    THEN it never throws and a miss is only `Absent` (R2 AC5)
  - GIVEN a list that violates a table invariant
    WHEN the table constructor is called with it
    THEN it throws `IllegalArgumentException` whose message contains none of the list's key text (backstop only; the parser rejects first, T11)
- **Tests:**
  - `` `staples miss is absent and distinct from zero-valued water`() ``
  - `` `no nutrient value is readable from an absent staples result`() ``
  - `` `alias hit returns the mapped canonical key and a miss is absent`() ``
  - `` `alias absent is distinct from an empty key and from the echoed variant`() ``
  - `` `seasonality entry with no substitutions is found and distinct from absent`() ``
  - `` `section lookup returns the first section with its index and a miss is absent`() ``
  - `` `section absent is distinct from the first section and its index`() ``
  - `` `lookup of an empty string is absent and does not throw`() ``
  - `` `staples table constructor rejects a repeated key with a constant message`() ``
  - `` `seasonality table constructor rejects a month outside 1 to 12`() ``
  - `` `section order table constructor rejects a repeated walk index`() ``
  - `` `section order table constructor rejects a mapping to an unlisted section`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/ReferenceLookupTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ReferenceLookupTest"`; `grep -cE '^import (android\.|org\.json)' app/src/main/java/ie/pantry/data/reference/ReferenceTables.kt` prints `0`.

### - [x] T5: `DatasetParser` core and staples entry-level matrix rows

- **Requirement:** R1
- **Description:** Create `internal object DatasetParser` with `parseStaples(bytes): LoadResult<StaplesTable>` and the private helpers `decodeUtf8Strict` (`CodingErrorAction.REPORT`), `readDocument` (`JSONTokener(text).nextValue()` then `nextClean() == '\u0000'`), `requireFields` (`MISSING_FIELD` then `UNEXPECTED_FIELD`), `string`, `number`, `wholeNumber` and `basis` (exact `NutritionBasis.entries` name match), plus the stackless `private class ParseAbort`, using only `has`/`get` followed by an explicit Kotlin `is` check and never an `opt*` accessor or `getString`/`getInt`/`getDouble`/`getJSONArray` (AD9), and add `DatasetLoaderTest` with the shared tuple-and-sentinel assertion helper and the staples entry-level rows below.  Every F2 test class runs under `@RunWith(RobolectricTestRunner::class)` (design Testing Strategy), because the store logs through `android.util.Log` and `org.json` is a stub on a plain JVM.
- **Files:**
  - Read: `specs/F2-reference-data-store-v1-dataset-curation/02_design.md` — AD9, AD10, Error Handling category table, Asset JSON layout
  - Read: `app/src/test/java/ie/pantry/testutil/Sentinels.kt` — `INGREDIENT`, `all`
  - Create: `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt`
  - Create: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
  - Create: `app/src/test/resources/reference/fixtures/staples_*.json` — the ten staples fixtures named in Tests (one-row data files, counted as one fixture group for sizing; see Q1)
- **Dependencies:** T4
- **Parallel:** Yes (with T1, T2, T21) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a staples fixture in which entry 1 omits `energyKcal`
    WHEN `parseStaples` parses it
    THEN the result is `LoadFailed(LoadFailure(STAPLES, MISSING_FIELD, "entries", 1, "energyKcal"))` and is not `Ready`, so no defaulted `0`/`0.0`/`""` entry exists (R1 AC4)
  - GIVEN a staples fixture whose entries 0 and 2 share a key
    WHEN it is parsed
    THEN the result is `DUPLICATE_KEY` at `("entries", 2, "key")`, the position of the second occurrence, and neither entry is kept (R1 AC5)
  - GIVEN staples fixtures with a numeric field holding a string, a string field holding a number, and a required field holding JSON `null`
    WHEN each is parsed
    THEN each is `WRONG_TYPE` at its exact entry and field, and nothing is coerced (R1 AC7)
  - GIVEN any staples failure fixture carrying `Sentinels.INGREDIENT`
    WHEN its `LoadFailure` is rendered with `toString()`
    THEN no sentinel appears (AD3)
- **Tests:** (fixture → expected (category, array, entryIndex, field), dataset STAPLES)
  - `` `staples valid fixture loads Ready with count equal to array length`() `` — staples_valid.json (three entries, one water all zeros) → Ready, 3 entries
  - `` `staples empty entries array loads Ready with zero entries`() `` — staples_empty.json → Ready, 0 entries (spec [SEAL-19])
  - `` `staples entry missing energyKcal fails MISSING_FIELD at entries 1 energyKcal`() `` — staples_missing_energy.json → (MISSING_FIELD, entries, 1, energyKcal)
  - `` `staples repeated key fails DUPLICATE_KEY at entries 2 key`() `` — staples_duplicate_key.json → (DUPLICATE_KEY, entries, 2, key)
  - `` `staples energyKcal as string fails WRONG_TYPE at entries 0 energyKcal`() `` — staples_energy_as_string.json ("52") → (WRONG_TYPE, entries, 0, energyKcal)
  - `` `staples key as number fails WRONG_TYPE at entries 0 key`() `` — staples_key_as_number.json → (WRONG_TYPE, entries, 0, key)
  - `` `staples proteinG null fails WRONG_TYPE at entries 1 proteinG`() `` — staples_protein_null.json → (WRONG_TYPE, entries, 1, proteinG)
  - `` `staples lowercase basis fails INVALID_ENUM_VALUE at entries 0 basis`() `` — staples_bad_basis.json ("per_100g") → (INVALID_ENUM_VALUE, entries, 0, basis)
  - `` `staples negative fatG fails INVALID_VALUE at entries 0 fatG`() `` — staples_negative_fat.json → (INVALID_VALUE, entries, 0, fatG)
  - `` `staples fibreG field fails UNEXPECTED_FIELD at entries 1 with no field name`() `` — staples_unexpected_field.json → (UNEXPECTED_FIELD, entries, 1, —)
  - File: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.DatasetLoaderTest"`; `grep -nE '\.opt[A-Z]|\.(getString|getInt|getDouble|getLong|getBoolean|getJSONArray|getJSONObject)\(' app/src/main/java/ie/pantry/data/reference/DatasetParser.kt` prints nothing.

### - [x] T6: Document-level and top-level structure matrix rows

- **Requirement:** R1
- **Description:** Add the document-level and top-level structure rows below through `parseStaples`, extending `DatasetParser` only where a row fails, so malformed UTF-8, tokener errors, trailing content, array holes, a non-object top level, a missing or unexpected top-level array, a non-array `entries` and a non-object entry each produce their exact tuple.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt` — only if a row is red
  - Modify: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
  - Create: `app/src/test/resources/reference/fixtures/` — `invalid_json.json`, `staples_trailing_content.json`, `staples_array_hole.json`, `top_level_null.json`, `top_level_scalar.json`, `top_level_array.json`, `staples_missing_entries.json`, `staples_unexpected_top_level.json`, `staples_entries_not_array.json`, `staples_entry_not_object.json`
- **Dependencies:** T5
- **Parallel:** Yes (with T1, T2, T21) — T7–T11 also modify `DatasetLoaderTest.kt` and follow it
- **Acceptance Criteria:**
  - GIVEN an asset that is not valid JSON (truncated, malformed UTF-8, zero bytes, whitespace only, or content after the closing brace)
    WHEN it is parsed
    THEN the result is `LoadFailed` with category `INVALID_JSON`, dataset `STAPLES` and no position, and the truncated fixture's sentinel is absent from `toString()` (R1 AC6, AC7; AD3)
  - GIVEN a structurally wrong document (top-level `null`, scalar or array; missing, extra or non-array top-level field; a non-object entry)
    WHEN it is parsed
    THEN it fails with the exact tuple listed in Tests and never yields `Ready`
- **Tests:** (dataset STAPLES)
  - `` `truncated staples document fails INVALID_JSON with no position and no sentinel`() `` — invalid_json.json → (INVALID_JSON, —, —, —)
  - `` `malformed UTF-8 bytes fail INVALID_JSON with no position`() `` — bytes generated in the test → (INVALID_JSON, —, —, —)
  - `` `zero-byte asset fails INVALID_JSON with no position`() `` — generated ByteArray(0) → (INVALID_JSON, —, —, —) *characterisation*
  - `` `whitespace-only asset fails INVALID_JSON with no position`() `` — generated → (INVALID_JSON, —, —, —) *characterisation*
  - `` `content after the closing brace fails INVALID_JSON with no position`() `` — staples_trailing_content.json ({…} {}) → (INVALID_JSON, —, —, —)
  - `` `trailing comma array hole fails INVALID_JSON at entries 1 with no field`() `` — staples_array_hole.json ([{…},]) → (INVALID_JSON, entries, 1, —) *characterisation*
  - `` `top-level null fails WRONG_TYPE with no position`() `` — top_level_null.json → (WRONG_TYPE, —, —, —) *characterisation*
  - `` `top-level scalar fails WRONG_TYPE with no position`() `` — top_level_scalar.json (42) → (WRONG_TYPE, —, —, —)
  - `` `top-level array fails WRONG_TYPE with no position`() `` — top_level_array.json → (WRONG_TYPE, —, —, —)
  - `` `missing entries array fails MISSING_FIELD with field entries and no entry position`() `` — staples_missing_entries.json ({}) → (MISSING_FIELD, —, —, entries)
  - `` `unexpected top-level field fails UNEXPECTED_FIELD with no position`() `` — staples_unexpected_top_level.json → (UNEXPECTED_FIELD, —, —, —)
  - `` `entries as an object fails WRONG_TYPE with field entries and no entry position`() `` — staples_entries_not_array.json → (WRONG_TYPE, —, —, entries)
  - `` `entry that is not an object fails WRONG_TYPE at entries 0 with no field`() `` — staples_entry_not_object.json ([42]) → (WRONG_TYPE, entries, 0, —)
  - File: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.DatasetLoaderTest"`

### - [x] T7: Boundary and `org.json` characterisation matrix rows

- **Requirement:** R1
- **Description:** Add the boundary rows (values accepted at load time) and one characterisation row per `org.json` leniency class AD9 relies on (comments, single quotes, unquoted names, octal `010`, NUL after the closing brace, `NaN`, `1e999`), retiring DR1 before any loader or store work.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt` — only if a non-characterisation row is red
  - Modify: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
  - Create: `app/src/test/resources/reference/fixtures/` — `staples_negative_zero_fat.json`, `staples_energy_1e308.json`, `staples_nan_energy.json`, `staples_energy_1e999.json`, `top_level_nan.json`, `lenient_comment.json`, `lenient_single_quotes.json`, `lenient_unquoted_names.json`, `lenient_octal.json`
- **Dependencies:** T6
- **Parallel:** Yes (with T1, T2, T21) — T8–T11 follow it on the shared test file
- **Acceptance Criteria:**
  - GIVEN a staples fixture holding `-0.0` for `fatG`, or `1e308` for `energyKcal`
    WHEN it is parsed
    THEN it loads `Ready` (the upper bound is test policy, T21, not a load rule; AD10)
  - GIVEN a staples fixture with `NaN` or `1e999` as an object value
    WHEN it is parsed
    THEN it fails `INVALID_JSON` with no position, because `JSONObject.put` rejects non-finite doubles (AD9, AD10)
  - GIVEN each leniency fixture
    WHEN it is parsed
    THEN the observed result is pinned by its row, so a platform change to `org.json` fails a named test (DR1, DR6)
- **Tests:** (dataset STAPLES)
  - `` `negative zero fatG loads Ready`() `` — staples_negative_zero_fat.json → Ready
  - `` `energyKcal 1e308 loads Ready at load time`() `` — staples_energy_1e308.json → Ready
  - `` `NaN nutrient in an object fails INVALID_JSON with no position`() `` — staples_nan_energy.json → (INVALID_JSON, —, —, —) *characterisation*
  - `` `energyKcal 1e999 fails INVALID_JSON with no position`() `` — staples_energy_1e999.json → (INVALID_JSON, —, —, —) *characterisation*
  - `` `top-level NaN fails WRONG_TYPE with no position`() `` — top_level_nan.json → (WRONG_TYPE, —, —, —) *characterisation* (design [SEAL-22])
  - `` `line comment inside a staples document is accepted`() `` — lenient_comment.json → Ready *characterisation*
  - `` `single-quoted strings are accepted`() `` — lenient_single_quotes.json → Ready *characterisation*
  - `` `unquoted field names are accepted`() `` — lenient_unquoted_names.json → Ready *characterisation*
  - `` `leading-zero octal 010 is accepted as 8`() `` — lenient_octal.json ("energyKcal": 010) → Ready, energyKcal == 8.0 *characterisation*
  - `` `NUL after the closing brace is accepted`() `` — valid bytes plus \u0000 generated in the test → Ready *characterisation*
  - File: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.DatasetLoaderTest"`; any characterisation row whose expectation was changed to match Robolectric has an Implementation Deviations row.

### - [x] T8: Alias parsing and its matrix rows

- **Requirement:** R1, R5
- **Description:** Add `DatasetParser.parseAliases` (fields `variant`, `canonicalKey`; duplicate `variant` is `DUPLICATE_KEY`) and the alias rows below, including one document-level row for the `ALIASES` dataset.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt`
  - Modify: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
  - Create: `app/src/test/resources/reference/fixtures/aliases_*.json` — the seven alias fixtures named in Tests
- **Dependencies:** T7
- **Parallel:** Yes (with T1, T2, T21) — T9–T11 follow it on the shared files
- **Acceptance Criteria:**
  - GIVEN each alias fixture below
    WHEN `parseAliases` parses it
    THEN the result is `Ready` with a count equal to the array length, or `LoadFailed` with dataset `ALIASES` and the exact tuple listed (R1 AC4, AC5, AC7)
- **Tests:** (dataset ALIASES)
  - `` `aliases valid fixture loads Ready with count equal to array length`() `` — aliases_valid.json → Ready, 2 entries
  - `` `aliases repeated variant fails DUPLICATE_KEY at entries 1 variant`() `` — aliases_duplicate_variant.json → (DUPLICATE_KEY, entries, 1, variant)
  - `` `aliases entry missing canonicalKey fails MISSING_FIELD at entries 0 canonicalKey`() `` — aliases_missing_canonical_key.json → (MISSING_FIELD, entries, 0, canonicalKey)
  - `` `aliases variant as number fails WRONG_TYPE at entries 0 variant`() `` — aliases_variant_as_number.json → (WRONG_TYPE, entries, 0, variant)
  - `` `aliases canonicalKey null fails WRONG_TYPE at entries 1 canonicalKey`() `` — aliases_canonical_key_null.json → (WRONG_TYPE, entries, 1, canonicalKey)
  - `` `aliases extra field fails UNEXPECTED_FIELD at entries 0 with no field name`() `` — aliases_unexpected_field.json → (UNEXPECTED_FIELD, entries, 0, —)
  - `` `aliases trailing content fails INVALID_JSON with no position`() `` — aliases_trailing_content.json → (INVALID_JSON, —, —, —)
  - File: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.DatasetLoaderTest"`

### - [x] T9: Seasonality parsing and its matrix rows

- **Requirement:** R1, R5
- **Description:** Add `DatasetParser.parseSeasonality` (`key`; `inSeasonMonths` as a non-empty array of distinct `Int` 1..12, `Int` only; `substitutions` as a possibly empty array of strings) and the seasonality rows below, including the R5 AC5 one-extra-entry pair, a non-Int month element, a `NaN`-in-array element and a document-level row for `SEASONALITY`.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt`
  - Modify: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
  - Create: `app/src/test/resources/reference/fixtures/seasonality_*.json` — the seventeen seasonality fixtures named in Tests
- **Dependencies:** T8
- **Parallel:** Yes (with T1, T2, T21) — T10 and T11 follow it on the shared files
- **Acceptance Criteria:**
  - GIVEN a whole-number month holding `3.0`, `"6"`, a `Long`-range value or `NaN`
    WHEN the fixture is parsed
    THEN each is `WRONG_TYPE` at `("entries", 0, "inSeasonMonths")` and none is coerced (R1 AC7)
  - GIVEN `seasonality_one.json` and `seasonality_one_plus_extra.json`, which differ only by one entry added in the JSON
    WHEN both are parsed by the same parser and the extra key is looked up
    THEN it is `Absent` in the first table and `Found` in the second, with no Kotlin source or Room schema change between them (R5 AC5)
  - GIVEN each other seasonality fixture below
    WHEN it is parsed
    THEN the result is `Ready` or `LoadFailed` with dataset `SEASONALITY` and the exact tuple listed
- **Tests:** (dataset SEASONALITY)
  - `` `seasonality valid fixture loads Ready with count equal to array length`() `` — seasonality_valid.json (three entries, one with [] substitutions) → Ready, 3 entries
  - `` `seasonality months 1 and 12 load Ready`() `` — seasonality_months_1_and_12.json → Ready
  - `` `seasonality month three point zero fails WRONG_TYPE at entries 0 inSeasonMonths`() `` — seasonality_fractional_month.json ([3.0]) → (WRONG_TYPE, entries, 0, inSeasonMonths)
  - `` `seasonality month as string fails WRONG_TYPE at entries 0 inSeasonMonths`() `` — seasonality_month_as_string.json (["6"]) → (WRONG_TYPE, entries, 0, inSeasonMonths)
  - `` `seasonality Long-range month fails WRONG_TYPE at entries 0 inSeasonMonths`() `` — seasonality_long_month.json ([4294967296]) → (WRONG_TYPE, entries, 0, inSeasonMonths)
  - `` `seasonality NaN month element fails WRONG_TYPE at entries 0 inSeasonMonths`() `` — seasonality_nan_month.json ([NaN]) → (WRONG_TYPE, entries, 0, inSeasonMonths) *characterisation* (design [SEAL-22])
  - `` `seasonality month 0 fails INVALID_VALUE at entries 0 inSeasonMonths`() `` — seasonality_month_0.json → (INVALID_VALUE, entries, 0, inSeasonMonths)
  - `` `seasonality month 13 fails INVALID_VALUE at entries 1 inSeasonMonths`() `` — seasonality_month_13.json → (INVALID_VALUE, entries, 1, inSeasonMonths)
  - `` `seasonality repeated month fails INVALID_VALUE at entries 0 inSeasonMonths`() `` — seasonality_repeated_month.json ([6, 6]) → (INVALID_VALUE, entries, 0, inSeasonMonths)
  - `` `seasonality empty months fails INVALID_VALUE at entries 0 inSeasonMonths`() `` — seasonality_empty_months.json → (INVALID_VALUE, entries, 0, inSeasonMonths)
  - `` `seasonality non-string substitution fails WRONG_TYPE at entries 0 substitutions`() `` — seasonality_substitution_not_string.json ([7]) → (WRONG_TYPE, entries, 0, substitutions)
  - `` `seasonality entry missing substitutions fails MISSING_FIELD at entries 0 substitutions`() `` — seasonality_missing_substitutions.json → (MISSING_FIELD, entries, 0, substitutions)
  - `` `seasonality repeated key fails DUPLICATE_KEY at entries 1 key`() `` — seasonality_duplicate_key.json → (DUPLICATE_KEY, entries, 1, key)
  - `` `seasonality extra field fails UNEXPECTED_FIELD at entries 0 with no field name`() `` — seasonality_unexpected_field.json → (UNEXPECTED_FIELD, entries, 0, —)
  - `` `seasonality truncated document fails INVALID_JSON with no position`() `` — seasonality_truncated.json → (INVALID_JSON, —, —, —)
  - `` `extra seasonality entry added by JSON edit alone is absent before and found after`() `` — seasonality_one.json / seasonality_one_plus_extra.json (R5 AC5)
  - File: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.DatasetLoaderTest"`

### - [x] T10: Section-order parsing and its matrix rows

- **Requirement:** R1, R4
- **Description:** Add `DatasetParser.parseSectionOrder`, which parses the `sections` array (`name`, `walkIndex` as `Int` only, ≥ 0, unique, not necessarily contiguous) before the `mappings` array (`key`, `section` naming a listed section) whatever their order in the file (AD8, AD10, design `[SEAL-19]`), and the section-order rows below.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt`
  - Modify: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
  - Create: `app/src/test/resources/reference/fixtures/section_order_*.json` — the eleven section-order fixtures named in Tests
- **Dependencies:** T9
- **Parallel:** Yes (with T1, T2, T21) — T11 follows it on the shared files
- **Acceptance Criteria:**
  - GIVEN a section-order fixture with sections at `walkIndex` 0, 10 and 20 and two mappings
    WHEN it is parsed
    THEN it is `Ready`, every section has exactly one index, the section and mapping counts equal their array lengths, and `walkIndex` 0 and a non-contiguous sequence are accepted (R4 AC1)
  - GIVEN a repeated `walkIndex`, a mapping to an unlisted section, or a whole-number `walkIndex` written `2.5` or `3.0`
    WHEN the fixture is parsed
    THEN it fails `INVALID_VALUE` or `WRONG_TYPE` at the exact tuple listed, so no two sections share an index and no key maps to an unlisted section (R4 AC2, AC3; R1 AC7)
- **Tests:** (dataset SECTION_ORDER)
  - `` `section order valid fixture loads Ready with counts equal to both array lengths`() `` — section_order_valid.json → Ready, 3 sections, 2 mappings
  - `` `section order fractional walkIndex fails WRONG_TYPE at sections 1 walkIndex`() `` — section_order_fractional_index.json (2.5) → (WRONG_TYPE, sections, 1, walkIndex)
  - `` `section order walkIndex three point zero fails WRONG_TYPE at sections 0 walkIndex`() `` — section_order_whole_double_index.json (3.0) → (WRONG_TYPE, sections, 0, walkIndex)
  - `` `section order negative walkIndex fails INVALID_VALUE at sections 0 walkIndex`() `` — section_order_negative_index.json → (INVALID_VALUE, sections, 0, walkIndex)
  - `` `section order repeated walkIndex fails INVALID_VALUE at sections 2 walkIndex`() `` — section_order_index_collision.json → (INVALID_VALUE, sections, 2, walkIndex)
  - `` `section order mapping to unlisted section fails INVALID_VALUE at mappings 1 section`() `` — section_order_unlisted_section.json → (INVALID_VALUE, mappings, 1, section)
  - `` `section order repeated name fails DUPLICATE_KEY at sections 1 name`() `` — section_order_duplicate_name.json → (DUPLICATE_KEY, sections, 1, name)
  - `` `section order repeated mapping key fails DUPLICATE_KEY at mappings 1 key`() `` — section_order_duplicate_mapping.json → (DUPLICATE_KEY, mappings, 1, key)
  - `` `section order missing walkIndex fails MISSING_FIELD at sections 0 walkIndex`() `` — section_order_missing_walk_index.json → (MISSING_FIELD, sections, 0, walkIndex)
  - `` `section order missing mappings array fails MISSING_FIELD with field mappings and no entry position`() `` — section_order_missing_mappings.json → (MISSING_FIELD, —, —, mappings)
  - `` `section order mapping extra field fails UNEXPECTED_FIELD at mappings 0 with no field name`() `` — section_order_mapping_unexpected_field.json → (UNEXPECTED_FIELD, mappings, 0, —)
  - File: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.DatasetLoaderTest"`

### - [x] T11: Multi-fault precedence rows and table-invariant parity

- **Requirement:** R1
- **Description:** Add rows that pin AD10's deterministic precedence (array order; then, within the first failing entry, missing before unexpected before type before value, each in schema field order; cross-entry checks after the entry's own field checks; `sections` before `mappings`) and one parity test asserting that every table-constructor invariant from T4 is rejected first by the parser with its own category, never `INTERNAL_ERROR`.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt` — only if a precedence row is red
  - Modify: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
  - Create: `app/src/test/resources/reference/fixtures/` — `staples_multi_fault_entries.json`, `staples_multi_fault_one_entry.json`, `staples_type_before_value.json`, `staples_field_fault_before_duplicate.json`, `section_order_sections_before_mappings.json`
- **Dependencies:** T10
- **Parallel:** Yes (with T1, T2, T21) — T12 follows it on `DatasetLoaderTest.kt`
- **Acceptance Criteria:**
  - GIVEN a fixture with more than one fault
    WHEN it is parsed
    THEN exactly one `LoadFailure` is returned, carrying the tuple AD10's order selects, as listed in Tests (design `[SEAL-16]`)
  - GIVEN each T4 table invariant (repeated key, month outside 1..12, repeated `walkIndex`, unlisted mapping section)
    WHEN its equivalent fixture from T5, T9 or T10 is parsed
    THEN the parser returns that row's own category (`DUPLICATE_KEY` or `INVALID_VALUE`), never `INTERNAL_ERROR` (AD10)
- **Tests:**
  - `` `earlier failing entry wins over a later one`() `` — staples_multi_fault_entries.json (entry 1 energyKcal as string, entry 2 missing key) → (WRONG_TYPE, entries, 1, energyKcal)
  - `` `missing field wins over unexpected type and value faults in one entry`() `` — staples_multi_fault_one_entry.json (entry 0: negative energyKcal, string proteinG, extra fibreG, no carbohydrateG) → (MISSING_FIELD, entries, 0, carbohydrateG)
  - `` `type fault on a later field wins over a value fault on an earlier field`() `` — staples_type_before_value.json (entry 0: energyKcal −1, fatG "x") → (WRONG_TYPE, entries, 0, fatG)
  - `` `entry field fault wins over its duplicate key`() `` — staples_field_fault_before_duplicate.json (entry 1 repeats entry 0's key and has negative fatG) → (INVALID_VALUE, entries, 1, fatG)
  - `` `sections are checked before mappings whatever their file order`() `` — section_order_sections_before_mappings.json (mappings first in the text with an unlisted section at index 0; sections index 1 repeats a name) → (DUPLICATE_KEY, sections, 1, name)
  - `` `parser rejects every table invariant with its own category before the table constructor`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.DatasetLoaderTest"`

## Phase 3: Loader and Store (FC3, FC4)

### - [x] T12: `AssetSource` seam, `loadDataset` and loader rows

- **Requirement:** R1
- **Description:** Create the pure `fun interface AssetSource` with `AssetSource.UNAVAILABLE` (every `open` throws `FileNotFoundException` with a constant message), `AndroidAssetSource(assets: AssetManager)` (holds the manager, opens nothing at construction), and `internal fun <T : Any> loadDataset(source, dataset, parse): LoadResult<T>` in `DatasetLoader.kt` as the single catch site (`FileNotFoundException` → `ASSET_ABSENT`, other `IOException` → `ASSET_UNREADABLE`, other `Exception` → `INTERNAL_ERROR`, `CancellationException` rethrown, stream always closed, no message or cause kept), plus `FixtureAssetSource(map)` and `RecordingAssetSource(delegate)` (thread-safe `openedPaths`, `openThreads`, `openCount`) in `testutil/AssetSources.kt`, and the loader rows below.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/reference/DatasetParser.kt` — the four `parseX` functions passed as `parse`
  - Create: `app/src/main/java/ie/pantry/data/reference/AssetSource.kt`
  - Create: `app/src/main/java/ie/pantry/data/reference/AndroidAssetSource.kt`
  - Create: `app/src/main/java/ie/pantry/data/reference/DatasetLoader.kt`
  - Create: `app/src/test/java/ie/pantry/testutil/AssetSources.kt`
  - Modify: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Dependencies:** T11
- **Parallel:** Yes (with T18, T19, T20, T21) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a `FixtureAssetSource` with no mapping for the requested path, or `AssetSource.UNAVAILABLE`
    WHEN `loadDataset` is called
    THEN it returns `LoadFailed` with category `ASSET_ABSENT` and no position, which is distinct from `Lookup.Absent`, and nothing is thrown (R1 AC6)
  - GIVEN a source whose stream throws `IOException` mid-read, or a `parse` lambda that throws `IllegalStateException` carrying `Sentinels.INGREDIENT`
    WHEN `loadDataset` is called
    THEN it returns `ASSET_UNREADABLE` or `INTERNAL_ERROR` respectively, and neither `LoadFailure.toString()` contains a sentinel (AD3)
  - GIVEN the valid staples fixture mapped through `FixtureAssetSource` and wrapped in `RecordingAssetSource`
    WHEN `loadDataset` is called
    THEN it returns `Ready` with the fixture's count, records exactly one open, and closes the stream (observed through a test-local tracking `InputStream` wrapper, returned by a tracking `AssetSource`, that records `close()`)
- **Tests:**
  - `` `unmapped fixture path loads as ASSET_ABSENT with no position`() ``
  - `` `unavailable source loads as ASSET_ABSENT and does not throw`() ``
  - `` `stream failing mid-read loads as ASSET_UNREADABLE`() ``
  - `` `parse lambda throwing loads as INTERNAL_ERROR with no sentinel`() ``
  - `` `valid fixture through the loader is Ready with one open and a closed stream`() ``
  - `` `truncated fixture through the loader is INVALID_JSON with no sentinel`() ``
  - `` `android asset source throws FileNotFoundException for a missing asset`() `` — Robolectric context.assets, `AndroidAssetSource.open()` called directly against a path that is never one of the four shipped datasets' asset paths (stays correct once every dataset ships)
  - File: `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.DatasetLoaderTest"`; `grep -cE '^import android\.' app/src/main/java/ie/pantry/data/reference/AssetSource.kt` prints `0`; `grep -nE '\.(message|cause|localizedMessage)\b' app/src/main/java/ie/pantry/data/reference/DatasetLoader.kt` prints nothing.

### - [x] T13: `ReferenceDataStore`, failure caching and store-level lookups

- **Requirement:** R1, R2, R6
- **Description:** Create `ReferenceDataStore(source: AssetSource, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)` (I6) owning `CoroutineScope(SupervisorJob() + ioDispatcher)` with one `scope.async(start = CoroutineStart.LAZY) { loadAndLog(dataset, parser) }` per dataset, `suspend` accessors `staples()`/`aliases()`/`seasonality()`/`sectionOrder()` returning `LoadResult<XTable>` (the caller unwraps `Ready`, design `[SEAL-18]`), and the shortcuts `stapleFor`/`canonicalKeyForVariant`/`seasonalityFor`/`sectionFor` returning `LookupResult<T>`; the final names are fixed here as `loadDataset` (the top-level load body in `DatasetLoader.kt`, T12) and `loadAndLog` (the private store wrapper that calls it and writes exactly one `Log.w("PantryRef", "dataset=<DATASET> category=<CATEGORY>")` on `LoadFailed`), closing design `[SEAL-05]`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/reference/DatasetLoader.kt` — `loadDataset`
  - Create: `app/src/main/java/ie/pantry/data/reference/ReferenceDataStore.kt`
  - Create: `app/src/test/java/ie/pantry/data/reference/LoadFailureCachingTest.kt`
  - Modify: `app/src/test/java/ie/pantry/data/reference/ReferenceLookupTest.kt`
- **Dependencies:** T12
- **Parallel:** Yes (with T18–T21) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a store over an absent asset, and a store over an invalid asset carrying `Sentinels.INGREDIENT`
    WHEN a lookup is made and then made again
    THEN both return an equal `LoadFailed`, no exception escapes, `RecordingAssetSource` shows one open (not retried), and `ShadowLog.getLogsForTag("PantryRef")` holds exactly one entry equal to `dataset=STAPLES category=<CATEGORY>` with no sentinel (R1 AC8, Q7)
  - GIVEN a store over a broken asset
    WHEN `stapleFor` is called
    THEN the result is `LoadFailed`, never `Lookup.Absent` (R1 AC6; R2 intro)
  - GIVEN a store over the four valid fixtures
    WHEN each shortcut is called for a hit and a miss, and each accessor is called twice
    THEN the shortcuts return `Found` and `Absent`, the accessors return the same `Ready` table instance, and a successful load writes no log line (R2 AC1–AC5; R6 AC4)
- **Tests:**
  - `` `absent asset returns an equal LoadFailed twice with one open`() ``
  - `` `invalid asset returns an equal LoadFailed twice with one open`() ``
  - `` `failed load writes exactly one content-free log line`() ``
  - `` `failed load log and failure carry no sentinel`() ``
  - `` `successful load writes no log line`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/LoadFailureCachingTest.kt`
  - `` `store lookup on a broken asset is LoadFailed never Absent`() ``
  - `` `store shortcuts return found and absent for each dataset`() ``
  - `` `store accessor returns the same Ready table instance on every call`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/ReferenceLookupTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.LoadFailureCachingTest" --tests "*.ReferenceLookupTest"`; `grep -n 'CoroutineStart.LAZY' app/src/main/java/ie/pantry/data/reference/ReferenceDataStore.kt` prints at least one line.

### - [x] T14: Blocking and gated sources and `ReferenceLoadThreadingTest`

- **Requirement:** R6
- **Description:** Add `BlockingAssetSource` and `GatedAssetSource` to `testutil/AssetSources.kt` with the surfaces below, and create `ReferenceLoadThreadingTest` (real dispatchers under `runBlocking` + `withTimeout`, not `runTest`) covering off-main loading, the caller regaining control before the read completes, exactly-once loading under 32 concurrent callers with a caller-started count and a negative control, no reopen after load, and a cancelled first caller not cancelling the shared load (AD6).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/reference/ReferenceDataStore.kt` — accessors and shortcuts
  - Modify: `app/src/test/java/ie/pantry/testutil/AssetSources.kt`
  - Create: `app/src/test/java/ie/pantry/data/reference/ReferenceLoadThreadingTest.kt`
- **Dependencies:** T13
- **Parallel:** Yes (with T15, T17–T23) — disjoint files; T15 reads only types created by T12 while T14 modifies `AssetSources.kt`, and T16 depends on T14 (it reads `BlockingAssetSource`), so it is not marked parallel with it.
- **Acceptance Criteria:**
  - GIVEN `BlockingAssetSource` with surface `openCount: Int`, `openThreads: List<Thread>`, `awaitStarted(timeout: Long, unit: TimeUnit): Boolean`, `release()`, `completed: Boolean` (true only when an open finished after `release()`) and `timedOut: Boolean` (true only when its 30 s wait expired), whose `open` records the thread, counts down `started`, waits on the release latch for at most 30 s and then throws `FileNotFoundException`
    WHEN `CoroutineScope(Dispatchers.Unconfined).async { store.stapleFor("water") }` is started on the test's main thread
    THEN the returned `Deferred` is not completed while `awaitStarted(5, SECONDS)` is true, and the recorded open thread is not `Looper.getMainLooper().thread` (R6 AC2)
  - GIVEN `GatedAssetSource(delegate)` with surface `openCount: Int`, `openThreads: List<Thread>`, `blockedAtGate: Int` (opens currently waiting), `awaitBlocked(atLeast: Int, timeout: Long, unit: TimeUnit): Boolean` (bounded poll), `release()` (the only path that lets an open reach the delegate), `completed: Boolean` (set only on the release path) and `timedOut: Boolean` (30 s cap, then throws), and a body parameterised over a lookup lambda that increments a caller-started counter inside the lookup lambda immediately before the store call, in each of 32 coroutines launched on the fixed 32-thread dispatcher that is a parameter of the body
    WHEN the body waits (bounded) until the caller-started count is 32 and one open is blocked at the gate, then releases the gate and awaits every caller under `withTimeout`; the started counter is incremented inside the lookup lambda immediately before the store call, not at coroutine start, and the dispatcher (a fixed 32-thread pool, closed in a `finally`) is a parameter of the test body shared by the positive run and the control
    THEN `openCount` is 1 (assertion message names "open count") and all 32 `Ready.table` values are the same instance (`assertSame`) (R6 AC3, design `[SEAL-13]`)
  - GIVEN the same body run with a negative-control lambda that calls `loadDataset` once per call
    WHEN it runs inside `assertFailsWith<AssertionError>` on a dedicated fixed 32-thread dispatcher (`Executors.newFixedThreadPool(32).asCoroutineDispatcher()`, closed in a `finally`), because each control call blocks a thread inside `loadDataset` and `Dispatchers.Default` has only as many threads as CPU cores, so 32 blocked callers would starve it and the started count could never reach 32
    THEN it fails, and the caught error's message names the open-count assertion (open count above one), so the concurrency test is shown able to fail (design `[SEAL-14]`)
  - GIVEN a dataset that has already loaded
    WHEN further lookups are made, including after a first caller was cancelled mid-read
    THEN `openCount` stays 1 and the load still completes and is cached (R6 AC4; AD6)
- **Tests:**
  - `` `first lookup from the main thread opens the asset off the main thread`() ``
  - `` `main-thread caller regains control before the blocked read completes`() ``
  - `` `thirty-two concurrent first lookups open the asset once and share one table`() ``
  - `` `concurrency check fails on the open count when every caller loads separately`() ``
  - `` `lookups after load never reopen the asset`() ``
  - `` `cancelled first caller does not cancel the shared load`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/ReferenceLoadThreadingTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ReferenceLoadThreadingTest"`; `grep -c 'runTest' app/src/test/java/ie/pantry/data/reference/ReferenceLoadThreadingTest.kt` prints `0`.

## Phase 4: Container Wiring and Seam Tests (FC5, FC7)

### - [x] T15: `AppContainer` wiring and `AppContainerTest` additions

- **Requirement:** R6
- **Description:** Add the fourth `AppContainer` constructor parameter `referenceAssets: AssetSource = AssetSource.UNAVAILABLE` and `val referenceData: ReferenceDataStore = ReferenceDataStore(referenceAssets)`, have only `production(app, clock)` pass `AndroidAssetSource(app.assets)` (I7, AD4), and add the design's `AppContainerTest` cases while keeping F1's three tests unchanged.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/AssetSources.kt` — `RecordingAssetSource`, `FixtureAssetSource`
  - Modify: `app/src/main/java/ie/pantry/di/AppContainer.kt`
  - Modify: `app/src/test/java/ie/pantry/di/AppContainerTest.kt`
- **Dependencies:** T13
- **Parallel:** Yes (with T14, T17–T23) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `AppContainer(db, store, processor, recording)` with a `RecordingAssetSource`
    WHEN construction completes
    THEN zero opens are recorded (R6 AC1), and one `referenceData.stapleFor(…)` on the same kind of container records exactly one open (positive control, AD4)
  - GIVEN a container built with F1's three-argument call
    WHEN `referenceData.stapleFor("water")` is called
    THEN it returns `LoadFailed` with category `ASSET_ABSENT` and does not throw (spec Modified Files)
  - GIVEN the running `PantryApplication` under Robolectric
    WHEN `container.referenceData` is read twice
    THEN the same `ReferenceDataStore` instance is returned and a container built over a recording test source records zero opens after construction (AD4)
- **Tests:**
  - `` `container construction opens no dataset asset`() ``
  - `` `one lookup through the container records exactly one open`() ``
  - `` `three-argument container returns ASSET_ABSENT from stapleFor without throwing`() ``
  - `` `container reference data store is the same instance across reads`() ``
  - File: `app/src/test/java/ie/pantry/di/AppContainerTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.AppContainerTest"`; `grep -c 'AndroidAssetSource(app.assets)' app/src/main/java/ie/pantry/di/AppContainer.kt` prints `1`.

### - [x] T16: Full blocked-read first-paint test and companion zero-opens test

- **Requirement:** R6
- **Description:** Extend `BlockingReadPantryApplication` to build a `BlockingAssetSource`, expose it as `blockingSource` and pass it as the container's fourth argument; add a `RecordingPantryApplication` (the recording-source test Application, whose container wraps a `FixtureAssetSource` holding the valid staples fixture in a `RecordingAssetSource` exposed as `recordingSource`); and complete `FirstPaintNotBlockedTest` with AD15's hardened blocked-read test, the companion `launching MainActivity opens no dataset asset` test with a bounded drain step after Compose idle, and its positive control, each class selected by method-level `@Config(application = …)`.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/AssetSources.kt` — `BlockingAssetSource`, `RecordingAssetSource`, `FixtureAssetSource`
  - Modify: `app/src/test/java/ie/pantry/ui/BlockingReadPantryApplication.kt`
  - Create: `app/src/test/java/ie/pantry/ui/RecordingPantryApplication.kt` — **[ASSUMPTION]** a second test Application, not in the design's File Structure (see Q2)
  - Modify: `app/src/test/java/ie/pantry/ui/FirstPaintNotBlockedTest.kt`
- **Dependencies:** T2, T14, T15
- **Parallel:** Yes (with T17–T23) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `BlockingReadPantryApplication` and zero opens recorded
    WHEN the test (1) starts `container.referenceData.stapleFor("water")` in `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, (2) asserts `blockingSource.awaitStarted(5, SECONDS)` and exactly one open on a non-main thread, (3) launches `MainActivity` within a 10 s wall clock, and (4) asserts `onNodeWithText("Pantry").assertIsDisplayed()`
    THEN the placeholder is displayed while the read is still blocked, and at the end `!completed`, `!timedOut` and no open on the main thread hold; `@After` releases the source, closes the scenario, cancels the scope and closes the in-memory database (R6 AC5; AD15)
  - GIVEN `RecordingPantryApplication` and no lookup issued
    WHEN `MainActivity` is launched, Compose goes idle, and then a bounded drain step runs (idle the main looper, run an empty `withContext(Dispatchers.IO)` and `withContext(Dispatchers.Default)` block under a 2 s `withTimeout`, then poll the open count every 50 ms for 1 s), all within a 10 s wall clock
    THEN the recording source shows zero opens, so a launch-time pre-warm or startup read on any thread fails the test (R6 AC1; Q8; design `[SEAL-10]`, `[SEAL-11]`)
  - GIVEN the same `RecordingPantryApplication`
    WHEN `MainActivity` is launched and one `stapleFor` lookup is awaited (bounded 5 s)
    THEN exactly one open is recorded, showing the recording source is the one the container uses (positive control, design `[SEAL-10]`)
- **Tests:**
  - `` `placeholder is displayed while a dataset read is blocked`() ``
  - `` `launching MainActivity opens no dataset asset`() ``
  - `` `recording application records exactly one open after one lookup`() ``
  - File: `app/src/test/java/ie/pantry/ui/FirstPaintNotBlockedTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.FirstPaintNotBlockedTest"`; red-first check (a local probe that is reverted and never committed or shipped, so it does not breach the spec's Never Do or Ask First on startup reads, which govern the shipped app): temporarily add an `onCreate` override to `PantryApplication` that calls `CoroutineScope(Dispatchers.IO).launch { container.referenceData.staples() }`, confirm `launching MainActivity opens no dataset asset` fails, remove it, and confirm the class is green again; if the blocked-read test hangs or cannot find the Compose hierarchy, apply DR2's fallback (start the blocked read on a plain `Thread`) and log an Implementation Deviation.

### - [x] T17: `ReadOnlySurfaceTest` and repo-root helper

- **Requirement:** R7
- **Description:** Create `testutil/RepoPaths.kt` (walks up from `File("").absoluteFile` to the first directory holding `settings.gradle.kts`, AD13) and `ReadOnlySurfaceTest`, which checks by Java reflection, downcast writes and source inspection that `ie.pantry.data.reference` exposes no way to change loaded data and that its six pure files stay free of `android.*`, `org.json` and any `ie.pantry.data.db` import other than `NutritionBasis` (AD2, AD4, AD7).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/reference/` — every main file, for the reflection target list and the source denylist
  - Create: `app/src/test/java/ie/pantry/testutil/RepoPaths.kt`
  - Create: `app/src/test/java/ie/pantry/data/reference/ReadOnlySurfaceTest.kt`
- **Dependencies:** T13
- **Parallel:** Yes (with T14, T15, T16, T18–T21) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the entry types, tables, result types, `LoadFailure` and `ReferenceDataStore`
    WHEN their public and non-mangled declared members are reflected
    THEN every declared field is `final` and no method is named `set*`, `add*`, `remove*`, `put*`, `clear*` or `replace*` (R7 AC1, AC2)
  - GIVEN each `entries`, `sections`, `mappings`, `inSeasonMonths` and `substitutions` collection returned by a lookup or table
    WHEN it is cast to `MutableList`/`MutableSet` and hit with `add`, `remove` and `clear`
    THEN each attempt throws `UnsupportedOperationException` and a later lookup is unchanged; and a table built from caller-owned mutable lists is unaffected when those lists are mutated afterwards (R7 AC4)
  - GIVEN the files under `app/src/main/java/ie/pantry/data/reference/`, found through `RepoPaths`
    WHEN their text is inspected
    THEN none contains `FileOutputStream`, `openFileOutput`, `SharedPreferences`, `.edit(`, `writeText`, `writeBytes`, `@Insert`, `@Update` or `Dao`, and `Lookup.kt`, `LoadFailure.kt`, `ReferenceDataset.kt`, `ReferenceEntries.kt`, `ReferenceTables.kt` and `AssetSource.kt` contain no `import android.`, no `import org.json` and no `import ie.pantry.data.db` other than `ie.pantry.data.db.entity.NutritionBasis` (R7 AC3; AD2)
- **Tests:**
  - `` `repo root is the nearest directory holding settings gradle kts`() ``
  - `` `every declared field of reference types is final`() ``
  - `` `no reference type declares a mutator method`() ``
  - `` `downcast writes to every returned collection throw and leave lookups unchanged`() ``
  - `` `table built from caller-owned mutable lists is unaffected by later mutation`() ``
  - `` `reference package source writes nothing`() ``
  - `` `pure reference files import neither android nor org json nor other db types`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/ReadOnlySurfaceTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ReadOnlySurfaceTest"`; review each hit of `grep -rnE 'Mutable|\bvar\b' app/src/main/java/ie/pantry/data/reference` and confirm none is the type of a public or internal property or constructor parameter (the "never a `Mutable*` type" clause of R7 AC2 that reflection cannot see, AD7).

## Phase 5: Small Real Seed (FC6, FC7)

Shipped-asset format for every dataset task below **[ASSUMPTION — a diffability and grep convention, not a design rule]**: UTF-8 with no BOM, two-space indent, one entry object per line, `"field": value` with one space after the colon, and a trailing newline. No figure, month, section name or alias is invented by the implementer: every value comes from the named source, and anything the source does not give is left out and listed for the developer. Seasonality substitutions are developer-sourced: either a list the developer supplies or an implementer draft the developer approves item by item, recorded as developer-approved in the provenance record; a substitute key outside the seeded and staples keys is a stop-and-ask, never an invention. **Field-order check (used by T18 and T23):** a throwaway script kept out of the repo regenerates each key's tuple from `git show e037454^:app/src/main/assets/staples.csv` (CSV column order kcal, protein, carbohydrate, fat maps to JSON `energyKcal`, `proteinG`, `carbohydrateG`, `fatG`) and diffs it against the values parsed from `staples.json`, printing nothing on a match, so a carbohydrate/fat swap cannot pass. Claude runs no mutating git; every file these tasks create or change is left in the working tree for the developer to commit.

### - [x] T18: Staples and alias seed assets and provenance skeleton

- **Requirement:** R3, R5
- **Description:** Create `staples.json` with the first 10 data rows of the prior build's CSV (`git show e037454^:app/src/main/assets/staples.csv`, read-only) in file order, each key converted to the canonical surface form (lowercase, trimmed, single-spaced) and its kcal, protein, fat and carbohydrate copied verbatim with `"basis": "PER_100G"` and fibre, sugar and salt dropped; `aliases.json` with the alias entries the developer names (Q10), whose targets are keys in this staples seed; and `docs/reference-data-provenance.md` with the four exact headings `## Staples`, `## Aliases`, `## Seasonality` and `## Section order`, each holding one `- **Source:** …` line and one `- **Retrieved:** YYYY-MM-DD` line, filled for Staples and Aliases and holding the literal placeholder `PENDING-SOURCE` for the two datasets whose sources arrive in T19 and T20. The `Retrieved` date for the Staples and Aliases provenance sections is a developer input: the implementer stops and asks for it (an ISO `YYYY-MM-DD` date) and never chooses one. Each provenance section in the skeleton carries a `- **Licence:**` line: for Staples and Aliases the developer supplies the licence statement at this task (the implementer stops and asks, and writes `Licence: not stated by source` only once the developer confirms the source gives none); for the two sections whose sources arrive in T19 and T20 it holds `PENDING-SOURCE` until then, so the implementer never asserts a licence fact for a source it has not seen.
- **Files:**
  - Read: `git show e037454^:app/src/main/assets/staples.csv` — header and first 10 data rows **[ASSUMPTION: columns are name, kcal, protein, carbohydrate, fat, fibre, sugar, salt per 100 g, per spec Q4/Q5; confirm from the header before copying]**
  - Create: `app/src/main/assets/reference/staples.json`
  - Create: `app/src/main/assets/reference/aliases.json`
  - Create: `docs/reference-data-provenance.md`
- **Dependencies:** T11
- **Parallel:** Yes (with T12–T17, T21) — data and documentation files only
- **Acceptance Criteria:**
  - GIVEN the first 10 data rows of the prior CSV
    WHEN `staples.json` is compared with them row by row
    THEN each entry's four nutrient figures equal the CSV's figures exactly (no rounding, estimation or filled gap), every entry is `PER_100G`, and a CSV row with a missing or non-numeric figure is left out and listed for the developer rather than filled (R3 AC2; spec Never Do)
  - GIVEN the alias entries the developer names
    WHEN `aliases.json` is written
    THEN it holds only those entries, at least one, each `canonicalKey` is a key in `staples.json`, and the developer confirms the list (R5 AC4; Q10)
  - GIVEN `docs/reference-data-provenance.md`
    WHEN it is read
    THEN the Staples section names the prior-build CSV and commit as the seed source and states that no Open Food Facts or other ODbL-licensed data is used, and the Aliases section names the developer's choice as its source (R3 AC4; ARCHITECTURE Q1)
- **Tests:** Not applicable — data curation only; the shipped-content tests land in T22 (see Verification).
- **Verification:** `grep -c '"key"' app/src/main/assets/reference/staples.json` prints `10`; `grep -c '"PER_100G"' app/src/main/assets/reference/staples.json` prints `10`; `grep -cE '"(fibre|sugar|salt)[A-Za-z]*"' app/src/main/assets/reference/staples.json` prints `0`; `grep -c '"variant"' app/src/main/assets/reference/aliases.json` prints at least `1`; `iconv -f UTF-8 -t UTF-8 app/src/main/assets/reference/staples.json > /dev/null && iconv -f UTF-8 -t UTF-8 app/src/main/assets/reference/aliases.json > /dev/null` exits 0; `for h in '## Staples' '## Aliases' '## Seasonality' '## Section order'; do grep -qx "$h" docs/reference-data-provenance.md || echo "missing: $h"; done` prints nothing; `grep -ci 'odbl' docs/reference-data-provenance.md` prints at least `1`. Manual, repeatable: the developer opens the CSV output beside `staples.json` and checks all four figures of every seed entry, and reads the alias list; the task is not done until the developer confirms both. Run the field-order check defined in the Phase 5 preamble.

### - [x] T19: Complete section list, seed mappings and `expected_sections.txt`

- **Requirement:** R4
- **Description:** Write `section_order.json` with the **complete** tesco.ie department list the developer supplies (names exactly as given, in the developer's chosen display order, `walkIndex` 0, 10, 20, … so later insertions need no renumbering, AD8) and a `mappings` entry for each of the 10 seed staples keys, write the same names in the same order one per line to `expected_sections.txt`, and fill the provenance record's `## Section order` Source and Retrieved lines from the developer's snapshot. In `docs/reference-data-provenance.md`, replace this dataset's section's `PENDING-SOURCE` Source, Retrieved and Licence lines with the developer-supplied values (stopping to ask for any the developer has not given), so no `PENDING-SOURCE` remains once both tasks are done.
- **Files:**
  - Read: `app/src/main/assets/reference/staples.json` — the 10 seed keys to map
  - Create: `app/src/main/assets/reference/section_order.json`
  - Create: `app/src/test/resources/reference/expected_sections.txt`
  - Modify: `docs/reference-data-provenance.md`
- **Dependencies:** T18
- **Parallel:** Yes (with T12–T17, T21) — not with T20, which also edits the provenance record
- **Acceptance Criteria:**
  - GIVEN the tesco.ie department list and display order the developer supplies, with its snapshot date
    WHEN `section_order.json` and `expected_sections.txt` are written
    THEN both hold exactly those names, in that order, and no section is added, merged, renamed or dropped by the implementer; if no list has been supplied, the task stops and asks (Q6; R4 AC5)
  - GIVEN the 10 seed staples keys
    WHEN their mappings are drafted
    THEN each maps to exactly one listed section, and the developer reviews and confirms every key-to-section pair before the task is done (R4 AC3, AC4)
  - GIVEN the provenance record
    WHEN its `## Section order` section is read
    THEN it names the tesco.ie department list as the source with the developer's snapshot date as `Retrieved` (R3 AC4)
- **Tests:** Not applicable — data curation only; the shipped-content tests land in T22 (see Verification).
- **Verification:** `grep -o '"name": "[^"]*"' app/src/main/assets/reference/section_order.json | sed 's/^"name": "//; s/"$//' | diff - app/src/test/resources/reference/expected_sections.txt` prints nothing; `grep -o '"walkIndex": [0-9]*' app/src/main/assets/reference/section_order.json | sort | uniq -d` prints nothing; `grep -c '"section"' app/src/main/assets/reference/section_order.json` prints `10`; `awk '/^## Section order/{f=1;next} /^## /{f=0} f' docs/reference-data-provenance.md | grep -cE '^- \*\*Retrieved:\*\* [0-9]{4}-[0-9]{2}-[0-9]{2}$'` prints `1`. Manual, repeatable: the developer reads `expected_sections.txt` top to bottom against the tesco.ie department list and the display order, then reads each `mappings` line, and confirms both.

### - [x] T20: Seasonality seed slice

- **Requirement:** R5
- **Description:** Write `seasonality.json` with a few Republic-of-Ireland keys the developer names, their in-season months transcribed from the seasonal calendar the developer names or provides (never UK months or general knowledge), and at least one entry whose substitutions the developer approves, each substitution being a key in the staples seed or in this slice, and fill the provenance record's `## Seasonality` Source and Retrieved lines. Substitutions follow the Phase 5 rule: developer-supplied or developer-approved item by item, and a key outside the seeded keys stops for the developer. In `docs/reference-data-provenance.md`, replace this dataset's section's `PENDING-SOURCE` Source, Retrieved and Licence lines with the developer-supplied values (stopping to ask for any the developer has not given), so no `PENDING-SOURCE` remains once both tasks are done. Until T23 adds the full staples set, a substitution may name only the 10 keys already in `staples.json` or another key in this slice; a substitute outside them is a stop-and-ask, never an invention.
- **Files:**
  - Read: `app/src/main/assets/reference/staples.json` — keys a substitution may name
  - Create: `app/src/main/assets/reference/seasonality.json`
  - Modify: `docs/reference-data-provenance.md`
- **Dependencies:** T19
- **Parallel:** Yes (with T12–T17, T21) — data and documentation files only
- **Acceptance Criteria:**
  - GIVEN the seasonal calendar the developer names and the seed keys the developer chooses
    WHEN each entry's months are written
    THEN every month set is taken from that calendar, a key the calendar does not cover is left out and listed, and the developer checks each entry's months against the calendar before the task is done (Q10; R5 AC2)
  - GIVEN the drafted substitutions
    WHEN the developer reviews them
    THEN at least one entry names at least one approved substitution, none names its own key, and each names a key in the staples seed or this slice (R5 AC2, AC3; AD11)
- **Tests:** Not applicable — data curation only; the shipped-content tests land in T22 (see Verification).
- **Verification:** `grep -c '"inSeasonMonths"' app/src/main/assets/reference/seasonality.json` prints at least `1`; `grep -cE '"substitutions": \[ *"' app/src/main/assets/reference/seasonality.json` prints at least `1`; `iconv -f UTF-8 -t UTF-8 app/src/main/assets/reference/seasonality.json > /dev/null` exits 0; `awk '/^## Seasonality/{f=1;next} /^## /{f=0} f' docs/reference-data-provenance.md | grep -cE '^- \*\*Source:\*\* .+$'` prints `1`; `awk '/^## Seasonality/{f=1;next} /^## /{f=0} f' docs/reference-data-provenance.md | grep -cE '^- \*\*Retrieved:\*\* [0-9]{4}-[0-9]{2}-[0-9]{2}$'` prints `1`; `grep -c PENDING-SOURCE docs/reference-data-provenance.md` prints `0`. Manual, repeatable: for each line of `seasonality.json`, the developer compares the months with the named seasonal calendar and reads the substitutions, and confirms.

### - [x] T21: Curation-policy helpers with negative controls

- **Requirement:** R3, R4, R5
- **Description:** Create `ShippedDatasetsTest` holding the curation-policy checks as top-level internal helper functions over tables and texts (surface-form violations, alias self-maps, alias chains, self-substitutions, unresolved alias targets and substitutions against the AD11 known-key sets, unmapped staples keys, nutrient upper bounds, section-set difference against an `expected_sections.txt` text, at-least-one-substitution, a 1 MB size cap and a JSON depth measure), each proven able to fail by one violating fixture table built through the `internal` table constructors and one passing control.  Every F2 test class runs under `@RunWith(RobolectricTestRunner::class)` (design Testing Strategy), because the store logs through `android.util.Log` and `org.json` is a stub on a plain JVM.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/reference/ReferenceTables.kt` — `internal` constructors
  - Create: `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt`
- **Dependencies:** T4
- **Parallel:** Yes (with T5–T20) — a new test file only
- **Acceptance Criteria:**
  - GIVEN each helper and one fixture table or text that violates exactly its rule
    WHEN the helper runs on it and on a passing control
    THEN it reports the offending key or line for the violation and nothing for the control (design `[SEAL-15]`)
  - GIVEN the surface-form rule
    WHEN it is applied to a key
    THEN the key passes only if it is non-empty, equals its own `lowercase(Locale.ROOT)`, equals its `trim()`, and contains no two consecutive spaces (R3 AC3; Q3)
  - GIVEN the depth measure, defined as the deepest nesting of JSON objects and arrays with the top-level object at depth 1 and brackets inside string literals not counted, and the size cap, defined as at most 1 048 576 bytes
    WHEN they are applied to the design's four layouts and to a depth-5 text and a 1 048 577-byte text
    THEN the layouts measure 3 (staples, aliases, section order) and 4 (seasonality) and pass the cap of 4, while the depth-5 and oversized texts fail (AD6; design `[SEAL-06]`)
  - GIVEN the nutrient upper bound
    WHEN it is applied
    THEN energy above 1000 kcal or any macro above 100 g per 100 g is reported (AD10)
- **Tests:**
  - `` `surface form check rejects uppercase padded and double-spaced keys`() ``
  - `` `alias self-map check rejects a variant mapped to itself`() ``
  - `` `alias chain check rejects a target that is also a variant`() ``
  - `` `self-substitution check rejects an entry substituting its own key`() ``
  - `` `cross-resolution check rejects an alias target outside the known keys`() ``
  - `` `cross-resolution check rejects a substitution outside staples and seasonality keys`() ``
  - `` `unmapped key check rejects a staples key with no section mapping`() ``
  - `` `nutrient upper bound check rejects energy above 1000 kcal`() ``
  - `` `nutrient upper bound check rejects a macro above 100 g`() ``
  - `` `section set comparison fails on a stale or an extra snapshot line`() ``
  - `` `substitution presence check rejects a slice with no substitutions`() ``
  - `` `depth measure gives 3 and 4 for the design layouts and rejects depth 5`() ``
  - `` `size cap rejects an asset over 1 MB`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ShippedDatasetsTest"`

### - [x] T22: Shipped-content tests and `ProvenanceRecordTest`

- **Requirement:** R1, R3, R4, R5
- **Description:** Add to `ShippedDatasetsTest` the tests that load the four real assets through `loadDataset(AndroidAssetSource(context.assets), …)` and run T21's helpers over them (no 160–180 band yet, and no fixed seasonality or alias count), and create `ProvenanceRecordTest`, which finds the record through `RepoPaths`, splits it at `## ` headings, and asserts that each of the four dataset sections carries its own `- **Source:**` line and its own `- **Retrieved:**` ISO date, so a line in one section never satisfies another (design `[SEAL-21]`, spec `[SEAL-07]`, `[SEAL-17]`). `ProvenanceRecordTest` splits sections on lines anchored at exactly `## ` (two hashes), so the `###` subsections T25 adds under `## Staples` stay inside their section.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/RepoPaths.kt` — repo-root search
  - Modify: `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt`
  - Create: `app/src/test/java/ie/pantry/data/reference/ProvenanceRecordTest.kt`
- **Dependencies:** T12, T17, T20, T21
- **Parallel:** Yes (with T14, T15, T16) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `app/src/main/assets/reference/`
    WHEN it is inspected through `RepoPaths`
    THEN the four dataset files exist, each decodes as strict UTF-8, each is at most 1 MB with depth at most 4, and each loads `Ready` with a count equal to its raw array length (R1 AC1, AC3; AD6)
  - GIVEN the shipped staples
    WHEN every entry is inspected
    THEN each is `PER_100G` with four finite, non-negative nutrients within the upper bounds, and every key is in surface form and appears once (R3 AC2, AC3)
  - GIVEN the shipped section order and staples
    WHEN they are inspected
    THEN every section has one unique index, every mapping names a listed section, every staples key maps to a section, and the section names equal `expected_sections.txt` as a set (R4 AC1–AC5)
  - GIVEN the shipped seasonality and aliases
    WHEN they are inspected
    THEN each has at least one entry, months are non-empty distinct values 1..12, at least one entry names a substitution, substitutions and aliases are in surface form, never self-referencing, never chained, and resolve to known keys (R5 AC1–AC4; AD11)
  - GIVEN `docs/reference-data-provenance.md`
    WHEN `ProvenanceRecordTest` runs from `app/` or from the repository root
    THEN the file exists, has the four exact headings, and each section's own `Source` line is non-empty and its own `Retrieved` date parses with `LocalDate.parse` (R3 AC4)
- **Tests:**
  - `` `four dataset assets exist under main assets reference`() ``
  - `` `every shipped asset decodes as strict UTF-8 within the size and depth caps`() ``
  - `` `every shipped asset loads Ready with count equal to its raw array length`() ``
  - `` `every shipped staples entry is PER_100G with finite non-negative bounded nutrients`() ``
  - `` `every shipped staples key is in canonical surface form and unique`() ``
  - `` `every shipped section has one unique walk index`() ``
  - `` `every shipped mapping names a listed indexed section`() ``
  - `` `every shipped staples key resolves to a listed section`() ``
  - `` `shipped section names equal the expected sections snapshot`() ``
  - `` `shipped seasonality months are non-empty distinct values from 1 to 12`() ``
  - `` `shipped seasonality names at least one substitution and none of its own key`() ``
  - `` `shipped aliases are surface form never self-mapped and never chained`() ``
  - `` `shipped alias targets and substitutions resolve to known keys`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt`
  - `` `provenance record exists at the repository docs path`() ``
  - `` `provenance record has the four exact dataset headings`() ``
  - `` `each dataset section has its own non-empty Source line`() ``
  - `` `each dataset section has its own Retrieved date that parses as an ISO date`() ``
  - `` `a Retrieved date in a neighbouring section does not satisfy a section missing its own`() `` — synthetic text, negative control
  - File: `app/src/test/java/ie/pantry/data/reference/ProvenanceRecordTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ShippedDatasetsTest" --tests "*.ProvenanceRecordTest"`; this requires the `PENDING-SOURCE` placeholders from T18 to have been replaced by T19 and T20 (`grep -c PENDING-SOURCE docs/reference-data-provenance.md` prints `0`). If Robolectric does not expose the assets through `context.assets` (DR7), apply DR7's fallback (a test-only file-backed `AssetSource` over `src/main/assets/`) and log an Implementation Deviation.

## Phase 6: Full Curation (FC6)

Each task below changes one dataset (plus the provenance record where stated), so each is one reviewable diff (`[DEF-01]`, DR5). The Phase 5 format and honesty rules apply. Claude runs no mutating git; the developer commits each task's files.

### - [x] T23: Staples full set from the prior CSV, with a mapping for every key

- **Requirement:** R3, R4
- **Description:** Extend `staples.json` to every usable data row of the prior build's 166-row CSV (same verbatim-copy and surface-form rules as T18) and, in the same reviewable step, add a `mappings` entry in `section_order.json` for every new key so `ShippedDatasetsTest` stays green (see Q3).
- **Files:**
  - Read: `git show e037454^:app/src/main/assets/staples.csv` — all data rows
  - Read: `app/src/test/resources/reference/expected_sections.txt` — the only sections a key may map to
  - Modify: `app/src/main/assets/reference/staples.json`
  - Modify: `app/src/main/assets/reference/section_order.json`
- **Dependencies:** T22
- **Parallel:** Yes (with T14, T15, T16) — data files only
- **Acceptance Criteria:**
  - GIVEN each CSV data row
    WHEN it becomes a staples entry
    THEN its four figures equal the CSV's exactly, and a row whose surface-form key repeats an earlier key, or whose figure is missing or non-numeric, is left out and listed for the developer rather than merged, estimated or filled (R3 AC2, AC3)
  - GIVEN the resulting entry count
    WHEN it is compared with the 160–180 band
    THEN it is inside the band, or the task stops and the developer decides; the implementer never adds rows from any other source to reach the band (R3 AC1; Q4)
  - GIVEN every staples key
    WHEN its mapping is drafted
    THEN it maps to exactly one section listed in `expected_sections.txt`, and the developer reviews every key-to-section pair before the task is done (R4 AC4; Q6)
- **Tests:** Not applicable — data curation only; covered by T22's shipped-content tests (see Verification).
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ShippedDatasetsTest"`; `n=$(grep -c '"key"' app/src/main/assets/reference/staples.json); [ "$n" -ge 160 ] && [ "$n" -le 180 ] && echo ok` prints `ok`; `grep -c '"section"' app/src/main/assets/reference/section_order.json` prints the same number as `grep -c '"key"' app/src/main/assets/reference/staples.json`. Manual, repeatable: the developer compares entries at positions 0, 15, 30, … (every 15th line of `entries`) with the matching CSV rows on all four figures, reads the list of rows left out, and reads every `mappings` line against the section list; the task is done when the developer confirms all three. Run the field-order check defined in the Phase 5 preamble.

### - [x] T24: Staples 160–180 count-band assertion

- **Requirement:** R3
- **Description:** Add the count-band test to `ShippedDatasetsTest` now that the full staples set has landed, keeping it out of earlier steps so the seed stayed green (DR5, `[DEF-01]`).
- **Files:**
  - Modify: `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt`
- **Dependencies:** T23
- **Parallel:** Yes (with T25, T26, T27) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the shipped staples asset
    WHEN a Robolectric test loads it
    THEN it has between 160 and 180 entries inclusive (R3 AC1)
- **Tests:**
  - `` `shipped staples count is within 160 to 180 inclusive`() ``
  - File: `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ShippedDatasetsTest"`

### - [x] T25: Nutrition database spot-check record

- **Requirement:** R3
- **Description:** Record the developer's spot-check of at least 20 staples entries against a recognised public nutrition composition database the developer names, in a `### Nutrition database spot-check` table under `## Staples` (one row per key: key, database ID, database entry type, Pantry and database figures for the four nutrients, action), naming the database and its retrieval date, and apply to `staples.json` only the figures the developer decides to change, each also recorded in a `### Figures changed after the spot-check` table (key, field, old value, new value, database ID). The database's own retrieval date is a `- **Database retrieved:** YYYY-MM-DD` line in the spot-check subsection, so it does not collide with the section's `- **Retrieved:**` line that `ProvenanceRecordTest` reads. "At least 20" is a floor, not a ceiling: as actually executed, the developer supplied a full nutrition-composition database (CoFID) and directed that every staples key with a clear match be reconciled to it, not a hand-picked 20-key subset — 152 of 166 keys were matched and updated (513 individual field changes), with the 14 keys lacking an adequate raw/plain match left unchanged; both are recorded in the spot-check and changed-figures tables.
- **Files:**
  - Read: `app/src/main/assets/reference/staples.json` — current figures for the table's Pantry column
  - Modify: `docs/reference-data-provenance.md`
  - Modify: `app/src/main/assets/reference/staples.json` — only for developer-decided changes
- **Dependencies:** T23
- **Parallel:** Yes (with T24, T26, T27) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the database figures the developer supplies for at least 20 keys
    WHEN the spot-check table is written
    THEN each row carries the developer's database figures and ID as given, the implementer neither looks up nor derives a database figure itself, and the section names the database and its retrieval date (R3 AC4)
  - GIVEN a figure the developer decides to change
    WHEN `staples.json` is edited
    THEN the new value is exactly the developer's, and a row in the changed-figures table records key, field, old value, new value and database ID; no other figure changes (spec Never Do)
- **Tests:** Not applicable — provenance recording only; T22's `ShippedDatasetsTest` and `ProvenanceRecordTest` re-check the files (see Verification).
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ShippedDatasetsTest" --tests "*.ProvenanceRecordTest"`; `awk '/^### Nutrition database spot-check/{f=1;next} /^#/{f=0} f && /^\|/ && !/^\|[-: |]+$/{n++} END{print n-1}' docs/reference-data-provenance.md` prints at least `20`; `grep -c '^### Figures changed after the spot-check' docs/reference-data-provenance.md` prints `1`. Manual, repeatable: for each changed-figures row, the developer finds the key's line in `staples.json` and confirms the field holds the new value. Distinctness of the 20 keys and a non-empty database ID per row are a manual developer check (the awk only counts rows).

### - [x] T26: Seasonality slice to about 40 Irish keys

- **Requirement:** R5
- **Description:** Extend `seasonality.json` to the developer-reviewed slice of about 40 common Irish fruit, vegetable and herb keys the developer names (the number is a target, not a threshold), with months transcribed from the named seasonal calendar and substitutions for out-of-season entries drafted only from keys in `staples.json` or this slice and approved by the developer. Substitutions follow the Phase 5 rule: developer-supplied or developer-approved item by item, and a key outside the seeded keys stops for the developer.
- **Files:**
  - Read: `app/src/main/assets/reference/staples.json` — keys a substitution may name
  - Modify: `app/src/main/assets/reference/seasonality.json`
- **Dependencies:** T23
- **Parallel:** Yes (with T24, T25) — not with T27, which reads this slice's keys
- **Acceptance Criteria:**
  - GIVEN the developer's key list and the seasonal calendar the developer names
    WHEN each entry is written
    THEN its months come from that calendar only, an uncovered key is left out and listed, and the developer checks every entry's months and substitutions before the task is done (Q10; R5 AC2, AC3)
- **Tests:** Not applicable — data curation only; covered by T22's shipped-content tests (see Verification).
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ShippedDatasetsTest"`; `grep -c '"inSeasonMonths"' app/src/main/assets/reference/seasonality.json` is recorded in the T28 review line (no count is asserted, PLAN Q1). Manual, repeatable: the developer compares each line's months with the named calendar and reads each substitution, and confirms.

### - [x] T27: Final alias seed

- **Requirement:** R5
- **Description:** Replace or extend `aliases.json` with the handful of known exceptions the developer chooses when reviewing the full staples and seasonality keys (R5 [ASSUMPTION], Q10), each target a known key.
- **Files:**
  - Read: `app/src/main/assets/reference/staples.json`, `app/src/main/assets/reference/seasonality.json` — known keys
  - Modify: `app/src/main/assets/reference/aliases.json`
- **Dependencies:** T23, T26
- **Parallel:** Yes (with T24, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the variants and targets the developer names
    WHEN `aliases.json` is written
    THEN it holds only those entries, at least one, and each is surface form, not self-mapped, not chained and resolves to a known key; the implementer adds no alias of its own (R5 AC4; AD11)
- **Tests:** Not applicable — data curation only; covered by T22's shipped-content tests (see Verification).
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ShippedDatasetsTest"`; `grep -c '"variant"' app/src/main/assets/reference/aliases.json` prints at least `1`. Manual, repeatable: the developer reads every line of `aliases.json` and confirms it is one they chose.

### - [x] T28: Provenance record completion and developer review

- **Requirement:** R3, R4, R5
- **Description:** Complete `docs/reference-data-provenance.md` so each section states its source, licence and retrieval date, the Staples section keeps the prior-CSV origin, the spot-check and the no-ODbL statement, the Section order section records the developer's confirmation that the snapshot matches tesco.ie's department list and that the display order groups sections by conventional supermarket-department adjacency, and the Seasonality and Aliases sections record the developer's review of the final seeds with its date. Each provenance section carries a `- **Reviewed:** YYYY-MM-DD` line and a `- **Licence:**` line; the Verification greps for exactly those formats.
- **Files:**
  - Modify: `docs/reference-data-provenance.md`
- **Dependencies:** T24, T25, T26, T27
- **Parallel:** No — last curation step; it records reviews of every earlier data task
- **Acceptance Criteria:**
  - GIVEN the final four assets
    WHEN the developer reviews them and the record
    THEN the record carries a dated review line in each of the four sections, written from the developer's own confirmation (R3 AC4; R4 AC5; spec Success Criteria, Q10)
  - GIVEN the finished record
    WHEN `ProvenanceRecordTest` runs
    THEN it passes, and the spot-check size (at least 20) and the no-ODbL statement are confirmed at review (R3 AC4)
- **Tests:** Not applicable — documentation only; T22's `ProvenanceRecordTest` re-checks the file (see Verification).
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ProvenanceRecordTest"`; `for h in '## Staples' '## Aliases' '## Seasonality' '## Section order'; do awk -v h="$h" '$0==h{f=1;next} /^## /{f=0} f' docs/reference-data-provenance.md | grep -qE '^- \*\*Reviewed:\*\* [0-9]{4}-[0-9]{2}-[0-9]{2}$' || echo "no dated review line: $h"; awk -v h="$h" '$0==h{f=1;next} /^## /{f=0} f' docs/reference-data-provenance.md | grep -qE '^- \*\*Licence:\*\* .+' || echo "no licence line: $h"; done` prints nothing; `grep -c PENDING-SOURCE docs/reference-data-provenance.md` prints `0`. Manual, repeatable: the developer reads the whole record and confirms each review line states what they checked.

## Phase 7: Closeout

### - [x] T29: Feature closeout: full suite, lint, release APK and manifest checks

- **Requirement:** R1, R6, R7
- **Description:** From a fresh clone of the developer's committed branch, run the full suite, lint and both assembles, list the release APK's reference assets, run the spec's release-manifest greps, and confirm the database schema, manifest and dependency boundaries are unchanged (spec `[SEAL-20]`).
- **Files:**
  - Read: `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` — release component baseline
  - Read: `app/build/outputs/apk/release/*.apk` — asset listing
- **Dependencies:** T1–T28
- **Parallel:** No — final gate
- **Acceptance Criteria:**
  - GIVEN a fresh clone with no `build/` or `.gradle/` directories
    WHEN `./gradlew assembleDebug assembleRelease` and then `./gradlew :app:testDebugUnitTest lintDebug` run
    THEN all succeed, with every F1 test green (spec Success Criteria)
  - GIVEN the release APK
    WHEN its contents are listed
    THEN the four dataset assets are under `assets/reference/` (R1 AC2)
  - GIVEN the release-merged manifest, `app/schemas/` and `PantryDatabase`
    WHEN they are inspected
    THEN the manifest holds the F1 baseline (1/1/1/1 components, 2 exported, 0 `android.permission`), the database version is still 1, and nothing under `app/schemas/`, `app/src/main/java/ie/pantry/data/db/` or `AndroidManifest.xml` changed since F1 (R1 AC9; Network Exposure Triage)
- **Tests:** Not applicable — runs the full test suite; see Verification.
- **Verification:** Precondition: the developer has committed the working tree (Claude runs no mutating git). From a fresh clone: `./gradlew assembleDebug assembleRelease && ./gradlew :app:testDebugUnitTest lintDebug`; `unzip -l app/build/outputs/apk/release/*.apk | grep -c 'assets/reference/'` prints `4`; with `M=app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`, `for tag in activity service provider receiver; do echo "$tag: $(grep -c "<$tag" "$M")"; done` prints 1 for each, `grep -c 'android:exported="true"' "$M"` prints `2` and `grep -c 'uses-permission android:name="android.permission' "$M"` prints `0`; `git diff --quiet dcae1ea -- app/schemas app/src/main/AndroidManifest.xml app/src/main/java/ie/pantry/data/db` exits 0; `grep -niE 'okhttp|ktor|retrofit|serialization|moshi|gson|kotlin-reflect' gradle/libs.versions.toml app/build.gradle.kts` prints nothing; `grep -rn 'Initializer' app/src/main/java` prints nothing; `grep -rnE '\.opt[A-Z]' app/src/main/java/ie/pantry/data/reference` prints nothing. Precondition: the fresh clone needs the Android SDK location, so copy `local.properties` (git-ignored) into it or export `ANDROID_HOME` before running the gate.

## Implementation Order

1. T1, T3 — parallel start: T1 settles the Compose test tooling (DR3) while T3 lays down the pure types; the design lets step 2 run beside step 1.
2. T2, T4 — T2 proves the application hook and the Robolectric Compose harness (DR2 smoke) before anything depends on them; T4 builds the tables on T3's types.
3. T5 → T6 → T7 → T8 → T9 → T10 → T11 — serial, because every step edits `DatasetParser.kt` and `DatasetLoaderTest.kt`; T5–T7 run the staples rows and the characterisation rows first so DR1 is retired before any loader or store work. T21 (policy helpers, a new test file) can start any time after T4.
4. T12 — the `AssetSource` seam and `loadDataset` wrap the finished parser; loader-only rows join the matrix.
5. T13 — the store fixes the final `loadDataset`/`loadAndLog` names and the failure-caching rule. T18 → T19 → T20 (small real seed, serial on the provenance record) can run beside the code tasks from T11 on.
6. T14, T15, T17 — parallel: threading tests, container wiring and the read-only surface touch disjoint files.
7. T16 — the full blocked-read and companion tests need T2's hook, T14's blocking source and T15's fourth container parameter.
8. T22 — shipped-content and provenance tests prove the end-to-end path on the small real seed before the large curation effort.
9. T23 — full staples and their mappings in one reviewable step, keeping `ShippedDatasetsTest` green.
10. T24, T25, T26 — parallel: the band assertion, the spot-check record and the seasonality slice touch disjoint files. T27 follows T26, whose keys aliases may target.
11. T28 — the provenance record closes with the developer's dated reviews of every dataset.
12. T29 — final gate from a fresh clone.

Notes:
- **Parallel tasks share the `app` test source set.** Run parallel groups in separate worktrees, or serialise their Verification runs, so a half-written sibling task cannot break another task's `testDebugUnitTest`.
- **Developer inputs block specific tasks.** T18 (alias list), T19 (tesco.ie department list, snapshot date, display order, mapping review), T20 and T26 (seasonality keys, seasonal calendar, substitution approval), T25 (nutrition database figures) and T28 (review confirmations) cannot finish without them; the implementer stops and asks rather than supplying the data.
- **Coverage.** R1: T3, T5–T13, T22, T29. R2: T3, T4, T13. R3: T18, T21–T25, T28. R4: T4, T10, T19, T21–T23, T28. R5: T8, T9, T18, T20–T22, T26–T28. R6: T1, T2, T13–T16. R7: T4, T17, T29. FC1: T3, T4. FC2: T5–T11. FC3: T12. FC4: T13. FC5: T2, T15. FC6: T18–T20, T23, T25–T28. FC7: T1, T2, T14, T16, T17, T21, T22, T24. R1 AC2 and the release-manifest triage are T29's Commands checks, not unit tests; R1 AC9 is F1's `SchemaShapeTest` plus T29's `git` checks. No task carries a `[CFC-N]` tag: F2 is in none of CFC-1 to CFC-4 (spec Never Do).

## Implementation Deviations

> Phase-4 minor-deviation ledger — populated by the triage gate's minor path (`SKILL.md` § "Mid-implementation discovery"). Append-only during Phase 4; resolved at the Final-Check completion gate. Leave empty until a deviation is logged.

| Date | Task | What spec/design said | What was actually done | Why | Classification | Backport status |
|------|------|-----------------------|------------------------|-----|----------------|-----------------|
| 2026-09-27 | T12 | T12's test `` `android asset source maps a missing asset to ASSET_ABSENT`() `` asserted that `AndroidAssetSource` against the real Robolectric asset context, reading `ReferenceDataset.STAPLES`'s path, produces `LoadFailure.Category.ASSET_ABSENT` | Discovered failing (full-suite run) once T18 shipped the real `app/src/main/assets/reference/staples.json`: the asset now exists, so the loader returns `Ready`, not `LoadFailed`. Rewrote the test as `` `android asset source throws FileNotFoundException for a missing asset`() ``, calling `AndroidAssetSource.open()` directly against a path that is never one of the four shipped datasets' asset paths, so it stays correct once T19/T20 ship the remaining two datasets too | The original test's premise (the real staples asset is absent) held only until T18 landed; nobody re-ran the full suite between T18 and this discovery. `loadDataset`'s exception-to-category mapping for `ASSET_ABSENT` is already exhaustively covered elsewhere in `DatasetLoaderTest` via `FixtureAssetSource`, so testing `AndroidAssetSource`'s own throw-on-miss contract directly, against a guaranteed-absent path, is the robust fix rather than swapping to a different not-yet-shipped dataset that would go stale again at T19/T20 | Minor (test-only, no behavior/AC change; full 254-test suite green after the fix) | backported — T12's own **Tests:** list (this file) corrected to name the actual test |
| 2026-09-28 | T25 | T25's Description/AC frame the task as a bounded spot-check: "at least 20 staples entries," with the developer deciding case-by-case which figures (if any) to change | The developer supplied a full nutrition-composition dataset (CoFID, 2910 rows) rather than 20 individual lookups, and directed "take all of CoFID's as assumed correct, across the whole sheet." Hand-verified a hand-checked key-to-CoFID-entry mapping for 152 of the 166 staples keys (raw/plain forms only, no cooked/compound matches accepted); applied CoFID's figures to all 152 (513 individual field changes, all recorded in the provenance record's "Figures changed" table); left the 14 keys with no adequate raw/plain CoFID match unchanged (chopped tomato, passata, soured cream, cream cheese, breadcrumb, caster sugar, maple syrup, vegetable stock, fish sauce, balsamic vinegar, dijon mustard, chicken breast, chicken thigh, chicken); capped three CoFID carbohydrate figures (sugar, brown sugar, icing sugar) at exactly 100 g — CoFID reports them fractionally over 100 g/100 g, a measurement/rounding artefact, not a real value exceeding the physical maximum | The spot-check's own AC never capped the update at 20 keys — "at least 20" is a floor, and the developer's explicit direction to trust the whole sheet is exactly the "the developer decides" clause the AC already grants, just exercised across every key with a good match rather than a hand-picked subset. All 152→513-change work is fully auditable in the provenance record's two tables; the 14 left unmatched and the 3 physically-impossible-value caps are disclosed there too | Minor (data-only, developer-directed, fully disclosed in provenance record; full 254+13-new-test suite green) | backported — T25's own Description (this file) corrected to record the actual full-sheet scope |

## TDD Exceptions

> Phase-4 TDD-cycle-skip log — appended by the calling Claude during Phase 4 when the test-first red→green→refactor cycle is skipped for a code-stack task. Append-only during Phase 4; `Resolution`-column updates (`pending` → `accepted` or `remediate`) are resolved at the Final-Check completion gate. Leave empty until a skip is logged. **Code stacks (python/java/kotlin) only.** Not applicable for generic-profile tasks. (A code task that genuinely needs *no* test at all is not a skip — use the `**Tests:** none — <reason>` override instead.)

| Date | Task | Skip Reason | Resolution |
|------|------|-------------|------------|

## Open Questions

> All questions must be resolved before proceeding to implementation.

- [x] Q1: The fixture-matrix tasks (T5–T11) each create between 5 and 17 one-row fixture JSON files, beyond the 3–5 authored-file sizing rule if each fixture is counted as a file. This breakdown counts each task's fixtures as one fixture group (they are tiny, single-purpose data files that one test reads each). Accept that counting, or split the matrix tasks further (for example, one task per 5 rows)?
  - **Resolution:** Accepted. A fixture file is data, not authored logic, so each matrix task's one-row fixtures count as one group against the 3-5 file sizing rule, and each fixture still has its own named test. (Decided by the synthesizer.)
- [x] Q2: The companion zero-opens test needs a second test Application with a recording source (design `[SEAL-10]` asks for one but the design's File Structure lists only `BlockingReadPantryApplication.kt`). T16 adds `app/src/test/java/ie/pantry/ui/RecordingPantryApplication.kt`. Accept the extra test file, or should `BlockingReadPantryApplication` carry both sources behind a flag?
  - **Resolution:** Accepted. `app/src/test/java/ie/pantry/ui/RecordingPantryApplication.kt` is a small test-only file; it is clearer than a flag on `BlockingReadPantryApplication`. (Decided by the synthesizer.)
- [x] Q3: Design Implementation Sequence step 6 lists "staples to 160–180" and "mappings for every staples key" as separate curation steps. Adding staples without mappings would turn `every shipped staples key resolves to a listed section` red between the two, so T23 does both in one reviewable step (two files). Accept the merge, or split them and accept a knowingly red test between the two tasks?
  - **Resolution:** Accepted. The full staples set and a mapping for every staples key are one step, because splitting them would knowingly leave `every shipped staples key resolves to a listed section` failing between the two. (Decided by the synthesizer.)
- [x] Q4: If surface-form conversion of the prior CSV's names produces duplicate keys, or some rows lack a figure, the usable count may fall below 160 (the CSV has 166 rows; its header and contents were not inspected here, because this drafting pass ran no git). T23 then stops for the developer. Which is preferred if that happens: widen the band, accept fewer rows with a recorded reason, or have the developer supply extra rows from a named, non-ODbL source recorded in the provenance record?
  - **Resolution:** The implementer stops and asks the developer, reporting the exact usable-row count and the dropped rows, and the developer chooses then: widen the band, accept fewer rows with a recorded reason, or add rows from a named non-ODbL source. No default is baked into the plan. (Decided by the user.)
- [x] Q5: T18, T19, T20, T25, T26, T27 and T28 need developer-supplied inputs: the alias exceptions, the tesco.ie department list with its snapshot date and display order, the seasonality key list with the seasonal calendar (URL or copy), the substitution approvals, the nutrition database figures for at least 20 keys, and dated review confirmations. Confirm the developer will provide each at its task (the implementer will stop and ask otherwise), and whether the implementer may fetch the seasonal-calendar and tesco.ie pages the developer names to draft from, or must work only from a copy the developer provides.
  - **Resolution:** Only copies the developer provides: the developer saves or pastes each source (alias exceptions, the tesco.ie department list with its snapshot date and display order, the seasonality key list with the seasonal calendar, nutrition database figures for at least 20 keys, dated review confirmations) into the working tree, and the implementer never fetches an external page. The provenance record cites exactly what the developer supplied. (Decided by the user.)
- [x] Q6: Matrix and shipped-content test names avoid `.`, `:`, `/`, `[`, `]`, `<`, `>` and apostrophes so they are legal backtick names on the JVM and match by name at the completion gate; values such as `3.0` and `-0.0` are therefore spelled out (`three point zero`, `negative zero`). Accept this naming convention?
  - **Resolution:** Accepted. Test names spell out values (`three point zero`, `negative zero`) and avoid `.`, `:`, `/`, brackets and apostrophes, so they are valid backtick names on the JVM and match by name at the completion gate. (Decided by the synthesizer.)

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
| 1    | 2026-09-26 | 1     | 0           | 15        | 0        | 9      | tags=d1u0c0                     |
| 2    | 2026-09-26 | 1     | 0           | 12        | 0        | 4      | tags=d1u0c0                     |
| 3    | 2026-09-26 | 1     | 1           | 7         | 0        | 2      | tags=d1u0c0                     |
| 4    | 2026-09-26 | 0     | 0           | 0         | 0        | 13     | converged (0 HIGH); tags=d0u0c0 |
| 5    | 2026-09-27 | 0     | 0           | 0         | 0        | 4      | converged (0 HIGH); tags=d0u0c0; upstream-panel 2dd7c8f8 |

### Sealed dispositions

- `[SEAL-01]` **T24 (one count-band test) could fold into T23** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the 160-180 band assertion lands only in the full-curation step and is its own independently reviewable gate; folding it into T23 would grow that data task and blur its exit check.
- `[SEAL-02]` **T5-T11 form a serial chain over the same two files that…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the approved spec routed the strict-parse criterion here as a fixture matrix of independently checkable rows ([DEF-02]), so the one-group-per-task split is the requested shape and Open Question Q1 already accepted the file count.
- `[SEAL-03]` **T26 and T27 are two tiny data-only tasks with the same…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the approved spec asks for per-dataset reviewable curation steps ([DEF-01]) and each dataset has a different developer-supplied source and reviewer sign-off.
- `[SEAL-04]` **T28 duplicates review lines that earlier tasks already…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - T28 is the final dated-review gate that makes the provenance record complete and checkable in one place; the earlier tasks record drafts and the greps in T28 check the final form.
- `[SEAL-05]` **T16 not-completed assertion is vacuously true while nothing…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the design already records it as not coverage; the informative assertions are the open count, thread and not-timed-out checks.
- `[SEAL-06]` **The characterisation rule lets an implementer rewrite an…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the deviation log is the guard by design, and the R1 AC6 rows (zero-byte, top-level null) are fixed by the approved design's failure categories, so a mismatch is a logged deviation, not a silent rewrite.
- `[SEAL-07]` **T3 path-constant test is redundant with the…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - a one-line constants test fails with a clearer message than a loader failure and costs almost nothing.
- `[SEAL-08]` **T16 red-first probe is ceremony that duplicates the…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the probe is the only way to show the companion test can fail on the regression it exists to catch; it is local and reverted, and the wording was clarified in this pass.
- `[SEAL-09]` **T29 fresh-clone requirement is heavier than the design's…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - F1's closeout used the same fresh-clone gate and it caught the environmental prerequisites; the precondition is now stated.
- `[SEAL-10]` **T1 manifest-count greps partly duplicate T29's gate** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - T1 is the early baseline check, and duplication of a cheap grep is harmless.
- `[SEAL-11]` **T16 companion zero-opens test can pass trivially** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the red-first probe is the recorded manual step that shows it can fail, and the positive control in the same task covers the seam.
- `[SEAL-12]` **The Robolectric sentence is repeated in T3, T5 and T21** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - cosmetic and each statement is correct where it stands.
- `[SEAL-13]` **T28 line formats are conventions beyond AD13 and not tested…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - T28's Verification now checks both formats, and they are harmless conventions.
- `[SEAL-14]` **T29 repeats T1's manifest greps and T5's opt grep** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the repetition is cheap and is the intended final gate, and the finding itself asks for no action.
- `[SEAL-15]` **T25 row-count awk would under-count a headerless table** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the provenance table is written with a header by the same task, and the manual developer check covers a malformed table.
- `[SEAL-16]` **ProvenanceRecordTest would accept a surviving…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the greps in T20, T22 and T28 assert zero placeholders at the three points where they must be gone, so the guard exists; adding the same assertion to the test is an implementation-time refinement the implementer can make under T22's Verification.
- `[SEAL-17]` **The Phase 5 field-order check script is unspecified about…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the script is a throwaway written under the check's stated purpose (regenerate the four kept columns per key from the CSV and diff), and its first run shows immediately if it mishandles the comment lines.
- `[SEAL-18]` **T5 and T12 greps also match KDoc and comments** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - a false-alarm hit is visible and the implementer rewords the comment; the greps err toward flagging, which is the safe direction for forbidden accessors.
- `[SEAL-19]` **T18 Acceptance Criteria and Verification omit the Licence…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the Description is part of the task and T28 and T22 verify the final provenance form; the implementer follows the whole task, and the placeholder lifecycle was traced across T18 to T22 and T28.
- `[SEAL-20]` **T18 does not give the exact bold line format for the…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - T28's Verification names the exact format and fails loudly on a mismatch, so the implementer corrects it at that gate.
- `[SEAL-21]` **Sentinel paragraph leaves two array fixtures unaddressed** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - both can hold a string, so the general rule already applies to them and they are not exempt; wording only.
- `[SEAL-22]` **T25 and T27 parallel claim depends on T25 changing figures…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - T25 records the developer's spot-check corrections to figures only, and a key change would trip T22's cross-resolution checks immediately.
- `[SEAL-23]` **T6 shorthand for the not-object fixture is ambiguous** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the fixture content is written where the fixture is created and the test asserts its tuple, so the shorthand cannot mislead.
- `[SEAL-24]` **T19 Verification does not assert Seasonality still holds…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - T20 depends on T19 and clears it, so an assertion adds nothing.
- `[SEAL-25]` **T14 acceptance criteria state two clauses twice** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - editorial; both statements are consistent.
- `[SEAL-26]` **T18 and T19/T20 repeat the Licence and replace-placeholder…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the repetition sits in each task an implementer reads alone and the statements agree.
- `[SEAL-27]` **T18 depends on T11 though it is pure data** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - harmless serialisation stated in Implementation Order.
- `[SEAL-28]` **T19 Parallel note not with T20 is redundant** (pass 4, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - editorial; T20 depends on T19.
- `[SEAL-29]` **The dispatch brief mis-attributed the "conventional…** (pass 5, accepted-as-risk) — Defense: a labeling slip in the review-dispatch prompt, not a defect in tasks.md itself — T28's actual wording already matches spec.md R4 AC5 verbatim, confirmed by both panelists.
- `[SEAL-30]` **T28's developer-review confirmation of the display order's…** (pass 5, accepted-as-risk) — Defense: mirrors the already-accepted risk RK7 / spec `[SEAL-05]` — a data-edit fix is cheap even late, and JSON re-editing carries no schema migration, so this is not a new risk class introduced by the terminology cascade.
- `[SEAL-31]` **`walkIndex`-naming test/fixture names (e.g. "rejects a…** (pass 5, accepted-as-risk) — Defense: the identifier-vs-prose distinction is already covered by design.md's `[SEAL-24]`; a residual grep-hygiene footnote, not a defect.
- `[SEAL-32]` **Q5's question text says "FDC figures" while its Resolution…** (pass 5, accepted-as-risk) — Defense: pre-existing synonym drift (FDC = USDA FoodData Central) unrelated to this pass's display-order cascade; out of scope here.

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to implementation
- **Content Hash:** `c87b7aa50fa3daec`
- **Hash basis:** v2