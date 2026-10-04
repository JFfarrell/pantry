# Feature: Ingredient Normalisation & Quantity Engine

**PLAN feature identifier:** `F3`

## Objective

**From PLAN F3:** `blueprint/03_PLAN.md` → Feature Breakdown → `### F3: Ingredient Normalisation & Quantity Engine` (Description and Acceptance Criteria), plus `## Cross-Feature Contracts` → CFC-1, CFC-2, CFC-4; component C4 per `blueprint/02_ARCHITECTURE.md` → `### C4 — Ingredient Normalisation & Quantity Engine`.

F3 fixes the one parse function and its typed outcomes before any caller exists (F5, F6 and F11 build on it later). It also makes F2's shipped alias, staples, seasonality and section keys agree with the normalisation rule, so the first real consumer does not miss keys silently.

## Requirements

**Terms.** A *line* is one free-text ingredient line (for example `2 large onions, finely chopped`). Its *quantity segment* is the leading amount, unit and container words (`2 x 400g tins`). Its *name phrase* is what remains, case-folded, with preparation words not yet removed. The *canonical key* is the engine's output string in F2's canonical surface form (lowercase, trimmed, single-spaced; F2 Q3). A *dimension* is one of F1's `QuantityDimension` values: `MASS`, `VOLUME`, `COUNT`, `UNQUANTIFIED`. A *count unit* is the noun a `COUNT` quantity counts (`clove`, `tin`, or none for a bare `3 eggs`). *Key-absent* and *unquantified* are the two explicit-absence outcomes this feature produces.

### R1: One parse entry point, total and deterministic

As the developer building F6 and F11, I want exactly one function that turns a line into a canonical key and a quantity, and that never throws, so that every input route gets identical results and no route writes its own parsing.

**Acceptance Criteria:**

- GIVEN the non-private declarations of F3's package
  WHEN they are inspected
  THEN exactly one of them accepts raw line text and returns both a canonical-key result and a quantity result, and no other non-private declaration accepts raw line text (the key rule and quantity parser cannot be called separately on a raw line).

- GIVEN the shipped alias table and each line in the shared parse-fidelity corpus (R8)
  WHEN each line is parsed twice, by two separately constructed engine instances over that same table
  THEN the two results are equal (`==`) for every line, so identical line text yields an identical canonical key and quantity whatever route supplied it. [CFC-1]

- GIVEN the empty string, a whitespace-only line, a 10,000-character line, a line of control characters, a line of emoji, and the numeric edge cases `1/0 g`, `0 g`, `-2 eggs`, `1e999 g`, `99999999999999999999 g` and `½½ tsp`
  WHEN each is parsed
  THEN each returns a typed result and none throws. Each case that has no ingredient name left gives key-absent (R2), and each case with no confidently recognised amount gives unquantified (R4).

### R2: Canonical key: rule first, alias table for exceptions

As the developer building F8, F9 and F11, I want each line reduced to one canonical key by a predictable rule, with F2's alias table overriding only where the rule is wrong, so that the same ingredient gets the same key and an alias entry is always a deliberate, reviewable exception (ARCHITECTURE Q2, R6).

**[ASSUMPTION]** The rule and the order the table is consulted in are set by Q1 and Q2. The criteria below use these defaults. The rule case-folds, folds diacritics (`é` becomes `e`), turns hyphens into spaces, and collapses whitespace. It removes the quantity segment, anything after the first comma, and anything in parentheses. It then removes preparation and size words from a fixed list (Q2), and singularises the last word by regular English plural rules (Q2). The alias table (`ie.pantry.data.reference.AliasTable`, exact-match `lookup(variant): Lookup<String>`) is consulted first with the name phrase, then with the rule's output. The first hit wins, and the rule's output is used if both miss.

**Acceptance Criteria:**

- GIVEN an empty fixture alias table
  WHEN `  Red  ONION `, `2 large onions, finely chopped`, `500g carrots`, `4 ripe tomatoes`, `a handful of cherries` and `Tomato purée` are parsed
  THEN the canonical keys are `red onion`, `onion`, `carrot`, `tomato`, `cherry` and `tomato puree`, so regular plurals, preparation words, size words, case, whitespace and diacritics are all handled by the rule alone.

- GIVEN an empty fixture alias table
  WHEN `self-raising flour`, `200g couscous` and `1 bunch asparagus` are parsed
  THEN the keys are `self raising flour`, `couscous` and `asparagus`: hyphens become spaces, and a word ending in `-us` or `-ss` is not singularised.

- GIVEN a fixture alias table holding exactly one irregular plural (`bay leaves` → `bay leaf`) and one brand-name-to-generic mapping (`philadelphia` → `cream cheese`)
  WHEN `2 bay leaves` and `200g Philadelphia` are parsed, and then `3 carrots`
  THEN the first two lines get `bay leaf` and `cream cheese` from the table, and `3 carrots` gets `carrot` with the table unchanged and no entry for it. A unit test asserts this by counting the table lookups that hit.

- GIVEN the shipped alias table
  WHEN `1 eggplant`, `2 zucchini`, `a handful of cilantro`, `200g shrimp`, `3 scallions` and `300g brussel sprouts` are parsed
  THEN the keys are `aubergine`, `courgette`, `coriander`, `prawn`, `spring onion` and `brussels sprout`.

- GIVEN any line from which no name text remains after the quantity segment and preparation words are removed (for example `2 tbsp`, `500 g`, the empty string or `, chopped`)
  WHEN it is parsed
  THEN the key result is the explicit key-absent value, which a unit test asserts is distinct from a key of `""` and from any derived key. The key result has no string field. [CFC-2]

- GIVEN the lines `400 g tomatoes` and `2 tbsp tomato purée`
  WHEN both are parsed
  THEN their canonical keys differ (`tomato` and `tomato puree`), so the rule never folds a product into its base ingredient (ARCHITECTURE R6).

### R3: Reconciliation with F2's shipped datasets

As the developer, I want F3's own tests to check that every key F2 shipped is one the engine can actually produce, so that a curated entry the rule can never reach fails a test instead of showing up as *unmatched*, *unknown* or a terminal-bucket entry in the app (F2 Q3 resolution, F2 `[SEAL-02]`, F2 RK1).

**Acceptance Criteria:**

