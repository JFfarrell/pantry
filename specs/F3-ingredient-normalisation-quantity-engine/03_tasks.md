# Tasks: Ingredient Normalisation & Quantity Engine

**Spec:** `specs/F3-ingredient-normalisation-quantity-engine/01_spec.md`
**Design:** `specs/F3-ingredient-normalisation-quantity-engine/02_design.md`

## Summary

| Task | Description | Requirement | Dependencies | Parallel | Status |
|------|-------------|-------------|--------------|----------|--------|
| T1 | Unit enums, tokens and Q4 factors | R4 | None | Yes (with T4, T13) | Done |
| T2 | Sealed key, quantity and outcome types with construction guards | R2, R4 | T1 | Yes (with T4, T13) | Done |
| T3 | F1 column mapping: `QuantityColumns`, `toColumns`, `decode` | R4 | T2 | Yes (with T4, T6, T7, T10, T12, T13) | Done |
| T4 | Hand-transcribed parse-fidelity corpus and F5 extension fixture | R8 | None | Yes (with T1, T2, T3, T6, T7, T12, T13) | Done |
| T5 | Shared corpus loader `ParseFidelityCorpus` and loader-level corpus tests | R8 | T3, T4 | Yes (with T6–T14, T16) | Done |
| T6 | Entry-point skeleton: sealed `ScannedLine`, `scan`, catch-all `parse`, stubs, `EntryPointTest` | R1 | T2 | Yes (with T3, T4, T5, T12, T13) | Done |
| T7 | `EnginePurityTest` and the source-inspection half of `EngineErrorHygieneTest` | R9, R10 | T6 | Yes (with T3, T4, T5, T8–T15) | Done |
| T8 | Quantity parser: word cursor, AMOUNT grammar, validity, rows G1, G8, G9, G13 | R4 | T3, T4, T6 | Yes (with T5, T7, T10, T12, T13) | Done |
| T9 | Quantity parser: rows G2–G7 and G10–G12 in table order | R4 | T8 | Yes (with T5, T7, T10, T12, T13) | Done |
| T10 | Canonical-key rule K1–K8 with fixture-table tests | R2 | T4, T6 | Yes (with T3, T5, T7, T8, T9, T12, T13) | Done |
| T11 | Spec R2/R7 lines end to end through `parse` | R1, R2, R4, R7 | T9, T10, T12 | Yes (with T5, T7, T13) | Done |
| T12 | Conversion, scaling and merge operations | R5, R6, R7 | T2 | Yes (with T3–T10, T13) | Done |
| T13 | Q3 alias additions and provenance record | R3 | None | Yes (with T1–T12, T16) | Done |
| T14 | `ShippedReferenceTables` and `ShippedKeyReconciliationTest` | R2, R3 | T11, T13 | Yes (with T5, T7, T16) | Done |
| T15 | CFC-1 corpus enforcement tests: corpus fidelity, cross-package load, cross-instance determinism [CFC-1] | R1, R8 | T5, T14 | Yes (with T7, T16) | Done |
| T16 | `EngineErrorHygieneTest` sentinel and throwing-seam cases | R10 | T7, T11 | Yes (with T5, T13, T14, T15) | Done |
| T17 | Feature closeout: F3 filter, F2 regression, full suite, lint, purity grep | R1–R10 | T1–T16 | No | Done |

## Phase 1: Result and Unit Model (FC1; design Implementation Sequence step 1)

### - [x] T1: Unit enums, tokens and Q4 factors

- **Requirement:** R4
- **Description:** Create `Units.kt` with `sealed interface AmountUnit`, `enum class MeasureUnit` (token, dimension, `factorToBase`, `internal val spellings`) and `enum class CountUnit` (token, spellings) exactly as DM4/DM5, plus `const val BARE_COUNT_TOKEN = "each"`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/entity/QuantityDimension.kt` — the reused dimension enum
  - Create: `app/src/main/java/ie/pantry/domain/ingredient/Units.kt`
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
- **Dependencies:** None
- **Parallel:** Yes (with T4, T13) — disjoint files, no shared types
- **Acceptance Criteria:**
  - GIVEN every `MeasureUnit`, every `CountUnit` and `BARE_COUNT_TOKEN`
    WHEN their tokens are collected
    THEN there are 24 tokens and all are unique (DM5; R4 AC6 "one stable, unique `quantityUnit` token")
  - GIVEN each `MeasureUnit`
    WHEN its dimension and `factorToBase` are read
    THEN they match the DM4 table exactly (`G`/`ML` are 1.0; `OZ` 28.349523125; `LB` 453.59237; `FL_OZ` 28.4130625; `PINT` 568.26125; `CUP` 250.0), and no unit is `COUNT` or `UNQUANTIFIED`
- **Tests:**
  - `` `every unit token is unique across measure units count units and the bare count token`() `` — 24 distinct tokens
  - `` `measure unit factors and dimensions match the Q4 table`() `` — exact factor and dimension per enum value
  - `` `every unit spelling belongs to exactly one unit`() `` — no spelling shared between two units (needed by the parser in T8)
  - File: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.QuantityParserTest'`

### - [x] T2: Sealed key, quantity and outcome types with construction guards

- **Requirement:** R2, R4
- **Description:** Create `Quantity.kt` with `CanonicalKey` (`Derived`/`data object Absent`), `Quantity` (`Measured`/`Counted`/`data object Unquantified`, each exposing `dimension`), `ParsedLine`, and the sealed outcomes `Conversion`, `Scaling`, `MergeResult` with `data object` failures (AD1, DM1–DM3, DM6); `init` guards use constant messages.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/domain/ingredient/Units.kt` — `MeasureUnit`, `CountUnit`
  - Create: `app/src/main/java/ie/pantry/domain/ingredient/Quantity.kt`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/CanonicalKeyRuleTest.kt`
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/ConversionScalingMergeTest.kt`
- **Dependencies:** T1
- **Parallel:** Yes (with T4, T13) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a caller constructing `Measured(0.0, G)`, `Counted(0.0, null)`, a negative or non-finite amount, or `Derived("")`
    WHEN construction runs
    THEN it fails with `IllegalArgumentException`, so a `MASS` 0 g, a `COUNT` 0 and a `""` key cannot exist (AD1)
  - GIVEN `CanonicalKey.Absent` and `Quantity.Unquantified`
    WHEN compared and reflected on
    THEN `Absent` is unequal to every `Derived` the test builds and declares no `String` field; `Unquantified` is unequal to `Measured(1.0, G)` and `Counted(1.0, null)` and declares no `double` or unit field (R2 AC5, R4 AC5) [CFC-2]
  - GIVEN `NotConvertible`, `InvalidRatio` and `NotMergeable`
    WHEN reflected on and printed
    THEN each declares no field and its `toString()` is its own simple name (AD1; R10 AC1 groundwork)
- **Tests:**
  - `` `measured and counted reject a zero negative or non finite amount`() `` — construction guard (QuantityParserTest)
  - `` `quantities report their dimension`() `` — `MASS`/`VOLUME` from unit, `COUNT`, `UNQUANTIFIED` (QuantityParserTest)
  - `` `unquantified is not equal to any measured or counted quantity`() `` — R4 AC5 (QuantityParserTest)
  - `` `unquantified has no numeric or unit field`() `` — reflection (QuantityParserTest)
  - `` `derived key rejects a blank value`() `` — R2 AC5 (CanonicalKeyRuleTest)
  - `` `absent key has no string field and equals no derived key`() `` — R2 AC5 (CanonicalKeyRuleTest)
  - `` `operation failure values are field free data objects`() `` — construction-level cases (ConversionScalingMergeTest)
  - Files: `QuantityParserTest.kt`, `CanonicalKeyRuleTest.kt`, `ConversionScalingMergeTest.kt` under `app/src/test/java/ie/pantry/domain/ingredient/`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.QuantityParserTest' --tests 'ie.pantry.domain.ingredient.CanonicalKeyRuleTest' --tests 'ie.pantry.domain.ingredient.ConversionScalingMergeTest'`

### - [x] T3: F1 column mapping: `QuantityColumns`, `toColumns`, `decode`

- **Requirement:** R4
- **Description:** Add `data class QuantityColumns(amount: Double?, unit: String?, dimension)` with member `decode(): ColumnDecode`, the sealed `ColumnDecode` (`Decoded` / `data object Inconsistent`) and top-level `Quantity.toColumns()` per AD11/I5; a bare count's token is `each`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/db/entity/RecipeIngredient.kt` — the documented column contract ("Null iff dimension is UNQUANTIFIED"); not imported (design Integration Points)
  - Modify: `app/src/main/java/ie/pantry/domain/ingredient/Quantity.kt`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
