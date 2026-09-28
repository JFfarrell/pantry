# Feature: Reference Data Store & v1 Dataset Curation

**PLAN feature identifier:** `F2`

## Objective

**From PLAN F2:** `blueprint/03_PLAN.md` → Feature Breakdown → `### F2: Reference Data Store & v1 Dataset Curation` (Description and Acceptance Criteria); component C2 per `blueprint/02_ARCHITECTURE.md` → `### C2 — Reference Data Store`.

F2 delivers the four curated, read-only reference datasets as APK assets and a lazy, off-main-thread, load-once store that answers lookups by canonical key with an explicit absence value, so later features have real reference data to run against.

## Requirements

### R1: Datasets load from bundled JSON assets

As the developer, I want the four reference datasets shipped as plain JSON assets inside the APK and parsed into typed, read-only entries, so that curated data stays diffable in git and reaches every build with no network and no Room table.

**Terms.** For counting and duplicate detection, a `SectionOrderEntry` is one section (a name plus a display-order index); the canonical-key-to-section mappings are a second array in the same asset, counted and checked for duplicate keys separately. A duplicate is two entries sharing the lookup key (the alias variant, the staples key, the seasonality key, the section name, or the mapped key). Duplicate field names inside a single JSON object are not detected (`org.json` keeps the last value); RK8 records this.

**Acceptance Criteria:**

- GIVEN the app module's source tree
  WHEN `app/src/main/assets/` is inspected
  THEN it holds at least one JSON asset for each of `StaplesEntry`, `AliasEntry`, `SeasonalityEntry` and `SectionOrderEntry` data, and each is a plain UTF-8 JSON text file (that it is tracked in git is confirmed at review, not by a test).

- GIVEN a release APK built with `./gradlew assembleRelease`
  WHEN its contents are listed
  THEN all four dataset assets are present under the APK's `assets/` directory.

- GIVEN the four shipped dataset assets
  WHEN the loader reads them under a Robolectric test
  THEN each parses without error into its typed entries, and the number of entries loaded equals the number of entries in the corresponding JSON file.

- GIVEN a fixture dataset in which one entry omits a required field (for example a staples entry with no energy value)
  WHEN the loader parses it
  THEN parsing fails with a typed load failure naming the dataset and the entry's position, and the missing field is never materialised as `0`, `0.0`, an empty string or any other default.

- GIVEN a fixture dataset in which two entries share the same lookup key
  WHEN the loader parses it
  THEN parsing fails with a typed load failure rather than keeping either entry silently.

- GIVEN a fixture asset that is not valid JSON, or is absent
  WHEN the loader is asked for that dataset
  THEN the result is a typed load failure that is distinguishable from the lookup-miss absence value R2 defines, so a broken asset is never reported to a caller as "not in the table". The runtime handling of that failure is Q7.

- GIVEN fixtures in which a required numeric field holds a string, a required string field holds a number, a required field holds JSON `null`, a whole-number field (a month or an index) holds a fractional value (including `3.0`), or the document has content after its closing bracket
  WHEN the loader parses each
  THEN each is a typed load failure naming the dataset and the entry's position, and none is coerced (`org.json`'s `getString` turns a number into text and `getInt` truncates a double, so the loader checks each value's type explicitly, per Q1).

- GIVEN an asset that fails to load (absent or invalid)
  WHEN a lookup is made and then made again
  THEN each lookup returns the typed load failure and no exception escapes to the caller; the failed load is not retried within the process (a packaging defect does not heal itself, so the failure is kept for the life of the process); and the only log line carries the dataset name and a failure category, never asset content (Q7).

- GIVEN `app/schemas/ie.pantry.data.db.PantryDatabase/1.json` and `PantryDatabase`
  WHEN they are inspected after F2 lands
  THEN the database version is still 1, the schema JSON is unmodified, and no table holds reference-dataset content — so a later coverage increase is a JSON data edit with no schema migration.

### R2: Lookup by key with an explicit absence value

As the developer building C4, C5, C6 and C10 (F3, F11, F9, F8), I want every lookup to return either the matching entry or an explicit absence value, so that "not in the table" can drive the *unmatched*, *unknown* and unknown-section states without being mistaken for real data. A caller handles both the typed load failure (R1) and the absence value, and neither is ever mistaken for a found entry.

**Acceptance Criteria:**

- GIVEN a loaded staples fixture containing an entry for key `water` whose nutrient values are all zero
  WHEN the staples table is queried for `water` and then for a key not in the fixture
  THEN the first returns the found entry with its zero values, the second returns the absence value, and the unit test asserts the two results are not equal and cannot be confused by a caller reading nutrient values.

- GIVEN a loaded alias fixture mapping one raw variant to a canonical key
  WHEN the alias table is queried for that variant and then for a variant not in the fixture
  THEN the first returns the mapped canonical key, and the second returns the absence value, which a unit test asserts is distinct from an empty-string key and from echoing the queried variant back.

- GIVEN a loaded seasonality fixture containing one entry that names no substitutions
  WHEN the seasonality reference is queried for that key and then for a key not in the fixture
  THEN the first returns the found entry with its months and an empty substitution list, the second returns the absence value, and a unit test asserts the absence value is distinct from a found entry with no substitutions.