- GIVEN the shipped `staples.json`, `seasonality.json`, `section_order.json` and `aliases.json`, loaded under Robolectric through F2's loader
  WHEN every staples key, seasonality key, seasonality substitution, section-mapping key and alias target K is parsed as the line `K`, against the shipped alias table
  THEN each gives canonical key K (each is a fixed point of the engine). On failure the test lists the offending keys. **Known violations under the assumed rule:** `oats`, `baked beans` and `chopped tomato` (Q3). These must be fixed by the decision Q3 records before F3 is accepted. No allow-list is permitted.

- GIVEN every variant V in the shipped alias table
  WHEN the test checks which name phrases and rule outputs the engine consults the table with
  THEN V is reachable: the line `V` hits V at one of the lookup stages R2 defines. A variant the engine can never pass to the table fails the test.

### R4: Typed, dimensioned quantity that is never invented

As a Pantry user reading a merged shopping list, I want every amount on it to come from a number the recipe actually stated, and anything the app could not read to be marked unquantified, so that a mis-parse never shows me a confident wrong figure (SCOPE G3; ARCHITECTURE R2).

**[ASSUMPTION]** The recognised units and their conversion factors are set by Q4, and ranges, multipliers and parenthetical sizes by Q6. The criteria below use those defaults.

**Acceptance Criteria:**

- GIVEN the lines `500g plain flour`, `1 kg potatoes`, `8 oz cheddar`, `1 lb beef mince`
  WHEN each is parsed
  THEN each quantity has dimension `MASS` and amounts 500 g, 1 kg, 8 oz and 1 lb respectively.

- GIVEN the lines `150ml milk`, `1 l chicken stock`, `2 tbsp olive oil`, `1½ tsp ground cumin`, `1 cup rice`
  WHEN each is parsed
  THEN each quantity has dimension `VOLUME` and amounts 150 ml, 1 l, 2 tbsp, 1.5 tsp and 1 cup respectively. `½`, `1/2` and `1 1/2` forms parse to 0.5, 0.5 and 1.5.

- GIVEN the lines `3 eggs`, `3 cloves garlic, crushed`, `1 tin chickpeas`
  WHEN each is parsed
  THEN each quantity has dimension `COUNT`: 3 with no count unit, 3 `clove` and 1 `tin` respectively.

- GIVEN the lines `2 x 400g tins chopped tomatoes` and `1 (400 g) tin chickpeas`
  WHEN each is parsed
  THEN the quantities are 800 g and 400 g, both `MASS`: the product of two confidently recognised numbers is used, and a stated size in parentheses takes precedence over its container count (Q6).

- GIVEN the lines `salt, to taste`, `a pinch of salt`, `a handful of basil`, `some tomatoes`, `2-3 carrots`, `1,5 kg flour`, `0 g sugar`, `a knob of butter` and `3 splodges of ketchup`
  WHEN each is parsed
  THEN each quantity is the explicit unquantified value. The unquantified value carries no numeric property and no unit, so a number cannot be put in it, and a unit test asserts it is not equal to a `MASS` 0 g, a `COUNT` 0 or any other measured quantity. No code path in F3 produces a measured quantity whose amount was not read from the line (a range, an unknown unit, a zero or negative amount, or an unreadable number all give unquantified). [CFC-2]

- GIVEN each measured quantity the engine can produce, one per recognised unit
  WHEN it is mapped to F1's `RecipeIngredient` column triple (`quantityAmount: Double?`, `quantityUnit: String?`, `dimension: QuantityDimension`) and back
  THEN the round-trip gives an equal quantity, each recognised unit has one stable, unique `quantityUnit` token, and unquantified maps to (`null`, `null`, `UNQUANTIFIED`), matching the column contract F1 already documents ("Null iff dimension is UNQUANTIFIED"). F3 writes no storage; this is the value mapping F6 will persist through.

### R5: Within-dimension unit conversion

As the developer building F9 and F11, I want to convert a quantity to another unit of the same dimension and never across dimensions, so that combining or displaying amounts never needs a density estimate (ARCHITECTURE C4 Boundary).

**Acceptance Criteria:**

- GIVEN measured quantities of 1 kg, 1 lb, 1 oz, 1 l, 1 tbsp, 1 tsp and 1 cup
  WHEN each is converted to its dimension's base unit (grams for `MASS`, millilitres for `VOLUME`)
  THEN the results are 1000 g, 453.59237 g, 28.349523125 g, 1000 ml, 15 ml, 5 ml and 250 ml (Q4 factors), within an absolute tolerance of 1e-9.

- GIVEN a `MASS` quantity and a `VOLUME` target unit, a `COUNT` quantity and a `MASS` target unit, and a `COUNT` 3 `clove` quantity and a `bulb` target unit
  WHEN each conversion is requested
  THEN each returns a typed not-convertible outcome with no amount, and none throws.

- GIVEN an unquantified value
  WHEN conversion to any unit is requested
  THEN the result is the typed not-convertible outcome, never a measured quantity.

### R6: Scaling by a servings ratio

As a Pantry user cooking a recipe for more or fewer people than it serves, I want its quantities scaled in proportion, with unquantified lines left as they are, so that the list reflects what I am actually cooking (SCOPE G3).

**Acceptance Criteria:**

- GIVEN 400 g, 2 tbsp and a `COUNT` of 2 eggs
  WHEN each is scaled by the ratio 1.5
  THEN the results are 600 g, 3 tbsp and 3 eggs, each in the same dimension and unit, with no rounding applied by F3 (presentation rounding belongs to F11).

- GIVEN an unquantified value
  WHEN it is scaled by any valid ratio
  THEN the result is the same unquantified value.

- GIVEN any quantity
  WHEN it is scaled by a ratio of 0, a negative ratio, `NaN` or an infinite ratio
  THEN the result is a typed invalid-ratio outcome with no amount, and none throws. Working out the ratio (servings ÷ yield, and the 1× baseline where a recipe gives no yield) is F11's job (PLAN F11), not F3's.

### R7: Merge-compatibility predicate and merge

As a Pantry user, I want the same ingredient from several recipes combined into one list entry only where the amounts are physically comparable, so that 1 kg and 500 g of potatoes show as 1.5 kg while tomatoes and tomato purée stay separate (SCOPE G3).

**Acceptance Criteria:**

- GIVEN two parsed lines `1 kg potatoes` and `500 g potatoes`
  WHEN the predicate is asked whether they may merge, and they are then merged
  THEN the predicate answers true, and the merged quantity is `MASS` and equal to 1.5 kg (1500 g within 1e-9).

