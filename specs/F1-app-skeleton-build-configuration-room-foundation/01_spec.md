# Feature: App Skeleton, Build Configuration & Room Foundation

**PLAN feature identifier:** `F1`

## Objective

**From PLAN F1:** `blueprint/03_PLAN.md` → Feature Breakdown → `### F1: App Skeleton, Build Configuration & Room Foundation` (Description and Acceptance Criteria).

F1 is the only feature that writes the database schema for entities it does not itself read or display — every table it creates is consumed by a feature built later (F5–F12, F16), so a shape defect here surfaces as a migration on a distributed user's device rather than as a failing test in the feature that noticed it.

## Requirements

### R1: Reproducible single-module build

As the developer, I want a single-module Gradle Kotlin DSL build with a version catalog and pinned platform levels, so that a clean checkout produces the same debug and release APKs on any machine without hand-configured state.

**Acceptance Criteria:**

- GIVEN a clean checkout of the repository with no `build/` or `.gradle/` directories present
  WHEN `./gradlew assembleDebug assembleRelease` is run
  THEN both tasks succeed and a debug APK and a release APK are produced.

- GIVEN the app module's build script
  WHEN its configuration is inspected
  THEN `minSdk` is 26, `compileSdk` is 35, `targetSdk` is 35, and the Kotlin/Java toolchain is JDK 17.

- GIVEN every third-party dependency the app declares (Compose BOM, Material 3, Room, AndroidX, the test libraries)
  WHEN the app module's build script is inspected
  THEN each dependency is referenced through an alias defined in `gradle/libs.versions.toml`, and no dependency coordinate carries a literal version string in a `build.gradle.kts` file.

- GIVEN the release build type
  WHEN its configuration is inspected
  THEN `minifyEnabled` is `false` and no shrinker/obfuscation configuration is applied.

- GIVEN the merged release manifest
  WHEN it is inspected
  THEN the `<application>` element declares `android:allowBackup="false"`, and the manifest declares no `android.permission.INTERNET` (or any other network) permission.

### R2: Compose app shell and app-scoped dependency container

As the developer, I want a launchable Compose/Material 3 shell wired through one manually constructed app-scoped container, so that later features obtain their database and DAOs from a single well-known place instead of constructing their own.

**Acceptance Criteria:**

- GIVEN the built debug APK installed on a device or emulator
  WHEN the launcher icon is opened
  THEN a Compose screen themed with Material 3 renders without crashing, and no user-facing catalogue, import, list or walkthrough behaviour is present. F1 ships a minimal placeholder screen purely as a launch/install target (Q2, resolved).

- GIVEN the running application
  WHEN the app-scoped container is obtained twice from the `Application` instance
  THEN both calls return the same container, and the container exposes exactly one `RoomDatabase` instance and one instance of each DAO.

- GIVEN the app-scoped container
  WHEN its construction code is inspected
  THEN every dependency is supplied by constructor injection written by hand, with no annotation-processing or reflection-based DI framework on the classpath.

- GIVEN the app-scoped container
  WHEN an instrumentation-free JVM/Robolectric test constructs it against an in-memory database
  THEN construction succeeds without requiring a real device, an application context beyond Robolectric's, or any network access.

### R3: Room schema covering every planned entity, with an exported schema

As the developer, I want every entity the project will need defined in Room at schema version 1 with its schema JSON committed, so that later features add rows rather than tables and every schema change from here on is a reviewable diff.

**Acceptance Criteria:**

- GIVEN the Room database class
  WHEN its `entities` list is inspected
  THEN it contains exactly `Recipe`, `RecipeIngredient`, `ShoppingListSelectionEntry`, `ShoppingList`, `ShoppingListItem`, `RetailerAssistSession` and `NutritionCacheEntry`, and its `version` is 1.

- GIVEN the entity definitions
  WHEN the child entities are inspected
  THEN `RecipeIngredient` is a child of `Recipe`, `ShoppingListItem` is a child of `ShoppingList`, and each child declares a foreign key to its parent with an index on the foreign-key column.

