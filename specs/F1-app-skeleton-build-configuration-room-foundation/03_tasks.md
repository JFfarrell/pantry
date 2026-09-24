# Tasks: App Skeleton, Build Configuration & Room Foundation

**Spec:** `specs/F1-app-skeleton-build-configuration-room-foundation/01_spec.md`
**Design:** `specs/F1-app-skeleton-build-configuration-room-foundation/02_design.md`

## Summary

| Task | Description | Requirement | Dependencies | Parallel | Status |
|------|-------------|-------------|--------------|----------|--------|
| T1 | Gradle wrapper, settings, root build script and version catalog | R1 | None | No | Done |
| T2 | App module build script, manifest, resources and README | R1 | T1 | No | Done |
| T3 | Bare `PantryApplication`, Robolectric config and `ManifestPolicyTest` | R1 | T2 | Yes (with T5, T18) | Done |
| T4 | Material 3 theme, `MainActivity`, placeholder screen and first full assemble | R1, R2 | T2, T3 | Yes (with T5, T18) | Done |
| T5 | Enums, `SelectionSnapshot` and `Converters` with `ConvertersTest` | R3 | T2 | Yes (with T3, T4, T18) | Done |
| T6 | Recipe-side entities and relations | R3 | T5 | Yes (with T7, T17, T18) | Done |
| T7 | List-side and nutrition-cache entities and relation | R3 | T5 | Yes (with T6, T17, T18) | Done |
| T8 | `PantryDatabase`, `ALL_MIGRATIONS`, committed `1.json`, `SchemaShapeTest` | R3, R6 | T3, T6, T7 | Yes (with T17, T18) | Done |
| T9 | Migration harness skeleton with `FixtureV1` | R6 | T8 | Yes (with T10, T11, T15, T17, T18, T25) | Not Started |
| T10 | `RecipeDao` reads and `updatedAt`-bumping writes | R4, R5 | T8 | Yes (with T9, T15, T17, T18, T25) | Not Started |
| T11 | `RecipeDao` soft-delete filter, sweep and repository-only ops | R3, R4, R5 | T10 | Yes (with T9, T15, T17, T18, T25) | Not Started |
| T12 | `SelectionEntryDao` with `Flow` reads | R4 | T11 | Yes (with T9, T15, T17, T18, T25) | Not Started |
| T13 | `ShoppingListDao` and `RetailerAssistDao` with `Flow` reads | R4 | T10, T12 | Yes (with T9, T15, T17, T18, T25) | Not Started |
| T14 | `NutritionCacheDao` and DAO-surface review | R3 | T13 | Yes (with T16, T17, T18, T19, T25) | Not Started |
| T15 | `PersistenceException`, `Sentinels` and error-hygiene test foundation [CFC-4] | R3 | T8 | Yes (with T9, T10, T11, T12, T13, T17, T18, T25) | Not Started |
| T16 | Error-hygiene cases for DAO constraint failures [CFC-4] | R3 | T10, T13, T15 | Yes (with T14, T17, T18, T19, T25) | Not Started |
| T17 | `ThumbnailProcessor` bounds-aware decode/downsample/re-encode | R8 | T3 | Yes (with T5–T16, T18) | Not Started |
| T18 | `ThumbnailStore` and `ThumbnailOutcome` | R8 | T2 | Yes (with T3–T17) | Done |
| T19 | `RecipeRepository.saveNewRecipe` with thumbnail path | R8 | T11, T15, T17, T18 | Yes (with T14, T16, T25) | Not Started |
| T20 | `RecipeRepository` replace/remove thumbnail and hard delete | R8 | T19 | Yes (with T21, T22, T23, T24, T25) | Not Started |
| T21 | Error-hygiene cases for repository and thumbnail failures [CFC-4] | R3 | T16, T19 | Yes (with T20, T22, T23, T24, T25) | Not Started |
| T22 | `AppContainer` and `PantryApplication.container` wiring | R2 | T14, T19 | Yes (with T20, T21, T23, T24, T25) | Not Started |
| T23 | `RestartDurabilityTest` (restart + no network) | R7 | T14 | Yes (with T19, T20, T21, T22, T24, T25) | Not Started |
| T24 | Extend migration harness to read fixture back through DAOs | R6 | T9, T14 | Yes (with T19, T20, T21, T22, T23, T25) | Not Started |
| T25 | Write `docs/migrations.md` | R6 | T8 | Yes (with T9–T24) | Done |
| T26 | Feature closeout: full suite, lint, clean build and manual checks | R1, R2, R3, R6 | T1–T25 | No | Not Started |

## Phase 1: Build and App Shell (FC1, FC2)

### - [x] T1: Gradle wrapper, settings, root build script and version catalog

- **Requirement:** R1
- **Description:** Create the Gradle wrapper, `settings.gradle.kts` (single `:app`, `FAIL_ON_PROJECT_REPOS`, `rootProject.name = "pantry"`), `gradle.properties`, root `build.gradle.kts` (plugin aliases `apply false`) and `gradle/libs.versions.toml` holding every plugin and library version listed in design § Dependencies.
- **Files:**
  - Read: `specs/F1-app-skeleton-build-configuration-room-foundation/02_design.md` — FC1 key contents and the § Dependencies candidate version set
  - Create: `settings.gradle.kts`
  - Create: `build.gradle.kts`
  - Create: `gradle.properties`
  - Create: `gradle/libs.versions.toml`
  - Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar` — generated as one unit by `gradle wrapper --gradle-version 8.11.1` **[ASSUMPTION: a local Gradle install or an existing wrapper is available to generate them]**
- **Dependencies:** None
- **Parallel:** No — every later task builds on it
- **Acceptance Criteria:**
  - GIVEN the root build files
    WHEN any dependency or plugin coordinate is declared
    THEN its version lives only in `gradle/libs.versions.toml`, and no `.gradle.kts` file carries a literal version string (R1 AC3)
- **Tests:** Not applicable — build configuration only; see Verification.
- **Verification:** `./gradlew help` succeeds (a warning that `:app` has no project directory yet is tolerated until T2 creates it), and `grep -nE '"[^"]+:[^"]+:[0-9][^"]*"' build.gradle.kts settings.gradle.kts` prints nothing.

### - [x] T2: App module build script, manifest, resources and README

- **Requirement:** R1
- **Description:** Create `app/build.gradle.kts` per FC1 (namespace/applicationId `ie.pantry`, minSdk 26, compile/targetSdk 35, JDK 17, Compose, Room plugin `schemaDirectory`, test-assets `schemas` srcDir, `isIncludeAndroidResources`, test `maxHeapSize = "1g"`, release `isMinifyEnabled = false`/`isShrinkResources = false`, all deps via `libs.*`), the FC2 manifest and resources, and the root `README.md` stating the JDK 17 prerequisite (AD15).
- **Files:**
  - Read: `gradle/libs.versions.toml` — alias names to reference
  - Create: `app/build.gradle.kts`
  - Create: `app/src/main/AndroidManifest.xml`
  - Create: `app/src/main/res/values/strings.xml`
  - Create: `app/src/main/res/values/themes.xml`
  - Create: `README.md`
- **Dependencies:** T1
- **Parallel:** No — T3, T4, T5 and T18 all need the app module
- **Acceptance Criteria:**
  - GIVEN a checkout with no `build/` or `.gradle/` directories
    WHEN `./gradlew :app:processDebugResources` is run
    THEN it succeeds, so the plugin and library versions in the catalog resolve and the manifest and resources merge (the full assemble check waits for T4, because the manifest names `.PantryApplication` (T3) and `.ui.MainActivity` (T4))
  - GIVEN `app/build.gradle.kts`
    WHEN it is inspected
    THEN `minSdk` is 26, `compileSdk` and `targetSdk` are 35, the toolchain is JDK 17, and release has `isMinifyEnabled = false` with no `proguardFiles` (R1 AC2, AC4)
  - GIVEN `app/src/main/AndroidManifest.xml`
    WHEN it is inspected
    THEN `<application>` has `android:allowBackup="false"` and there is no `<uses-permission>` element (R1 AC5 source; Network Exposure Triage branch (a))
- **Tests:** Not applicable — build configuration and resources only; the manifest policy is asserted by T3's test.
- **Verification:** `./gradlew :app:processDebugResources` succeeds; `for p in 'minSdk = 26' 'compileSdk = 35' 'targetSdk = 35' 'jvmToolchain(17)' 'isMinifyEnabled = false'; do grep -qF "$p" app/build.gradle.kts || echo "missing: $p"; done` prints nothing; `grep -nE '"[^"]+:[^"]+:[0-9][^"]*"' app/build.gradle.kts` prints nothing; `grep -n 'uses-permission' app/src/main/AndroidManifest.xml` prints nothing.

### - [x] T3: Bare `PantryApplication`, Robolectric config and `ManifestPolicyTest`

- **Requirement:** R1
- **Description:** Create `PantryApplication` as a plain `Application` subclass (the `container` property is added in T22), pin Robolectric to `sdk=35` (use the `sdk=34` fallback from design Testing Strategy if Robolectric's SDK 35 needs a newer test JVM than the spec's JDK 17), and add `ManifestPolicyTest` asserting the debug-merged manifest's backup and permission policy.
- **Files:**
  - Read: `app/src/main/AndroidManifest.xml` — `android:name=".PantryApplication"` reference
  - Create: `app/src/main/java/ie/pantry/PantryApplication.kt`
  - Create: `app/src/test/resources/robolectric.properties`
  - Create: `app/src/test/java/ie/pantry/ManifestPolicyTest.kt`
- **Dependencies:** T2
- **Parallel:** Yes (with T5, T18) — disjoint files; T4 follows it
- **Acceptance Criteria:**
  - GIVEN the debug-merged manifest loaded under Robolectric
    WHEN `ApplicationInfo.flags` and `PackageInfo.requestedPermissions` are read
    THEN `FLAG_ALLOW_BACKUP` is not set and `requestedPermissions` contains no `android.permission.INTERNET` and no other `android.permission.*` entry; the androidx.core self-signature permission `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that the manifest merger adds is tolerated (R1 AC5, debug variant)