- GIVEN the pairs (`400 g tomatoes`, `2 tbsp tomato purée`) with key and dimension both different, (`400 g tomatoes`, `100 g tomato purée`) with only the key different, and (`200 g onions`, `2 onions`) with only the dimension different
  WHEN the predicate is asked for each pair
  THEN it answers false for all three, and asking for a merge of any of them returns a typed not-mergeable outcome with no amount, not an exception.

- GIVEN (`3 cloves garlic`, `1 bulb garlic`), the same key and dimension `COUNT` but different count units
  WHEN the predicate is asked
  THEN it answers false (Q5).

- GIVEN (`salt, to taste`, `a pinch of salt`), the same key and both unquantified
  WHEN the predicate is asked and they are merged
  THEN it answers true, and the merged result is the unquantified value with no number (Q7).

- GIVEN (`200 g salt`, `salt, to taste`), the same key with one measured and one unquantified
  WHEN the predicate is asked
  THEN it answers false.

- GIVEN any pair in which either line is key-absent
  WHEN the predicate is asked
  THEN it answers false, even when both lines are key-absent.

- GIVEN any two mergeable quantities a and b
  WHEN they are merged as (a, b) and as (b, a)
  THEN the two results are equal.

### R8: Shared parse-fidelity corpus

As the developer of F5, F6 and F11, I want one importable corpus of real ingredient lines with their expected keys and quantities, owned by F3, so that each later route can drive the same lines through its own path and prove its results are byte-identical (CFC-1 Enforcement).

**Acceptance Criteria:**

- GIVEN the corpus fixture file and its test-side loader
  WHEN the corpus test runs under `./gradlew :app:testDebugUnitTest` (a local JVM test, no device)
  THEN the corpus holds at least 50 real lines hand-transcribed from real recipe web pages the developer selects independently of F5. Each entry records its line text, its source page, its origin `HAND_TRANSCRIBED`, and a developer-authored expected canonical key (or key-absent) and expected quantity (dimension, amount, unit and count unit, or unquantified). Every line parses against the shipped alias table to exactly its expected values. This corpus is the shared parse-fidelity fixture named by CFC-1. [CFC-1]

- GIVEN the corpus
  WHEN its entries are counted by expected outcome
  THEN at least one entry expects each of `MASS`, `VOLUME`, `COUNT` and unquantified, at least one expects a key resolved through the alias table, at least one is a non-ingredient line (such as a section header) or expects key-absent (Q10), and at least one has a fractional amount **[ASSUMPTION — coverage floor, Q10]**.

- GIVEN a test class in a package other than F3's (for example `ie.pantry.testutil`)
  WHEN it loads the corpus through the shared loader and iterates the entries
  THEN it gets every entry with its line text and expected values without depending on any F3 test class. The corpus is a reusable, importable asset that F6's and F11's CFC-1 tests drive their own routes with, not an internal detail of F3's tests.

- GIVEN a fixture copy of the corpus with one extra entry whose origin is `F5_HARVESTED`
  WHEN it is loaded through the same loader
  THEN the extra entry is loaded and filterable by origin with no Kotlin source change, so F5's commitment to add at least 20 harvested lines (PLAN F5's CFC-1 criterion) is a data edit to the fixture.

- GIVEN the corpus's expected values
  WHEN they are reviewed
  THEN they were written by the developer from reading each source line, not produced by running the engine and copying its output (confirmed at review; RK6).

### R9: No Android dependency

As the developer, I want the engine to be plain Kotlin, so that its correctness tests run in seconds on the JVM (ARCHITECTURE Technology Choices, App architecture).

**Acceptance Criteria:**