- GIVEN a field that ARCHITECTURE's Data Models table marks optional (a `Recipe`'s cooking time, its yield, its thumbnail reference, its `sourceUrl`/`fetchedAt`, a `ShoppingListItem`'s quantity)
  WHEN its column definition is inspected
  THEN the column is nullable and carries no non-null default, so an absent value is stored as absence rather than as a zero or an empty string.

- GIVEN the Room schema-export directory is configured on the app module
  WHEN `./gradlew assembleDebug` is run and `git status` is inspected
  THEN `app/schemas/<database-class>/1.json` exists, is tracked in version control, and is unmodified by the build.

- GIVEN the seven entities
  WHEN the DAO surface is inspected
  THEN each entity is reachable through at least one DAO method, and no DAO exposes a method containing merging, scaling, nutrition-aggregation or seasonality logic.

### R4: Reactive `Flow` reads that emit on change

As the developer, I want every read a screen will observe exposed as a `Flow`, so that later UI features see an edit propagate without writing manual invalidation.

**Acceptance Criteria:**

- GIVEN the DAOs for recipes, shopping-list selection entries, shopping lists and the retailer-assist pointer
  WHEN their read methods are inspected
  THEN each observable read returns `Flow<...>` rather than a one-shot value.

- GIVEN a Robolectric DAO test collecting a recipe `Flow` from an in-memory database seeded with one recipe
  WHEN that recipe row is updated
  THEN the `Flow` emits a second value carrying the updated row, without the collector re-querying.

- GIVEN a Robolectric DAO test collecting the selection-entry, shopping-list and retailer-assist-pointer `Flow`s
  WHEN a row backing each is inserted, updated and deleted
  THEN each `Flow` emits an updated value after every one of those writes.

- GIVEN a `Flow` read whose query matches no rows
  WHEN it is collected
  THEN it emits an empty result rather than failing or never emitting.

### R5: `Recipe.updatedAt` bumped by the persistence layer on any write

As the developer, I want `updatedAt` maintained inside the persistence layer rather than by each caller, so that the invalidation key ARCHITECTURE's R8 cache contingency depends on cannot be silently skipped by a later feature.

**Acceptance Criteria:**

- GIVEN a stored `Recipe` with a known `updatedAt`
  WHEN any field of that `Recipe` row is written through the persistence layer
  THEN `updatedAt` is advanced to the write's timestamp, asserted by a DAO test with an injected/controllable time source.

- GIVEN a stored `Recipe` with one or more `RecipeIngredient` children and a known `updatedAt`
  WHEN a child `RecipeIngredient` row is inserted or updated
  THEN the parent `Recipe`'s `updatedAt` is advanced, asserted by a DAO test.

- GIVEN a stored `Recipe` with a known `updatedAt` and at least one `RecipeIngredient` child
  WHEN that child `RecipeIngredient` row is deleted
  THEN the parent `Recipe`'s `updatedAt` is advanced, asserted by a DAO test.

- GIVEN a caller that writes a `Recipe` or a `RecipeIngredient`
  WHEN it supplies no `updatedAt` value of its own
  THEN the bump still occurs, so no write path can bypass it.

### R6: Migration harness and a written migration path

As the developer, I want a Robolectric migration-test harness in place at schema version 1, so that the first real migration is exercised by an existing, already-green test rather than by one written under release pressure.

**Acceptance Criteria:**

- GIVEN the app module's test configuration
  WHEN the test sources are inspected
  THEN a Room `MigrationTestHelper`-based harness exists that opens a database from a committed schema JSON, populates it from a fixture, runs the declared migrations, and reads the fixture rows back.

- GIVEN schema version 1, where no prior version exists to round-trip from
  WHEN the migration harness test is run
  THEN it passes vacuously — asserting the harness resolves against the committed `1.json` and reports that no migration is pending — and does not silently skip.

- GIVEN the harness
  WHEN its assertions are inspected
  THEN it asserts on the fixture's row contents after migration, not merely that the migration resolved or that the schema opened.

- GIVEN the database builder
  WHEN its configuration is inspected
  THEN `fallbackToDestructiveMigration` (in any form) is not called, so a missing migration fails loudly instead of wiping user data.

- GIVEN the repository
  WHEN documentation is inspected
  THEN a written migration procedure exists stating that every schema change bumps the version, commits the new exported schema JSON, adds an explicit `Migration`, and extends the harness fixture.

