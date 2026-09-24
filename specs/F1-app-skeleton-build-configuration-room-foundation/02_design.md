# Design: App Skeleton, Build Configuration & Room Foundation

**Spec:** `specs/F1-app-skeleton-build-configuration-room-foundation/01_spec.md`

## Goals and Non-Goals

**Goals:**
- A reproducible single-`:app`-module build with every version in `gradle/libs.versions.toml` (R1).
- A launchable Material 3 placeholder shell and one hand-written app-scoped container (R2).
- All seven entities at Room schema version 1, exported schema committed, DAOs with `Flow` reads, the `pendingDeletionAt` filter and sweep, and the `updatedAt` bump inside the persistence layer (R3, R4, R5).
- A migration harness that asserts on fixture row contents, plus a written migration procedure (R6).
- A Robolectric restart/offline durability proof (R7).
- One thumbnail write path: bounds-aware decode, downsample, JPEG re-encode, file write/replace/delete, with failures degrading to no-image (R8).
- Honour CFC-4: no exception built or passed on by F1's persistence code carries row content (R3 AC8).

Requirement → component coverage (component IDs below are `FC<n>`, to avoid clashing with ARCHITECTURE's `C1`–`C10`):

| Requirement | Components |
|---|---|
| R1 | FC1, FC2 (manifest) |
| R2 | FC2, FC3 |
| R3 | FC4, FC5, FC6 |
| R4 | FC5 |
| R5 | FC5 |
| R6 | FC4 (`Migrations.kt`, builder config), FC9 |
| R7 | FC9 (restart test), FC4 |
| R8 | FC7, FC8 |

**Non-Goals:**
- Any user-facing catalogue, import, form, list or walkthrough screen (spec Boundaries, Never Do). The placeholder screen has no content.
- The C9 undo-window timer, its foreground-scope lifecycle observer, and *when* the sweep runs. F1 supplies the sweep and the soft-delete column operations; F6 calls them.
- Search/filter queries (F7), selection add/remove rules and servings defaulting (F10), list generation and merging (F11), retailer-assist pointer movement (F12), and nutrition resolution (F9/F16). F1 provides row-level DAO operations only.
- Release signing, launcher icon artwork and store-listing assets (F15). `assembleRelease` produces an unsigned release APK.
- JUnit 5 / Jupiter. No F1 code is a pure domain engine. F3 adds the JUnit Platform and the vintage engine when it needs them (see AD9).
- Orphan-file garbage collection at startup. Only replacement and hard deletion remove files (DR6).
- Any network code: no HTTP client, no permission, no socket (Network Exposure Triage, branch (a)).

## Architecture Decisions

| Decision | Choice | Alternatives Rejected | Rationale | Consequences |
|----------|--------|-----------------------|-----------|--------------|
| AD1 — Room database class name / schema path | `ie.pantry.data.db.PantryDatabase`. Schema exported through the Room Gradle plugin (`room { schemaDirectory("$projectDir/schemas") }`) to `app/schemas/ie.pantry.data.db.PantryDatabase/1.json` | KSP arg `room.schemaLocation` (the older mechanism, and it is not incremental-safe) | Fixes the spec's `<database-class>` placeholder. The plugin is Room's supported path for schema export plus test assets | `.gitignore` is already correct: it ignores `build/`, not `app/schemas/`. `1.json` is regenerated on every build and must be unmodified by it — no diff in `git status` (R3 AC5's actual requirement; "byte-identical" here means identical to the already-committed file, not a stronger claim) |
| AD2 — `updatedAt` bump mechanism (spec Decision Point) | The DAO is an abstract class. Room hands it `PantryDatabase` through its constructor. Raw `@Insert`/`@Update`/`@Delete` methods for `Recipe`/`RecipeIngredient` are `protected abstract`. The only public writes are `open @Transaction` wrappers that set `updatedAt = clock.instant()` on the parent row inside the same transaction | SQLite `AFTER UPDATE` triggers (the time source can't be injected, so R5 AC1's controllable clock fails, and the triggers aren't in the exported schema); a repository-only bump over public raw DAO writes (bypassable, which fails R5 AC5); making the caller pass `updatedAt` (bypassable) | Only the wrapper can reach the raw ops, so no write path skips the bump. Parent and child are written in one transaction | Relies on Room codegen accepting `protected abstract` DAO methods **[ASSUMPTION — verified at first compile; fallback in DR2 preserves the same no-public-reach guarantee]** |
| AD3 — Time source injection (spec Decision Point) | `java.time.Clock`, held on `PantryDatabase` as `clock` (set once by the `PantryDatabase.create`/`createInMemory` factories before the instance is returned). DAOs read it through the constructor-injected database | A `Clock` argument on every DAO write; a global singleton | Room builds DAOs itself and only passes the database in, so the database is the one injection seam Room offers. `Clock.fixed`/a mutable test clock gives deterministic R5 tests | `Instant` columns are stored as epoch-millis `Long` through a `TypeConverter` (java.time is available from `minSdk` 26) |
| AD4 — Soft-delete filtering | Every recipe-backed query (catalogue, detail, selection join) has `WHERE recipe.pendingDeletionAt IS NULL` in its SQL. `setPendingDeletion`/`clearPendingDeletion`/`clearAllPendingDeletions` are single-column `UPDATE`s that do **not** bump `updatedAt` (Q2, resolved — confirmed; see spec R5 AC2) | Filtering in Kotlin after the query (easy to forget in one Flow); a separate "trash" table (copies data, so an Undo is no longer a no-op) | Room's invalidation tracker re-emits every observer of `recipe` on the column change, so no cross-screen code is needed (ARCHITECTURE C3 Boundary) | The selection entry row is untouched during the pending window, so Undo restores selection membership exactly (F6 relies on this) |
| AD5 — CFC-4 strategy for the persistence layer | (a) All SQL values are bound Room parameters: no `@RawQuery`, no string-built SQL, anywhere in `data/db`. That keeps every SQLite/Room exception message down to schema identifiers and static SQL text. (b) F1's own thrown type, `PersistenceException`, has a message built only from enum category + constant operation name + cause class simple-name. It does **not** chain the cause (`cause = null`); it copies the cause's `stackTrace` frames so debuggability survives without the cause's message. (c) Every F1-authored catch site (repository, migrations, thumbnail path) converts to `PersistenceException` or a `ThumbnailOutcome.NoImage`. (d) `PersistenceErrorHygieneTest` forces each failure class against sentinel content and asserts the whole throwable graph is sentinel-free | Wrapping every Room-generated DAO method in a hand-written guard (doubles the DAO surface; `Flow` wrappers add a second protected-abstract layer); chaining causes as-is (a future library message change would leak) | Makes the Contract clause true by construction (content never reaches SQL text or messages) and proves it by test. The re-wrap rule applies at every site where F1 code catches | Raw SQLite exceptions from a directly collected DAO `Flow` still reach callers unwrapped, but they are content-free by (a) and covered by (d). A Room upgrade that changes messages is caught by (d) |
| AD6 — DI container shape (spec Decision Point) | `AppContainer` is a plain class whose primary constructor takes its already-built dependencies. `AppContainer.production(app)` builds the production graph. `PantryApplication.container` is `by lazy` (synchronized) | Hilt/Dagger/Koin (spec Ask First; ARCHITECTURE Technology Choices) | Constructor injection written by hand (R2 AC3). Tests build it from `createInMemory` (R2 AC4) | Composables reach it through `(LocalContext.current.applicationContext as PantryApplication).container`. Later ViewModel factories read it from `APPLICATION_KEY` (no F1 dependency added) |
| AD7 — Primary keys | Auto-generated `Long` ids for `Recipe`, `RecipeIngredient`, `ShoppingList`, `ShoppingListItem`. Natural keys where the model is one-per-parent: `ShoppingListSelectionEntry.recipeId`, `RetailerAssistSession.shoppingListId` (each a genuine FK to its one parent). `NutritionCacheEntry.canonicalKey` is also a natural (non-generated) key, but standalone — it has no parent FK, and is keyed by the domain lookup value itself rather than a one-per-parent relationship | UUID strings (no sync or merge use case; `Long` joins are cheaper) | The two FK natural keys rule out duplicate selection entries or sessions structurally; `canonicalKey` is natural because it *is* the lookup key C6 queries by, not for a structural-uniqueness reason | Selection and session rows have foreign keys with `ON DELETE CASCADE`. Hard-deleting a recipe removes its selection entry (F10 owns the drop notice). `NutritionCacheEntry` rows are never cascaded — nothing references them by FK |
| AD8 — Backup control (spec Decision Point) | `android:allowBackup="false"` only, as ARCHITECTURE commits | Adding `dataExtractionRules.xml` as well (Q4, considered and declined) | Covers every API level from 26 to 35 for cloud backup | **Accepted (Q4, resolved):** on API 31+ with `targetSdk` ≥ 31, `allowBackup="false"` does not disable device-to-device transfer, so Pantry data can move during the user's own phone setup. Accepted as-is: this is the user's own D2D transfer, not the "public conveyance" ARCHITECTURE Q1's off-device claim guards against, and it avoids a second backup-control surface for a single-developer project |
| AD9 — Test runner (spec Decision Point) | JUnit 4 on AGP's default unit-test runner, with `RobolectricTestRunner`, `kotlin.test` assertions and `kotlinx-coroutines-test` | JUnit Platform + vintage engine now (a runner change with no F1 consumer) | Robolectric's runner is JUnit 4. ARCHITECTURE puts JUnit 5 on domain engines, and F1 has none | F3 adds `useJUnitPlatform()` + `junit-vintage-engine`. F1's tests run unchanged under vintage |
| AD10 — Migration fixture representation (spec Decision Point, `[DEF-01]`) | Programmatic inserts: a Kotlin `FixtureV1` object writes rows with `SupportSQLiteDatabase.insert(table, CONFLICT_ABORT, ContentValues)` against the raw v1 schema that `MigrationTestHelper.createDatabase(name, 1)` produces | A SQL script asset (not type-checked; drifts silently) | Fixture values sit next to the assertions that read them back | Each future version adds `FixtureVn` only if it needs new columns seeded; `FixtureV1` stays the oldest-version fixture |
| AD14 — Nutrition cache measurement basis (Q3, resolved) | `NutritionCacheEntry` gets a new non-null `basisUnit: NutritionBasis` column (`PER_100G` or `PER_100ML`, stored as `name` via a `Converters` entry, the same pattern as `QuantityDimension`). The four nutrient columns are renamed basis-neutral — `energyKcal`, `proteinG`, `fatG`, `carbohydrateG` (each `Double?`, unchanged nullability) — since their old `...Per100g` names would misdescribe a fluid ingredient stored per 100 mL | Keeping the `...Per100g`-suffixed names and adding a separate implicit "assume g unless fluid" convention (undiscoverable from the schema alone); two parallel column sets, one per basis (doubles nullable columns, and a lookup would need to know which set to read before reading it) | Solid/mass-measured ingredients need a per-100g figure and fluids need per-100mL; a single un-discriminated column set silently conflates the two. A discriminator column keeps one set of nutrient columns and makes the basis an explicit, queryable fact per row, which C6 (F9/F16) reads before presenting or converting a figure | `basisUnit` has no default — the caller (C6) must supply it at upsert time, same as every other required `NutritionCacheEntry` field. Changing which basis an existing cached key uses is an upsert, not a migration |
| AD15 — JDK 17 provisioning (Q5, resolved) | Document JDK 17 as the one build prerequisite in a new root `README.md`; `jvmToolchain(17)` resolves against whatever JDK Gradle finds (`JAVA_HOME` / toolchain auto-detection), with no auto-download plugin | The `org.gradle.toolchains.foojay-resolver-convention` settings plugin (auto-provisions a matching JDK if none is found) | Not in ARCHITECTURE's Technology Choices (spec Ask First); a solo developer's own dev machine (their IDE/Android Studio install) already has JDK 17, per SCOPE's no-CI/no-team constraint there is no provisioning-less build agent this needs to serve; one documented prerequisite is simpler than one more Gradle plugin. **Note (reconciling with DR1):** *this* devcontainer has no JDK 17/SDK today (DR1) — that's a statement about the current sandbox, not the developer's actual build machine, which DR1's own mitigation already assumes ("building happens on a machine with JDK 17 + SDK 35"); AD15 documents what that machine needs, DR1 tracks the risk that *this* container isn't it | R1 AC1's "without hand-configured state" means no repo-local hand-edits (no `local.properties` SDK path, no manual config) — a system JDK is ordinary build tooling a developer already has, not hand-configured *project* state |
| AD11 — Thumbnail pipeline placement | `data/thumbnail/ThumbnailProcessor` (bytes → JPEG bytes, pure platform-graphics logic) and `ThumbnailStore` (file I/O under `filesDir/thumbnails/`), composed by `data/db/RecipeRepository`. One entry point takes a `ByteArray` and is source-agnostic | A per-source path; decoding inside a DAO | The spec needs one path for import and picker bytes alike. Keeping file I/O out of DAOs keeps DAOs row-only (spec Always Do) | Hard deletes and thumbnail changes go through `RecipeRepository`. The raw DAO ops for them are documented as repository-only (DR3) |
| AD12 — Thumbnail parameters | Longest edge ≤ **512 px**, JPEG quality **85**. Two-pass `BitmapFactory` decode: `inJustDecodeBounds`, then a power-of-two `inSampleSize` that keeps the longest edge ≥ 512, then an exact `createScaledBitmap`. EXIF orientation read with platform `android.media.ExifInterface(InputStream)` and applied before scaling. Written atomically (`.tmp` then `renameTo`) as `thumbnails/<uuid>.jpg` **[ASSUMPTION — 512/85 values]** | `androidx.exifinterface` (a new dependency, spec Ask First); storing originals; `BLOB` | Satisfies ARCHITECTURE Thumbnail storage and bounds-aware decode. Resolves spec `[SEAL-02]` (EXIF) with no new dependency. Re-encoding strips EXIF metadata | Fixed small per-recipe footprint (ARCHITECTURE R13) |
| AD13 — Destructive migration | The builder calls `.addMigrations(*ALL_MIGRATIONS)` and never calls any `fallbackToDestructiveMigration*` variant | — | Spec Never Do; ARCHITECTURE R9 | A missing migration or a downgrade throws `IllegalStateException` from Room on open. The error hygiene test exercises this path |

## Component Design

### FC1 — Build configuration

**Responsibility:** Produce debug and release APKs reproducibly from a clean checkout with pinned platform levels.

**Location:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `app/build.gradle.kts`, `README.md`

**Key contents:**
- `settings.gradle.kts`: `pluginManagement`/`dependencyResolutionManagement` repositories (`google()`, `mavenCentral()`, `gradlePluginPortal()`), `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, `include(":app")`, `rootProject.name = "pantry"`.
- Root `build.gradle.kts`: `alias(libs.plugins.x) apply false` for AGP application, Kotlin Android, Kotlin Compose compiler, KSP and Room.
- `app/build.gradle.kts`:
  - `namespace = "ie.pantry"`, `applicationId = "ie.pantry"`, `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`.
  - `compileOptions` source/target 17 and `kotlin { jvmToolchain(17) }`.
  - `buildFeatures.compose = true`.
  - `buildTypes.release { isMinifyEnabled = false; isShrinkResources = false }` (the Kotlin DSL spelling of `minifyEnabled false`), with no `proguardFiles`.
  - `room { schemaDirectory("$projectDir/schemas") }`.
  - `sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")` so `MigrationTestHelper` finds `1.json` under Robolectric **[ASSUMPTION; see DR4]**.
  - `testOptions.unitTests.isIncludeAndroidResources = true`.
  - `tasks.withType<Test> { maxHeapSize = "1g" }`, which bounds the thumbnail memory test (FC7).
  - Every dependency is referenced as `libs.<alias>`.
- `gradle.properties`: `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official`, `org.gradle.jvmargs=-Xmx2g`. This file isn't listed in the spec's New Files but AGP needs it.
- `README.md`: states the one build prerequisite — JDK 17 installed (`JAVA_HOME` pointing at it, or on `PATH`) — and the standard build/test commands from the spec's Commands section (AD15, Q5 resolved).

### FC2 — App shell

**Responsibility:** Provide the installable launch target that owns the container.

**Location:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/{strings,themes}.xml`, `app/src/main/java/ie/pantry/PantryApplication.kt`, `app/src/main/java/ie/pantry/ui/{MainActivity,PlaceholderScreen}.kt`, `app/src/main/java/ie/pantry/ui/theme/Theme.kt`

**Key classes/functions:**
- `PantryApplication : Application` exposes `val container: AppContainer by lazy { AppContainer.production(this) }`.
- `MainActivity : ComponentActivity` calls `setContent { PantryTheme { PlaceholderScreen() } }`.
- `PantryTheme(content)` is a top-level `@Composable` wrapping `MaterialTheme` with `lightColorScheme()`/`darkColorScheme()` chosen by `isSystemInDarkTheme()`, with dynamic colour on API 31+.
- `PlaceholderScreen()` is a top-level `@Composable`: a `Scaffold` with an app-name `Text` and nothing else.
- Manifest:
  - `<application android:name=".PantryApplication" android:allowBackup="false" android:label="@string/app_name" android:theme="@style/Theme.Pantry">`.
  - One `<activity android:name=".ui.MainActivity" android:exported="true">` with the MAIN/LAUNCHER filter.
  - No `<uses-permission>` of any kind.
  - No `android:icon` (the platform default icon is used until F15).
  - `Theme.Pantry` has the platform parent `android:Theme.Material.Light.NoActionBar`, so no Material Components XML dependency is needed.

### FC3 — App container

**Responsibility:** Hold exactly one database, one of each DAO, and the repository, all wired by hand.

**Location:** `app/src/main/java/ie/pantry/di/AppContainer.kt`

**Key classes/functions:**
- `class AppContainer(val database: PantryDatabase, thumbnailStore: ThumbnailStore, thumbnailProcessor: ThumbnailProcessor)`: primary-constructor injection. Its `val` properties are read from the database once: `recipeDao`, `selectionEntryDao`, `shoppingListDao`, `retailerAssistDao`, `nutritionCacheDao`, plus `recipeRepository`.
- `AppContainer.production(app: Application, clock: Clock = Clock.systemUTC()): AppContainer` is a companion function using `PantryDatabase.create(app, clock)` and `ThumbnailStore(app.filesDir)`.

### FC4 — Schema

**Responsibility:** Declare the seven version-1 entities, their converters, and the database's construction rules.

**Location:** `app/src/main/java/ie/pantry/data/db/PantryDatabase.kt`, `Converters.kt`, `Migrations.kt`, `entity/{Recipe,RecipeIngredient,ShoppingListSelectionEntry,ShoppingList,ShoppingListItem,RetailerAssistSession,NutritionCacheEntry,QuantityDimension,NutritionBasis,SelectionSnapshot}.kt`, `relation/{RecipeWithIngredients,SelectionEntryWithRecipe,ShoppingListWithItems}.kt`

**Key classes/functions:**
- `@Database(entities = [Recipe::class, RecipeIngredient::class, ShoppingListSelectionEntry::class, ShoppingList::class, ShoppingListItem::class, RetailerAssistSession::class, NutritionCacheEntry::class], version = PantryDatabase.VERSION, exportSchema = true)`, `@TypeConverters(Converters::class)`, `abstract class PantryDatabase : RoomDatabase()`. It also holds:
  - `lateinit var clock: Clock` (private set; assigned by the factories)
  - the five abstract DAO getters
  - `companion { const val VERSION = 1; const val FILE_NAME = "pantry.db"; fun create(context, clock); fun createInMemory(context, clock) }`
- `val ALL_MIGRATIONS: Array<Migration> = emptyArray()` is a top-level `val` in `Migrations.kt`, used by both factories and the harness.
- `Converters`: `Instant?`⇄`Long?`, `QuantityDimension`⇄`String` (the enum `name`), `NutritionBasis`⇄`String` (the enum `name`, same pattern), `List<Long>`⇄`String` (comma-joined; `""` = empty list), `List<SelectionSnapshot>`⇄`String` (`recipeId:servings` pairs joined by `|`; servings empty when null).

### FC5 — DAOs

**Responsibility:** Expose row-level reads as `Flow` and writes that enforce the `updatedAt` bump and the soft-delete filter.

**Location:** `app/src/main/java/ie/pantry/data/db/dao/{RecipeDao,SelectionEntryDao,ShoppingListDao,RetailerAssistDao,NutritionCacheDao}.kt`

**Key classes/functions:** see Interfaces I3–I7. `RecipeDao` is the only DAO that writes `recipe` or `recipe_ingredient`. There is no separate ingredient DAO, so every child write goes through a bumping wrapper. No DAO contains merging, scaling, nutrition or seasonality logic (R3 AC6), which is checked at code review against this list.

### FC6 — Persistence error surface

**Responsibility:** Give F1's catch sites one content-free exception type.

**Location:** `app/src/main/java/ie/pantry/data/db/PersistenceException.kt`

**Key classes/functions:**
- `PersistenceException`
- `PersistenceException.Category`
- `persistenceFailure(operation, cause)`, a top-level factory that maps SQLite exception types to categories, copies stack frames and logs. `Category.IO` and `Category.UNKNOWN` are reserved mappings with no F1 code path that produces them today (F1's own thumbnail I/O failures degrade to `ThumbnailOutcome.NoImage` before reaching this factory, per FC7/FC8) — they exist so a later feature's own catch site can route a real `IOException` or an unrecognised exception type through the same content-free factory rather than defining a second one. `PersistenceErrorHygieneTest`'s coverage (Testing Strategy) is scoped to the categories F1 itself can force.

### FC7 — Thumbnail pipeline

**Responsibility:** Turn untrusted-size image bytes into a stored, downsampled JPEG file or an explicit no-image outcome.

**Location:** `app/src/main/java/ie/pantry/data/thumbnail/{ThumbnailProcessor,ThumbnailStore,ThumbnailOutcome}.kt`

**Key classes/functions:**
- `ThumbnailProcessor.process(bytes): ByteArray?` decodes, orients, downsamples and encodes. It returns `null` on decode failure and never throws for bad input.
- `computeSampleSize(width, height, maxEdge): Int` is an internal top-level function.
- `ThumbnailStore.write(jpeg): String` returns the relative path and throws `IOException`. `ThumbnailStore.delete(relativePath): Boolean` and `ThumbnailStore.resolve(relativePath): File`.
- `sealed interface ThumbnailOutcome { data class Stored(relativePath); data class NoImage(reason: NoImageReason) }`.

### FC8 — Recipe repository

**Responsibility:** Compose recipe row writes with thumbnail file writes, replacement and deletion so that files and rows never diverge.

**Location:** `app/src/main/java/ie/pantry/data/db/RecipeRepository.kt`

**Key classes/functions:** `saveNewRecipe`, `replaceThumbnail`, `removeThumbnail`, `deleteRecipe` (I8).

### FC9 — Verification harness and migration procedure

**Responsibility:** Prove schema, migration, durability and error-hygiene properties, and document the migration procedure.

**Location:** `app/src/test/java/ie/pantry/**` (see File Structure), `app/src/test/resources/robolectric.properties`, `docs/migrations.md`

**Key classes/functions:**
- `MigrationHarnessTest` and `FixtureV1`.
- `RestartDurabilityTest`.
- `PersistenceErrorHygieneTest`.
- Test support: `MutableClock`, `TestDatabases`, `FlowRecorder`, `Sentinels`, `ImageFixtures`.

## Data Models

IDs: DM1 `Recipe`, DM2 `RecipeIngredient`, DM3 `ShoppingListSelectionEntry`, DM4 `ShoppingList`, DM5 `ShoppingListItem`, DM6 `RetailerAssistSession`, DM7 `NutritionCacheEntry`. Table names are snake_case (`recipe`, `recipe_ingredient`, `shopping_list_selection_entry`, `shopping_list`, `shopping_list_item`, `retailer_assist_session`, `nutrition_cache_entry`). Every optional column is nullable with **no** `@ColumnInfo(defaultValue)` (R3 AC3). Timestamps are `Instant` stored as epoch-millis `INTEGER`.

| Model | Field | Type | Constraints | Description |
|-------|-------|------|-------------|-------------|
| Recipe | id | Long | PK, autoGenerate | Row id |
| Recipe | title | String | Required, non-null | Recipe title (content: never in messages) |
| Recipe | method | String | Required, non-null | Method text |
| Recipe | yieldServings | Int? | Nullable, no default — absent when unstated | Stated yield; G3's 1× baseline is applied by C5, not stored |
| Recipe | cookingTimeMinutes | Int? | Nullable, no default — absent when unstated (ARCHITECTURE Q3) | Cooking time |
| Recipe | thumbnailPath | String? | Nullable, no default — absent = explicit no-image | Path relative to `filesDir`, e.g. `thumbnails/<uuid>.jpg` |
| Recipe | sourceUrl | String? | Nullable, no default — absent for manual entry | Import source |
| Recipe | fetchedAt | Instant? | Nullable, no default — absent for manual entry | Import time |
| Recipe | updatedAt | Instant | Non-null. The Kotlin default is `Instant.EPOCH`, overwritten by every `RecipeDao` write; there is no column default | Invalidation key (ARCHITECTURE R8) |
| Recipe | pendingDeletionAt | Instant? | Nullable, no default | Soft-delete marker (R3 AC4) |
| RecipeIngredient | id | Long | PK, autoGenerate | Row id |
| RecipeIngredient | recipeId | Long | FK → `recipe.id` ON DELETE CASCADE; indexed | Parent |
| RecipeIngredient | position | Int | Non-null, ≥ 0 (Q1, resolved — approved; a field beyond ARCHITECTURE's Data Models table) | Line order within the recipe |
| RecipeIngredient | rawText | String | Required, non-null | Original line (content) |
| RecipeIngredient | canonicalKey | String | Required, non-null | C4-derived key |
| RecipeIngredient | quantityAmount | Double? | Nullable, no default — null iff `dimension == UNQUANTIFIED` | Parsed amount |
| RecipeIngredient | quantityUnit | String? | Nullable, no default — null iff `UNQUANTIFIED` | Unit symbol as C4 emits it |
| RecipeIngredient | dimension | QuantityDimension | Non-null enum `MASS`/`VOLUME`/`COUNT`/`UNQUANTIFIED`, stored as `name` | Unit dimension or explicit unquantified |
| ShoppingListSelectionEntry | recipeId | Long | PK; FK → `recipe.id` ON DELETE CASCADE | Chosen recipe (one entry per recipe, AD7) |
| ShoppingListSelectionEntry | servings | Int? | Nullable, no default — absent means "no yield and no user value; C5 applies 1×" (Q6, resolved — kept as `Int?`; a non-1× scale factor for a yield-less recipe is deferred to F10, no schema change here) | Servings to shop for |
| ShoppingList | id | Long | PK, autoGenerate | Row id |
| ShoppingList | createdAt | Instant | Non-null | Generation time |
| ShoppingList | sourceSelection | List\<SelectionSnapshot\> | Non-null (may be empty); converter-encoded TEXT; **not** a foreign key, since the list is a snapshot that must survive recipe deletion. **[ASSUMPTION]** | "The selections it was generated from" as `(recipeId: Long, servings: Int?)` pairs |
| ShoppingListItem | id | Long | PK, autoGenerate | Row id |
| ShoppingListItem | shoppingListId | Long | FK → `shopping_list.id` ON DELETE CASCADE; indexed | Parent |
| ShoppingListItem | canonicalKey | String | Required | Merge key |
| ShoppingListItem | displayName | String | Required | Shown text |
| ShoppingListItem | quantityAmount | Double? | Nullable, no default — null iff `UNQUANTIFIED` | Merged amount |
| ShoppingListItem | quantityUnit | String? | Nullable, no default — null iff `UNQUANTIFIED` | Unit symbol |
| ShoppingListItem | dimension | QuantityDimension | Non-null; `UNQUANTIFIED` is the unquantified flag | Dimension |
| ShoppingListItem | section | String? | Nullable, no default — absent = unknown section (terminal bucket) | Tesco section |
| ShoppingListItem | walkOrderIndex | Int | Non-null, ≥ 0 | Stable walk order |
| RetailerAssistSession | shoppingListId | Long | PK; FK → `shopping_list.id` ON DELETE CASCADE | Current list |
| RetailerAssistSession | positionIndex | Int | Non-null, ≥ 0 | Pointer |
| RetailerAssistSession | skippedItemIds | List\<Long\> | Non-null; `""` = none skipped (an empty set, not absence) | Skipped `ShoppingListItem` ids |
| NutritionCacheEntry | canonicalKey | String | PK | Lookup key |
| NutritionCacheEntry | basisUnit | NutritionBasis | Non-null enum `PER_100G`/`PER_100ML`, stored as `name` (Q3, resolved, AD14) | Measurement basis the four nutrient columns below are expressed in — fluids use `PER_100ML`, solid/mass-measured ingredients use `PER_100G` |
| NutritionCacheEntry | energyKcal | Double? | Nullable, no default (Q3, resolved, AD14 — renamed from `energyKcalPer100g`) | Energy, expressed per the row's `basisUnit` (100 g or 100 mL) |
| NutritionCacheEntry | proteinG / fatG / carbohydrateG | Double? | Nullable, no default (Q3, resolved, AD14 — renamed from the `...Per100g`-suffixed fields) | Macros for the detail breakdown, expressed per the row's `basisUnit` |
| NutritionCacheEntry | sourceAttribution | String | Required | Credit text |
| NutritionCacheEntry | licenceTag | String | Required, e.g. `ODbL-1.0` | Licence |
| NutritionCacheEntry | fetchedAt | Instant | Non-null | Fetch time |
| NutritionCacheEntry | estimatedConversion | Boolean | Non-null | Estimated-conversion flag |

> Kotlin types are non-nullable by default. A field is `Type?` only where ARCHITECTURE's Data Models table marks it optional or where absence is a modelled state (`UNQUANTIFIED`, unknown section).

**Relationships:**
- `Recipe` has many `RecipeIngredient` (CASCADE). `RecipeWithIngredients` is `@Embedded val recipe: Recipe` + `@Relation(parentColumn = "id", entityColumn = "recipeId") val ingredients: List<RecipeIngredient>` — Room's `@Relation` has no `ORDER BY` parameter and no supported way to substitute a custom ordered query for it (both `observeRecipe`/`findRecipe` in I3 are `abstract`, fully Room-generated, with no method body to inject one into), so `orderedIngredients` is a computed property, `val orderedIngredients: List<RecipeIngredient> get() = ingredients.sortedBy { it.position }`, sorting the raw `@Relation` result in Kotlin — the standard, documented pattern for ordering a Room `@Relation` collection.
- `Recipe` has zero or one `ShoppingListSelectionEntry` (CASCADE). `SelectionEntryWithRecipe` is a JOIN projection.
- `ShoppingList` has many `ShoppingListItem` (CASCADE) and zero or one `RetailerAssistSession` (CASCADE). `ShoppingListWithItems` is `@Embedded` + `@Relation`.
- `NutritionCacheEntry` is standalone, keyed by canonical key.

**Persistence:**
- Room 2.x (KSP). File `pantry.db` in the default database directory.
- Room turns on `PRAGMA foreign_keys` because entities declare foreign keys; `SchemaShapeTest` asserts this.
- The schema JSON is committed. Thumbnails are files under `filesDir/thumbnails/`, never a `BLOB`.

## Interfaces

**I1 — Database factories**

```kotlin
companion object {
    const val VERSION = 1
    const val FILE_NAME = "pantry.db"

    /** On-disk database. Never calls fallbackToDestructiveMigration*. */
    fun create(context: Context, clock: Clock): PantryDatabase =
        Room.databaseBuilder(context.applicationContext, PantryDatabase::class.java, FILE_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build().also { it.clock = clock }

    /** In-memory database for Robolectric tests and container tests (R2 AC4). */
    fun createInMemory(context: Context, clock: Clock): PantryDatabase
}
```

Contracts: the returned instance's `clock` is always initialised. Opening is lazy, happening at the first query. A downgrade or a missing migration throws Room's `IllegalStateException` on first access and never wipes data.

**I2 — Container**

```kotlin
class AppContainer(
    val database: PantryDatabase,
    thumbnailStore: ThumbnailStore,
    thumbnailProcessor: ThumbnailProcessor,
) {
    val recipeDao: RecipeDao = database.recipeDao()
    val selectionEntryDao: SelectionEntryDao = database.selectionEntryDao()
    val shoppingListDao: ShoppingListDao = database.shoppingListDao()
    val retailerAssistDao: RetailerAssistDao = database.retailerAssistDao()
    val nutritionCacheDao: NutritionCacheDao = database.nutritionCacheDao()
    val recipeRepository = RecipeRepository(database, recipeDao, thumbnailStore, thumbnailProcessor)
    companion object { fun production(app: Application, clock: Clock = Clock.systemUTC()): AppContainer }
}
```

Contracts: construction does no I/O. Each property is read once, so there is one instance per container. `PantryApplication.container` is `lazy(SYNCHRONIZED)`, so two reads return the same container (R2 AC2).

**I3 — RecipeDao**

```kotlin
@Dao
abstract class RecipeDao(private val db: PantryDatabase) {
    // Observable reads — all filter `pendingDeletionAt IS NULL`
    @Query("SELECT * FROM recipe WHERE pendingDeletionAt IS NULL ORDER BY title COLLATE NOCASE, id")
    abstract fun observeCatalogue(): Flow<List<Recipe>>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id AND pendingDeletionAt IS NULL")
    abstract fun observeRecipe(id: Long): Flow<RecipeWithIngredients?>   // emits null while pending/absent

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id AND pendingDeletionAt IS NULL")
    abstract suspend fun findRecipe(id: Long): RecipeWithIngredients?

    // Bumping writes (the only public writes to recipe / recipe_ingredient)
    /** @return new recipe id. Sets updatedAt = clock.instant(); assigns recipeId to each ingredient. */
    @Transaction open suspend fun insertRecipeWithIngredients(recipe: Recipe, ingredients: List<RecipeIngredient>): Long
    /** Writes content fields; preserves stored thumbnailPath and pendingDeletionAt; bumps updatedAt. */
    @Transaction open suspend fun updateRecipe(recipe: Recipe)
    @Transaction open suspend fun insertIngredient(ingredient: RecipeIngredient): Long   // bumps parent
    @Transaction open suspend fun updateIngredient(ingredient: RecipeIngredient)         // bumps parent
    @Transaction open suspend fun deleteIngredient(ingredientId: Long)                   // bumps parent

    // Soft delete (no updatedAt bump — spec R5 AC2, Q2 confirmed)
    @Query("UPDATE recipe SET pendingDeletionAt = :at WHERE id = :id")
    abstract suspend fun setPendingDeletion(id: Long, at: Instant): Int
    @Query("UPDATE recipe SET pendingDeletionAt = NULL WHERE id = :id")
    abstract suspend fun clearPendingDeletion(id: Long): Int
    /** Recovery sweep (R3 AC7): unconditionally clears every leftover marker. @return rows cleared. */
    @Query("UPDATE recipe SET pendingDeletionAt = NULL WHERE pendingDeletionAt IS NOT NULL")
    abstract suspend fun clearAllPendingDeletions(): Int

    // Repository-only (FC8) — thumbnail reference and hard delete. KDoc marks them repository-only (DR3).
    @Transaction open suspend fun setThumbnailPath(id: Long, relativePath: String?)   // bumps updatedAt
    @Query("SELECT thumbnailPath FROM recipe WHERE id = :id") abstract suspend fun thumbnailPathOf(id: Long): String?
    @Query("DELETE FROM recipe WHERE id = :id") abstract suspend fun deleteRecipeRow(id: Long): Int

    // Raw ops — reachable only through the wrappers above (AD2)
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun rawInsertRecipe(r: Recipe): Long
    @Update protected abstract suspend fun rawUpdateRecipe(r: Recipe): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun rawInsertIngredients(i: List<RecipeIngredient>): List<Long>
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun rawInsertIngredient(i: RecipeIngredient): Long
    @Update protected abstract suspend fun rawUpdateIngredient(i: RecipeIngredient): Int
    @Query("SELECT recipeId FROM recipe_ingredient WHERE id = :id") protected abstract suspend fun parentOf(id: Long): Long?
    @Query("DELETE FROM recipe_ingredient WHERE id = :id") protected abstract suspend fun rawDeleteIngredient(id: Long): Int
    @Query("SELECT * FROM recipe WHERE id = :id") protected abstract suspend fun rawFind(id: Long): Recipe?
    @Query("UPDATE recipe SET updatedAt = :at WHERE id = :id") protected abstract suspend fun bump(id: Long, at: Instant): Int
}
```

Contracts:
- Every public write sets `updatedAt` to `db.clock.instant()` whatever value the caller passed (R5 AC5).
- A child write bumps its parent inside the same transaction, deletion included (R5 AC3/AC4).
- `updateRecipe` on a missing id is a no-op that returns normally.
- `deleteIngredient` on a missing id is a no-op with no bump.
- Constraint violations propagate as Room/SQLite exceptions, which are content-free per AD5.
- `setPendingDeletion`/`clearPendingDeletion`/`clearAllPendingDeletions` touch only `pendingDeletionAt`.

**I4 — SelectionEntryDao**

```kotlin
@Dao interface SelectionEntryDao {
    // Projection: e.recipeId, e.servings, then every recipe column aliased `recipe_<column>`
    // (explicit alias list written out at implementation; no `*` so Room resolves each column).
    @Query("""SELECT e.recipeId, e.servings, r.id AS recipe_id, r.title AS recipe_title, /* … all recipe columns … */
              r.pendingDeletionAt AS recipe_pendingDeletionAt
              FROM shopping_list_selection_entry e JOIN recipe r ON r.id = e.recipeId
              WHERE r.pendingDeletionAt IS NULL ORDER BY e.recipeId""")
    fun observeSelection(): Flow<List<SelectionEntryWithRecipe>>
    @Upsert suspend fun upsert(entry: ShoppingListSelectionEntry)
    @Query("DELETE FROM shopping_list_selection_entry WHERE recipeId = :recipeId") suspend fun delete(recipeId: Long): Int
}
```

`SelectionEntryWithRecipe` is `@Embedded val entry: ShoppingListSelectionEntry` plus `@Embedded(prefix = "recipe_") val recipe: Recipe`. The alias list in the query matches that prefix. Display order is by `recipeId`: the entry's PK is its rowid, and F10 owns any user-facing ordering.

**I5 — ShoppingListDao**

```kotlin
@Dao abstract class ShoppingListDao {
    @Query("SELECT * FROM shopping_list ORDER BY createdAt DESC, id DESC") abstract fun observeLists(): Flow<List<ShoppingList>>
    @Transaction @Query("SELECT * FROM shopping_list WHERE id = :id") abstract fun observeList(id: Long): Flow<ShoppingListWithItems?>
    /** Inserts the list and its items atomically; @return list id. */
    @Transaction open suspend fun insertListWithItems(list: ShoppingList, items: List<ShoppingListItem>): Long
    @Update abstract suspend fun updateItem(item: ShoppingListItem): Int
    @Query("DELETE FROM shopping_list WHERE id = :id") abstract suspend fun deleteList(id: Long): Int
    @Insert protected abstract suspend fun rawInsertList(l: ShoppingList): Long
    @Insert protected abstract suspend fun rawInsertItems(i: List<ShoppingListItem>)
}
```

**I6 — RetailerAssistDao**

```kotlin
@Dao interface RetailerAssistDao {
    @Query("SELECT * FROM retailer_assist_session WHERE shoppingListId = :listId")
    fun observeSession(listId: Long): Flow<RetailerAssistSession?>
    @Upsert suspend fun upsert(session: RetailerAssistSession)
    @Query("DELETE FROM retailer_assist_session WHERE shoppingListId = :listId") suspend fun delete(listId: Long): Int
}
```

**I7 — NutritionCacheDao** (one-shot; not observed by a screen, so not a `Flow` per R4 AC1's list)

```kotlin
@Dao interface NutritionCacheDao {
    @Query("SELECT * FROM nutrition_cache_entry WHERE canonicalKey = :key") suspend fun find(key: String): NutritionCacheEntry?
    @Upsert suspend fun upsert(entry: NutritionCacheEntry)
}
```

Empty-result contract for all `Flow`s: a list query emits `emptyList()` and a single-row query emits `null`. Neither fails nor stays silent (R4 AC4).

**I8 — RecipeRepository**

```kotlin
class RecipeRepository(
    private val db: PantryDatabase,
    private val recipeDao: RecipeDao,
    private val store: ThumbnailStore,
    private val processor: ThumbnailProcessor,
) {
    data class SaveResult(val recipeId: Long, val thumbnail: ThumbnailOutcome)

    /**
     * Saves a recipe with its ingredients and, if [thumbnailBytes] is non-null, a thumbnail
     * processed through the single decode/downsample/re-encode path (source-agnostic: import
     * fetch or photo picker). A thumbnail failure never prevents the save.
     * @throws PersistenceException if the row write fails (any thumbnail file already written is deleted first).
     */
    suspend fun saveNewRecipe(recipe: Recipe, ingredients: List<RecipeIngredient>, thumbnailBytes: ByteArray?): SaveResult

    /** Writes the new file, points the row at it (bumping updatedAt), then deletes the old file.
     *  On a decode/write failure the existing thumbnail is left unchanged and NoImage is returned. [ASSUMPTION] */
    suspend fun replaceThumbnail(recipeId: Long, thumbnailBytes: ByteArray): ThumbnailOutcome

    /** Clears the reference (explicit no-image, bumps updatedAt) and deletes the file. */
    suspend fun removeThumbnail(recipeId: Long)

    /** Hard delete: row (cascading ingredients + selection entry) then its thumbnail file. F6 calls this at finalization. */
    suspend fun deleteRecipe(recipeId: Long)
}
```

Contracts:
- `saveNewRecipe` sets `thumbnailPath` from the outcome, overriding any caller value, and forces `pendingDeletionAt = null`.
- Its sequence is: process bytes, then store the file (off the transaction, on `Dispatchers.IO`), then `insertRecipeWithIngredients`.
- The `ThumbnailOutcome` is `Stored` or `NoImage(DECODE_FAILED | WRITE_FAILED | NO_SOURCE)`.
- If deleting an old or orphaned file fails, the failure is logged by category only and not thrown (DR6).
- Every `SQLiteException`/`IllegalStateException` caught here is rethrown via `persistenceFailure(operation, e)`.

**I9 — Thumbnail pipeline**

```kotlin
class ThumbnailProcessor(private val maxEdgePx: Int = 512, private val jpegQuality: Int = 85) {
    /** @return JPEG bytes with longest edge ≤ maxEdgePx, or null if [source] does not decode as an image.
     *  Never decodes at native resolution: bounds first, then inSampleSize. Never throws for bad input. */
    fun process(source: ByteArray): ByteArray?
}
internal fun computeSampleSize(width: Int, height: Int, maxEdgePx: Int): Int   // largest power of two keeping longest edge ≥ maxEdgePx

class ThumbnailStore(filesDir: File) {
    /** Atomically writes into `thumbnails/<uuid>.jpg`. @return path relative to filesDir. @throws IOException */
    fun write(jpeg: ByteArray): String
    /** @return true if the file no longer exists afterwards. Never throws. */
    fun delete(relativePath: String): Boolean
    fun resolve(relativePath: String): File
}
```

Contracts:
- `process` catches `OutOfMemoryError` from the sampled decode as a defensive degradation: it returns `null` and logs the category.
- `process` reads `ExifInterface` orientation from the source bytes and applies it with a `Matrix` before the final scale. Any EXIF read failure is treated as orientation-normal.
- `resolve`/`delete` both run the same canonical-path check that rejects any path escaping `thumbnails/`, but surface it per their own return contract: `resolve(): File` throws `IllegalArgumentException` (it has no non-throwing way to report failure); `delete(): Boolean` returns `false` instead of throwing (consistent with its "never throws" contract above) — the check is shared, the failure signal is not.

**I10 — Error type**

```kotlin
class PersistenceException(
    val category: Category,
    val operation: String,          // compile-time constant name, e.g. "saveNewRecipe"
    val causeType: String?,         // e.g. "SQLiteConstraintException" — simple class name only
) : RuntimeException("Persistence failure: category=$category operation=$operation cause=$causeType") {
    enum class Category { CONSTRAINT, DATABASE_OPEN, MIGRATION, IO, UNKNOWN }
}
internal fun persistenceFailure(operation: String, cause: Throwable): PersistenceException  // no cause chaining; copies stackTrace
```

## Error Handling

- **Strategy:** Exceptions. Row-level programming or database failures throw. Expected degradations use outcome types (`ThumbnailOutcome`, nullable returns for "not found", `Int` rows-affected counts). Kotlin has no checked exceptions, so every throwing public function lists what it throws in KDoc.
- **Custom exceptions / error types:**
  - `PersistenceException` (I10). F1 code constructs it only through `persistenceFailure`. Its message is assembled only from an enum, a constant and a class simple-name, so no argument, entity field or cause message can reach it (CFC-4 Contract).
  - `ThumbnailOutcome.NoImage(reason)` is a value, not an exception.
  - Migration objects added later must throw only `PersistenceException(MIGRATION, "migrate_<from>_<to>", …)`, and `docs/migrations.md` states this rule.

| Condition | Raised as | Caught where |
|---|---|---|
| FK/unique/PK conflict on a DAO write | `SQLiteConstraintException` (Room/SQLite; message has schema identifiers only, AD5) | Callers directly, or converted to `PersistenceException(CONSTRAINT)` inside `RecipeRepository` |
| DB open failure / downgrade / missing migration / identity-hash mismatch | `IllegalStateException` / `SQLiteException` from Room on first access | `RecipeRepository` → `PersistenceException(DATABASE_OPEN or MIGRATION)`; direct DAO callers receive Room's content-free exception |
| Source bytes not decodable | none (`process` returns `null`) | `RecipeRepository` → `NoImage(DECODE_FAILED)`; recipe still saves |
| Thumbnail file write failure | `IOException` from `ThumbnailStore.write` | `RecipeRepository` → `NoImage(WRITE_FAILED)`; recipe still saves |
| Row insert fails after file written | `SQLiteException` | `RecipeRepository` deletes the new file, then throws `PersistenceException` |
| Old-file delete fails on replace/delete | none (`delete` returns `false`) | Logged; no throw (DR6) |
| Path escapes `thumbnails/` (`resolve`) | `IllegalArgumentException("thumbnail path outside store")` | Not caught; programming error, constant message |
| Path escapes `thumbnails/` (`delete`) | none (`delete` returns `false`, per its "never throws" contract) | Logged; no throw |

- **Logging:**
  - `android.util.Log` with tag `"PantryDb"` / `"PantryThumb"`.
  - `Log.w` at each conversion and degradation site, with structured `key=value` fields `category`, `operation`, `causeType`, `reason` only.
  - Never log a cause's `message`, an entity field, a path under `thumbnails/`, or byte contents.
  - No logging on success paths.
- **Unhandled paths:** raw exceptions from a `Flow` collected directly from a DAO reach the collector (C9 in later features). They are content-free (AD5), and the owning features handle them under their own `[CFC-4]` obligations.
- **User-facing errors:** none in F1. F14 owns the surfaces and F6 the save-failure copy.

## Testing Strategy

- **Framework:**
  - JUnit 4 + Robolectric (`@RunWith(RobolectricTestRunner::class)`) with `kotlin.test` assertions (AD9).
  - `kotlinx-coroutines-test` `runTest`, using real-time `withTimeout` around `Flow` awaits, since Room emits on its own executor.
  - Robolectric runs at `sdk=35` via `app/src/test/resources/robolectric.properties` **[ASSUMPTION: the pinned Robolectric supports SDK 35; otherwise 34]** — falling back to 34 changes nothing R1/R3's ACs assert (`compileSdk`/`targetSdk` stay 35 in the manifest and build script; only the *test-runtime* Android framework jar shifts) and no test in this design asserts SDK-35-specific framework behavior, so the fallback is test-tooling-only and doesn't reopen any AC.
  - Thumbnail tests use `@GraphicsMode(GraphicsMode.Mode.NATIVE)` for real decode/encode.
- **Test location:** `app/src/test/java/ie/pantry/...`, mirroring main.
- **Mocking approach:** no mocking library. The seams are real in-memory/on-disk Room databases, `MutableClock` (a `Clock` subclass with `advanceBy(Duration)`), and `ThumbnailStore` pointed at a temp dir, or at a regular *file* to force write failure.
- **Fixtures / test data** (`app/src/test/java/ie/pantry/testutil/`):
  - `TestDatabases.inMemory(clock)` creates a fresh database per test (`@Before`) and closes it in `@After`.
  - `TestDatabases.onDisk(name, clock)` uses the Robolectric app context's database path.
  - `Sentinels` provides content-bearing values (`TITLE = "SENTINEL-TITLE-9c1e Grandma's Stew"`, `INGREDIENT = "SENTINEL-ING-9c1e 400 g tomatoes"`, `URL = "https://sentinel-9c1e.example/recipe"`) and `Throwable.assertNoSentinel()`. That helper walks `message`, `localizedMessage`, `toString()`, `stackTraceToString()`, every `cause` and every `suppressed`.
  - `FlowRecorder<T>(flow, scope)` collects into a `Channel` on `Dispatchers.Default`, with `awaitNext(timeout = 5.seconds)` and `awaitUntil(predicate)`.
  - `ImageFixtures`:
    - `jpeg(w, h, exifOrientation)` and `png(w, h)` are small encoded images generated in-test.
    - `pngBomb(20_000, 20_000)` is a streamed grayscale PNG whose IDAT is `Deflater`-compressed zero rows: about 0.4 MB on disk, and 1.6 GB as ARGB_8888 if decoded naively.
    - `garbage()` is random non-image bytes.
  - `FixtureV1` holds the migration fixture (AD10).
- **Naming convention:** backtick-quoted descriptive names, e.g. `` `catalogue flow re-includes row when pendingDeletionAt cleared`() ``.
- **Coverage expectations:**
  - Every spec GIVEN/WHEN/THEN maps to a test below or to a named manual check.
  - Every public DAO/repository/processor/store function has at least one happy-path and one error/edge test.
  - Every recipe-backed `Flow` (`observeCatalogue`, `observeRecipe`, `observeSelection`) has the seed → set → absent → clear → reappear test.

| Test file | Covers |
|---|---|
| `data/db/SchemaShapeTest.kt` | R3 AC1 (parses `app/schemas/ie.pantry.data.db.PantryDatabase/1.json` with `org.json`: `version == 1`, exactly seven table names); R3 AC2 (`PRAGMA foreign_key_list` + `index_list` on both child tables, and on the selection/session FKs); R3 AC3/AC4 (`PRAGMA table_info`: each optional column in DM1–DM7 has `notnull = 0` and `dflt_value IS NULL`); `PRAGMA foreign_keys == 1` |
| `data/db/RecipeDaoTest.kt` | R4 AC2 (update → second emission); R4 AC4 (empty → `emptyList()`/`null`); R4 AC5 for `observeCatalogue`/`observeRecipe`; R5 AC1, AC3–AC5 (MutableClock: recipe update, ingredient insert/update/delete, caller-supplied stale `updatedAt` overwritten); R5 AC2 (`updatedAt` captured before/after each of the three `pendingDeletionAt`-only write paths — set, clear/restore, sweep — and confirmed unchanged); R3 AC7 (seed with `pendingDeletionAt` set → `clearAllPendingDeletions()` → cleared, returns count); R3 AC6 (every entity reachable, spread across DAO tests); `orderedIngredients` ordering (Q1): ingredients inserted out of `position` order, and a later update that changes one row's `position`, both assert `observeRecipe`'s `RecipeWithIngredients.orderedIngredients` reads back sorted by `position` |
| `data/db/SelectionEntryDaoTest.kt` | R4 AC3 (insert/update/delete each emit); R4 AC5 for `observeSelection` (entry hidden while recipe pending, back on clear); CASCADE on hard delete |
| `data/db/ShoppingListDaoTest.kt` | R4 AC3 for `observeLists`/`observeList`: each emits after `insertListWithItems` (insert), `updateItem` (update), and `deleteList` (delete); atomic `insertListWithItems`; snapshot converter round trip |
| `data/db/RetailerAssistDaoTest.kt` | R4 AC3 for `observeSession`: emits after `upsert` (insert and update — `@Upsert` covers both) and `delete` |
| `data/db/NutritionCacheDaoTest.kt` | Reachability, upsert/find, null nutrient fields round-trip as null, `basisUnit` round-trips for both `PER_100G` and `PER_100ML` (Q3/AD14) |
| `data/db/ConvertersTest.kt` | Each converter round-trips (including `NutritionBasis`), empty lists, and null servings in snapshots |
| `data/db/MigrationHarnessTest.kt` | R6 AC1–AC3: `createDatabase(NAME, 1)`; insert `FixtureV1`; close; assert `ALL_MIGRATIONS.isEmpty()` **and** `PantryDatabase.VERSION == 1` (the explicit "no migration pending" report); `if (VERSION > 1) runMigrationsAndValidate(NAME, VERSION, true, *ALL_MIGRATIONS)`; then open through `PantryDatabase.create`-equivalent builder on the same file (Room identity-hash validation) and assert every fixture row's field values via DAOs, including absent optionals. It asserts row contents, never only "opened" |
| `data/db/RestartDurabilityTest.kt` | R7 AC1–AC3: on-disk DB, one row per entity (with some optionals absent), `close()`, new instance on the same file, field-by-field equality, absent stays `null`. No-network guards: Robolectric `ShadowConnectivityManager` has no active network; `ProxySelector.setDefault` is replaced by a selector that fails the test on any `select()`; asserts `okhttp3.OkHttpClient` is not loadable |
| `data/db/PersistenceErrorHygieneTest.kt` | R3 AC8 / CFC-4. Forced failures, each against `Sentinels` content, each followed by `assertNoSentinel()`: (1) FK violation, inserting an ingredient with `rawText = INGREDIENT` for a missing recipe through `RecipeDao`; (2) PK conflict, re-inserting a recipe with an existing id and `title = TITLE`; (3) repository save after `db.close()`, which yields `PersistenceException`, and the test also asserts the category and a null `cause`; (4) downgrade, where a file stamped `user_version = 2` is opened by the v1 database, giving Room's `IllegalStateException` directly and `PersistenceException(MIGRATION or DATABASE_OPEN)` via the repository; (5) thumbnail write failure, where no exception escapes and the outcome is `NoImage(WRITE_FAILED)`; (6) FK violation through `ShoppingListDao`, inserting a `ShoppingListItem` with `displayName = INGREDIENT` for a missing `shoppingListId`, demonstrating AD5(a)'s hygiene scheme is uniform across DAOs and not only exercised through `RecipeDao` |
| `data/db/RecipeRepositoryTest.kt` | R8 AC1 (import-style PNG bytes and picker-style EXIF-rotated JPEG bytes each give a stored file that starts `FF D8 FF` with bounds ≤ 512, and the row references it); R8 AC3 (garbage bytes give `NoImage(DECODE_FAILED)`, row saved with `thumbnailPath == null`; store rooted at a regular file gives `NoImage(WRITE_FAILED)`, row saved); R8 AC4 (replace: new file exists, old file gone, `updatedAt` bumped); `deleteRecipe` removes row + file; rollback deletes the new file when the insert fails |
| `data/thumbnail/ThumbnailProcessorTest.kt` | `computeSampleSize` table cases; R8 AC2 (`pngBomb` processes to a ≤ 512 px JPEG within a 1 GB test heap, which is impossible without sampling); EXIF orientation 6 gives swapped output aspect |
| `data/thumbnail/ThumbnailStoreTest.kt` | Atomic write (no `.tmp` left), delete idempotence, path-escape rejection |
| `di/AppContainerTest.kt` | R2 AC2 (`ApplicationProvider.getApplicationContext<PantryApplication>().container` read twice gives the same instance; DAO getters identical across reads); R2 AC4 (container built on `createInMemory` with no network) |
| `ManifestPolicyTest.kt` | R1 AC5 for the debug-merged manifest under Robolectric: `ApplicationInfo.flags` lacks `FLAG_ALLOW_BACKUP`; `PackageInfo.requestedPermissions` contains no `android.permission.INTERNET` and no other `android.permission.*` entry (the merged manifest legitimately carries androidx.core's `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` self-signature permission, so an empty set is not asserted) |

Manual checks (no CI, per SCOPE):
- R1 AC1: run `./gradlew assembleDebug assembleRelease` from a fresh clone.
- R1 AC2/AC4: inspect `app/build.gradle.kts`.
- R1 AC3: `grep -nE '"[^"]+:[^"]+:[0-9][^"]*"' build.gradle.kts app/build.gradle.kts settings.gradle.kts` must print nothing.
- R1 AC5 for the release variant: inspect `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`.
- R2 AC1: `installDebug` and launch.
- R2 AC3: `grep -niE 'hilt|dagger|koin' gradle/libs.versions.toml app/build.gradle.kts` must print nothing — the actual claim is classpath absence, a build-configuration fact no reflection over one already-hand-written class (`AppContainer`) can establish.
- R3 AC5: run `git status --porcelain app/schemas` after a build and expect empty output.
- R3 AC8 / CFC-4 (AD5(a)): `grep -rn '@RawQuery' app/src/main/java/ie/pantry/data/db` must print nothing — the no-`@RawQuery` half of AD5(a)'s content-hygiene premise. The other half (no string-concatenated SQL) is checked at code review, the same as R3 AC6's DAO-content check below.
- R6 AC4: `grep -rn fallbackToDestructiveMigration app/src` must print nothing.
- R6 AC5: inspect `docs/migrations.md` for the numbered procedure already specified above (bump version; commit schema; add an explicit `Migration`; extend the harness fixture; run tests; never `fallbackToDestructiveMigration`).

## File Structure

```
/workspace/                                   (repository root; currently only blueprint/, specs/, .sdd/, .devcontainer/, CLAUDE.md, .gitignore)
├── README.md                                   — NEW: JDK 17 prerequisite, build/test commands (AD15)
├── settings.gradle.kts                        — NEW: single :app, repositories
├── build.gradle.kts                           — NEW: plugin aliases, apply false
├── gradle.properties                          — NEW: AndroidX / JVM args
├── gradlew, gradlew.bat                       — NEW: wrapper scripts
├── gradle/
│   ├── libs.versions.toml                     — NEW: every version + alias
│   └── wrapper/gradle-wrapper.{properties,jar} — NEW: pinned Gradle distribution
├── docs/migrations.md                         — NEW (FC9): migration procedure (R6 AC5)
├── .gitignore                                 — UNCHANGED (verified: does not ignore app/schemas/)
└── app/
    ├── build.gradle.kts                       — NEW (FC1)
    ├── schemas/ie.pantry.data.db.PantryDatabase/1.json   — NEW, generated then committed (FC4)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml            — NEW (FC2)
        │   ├── res/values/strings.xml         — NEW (FC2): app_name
        │   ├── res/values/themes.xml          — NEW (FC2): Theme.Pantry
        │   └── java/ie/pantry/
        │       ├── PantryApplication.kt       — NEW (FC2)
        │       ├── di/AppContainer.kt         — NEW (FC3)
        │       ├── ui/MainActivity.kt         — NEW (FC2)
        │       ├── ui/PlaceholderScreen.kt    — NEW (FC2)
        │       ├── ui/theme/Theme.kt          — NEW (FC2)
        │       ├── data/db/PantryDatabase.kt  — NEW (FC4)
        │       ├── data/db/Converters.kt      — NEW (FC4)
        │       ├── data/db/Migrations.kt      — NEW (FC4): ALL_MIGRATIONS
        │       ├── data/db/entity/Recipe.kt, RecipeIngredient.kt, ShoppingListSelectionEntry.kt,
        │       │   ShoppingList.kt, ShoppingListItem.kt, RetailerAssistSession.kt,
        │       │   NutritionCacheEntry.kt, QuantityDimension.kt, NutritionBasis.kt,
        │       │   SelectionSnapshot.kt — NEW (FC4)
        │       ├── data/db/relation/RecipeWithIngredients.kt, SelectionEntryWithRecipe.kt,
        │       │   ShoppingListWithItems.kt   — NEW (FC4)
        │       ├── data/db/dao/RecipeDao.kt, SelectionEntryDao.kt, ShoppingListDao.kt,
        │       │   RetailerAssistDao.kt, NutritionCacheDao.kt — NEW (FC5)
        │       ├── data/db/PersistenceException.kt — NEW (FC6)
        │       ├── data/db/RecipeRepository.kt — NEW (FC8)
        │       ├── data/thumbnail/ThumbnailProcessor.kt — NEW (FC7)
        │       ├── data/thumbnail/ThumbnailStore.kt     — NEW (FC7)
        │       └── data/thumbnail/ThumbnailOutcome.kt   — NEW (FC7)
        └── test/
            ├── resources/robolectric.properties — NEW (FC9): sdk=35
            └── java/ie/pantry/
                ├── ManifestPolicyTest.kt       — NEW
                ├── di/AppContainerTest.kt      — NEW
                ├── data/db/{SchemaShapeTest, ConvertersTest, RecipeDaoTest, SelectionEntryDaoTest,
                │   ShoppingListDaoTest, RetailerAssistDaoTest, NutritionCacheDaoTest,
                │   MigrationHarnessTest, FixtureV1, RestartDurabilityTest,
                │   PersistenceErrorHygieneTest, RecipeRepositoryTest}.kt — NEW
                ├── data/thumbnail/{ThumbnailProcessorTest, ThumbnailStoreTest}.kt — NEW
                └── testutil/{MutableClock, TestDatabases, Sentinels, FlowRecorder, ImageFixtures}.kt — NEW
```

CFC-4 verification artifact: F1 is a CFC-4 participant but is not named in CFC-4's Enforcement prose, which names F4 and F15. F1's per-feature test is `app/src/test/java/ie/pantry/data/db/PersistenceErrorHygieneTest.kt` (with `testutil/Sentinels.kt`). F15's pre-submission sweep re-checks the same sites.

`docs/migrations.md` contents (R6 AC5), as numbered steps:
1. Bump `PantryDatabase.VERSION`.
2. Build, and commit the new `app/schemas/.../<n>.json` without hand-editing any schema JSON.
3. Add an explicit `Migration(n-1, n)` to `ALL_MIGRATIONS`, throwing only `PersistenceException(MIGRATION, …)` and never interpolating row data into SQL.
4. Extend the harness: seed any new columns in a new `FixtureVn`, and assert the migrated `FixtureV1` rows field by field.
5. Run `./gradlew testDebugUnitTest`.
6. Never add `fallbackToDestructiveMigration*`.

## Dependencies

Every coordinate lives in `gradle/libs.versions.toml`. The versions below are **[ASSUMPTION]** candidates, a known mutually-compatible set. Implementation re-pins them to the newest mutually-compatible stable set, and R1 AC1 (clean `assembleDebug assembleRelease`) is the proof (DR1).

| Package | Purpose |
|---------|---------|
| Gradle wrapper 8.11.1 | Build tool |
| `com.android.application` 8.7.3 (AGP) | Android build; supports `compileSdk` 35 |
| `org.jetbrains.kotlin.android` 2.1.0 | Kotlin |
| `org.jetbrains.kotlin.plugin.compose` 2.1.0 | Compose compiler (Kotlin 2.x) |
| `com.google.devtools.ksp` 2.1.0-1.0.29 | Room code generation. Annotation processing for Room, not DI; R2 AC3 is unaffected |
| `androidx.room` (Gradle plugin) 2.6.1 | Schema directory + test wiring |
| `androidx.room:room-runtime`, `room-ktx` 2.6.1; `room-compiler` (ksp) 2.6.1 | Database, `Flow`/suspend support |
| `androidx.room:room-testing` 2.6.1 (test) | `MigrationTestHelper` |
| `androidx.compose:compose-bom` 2024.12.01; `ui`, `material3` | Shell UI |
| `androidx.activity:activity-compose` 1.9.3 | `setContent` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` 1.9.0 (test) | `runTest` |
| `org.robolectric:robolectric` 4.14.1 (test) | JVM Android runtime, native graphics |
| `junit:junit` 4.13.2 (test) | Runner (AD9) |
| `androidx.test:core` 1.6.1 (test) | `ApplicationProvider`, `InstrumentationRegistry` for `MigrationTestHelper` |
| `org.jetbrains.kotlin:kotlin-test-junit` (Kotlin version) (test) | `kotlin.test` assertions |

No HTTP client, no DI framework, no `androidx.exifinterface`, no linter, no `org.gradle.toolchains.foojay-resolver-convention` (Q5, resolved — declined in favor of a documented JDK 17 prerequisite, AD15). Everything above falls under ARCHITECTURE Technology Choices (Room, Compose/Material 3, AndroidX, Coroutines, Robolectric/JUnit/kotlin.test), so nothing triggers spec Ask First.

## Integration Points

The repository has no application code, so every row describes how **later** features connect to F1's surface.

| Existing Module | Direction | Change Required | Details |
|-----------------|-----------|-----------------|---------|
| `.gitignore` | — | No | Already ignores `build/`, `.gradle/`, `local.properties`, `.idea/`. Does not match `app/schemas/`, so `1.json` is tracked (spec Modified Files) |
| `ie.pantry.di.AppContainer` | Called by (F2–F16) | Yes, additively, in later features | Later features add their own `val`s (e.g. F4's gateway) through constructor injection. F1's properties are stable |
| `ie.pantry.data.db.dao.RecipeDao` | Called by F6, F7, F9 | No | F6 calls `setPendingDeletion`/`clearPendingDeletion`/`clearAllPendingDeletions` (the last at every foreground entry). F7 observes `observeCatalogue`/`observeRecipe`. Load-bearing: any new recipe-backed query must keep the `pendingDeletionAt IS NULL` filter (DR5) |
| `ie.pantry.data.db.RecipeRepository` | Called by F6 | No | The only path for thumbnail bytes (import-draft bytes from F5 via F6, picker bytes from F6) and for hard delete at finalization |
| `SelectionEntryDao`, `ShoppingListDao`, `RetailerAssistDao`, `NutritionCacheDao` | Called by F10, F11, F12, F9/F16 | No | Row-level only; business rules stay in C4/C5/C6/C10 |
| `ie.pantry.data.db.PersistenceException` | Caught by F6, F14 | No | Mapped to plain-text UI errors by category, never by message |
| `ALL_MIGRATIONS` / `MigrationHarnessTest` | Extended by any feature changing the schema; run by F15 | Yes, per `docs/migrations.md` | F15 re-runs the harness before submission |

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| DR1 | The candidate AGP/Kotlin/KSP/Room/Compose/Robolectric versions don't combine, or the devcontainer has no JDK 17/Android SDK (none is installed today) and its firewall blocks Maven/Google | High | Med | Step 1 of the Implementation Sequence proves the catalogue with `assembleDebug assembleRelease` before any feature code. Versions are re-pinned there. Building happens on a machine with JDK 17 + SDK 35, a documented prerequisite (Q5/AD15 — no auto-provisioning) |
| DR2 | Room codegen rejects `protected abstract` DAO methods (AD2) | Med | High | Verified at the first DAO compile (Sequence step 3). Fallback: `RecipeDao`'s wrapper methods perform the raw insert/update/delete directly against `db.openHelper.writableDatabase` — Room's own underlying `SupportSQLiteDatabase` (the same object Room's own generated DAO code writes through), reached via `RoomDatabase.openHelper`, both long-standing public Room APIs — instead of routing through a second `@Dao`-annotated type. This creates no new public surface at all: no second DAO, no new getter on `PantryDatabase` or `AppContainer` (a public `val`, per I2) for later feature code to reach. `RecipeDao`'s wrappers remain the only code that ever touches `recipe`/`recipe_ingredient` at the row level, so the fallback keeps the same no-public-reach guarantee AD2's primary design has and doesn't reopen the bypass R5 AC5 and ARCHITECTURE's "every write... bumps `updatedAt`" invariant depend on. `RecipeDaoTest`'s bump tests stay unchanged (they assert on the wrapper's observable behavior, not its internal write mechanism) |
| DR3 | A later feature calls `RecipeDao.setThumbnailPath`/`deleteRecipeRow` directly, orphaning or losing files (single module, so `internal` gives no protection) | Med | Med | Repository-only KDoc on those methods. `RecipeRepository` is the documented entry point in `AppContainer`. F15's sweep and each feature's code review check call sites |
| DR4 | `MigrationTestHelper` under Robolectric can't find `1.json` via test-source-set assets | Med | High | Proven in Sequence step 2, before any DAO work. Fallback: add `schemas` to the `debug` source set's assets (ships schema JSON in debug builds only, harmless), then re-run the harness |
| DR5 | A later recipe-backed query forgets the `pendingDeletionAt IS NULL` filter and a soft-deleted recipe leaks into a screen | Med | Med | The filter lives in SQL on every F1 query (AD4). Every recipe-backed `Flow` has the seed/set/clear/reappear test, and `docs/migrations.md` plus RecipeDao's KDoc state the rule for new queries |
| DR6 | An old thumbnail file survives because its delete fails after the row was updated | Low | Low | Delete runs only after the new row reference commits. Failure is logged by category. Files are private and small (ARCHITECTURE R13). Orphan GC is a named Non-Goal |
| DR7 | Robolectric native graphics can't decode the PNG bomb or read EXIF on the developer's host, so R8 AC2 can't run | Med | Med | `computeSampleSize` is also covered as a pure function. The thumbnail tests never use `assume*`, so an unsupported host fails loudly instead of skipping. Robolectric native graphics supports Linux/macOS/Windows x86_64 and macOS arm64 **[ASSUMPTION]**, which covers the expected dev hosts |
| DR8 | Room's default corruption handler deletes a corrupt database file, a wipe path outside migrations | Low | High | Accepted for F1: a corrupt SQLite file is unreadable anyway. Recorded here so F15's release checklist can reconsider a non-deleting `SupportSQLiteOpenHelper.Callback.onCorruption` |
| DR9 | Robolectric downloads its `android-all` jar on first run, which conflicts with a firewalled environment and could be misread as the "no network" test failing | Med | Low | The no-network guards (ProxySelector/connectivity) are installed inside the test method, after Robolectric bootstrap. The first run happens online once, and the jar is cached |

## Implementation Sequence

1. **FC1 + FC2** (build, manifest, placeholder UI). This retires DR1 first. Exit: `./gradlew assembleDebug assembleRelease` and `installDebug` succeed, `ManifestPolicyTest` passes, and the no-literal-version grep is empty.
2. **FC4 schema + FC9 harness skeleton** (entities, converters, `PantryDatabase`, `ALL_MIGRATIONS`, `1.json` committed, `SchemaShapeTest`, `MigrationHarnessTest` with `FixtureV1`). Entity shape is the costliest thing to get wrong (spec RK1), and DR4 is the least certain tooling. Q1/Q3/Q6 are resolved above, so `1.json` reflects `RecipeIngredient.position` (Q1) and `NutritionCacheEntry.basisUnit`/the renamed nutrient columns (Q3) from the first commit.
3. **FC5 DAOs** (with `MutableClock`, `FlowRecorder`), plus `RecipeDaoTest` and the four other DAO tests. `RecipeDao` goes first because it retires DR2. The other four DAOs can be built in parallel once `RecipeDao` compiles.
4. **FC6 error surface.** It can run in parallel with step 3. `PersistenceErrorHygieneTest` cases (1), (2) and (4) land here.
5. **FC7 thumbnail pipeline** (`ThumbnailProcessorTest`, `ThumbnailStoreTest`). This can start in parallel with steps 2–4, since it only needs step 1. Doing it early surfaces DR7.
6. **FC8 `RecipeRepository`** (needs 3, 4, 5), with `RecipeRepositoryTest` and hygiene cases (3) and (5).
7. **FC3 container + `PantryApplication` wiring** (needs 3 and 6), with `AppContainerTest`.
8. **FC9 closeout:** `RestartDurabilityTest`, `docs/migrations.md`, and a full `./gradlew testDebugUnitTest lintDebug` run. Finally confirm `git status --porcelain app/schemas` is clean after a rebuild.

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: `RecipeIngredient.position` (non-null `Int`) isn't in ARCHITECTURE's Data Models table, but ingredient lines have an order that an edit can change, and ordering by autoincrement id breaks on a reordering edit. Spec Ask First requires approval to add a field. Approve `position`, or order by `id` and accept that reordering later means a migration?
  - **Resolution:** Approved — `RecipeIngredient.position` (non-null `Int`) added; see the Data Models table (DM2) and `RecipeWithIngredients.orderedIngredients`, which already sorted by it.
- [x] Q2: R5 AC1 says "any field" written bumps `updatedAt`. ARCHITECTURE C3 says an Undo restores the row "with every field … untouched". This design exempts `pendingDeletionAt` writes (set, clear, sweep) from the bump (AD4). Confirm the exemption, or bump on soft-delete writes too? A bump only causes a spurious cache invalidation.
  - **Resolution:** Confirmed — exemption stands. spec.md R5 AC1 amended (hash now current) to explicitly exclude `pendingDeletionAt`-only writes from the `updatedAt` bump. ARCHITECTURE.md's blanket "every write" phrasing left as-is per user decision — accepted as a pre-existing wording imprecision, not backported.
- [x] Q3: `NutritionCacheEntry`'s "per-canonical-key nutrition values" isn't enumerated upstream. This design stores nullable `energyKcalPer100g`, `proteinGPer100g`, `fatGPer100g`, `carbohydrateGPer100g`. Is that the set F9/F16's detail breakdown needs? Changing it later means a migration.
  - **Resolution:** Per-100g is right for solid/mass-measured ingredients, but fluids need per-100mL — a single un-discriminated column set would silently conflate the two. Added a `basisUnit: NutritionBasis` (`PER_100G`/`PER_100ML`) discriminator column and renamed the four nutrient columns basis-neutral (`energyKcal`, `proteinG`, `fatG`, `carbohydrateG`) so their names don't misdescribe a fluid entry. See AD14 and the updated Data Models table.
- [x] Q4: On API 31+ (`targetSdk` 35), `android:allowBackup="false"` disables cloud backup but not device-to-device transfer, so Pantry data (including the ODbL cache) can move to the user's own new phone during setup. Keep `allowBackup="false"` alone as ARCHITECTURE commits and accept D2D (arguably desirable for the user, and not "public conveyance" under ARCHITECTURE Q1)? Or also add `dataExtractionRules.xml` excluding `device-transfer`?
  - **Resolution:** Accepted as-is — no `dataExtractionRules.xml`. `AD8` kept as originally drafted (`allowBackup="false"` only); see AD8's updated Consequences for the accepted-risk rationale.
- [x] Q5: R1 asks for a clean-checkout build "without hand-configured state". `jvmToolchain(17)` needs a local JDK 17 unless the `org.gradle.toolchains.foojay-resolver-convention` settings plugin auto-provisions one. That plugin isn't in ARCHITECTURE's Technology Choices (spec Ask First). Add it, or document "JDK 17 installed" as the one prerequisite?
  - **Resolution:** Document JDK 17 as a prerequisite (new root `README.md`) rather than add the foojay-resolver-convention plugin. See AD15.
- [x] Q6: SCOPE G2 lets the user change the "1× scale factor" of a recipe with no stated yield. `ShoppingListSelectionEntry.servings: Int?` (null = 1× baseline) can't hold a non-1× factor for such a recipe. Keep `servings: Int?` and let F10 migrate if needed? Or store a scale factor (e.g. `scale: Double?`) alongside it now?
  - **Resolution:** Keep `servings: Int?` as drafted; no schema change. Deferred to F10, which owns selection-entry shape decisions.

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
| 1    | 2026-09-23 | 2     | 0           | 14        | 0        | 2      | tags=d0u0c2 |

### Sealed dispositions

- `[SEAL-01]` **FC5's "no merging/scaling/nutrition/seasonality logic in…** (pass 1, accepted-as-risk) — Defense: synthesizer-judged — a structural-absence claim like this has no reliable grep pattern (there's no fixed string to search for); code review is already the named mechanism, consistent with how this class of check works elsewhere in the project.
- `[SEAL-02]` **`position`/`walkOrderIndex`/`positionIndex`'s "≥ 0"…** (pass 1, accepted-as-risk) — Defense: not required by any spec acceptance criterion (the reviewer's own finding notes this); a caller bug producing a negative value is a low-severity edge case outside this feature's committed ACs.

### Deferred dispositions

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `31f9c2e829bea3db`
- **Hash basis:** v2