- GIVEN every Kotlin source file under F3's main package
  WHEN a source-inspection test reads them (resolving the repository root the same way F2's `ReadOnlySurfaceTest` does)
  THEN none contains `import android.` or `import org.json`. Any import from `ie.pantry.data.` is limited to the pure types F2's design names for engines (`AliasTable`, `Lookup`) and F1's `QuantityDimension`.

- GIVEN F3's behaviour tests for R1's totality and single-entry-point criteria (AC1, AC3), R2 (fixture-table cases), R4, R5, R6 and R7
  WHEN they run
  THEN they run without `RobolectricTestRunner`. R1 AC2, R2's shipped-alias criterion, R3 and R8 all use Robolectric unconditionally, for the same reason: each loads the real shipped alias table, or one of R3's other shipped datasets, through F2's existing `org.json` loader, independent of F3's own still-open corpus-fixture-format Decision Point (below), which governs only how the corpus's *own* file is read on disk, not whether the shipped alias table is involved.

### R10: No raw line text in any exception or failure value

As a Pantry user, I want my recipes' ingredient text never to appear in a crash report or error message, so that Android vitals never carries my content (ARCHITECTURE Data Flow, third invariant).

**Acceptance Criteria:**

- GIVEN lines containing `Sentinels.INGREDIENT` built to reach every failure path: key-absent, unquantified by each cause R4 lists, the numeric edge cases of R1, not-convertible, invalid-ratio and not-mergeable
  WHEN each is parsed, converted, scaled or merged
  THEN no exception escapes. Every typed failure or absence value (key-absent, unquantified, not-convertible, invalid-ratio, not-mergeable) has a `toString()` with no sentinel, and holds only structured data from the engine's own vocabulary: an enum category, and never any substring of the input. Any throwable raised and caught inside the engine is never kept, chained or logged. This follows the same discipline as F2's content-free `LoadFailure`. [CFC-4]

- GIVEN every `require`, `check`, `error(...)` or thrown exception in F3's main sources
  WHEN the sources are inspected
  THEN each message is a constant string with no interpolated input text, key or amount, and a test that feeds sentinel content into any internal invariant it can reach asserts `assertNoSentinel()` on the resulting throwable.

## Project Structure

```
pantry/                                               (repository root)
├── docs/reference-data-provenance.md                 MODIFIED (conditional on Q3): alias entries recorded
└── app/
    ├── build.gradle.kts                              MODIFIED: environment-conditional Robolectric resolver setting (test tasks only)
    └── src/
        ├── main/
        │   ├── assets/reference/aliases.json             MODIFIED (conditional on Q3): reconciliation entries
        │   └── java/ie/pantry/
        │       └── domain/ingredient/                    NEW: the engine (pure Kotlin)
        └── test/
            ├── java/ie/pantry/
            │   ├── domain/ingredient/                    NEW: behaviour, reconciliation, corpus and hygiene tests
            │   └── testutil/                             NEW: ParseFidelityCorpus.kt (shared loader, R8) and
            │                                              ParseFidelityCorpusCrossPackageTest.kt (R8 AC3's proof)
            └── resources/ingredient/                     NEW: the corpus fixture and its F5-extension fixture
```

**[ASSUMPTION — the Design phase fixes the package and file split.]** The package `ie.pantry.domain.ingredient` starts the domain layer ARCHITECTURE's System Overview describes ("a domain layer of pure Kotlin engines"). It sits beside `ie.pantry.data.*`. No `domain` package exists yet.

### New Files

- `app/src/main/java/ie/pantry/domain/ingredient/IngredientEngine.kt`: the single parse entry point (R1), plus conversion, scaling and merge operations (R5 to R7), built over an `AliasTable`.
- `app/src/main/java/ie/pantry/domain/ingredient/CanonicalKeyRule.kt`: the normalisation rule and the order the alias table is consulted in (R2). Not callable on raw text from outside the package (R1).
- `app/src/main/java/ie/pantry/domain/ingredient/QuantityParser.kt`: quantity-segment recognition (R4).
- `app/src/main/java/ie/pantry/domain/ingredient/Quantity.kt`: the typed result types: key result (derived or key-absent), quantity (measured or unquantified), and the not-convertible, invalid-ratio and not-mergeable outcomes (R2, R4 to R7, R10).
- `app/src/main/java/ie/pantry/domain/ingredient/Units.kt`: recognised units, stable `quantityUnit` tokens and conversion factors (R4, R5, Q4).
- `app/src/test/java/ie/pantry/domain/ingredient/CanonicalKeyRuleTest.kt`: R2 against fixture tables.
- `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`: R4.
- `app/src/test/java/ie/pantry/domain/ingredient/ConversionScalingMergeTest.kt`: R5, R6, R7.
- `app/src/test/java/ie/pantry/domain/ingredient/EntryPointTest.kt`: R1's single-entry-point and totality criteria (AC1, AC3), plain JVM per R9 AC2.
- `app/src/test/java/ie/pantry/domain/ingredient/EntryPointDeterminismTest.kt`: R1's determinism criterion (AC2), against the shipped alias table and the shared parse-fidelity corpus (Robolectric per R9 AC2) — split from `EntryPointTest.kt` for the same Robolectric-boundary reason R2 is split into `CanonicalKeyRuleTest.kt`/`ShippedKeyReconciliationTest.kt`.
- `app/src/test/java/ie/pantry/domain/ingredient/ShippedKeyReconciliationTest.kt`: R2's shipped-alias criterion and R3 (Robolectric).
- `app/src/test/java/ie/pantry/domain/ingredient/ParseFidelityCorpusTest.kt`: R8 (Robolectric, shipped alias table).
- `app/src/test/java/ie/pantry/domain/ingredient/EngineErrorHygieneTest.kt`: R10, using the existing `ie.pantry.testutil.Sentinels` and `assertNoSentinel()`.
- `app/src/test/java/ie/pantry/domain/ingredient/EnginePurityTest.kt`: R9 source inspection.
- `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt`: the shared loader, in the test-utility package F1 and F2 already use (R8).
- `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpusCrossPackageTest.kt`: R8 AC3's own claim (loadable from outside F3's package, with no dependency on any F3 test class) — the only test file that actually proves it, since `ParseFidelityCorpusTest.kt` runs from inside F3's own package.
- `app/src/test/resources/ingredient/parse_fidelity_corpus.*`: the corpus (format is a Decision Point).
- `app/src/test/resources/ingredient/parse_fidelity_corpus_with_f5_entry.*`: R8's extension fixture.

### Modified Files