### R7: Offline durability across process restart

As the developer, I want the database proven readable with no network and after a process restart, so that G6's local-first promise rests on a test rather than on the absence of network code.

**Acceptance Criteria:**

- GIVEN a database populated with at least one row in each of the seven entities and then closed
  WHEN it is reopened from the same on-disk file in a fresh database instance (a simulated process restart)
  THEN every row is read back with its field values unchanged, asserted by a Robolectric test — consistent with this spec's own Boundaries, which reserve instrumented runs for the second-device check F15 owns.

- GIVEN the same test
  WHEN it executes
  THEN it completes with no network access available, and the code under test makes no network call of any kind.

- GIVEN a row whose optional field was stored as absent
  WHEN it is read back after the simulated restart
  THEN the field is still absent rather than materialised as a zero, an empty string or a default.

## Project Structure

```
pantry/                                   (repository root)
├── settings.gradle.kts                   Single :app module; plugin + dependency repositories
├── build.gradle.kts                      Root build script; plugin aliases declared, not applied
├── gradle/
│   ├── libs.versions.toml                Version catalog — the single place any version is pinned
│   └── wrapper/gradle-wrapper.properties Pinned Gradle distribution
├── gradlew, gradlew.bat                  Gradle wrapper
└── app/
    ├── build.gradle.kts                  minSdk 26 / compileSdk 35 / JDK 17, Compose, Room, release build type
    ├── schemas/<database-class>/1.json   Exported Room schema — committed to version control
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml       android:allowBackup="false"; no network permission
        │   └── java/ie/pantry/           (Android convention: Kotlin sources under src/main/java)
        │       ├── PantryApplication.kt
        │       ├── di/AppContainer.kt
        │       ├── data/db/              Database class, entities, DAOs
        │       └── ui/                   Compose theme + placeholder launcher screen
        └── test/
            └── java/ie/pantry/           Robolectric DAO, migration and restart tests
```

The package namespace and `applicationId` are `ie.pantry` (Q1, resolved). `<database-class>` stands for the Room database class name, fixed at Design.

### New Files

- `settings.gradle.kts` — declares the single `:app` module and the repositories.
- `build.gradle.kts` — root build script, plugin aliases from the catalog.
- `gradle/libs.versions.toml` — version catalog for every plugin and dependency.
- `app/build.gradle.kts` — platform levels, JDK 17 toolchain, Compose/Material 3, Room with the schema-export directory, release build type with `minifyEnabled false`.
- `app/src/main/AndroidManifest.xml` — application element with `android:allowBackup="false"`, the launcher activity, no network permission.
- `app/src/main/java/ie/pantry/PantryApplication.kt` — `Application` subclass owning the app-scoped container.
- `app/src/main/java/ie/pantry/di/AppContainer.kt` — manual constructor-injection container exposing the database and DAOs.
- `app/src/main/java/ie/pantry/data/db/` — the Room database class, the seven entities, their DAOs, and the `updatedAt` bump-on-write path.
- `app/src/main/java/ie/pantry/ui/` — Material 3 theme and the placeholder launcher screen.
- `app/schemas/<database-class>/1.json` — exported schema, committed.
- `app/src/test/java/ie/pantry/data/db/` — Robolectric DAO tests (`Flow` emission, `updatedAt` bump), the migration harness test, and the restart/offline durability test.
- `docs/migrations.md` — the written migration procedure R6 requires.

### Modified Files

- `.gitignore` — already ignores `build/`, `.gradle/`, `local.properties` and `.idea/`; confirm it does **not** ignore `app/schemas/`, so the exported schema JSON is tracked.

No other file in the repository is modified: apart from `blueprint/`, `.sdd/`, `CLAUDE.md` and the vendored `telescoping-sdd/` plugin, the working tree contains no application source.

## Commands

```bash
# Build both variants from a clean checkout
./gradlew assembleDebug assembleRelease

# Run the JVM/Robolectric test suite (DAO, migration, restart)
./gradlew testDebugUnitTest

# Static analysis — Android Lint, bundled with AGP (no new dependency)
./gradlew lintDebug

# Install the debug build on a connected device/emulator
./gradlew installDebug
```