- **Dependencies:** T2
- **Parallel:** Yes (with T4, T6, T7, T10, T12, T13) — disjoint files
- **Acceptance Criteria:**
  - GIVEN a quantity in every `MeasureUnit`, every `CountUnit` and the bare count
    WHEN it is mapped with `toColumns()` and read back with `decode()`
    THEN the result is `Decoded` of an equal quantity, and a bare count uses the token `each` (R4 AC6; I5)
  - GIVEN `Quantity.Unquantified`
    WHEN mapped
    THEN the triple is (`null`, `null`, `UNQUANTIFIED`) (R4 AC6)
  - GIVEN an unknown token, a token of the wrong dimension, a null amount on a measured dimension, a non-positive amount, a non-null amount on `UNQUANTIFIED`, and a non-null unit on `UNQUANTIFIED`
    WHEN each is decoded
    THEN each gives `ColumnDecode.Inconsistent` and none throws (AD11)
- **Tests:**
  - `` `every measure unit round trips through the column triple`() `` — R4 AC6
  - `` `every count unit and the bare count round trip through the column triple`() `` — R4 AC6, `each` token
  - `` `unquantified maps to null null UNQUANTIFIED`() `` — R4 AC6
  - `` `triples the engine could not produce decode to inconsistent`() `` — the six inconsistent triples
  - File: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.QuantityParserTest'`

## Phase 2: Parse-Fidelity Corpus (FC7; step 2)

> The corpus rows are written **by the developer**, by reading each source line on the design Q3 pages (spec R8 AC1, AC5; RK6; DR5). The implementer does not author expected values and never copies engine output into the corpus. T4 must be finished and committed by the developer before T8 or T10 starts, so the order is visible in `git log`.

### - [x] T4: Hand-transcribed parse-fidelity corpus and F5 extension fixture

- **Requirement:** R8
- **Description:** The developer writes `parse_fidelity_corpus.tsv` in the DM8 format (header, 9 tab-separated fields, no empty field, `<absent>`/`-` for absence) with at least 50 `HAND_TRANSCRIBED` lines from the design Q3 page list, and `parse_fidelity_corpus_with_f5_entry.tsv` as an exact copy plus one `F5_HARVESTED` row.
- **Files:**
  - Read: `specs/F3-ingredient-normalisation-quantity-engine/02_design.md` — DM8 row format, AD8/AD9/AD10 behaviour, design Q3 page list
  - Read: `app/src/main/assets/reference/aliases.json` — shipped variants, so at least one line is an alias hit
  - Create: `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv`
  - Create: `app/src/test/resources/ingredient/parse_fidelity_corpus_with_f5_entry.tsv`
- **Dependencies:** None
- **Parallel:** Yes (with T1, T2, T3, T6, T7, T12, T13) — test data only; must finish before T8 and T10
- **Acceptance Criteria:**
  - GIVEN the design Q3 pages (5 domains, 3 US-customary)
    WHEN the developer transcribes lines
    THEN the corpus holds ≥ 50 `HAND_TRANSCRIBED` rows from ≥ 5 distinct hosts, each with its page URL, retrieval date, line kind, verbatim line (no tab, well under 1,000 characters) and developer-written expected key and column triple (R8 AC1; Q9)
  - GIVEN the corpus rows
    WHEN counted by expected outcome
    THEN at least one row expects each of `MASS`, `VOLUME`, `COUNT` and `UNQUANTIFIED`, one expects a key resolved through a shipped alias variant, one is `NON_INGREDIENT` or expects `<absent>`, and one has a non-integer amount (R8 AC2; Q10)
  - GIVEN the extension fixture
    WHEN it is compared with the corpus
    THEN it is identical except for exactly one extra `F5_HARVESTED` row (R8 AC4)
- **Tests:** Not applicable — hand-authored test data; loaded and asserted by T5 and T15 (see Verification).
- **Verification:** With `C=app/src/test/resources/ingredient/parse_fidelity_corpus.tsv` and `E=app/src/test/resources/ingredient/parse_fidelity_corpus_with_f5_entry.tsv`:
  - `grep -v '^#' "$C" | head -n 1 | diff - <(printf 'origin\tsource_url\tretrieved\tline_kind\tline\texpected_key\texpected_dimension\texpected_amount\texpected_unit\n')` prints nothing.
  - `awk -F'\t' '/^#/{next} !h{h=1;next} {n++; if (NF!=9) bad++; for(i=1;i<=NF;i++) if($i=="") bad++} END{print n, bad+0}' "$C"` prints `N 0` with N ≥ 50.
  - `awk -F'\t' '/^#/{next} !h{h=1;next} $1!="HAND_TRANSCRIBED" || (($7=="UNQUANTIFIED") != ($8=="-" && $9=="-")) || ($9!="-" && $9 !~ /^(g|kg|mg|oz|lb|ml|l|cl|tsp|tbsp|cup|fl_oz|pint|clove|tin|can|bunch|bulb|slice|sprig|stick|sheet|packet|each)$/)' "$C"` prints nothing.
  - `awk -F'\t' '/^#/{next} !h{h=1;next} {split($2,a,"/"); print a[3]}' "$C" | sort -u | wc -l` prints at least `5`; `awk -F'\t' '/^#/{next} !h{h=1;next} {print $7}' "$C" | sort -u` prints all four dimensions.
  - `awk -F'\t' '$1=="F5_HARVESTED"' "$E" | wc -l` prints `1`; `diff <(awk -F'\t' '$1!="F5_HARVESTED"' "$E") "$C"` prints nothing.
  - Manual, repeatable: the developer confirms every row was transcribed from its page and its expected values written by hand (R8 AC5), and commits both files before T8 or T10 begins; `git log --oneline -- "$C"` then shows that commit.

### - [x] T5: Shared corpus loader `ParseFidelityCorpus` and loader-level corpus tests

- **Requirement:** R8
- **Description:** Create `ie.pantry.testutil.ParseFidelityCorpus` (I7) with `CorpusEntry`, `CorpusOrigin`, `LineKind`: a plain-Kotlin, classpath-reading TSV loader (no `org.json`, no dependency on any `ie.pantry.domain.ingredient` test class) that maps `<absent>` to `CanonicalKey.Absent`, `-` to null columns, `a/b` to a `Double`, and fails with `IllegalStateException("parse_fidelity_corpus: malformed row $row")`; start `ParseFidelityCorpusTest` (Robolectric runner, per design) with the loader and coverage-floor cases that need no engine.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/domain/ingredient/Quantity.kt` — `CanonicalKey`, `QuantityColumns`
  - Read: `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv` — the row format in practice
  - Create: `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt`
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/ParseFidelityCorpusTest.kt`
- **Dependencies:** T3, T4
- **Parallel:** Yes (with T6–T14, T16) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the corpus
    WHEN loaded with `ParseFidelityCorpus.load()`
    THEN it returns ≥ 50 `HAND_TRANSCRIBED` entries in file order from ≥ 5 distinct `source_url` hosts, each carrying its line, origin, source and date (R8 AC1)
  - GIVEN the loaded expected values
    WHEN counted
    THEN `MASS`, `VOLUME`, `COUNT` and `UNQUANTIFIED` are each present, ≥ 1 entry is `NON_INGREDIENT` or `CanonicalKey.Absent`, and ≥ 1 expected amount is non-integer (R8 AC2, except the alias-hit floor, which needs the engine and is in T15)
  - GIVEN `parse_fidelity_corpus_with_f5_entry.tsv`
    WHEN loaded through the same loader
    THEN `filter { it.origin == CorpusOrigin.F5_HARVESTED }` finds exactly one entry, with no Kotlin change (R8 AC4)
- **Tests:**
  - `` `corpus holds at least fifty hand transcribed entries from at least five hosts`() `` — R8 AC1
  - `` `absence markers load as explicit absence values`() `` — `<absent>` → `Absent`, `-` → (`null`, `null`, `UNQUANTIFIED`)
  - `` `corpus meets the expected outcome coverage floor`() `` — R8 AC2 (dimension, non-ingredient/absent, fraction)
  - `` `extension fixture adds exactly one F5 harvested entry filterable by origin`() `` — R8 AC4
  - File: `app/src/test/java/ie/pantry/domain/ingredient/ParseFidelityCorpusTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.ParseFidelityCorpusTest'`; `grep -c 'org.json' app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt` prints `0`; `grep -oP '^import ie\.pantry\.domain\.ingredient\.\K\w+' app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt | while read c; do test -f "app/src/test/java/ie/pantry/domain/ingredient/$c.kt" && echo "test-class import: $c"; done` prints nothing.