- `app/src/main/assets/reference/aliases.json`: two ordinary new entries, `oat` → `oats` and `baked bean` → `baked beans` (Q3). This is a data edit to an F2 asset (Ask First); neither entry is a self-map or a chain, so F2's already-approved `ShippedDatasetsTest` policy checks (surface form, no self-map, no chains, targets resolve) pass unchanged, and F2's `spec.md`/`design.md` are not touched. `chopped tomato`/`chopped tomatoes` is **not** an alias entry — it is a Q2 protected phrase inside F3's own rule (Q3) and needs no F2 file change at all.
- `docs/reference-data-provenance.md`: the `## Aliases` section records the two entries added under Q3.
- `app/build.gradle.kts`: an environment-conditional addition to the existing `tasks.withType<Test>` block. When `.toolchain/m2/repository` is a directory, it sets the `maven.repo.local` system property on every `:app` test task, so Robolectric can resolve its SDK jar from that local repository. The reason is that the implementation devcontainer has no route to Maven Central, so the SDK jar cannot be fetched at test time. This affects the F1 and F2 Robolectric tests as well as F3's, because the block is suite-wide. The condition is machine-local, because `.toolchain/` is gitignored and invisible to `git status`. The directory is expected to contain the `org/robolectric/android-all-instrumented` artifact for the SDK pinned in `app/src/test/resources/robolectric.properties`. An absent directory is a no-op, which is verified by reading the block, not by a test. Closeout checks the block with `git diff --numstat d3fb8b2 -- app/build.gradle.kts`, which is expected to show `11` added and `0` removed relative to the F2 baseline, and `grep -n 'systemProperty("maven.repo.local"' app/build.gradle.kts`, which is expected to show exactly one hit, inside the `isDirectory` guard. It then runs `ls .toolchain/m2/repository/org/robolectric/android-all-instrumented`, which is expected to list a directory for the SDK pinned in `app/src/test/resources/robolectric.properties`, and then runs the full `./gradlew :app:testDebugUnitTest` with the directory present, because the setting applies suite-wide, and records a green result as the evidence that the resolver honours the property. It adds no dependency artifact and does not touch `dependencies { }` or `gradle/libs.versions.toml`. [ASSUMPTION: Robolectric's resolver honours `maven.repo.local`. This was established by inspecting Robolectric's resolver during Phase 4, not re-verified here.]

No change to `PantryDatabase`, its entities, `app/schemas/`, `AppContainer`, `AndroidManifest.xml` or `gradle/libs.versions.toml`. How callers get an `AliasTable` (`ReferenceDataStore.aliases()`, which returns `LoadResult<AliasTable>`) and handle a `LoadFailed` is their concern, per F2 design AD2 ("Engines receive tables only").

## Commands

```bash
# F3's tests (includes the R8 AC3 cross-package proof, which lives outside F3's own package on purpose)
./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.*' --tests 'ie.pantry.testutil.ParseFidelityCorpusCrossPackageTest'

# F2's shipped-content checks still pass after any alias data edit (Q3)
./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.reference.ShippedDatasetsTest'

# Full suite (no F1/F2 regressions)
./gradlew :app:testDebugUnitTest

# Static analysis
./gradlew lintDebug

# Quick purity check (R9 is asserted by EnginePurityTest; this is a manual cross-check)
grep -rn 'import android\.\|import org\.json' app/src/main/java/ie/pantry/domain/ingredient/ || echo "clean"
```

## Boundaries

### Always Do

- Route every line through the single entry point (R1). Every other operation takes already-parsed values, never raw text.
- Return a typed outcome for every miss or failure (key-absent, unquantified, not-convertible, invalid-ratio, not-mergeable). Use sealed types with exhaustive `when`, in the style of F2's `Lookup`/`LoadResult` (F2 design AD1).
- Treat anything not confidently recognised as unquantified (ARCHITECTURE R2): when unsure, drop the number, never guess one.
- Take the alias table as a plain `AliasTable` parameter and query it synchronously (F2 design AD2). Never make the engine `suspend` or give it a `ReferenceDataStore`.
- Reuse F1's `QuantityDimension` enum for dimensions, as F2 reused `NutritionBasis`.
- Build every failure value from enums and constants only, and give every `require`/`check` a constant message (R10; F2's `LoadFailure` and `ReferenceTables` pattern).
- Write tests in the F1/F2 style: JUnit 4, `kotlin.test` assertions, backtick-quoted names. Use plain JVM tests for behaviour and Robolectric only where shipped assets are loaded (R9).
- Keep behaviour tests on fixture alias tables (through `AliasTable`'s `internal` constructor) and shipped-data checks in separate tests (R3, R8), so F17's data edits do not break behaviour tests.

### Ask First

- The two Q3 `aliases.json` additions (`oat` → `oats`, `baked bean` → `baked beans`) — an ordinary F2 data edit, developer-confirmed, but still someone else's asset. Any edit to F2's `ShippedDatasetsTest` policy checks, `01_spec.md` or `02_design.md` themselves is out of scope for F3 entirely: F3's Q3 resolution needs none, and if a future decision ever did need one, that is F2's own Re-Approval After Edits flow (hash-and-cascade), not a same-feature data tweak.
- Adding a unit, a count unit or a conversion factor beyond the Q4 table, or changing a factor.
- Changing the preparation- and size-word list or the singularisation rules after Q2 is resolved. Either changes keys for already-saved recipes once F6 ships.
- Any change to `PantryDatabase`, its entities or `app/schemas/`, including making `RecipeIngredient.canonicalKey` nullable (RK8).
- Adding any dependency, including a units or natural-language library.

### Never Do

- Never produce a measured quantity whose amount was not read from the line, and never default a missing amount to `0`, `1` or any other number (R4).
- Never convert across dimensions or between count units, and never use a density or typical-weight estimate (ARCHITECTURE C4 Boundary; that is F9's concern).
- Never put line text, a key or an amount into an exception message or a failure value (R10).
- Never import `android.*` or `org.json` in F3's main sources (R9).
- Never infer an alias by fuzzy matching or edit distance. Aliases are explicit data in C2 (ARCHITECTURE R6).
- Never read or write storage from F3's engine sources, and never compute nutrition, seasonality or section semantics (ARCHITECTURE C4 Boundary). This rule does not govern build scripts: the build-time directory check in `app/build.gradle.kts` is outside it, as set out under Network Exposure Triage.
- Never expose a second route that accepts raw line text, such as a public "key only" or "quantity only" parser (R1, CFC-1).
- Never tag an F3 criterion with CFC-3: PLAN's CFC-3 participant list (F7, F10 to F15) does not include F3. F3's tags are CFC-1 (R1, R8), CFC-2 (R2, R4) and CFC-4 (R10) only.

### Network Exposure Triage

**Branch (a): no new surface.** F3 is in-process pure computation. It adds no permission, no dependency, no component and no runtime I/O in the app or engine. The only asset reads are in tests: shipped tables through F2's existing loader, and the corpus fixture through a plain-Kotlin loader in test resources. The one build-time exception is the test-only resolver setting in `app/build.gradle.kts` (see Modified Files): at configuration time it checks whether the directory `.toolchain/m2/repository` exists and, only if it does, sets Robolectric's `maven.repo.local` system property for every `:app` test task. That check is a filesystem read at build time, not an I/O operation of F3's code. It adds no artifact, no permission and no component.

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: At which stage(s) is the alias table consulted? PLAN says "the canonical-key rule is applied first and the C2 alias table consulted only for exceptions". If it is consulted only with the rule's final output, the shipped `scallions` variant can never be reached (the rule singularises it first), and an irregular plural such as `bay leaves` has to be keyed by whatever the regular rule wrongly produces (for example `bay leave`). **[ASSUMPTION]** Two stages. First the name phrase: case-folded, quantity segment, comma tail and parentheses removed, preparation words still present. Then the rule's final output. The first hit wins. The rule still runs first in the sense that the table is only ever given normalised text.
- [x] Q2: What exactly is the rule? This covers the preparation- and size-word list, singularisation, text folding, and any protected multi-word phrase. **[ASSUMPTION]** Preparation and size words: `chopped, finely, roughly, thinly, diced, sliced, minced, crushed, grated, peeled, halved, trimmed, beaten, softened, melted, fresh, freshly, ground, large, medium, small, ripe`, plus the phrases `to taste`, `to serve` and `for garnish`. Before any preparation word is stripped, the rule checks the case-folded, quantity-and-comma-and-parentheses-stripped name phrase against a small **protected-phrase list** — multi-word canonical products where a would-be preparation word is actually part of the product name, not a cooking instruction (Q3: `chopped tomato`/`chopped tomatoes` — tinned chopped tomatoes are a distinct staples entry from fresh `tomato`, with different nutrient figures). A protected-phrase hit returns that phrase's canonical form directly, before preparation-word stripping or singularisation runs, and needs no alias-table entry. Otherwise: everything after the first comma and inside parentheses is dropped, then singularise only the last word: `-ies` → `-y`, `-oes` → `-o`, `-ches`/`-shes`/`-sses`/`-xes` → drop `-es`, otherwise drop a final `-s` unless the word ends `-ss`, `-us` or `-is`. Fold diacritics (NFD, strip combining marks). Hyphens become spaces. A compound line such as `salt and pepper` keeps the key `salt and pepper` (an honest *unmatched* downstream) rather than being split.
- [x] Q3: How are the shipped keys that are not fixed points reconciled (R3)? Under the Q2 default (before the protected-phrase check), `oats` → `oat`, `baked beans` → `baked bean`, and `chopped tomato` → `tomato` (`chopped` is a preparation word). Two different fixes are needed, and they are not the same kind of change:
  - **`oats` and `baked beans`:** ordinary alias entries that map the rule's wrong output back to the shipped key (`oat` → `oats`, `baked bean` → `baked beans`). Neither is a self-map (`oat` ≠ `oats`) and neither chains (`oats`/`baked beans` are not themselves used as a variant anywhere in the table), so F2's already-approved `ShippedDatasetsTest` policy (`aliasSelfMaps`, `aliasChains`) accepts both unchanged. This is an ordinary data edit to `aliases.json` under F2's own existing Ask First rule — no F2 test, spec or design change.
  - **`chopped tomato`:** **not** an alias-table fix. F2's approved `01_spec.md` R5 AC states "no entry maps a variant to itself" and `02_design.md` AD10 records "no alias mapping to itself, no alias chains" as a deliberate, already-reviewed policy — reconciling this key by adding a self-map alias entry would require reopening and re-approving F2's own spec.md and design.md (and the `ShippedDatasetsTest` policy those artifacts commit to), not a routine same-feature data tweak. The chosen fix instead lives entirely inside F3's own rule: `chopped tomato`/`chopped tomatoes` is a Q2 protected phrase, recognised and returned before preparation-word stripping runs, with no F2 asset, test, or artifact touched at all. The underlying reason this key needs protecting rather than folding into `tomato` in the first place: tinned chopped tomatoes and fresh tomatoes carry different nutrient figures in F2's shipped `staples.json` (this is exactly the over-merge risk RK2 names), so collapsing the two keys would be a real, silent nutrition-data loss, not just a naming inconvenience.
  - Two remaining options for `chopped tomato`, rejected in favour of the protected-phrase fix: renaming the staples key (also touches `section_order.json`'s mappings, and "chopped tomato" is already the clearest possible name for the product); or dropping the `chopped tomato` staples row entirely and accepting the nutrition-data loss above.
  **[ASSUMPTION]** Alias entries `oat` → `oats` and `baked bean` → `baked beans` (F2 data edit, Ask First, no F2 test/spec/design change); `chopped tomato` and `chopped tomatoes` as Q2 protected phrases resolving to `chopped tomato` (F3-internal, no F2 change at all).
- [x] Q4: Which units are recognised, and at what factors? **[ASSUMPTION]** Mass: `g`, `kg`, `mg`, `oz` (28.349523125 g), `lb` (453.59237 g). Volume: `ml`, `l`, `cl`, `tsp` (5 ml), `tbsp` (15 ml), `cup` (250 ml, the metric cup), `fl oz` (28.4130625 ml, imperial), `pint` (568.26125 ml, imperial). The Irish locale favours metric and imperial over US customary (the US cup is 236.6 ml and the US pint 473.2 ml); a line from a US site will be about 5% out in volume, and this is accepted (RK5). Count units: `clove`, `tin`, `can`, `bunch`, `bulb`, `slice`, `sprig`, `stick`, `sheet`, `packet`, and none for a bare count. Spelled-out and plural forms (`tablespoons`, `grams`, `kilos`) are recognised. `pinch`, `handful`, `knob`, `dash` and `splash` are unquantified.
- [x] Q5: Do `COUNT` quantities merge across different count units? PLAN's predicate is "same canonical key and same dimension", which read literally merges `3 cloves garlic` with `1 bulb garlic` into a meaningless 4. **[ASSUMPTION]** `COUNT` quantities merge only when their count units match. This is a narrowing of PLAN's predicate that is consistent with SCOPE G3's "physically comparable".
- [x] Q6: How are ranges, multipliers and parenthetical sizes read? **[ASSUMPTION]** A range (`2-3`, `2 to 3`) is unquantified: choosing either end is a guess. `N x M unit` (`2 x 400g tins`) is N×M in that unit. A parenthetical size on a counted container (`1 (400 g) tin`, `1 tin (400g)`) uses the stated size × the count. A decimal comma (`1,5 kg`) is unquantified.
- [x] Q7: Do two unquantified lines with the same key merge? **[ASSUMPTION]** Yes. `salt, to taste` and `a pinch of salt` become one unquantified `salt` entry with no number, which matches PLAN's literal predicate (same key, same dimension `UNQUANTIFIED`) and avoids duplicate list rows. An unquantified line never merges with a measured one.
- [x] Q8: F1's `RecipeIngredient.canonicalKey` is a non-null `String`, but F3 must give key-absent for a line with no name text (`2 tbsp`). Is persisting such a line F6's problem, for example by refusing to save it with an inline message or by treating it as a note, rather than F3's? **[ASSUMPTION]** Yes. F3 represents the absence and does not touch the schema. F6's spec decides how to handle it, and any schema change is F6's Ask First.
- [x] Q9: Which recipe pages make up the corpus? PLAN requires the developer to choose them, independently of F5. **[ASSUMPTION]** The developer supplies a list of pages from several different sites (at least five distinct domains, including at least one US-customary site, to exercise Q4) before the Tasks phase. Each corpus entry records the page URL and a retrieval date. The corpus is test data and does not ship in the APK.
- [x] Q10: What coverage must the corpus reach beyond the count of 50? How are non-ingredient lines (section headers such as `For the sauce:`) treated? **[ASSUMPTION]** R8's coverage floor: each dimension, unquantified, an alias hit, a fraction, and at least one non-ingredient or key-absent line. A section header is not detected specially: it parses to whatever the rule gives (`for the sauce:` becomes a key), and the corpus records that as the expected value, so the behaviour is pinned down rather than implied.

## Decision Points

- The shape of the result types. One option reuses `ie.pantry.data.reference.Lookup<String>` for the key result, since `Lookup.Absent` is already the house absence value; the other is an F3-owned sealed type with a `data object` key-absent. The unquantified value is a `data object` or carries a reason enum. Either way the shapes must follow F2 AD1's rules: exhaustive `when`, no nullable absence, no `kotlin.Result`.
- Numeric representation: `Double` in base units, or an exact decimal/rational. `Double` makes merge results depend on summation order at the ULP level, which matters for F11's byte-identical regeneration (RK4) and for CFC-1's byte-identical comparison across routes.
- The unit a merged or scaled quantity is expressed in: kept in the larger input unit (1.5 kg), always in the base unit (1500 g), or normalised to a display unit. R7 fixes value equality, not the unit.
- How R1's "only one non-private declaration accepts raw text" is enforced in a single Gradle module where `internal` is visible to every feature: `private` members in one file, or `internal` plus a source-inspection test.
- Whether the engine object is built once per `AliasTable` (a class) or is a set of functions taking the table as an argument.
- The corpus file format (JSON read by the loader, or TSV), and whether the loader uses `org.json` or a plain-text format. Every corpus consumer (R1 AC2, R8) already needs Robolectric regardless of this choice, since each also loads the real shipped alias table through F2's `org.json` loader — this Decision Point is only about how the corpus's own on-disk fixture is parsed, not about which test infrastructure a corpus test needs.
- Whether to cap the input line length before parsing (see RK7), and at what length.

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| RK1 | The quantity parser reads a number confidently but wrongly (for example `1 1/2` read as 11/2, or a leading `2` in `2 x 400g` taken as the whole amount), giving a wrong figure on the list: the failure ARCHITECTURE R2 calls trust-destroying | Med | High | Unquantified when unsure (R4); R4's named fraction and multiplier cases; the 50-line real corpus (R8), later grown by F5; F6 shows the raw line beside the parsed quantity (ARCHITECTURE R2) |
| RK2 | Over-merge: a rule step (preparation-word stripping, singularisation) folds distinct products together (`chopped tomatoes` into `tomato`, `ground almonds` into `almond`), and F11 then sums them | Med | High | Merge is gated on key **and** dimension (R7); R2's tomato/purée criterion; R3's fixed-point check on every shipped key; Q2 and Q3 settle the word list and exceptions explicitly |
| RK3 | Under-merge or silent misses: the rule produces keys that F2's curated data does not contain, so ingredients show as *unmatched* or *unknown* and land in the terminal bucket | High | Med | R3 fails the build on any shipped key that is not a fixed point or any alias variant that cannot be reached; F17 grows aliases from real misses |
| RK4 | Floating-point summation order makes two merges of the same quantities differ in the last bit, breaking F11's byte-identical regeneration or CFC-1's byte-identical comparison | Med | Med | Numeric-representation Decision Point; R1's determinism criterion; R7's order-independence criterion for pairs |
| RK5 | Recipes from US sites use US cups and pints while the engine assumes metric/imperial, so volumes are about 5% out with no signal | Med | Low | Recorded as an accepted default (Q4); the corpus includes a US-customary source (Q9) so the effect is visible in tests |
| RK6 | Corpus expected values are produced by running the engine and pasting its output, so the corpus just confirms the current behaviour, bugs included | Med | High | R8's review criterion: the developer writes expected values from the source line; the corpus is authored before, or independently of, the parser implementation |
| RK7 | An imported line (untrusted content from F5) is pathologically long or crafted to cause catastrophic regex backtracking, freezing the save path on the main thread | Low | Med | R1's 10,000-character totality case; the line-length cap Decision Point; Design avoids backtracking-prone patterns |
| RK8 | A key-absent line cannot be stored because `RecipeIngredient.canonicalKey` is non-null, and a caller "fixes" this by writing `""`, which collapses absence into a default (CFC-2) | Med | Med | Q8 routes the handling to F6 explicitly; R2's key-absent criterion forbids `""` inside F3; any schema change is Ask First |
| RK9 | Changing the rule after F6 ships alters keys for lines already saved, so old and new recipes stop merging | Low | Med | Q2 is fixed before Design; later rule changes are Ask First; F17's regression requirement re-runs F3's tests against expanded data |

## Success Criteria

- [ ] Exactly one non-private declaration accepts raw line text; parsing is total (never throws) and deterministic across engine instances.
- [ ] The canonical-key rule handles case, whitespace, diacritics, hyphens, preparation and size words, and regular plurals alone; the alias table overrides only for exceptions, including an irregular plural and a brand name.
- [ ] Every shipped staples, seasonality, substitution, section-mapping and alias-target key is a fixed point of the engine, and every shipped alias variant is reachable (Q3 decisions applied).
- [ ] Mass, volume and count quantities parse to typed, dimensioned values; anything not confidently recognised is unquantified, and that value cannot hold a number.
- [ ] Every measured quantity maps losslessly to and from F1's `RecipeIngredient` quantity columns.
- [ ] Within-dimension conversion is exact to the Q4 factors; cross-dimension and cross-count-unit conversion returns a typed not-convertible outcome.
- [ ] Scaling multiplies measured quantities, leaves unquantified values unchanged, and rejects an invalid ratio with a typed outcome.
- [ ] 1 kg + 500 g of one key merges to 1.5 kg; the three tomato, tomato-purée and onion pairs, and differing count units, never merge.
- [ ] A corpus of 50 or more real, hand-transcribed lines passes against the shipped alias table, meets its coverage floor, and can be loaded from outside F3's package, including entries tagged `F5_HARVESTED`.
- [ ] F3's main sources import no `android.*` or `org.json`, and its behaviour tests run without Robolectric.
- [ ] No exception escapes any engine operation, and no failure value or exception message contains input text.
- [ ] All tests pass.
- [ ] No regressions in existing functionality: F1's and F2's full `./gradlew :app:testDebugUnitTest` suite stays green, including `ShippedDatasetsTest` after any Q3 alias edit.

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

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes                   |
|------|------------|-------|-------------|-----------|----------|--------|-------------------------|
| 1    | 2026-09-28 | 2     | 0           | 2         | 0        | 0      | —                       |
| 2    | 2026-09-28 | 2     | 0           | 2         | 0        | 0      | —                       |
| 3    | 2026-09-28 | 1     | 0           | 3         | 0        | 1      | —                       |
| 4    | 2026-09-28 | 1     | 0           | 1         | 0        | 0      | —                       |
| 5    | 2026-09-28 | 1     | 0           | 1         | 0        | 0      | —                       |
| 6    | 2026-09-29 | 1     | 0           | 1         | 0        | 0      | —                       |
| 7    | 2026-09-29 | 0     | 0           | 2         | 0        | 2      | converged (0 HIGH)      |
| 8    | 2026-10-04 | 1     | 0           | 17        | 0        | 3      | upstream-panel 74088c27 |
| 9    | 2026-10-04 | 0     | 0           | 12        | 3        | 4      | converged (0 HIGH)      |
| 10   | 2026-10-04 | 1     | 0           | 2         | 0        | 6      | —                       |
| 11   | 2026-10-04 | 0     | 0           | 2         | 0        | 4      | converged (0 HIGH)      |

### Sealed dispositions

- `[SEAL-01]` **R1 AC2 and the Success Criteria presuppose a class-based…** (pass 3, accepted-as-risk) — Defense: doesn't block either implementation choice per the panelist's own assessment; a phrasing lean, not a commitment, and resolving the Decision Point either way still satisfies the AC as written.
- `[SEAL-02]` **Open Questions checkboxes could read as blocking TODOs to a…** (pass 7, accepted-as-risk) — Defense: standard SDD convention across every spec in this repo, not introduced by this pass, and the intended audience (developers with sibling docs open) already has the context.
- `[SEAL-03]` **`domain/ingredient/` tree row stays a category label rather…** (pass 7, accepted-as-risk) — Defense: matches the New Files section's own enumeration one line-scroll away; this is the pre-existing rollup convention, not an omission — the panelist itself distinguishes this from the pass-6 defect pattern.
- `[SEAL-04]` **Commands and Success Criteria do not surface the suite-wide…** (pass 8, accepted-as-risk) — Defense: the Modified Files bullet now states the suite-wide scope, and closeout re-runs the full F1/F2 suite under the existing No-regressions criterion. Commands is deliberately unchanged; this is synthesizer-judged, not user-confirmed.
- `[SEAL-05]` **Sandbox-only path lives in the committed build script; an…** (pass 8, accepted-as-risk) — Defense: the developer confirmed the in-repo block (03_tasks.md Implementation Deviations). Moving it to a Gradle init script is a future option, not a spec requirement; the design panel's opt-in recommendation is recorded in the design's own Panel Review. Synthesizer-judged, not user-confirmed.
- `[SEAL-06]` **No documented way to populate the directory or handle a…** (pass 8, accepted-as-risk) — Defense: populating the local repository is an environment-setup step outside this spec, not a behaviour requirement of F3; no repository file documents it, which is recorded here as a known gap. The expected artifact is named, so a partial directory is detectable at closeout. Synthesizer-judged, not user-confirmed.
- `[SEAL-07]` **Tree column alignment is off by a few characters** (pass 9, accepted-as-risk) — Defense: cosmetic only; the tree nests and renders correctly. Synthesizer-judged, not user-confirmed.
- `[SEAL-08]` **Branch (a) does not enumerate the screened exposure surfaces** (pass 9, accepted-as-risk) — Defense: the branch already declares no permission, no dependency, no component and no runtime I/O in the app or engine, and the independent audit found no trigger. Enumerating each surface is editorial. Synthesizer-judged, not user-confirmed.
- `[SEAL-09]` **Commands do not mention the directory prerequisite** (pass 9, accepted-as-risk) — Defense: the Modified Files bullet states the prerequisite and the closeout check, and populating the directory is the environment-setup gap recorded under SEAL-06. Synthesizer-judged, not user-confirmed.
- `[SEAL-10]` **The build clause trusts unchecked local repository contents** (pass 9, accepted-as-risk) — Defense: exploitation requires local write access to a gitignored repository, which already implies full local compromise; nothing is exposed to an untrusted network. Synthesizer-judged, not user-confirmed.
- `[SEAL-11]` **The Modified Files bullet is long and mixes what changes…** (pass 10, accepted-as-risk) — Defense: the bullet is one unit of the spec's Project Structure, and the closeout steps are the verification record; splitting them adds a section without changing any requirement. Synthesizer-judged, not user-confirmed.
- `[SEAL-12]` **Latest pass detail is empty, so pass 9's rows are not…** (pass 10, accepted-as-risk) — Defense: archive_pass.py clears Latest pass detail by design after archiving; pass 9's rows are preserved in the Trajectory and the Sealed dispositions. Synthesizer-judged, not user-confirmed.
- `[SEAL-13]` **Tree says test tasks only, while the bullet says every :app…** (pass 10, accepted-as-risk) — Defense: both are true; the bullet carries the precise scope and the tree label is a summary. Synthesizer-judged, not user-confirmed.
- `[SEAL-14]` **The tree marks the whole testutil directory NEW, though it…** (pass 10, accepted-as-risk) — Defense: the New Files list is the authority for which files are new; the tree label is a summary, carried over from the approved layout. Synthesizer-judged, not user-confirmed.
- `[SEAL-15]` **The numeric numstat expectation is fragile to comment edits** (pass 10, accepted-as-risk) — Defense: the grep check proves the guard is present, and the numstat check is corroborating evidence. Synthesizer-judged, not user-confirmed.
- `[SEAL-16]` **Storage is undefined in the Never Do rule** (pass 10, accepted-as-risk) — Defense: the rule is scoped to engine sources, where storage means persistence and file access in the ARCHITECTURE C4 sense; the build-script carve-out no longer depends on the term. Synthesizer-judged, not user-confirmed.
- `[SEAL-17]` **The grep names the guard placement but does not establish it** (pass 11, accepted-as-risk) — Defense: the closeout reviewer reads the diff at closeout, where a moved line is visible; the grep and numstat checks are corroborating evidence. Synthesizer-judged, not user-confirmed.
- `[SEAL-18]` **The grep expectation could name the if-block explicitly** (pass 11, accepted-as-risk) — Defense: editorial; the expected single hit is unambiguous in the file. Synthesizer-judged, not user-confirmed.
- `[SEAL-19]` **The Never Do storage bullet restates scope and adds a…** (pass 11, accepted-as-risk) — Defense: the carve-out is intentionally stated where the rule applies, so the build-script exception is not missed. Synthesizer-judged, not user-confirmed.
- `[SEAL-20]` **Branch (a) calls the build-time check "the one build-time…** (pass 11, accepted-as-risk) — Defense: branch (a) states both the no-runtime-I/O claim and the separate build-time check, so the wording is accurate. Synthesizer-judged, not user-confirmed.

### Deferred dispositions

- `[DEF-01]` **T17 closeout in 03_tasks.md requires the build script…** → 03_tasks.md (pass 9) — Routed because: the closeout criterion lives in 03_tasks.md, so the tasks cascade corrects T17 AC2 and its Verification once the spec converges.
- `[DEF-02]` **ShippedReferenceTables.kt and QuantityOperations.kt are…** → 02_design.md (pass 9) — Routed because: the design's File Structure is the authority for the file split (the spec's own ASSUMPTION defers it to Design), so the spec tree stays at the spec's granularity.
- `[DEF-03]` **R9 AC2 still calls the corpus format an open Decision…** → 02_design.md (pass 9) — Routed because: the corpus format is decided in design AD6, so the spec wording is refined when the spec next converges.

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `550926a079d1757b`
- **Hash basis:** v2