## Boundaries

### Always Do

- Declare every dependency and plugin version in `gradle/libs.versions.toml` and reference it by alias (ARCHITECTURE — Technology Choices, Build).
- Keep the app a single `:app` Gradle module.
- Commit the exported Room schema JSON with every schema version.
- Represent an absent value as a nullable column with no default, per PLAN's CFC-2 invariant as it applies to storage.
- Keep business rules out of the persistence layer — DAOs read and write rows; merging, scaling, nutrition and seasonality belong to C4, C5, C6 and C10 (ARCHITECTURE C3 Boundary).
- Bump `Recipe.updatedAt` inside the persistence layer on every write to a `Recipe` or a child `RecipeIngredient`, deletion included.
- Write tests that run on the JVM under Robolectric; reserve instrumented runs for the second-device check F15 owns.

### Ask First

- Adding any dependency not named in ARCHITECTURE's Technology Choices table — in particular an annotation-processing DI framework, a linter/formatter (ktlint, detekt) or a CI action.
- Changing `minSdk`, `compileSdk`, `targetSdk` or the JDK toolchain level away from the values SCOPE pins as Hard constraints.
- Enabling R8/ProGuard shrinking, which ARCHITECTURE explicitly declines for vitals readability.
- Adding a field to an entity that ARCHITECTURE's Data Models table does not list, or omitting one it does.
- Splitting the build into more than one Gradle module.

### Never Do

- Never call `fallbackToDestructiveMigration` in any form — a wipe on upgrade is the one permanently unrecoverable defect for a local-only app (ARCHITECTURE R9).
- Never set `android:allowBackup` to `true` or rely on `dataExtractionRules.xml` alone; the attribute must cover `minSdk` 26 through 35.
- Never add a network permission, an HTTP client, a socket, a listener or any network call in this feature — the app's sole HTTP call site is C7, built in F4.
- Never edit `app/schemas/*.json` by hand; it is generated by the Room compiler and committed as generated.
- Never build user-facing catalogue, import, list or walkthrough behaviour here — F1 ships substrate only.
- Never embed a recipe title, ingredient line or URL in an exception message constructed in the persistence layer (ARCHITECTURE Data Flow, third invariant). F1 is not a CFC-4 participating feature, so this is a local boundary rather than a tagged contract obligation.

### Network Exposure Triage

**Branch (a) — no new surface.** Introduces no new domain/route/port; the surfaces it touches already existed and are unchanged — checked: (1) the Android manifest declares no `android.permission.INTERNET` or other network permission; (2) no HTTP client library (OkHttp or otherwise) is added to the version catalog or the app module's dependencies; (3) no socket, `ServerSocket`, listener, bound port or content provider/exported component is created — the manifest's only exported component is the launcher activity, which serves no remote input; (4) no DNS record, hostname or public endpoint is registered or routed; (5) the Room database is a device-local file with no remote replica, and `android:allowBackup="false"` removes even the OS's own off-device conveyance path. The app's single HTTP call site (C7 — External Data Gateway) is introduced by F4, not here.

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: What `applicationId` and root package namespace should the app use? `ie.pantry` is a placeholder in this spec; the value is effectively permanent once published to Play (F15), so it needs the developer's decision before the module is generated.
  - **Resolution:** Resolved — `ie.pantry`. Matches the project's Republic-of-Ireland locale and Tesco-Ireland retailer scoping already established in SCOPE and ARCHITECTURE; a clean, short reverse-domain-style id with no real domain dependency. Every placeholder use of `ie.pantry` in Project Structure and New Files is now the final value, not a placeholder.