- **Tests:**
  - `` `debug manifest disallows backup`() `` — `FLAG_ALLOW_BACKUP` absent
  - `` `debug manifest requests no android permissions`() `` — no `android.permission.INTERNET` or any other `android.permission.*` entry (androidx.core's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is allowed)
  - File: `app/src/test/java/ie/pantry/ManifestPolicyTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ManifestPolicyTest"`

### - [x] T4: Material 3 theme, `MainActivity`, placeholder screen and first full assemble

- **Requirement:** R1, R2
- **Description:** Create `PantryTheme` (light/dark scheme, dynamic colour on API 31+), `MainActivity` calling `setContent { PantryTheme { PlaceholderScreen() } }`, and `PlaceholderScreen` (a `Scaffold` with the app-name `Text` only).
- **Files:**
  - Read: `app/src/main/res/values/strings.xml` — `app_name`
  - Create: `app/src/main/java/ie/pantry/ui/theme/Theme.kt`
  - Create: `app/src/main/java/ie/pantry/ui/MainActivity.kt`
  - Create: `app/src/main/java/ie/pantry/ui/PlaceholderScreen.kt`
- **Dependencies:** T2, T3
- **Parallel:** Yes (with T5, T18) — disjoint files; needs `PantryApplication` (T3) so the manifest's class references resolve
- **Acceptance Criteria:**
  - GIVEN a checkout with no `build/` or `.gradle/` directories
    WHEN `./gradlew assembleDebug assembleRelease` is run
    THEN both tasks succeed and a debug and a release APK are produced (R1 AC1; retires DR1, re-pinning versions in the catalog if the candidate set does not combine)
  - GIVEN the debug APK installed on a device or emulator
    WHEN the launcher icon is opened
    THEN a Material 3 Compose screen showing only the app name renders without crashing, with no catalogue, import, list or walkthrough behaviour (R2 AC1)
- **Tests:** none — spec R2 AC1 designates this a manual one-time launch check, not an automated test (spec Boundaries reserve instrumented runs for F15; SEAL-04 accepted this posture)
- **Verification:** `./gradlew assembleDebug assembleRelease` succeeds; with a device or emulator attached (a manual-check prerequisite Q2 does not cover), `adb logcat -c`, then `./gradlew installDebug`, launch Pantry from the launcher, confirm the themed screen shows only the app name, and confirm `adb logcat -d | grep -c 'FATAL EXCEPTION'` prints `0`.

## Phase 2: Schema and Migration Harness (FC4, FC9)

### - [x] T5: Enums, `SelectionSnapshot` and `Converters` with `ConvertersTest`

- **Requirement:** R3
- **Description:** Create `QuantityDimension`, `NutritionBasis`, `SelectionSnapshot(recipeId: Long, servings: Int?)` and `Converters` (`Instant?`⇄`Long?`, both enums by `name`, `List<Long>`⇄comma-joined `String`, `List<SelectionSnapshot>`⇄`recipeId:servings|…`) per FC4.
- **Files:**
  - Create: `app/src/main/java/ie/pantry/data/db/entity/QuantityDimension.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/entity/NutritionBasis.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/entity/SelectionSnapshot.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/Converters.kt`
  - Create: `app/src/test/java/ie/pantry/data/db/ConvertersTest.kt`
- **Dependencies:** T2
- **Parallel:** Yes (with T3, T4, T18) — disjoint files; plain JUnit, no Robolectric config needed
- **Acceptance Criteria:**
  - GIVEN a value of each converted type, including null, empty lists and a snapshot with null servings
    WHEN it is converted to its column form and back
    THEN the original value is returned, and an absent value stays absent rather than becoming a zero or empty default (R3 AC3 storage side)
- **Tests:**
  - `` `instant round-trips through epoch millis`() ``
  - `` `null instant round-trips as null`() ``
  - `` `quantity dimension round-trips by name`() ``
  - `` `nutrition basis round-trips by name for both bases`() ``
  - `` `empty long list encodes as empty string and decodes to empty list`() ``
  - `` `selection snapshots round-trip including null servings`() ``
  - `` `empty snapshot list round-trips`() ``
  - File: `app/src/test/java/ie/pantry/data/db/ConvertersTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ConvertersTest"`

### - [x] T6: Recipe-side entities and relations

- **Requirement:** R3
- **Description:** Create `Recipe`, `RecipeIngredient` (FK → `recipe.id` CASCADE, indexed, with `position`), `ShoppingListSelectionEntry` (PK = FK `recipeId`, CASCADE), `RecipeWithIngredients` (with computed `orderedIngredients`) and `SelectionEntryWithRecipe` (`@Embedded(prefix = "recipe_")`) exactly per design Data Models DM1–DM3, every optional field `Type?` with no `@ColumnInfo(defaultValue)`.
- **Files:**
  - Read: `specs/F1-app-skeleton-build-configuration-room-foundation/02_design.md` — Data Models DM1–DM3, AD7, Relationships
  - Create: `app/src/main/java/ie/pantry/data/db/entity/Recipe.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/entity/RecipeIngredient.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/entity/ShoppingListSelectionEntry.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/relation/RecipeWithIngredients.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/relation/SelectionEntryWithRecipe.kt`
- **Dependencies:** T5
- **Parallel:** Yes (with T7, T17, T18) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the `Recipe` and `RecipeIngredient` entity classes
    WHEN their fields are inspected
    THEN `yieldServings`, `cookingTimeMinutes`, `thumbnailPath`, `sourceUrl`, `fetchedAt`, `pendingDeletionAt`, `quantityAmount` and `quantityUnit` are nullable with no default annotation, and `RecipeIngredient` declares an indexed FK to `Recipe` (R3 AC2–AC4)
- **Tests:** Entity shape is asserted against the generated schema by T8's schema-shape tests; ingredient ordering by position is asserted by T10's DAO tests.
- **Verification:** `./gradlew :app:compileDebugKotlin`

### - [x] T7: List-side and nutrition-cache entities and relation

- **Requirement:** R3
- **Description:** Create `ShoppingList`, `ShoppingListItem` (FK → `shopping_list.id` CASCADE, indexed), `RetailerAssistSession` (PK = FK `shoppingListId`, CASCADE), `NutritionCacheEntry` (PK `canonicalKey`, `basisUnit`, basis-neutral nutrient columns per AD14) and `ShoppingListWithItems` per design Data Models DM4–DM7.
- **Files:**
  - Read: `specs/F1-app-skeleton-build-configuration-room-foundation/02_design.md` — Data Models DM4–DM7, AD7, AD14
  - Create: `app/src/main/java/ie/pantry/data/db/entity/ShoppingList.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/entity/ShoppingListItem.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/entity/RetailerAssistSession.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/entity/NutritionCacheEntry.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/relation/ShoppingListWithItems.kt`
- **Dependencies:** T5
- **Parallel:** Yes (with T6, T17, T18) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the list-side and nutrition entity classes
    WHEN their fields are inspected
    THEN `ShoppingListItem.quantityAmount`/`quantityUnit`/`section` and the four `NutritionCacheEntry` nutrient fields are nullable with no default annotation, `basisUnit` is non-null, and `ShoppingListItem` declares an indexed FK to `ShoppingList` (R3 AC2, AC3)
- **Tests:** Entity shape is asserted against the generated schema by T8's schema-shape tests; nutrition round-trips by T14's DAO tests.
- **Verification:** `./gradlew :app:compileDebugKotlin`

### - [x] T8: `PantryDatabase`, `ALL_MIGRATIONS`, committed `1.json`, `SchemaShapeTest`

- **Requirement:** R3, R6
- **Description:** Create `PantryDatabase` (seven entities, `version = VERSION = 1`, `exportSchema = true`, `Converters`, `clock` with private set, `create`/`createInMemory` factories per I1 with `.addMigrations(*ALL_MIGRATIONS)` and no destructive fallback; no DAO getters yet — T10/T12/T13/T14 add them), `ALL_MIGRATIONS = emptyArray()`, the `MutableClock` and `TestDatabases` (both `inMemory` and `onDisk` factories, which T15 and T23 only read) test utilities, and `SchemaShapeTest`; word any KDoc so it does not contain the literal `fallbackToDestructiveMigration`, which the verification grep would flag; build to generate `app/schemas/ie.pantry.data.db.PantryDatabase/1.json` and leave it in the working tree for the user to commit (Claude runs no mutating git commands).
- **Files:**
  - Read: `.gitignore` — confirm `app/schemas/` is not ignored
  - Create: `app/src/main/java/ie/pantry/data/db/PantryDatabase.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/Migrations.kt`
  - Create: `app/src/test/java/ie/pantry/testutil/MutableClock.kt`
  - Create: `app/src/test/java/ie/pantry/testutil/TestDatabases.kt`
  - Create: `app/src/test/java/ie/pantry/data/db/SchemaShapeTest.kt`
  - Create (generated by the Room compiler, never hand-edited): `app/schemas/ie.pantry.data.db.PantryDatabase/1.json`
- **Dependencies:** T3, T6, T7
- **Parallel:** Yes (with T17, T18) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the committed `1.json`
    WHEN it is parsed
    THEN `version` is 1 and the table set is exactly the seven snake_case tables (R3 AC1)
  - GIVEN an in-memory database
    WHEN `PRAGMA foreign_key_list` / `index_list` run on `recipe_ingredient`, `shopping_list_item`, `shopping_list_selection_entry` and `retailer_assist_session`
    THEN each declares its parent FK and the child FK columns are indexed (R3 AC2)
  - GIVEN every column DM1–DM7 marks optional, including `recipe.pendingDeletionAt`
    WHEN `PRAGMA table_info` runs
    THEN `notnull = 0` and `dflt_value` is null (R3 AC3, AC4)
  - GIVEN `./gradlew assembleDebug` has run after `1.json` was committed
    WHEN `git status --porcelain app/schemas` is inspected
    THEN it prints nothing (R3 AC5)
- **Tests:**
  - `` `exported schema is version 1 with exactly seven tables`() ``
  - `` `child tables declare indexed foreign keys to their parents`() ``
  - `` `selection and session tables declare foreign keys to their parents`() ``
  - `` `optional columns are nullable with no default`() ``
  - `` `recipe pendingDeletionAt is nullable with no default`() ``
  - `` `foreign keys pragma is enabled`() ``
  - File: `app/src/test/java/ie/pantry/data/db/SchemaShapeTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.SchemaShapeTest"`; `test -f app/schemas/ie.pantry.data.db.PantryDatabase/1.json`; `git check-ignore -q app/schemas/ie.pantry.data.db.PantryDatabase/1.json` exits 1; `grep -rn fallbackToDestructiveMigration app/src` prints nothing. After the user commits `1.json`, `./gradlew assembleDebug && git status --porcelain app/schemas` prints nothing.

### - [ ] T9: Migration harness skeleton with `FixtureV1`

- **Requirement:** R6
- **Description:** Create `FixtureV1` (programmatic `ContentValues` inserts, one row per table, some optionals absent, per AD10) and `MigrationHarnessTest` that creates the v1 database through `MigrationTestHelper` from the committed `1.json`, inserts the fixture, asserts `ALL_MIGRATIONS.isEmpty()` and `PantryDatabase.VERSION == 1`, runs `runMigrationsAndValidate` only when `VERSION > 1`, reopens the file through the production builder configuration, and asserts every fixture row field by field; this retires DR4 before DAO work. **[ASSUMPTION]** Read-back here uses cursor queries on the Room-opened database; T24 switches the read-back to DAOs once they exist (see Q1).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/PantryDatabase.kt` — `VERSION`, `FILE_NAME`, builder config
  - Read: `app/src/main/java/ie/pantry/data/db/Migrations.kt` — `ALL_MIGRATIONS`
  - Create: `app/src/test/java/ie/pantry/data/db/FixtureV1.kt`
  - Create: `app/src/test/java/ie/pantry/data/db/MigrationHarnessTest.kt`
- **Dependencies:** T8
- **Parallel:** Yes (with T10, T11, T15, T17, T18, T25) — only reads files T10/T11 modify; creates disjoint files
- **Acceptance Criteria:**
  - GIVEN schema version 1 with no prior version
    WHEN the harness runs
    THEN it resolves the committed `1.json`, reports no pending migration via explicit assertions, and does not skip (R6 AC1, AC2)
  - GIVEN the fixture rows inserted before the migration pass
    WHEN the database is reopened through the production builder configuration
    THEN every fixture field reads back equal, and absent optionals read back as null (R6 AC3)
- **Tests:**
  - `` `harness resolves committed schema 1 and reports no pending migration`() ``
  - `` `fixture rows read back field by field after migration pass`() ``
  - `` `absent optional fixture values read back as null`() ``
  - File: `app/src/test/java/ie/pantry/data/db/MigrationHarnessTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.MigrationHarnessTest"` (on failure to locate `1.json`, apply DR4's fallback: add `schemas` to the `debug` source set's assets in `app/build.gradle.kts`, then re-run).

## Phase 3: DAOs (FC5)

### - [ ] T10: `RecipeDao` reads and `updatedAt`-bumping writes

- **Requirement:** R4, R5
- **Description:** Create `RecipeDao` as an abstract class taking `PantryDatabase` (I3) with `observeCatalogue`, `observeRecipe`, `findRecipe`, the protected-abstract raw ops, and the public `@Transaction` wrappers `insertRecipeWithIngredients`/`updateRecipe`/`insertIngredient`/`updateIngredient`/`deleteIngredient` that set `updatedAt = db.clock.instant()` on the parent; add `recipeDao()` to `PantryDatabase`; add `FlowRecorder`. If Room rejects `protected abstract` (DR2), apply DR2's `openHelper.writableDatabase` fallback without adding any public surface.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/relation/RecipeWithIngredients.kt` — `orderedIngredients`
  - Create: `app/src/main/java/ie/pantry/data/db/dao/RecipeDao.kt`
  - Modify: `app/src/main/java/ie/pantry/data/db/PantryDatabase.kt` — add `abstract fun recipeDao(): RecipeDao`
  - Create: `app/src/test/java/ie/pantry/testutil/FlowRecorder.kt`
  - Create: `app/src/test/java/ie/pantry/data/db/RecipeDaoTest.kt`
- **Dependencies:** T8
- **Parallel:** Yes (with T9, T15, T17, T18, T25) — T12, T13 and T14 also modify `PantryDatabase.kt` and are ordered after it
- **Acceptance Criteria:**
  - GIVEN a recipe `Flow` collected from an in-memory database seeded with one recipe
    WHEN that recipe row is updated
    THEN the `Flow` emits a second value carrying the updated row (R4 AC2)
  - GIVEN no matching rows
    WHEN `observeCatalogue`/`observeRecipe` are collected
    THEN they emit `emptyList()`/`null` (R4 AC1, AC4)
  - GIVEN a stored recipe with a known `updatedAt` and a `MutableClock` advanced past it
    WHEN the recipe is updated, or a child ingredient is inserted, updated or deleted, including with a caller-supplied stale `updatedAt`
    THEN the parent's `updatedAt` equals the clock's instant (R5 AC1, AC3, AC4, AC5)
- **Tests:**
  - `` `catalogue flow emits empty list when no recipes`() ``
  - `` `recipe flow emits null for missing id`() ``
  - `` `recipe flow emits second value after recipe update`() ``
  - `` `findRecipe returns stored recipe by id`() ``
  - `` `findRecipe returns null for missing id`() ``
  - `` `insertRecipeWithIngredients stamps updatedAt from clock`() ``
  - `` `updateRecipe advances updatedAt to clock time`() ``
  - `` `updateRecipe overwrites caller supplied stale updatedAt`() ``
  - `` `updateRecipe preserves stored thumbnailPath and pendingDeletionAt`() ``
  - `` `updateRecipe on missing id is a no-op`() ``
  - `` `insertIngredient bumps parent updatedAt`() ``
  - `` `updateIngredient bumps parent updatedAt`() ``
  - `` `deleteIngredient bumps parent updatedAt`() ``
  - `` `deleteIngredient on missing id does not bump`() ``
  - `` `orderedIngredients sorted by position when inserted out of order`() ``
  - `` `orderedIngredients re-sorts after position update`() ``
  - File: `app/src/test/java/ie/pantry/data/db/RecipeDaoTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.RecipeDaoTest"`

### - [ ] T11: `RecipeDao` soft-delete filter, sweep and repository-only ops

- **Requirement:** R3, R4, R5
- **Description:** Add `setPendingDeletion`, `clearPendingDeletion`, `clearAllPendingDeletions` (single-column updates, no bump), and the repository-only `setThumbnailPath` (bumping), `thumbnailPathOf` and `deleteRecipeRow` to `RecipeDao`, with KDoc stating the `pendingDeletionAt IS NULL` rule for new queries (DR5) and the repository-only rule (DR3).
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/db/dao/RecipeDao.kt`
  - Modify: `app/src/test/java/ie/pantry/data/db/RecipeDaoTest.kt`
- **Dependencies:** T10
- **Parallel:** Yes (with T9, T15, T17, T18, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `observeCatalogue`/`observeRecipe` collected against one seeded recipe
    WHEN its `pendingDeletionAt` is set and then cleared
    THEN each `Flow` emits without the row, then emits with it again (R4 AC5)
  - GIVEN a stored recipe with a known `updatedAt`
    WHEN only `pendingDeletionAt` is written via set, clear or the sweep
    THEN `updatedAt` is unchanged after each of the three paths (R5 AC2)
  - GIVEN rows seeded with `pendingDeletionAt` set
    WHEN `clearAllPendingDeletions()` runs
    THEN every marker is cleared and the cleared count is returned (R3 AC7)
- **Tests:**
  - `` `catalogue flow drops row when pendingDeletionAt set`() ``
  - `` `catalogue flow re-includes row when pendingDeletionAt cleared`() ``
  - `` `recipe flow emits null while pending and row again when cleared`() ``
  - `` `findRecipe returns null while pending deletion`() ``
  - `` `setPendingDeletion leaves updatedAt unchanged`() ``
  - `` `clearPendingDeletion leaves updatedAt unchanged`() ``
  - `` `clearAllPendingDeletions leaves updatedAt unchanged`() ``
  - `` `clearAllPendingDeletions clears every leftover marker and returns count`() ``
  - `` `setThumbnailPath stores path and bumps updatedAt`() ``
  - `` `thumbnailPathOf returns stored path`() ``
  - `` `thumbnailPathOf returns null when unset or id missing`() ``
  - `` `deleteRecipeRow cascades to ingredients`() ``
  - File: `app/src/test/java/ie/pantry/data/db/RecipeDaoTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.RecipeDaoTest"`

### - [ ] T12: `SelectionEntryDao` with `Flow` reads

- **Requirement:** R4
- **Description:** Create `SelectionEntryDao` (I4) with the explicit `recipe_`-aliased JOIN projection filtered on `r.pendingDeletionAt IS NULL`, `upsert` and `delete`, and add `selectionEntryDao()` to `PantryDatabase`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/relation/SelectionEntryWithRecipe.kt` — prefix contract
  - Create: `app/src/main/java/ie/pantry/data/db/dao/SelectionEntryDao.kt`
  - Modify: `app/src/main/java/ie/pantry/data/db/PantryDatabase.kt` — add getter
  - Create: `app/src/test/java/ie/pantry/data/db/SelectionEntryDaoTest.kt`
- **Dependencies:** T11 — tests use `setPendingDeletion`/`deleteRecipeRow`
- **Parallel:** Yes (with T9, T15, T17, T18, T25) — not with T13/T14, which also modify `PantryDatabase.kt`
- **Acceptance Criteria:**
  - GIVEN a collected `observeSelection` `Flow`
    WHEN an entry is inserted, updated and deleted
    THEN the `Flow` emits an updated value after each write, and emits `emptyList()` when no entries exist (R4 AC1, AC3, AC4)
  - GIVEN a selected recipe
    WHEN its `pendingDeletionAt` is set and then cleared
    THEN the entry is absent from the `Flow` and then present again (R4 AC5)
- **Tests:**
  - `` `selection flow emits empty list when no entries`() ``
  - `` `selection flow emits after insert`() ``
  - `` `selection flow emits after update`() ``
  - `` `selection flow emits after delete`() ``
  - `` `selection flow hides entry while recipe pending deletion and restores on clear`() ``
  - `` `hard deleting recipe cascades its selection entry`() ``
  - File: `app/src/test/java/ie/pantry/data/db/SelectionEntryDaoTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.SelectionEntryDaoTest"`

### - [ ] T13: `ShoppingListDao` and `RetailerAssistDao` with `Flow` reads

- **Requirement:** R4
- **Description:** Create `ShoppingListDao` (I5, atomic `insertListWithItems`) and `RetailerAssistDao` (I6), and add both getters to `PantryDatabase`.
- **Files:**
  - Create: `app/src/main/java/ie/pantry/data/db/dao/ShoppingListDao.kt`
  - Create: `app/src/main/java/ie/pantry/data/db/dao/RetailerAssistDao.kt`
  - Modify: `app/src/main/java/ie/pantry/data/db/PantryDatabase.kt` — add two getters
  - Create: `app/src/test/java/ie/pantry/data/db/ShoppingListDaoTest.kt`
  - Create: `app/src/test/java/ie/pantry/data/db/RetailerAssistDaoTest.kt`
- **Dependencies:** T10 (`FlowRecorder`), T12 (ordering only — shared `PantryDatabase.kt`)
- **Parallel:** Yes (with T9, T15, T17, T18, T25) — not with T12/T14 (shared `PantryDatabase.kt`)
- **Acceptance Criteria:**
  - GIVEN collected `observeLists`, `observeList` and `observeSession` `Flow`s
    WHEN a backing row is inserted, updated and deleted
    THEN each `Flow` emits after every write, and emits `emptyList()`/`null` when nothing matches (R4 AC1, AC3, AC4)
  - GIVEN a list whose item insert fails
    WHEN `insertListWithItems` runs
    THEN no list row persists (atomicity)
- **Tests:**
  - `` `lists flow emits empty list when none`() ``
  - `` `list flow emits null for missing id`() ``
  - `` `lists and list flows emit after insertListWithItems`() ``
  - `` `list flow emits after updateItem`() ``
  - `` `lists and list flows emit after deleteList`() ``
  - `` `insertListWithItems is atomic when an item insert fails`() ``
  - `` `source selection snapshot round-trips`() ``
  - `` `deleting list cascades items and session`() ``
  - File: `app/src/test/java/ie/pantry/data/db/ShoppingListDaoTest.kt`
  - `` `session flow emits null when no session`() ``
  - `` `session flow emits after upsert insert`() ``
  - `` `session flow emits after upsert update`() ``
  - `` `session flow emits after delete`() ``
  - `` `empty skipped ids round-trip as empty list`() ``
  - File: `app/src/test/java/ie/pantry/data/db/RetailerAssistDaoTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ShoppingListDaoTest" --tests "*.RetailerAssistDaoTest"`

### - [ ] T14: `NutritionCacheDao` and DAO-surface review

- **Requirement:** R3
- **Description:** Create the one-shot `NutritionCacheDao` (I7), add its getter to `PantryDatabase`, and complete the R3 AC6 DAO-surface review now that all five DAOs exist.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/dao/` — all five DAOs, for the R3 AC6 review
  - Create: `app/src/main/java/ie/pantry/data/db/dao/NutritionCacheDao.kt`
  - Modify: `app/src/main/java/ie/pantry/data/db/PantryDatabase.kt` — add getter
  - Create: `app/src/test/java/ie/pantry/data/db/NutritionCacheDaoTest.kt`
- **Dependencies:** T13 (ordering only — shared `PantryDatabase.kt`)
- **Parallel:** Yes (with T16, T17, T18, T19, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a `NutritionCacheEntry` with null nutrient fields and each `basisUnit`
    WHEN it is upserted and found by key
    THEN nulls read back as null and `basisUnit` reads back unchanged
  - GIVEN the seven entities and five DAOs
    WHEN the DAO surface is inspected
    THEN each entity is reachable through at least one DAO method and no DAO contains merging, scaling, nutrition-aggregation or seasonality logic (R3 AC6)
- **Tests:**
  - `` `find returns null for unknown key`() ``
  - `` `upsert then find returns entry`() ``
  - `` `upsert replaces existing entry for same key`() ``
  - `` `null nutrient fields round-trip as null`() ``
  - `` `basisUnit round-trips for per 100 g and per 100 mL`() ``
  - File: `app/src/test/java/ie/pantry/data/db/NutritionCacheDaoTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.NutritionCacheDaoTest"`; review each file in `app/src/main/java/ie/pantry/data/db/dao/` against R3 AC6 (row reads/writes only; each of the seven entities appears as a DAO parameter or return type).

## Phase 4: Persistence Error Surface (FC6, CFC-4)

### - [ ] T15: `PersistenceException`, `Sentinels` and error-hygiene test foundation [CFC-4]

- **Requirement:** R3
- **Description:** Create `PersistenceException` (I10: message from category, constant operation and cause simple-name only; no cause chaining) and `persistenceFailure` (maps SQLite exception types to categories, copies stack frames, logs `key=value` fields only), the `Sentinels` fixture with `Throwable.assertNoSentinel()`, and `PersistenceErrorHygieneTest` covering the factory and hygiene case (4) direct-Room downgrade.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/PantryDatabase.kt` — factories for the downgrade case
  - Read: `app/src/test/java/ie/pantry/testutil/TestDatabases.kt` — `onDisk`
  - Create: `app/src/main/java/ie/pantry/data/db/PersistenceException.kt`
  - Create: `app/src/test/java/ie/pantry/testutil/Sentinels.kt`
  - Create: `app/src/test/java/ie/pantry/data/db/PersistenceErrorHygieneTest.kt`
- **Dependencies:** T8
- **Parallel:** Yes (with T9, T10, T11, T12, T13, T17, T18, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a cause whose message contains the title, ingredient and URL sentinels
    WHEN `persistenceFailure(operation, cause)` builds a `PersistenceException`
    THEN no sentinel appears anywhere in its throwable graph, `cause` is null, and the cause's stack frames are copied (R3 AC8, CFC-4)
  - GIVEN an on-disk database containing a sentinel-titled recipe and stamped `user_version = 2`
    WHEN the v1 `PantryDatabase` opens it
    THEN Room's `IllegalStateException` carries no sentinel (R3 AC8, CFC-4)
- **Tests:**
  - `` `assertNoSentinel detects sentinel in nested cause and suppressed`() `` — guards against a vacuous helper
  - `` `persistence failure message carries only category operation and cause type`() ``
  - `` `persistence failure does not chain the cause`() ``
  - `` `persistence failure copies cause stack frames`() ``
  - `` `persistence failure maps constraint exception to CONSTRAINT`() ``
  - `` `persistence failure logs only key value fields with no sentinel`() `` — reads `ShadowLog` for the conversion site (design Error Handling: `Log.w` fields `category`, `operation`, `causeType`, `reason` only)
  - `` `downgraded database open throws content-free exception`() ``
  - File: `app/src/test/java/ie/pantry/data/db/PersistenceErrorHygieneTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.PersistenceErrorHygieneTest"`

### - [ ] T16: Error-hygiene cases for DAO constraint failures [CFC-4]

- **Requirement:** R3
- **Description:** Add hygiene cases (1) FK violation via `RecipeDao` with `rawText = INGREDIENT`, (2) PK conflict via `RecipeDao` with `title = TITLE`, and (6) FK violation via `ShoppingListDao` with `displayName = INGREDIENT`, each followed by `assertNoSentinel()`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/dao/RecipeDao.kt` — public write wrappers
  - Read: `app/src/main/java/ie/pantry/data/db/dao/ShoppingListDao.kt` — `insertListWithItems`
  - Modify: `app/src/test/java/ie/pantry/data/db/PersistenceErrorHygieneTest.kt`
- **Dependencies:** T10, T13, T15
- **Parallel:** Yes (with T14, T17, T18, T19, T25) — only this task and T21 modify the hygiene test, and T21 depends on it
- **Acceptance Criteria:**
  - GIVEN content-bearing sentinel fixtures
    WHEN a DAO write fails on an FK or PK constraint
    THEN the resulting exception's throwable graph contains no sentinel (R3 AC8, CFC-4, AD5(a))
- **Tests:**
  - `` `recipe dao foreign key violation carries no ingredient sentinel`() ``
  - `` `recipe dao primary key conflict carries no title sentinel`() ``
  - `` `shopping list dao foreign key violation carries no item sentinel`() ``
  - File: `app/src/test/java/ie/pantry/data/db/PersistenceErrorHygieneTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.PersistenceErrorHygieneTest"`; `grep -rn '@RawQuery' app/src/main/java/ie/pantry/data/db` prints nothing.

## Phase 5: Thumbnail Pipeline (FC7)

### - [ ] T17: `ThumbnailProcessor` bounds-aware decode/downsample/re-encode

- **Requirement:** R8
- **Description:** Create `ThumbnailProcessor` (I9: `inJustDecodeBounds` pass, power-of-two `inSampleSize` via `computeSampleSize`, EXIF orientation via platform `ExifInterface`, exact scale to longest edge ≤ 512, JPEG quality 85, `null` on undecodable input or `OutOfMemoryError`) and the `ImageFixtures` generator (`jpeg`, `png`, streamed `pngBomb`, `garbage`).
- **Files:**
  - Create: `app/src/main/java/ie/pantry/data/thumbnail/ThumbnailProcessor.kt`
  - Create: `app/src/test/java/ie/pantry/testutil/ImageFixtures.kt`
  - Create: `app/src/test/java/ie/pantry/data/thumbnail/ThumbnailProcessorTest.kt`
- **Dependencies:** T3 — Robolectric config
- **Parallel:** Yes (with T5–T16, T18) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a 20 000 × 20 000 PNG bomb under a 1 GB test heap
    WHEN it is processed
    THEN a JPEG with longest edge ≤ 512 px is returned, which a native-resolution decode could not produce (R8 AC2)
  - GIVEN bytes that are not an image
    WHEN they are processed
    THEN `null` is returned and nothing is thrown (R8 AC3, decode half)
- **Tests:**
  - `` `computeSampleSize returns 1 when image already within max edge`() ``
  - `` `computeSampleSize returns largest power of two keeping longest edge at least max`() ``
  - `` `process downsamples png to jpeg within max edge`() ``
  - `` `process decodes png bomb within bounded test heap`() ``
  - `` `computeSampleSize returns 32 for a 20000 by 20000 image at max edge 512`() `` — pins the sampling factor the bomb test relies on, since under native graphics the heap cap alone does not discriminate a native-resolution decode
  - `` `process applies exif orientation 6 by swapping aspect`() ``
  - `` `process returns null for undecodable bytes`() ``
  - File: `app/src/test/java/ie/pantry/data/thumbnail/ThumbnailProcessorTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ThumbnailProcessorTest"` (no `assume*` — an unsupported native-graphics host must fail, per DR7).

### - [x] T18: `ThumbnailStore` and `ThumbnailOutcome`

- **Requirement:** R8
- **Description:** Create `ThumbnailStore` (I9: atomic `.tmp`→`renameTo` write to `thumbnails/<uuid>.jpg`, never-throwing `delete`, `resolve` with the shared canonical-path escape check) and the `ThumbnailOutcome` sealed interface (`Stored`, `NoImage(DECODE_FAILED | WRITE_FAILED | NO_SOURCE)`).
- **Files:**
  - Create: `app/src/main/java/ie/pantry/data/thumbnail/ThumbnailStore.kt`
  - Create: `app/src/main/java/ie/pantry/data/thumbnail/ThumbnailOutcome.kt`
  - Create: `app/src/test/java/ie/pantry/data/thumbnail/ThumbnailStoreTest.kt`
- **Dependencies:** T2
- **Parallel:** Yes (with T3–T17) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a store rooted at a regular file instead of a directory
    WHEN `write` is called
    THEN it throws `IOException` (the failure T19 degrades to `NoImage(WRITE_FAILED)`)
  - GIVEN a path escaping `thumbnails/`
    WHEN `resolve` or `delete` is called
    THEN `resolve` throws `IllegalArgumentException` and `delete` returns `false`
- **Tests:**
  - `` `write stores file under thumbnails and returns relative path`() ``
  - `` `write leaves no tmp file behind`() ``
  - `` `write throws IOException when store root is a regular file`() ``
  - `` `delete removes file and is idempotent`() ``
  - `` `resolve rejects path escaping thumbnails`() ``
  - `` `delete returns false for path escaping thumbnails`() ``
  - File: `app/src/test/java/ie/pantry/data/thumbnail/ThumbnailStoreTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.ThumbnailStoreTest"`

## Phase 6: Recipe Repository (FC8)

### - [ ] T19: `RecipeRepository.saveNewRecipe` with thumbnail path

- **Requirement:** R8
- **Description:** Create `RecipeRepository` (I8) with `saveNewRecipe`: process bytes, store the file on `Dispatchers.IO`, then `insertRecipeWithIngredients` with `thumbnailPath` from the outcome and `pendingDeletionAt = null`; on row failure delete the new file and rethrow via `persistenceFailure`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/dao/RecipeDao.kt` — bumping and repository-only ops
  - Read: `app/src/main/java/ie/pantry/data/db/PersistenceException.kt` — `persistenceFailure`
  - Create: `app/src/main/java/ie/pantry/data/db/RecipeRepository.kt`
  - Create: `app/src/test/java/ie/pantry/data/db/RecipeRepositoryTest.kt`
- **Dependencies:** T11, T15, T17, T18
- **Parallel:** Yes (with T14, T16, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN import-style PNG bytes and picker-style EXIF-rotated JPEG bytes
    WHEN each is saved with a recipe
    THEN each stored file starts `FF D8 FF`, has longest edge ≤ 512 px, and the row references it (R8 AC1)
  - GIVEN undecodable bytes, or a store whose write fails
    WHEN the recipe is saved
    THEN the recipe row is saved with `thumbnailPath == null` and the outcome is `NoImage(DECODE_FAILED)` or `NoImage(WRITE_FAILED)` respectively, with no hang or crash (R8 AC3)
- **Tests:**
  - `` `saveNewRecipe stores import png bytes as downsampled jpeg`() ``
  - `` `saveNewRecipe stores picker exif jpeg bytes as downsampled jpeg`() ``
  - `` `saveNewRecipe with undecodable bytes saves recipe with DECODE_FAILED`() ``
  - `` `saveNewRecipe with failing store saves recipe with WRITE_FAILED`() ``
  - `` `saveNewRecipe with null bytes saves recipe with NO_SOURCE`() ``
  - `` `saveNewRecipe overrides caller thumbnailPath and pendingDeletionAt`() ``
  - `` `saveNewRecipe deletes written file when row insert fails`() ``
  - File: `app/src/test/java/ie/pantry/data/db/RecipeRepositoryTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.RecipeRepositoryTest"`

### - [ ] T20: `RecipeRepository` replace/remove thumbnail and hard delete

- **Requirement:** R8
- **Description:** Add `replaceThumbnail` (write new file, `setThumbnailPath`, then delete old file; on failure leave the existing thumbnail and return `NoImage`), `removeThumbnail` and `deleteRecipe` (row then file), logging failed file deletes by category only (DR6).
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/data/db/RecipeRepository.kt`
  - Modify: `app/src/test/java/ie/pantry/data/db/RecipeRepositoryTest.kt`
- **Dependencies:** T19
- **Parallel:** Yes (with T21, T22, T23, T24, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a saved recipe with a thumbnail
    WHEN its thumbnail is replaced
    THEN the new file exists, the old file is gone, and `updatedAt` is bumped (R8 AC4)
  - GIVEN a saved recipe with a thumbnail
    WHEN `deleteRecipe` runs
    THEN the row and its thumbnail file are both gone
- **Tests:**
  - `` `replaceThumbnail writes new file then deletes old file and bumps updatedAt`() ``
  - `` `replaceThumbnail with undecodable bytes leaves existing thumbnail unchanged`() ``
  - `` `removeThumbnail clears reference and deletes file`() ``
  - `` `deleteRecipe removes row and thumbnail file`() ``
  - File: `app/src/test/java/ie/pantry/data/db/RecipeRepositoryTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.RecipeRepositoryTest"`

### - [ ] T21: Error-hygiene cases for repository and thumbnail failures [CFC-4]

- **Requirement:** R3
- **Description:** Add hygiene cases (3) repository save after `db.close()`, (4) the repository half of the downgrade case, and (5) thumbnail write failure, each against `Sentinels` content.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/RecipeRepository.kt` — catch/convert sites
  - Modify: `app/src/test/java/ie/pantry/data/db/PersistenceErrorHygieneTest.kt`
- **Dependencies:** T16, T19
- **Parallel:** Yes (with T20, T22, T23, T24, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a closed or downgraded database and a sentinel-titled recipe
    WHEN `saveNewRecipe` is called
    THEN a `PersistenceException` with the expected category (`DATABASE_OPEN`/`MIGRATION` for the downgrade) and a null `cause` is thrown, and its throwable graph contains no sentinel (R3 AC8, CFC-4)
  - GIVEN a store whose write fails
    WHEN a sentinel-titled recipe is saved with thumbnail bytes
    THEN no exception escapes and the outcome is `NoImage(WRITE_FAILED)`
- **Tests:**
  - `` `repository save after close throws content-free PersistenceException with null cause`() ``
  - `` `repository save on downgraded database throws content-free PersistenceException`() ``
  - `` `thumbnail write failure yields NoImage with no escaping exception`() ``
  - `` `thumbnail write failure logs no sentinel`() `` — reads `ShadowLog` for the degradation site
  - File: `app/src/test/java/ie/pantry/data/db/PersistenceErrorHygieneTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.PersistenceErrorHygieneTest"`

## Phase 7: App Container (FC3)

### - [ ] T22: `AppContainer` and `PantryApplication.container` wiring

- **Requirement:** R2
- **Description:** Create `AppContainer` (I2: primary-constructor injection, DAO `val`s read once, `recipeRepository`, companion `production(app, clock)`) and add `val container by lazy { AppContainer.production(this) }` to `PantryApplication`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/RecipeRepository.kt` — constructor
  - Create: `app/src/main/java/ie/pantry/di/AppContainer.kt`
  - Modify: `app/src/main/java/ie/pantry/PantryApplication.kt`
  - Create: `app/src/test/java/ie/pantry/di/AppContainerTest.kt`
- **Dependencies:** T14, T19
- **Parallel:** Yes (with T20, T21, T23, T24, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the running `PantryApplication` under Robolectric
    WHEN `container` is read twice
    THEN the same container, the same `PantryDatabase` and the same DAO instances are returned (R2 AC2)
  - GIVEN an in-memory database
    WHEN an `AppContainer` is constructed from it
    THEN construction succeeds with no device and no network (R2 AC4)
  - GIVEN the build configuration
    WHEN it is inspected
    THEN no DI framework is on the classpath (R2 AC3)
- **Tests:**
  - `` `application container is the same instance across reads`() ``
  - `` `container database and DAO properties are identical across reads`() ``
  - `` `container builds on in-memory database without network`() ``
  - File: `app/src/test/java/ie/pantry/di/AppContainerTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.AppContainerTest"`; `grep -niE 'hilt|dagger|koin' gradle/libs.versions.toml app/build.gradle.kts` prints nothing.

## Phase 8: Durability, Migration Procedure and Closeout (FC9)

### - [ ] T23: `RestartDurabilityTest` (restart + no network)

- **Requirement:** R7
- **Description:** Create `RestartDurabilityTest`: on-disk database, one row per entity with some optionals absent, `close()`, reopen a new instance on the same file, compare field by field, with the no-network guards (no active `ConnectivityManager` network, a failing `ProxySelector` installed after Robolectric bootstrap, `okhttp3.OkHttpClient` not loadable).
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/TestDatabases.kt` — `onDisk`
  - Create: `app/src/test/java/ie/pantry/data/db/RestartDurabilityTest.kt`
- **Dependencies:** T14
- **Parallel:** Yes (with T19, T20, T21, T22, T24, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a closed database populated with a row in each of the seven entities
    WHEN it is reopened from the same file in a fresh instance
    THEN every row reads back with unchanged field values (R7 AC1)
  - GIVEN the same test
    WHEN it executes
    THEN no network access is available or attempted (R7 AC2)
  - GIVEN a row whose optional field was stored absent
    WHEN read back after reopen
    THEN it is still `null` (R7 AC3)
- **Tests:**
  - `` `every entity row reads back unchanged after reopen`() ``
  - `` `absent optional values stay null after reopen`() ``
  - `` `reopen completes with no network access`() ``
  - File: `app/src/test/java/ie/pantry/data/db/RestartDurabilityTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.RestartDurabilityTest"`

### - [ ] T24: Extend migration harness to read fixture back through DAOs

- **Requirement:** R6
- **Description:** Change `MigrationHarnessTest`'s post-migration read-back from cursor queries to the five DAOs, as design Testing Strategy specifies, keeping every field-level assertion.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/dao/` — DAO read methods
  - Modify: `app/src/test/java/ie/pantry/data/db/MigrationHarnessTest.kt`
  - Modify: `app/src/test/java/ie/pantry/data/db/FixtureV1.kt` — expose expected values for DAO comparison, if needed
- **Dependencies:** T9, T14
- **Parallel:** Yes (with T19, T20, T21, T22, T23, T25) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the v1 fixture inserted through `MigrationTestHelper`
    WHEN the database is reopened through the production builder configuration and read through the DAOs
    THEN every fixture row's fields, including absent optionals, match the fixture (R6 AC1, AC3)
- **Tests:**
  - `` `fixture rows read back through DAOs after migration pass`() ``
  - File: `app/src/test/java/ie/pantry/data/db/MigrationHarnessTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "*.MigrationHarnessTest"`

### - [x] T25: Write `docs/migrations.md`

- **Requirement:** R6
- **Description:** Write the six-step migration procedure from design § File Structure (bump `VERSION`; build and commit the new schema JSON unedited; add an explicit `Migration` to `ALL_MIGRATIONS` throwing only `PersistenceException(MIGRATION, …)` with no row data in SQL; extend the harness with `FixtureVn`; run `./gradlew testDebugUnitTest`; never `fallbackToDestructiveMigration*`), plus the rule that every new recipe-backed query keeps `pendingDeletionAt IS NULL` (DR5).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/Migrations.kt` — `ALL_MIGRATIONS` name
  - Create: `docs/migrations.md`
- **Dependencies:** T8
- **Parallel:** Yes (with T9–T24) — disjoint file
- **Acceptance Criteria:**
  - GIVEN the repository
    WHEN `docs/migrations.md` is inspected
    THEN it states that every schema change bumps the version, commits the new exported schema JSON, adds an explicit `Migration`, and extends the harness fixture (R6 AC5)
- **Tests:** Not applicable — documentation only; see Verification.
- **Verification:** `for p in 'VERSION' 'schemas' 'Migration' 'ALL_MIGRATIONS' 'FixtureV' 'testDebugUnitTest' 'fallbackToDestructiveMigration' 'pendingDeletionAt IS NULL'; do grep -q "$p" docs/migrations.md || echo "missing: $p"; done` prints nothing, and `grep -cE '^[1-6]\. ' docs/migrations.md` prints `6` (the ordered procedure, not just the keywords).

### - [ ] T26: Feature closeout: full suite, lint, clean build and manual checks

- **Requirement:** R1, R2, R3, R6
- **Description:** Run the full suite and lint, rebuild both variants from a clean checkout, and perform the design's remaining manual checks.
- **Files:**
  - Read: `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` — release-variant manifest policy
  - Read: `app/build.gradle.kts` — platform levels and release build type
- **Dependencies:** T1–T25
- **Parallel:** No — final gate
- **Acceptance Criteria:**
  - GIVEN a fresh clone with no `build/` or `.gradle/` directories
    WHEN `./gradlew assembleDebug assembleRelease` then `./gradlew testDebugUnitTest lintDebug` run
    THEN all succeed (R1 AC1; all F1 tests green)
  - GIVEN the merged release manifest
    WHEN it is inspected
    THEN `android:allowBackup="false"` is present and no `android.permission.*` `uses-permission` is declared, tolerating androidx.core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (R1 AC5, release variant)
  - GIVEN the debug APK installed
    WHEN it is launched
    THEN the placeholder screen renders without crashing (R2 AC1)
  - GIVEN a rebuild after `1.json` is committed
    WHEN `git status --porcelain app/schemas` runs
    THEN it prints nothing (R3 AC5)
- **Tests:** Not applicable — runs every test written in T3–T24; see Verification.
- **Verification:** Precondition: the user has committed the working tree (`1.json`, the new sources and the wrapper jar); Claude runs no mutating git. From a fresh clone: `./gradlew assembleDebug assembleRelease && ./gradlew testDebugUnitTest lintDebug`; with `M=app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`, `grep -c 'allowBackup="false"' "$M"` prints `1` and `grep -c 'uses-permission android:name="android.permission' "$M"` prints `0`; `grep -rn fallbackToDestructiveMigration app/src`, `grep -rn '@RawQuery' app/src/main/java/ie/pantry/data/db`, `grep -niE 'hilt|dagger|koin' gradle/libs.versions.toml app/build.gradle.kts` and `grep -nE '"[^"]+:[^"]+:[0-9][^"]*"' build.gradle.kts app/build.gradle.kts settings.gradle.kts` each print nothing; `git status --porcelain app/schemas` prints nothing; `./gradlew installDebug` and launch per T4.

## Implementation Order

1. T1 — wrapper, settings and catalog are the base of every other task.
2. T2 — app module build script, manifest and resources; `:app:processDebugResources` proves the catalog resolves. The first full `assembleDebug assembleRelease` (retires DR1) is T4's, once the manifest's classes exist.
3. T3, T5, T18 — parallel: disjoint files, each needs only the app module. T4 follows T3 (the manifest's `.PantryApplication` reference must resolve for the first full assemble) and runs alongside T5 and T18.
4. T6, T7, T17 — parallel: entities need T5's enums/converters; T17 needs T3's Robolectric config and surfaces DR7 early.
5. T8 — database class and committed `1.json` pin the schema before any DAO (spec RK1).
6. T9, T10, T15, T25 — parallel: T9 retires DR4 before DAO work; T10 retires DR2; T15 needs only the database.
7. T11 — completes `RecipeDao` (soft delete, sweep, repository-only ops).
8. T12 — selection DAO needs T11's soft-delete ops for its tests.
9. T13 — list and session DAOs; serial after T12 only because both edit `PantryDatabase.kt`.
10. T14, T16, T19 — parallel: T14 is the last `PantryDatabase.kt` edit; T16 needs T13's DAO; T19 needs T11, T15, T17, T18.
11. T20, T21, T22, T23, T24 — parallel: repository completion, repository hygiene cases, container wiring, durability test, and DAO-based harness read-back all touch disjoint files.
12. T26 — final gate after everything is green.

Notes:
- **Catalog re-pin.** T2 and T4 retire DR1's version-set risk for plain compilation, but Room/KSP codegen and Robolectric-with-Room first run at T8. A catalog re-pin found there must re-run the T3, T5, T17 and T18 tests before continuing.
- **Parallel tasks share the `app` test source set.** Run the parallel groups above in separate worktrees, or serialise their Verification runs, so a half-written sibling task cannot break another task's `testDebugUnitTest`.

## Implementation Deviations

> Phase-4 minor-deviation ledger — populated by the triage gate's minor path (`SKILL.md` § "Mid-implementation discovery"). Append-only during Phase 4; resolved at the Final-Check completion gate. Leave empty until a deviation is logged.

| Date | Task | What spec/design said | What was actually done | Why | Classification | Backport status |
|------|------|-----------------------|------------------------|-----|----------------|-----------------|
| 2026-09-24 | T1 | T1 Verification: `./gradlew help` succeeds, tolerating a warning that `:app` has no project directory until T2 | Created an empty `app/` directory during T1 so the wrapper could be generated | The developer's system Gradle is 9.x, which makes a missing project directory a hard error (not the warning Gradle 8.11.1 gives), so `gradle wrapper` failed | minor | pending |
| 2026-09-24 | T8 | Design I1 lists the factories `create` and `createInMemory` on `PantryDatabase` | Added an `internal fun createAt(context, fileName, clock)` alongside them; `create` delegates to it with `FILE_NAME` | `TestDatabases.onDisk(name, clock)` (design Testing Strategy) needs a differently-named database file, and `clock` has a private setter only the factories can assign; the addition is internal and changes no public interface | minor | pending |

## TDD Exceptions

> Phase-4 TDD-cycle-skip log — appended by the calling Claude during Phase 4 when the test-first red→green→refactor cycle is skipped for a code-stack task. Append-only during Phase 4; `Resolution`-column updates (`pending` → `accepted` or `remediate`) are resolved at the Final-Check completion gate. Leave empty until a skip is logged. **Code stacks (python/java/kotlin) only.** Not applicable for generic-profile tasks. (A code task that genuinely needs *no* test at all is not a skip — use the `**Tests:** none — <reason>` override instead.)

| Date | Task | Skip Reason | Resolution |
|------|------|-------------|------------|

## Open Questions

> All questions must be resolved before proceeding to implementation.

- [x] Q1: Design Implementation Sequence step 2 puts `MigrationHarnessTest` before the DAOs (to retire DR4 early), but design Testing Strategy says the harness asserts fixture rows "via DAOs". This breakdown reads rows back with cursor queries in T9 and switches to DAO read-back in T24. Accept the two-step split, or move the whole harness after T14 and give up retiring DR4 early?
  - **Resolution:** Accept the two-step split (T9 cursor read-back now, T24 DAO read-back after T14). Confirmed by the user.
- [x] Q2: Every Verification command needs JDK 17, Android SDK 35 and network access to Google Maven, Maven Central and Robolectric's `android-all` jars. Design DR1 records that this devcontainer has none of these. Will Phase 4 run on the developer's own machine, or will the devcontainer be provisioned first?
  - **Resolution:** Phase 4 verification runs on the developer's own machine, which has JDK 17, Android SDK 35 and network access. No devcontainer provisioning task is added. Confirmed by the user.

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

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes                                                                              |
|------|------------|-------|-------------|-----------|----------|--------|------------------------------------------------------------------------------------|
| 1    | 2026-09-24 | 1     | 0           | 13        | 0        | 12     | tags=d0u1c0; halt vote (delivery-manager, critic: T3 no-permissions test and T26…) |
| 2    | 2026-09-24 | 0     | 0           | 0         | 0        | 17     | converged (0 HIGH); tags=d0u0c0                                                    |

### Sealed dispositions

- `[SEAL-01]` **T8 is oversized (6 files, schema plus test utilities)** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - T8 authors 5 files, and the 6th, 1.json, is generated by Room and never hand-edited, so it is within the 3-5 file sizing rule; splitting the test utilities out would add a task for no dependency gain.
- `[SEAL-02]` **T9 cursor read-back is rewritten by T24 as DAO read-back…** (pass 1, accepted-as-risk) — Defense: user-confirmed decision (Q1, two-step split accepted in Open Questions) - retires DR4 early per design Implementation Sequence step 2.
- `[SEAL-03]` **T26 re-lists nearly every grep and manual check from…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged (pass already non-terminal, kept as-is on the merits) - the closeout deliberately re-runs the checks as one final gate from a clean checkout; the duplication is intentional and low-cost.
- `[SEAL-04]` **T14 DAO-surface review (R3 AC6) is manual, in Verification…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - a structural-absence review has no reliable grep pattern and is consistent with the spec's SEAL-01 posture on this class of check.
- `[SEAL-05]` **Exposure sequencing: no edge found** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - informational, nothing to change.
- `[SEAL-06]` **T2 grep counted lines rather than distinct settings** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - resolved by the per-pattern check applied to the overlapping MED.
- `[SEAL-07]` **T6/T7 verify only compileDebugKotlin so Room relation…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - tasks.md is terminal and cannot defer forward; T8 SchemaShapeTest and the DAO tests are the detecting tasks and are ordered directly after T6/T7.
- `[SEAL-08]` **T3/T4 ordering rationale is repeated in three places** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - editorial only; each place is read on its own by a different reader.
- `[SEAL-09]` **T6 and T7 could be merged** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - the split gives parallelism and each stays under the sizing limit.
- `[SEAL-10]` **Hygiene test spread across T15, T16 and T21** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - splits are forced by dependency ordering (T21 needs the repository).
- `[SEAL-11]` **T15 assertNoSentinel self-test is small extra scope** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - it guards the CFC-4 helper against passing vacuously, which is the point of the [CFC-4] tests.
- `[SEAL-12]` **T25 docs task could fold into T8** (pass 1, accepted-as-risk) — Defense: synthesizer-judged - T25 is independent and parallel with disjoint files; folding it would grow T8 past the sizing rule.
- `[SEAL-13]` **T2 verification does not check R1 AC4 no-proguardFiles…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - T2 AC2 already states release has no proguardFiles; the missing grep is an implementation-time verification detail for Phase 4, and R8/R1 build checks in T4 and T26 exercise the release variant.
- `[SEAL-14]` **T21 repository-save-after-close test may be vacuous because…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - how the failure is forced is a Phase 4 test-writing detail; the T21 downgrade case independently forces a real open failure, and the red-first TDD step will expose a vacuous close case.
- `[SEAL-15]` **T17 bomb test does not prove process uses the computed…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - design I9 defines no decode seam and inventing one here would add interface surface to an approved design; pass 1 already added the sampling-factor test, and the bomb test at least fails on an undecodable or oversized result; Phase 4 may strengthen it if a seam proves cheap.
- `[SEAL-16]` **T20 tests omit replace WRITE_FAILED, old-file-delete-fails…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - these are edge cases that a Phase 4 implementer materialises while writing T20 under test-first; R8 ACs are covered by the tests already named.
- `[SEAL-17]` **Android SDK location is missing from documented…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - Q2 records that verification runs on the developer's own machine, which already has the SDK configured; documenting it in the T2 README is optional polish.
- `[SEAL-18]` **T1 wrapper generation assumes a local Gradle install with…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - T1 already marks this as an explicit ASSUMPTION; on the developer's own machine the user can run gradle wrapper, so a written fallback adds nothing.
- `[SEAL-19]` **T19 does not name the open/downgrade IllegalStateException…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - T21 case (4) asserts that conversion against T19's saveNewRecipe and T19 depends on T15 which defines persistenceFailure; naming it in T19's prose is editorial.
- `[SEAL-20]` **T15 downgrade case depends only on T8 and needs a way to…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - T15 can open the database through the PantryDatabase factories that T8 provides, as its own Read entry states; no DAO is needed to trigger Room's downgrade exception.
- `[SEAL-21]` **T26 greps fallbackToDestructiveMigration across tests but…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the literal appears in comments only if a Phase 4 author copies design KDoc; a hit is visible and fixed at that moment; T8 already carries the warning and the pass-1 Addressed row for the same class.
- `[SEAL-22]` **T15/T16/T21 add tests beyond the design's six hygiene…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - all are traceable to design Error Handling (the Log.w field rule) and add CFC-4 coverage; user-visible cost is a few small tests.
- `[SEAL-23]` **T10/T11 RecipeDaoTest lists have near-duplicate updatedAt…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - per-acceptance-criterion test names keep R5 traceability readable; consolidation would trade that away.
- `[SEAL-24]` **T4 and T17 Parallel lists omit each other** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - nothing conflicts between them; editorial.
- `[SEAL-25]` **T21 marked parallel with T20 while reading…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - T21's access is read-only and the pass-1 isolation note in Implementation Order covers shared-source-set runs.
- `[SEAL-26]` **T25 grep for six numbered steps is a brittle line counter** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - it is a coarse presence check added in pass 1 and the AC is a documentation statement reviewed by the user at implementation.
- `[SEAL-27]` **T3 sdk=34 fallback has no trigger to log an Implementation…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - the fallback is pre-authorised by design Testing Strategy, so using it is not a deviation from the approved design.
- `[SEAL-28]` **T9 Parallel list and Implementation Order step 6 group…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - editorial; both are true statements about what may run in parallel.
- `[SEAL-29]` **T26 re-runs greps from earlier tasks and Implementation…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass, not user-confirmed) - this repeats concerns already accepted in pass 1 (intentional closeout gate and repeated ordering rationale) with no new evidence.

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to implementation
- **Content Hash:** `3e4fc65bdca6d1d2`
- **Hash basis:** v2