- GIVEN a loaded section-order fixture whose first section has the lowest display-order index
  WHEN the section ordering is queried for a key mapped to that first section and then for a key with no mapping
  THEN the first returns that section and its display-order index, the second returns the absence value, and a unit test asserts the absence value is distinct from the first section and its index.

- GIVEN any of the four datasets
  WHEN its public lookup signature is inspected
  THEN a miss is expressed only through the absence value — no lookup returns a default entry, a zero or an empty record for a miss, and no lookup throws for a miss.

### R3: v1 staples nutrition table

As the developer building the offline nutrition path (F9), I want a curated staples table of about 170 raw ingredients, so that G4's nutrition figures resolve with no network for the common case.

**Acceptance Criteria:**

- GIVEN the shipped staples asset
  WHEN a Robolectric test loads it
  THEN it contains between 160 and 180 entries inclusive (the tolerance band for PLAN's "approximately 170", Q4 resolved).

- GIVEN every entry in the shipped staples asset
  WHEN a test inspects it
  THEN every nutrient field the entry schema defines (Q5: `basis`, kcal, protein, fat, carbohydrate) holds a present, finite, non-negative number on one declared basis (every v1 row is `PER_100G`, Q5 resolved), and no field is absent.

- GIVEN every key in the shipped staples asset
  WHEN a test inspects it
  THEN each key is non-empty, lowercase, has no leading or trailing whitespace and no repeated internal spaces, and appears once (the canonical-key surface form, Q3 resolved).

- GIVEN the shipped staples asset
  WHEN its provenance record is inspected
  THEN `docs/reference-data-provenance.md` exists with a section for each of the four datasets naming its source and retrieval date; the staples section records the origin of the seed rows, the spot-check against a recognised public nutrition composition database the developer names (at least 20 entries, with the dataset and its retrieval date) and a statement that no Open Food Facts or other ODbL-licensed data was used (ARCHITECTURE Q1, Q4 resolved); and a test (reading the file relative to the repository root, since Gradle runs unit tests in `app/`) asserts the file exists, names all four datasets and carries a retrieval date for each; the spot-check size and the no-ODbL statement are confirmed at review.

### R4: Complete Tesco Ireland section-ordering list

As a Pantry user preparing a Tesco Ireland click-and-collect order (an order placed online and collected already-picked, with no in-store shopping), I want every shopping-list section to have one fixed display position, so that F11 can show my list grouped by section in a stable, organized order without guessing. The order is a presentation order, not a physical store-walk order: shopping is online, so no in-store walk exists for it to model.

**Acceptance Criteria:**

- GIVEN the shipped section-order asset
  WHEN a Robolectric test loads it
  THEN every section it names has exactly one display-order index, and no section is named without an index.

- GIVEN the shipped section-order asset
  WHEN its display-order indices are compared
  THEN no two sections share an index.

- GIVEN every canonical-key-to-section mapping in the shipped section-order asset
  WHEN a test resolves the mapped section
  THEN it names a section that is in the asset's section list with an index, so no key maps to an unindexed or unlisted section.

- GIVEN the shipped staples asset and the shipped section-order asset
  WHEN a test looks up each staples key in the section mapping
  THEN every staples key resolves to a listed section (Q6 resolved: every staples key maps to a section at v1).

- GIVEN the shipped section-order asset
  WHEN a test compares its section names, as a set, with the enumerated tesco.ie department list held in `app/src/test/resources/reference/expected_sections.txt`
  THEN the sets are equal, so drift from the recorded snapshot fails mechanically; that the snapshot itself matches tesco.ie's department list, and that the display order groups sections by conventional supermarket-department adjacency — the same order F11 groups by when rendering the generated list — are confirmed by the developer's review recorded in the provenance record ("complete at v1", PLAN F2 Description, Q6 resolved).

### R5: v1 seasonality/substitution seed slice and alias seed

As the developer, I want a first Republic-of-Ireland seasonality/substitution slice and a seeded alias table shipped as plain JSON, so that F8 and F3 have real data to run against and F17 can grow coverage as a data edit. **[ASSUMPTION]** The alias seed is minimal: a handful of known exceptions chosen by the developer when reviewing the seeds (Q10), and reconciled with F3's rule by F3's own tests.

**Acceptance Criteria:**

- GIVEN the shipped seasonality asset
  WHEN a Robolectric test loads it
  THEN it contains at least one entry, and the test asserts no fixed entry count (PLAN F2, Q1 resolved).

- GIVEN every entry in the shipped seasonality asset
  WHEN a test inspects it
  THEN its in-season months are a non-empty set of distinct values from 1 to 12, and each substitution it names is a key in the same canonical-key surface form R3 checks and is not the entry's own key.

- GIVEN the shipped seasonality asset
  WHEN a test inspects its entries
  THEN at least one entry names at least one substitution (Q10 resolved: the seed includes substitutions for out-of-season entries), so the substitution path is exercised by real shipped data.

- GIVEN the shipped alias asset
  WHEN a Robolectric test loads it
  THEN it contains at least one entry, every variant and every target is in the canonical-key surface form R3 checks (lookup is an exact match on the normalised variant, Q3, so a variant outside that form could never be hit), and no entry maps a variant to itself.

- GIVEN two seasonality fixtures that differ only in that the second has one extra entry, added by editing the JSON alone
  WHEN each is loaded through the same loader
  THEN the extra entry is found by lookup in the second and absent in the first, with no Kotlin source change and no Room schema version change between the two.

### R6: Lazy, off-main-thread, load-once

As a Pantry user opening the app on a low-end phone, I want the first screen drawn before any dataset is read, so that curated data never delays launch.

**Acceptance Criteria:**

- GIVEN a freshly constructed `AppContainer`
  WHEN construction completes
  THEN no dataset asset has been opened, asserted by a test using an asset source that records every open.

- GIVEN a dataset that has not yet been loaded
  WHEN the first lookup against it is made from the main thread
  THEN the asset read and parse run on a non-main thread, asserted by a test that records the thread on which the asset source is opened, and the calling main thread is not blocked while the read is in flight (whether the lookup is a `suspend` call or reads an immutable snapshot is a Design choice), and the test asserts the caller regains control (the lookup suspends or returns) before the injected source's read completes.

- GIVEN a dataset that has not yet been loaded
  WHEN several lookups against it are started concurrently
  THEN the asset is opened and parsed exactly once, and every lookup receives entries from that single load.

- GIVEN a dataset that has already been loaded
  WHEN further lookups are made
  THEN the asset is not opened again for the life of the process.

- GIVEN an asset source that blocks and never completes a dataset read, injected through the `AppContainer` constructor by an overridable container-creation hook on `PantryApplication` (the production default reads the real assets; Q8)
  WHEN a Compose UI test under Robolectric starts `MainActivity` with that container and issues a dataset lookup from a background coroutine
  THEN the placeholder screen's content is displayed while that read is still blocked, and the test asserts the blocked read was actually started, so the test cannot pass without exercising the seam. Nothing at app start touches the datasets (Q8). This is a seam-exercise test: it fails if an eager read is added to the Application or Activity startup path (the first criterion covers container construction), and it needs `PantryApplication` and its container-creation hook to be `open`, a test subclass registered with `@Config(application = …)`, and the blocked read released in `@After` so no thread leaks.

### R7: Read-only at runtime

As the developer, I want the reference data store to expose no way to change loaded data, so that C4, C5, C6 and C10 can share it without locking (ARCHITECTURE C2 Key Concerns).

**Acceptance Criteria:**

- GIVEN the public surface of `ie.pantry.data.reference`
  WHEN it is inspected
  THEN no public or internal function, property setter or constructor parameter lets a caller add, remove or replace a loaded entry.

- GIVEN every public entry type (`StaplesEntry`, `AliasEntry`, `SeasonalityEntry`, `SectionOrderEntry` and any type they contain)
  WHEN it is inspected
  THEN all properties are `val`, and any collection property is typed as a read-only Kotlin collection interface (`List`, `Set`, `Map`), never a `Mutable*` type.

- GIVEN the reference data store
  WHEN its implementation is inspected
  THEN it writes nothing: no asset, file, database row or preference is written by any code in `ie.pantry.data.reference`.

- GIVEN a collection returned by any lookup on any of the four datasets
  WHEN a test casts it to its mutable counterpart and attempts to modify it
  THEN the attempt throws or leaves every later lookup unchanged.

## Project Structure

```
pantry/                                        (repository root)
├── docs/reference-data-provenance.md          NEW: where each dataset's content came from (R3, R4, R5)
├── gradle/libs.versions.toml                  Version catalog — gains Compose UI-test aliases (no JSON library: Q1 is `org.json`)
└── app/
    ├── build.gradle.kts                       Test dependencies for the Compose UI test
    └── src/
        ├── main/
        │   ├── assets/reference/              NEW: the four dataset JSON files (plain text, versioned in git)
        │   └── java/ie/pantry/
        │       ├── PantryApplication.kt       MODIFIED: overridable container-creation hook (R6 test seam)
        │       ├── di/AppContainer.kt         MODIFIED: exposes the one reference data store
        │       └── data/reference/            NEW: entry types, absence value, loader, store
        └── test/
            ├── java/ie/pantry/
            │   ├── data/reference/            NEW: loader, lookup/absence, threading, shipped-content tests
            │   ├── di/AppContainerTest.kt     MODIFIED: no asset I/O on construction; one store instance
            │   └── ui/                        NEW: cold-start first-paint Compose test
            └── resources/reference/           NEW: small fixture datasets (zero-valued entry, missing field, duplicate key, invalid JSON)
```

The package `ie.pantry.data.reference` sits beside F1's `ie.pantry.data.db` and `ie.pantry.data.thumbnail`. File names below are proposals **[ASSUMPTION — the Design phase fixes the final split]**.

### New Files

- `app/src/main/assets/reference/staples.json` — the staples nutrition table (R3).
- `app/src/main/assets/reference/aliases.json` — the alias table seed (R5).
- `app/src/main/assets/reference/seasonality.json` — the Republic-of-Ireland seasonality/substitution seed slice (R5).
- `app/src/main/assets/reference/section_order.json` — the Tesco Ireland section list with display-order indices, plus the canonical-key-to-section mapping (R4).
- `app/src/main/java/ie/pantry/data/reference/ReferenceEntries.kt` — `StaplesEntry`, `AliasEntry`, `SeasonalityEntry`, `SectionOrderEntry`.
- `app/src/main/java/ie/pantry/data/reference/Lookup.kt` — the explicit found/absent result type (R2).
- `app/src/main/java/ie/pantry/data/reference/DatasetLoader.kt` — strict parse and validation of one asset, typed load failure (R1).
- `app/src/main/java/ie/pantry/data/reference/ReferenceDataStore.kt` — lazy, off-main-thread, load-once cache and the four lookups (R2, R6, R7).
- `app/src/test/java/ie/pantry/data/reference/DatasetLoaderTest.kt` — R1's parse, missing-field, duplicate-key and invalid-asset criteria against fixtures.
- `app/src/test/java/ie/pantry/data/reference/ReferenceLookupTest.kt` — R2's per-dataset found/absent criteria.
- `app/src/test/java/ie/pantry/data/reference/ReferenceLoadThreadingTest.kt` — R6's lazy, off-main-thread and load-once criteria.
- `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt` — R3, R4 and R5 against the real shipped assets.
- `app/src/test/java/ie/pantry/ui/FirstPaintNotBlockedTest.kt` — R6's blocked-read Compose UI test, with a test-only `PantryApplication` subclass supplying the blocking container.
- `docs/reference-data-provenance.md` — the provenance record: a section per dataset with source, licence and retrieval date; the tesco.ie department list snapshot; the Republic-of-Ireland seasonal calendar source the developer names; and the developer's seed review (R3, R4, R5).
- `app/src/test/resources/reference/expected_sections.txt` — the enumerated tesco.ie department list the shipped section-order asset is compared against (R4).
- `app/src/test/resources/reference/*.json` — fixture datasets used by the loader and lookup tests.

### Modified Files

- `app/src/main/java/ie/pantry/di/AppContainer.kt` — constructs and exposes one reference data store; construction opens no asset (R6). The new constructor parameter (the asset source) has a default that opens nothing and fails with a typed load failure if read, so existing positional calls in F1's tests keep compiling and never read reference data; real asset wiring is only in `AppContainer.production`.
- `app/src/main/java/ie/pantry/PantryApplication.kt` — the `container` property is created through an overridable hook whose default is `AppContainer.production(this)`, so a test application can supply a container with a blocking asset source (R6). `PantryApplication` and the hook are `open` so a test subclass can override it.
- `app/src/test/java/ie/pantry/di/AppContainerTest.kt` — asserts the store is the same instance across reads and that container construction opens no asset.
- `gradle/libs.versions.toml` and `app/build.gradle.kts` — add `androidx.compose.ui:ui-test-junit4` as `testImplementation` and `androidx.compose.ui:ui-test-manifest` as `debugImplementation` (it registers the test activity in the debug manifest only) through catalog aliases (Q9). No JSON library is added (Q1: `org.json`).

No change to `PantryDatabase`, its entities, DAOs, `app/schemas/` or `AndroidManifest.xml`.

## Commands

```bash
# Build both variants and confirm the assets ship in the release APK
./gradlew assembleDebug assembleRelease
unzip -l app/build/outputs/apk/release/*.apk | grep 'assets/reference/'

# Run F2's tests
./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.reference.*'
./gradlew :app:testDebugUnitTest --tests 'ie.pantry.ui.FirstPaintNotBlockedTest'
./gradlew :app:testDebugUnitTest --tests 'ie.pantry.di.AppContainerTest'

# Full suite (no F1 regressions)
./gradlew :app:testDebugUnitTest

# Static analysis — Android Lint, bundled with AGP
./gradlew lintDebug

# Merged release manifest: F2 adds no component or permission beyond the F1 baseline
M=app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
for tag in activity service provider receiver; do echo "$tag: $(grep -c "<$tag" "$M")"; done   # each prints 1
grep -c 'android:exported="true"' "$M"                                                       # prints 2
grep -c 'uses-permission android:name="android.permission' "$M"                              # prints 0
```

## Boundaries

### Always Do

- Ship reference data only as JSON assets under `app/src/main/assets/`, loaded once and held in memory (ARCHITECTURE Technology Choices, Bundled dataset format).
- Return the explicit absence value for every lookup miss, per R2.
- Parse strictly: a missing required field or a duplicate key is a typed load failure, never a default value.
- Read and parse assets off the main thread, and only on first use (R6).
- Expose the store through `AppContainer` by hand-written constructor injection, as F1 does for the database and DAOs.
- Write tests in the F1 style: JUnit 4 with `RobolectricTestRunner`, `kotlin.test` assertions, backtick-quoted test names, `kotlinx-coroutines-test` `runTest` for suspending code.
- Test lookup and loader behaviour against small fixture datasets, and test shipped content (R3–R5) in a separate test, so F17's data edits do not break behaviour tests.
- Record where the staples figures came from (R3).

### Ask First

- Adding any runtime dependency not already in `gradle/libs.versions.toml`, including a JSON library such as `kotlinx.serialization`, Moshi or Gson (Q1).
- Adding the Compose UI-test libraries or any other test dependency to the catalog (Q9).
- Changing the dataset JSON layout after it is agreed at Design, including adding a schema-version field (Q2).
- Adding fields to `StaplesEntry` beyond those Q5 fixes.
- Choosing the section set that makes the section-ordering list "complete" (Q6), and the seasonality seed's keys and source (Q10).
- Doing anything at app start that touches the datasets, such as a background pre-warm (Q8).

### Never Do

- Never mutate loaded data through any public or internal API, and never return a collection a caller could use to change the cached data (R7).
- Never return `0`, an empty entry, an empty string or a default for a lookup miss, and never throw for a miss (R2).
- Never present a failed load as an empty dataset or as a stream of misses (R1).
- Never store reference data in Room, and never bump `PantryDatabase`'s version or edit `app/schemas/` in this feature.
- Never source staples figures from Open Food Facts or any ODbL-licensed data, and never copy rows from any external dataset into a bundled dataset unless the provenance record names its source and licence (ARCHITECTURE Q1, R3); spot-checking a figure against a recognised public nutrition composition database the developer names, or taking months from a published calendar, is permitted.
- Never implement canonical-key derivation (case-fold, strip, singularise, alias resolution of free text) here; that is C4, built in F3.
- Never add a network permission, an HTTP client, or any component beyond the four already in the merged release manifest (see Network Exposure Triage): no new activity, service, receiver, provider or exported component, whether written by hand or pulled in by a new dependency.
- Never tag an F2 criterion `[CFC-N]`: PLAN's Cross-Feature Contracts (CFC-1 to CFC-4) do not list F2 as a participant. R2's explicit-absence rule is F2's own PLAN criterion.

### Network Exposure Triage

**Branch (a) — no new surface.** Introduces no new domain, route, port or outbound call; the datasets are APK assets read from local storage. Checked: (1) manifest permissions — `app/src/main/AndroidManifest.xml` declares no `<uses-permission>`, and F2 adds none; F1's `ManifestPolicyTest` already asserts no `android.permission.*` is requested and must still pass; (2) HTTP client on the classpath — `gradle/libs.versions.toml` and `app/build.gradle.kts` name no OkHttp, Ktor, Retrofit or other HTTP client, and F2 adds none (the JSON-library choice in Q1 must not bring one in transitively); (3) components — the merged release manifest holds exactly the F1 baseline of four: `ie.pantry.ui.MainActivity` (exported launcher), Room's `androidx.room.MultiInstanceInvalidationService` (not exported), `androidx.startup.InitializationProvider` (not exported) and `androidx.profileinstaller.ProfileInstallReceiver` (exported, guarded by the `DUMP` permission); the last three are contributed by libraries F1 already depends on, and F2 adds no activity, service, receiver or provider and no new exported component; (4) network calls at load time — the loader reads only from the APK's `AssetManager` (or an injected local source in tests) and opens no socket, URL connection or DNS lookup. The app's single HTTP call site (C7) arrives in F4.

**Merged-manifest check.** F1's `ManifestPolicyTest` reads the debug-merged manifest; the release-merged manifest is covered only by the Commands checks below. Beyond the source manifest, the merged release manifest is checked (Commands): it holds exactly the F1 baseline of four: `ie.pantry.ui.MainActivity` (exported launcher), Room's `androidx.room.MultiInstanceInvalidationService` (not exported), `androidx.startup.InitializationProvider` (not exported) and `androidx.profileinstaller.ProfileInstallReceiver` (exported, guarded by the `DUMP` permission), so one activity, one service, one provider and one receiver, of which two are exported; and, with F1's `ManifestPolicyTest`, no `android.permission.*` `uses-permission` entry. F2's test-only libraries are `testImplementation` and `debugImplementation`, so none reaches the release manifest.

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: Which JSON parser reads the assets? Options: `org.json` (in the Android framework, no new dependency, already used by F1's `SchemaShapeTest`, but its `opt*` accessors default missing values to `0`/`""`, so every read must use the throwing accessors); `kotlinx.serialization` (named in ARCHITECTURE for JSON-LD but not yet in the catalog, needs the serialization Gradle plugin; F5 will need it anyway); or another library (not named in ARCHITECTURE). Adding a library is an Ask First item.
  - **Resolution:** `org.json`, using only the throwing accessors (`getString`, `getInt`, `getDouble`, `getJSONArray`) and never the `opt*` accessors, so a missing field fails loudly and is never defaulted to `0` or an empty string. No new library is added. (Decided by the user.)
- [x] Q2: How are dataset schemas versioned? Does each asset carry a `schemaVersion` field that the loader checks, and what does a mismatch do? Or does git history alone version the format, since the assets and loader always ship together in one APK?
  - **Resolution:** No `schemaVersion` field. The assets and the loader always ship together in one APK, so git history versions the format; a later change to the format is a data-and-code edit in one commit. This reconciles ARCHITECTURE C2's "schema, versioning" boundary: versioning is by git, and a version field is deferred until an asset needs to outlive its loader. (Default accepted by the user.)
- [x] Q3: What canonical-key form do the curated keys use, given F3 builds the normalisation rule after F2? R3's surface-form check (lowercase, trimmed, single-spaced) is an assumption; singular form and stripping of preparation words cannot be checked until F3 exists. Is alias lookup an exact match on the string the caller passes, or does it case-fold first? Who reconciles curated keys with F3's rule, and when: F3's own tests, or a re-check of F2's data when F3 lands?
  - **Resolution:** Keys are exact-match on the canonical surface form (lowercase, trimmed, single-spaced). Alias lookup is an exact match on the already-normalised variant a caller passes. Reconciling curated keys with F3's normalisation rule is F3's obligation: F3's own tests must load F2's shipped datasets and assert that the staples and alias keys resolve under its rule. (Default accepted by the user.)
- [x] Q4: How are the ~170 staples curated, and how is their quality and provenance shown? The prior build's `app/src/main/assets/staples.csv` (deleted in commit `e037454`, readable with `git show e037454^:app/src/main/assets/staples.csv`) has 166 rows of per-100 g figures described as "typical raw values from standard composition tables". Is it the starting point? Is provenance recorded per file or per entry, and in the JSON itself or in a sibling document? What tolerance does "approximately 170" allow (R3 assumes 160–180)?
  - **Resolution:** Start from the prior build's 166-row `staples.csv` (`git show e037454^:app/src/main/assets/staples.csv`), spot-check the figures against a recognised public nutrition composition database the developer names (publicly available, openly licensed), and record provenance per file in a sibling plain-text document. The count band is 160-180 inclusive. Never Open Food Facts or any ODbL-licensed data. All figures are per 100 g (the reference database's own basis, metric, so no unit conversion is needed); volume conversion for liquids is F9's concern. (Decided by the user, with the per-100 g clarification.)
- [x] Q5: What fields does `StaplesEntry` carry? The prior build had kcal, protein, carbohydrate, fat, fibre, sugar and salt per 100 g. F9 must flag figures that "rest on an estimated weight conversion" (PLAN F9). Does a staples entry also carry a typical per-item weight or a density for count and volume quantities, or does that data live somewhere else?
  - **Resolution:** `StaplesEntry` carries a `basis` field, and kcal, protein, fat and carbohydrate. Every v1 row has basis `PER_100G`; the field exists so a later per-100 mL row is possible without a schema change. `basis` reuses F1's existing `NutritionBasis` enum (`ie.pantry.data.db.entity.NutritionBasis`, a plain enum with no Android imports) rather than defining a second one. Fibre, sugar and salt, which the prior build's CSV carried, are deliberately dropped: F9's breakdown needs only energy, protein, fat and carbohydrate, the same four F1's `NutritionCacheEntry` stores. A per-item weight or density is not part of `StaplesEntry`: F9 owns the estimated-conversion disclosure and specifies where that data lives. (Default accepted by the user.)
- [x] Q6: What makes the Tesco Ireland section-ordering list "complete"? Which section set is authoritative (tesco.ie's online department list, one physical store's aisle walk, or the developer's own list), and how many canonical keys must map to a section at v1 (every staples key, every seasonality key, or only what testing exercises)? Keys without a mapping land in F11's terminal bucket.
  - **Resolution:** The section set is tesco.ie's online department list, in a stable display order the developer chooses — a presentation order, not a physical store-walk order (shopping is click-and-collect, so no store walk exists for it to model). "Complete" means every section in that set is present with one unique display-order index, and every staples key maps to a section; keys with no mapping land in F11's terminal bucket. (Decided by the user.)
- [x] Q7: What happens at runtime if a bundled asset fails to load? R1 requires a typed failure distinct from a miss, and the shipped assets are checked by tests, so such a failure means a packaging defect. Should the app fail fast (crash, visible in Android vitals), or should callers receive the failure and render something? F8 and F9 would have to handle the second option.
  - **Resolution:** The loader returns a typed load failure to callers, kept distinct from a lookup miss. The app does not crash; callers fall back to their existing "no data" states, and the failure is logged by category only. (Default accepted by the user.)
- [x] Q8: Should any load start at app launch? A pure first-use load means F2 triggers no load at startup, so R6's Compose test mainly proves nothing blocks first paint. A background pre-warm makes the test prove more but adds startup work. How does the Compose test substitute its blocking asset source into `PantryApplication`'s container?
  - **Resolution:** Datasets load lazily on first use, with no pre-warm at launch. The Compose test injects a blocking asset source through the container's constructor; the production default reads the real assets. (Default accepted by the user.)
- [x] Q9: Should the Compose UI test run under Robolectric (consistent with F1's rule that instrumented runs are reserved for F15's second-device check), with `ui-test-junit4` and `ui-test-manifest` added to the catalog? ARCHITECTURE's Testing row names Compose UI tests, but the libraries are not in the catalog yet.
  - **Resolution:** The Compose UI test runs under Robolectric, consistent with F1's rule that instrumented runs are reserved for F15's second-device check. `ui-test-junit4` and `ui-test-manifest` are added to the version catalog as test-only dependencies. (Default accepted by the user.)
- [x] Q10: Which keys make up the seasonality/substitution seed, and what is the Republic-of-Ireland calendar source? The prior build's `Seasonality.kt` used UK months. Does the v1 slice include substitution data for out-of-season entries, or only months? PLAN says "common Irish fruit, vegetable and herb staples" with no count; the list needs the developer's decision.
  - **Resolution:** The developer-reviewed seed is drafted as about 40 common Irish fruit, vegetable and herb keys with months taken from the Republic-of-Ireland seasonal calendar source the developer names (Republic-of-Ireland months, not the UK months the prior build used), including substitution data for out-of-season entries. The user reviews the data before the feature is accepted; the number is a target, not an acceptance threshold (PLAN Q1). (Plan accepted by the user.)

## Decision Points

- The shape of the absence value: a sealed found/absent result type, or a nullable return type. R2 fixes the behaviour, not the type.
- Whether lookups are `suspend` functions on the store, or a `suspend` load returns an immutable snapshot that pure-Kotlin engines (F3, F8, F9, with no `android.*` imports) query synchronously.
- The seam through which tests inject an asset source (fixture files, a blocking source, an open-recording source) in place of `AssetManager`.
- Whether entry types live in a package free of `android.*` imports, so F3/F8/F9's no-Android checks still pass when they depend on them.
- The mechanism that protects returned collections from downcast-and-mutate (a defensive copy or an unmodifiable wrapper); R7 fixes the behaviour, not the mechanism.
- Whether R7's inspection is backed by a Java-reflection test (no public setters, no `Mutable*` property types) in addition to review.
- Whether display-order indices must be contiguous (0..n-1) or only unique, and whether a key may map to more than one section.
- Whether alias targets may themselves be alias variants (chains), and whether a target must exist in at least one other dataset.
- One asset file per dataset, or the section list and the key-to-section mapping in separate files.
- Whether shipped-content validation (R3–R5) runs at load time too, or only in tests.

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| RK1 | Curated keys do not match what F3's rule produces (for example `tomatoes` curated but `tomato` derived), so real ingredients silently miss and show *unmatched* / *unknown* / terminal-bucket | High | Med | R3's surface-form check now; Q3 fixes the key convention before curation; F3's tests and F17's regressions re-check against real lines |
| RK2 | A lenient parser defaults a missing number to `0`, publishing a false zero-calorie or zero-index entry that looks like real data | Med | High | R1's missing-field criterion against a fixture; strict accessors only (Q1) |
| RK3 | `StaplesEntry` lacks what F9 needs for estimated weight conversions, forcing a format change and a re-curation pass once F9 starts | Med | Med | Q5 resolved before curation starts; the format is JSON, so the change is a data and loader edit, not a migration |
| RK4 | A caller downcasts a returned `List`/`Map` to a mutable type and changes the shared cache, corrupting every other component's reads | Low | High | R7's read-only types plus the collection-protection Decision Point |
| RK5 | The Robolectric Compose test does not reproduce a real device cold start and passes whatever the loader does | Med | Med | Use a source that never completes the read, so the test fails if first paint waits on it; R6's thread-recording test covers the thread separately |
| RK6 | Staples figures are copied from Open Food Facts during curation, breaking ARCHITECTURE Q1's independence premise and bringing ODbL obligations onto a bundled dataset | Low | High | R3's provenance criterion; Never Do bullet; provenance recorded in the repository (Q4) |
| RK7 | The section list is a snapshot of tesco.ie's current online department list, which changes after release, so section names or the developer's chosen display order drift from what tesco.ie shows (no physical walk depends on the order; only presentation is affected) | Med | Low | Plain JSON makes a fix a data edit; F17-style data passes can correct it with no code change |
| RK8 | A dataset JSON object repeats a field name, and `org.json` silently keeps the last value, so a curation typo passes the loader | Low | Low | Not detected by the loader; caught by the shipped-content tests only when the surviving value is invalid; accepted for v1 because the assets are developer-curated and reviewed |

## Success Criteria

- [ ] All four datasets load from JSON assets under `app/src/main/assets/`, and all four are present in the release APK.
- [ ] A missing required field, a duplicate key or an invalid asset fails with a typed load failure that is distinct from a lookup miss.
- [ ] Each dataset returns an explicit absence value on a miss, distinct from a zero-valued, empty or first-position entry, shown by one unit test per dataset.
- [ ] The shipped staples table has between 160 and 180 entries , each fully populated, with keys in canonical surface form and a recorded non-ODbL provenance.
- [ ] Every section in the shipped section-ordering list has exactly one display-order index, no two share one, and every key maps to a listed section.
- [ ] The seasonality seed and the alias seed each load with at least one valid entry and no fixed count target.
- [ ] The developer has reviewed the seasonality seed (keys, months from the developer-named Republic-of-Ireland seasonal calendar, substitutions) and the alias seed, and the review is recorded in `docs/reference-data-provenance.md` (Q10).
- [ ] Container construction opens no asset; the first load runs off the main thread, happens once under concurrent lookups and is never repeated.
- [ ] A Compose UI test shows the placeholder screen on cold start while a dataset read is still blocked.
- [ ] The public surface of `ie.pantry.data.reference` exposes no way to mutate loaded data, confirmed by inspection.
- [ ] `PantryDatabase` stays at version 1 with `app/schemas/` unchanged.
- [ ] The Network Exposure Triage branch-(a) declaration holds: no permission, no HTTP client, no new exported component, no network call at load time.
- [ ] All tests pass.
- [ ] No regressions in existing functionality (F1's full `./gradlew :app:testDebugUnitTest` suite, including `ManifestPolicyTest` and `AppContainerTest`, stays green).

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

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes              |
|------|------------|-------|-------------|-----------|----------|--------|--------------------|
| 1    | 2026-09-25 | 1     | 0           | 20        | 1        | 2      | —                  |
| 2    | 2026-09-25 | 2     | 1           | 17        | 1        | 3      | —                  |
| 3    | 2026-09-25 | 0     | 0           | 0         | 0        | 18     | converged (0 HIGH) |
| 4    | 2026-09-27 | 1     | 0           | 7         | 1        | 0      | upstream-panel 073f32e1 |

### Sealed dispositions

- `[SEAL-01]` **R5 fourth AC near-tautological given R1 and R2** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the AC is the only one that ties a coverage increase to a pure data edit with no schema change, which is PLAN's stated F17 mechanism; redundancy is cheap.
- `[SEAL-02]` **Key-form check admits keys F3 may not derive; mismatch…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - reconciliation is owned by F3's spec and its tests (Q3 resolution), so it is routed to the F3 spec, not this one.
- `[SEAL-03]` **Spec does not say how callers surface a LoadFailure to the…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged - how F8, F9 and F11 render a load failure belongs to those features' own specs; F2 guarantees the failure is distinct from a miss (R1, R2), which is what those specs need.
- `[SEAL-04]` **R1 failed-load-not-retried AC is a Design detail** (pass 2, accepted-as-risk) — Defense: synthesizer-judged - it records the resolved Q7 decision the user accepted (failure kept for the process life), so it is a behaviour requirement, not only a mechanism.
- `[SEAL-05]` **R4 AC5 checks section sets only, not walk order versus the…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged - acknowledged by RK7; the developer's ordering review is recorded in the provenance record and a data edit can correct it.
- `[SEAL-06]` **R1 strict-parse fixtures omit an unknown or wrong-case…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - a value that is not a valid enum name is already a wrong-type or invalid-value failure under R1's strict-parse rule; the exact fixture list is sized in tasks.md.
- `[SEAL-07]` **Provenance retrieval date has no fixed format** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the format is a detail of the provenance test that tasks.md and design.md fix (an ISO date is the obvious choice); the criterion already requires a date per dataset.
- `[SEAL-08]` **Typed load-failure categories are never enumerated** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - R1 already names the failure cases (absent, invalid JSON, missing field, wrong type, duplicate key); the exact type shape is a Design decision in design.md.
- `[SEAL-09]` **No shipped-content check that alias targets or substitution…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - a cross-dataset resolution check depends on Q3's key form and F3's rule, which F3's own tests reconcile (Q3 resolution); the shipped-content tests in tasks.md can add it cheaply.
- `[SEAL-10]` **Q1 mandates throwing accessors but R1 requires explicit…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the two are compatible: Q1 sets the minimum (throwing accessors, never opt*) and R1's more specific criterion governs; design.md reconciles the wording.
- `[SEAL-11]` **Android org.json is lenient, so plain JSON and invalid JSON…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the assets are developer-curated and reviewed, and RK8 already records the parser-leniency class; the fixture list for what the loader rejects is sized in tasks.md.
- `[SEAL-12]` **Decision Point offers a nullable return for absence, which…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the Decision Point is explicitly a Design choice and R1 and R2 fix the behaviour; design.md will reject a return form that cannot carry the typed failure.
- `[SEAL-13]` **R7 AC1 forbids constructor parameters that replace entries…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the injected source is a load-time input, not a way to replace a loaded entry; design.md states the seam so R7's inspection is unambiguous.
- `[SEAL-14]` **R6 blocked-read test cannot fail cleanly as an assertion** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - a bounded wait is a test-construction detail for tasks.md, and the test is already stated to assert the blocked read started.
- `[SEAL-15]` **Store needs kotlinx-coroutines-core at main scope but the…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the dependency arrives transitively through Room's ktx already used in F1, and any catalog addition is covered by the Ask First rule; design.md records the decision.
- `[SEAL-16]` **R6 Compose test AC carries setup mechanics that belong to…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the setup is the seam the pass-2 HIGH required to be named for the test to be satisfiable; design.md may relocate the mechanics but the seam stays specified here.
- `[SEAL-17]` **Provenance test reads a docs file by repo-root-relative…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the AC already notes tests run in app/; design.md fixes the path-resolution mechanism.
- `[SEAL-18]` **Stray space in Success Criteria row 4** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - editorial; no downstream consequence.
- `[SEAL-19]` **Loader behaviour on a valid but empty array is unstated** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - an empty fixture is a fixture-design detail for tasks.md; R1's count-equals-file criterion already implies an empty file loads zero entries.
- `[SEAL-20]` **Commands release-manifest greps depend on a fresh…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the Commands block already runs assembleRelease first; a stale manifest is caught by the fresh-clone check at the closeout task in tasks.md.
- `[SEAL-21]` **R1 AC1 says assets holds a file per dataset while the…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the Project Structure section is explicit about the path; editorial.
- `[SEAL-22]` **Project Structure labels the repository root pantry/** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - cosmetic; the label is a name for the root, not a path.
- `[SEAL-23]` **Acceptance-criteria count is at the upper edge of…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the panel has already trimmed and merged redundant criteria across three passes and each remaining one traces to PLAN or a resolved question.

### Deferred dispositions

- `[DEF-01]` **Curation effort unsized and unsequenced in one feature** → design.md (pass 1) — Routed because: phasing the loader and small real seed ahead of full curation is a Design and Tasks sequencing choice, not a requirement; the requirements and acceptance criteria stay as written.
- `[DEF-02]` **R1 strict-parse AC bundles seven fixture failure modes;…** → tasks.md (pass 2) — Routed because: splitting the bundled acceptance criterion into a fixture matrix is a task-breakdown and sizing choice; the criterion itself stays whole here.
- `[DEF-03]` **tasks.md's T19 description and Q5's developer-input framing…** → tasks.md (pass 4) — Routed because: T19's own task description and its developer-input ask are a Tasks-phase edit, to be corrected when T19 is picked up, before the developer is asked for section data.

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `ab2ce3a2a5d90764`
- **Hash basis:** v2