- [x] Q2: Does F1 ship a minimal placeholder Compose launcher screen (assumed in R2), or should the app launch to a bare themed empty surface with no content at all? A launchable activity is needed either way to verify `installDebug`; the question is only how much placeholder content is acceptable in a feature that ships no user-facing behaviour.
  - **Resolution:** Resolved — a minimal placeholder screen (R2's existing assumption). A themed Material 3 screen with no catalogue/import/list content, just enough to prove the app launches and installs cleanly.

## Decision Points

- Whether `Recipe.updatedAt` is bumped by DAO-level `@Transaction` methods, a repository wrapper, or a Room `@Update`/trigger mechanism — R5 fixes the behaviour, not the mechanism.
- How the time source behind `updatedAt` is injected so tests can control it (a `Clock` on the container, a DAO parameter, or a functional seam).
- Whether the ARCHITECTURE-named JUnit 5 test stack runs Robolectric DAO tests directly or via the JUnit vintage engine, since Robolectric's runner is JUnit 4-based.
- Whether the nutrition cache's auto-backup exclusion is expressed solely by `android:allowBackup="false"` or additionally by `dataExtractionRules.xml` for API 31+ defence in depth.
- The concrete column types and primary-key strategy for each entity (auto-generated integer ids vs. UUID strings).
- Where the app-scoped container hangs off the `Application` and how a Composable or ViewModel obtains it.

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| RK1 | Entities are designed here for features built much later (F10–F12, F16), so a shape defect only surfaces once a consumer exists — by then it costs a migration rather than an edit | High | Med | Define each entity strictly from ARCHITECTURE's Data Models table rather than from guessed needs; accept that some later change is a migration and make migrations cheap via R6's harness, which exists precisely to absorb this |
| RK2 | The migration harness passes vacuously at version 1 and is never exercised for real, so the first genuine migration is its first real run (ARCHITECTURE R9) | Med | High | R6 requires the harness to assert on fixture row contents, not merely that a migration resolved, and to fail loudly rather than skip; F15's pre-submission criterion re-runs it before every release |
| RK3 | The exported schema JSON is generated into an ignored or untracked path and never committed, silently disabling migration testing | Med | High | R3's acceptance criterion checks the file is tracked after a clean build, and the Modified Files note requires confirming `.gitignore` does not cover `app/schemas/` |
| RK4 | AGP, Kotlin, Compose and Room version combinations are mutually constrained; an arbitrary pick fails to build or fails only at runtime | Med | Med | Pin every version in one catalog file and prove the combination with `assembleDebug assembleRelease` from a clean checkout (R1) before any feature code is written |
| RK5 | With no CI (SCOPE Constraints — Team), the F1 test suite is only run when the developer remembers to run it, so a regression here is invisible until a later feature trips over it | Med | Med | Keep the whole suite JVM/Robolectric so `./gradlew testDebugUnitTest` is seconds, not a device run; F15's release gate re-runs the migration suite explicitly |

## Success Criteria

- [ ] `./gradlew assembleDebug assembleRelease` succeeds from a clean checkout and produces both APKs.
- [ ] `minSdk` 26, `compileSdk` 35, `targetSdk` 35 and the JDK 17 toolchain are configured, and every dependency version lives only in `gradle/libs.versions.toml`.
- [ ] The release build sets `minifyEnabled false` and the merged release manifest declares `android:allowBackup="false"`.
- [ ] The debug APK installs and launches into a Material 3 Compose screen without crashing.
- [ ] One app-scoped container, built by hand-written constructor injection, supplies a single database instance and a single instance of each DAO.
- [ ] All seven entities exist at schema version 1 and `app/schemas/<database-class>/1.json` is committed and unmodified by a build.
- [ ] Recipe, selection-entry, shopping-list and retailer-assist-pointer reads are `Flow`s that emit on the underlying row changing, proven by Robolectric DAO tests.
- [ ] `Recipe.updatedAt` advances on a `Recipe` write, a child `RecipeIngredient` insert or update, and a child `RecipeIngredient` deletion, proven by DAO tests.
- [ ] The Robolectric migration harness exists, asserts on fixture row contents, and passes at schema version 1; `fallbackToDestructiveMigration` appears nowhere.
- [ ] A test proves the database is readable after a simulated process restart with no network available, and that an absent optional value is still absent afterwards.
- [ ] The Network Exposure Triage branch-(a) declaration holds: no network permission, no HTTP client, no listener.
- [ ] All tests pass.
- [ ] No regressions in existing functionality (F1 is the first feature; the repository contains no prior application code to regress).

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

| Pass | Date | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes |
|------|------|-------|-------------|-----------|----------|--------|-------|

### Sealed dispositions

### Deferred dispositions

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [ ] Approved to proceed to next phase
- **Content Hash:** `pending`