## Phase 3: Entry Point and Guards (FC2; step 3)

### - [x] T6: Entry-point skeleton: sealed `ScannedLine`, `scan`, catch-all `parse`, stubs, `EntryPointTest`

- **Requirement:** R1
- **Description:** Create `IngredientEngine.kt` (public `(AliasTable)` and internal `(aliasLookup)` constructors, `internal sealed interface ScannedLine`, `private class Scanned`, private `scan` running AD8 N1–N6, `MAX_LINE_CHARS = 1_000`, private `UNREADABLE`, `parse` inside AD12's unbound catch-all) and stub `QuantityParser.read(ScannedLine)` / `CanonicalKeyRule.resolve(ScannedLine, …)` that return `Unquantified`/`Absent`, then add `EntryPointTest` (design Testing Strategy row). If the `private class` implementing the `internal sealed interface` does not compile under Kotlin 2.1.0, apply DR2's fallback (`sealed class` with a `private` constructor) and log an Implementation Deviation.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/reference/ReferenceTables.kt` — `AliasTable.lookup`
  - Read: `app/src/test/java/ie/pantry/testutil/RepoPaths.kt` — repository-root resolution for source inspection
  - Create: `app/src/main/java/ie/pantry/domain/ingredient/IngredientEngine.kt`
  - Create: `app/src/main/java/ie/pantry/domain/ingredient/QuantityParser.kt`
  - Create: `app/src/main/java/ie/pantry/domain/ingredient/CanonicalKeyRule.kt`
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/EntryPointTest.kt`
- **Dependencies:** T2
- **Parallel:** Yes (with T3, T4, T5, T12, T13) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the concatenated main sources of `ie.pantry.domain.ingredient`
    WHEN `rawTextAcceptors` scans them
    THEN the result is exactly `["IngredientEngine.parse"]`; on inline negative-control snippets it flags `internal fun keyOnly(line: String)`, `fun String.quantityOnly()` and a three-line parameter list, and does not flag `private fun helper(line: String)` (R1 AC1; AD4; DR4)
  - GIVEN `ScannedLine` and `IngredientEngine`
    WHEN reflected on
    THEN `ScannedLine` is `sealed` with a single `private` implementer, and `IngredientEngine::class.java.methods` has exactly one public method with a `String` parameter, `parse` (R1 AC1)
  - GIVEN `""`, whitespace, `"a".repeat(10_000)`, control characters, emoji, `1/0 g`, `0 g`, `-2 eggs`, `1e999 g`, `99999999999999999999 g` and `½½ tsp`
    WHEN each is parsed
    THEN each returns a `ParsedLine` with `Quantity.Unquantified` and none throws; every case except `-2 eggs` gives `CanonicalKey.Absent` (R1 AC3; the `-2 eggs` → `egg` key is asserted in T11 once parser and rule exist)
- **Tests:**
  - `` `only IngredientEngine parse accepts raw line text`() `` — R1 AC1 source inspection
  - `` `raw text detector flags internal helpers String receivers and multi line parameter lists`() `` — negative controls that must be flagged
  - `` `raw text detector ignores a private helper`() `` — negative control that must not be flagged
  - `` `scanned line is sealed with a single private implementer`() `` — AD4 gate
  - `` `IngredientEngine exposes exactly one public method taking a String`() `` — reflection
  - `` `every R1 edge case returns a typed result without throwing`() `` — R1 AC3, all eleven unquantified
  - `` `edge cases with no name text give an absent key`() `` — R1 AC3, ten cases
  - File: `app/src/test/java/ie/pantry/domain/ingredient/EntryPointTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.EntryPointTest'`; `grep -c 'Regex\|toRegex' app/src/main/java/ie/pantry/domain/ingredient/IngredientEngine.kt` prints `0` (AD7).

### - [x] T7: `EnginePurityTest` and the source-inspection half of `EngineErrorHygieneTest`

- **Requirement:** R9, R10
- **Description:** Add `EnginePurityTest` (R9 AC1 import/FQN scan, AC2 runner reflection over the six JVM classes) and `EngineErrorHygieneTest` with R10 AC2's source inspection and the constructor-guard `assertNoSentinel()` cases, so the guards exist before any parsing logic.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/Sentinels.kt` — `Sentinels`, `assertNoSentinel()`
  - Read: `app/src/test/java/ie/pantry/data/reference/ReadOnlySurfaceTest.kt` — the house source-inspection pattern
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/EnginePurityTest.kt`
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/EngineErrorHygieneTest.kt`
- **Dependencies:** T6
- **Parallel:** Yes (with T3, T4, T5, T8–T15) — creates only its own two test files; it reads, never edits, the main sources
- **Acceptance Criteria:**
  - GIVEN every `.kt` file under `app/src/main/java/ie/pantry/domain/ingredient/`
    WHEN inspected
    THEN none contains `android.` or `org.json`, and the only `import ie.pantry.data.` lines are `reference.AliasTable`, `reference.Lookup` and `db.entity.QuantityDimension` (R9 AC1)
  - GIVEN `EntryPointTest`, `CanonicalKeyRuleTest`, `QuantityParserTest`, `ConversionScalingMergeTest`, `EngineErrorHygieneTest` and `EnginePurityTest`
    WHEN reflected on
    THEN none carries `@RunWith(RobolectricTestRunner::class)` (R9 AC2)
  - GIVEN the main sources
    WHEN inspected
    THEN there is no `throw`, no `error(`, no `Log`, `println`, `printStackTrace` or `System.err`, and every `require`/`check`/`requireNotNull`/`checkNotNull` message is a string literal with no `$`; the `IllegalArgumentException`s from `Measured(0.0, G)` and `Derived("")` pass `assertNoSentinel()` (R10 AC2) [CFC-4]
- **Tests:**
  - `` `main sources reference neither android nor org json`() `` — R9 AC1
  - `` `data layer imports are limited to AliasTable Lookup and QuantityDimension`() `` — R9 AC1
  - `` `behaviour test classes do not use the Robolectric runner`() `` — R9 AC2
  - `` `main sources contain no throw error call or logging`() `` — R10 AC2
  - `` `every require and check message is a constant literal`() `` — R10 AC2
  - `` `constructor guard exceptions carry no sentinel`() `` — R10 AC2
  - Files: `EnginePurityTest.kt`, `EngineErrorHygieneTest.kt` under `app/src/test/java/ie/pantry/domain/ingredient/`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.EnginePurityTest' --tests 'ie.pantry.domain.ingredient.EngineErrorHygieneTest'`; `grep -rn 'import android\.\|import org\.json' app/src/main/java/ie/pantry/domain/ingredient/ || echo clean` prints `clean`.

## Phase 4: Parsing Logic (FC3, FC4; steps 4–5)

> FC3's grammar table order is normative (DR1). T8 builds the cursor, the AMOUNT grammar and the fallback/basic rows; T9 inserts the remaining rows **ahead of** G8 in table order. Through T8–T10 the other half of `parse` may still be a stub, so `QuantityParserTest` asserts only quantities and T10 asserts keys only on quantity-free lines; the spec's exact mixed lines are asserted end to end in T11.

### - [x] T8: Quantity parser: word cursor, AMOUNT grammar, validity, rows G1, G8, G9, G13

- **Requirement:** R4
- **Description:** Replace the `QuantityParser.read` stub with the private word cursor (digit↔letter splitting), the AMOUNT grammar (digits ≤ 6 + ≤ 6, decimal point, `a/b`, vulgar fractions, `1½`, `1 1/2`, `1 ½`), post-match validity (finite, `> 0`, non-zero denominator), malformed-word consumption, multi-word spellings before single ones, and rows G1, G8 (with optional trailing `.` and count unit), G9 and G13; `String.toDouble()` is only ever given a validated digit run (AD12).
- **Files:**
  - Read: `app/src/main/java/ie/pantry/domain/ingredient/Units.kt` — spellings
  - Read: `app/src/main/java/ie/pantry/domain/ingredient/IngredientEngine.kt` — `ScannedLine` text after N1–N6
  - Modify: `app/src/main/java/ie/pantry/domain/ingredient/QuantityParser.kt`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
- **Dependencies:** T3, T4, T6
- **Parallel:** Yes (with T5, T7, T10, T12, T13) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `500g plain flour`, `1 kg potatoes`, `8 oz cheddar`, `1 lb beef mince`
    WHEN parsed
    THEN each is `MASS` with 500 g, 1 kg, 8 oz and 1 lb (R4 AC1)
  - GIVEN `150ml milk`, `1 l chicken stock`, `2 tbsp olive oil`, `1½ tsp ground cumin`, `1 cup rice`, and `½`, `1/2`, `1 1/2` forms
    WHEN parsed
    THEN each is `VOLUME` with 150 ml, 1 l, 2 tbsp, 1.5 tsp and 1 cup, and the fractions read 0.5, 0.5 and 1.5 (R4 AC2)
  - GIVEN `3 eggs`, `3 cloves garlic, crushed`, `1 tin chickpeas`
    WHEN parsed
    THEN each is `COUNT`: 3 with no count unit, 3 `CLOVE`, 1 `TIN` (R4 AC3)
  - GIVEN `0 g sugar`, `1/0 g`, `1e999 g`, `½½ tsp`, a 20-digit amount and a 7-digit integer amount
    WHEN parsed
    THEN each is `Unquantified`; a 6-digit integer amount is read (AD9; `[SEAL-08]` boundary)
- **Tests:**
  - `` `metric and imperial mass lines parse to MASS`() `` — R4 AC1
  - `` `volume lines parse to VOLUME with fractions read exactly`() `` — R4 AC2
  - `` `counted lines parse to COUNT with their count unit`() `` — R4 AC3 (G9, G13)
  - `` `every measure unit spelling parses including trailing dot and fl oz`() `` — DM4 coverage
  - `` `every count unit parses in singular and plural`() `` — DM5 coverage
  - `` `a stated size followed by a container word is measured`() `` — G8 with count unit (`400g tin`)
  - `` `zero zero denominator and malformed amounts are unquantified`() `` — validity and malformed rules
  - `` `six digit amounts are read and seven digit amounts are not`() `` — AD9 digit cap
  - File: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
- **Verification:** Pre-start: `git log --oneline -- app/src/test/resources/ingredient/parse_fidelity_corpus.tsv` shows T4's commit. Then `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.QuantityParserTest' --tests 'ie.pantry.domain.ingredient.EntryPointTest'`; `grep -c 'Regex\|toRegex' app/src/main/java/ie/pantry/domain/ingredient/QuantityParser.kt` prints `0`.

### - [x] T9: Quantity parser: rows G2–G7 and G10–G12 in table order

- **Requirement:** R4
- **Description:** Add rows G2 (lead words `a, an, some, few, several`, never read as 1), G3 (negative), G4 (range), G5 (decimal comma), G6 (`N x M unit`), G7 (parenthetical size × count), G10 (vague measure), G11 (`<amount> <unknown word> of`) and G12 (two numbers that are not a mixed number) ahead of G8 in FC3's table order, each consuming its words as the table states.
- **Files:**
  - Modify: `app/src/main/java/ie/pantry/domain/ingredient/QuantityParser.kt`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
- **Dependencies:** T8
- **Parallel:** Yes (with T5, T7, T10, T12, T13) — disjoint files
- **Acceptance Criteria:**
  - GIVEN `2 x 400g tins chopped tomatoes` and `1 (400 g) tin chickpeas`
    WHEN parsed
    THEN the quantities are `MASS` 800 g and 400 g (R4 AC4; Q6); `1 tin (400g) chickpeas` and `2x400g` also read as stated
  - GIVEN `salt, to taste`, `a pinch of salt`, `a handful of basil`, `some tomatoes`, `2-3 carrots`, `1,5 kg flour`, `0 g sugar`, `a knob of butter` and `3 splodges of ketchup`
    WHEN parsed
    THEN each quantity is `Quantity.Unquantified` (R4 AC5) [CFC-2]
  - GIVEN `a tin of chickpeas`, `an onion`, `-2 eggs`, `2 to 3 carrots`, `2 pinches salt` and `2 400g tins tomatoes`
    WHEN parsed
    THEN each is `Unquantified`, and `2 400g tins` is not read as `COUNT` 2 (design Q2; G3, G4, G10, G12; DR1)
- **Tests:**
  - `` `a multiplier multiplies two confident numbers`() `` — G6, R4 AC4
  - `` `a parenthetical size takes precedence over its container count`() `` — G7, R4 AC4
  - `` `the R4 unquantified lines all give the unquantified value`() `` — R4 AC5, nine lines
  - `` `lead word amounts are never read as one`() `` — G2, design Q2
  - `` `negative ranged and decimal comma amounts are unquantified`() `` — G3, G4, G5
  - `` `vague and unknown units are unquantified`() `` — G10, G11
  - `` `two numbers that are not a mixed number are unquantified`() `` — G12 precedence over G13 (DR1)
  - File: `app/src/test/java/ie/pantry/domain/ingredient/QuantityParserTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.QuantityParserTest' --tests 'ie.pantry.domain.ingredient.EntryPointTest'`

### - [x] T10: Canonical-key rule K1–K8 with fixture-table tests

- **Requirement:** R2
- **Description:** Replace the `CanonicalKeyRule.resolve` stub with AD10's K1–K8 (parentheses, comma cut, hyphen fold, stage-1 lookup of the name phrase, protected phrases `chopped tomato(es)` → `chopped tomato`, preparation phrases and the 22 words, last-word `singularise`, stage-2 lookup), each lookup matched with an exhaustive `when` on `Lookup`.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/data/reference/Lookup.kt` — `Found`/`Absent`
  - Modify: `app/src/main/java/ie/pantry/domain/ingredient/CanonicalKeyRule.kt`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/CanonicalKeyRuleTest.kt`
- **Dependencies:** T4, T6
- **Parallel:** Yes (with T3, T5, T7, T8, T9, T12, T13) — disjoint files
- **Acceptance Criteria:** **[ASSUMPTION]** Asserted here on quantity-free lines through `IngredientEngine(fixtureTable).parse`, none starting with a lead word, so they hold whether or not T8/T9 have landed; the spec's exact quantity-bearing lines are asserted in T11.
  - GIVEN an empty fixture table
    WHEN `  Red  ONION `, `large onions, finely chopped`, `ripe tomatoes`, `cherries`, `Tomato purée`, `self-raising flour`, `couscous` and `asparagus` are parsed
    THEN the keys are `red onion`, `onion`, `tomato`, `cherry`, `tomato puree`, `self raising flour`, `couscous` and `asparagus` (R2 AC1, AC2 by the rule alone)
  - GIVEN a fixture table holding only `bay leaves` → `bay leaf` and `philadelphia` → `cream cheese`, wrapped in a counting lookup
    WHEN `bay leaves`, `Philadelphia` and `carrots` are parsed
    THEN the keys are `bay leaf`, `cream cheese` and `carrot` and exactly 2 lookups hit (R2 AC3)
  - GIVEN `""`, `, chopped`, `finely chopped` and `to taste`
    WHEN parsed
    THEN the key is `CanonicalKey.Absent` (R2 AC5) [CFC-2]
  - GIVEN `tomatoes`, `tomato purée`, `chopped tomatoes`, `salt and pepper` and `For the sauce:`
    WHEN parsed with an empty table
    THEN the keys are `tomato`, `tomato puree`, `chopped tomato`, `salt and pepper` and `for the sauce` (R2 AC6; Q2 protected phrase; design Q1 folding)
- **Tests:**
  - `` `case whitespace and diacritics are folded`() `` — R2 AC1
  - `` `preparation and size words are removed`() `` — R2 AC1
  - `` `the last word is singularised by the Q2 suffix rules`() `` — `-ies`, `-oes`, `-ches`/`-shes`/`-sses`/`-xes`, `-s`, and `-ss`/`-us`/`-is` kept
  - `` `hyphens become spaces`() `` — R2 AC2
  - `` `comma tails and parenthesised text are dropped`() `` — K1, K2, unbalanced `(`
  - `` `the alias table overrides only the irregular plural and the brand name`() `` — R2 AC3 hit count
  - `` `a stage one hit wins over the rule and the protected phrase`() `` — AD10 K4 ordering
  - `` `chopped tomatoes is a protected phrase`() `` — Q2/Q3
  - `` `no remaining name text gives the absent key`() `` — R2 AC5
  - `` `a product is never folded into its base ingredient`() `` — R2 AC6
  - File: `app/src/test/java/ie/pantry/domain/ingredient/CanonicalKeyRuleTest.kt`
- **Verification:** Pre-start: `git log --oneline -- app/src/test/resources/ingredient/parse_fidelity_corpus.tsv` shows T4's commit. Then `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.CanonicalKeyRuleTest' --tests 'ie.pantry.domain.ingredient.EntryPointTest'`

### - [x] T11: Spec R2/R7 lines end to end through `parse`

- **Requirement:** R1, R2, R4, R7
- **Description:** With parser and rule both complete, assert the spec's exact R2 lines, the `-2 eggs` key from R1 AC3, that no grammar row leaks quantity words into the key, and R7's pairs as actually parsed lines; fix any defect this exposes in `QuantityParser.kt` or `CanonicalKeyRule.kt`.
- **Files:**
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/CanonicalKeyRuleTest.kt`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/EntryPointTest.kt`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/ConversionScalingMergeTest.kt`
  - Modify (only if a test exposes a defect): `app/src/main/java/ie/pantry/domain/ingredient/QuantityParser.kt`, `app/src/main/java/ie/pantry/domain/ingredient/CanonicalKeyRule.kt`
- **Dependencies:** T9, T10, T12
- **Parallel:** Yes (with T5, T7, T13) — disjoint files
- **Acceptance Criteria:**
  - GIVEN an empty fixture table
    WHEN `  Red  ONION `, `2 large onions, finely chopped`, `500g carrots`, `4 ripe tomatoes`, `a handful of cherries`, `Tomato purée`, `self-raising flour`, `200g couscous` and `1 bunch asparagus` are parsed
    THEN the keys are `red onion`, `onion`, `carrot`, `tomato`, `cherry`, `tomato puree`, `self raising flour`, `couscous` and `asparagus` (R2 AC1, AC2)
  - GIVEN the R2 AC3 fixture table under a counting lookup
    WHEN `2 bay leaves`, `200g Philadelphia` and `3 carrots` are parsed
    THEN the keys are `bay leaf`, `cream cheese` and `carrot`, with exactly 2 hits (R2 AC3)
  - GIVEN `2 tbsp`, `500 g`, `""` and `, chopped`, and the pair `400 g tomatoes` / `2 tbsp tomato purée`
    WHEN parsed
    THEN the first four give `CanonicalKey.Absent` and the pair gives `tomato` and `tomato puree` (R2 AC5 [CFC-2], AC6)
  - GIVEN one line per consuming grammar row (for example `0 g sugar`, `3 splodges of ketchup`, `a tin of chickpeas`, `2 x 400g tins chopped tomatoes`, `2-3 carrots`, `1,5 kg flour`, `2 400g tins tomatoes`) and `-2 eggs`
    WHEN parsed
    THEN no number or unit word appears in the key, and `-2 eggs` gives `Derived("egg")` with `Unquantified` (FC3 "consumed"; R1 AC3)
  - GIVEN the R7 lines `1 kg potatoes`/`500 g potatoes`, the three tomato/purée/onion pairs, `3 cloves garlic`/`1 bulb garlic`, `salt, to taste`/`a pinch of salt` and `200 g salt`/`salt, to taste`, parsed with an empty table
    WHEN `canMergeWith` and `mergeWith` are called
    THEN only the potato and salt/salt-unquantified pairs merge (1500 g within 1e-9; `Unquantified`), and every other pair gives `NotMergeable` (R7 AC1–AC5)
- **Tests:**
  - `` `R2 rule lines give their spec keys with an empty table`() `` — R2 AC1, AC2 (CanonicalKeyRuleTest)
  - `` `R2 alias lines hit the table only for the irregular plural and the brand name`() `` — R2 AC3 (CanonicalKeyRuleTest)
  - `` `quantity only lines give the absent key`() `` — R2 AC5 (CanonicalKeyRuleTest)
  - `` `tomatoes and tomato puree lines keep different keys`() `` — R2 AC6 (CanonicalKeyRuleTest)
  - `` `no quantity segment word leaks into the key`() `` — one line per consuming row (CanonicalKeyRuleTest)
  - `` `a negative count line keeps its name as the key`() `` — R1 AC3 (EntryPointTest)
  - `` `parsed R7 lines merge exactly as the spec states`() `` — R7 AC1–AC5 (ConversionScalingMergeTest)
  - Files: `CanonicalKeyRuleTest.kt`, `EntryPointTest.kt`, `ConversionScalingMergeTest.kt` under `app/src/test/java/ie/pantry/domain/ingredient/`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.*'`

## Phase 5: Operations (FC5; step 6)

### - [x] T12: Conversion, scaling and merge operations

- **Requirement:** R5, R6, R7
- **Description:** Create `QuantityOperations.kt` with top-level `Quantity.convertTo`, `Quantity.scaledBy`, `ParsedLine.mergeWith` and `ParsedLine.canMergeWith = mergeWith(other) is Merged` (I2–I4), keeping the input unit when scaling and on same-unit merges and adding in the base unit on mixed-unit merges (AD3), with every result checked finite and `> 0` before it is wrapped.
- **Files:**
  - Read: `app/src/main/java/ie/pantry/domain/ingredient/Quantity.kt` — result types
  - Read: `app/src/main/java/ie/pantry/domain/ingredient/Units.kt` — `factorToBase`
  - Create: `app/src/main/java/ie/pantry/domain/ingredient/QuantityOperations.kt`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/ConversionScalingMergeTest.kt`
- **Dependencies:** T2
- **Parallel:** Yes (with T3–T10, T13) — disjoint files
- **Acceptance Criteria:** **[ASSUMPTION]** Values are built directly (`ParsedLine(CanonicalKey.Derived("potato"), Quantity.Measured(1.0, MeasureUnit.KG))`) so this task does not wait for the parser; the same spec pairs through `parse` are in T11.
  - GIVEN 1 kg, 1 lb, 1 oz, 1 l, 1 tbsp, 1 tsp and 1 cup
    WHEN each is converted to `G` or `ML`
    THEN the amounts are 1000, 453.59237, 28.349523125, 1000, 15, 5 and 250 within 1e-9 (R5 AC1)
  - GIVEN `MASS` → a `VOLUME` unit, `COUNT` → a `MASS` unit, `COUNT` 3 `CLOVE` → `BULB`, `Unquantified` → any unit, and an overflowing result
    WHEN converted
    THEN each gives `Conversion.NotConvertible` and none throws (R5 AC2, AC3)
  - GIVEN 400 g, 2 tbsp, a bare count of 2 and `Unquantified`
    WHEN scaled by 1.5, and any quantity scaled by 0, a negative, `NaN`, `±Infinity`, or `Measured(1e308, G)` by 1e10
    THEN the results are 600 g, 3 tbsp, 3 and `Unquantified` in the same unit with no rounding; the bad ratios and the overflow give `Scaling.InvalidRatio` (R6 AC1–AC3)
  - GIVEN the R7 pairs as values: same key 1 kg + 500 g; key-differs, dimension-differs, count-unit-differs, measured-with-unquantified, and any `Absent` key (including both `Absent`); two `Unquantified` of one key; 2 tbsp + 1 tbsp
    WHEN `canMergeWith` and `mergeWith` are called both ways
    THEN 1 kg + 500 g gives `MASS` 1500 g within 1e-9, two `Unquantified` merge to `Unquantified`, 2 tbsp + 1 tbsp gives 3 tbsp, every other pair gives `false`/`NotMergeable`, the predicate always agrees with `mergeWith`, and `merge(a, b) == merge(b, a)` on six mergeable pairs (R7 AC1–AC7; Q5, Q7) [CFC-2]
- **Tests:**
  - `` `within dimension conversion to the base unit uses the Q4 factors`() `` — R5 AC1
  - `` `conversion across dimensions or count units is not convertible`() `` — R5 AC2
  - `` `unquantified and overflowing conversions are not convertible`() `` — R5 AC3, non-finite guard
  - `` `a count converts only to its own count unit`() `` — I2 identity
  - `` `scaling multiplies amounts and keeps the unit`() `` — R6 AC1
  - `` `scaling leaves unquantified unchanged`() `` — R6 AC2
  - `` `zero negative NaN infinite and overflowing ratios are invalid`() `` — R6 AC3
  - `` `one kilogram and five hundred grams of one key merge to fifteen hundred grams`() `` — R7 AC1
  - `` `different key dimension or count unit never merges`() `` — R7 AC2, AC3
  - `` `two unquantified lines of one key merge to unquantified`() `` — R7 AC4
  - `` `measured and unquantified lines never merge`() `` — R7 AC5
  - `` `absent keys never merge`() `` — R7 AC6
  - `` `merge is symmetric for every mergeable pair`() `` — R7 AC7
  - `` `same unit merges keep the unit`() `` — AD3
  - `` `the predicate agrees with merge on every pair`() `` — FC5
  - File: `app/src/test/java/ie/pantry/domain/ingredient/ConversionScalingMergeTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.ConversionScalingMergeTest'`

## Phase 6: Shipped-Key Reconciliation (FC6, FC8; step 7)

### - [x] T13: Q3 alias additions and provenance record

- **Requirement:** R3
- **Description:** Append `{"variant": "oat", "canonicalKey": "oats"}` and `{"variant": "baked bean", "canonicalKey": "baked beans"}` to `aliases.json` (DM9; Ask First, developer-confirmed in spec Q3), and extend the `## Aliases` `- **Source:**` line in the provenance record to name the two F3 reconciliation entries, keeping the `- **Retrieved:**` format. `chopped tomato` gets no alias entry (it is a protected phrase, T10).
- **Files:**
  - Read: `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt` — `aliasSelfMaps`, `aliasChains`, `unresolvedAliasTargets` policy
  - Modify: `app/src/main/assets/reference/aliases.json`
  - Modify: `docs/reference-data-provenance.md`
- **Dependencies:** None
- **Parallel:** Yes (with T1–T12, T16) — data and documentation files no other task touches
- **Acceptance Criteria:**
  - GIVEN the edited `aliases.json`
    WHEN F2's `ShippedDatasetsTest` runs unchanged
    THEN it passes: both targets are staples keys, neither entry is a self-map, and neither forms a chain (spec Q3; FC6)
  - GIVEN the edited provenance record
    WHEN `ProvenanceRecordTest` runs
    THEN it passes, and the `## Aliases` Source line names the two entries (FC6)
- **Tests:** Not applicable — data and documentation only; F2's existing `ShippedDatasetsTest` and `ProvenanceRecordTest` re-check both files, and T14 asserts the reconciliation (see Verification).
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.reference.ShippedDatasetsTest' --tests 'ie.pantry.data.reference.ProvenanceRecordTest'`; `grep -cF '{"variant": "oat", "canonicalKey": "oats"}' app/src/main/assets/reference/aliases.json` and `grep -cF '{"variant": "baked bean", "canonicalKey": "baked beans"}' app/src/main/assets/reference/aliases.json` each print `1`; `grep -c '"chopped tomato' app/src/main/assets/reference/aliases.json` prints `0`; `git diff --stat -- app/src/test/java/ie/pantry/data/reference/ specs/F2-reference-data-store-v1-dataset-curation/` prints nothing.

### - [x] T14: `ShippedReferenceTables` and `ShippedKeyReconciliationTest`

- **Requirement:** R2, R3
- **Description:** Create `ie.pantry.testutil.ShippedReferenceTables` (I8), loading each shipped table through `DatasetLoader.loadDataset(AndroidAssetSource(context.assets), ReferenceDataset.X, DatasetParser::parseX)` and failing with `assertIs<LoadResult.Ready<…>>`, and the Robolectric `ShippedKeyReconciliationTest` for R2 AC4 and R3 (no allow-list; failures list the offending keys).
- **Files:**
  - Read: `app/src/test/java/ie/pantry/data/reference/ShippedDatasetsTest.kt` — the asset loading path to repeat
  - Read: `app/src/main/java/ie/pantry/data/reference/ReferenceTables.kt` — table accessors for keys, substitutions, mappings, variants
  - Create: `app/src/test/java/ie/pantry/testutil/ShippedReferenceTables.kt`
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/ShippedKeyReconciliationTest.kt`
- **Dependencies:** T11, T13
- **Parallel:** Yes (with T5, T7, T16) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the shipped alias table
    WHEN `1 eggplant`, `2 zucchini`, `a handful of cilantro`, `200g shrimp`, `3 scallions` and `300g brussel sprouts` are parsed
    THEN the keys are `aubergine`, `courgette`, `coriander`, `prawn`, `spring onion` and `brussels sprout` (R2 AC4)
  - GIVEN every staples key, seasonality key, seasonality substitution, section-mapping key and alias target K
    WHEN `K` is parsed against the shipped alias table
    THEN `parse(K).key == CanonicalKey.Derived(K)`, including `oats`, `baked beans` and `chopped tomato`; the failure message lists every offender (R3 AC1)
  - GIVEN every shipped alias variant V
    WHEN a recording engine over the shipped table parses `V`
    THEN the recording contains a `Found` result for the query `V` at stage K4 or K8 (R3 AC2)
- **Tests:**
  - `` `shipped alias variants give their curated keys`() `` — R2 AC4
  - `` `every shipped staples and seasonality key and substitution is a fixed point`() `` — R3 AC1
  - `` `every section mapping key and alias target is a fixed point`() `` — R3 AC1
  - `` `every shipped alias variant is reachable at a lookup stage`() `` — R3 AC2
  - File: `app/src/test/java/ie/pantry/domain/ingredient/ShippedKeyReconciliationTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.ShippedKeyReconciliationTest'`. Manual, repeatable: read the test and confirm no key is skipped or exempted (no allow-list, R3 AC1). A shipped key other than the three Q3 keys that fails is a spec-level decision routed through the Phase-4 triage gate, never an allow-list.

## Phase 7: CFC-1 Artifacts and Hygiene (FC7, FC8; step 8)

### - [x] T15: CFC-1 corpus enforcement tests: corpus fidelity, cross-package load, cross-instance determinism [CFC-1]

- **Requirement:** R1, R8
- **Description:** Complete the CFC-1 enforcement artifact that PLAN names ("the shared parse-fidelity test corpus owned by F3", design AD14), built on T4's corpus and T5's loader: finish `ParseFidelityCorpusTest`, and add `EntryPointDeterminismTest` and `ParseFidelityCorpusCrossPackageTest` (all three Robolectric, per R9 AC2; the cross-package test is the reference usage pattern that F6's and F11's route tests copy). If a corpus row and the engine disagree, the developer decides which side is wrong. The fix goes through the Phase-4 triage gate and never copies engine output into the corpus.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt` — `load`, `CorpusEntry`
  - Read: `app/src/test/java/ie/pantry/testutil/ShippedReferenceTables.kt` — `aliasTable()`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/ParseFidelityCorpusTest.kt`
  - Create: `app/src/test/java/ie/pantry/domain/ingredient/EntryPointDeterminismTest.kt`
  - Create: `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpusCrossPackageTest.kt`
- **Dependencies:** T5, T14
- **Parallel:** Yes (with T7, T16) — disjoint files
- **Acceptance Criteria:**
  - GIVEN the corpus and the shipped alias table under `./gradlew :app:testDebugUnitTest`
    WHEN each entry's line is parsed
    THEN its key equals the expected key and its `toColumns()` matches the expected triple (dimension and unit `==`, amount within 1e-9) (R8 AC1) [CFC-1]
  - GIVEN a counting engine over the shipped table
    WHEN the corpus is parsed
    THEN at least one entry produces a `Found` hit (R8 AC2 alias-hit floor)
  - GIVEN two `IngredientEngine` instances, each over its own `ShippedReferenceTables.aliasTable()` load
    WHEN every corpus line is parsed by both
    THEN the `ParsedLine`s are `==` and their `toColumns()` are `==` with exact `Double` equality (R1 AC2; AD14 byte-identical) [CFC-1]
  - GIVEN `ParseFidelityCorpusCrossPackageTest` in `ie.pantry.testutil`, importing only `ParseFidelityCorpus`, `ShippedReferenceTables` and F3 main types
    WHEN it loads and drives every entry through `IngredientEngine(ShippedReferenceTables.aliasTable()).parse`
    THEN it gets every entry with its line and expected values, and each matches (R8 AC3)
- **Tests:**
  - `` `every corpus line parses to its expected key and columns against the shipped alias table`() `` — R8 AC1 (ParseFidelityCorpusTest)
  - `` `at least one corpus entry resolves through the alias table`() `` — R8 AC2 (ParseFidelityCorpusTest)
  - `` `two engines over separately loaded shipped tables give identical results for every corpus line`() `` — R1 AC2 (EntryPointDeterminismTest)
  - `` `corpus loads from outside the engine package with every expected value`() `` — R8 AC3 (ParseFidelityCorpusCrossPackageTest)
  - `` `each corpus entry driven through the engine matches its expected values`() `` — R8 AC3 reference usage (ParseFidelityCorpusCrossPackageTest)
  - Files: `app/src/test/java/ie/pantry/domain/ingredient/ParseFidelityCorpusTest.kt`, `app/src/test/java/ie/pantry/domain/ingredient/EntryPointDeterminismTest.kt`, `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpusCrossPackageTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.ParseFidelityCorpusTest' --tests 'ie.pantry.domain.ingredient.EntryPointDeterminismTest' --tests 'ie.pantry.testutil.ParseFidelityCorpusCrossPackageTest'`; `grep -oP '^import ie\.pantry\.domain\.ingredient\.\K\w+' app/src/test/java/ie/pantry/testutil/ParseFidelityCorpusCrossPackageTest.kt | while read c; do test -f "app/src/test/java/ie/pantry/domain/ingredient/$c.kt" && echo "test-class import: $c"; done` prints nothing. Manual, repeatable (R8 AC5): the developer confirms that no expected value was changed to match engine output, and records each corpus fix in Implementation Deviations.

### - [x] T16: `EngineErrorHygieneTest` sentinel and throwing-seam cases

- **Requirement:** R10
- **Description:** Complete `EngineErrorHygieneTest` R10 AC1: lines containing `Sentinels.INGREDIENT` that reach every failure value, operations driven into each of their failures, and an engine built on a lookup that throws `RuntimeException(Sentinels.INGREDIENT)`.
- **Files:**
  - Read: `app/src/test/java/ie/pantry/testutil/Sentinels.kt` — `Sentinels.all`, `assertNoSentinel()`
  - Modify: `app/src/test/java/ie/pantry/domain/ingredient/EngineErrorHygieneTest.kt`
- **Dependencies:** T7, T11
- **Parallel:** Yes (with T5, T13, T14, T15) — only file is its own test class
- **Acceptance Criteria:**
  - GIVEN sentinel-bearing lines built to give key-absent and unquantified through G2–G5, G10–G12, a zero amount, a malformed word and each R1 numeric edge case
    WHEN parsed, and the resulting values then drive `convertTo`, `scaledBy`, `mergeWith` and `QuantityColumns.decode()` into `NotConvertible`, `InvalidRatio`, `NotMergeable` and `Inconsistent`
    THEN no exception escapes, and no failure value's `toString()` contains any `Sentinels.all` value (compared with `ignoreCase = true`) (R10 AC1) [CFC-4]
  - GIVEN an engine whose lookup throws `RuntimeException(Sentinels.INGREDIENT)`
    WHEN a line with name text is parsed
    THEN it returns `ParsedLine(CanonicalKey.Absent, Quantity.Unquantified)` and nothing is thrown (R10 AC1, AC2; AD12)
- **Tests:**
  - `` `sentinel lines reach every absence value without leaking`() `` — R10 AC1, parse side
  - `` `operation failures built from sentinel lines carry no sentinel`() `` — R10 AC1, operation side
  - `` `a throwing alias lookup yields an unreadable line and nothing escapes`() `` — R10 AC2, AD12 catch-all
  - File: `app/src/test/java/ie/pantry/domain/ingredient/EngineErrorHygieneTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.EngineErrorHygieneTest'`

## Phase 8: Closeout (step 9)

### - [x] T17: Feature closeout: F3 filter, F2 regression, full suite, lint, purity grep

- **Requirement:** R1–R10
- **Description:** Run the spec's Commands, confirm the design's unchanged-file list is untouched, and walk the spec's Success Criteria.
- **Files:**
  - Read: `specs/F3-ingredient-normalisation-quantity-engine/01_spec.md` — Commands and Success Criteria
  - Read: `specs/F3-ingredient-normalisation-quantity-engine/02_design.md` — File Structure's "These files are not changed" sentence
- **Dependencies:** T1–T16
- **Parallel:** No — final gate
- **Acceptance Criteria:**
  - GIVEN the finished working tree
    WHEN the F3 filter, `ShippedDatasetsTest`, the full `./gradlew :app:testDebugUnitTest` and `./gradlew lintDebug` run
    THEN all pass, with every F1 and F2 test green (spec Success Criteria)
  - GIVEN `PantryDatabase`, the entities, `app/schemas/`, `AppContainer`, `AndroidManifest.xml`, `gradle/libs.versions.toml` and F2's tests
    WHEN diffed against the F2 baseline
    THEN none has changed (design File Structure; spec Network Exposure Triage)
  - GIVEN `app/build.gradle.kts`
    WHEN diffed against the F2 baseline `d3fb8b2`
    THEN the only change is the 11 added lines inside the existing `tasks.withType<Test>` block (`git diff --numstat d3fb8b2 -- app/build.gradle.kts` reads `11 0`), as recorded in the spec's Modified Files
- **Tests:** Not applicable — runs the existing suites; see Verification.
- **Verification:** `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.domain.ingredient.*' --tests 'ie.pantry.testutil.ParseFidelityCorpusCrossPackageTest'`; `./gradlew :app:testDebugUnitTest --tests 'ie.pantry.data.reference.ShippedDatasetsTest'`; `./gradlew :app:testDebugUnitTest lintDebug`; `grep -rn 'import android\.\|import org\.json' app/src/main/java/ie/pantry/domain/ingredient/ || echo clean` prints `clean`; `git status --porcelain app/schemas` prints nothing; `git diff --quiet d3fb8b2 -- app/schemas app/src/main/AndroidManifest.xml gradle/libs.versions.toml app/src/main/java/ie/pantry/data app/src/main/java/ie/pantry/di app/src/test/java/ie/pantry/data` exits 0; `git diff --numstat d3fb8b2 -- app/build.gradle.kts` prints `11\t0\tapp/build.gradle.kts`; `grep -n 'systemProperty("maven.repo.local"' app/build.gradle.kts` prints exactly one hit; `ls .toolchain/m2/repository/org/robolectric/android-all-instrumented` lists a directory; and the full `./gradlew :app:testDebugUnitTest` runs with `.toolchain/m2/repository` present. Manual, repeatable: the developer ticks each spec Success Criteria line against the passing test that proves it.

## Implementation Order

1. T1, T4, T13: parallel start. T1 lays down the unit enums. The developer starts transcribing the corpus, which must be committed before T8 and T10 (DR5). The Q3 data edit has no code dependency.
2. T2: the sealed result types on T1's units. Every later code task depends on them.
3. T3, T6, T12: parallel after T2. T3 adds column mapping, T6 adds the entry-point skeleton and retires DR2 before any logic exists (step 3), and T12 adds operations, which design step 6 allows any time after step 1.
4. T5, T7: T5 builds the loader on T3 and T4, and T7 adds the purity and hygiene guards on T6.
5. T8 → T9, in parallel with T10. The parser is the highest-risk step (DR1). T9 edits the same files as T8, so it runs after it. The rule (T10) needs only the skeleton and the committed corpus.
6. T11: the spec's exact R2/R7 lines through the full `parse`, once parser, rule and operations are all in.
7. T14: shipped-key reconciliation needs the full rule (T11) and the Q3 alias entries (T13).
8. T15, T16: parallel. T15 builds the CFC-1 enforcement tests on the corpus, the loader and the shipped-table helper. T16 finishes the hygiene cases.
9. T17: closeout gate.

## Implementation Deviations

> Phase-4 minor-deviation ledger — populated by the triage gate's minor path (`SKILL.md` § "Mid-implementation discovery"). Append-only during Phase 4; resolved at the Final-Check completion gate. Leave empty until a deviation is logged.

| Date | Task | What spec/design said | What was actually done | Why | Classification | Backport status |
|------|------|-----------------------|------------------------|-----|----------------|-----------------|
| 2026-09-30 | (pre-T1, sandbox setup) | design.md's File Structure lists `app/build.gradle.kts` among files F3 does not touch | Added an 11-line, environment-conditional block to `app/build.gradle.kts` (`tasks.withType<Test>`) that points Robolectric's dependency resolver at a local Maven-layout repo (`.toolchain/m2/repository`) via the standard `maven.repo.local` system property, but only when that directory exists | This sandboxed devcontainer has no route to Maven Central, so Robolectric's runtime `android-all-instrumented` SDK-jar fetch fails outright; without this, no Robolectric test (T5, T13, T14, T15, and F1/F2's own shipped-dataset tests) could run at all in this environment. The change is a no-op on any machine without `.toolchain/m2/repository` (the original developer's machine, CI), so it does not alter build behaviour anywhere else. Disclosed to and confirmed working with the developer earlier in this implementation session. | Minor — no external interface, dependency, or security/privacy surface changed. The backport amended T17's acceptance criterion (AC3) and adds a suite-wide effect on F1/F2 Robolectric tests, recorded in the spec's Modified Files; purely conditional test-infrastructure wiring. | backported — developer decision at the T17 completion gate; design File Structure, Integration Points and spec Triage / Modified Files updated |

## TDD Exceptions

> Phase-4 TDD-cycle-skip log — appended by the calling Claude during Phase 4 when the test-first red→green→refactor cycle is skipped for a code-stack task. Append-only during Phase 4; `Resolution`-column updates (`pending` → `accepted` or `remediate`) are resolved at the Final-Check completion gate. Leave empty until a skip is logged. **Code stacks (python/java/kotlin) only.** Not applicable for generic-profile tasks. (A code task that genuinely needs *no* test at all is not a skip — use the `**Tests:** none — <reason>` override instead.)

| Date | Task | Skip Reason | Resolution |
|------|------|-------------|------------|

## Open Questions

> All questions must be resolved before proceeding to implementation.

None. Spec Q1–Q10 and design Q1–Q3 are all resolved, and this breakdown raises no new blocking question.

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
| 1    | 2026-09-29 | 0     | 0           | 0         | 0        | 14     | converged (0 HIGH); tags=d0u0c0 |
| 2    | 2026-10-04 | 6     | 0           | 10        | 0        | 6      | tags=d0u0c0 upstream-panel 18d1fd5f |

### Sealed dispositions

- `[SEAL-01]` **Phase 4 header cites design steps "4-5" but T11 (in Phase…** (pass 1, accepted-as-risk) — Defense: T11's own Dependencies field (T9, T10, T12) is authoritative and correct; the phase-header step-range is descriptive framing, not a dependency source, so the imprecision costs nothing a developer reading T11 directly would miss.
- `[SEAL-02]` **T17's closeout regression check hardcodes baseline commit…** (pass 1, accepted-as-risk) — Defense: `d3fb8b2` is the real, current F2 closeout commit and this repo's history has not been rewritten; if it ever is, T17's Verification step is re-pointed at implementation time — a symbolic-ref indirection is a nicety, not a correctness gap today.
- `[SEAL-03]` **T9 bundles nine grammar rows (G2-G7, G10-G12) into one task…** (pass 1, accepted-as-risk) — Defense: DR1's actual mitigation (one test line per grammar row, corpus authored before either task starts) applies uniformly regardless of how the rows are grouped into tasks; the asymmetry is bundling, not reduced test coverage.
- `[SEAL-04]` **Corpus host-count in design Q3's page list sits exactly at…** (pass 1, accepted-as-risk) — Defense: the developer-supplied Q3 page list was confirmed against the ≥5-host floor at design approval (2026-09-29); if a transcription ever drops a line short, T5's own coverage-floor test (R8 AC1) fails loudly at that task, not silently downstream.
- `[SEAL-05]` **T9's phase-note says grammar rows G10-G12 are inserted…** (pass 1, accepted-as-risk) — Defense: the phrase is loose framing, not a claim about table position; T9's own Description enumerates the rows in the design's actual G1-G13 order, and per critic's own finding the rows' trigger tokens are mutually exclusive, so no implementer ambiguity results.
- `[SEAL-06]` **T10's assumption that its tests hold regardless of T8/T9's…** (pass 1, accepted-as-risk) — Defense: T6's stub is a temporary placeholder exercised only by `EntryPointTest`'s totality cases, none of which read `nameStart`; T10 flags its own reliance inline as `[ASSUMPTION]`, and any wrong guess is caught by T11's end-to-end re-assertion before Phase 4 closes — self-correcting within the TDD cycle.
- `[SEAL-07]` **T8, T9, T10, T12, T13 don't re-run…** (pass 1, accepted-as-risk) — Defense: purity/hygiene regressions are structural (an import, a `throw`, a log call) that TDD practice surfaces while writing the code, and T17's full-suite gate is the design's own stated final checkpoint (Implementation Sequence step 9) for exactly this cross-cutting class of check.
- `[SEAL-08]` **T1's AC "no unit is COUNT or UNQUANTIFIED" is tautological…** (pass 1, accepted-as-risk) — Defense: the assertion is a cheap, permanent regression guard — if a future edit ever mis-adds a COUNT/UNQUANTIFIED `MeasureUnit`, this is the test that catches it; removing a currently-vacuous check would delete the one test built to catch that specific future mistake.
- `[SEAL-09]` **T6 and T7 split the design's single Implementation Sequence…** (pass 1, accepted-as-risk) — Defense: the split follows a real file boundary (T6 creates main-source stubs; T7 creates test-only files and touches no main file) and lets T7 run in parallel with T3-T5/T8-T15 on just a T6 dependency, which a merged task would prevent by gating T7's guard tests behind T6's larger file set.
- `[SEAL-10]` **T10 and T11 write near-duplicate `CanonicalKeyRuleTest`…** (pass 1, accepted-as-risk) — Defense: the design's own Implementation Sequence wants the rule (step 5) built in parallel with the parser (step 4), which requires T10's temporarily-narrower quantity-free coverage; T11 then supersedes it with the full lines. The duplication is the cost of the parallelism the design itself calls for.
- `[SEAL-11]` **T12's R7 merge pairs are re-asserted end-to-end in T11 on…** (pass 1, accepted-as-risk) — Defense: T11's re-assertion is integration proof that parser and rule compose correctly through the real `parse()` entry point (R1's single-entry-point requirement); T12's direct-construction tests build `ParsedLine` by hand and never exercise `parse()`, so it isn't the same code path.
- `[SEAL-12]` **T4's Verification runs five awk/grep pipelines that…** (pass 1, accepted-as-risk) — Defense: T4's checks assert facts about the raw file before any Kotlin loader exists to test them, catching a malformed corpus at the point the developer commits it rather than as a less-direct T5 failure later — intentional defense-in-depth on hand-authored test data (RK6), not accidental duplication.
- `[SEAL-13]` **T13's Verification stacks four grep/git-diff checks on top…** (pass 1, accepted-as-risk) — Defense: T13's checks assert facts specific to this task's own two-line data edit (the exact new entries, no chopped-tomato self-map, F2's test files untouched) that `ShippedDatasetsTest`/`ProvenanceRecordTest` don't themselves assert — complementary, not duplicated.
- `[SEAL-14]` **T1 and T2 split the design's single FC1 step into two tasks…** (pass 1, accepted-as-risk) — Defense: the split follows a real dependency edge (Quantity.kt's sealed types construct from MeasureUnit/CountUnit, so T2 genuinely depends on T1's output) and a real file boundary (Units.kt vs Quantity.kt) — one task per file/reason-to-change, ordinary granularity.
- `[SEAL-15]` **Corpus-before-parser ordering is not provable from git,…** (pass 2, accepted-as-risk) — Defense: the ordering is evidenced when the user commits; git history cannot show it until then. Synthesizer-judged, not user-confirmed.
- `[SEAL-16]` **No recorded human attestation for R8 AC5 and the corpus** (pass 2, accepted-as-risk) — Defense: the developer's approval of the corpus is in the Phase-4 conversation record ("tsv looks good"); it is not repeated in this file. Synthesizer-judged, not user-confirmed.
- `[SEAL-17]` **Grammar under-specifies count-unit consumption after G4, G5…** (pass 2, accepted-as-risk) — Defense: the corpus rows and QuantityParserTest fix the behaviour for the cases listed, and the tests pass. A grammar wording refinement is outside this closeout. Synthesizer-judged, not user-confirmed.
- `[SEAL-18]` **The build-file change is described in six places** (pass 2, accepted-as-risk) — Defense: the descriptions are consistent after the spec and design backports, and they are cross-referenced rather than duplicated. Synthesizer-judged, not user-confirmed.
- `[SEAL-19]` **Deviation row narrative is long** (pass 2, accepted-as-risk) — Defense: the classification and backport cells carry the audit weight; the narrative is the disclosure record. Synthesizer-judged, not user-confirmed.
- `[SEAL-20]` **Design wording "at line 58" is ambiguous about insertion** (pass 2, accepted-as-risk) — Defense: the design states the block contains the 11 lines; the diff hunk confirms insertion within it. Synthesizer-judged, not user-confirmed.

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to implementation
- **Content Hash:** `80bfe9a8ba23dd13`
- **Hash basis:** v2