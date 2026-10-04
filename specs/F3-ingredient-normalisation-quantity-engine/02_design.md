# Design: Ingredient Normalisation & Quantity Engine

**Spec:** `specs/F3-ingredient-normalisation-quantity-engine/01_spec.md`

## Goals and Non-Goals

**Goals:**
- One public parse entry point, `IngredientEngine.parse(line)`, that is total and deterministic and returns an F3-owned canonical-key result and quantity result (R1; CFC-1). The key rule and quantity parser can be reached only through it, and a sealed type makes that true at compile time, not only by convention (AD4).
- A canonical-key rule plus a two-stage alias lookup (spec Q1, Q2) that makes every shipped F2 key a fixed point once the spec Q3 reconciliation is applied (R2, R3).
- Explicit absence values. A missing key cannot be represented as `""`, and an unquantified quantity cannot hold a number (R2 AC5, R4 AC5; CFC-2).
- Within-dimension conversion, scaling and merge that return typed failure values and never throw (R5–R7).
- A shared parse-fidelity corpus with a loader in `ie.pantry.testutil`, which F6's and F11's CFC-1 route tests import unchanged (R8; CFC-1 Enforcement).
- Plain-Kotlin main sources, and no line text in any failure value or exception message (R9, R10; CFC-4).

**Non-Goals:**
- How F6 persists a key-absent line or makes `RecipeIngredient.canonicalKey` nullable (spec Q8, RK8). F3 only represents the absence.
- Working out the servings ratio and rounding values for display (F11, spec R6).
- Converting across dimensions, density estimates and typical-weight estimates (F9; ARCHITECTURE C4 Boundary).
- The CFC-1 route tests (import-confirm, manual entry, post-save edit). F6 and F11 own them. F3 owns only the corpus, its loader and a same-table determinism test (AD14).
- Reading a quantity that does not lead the line (`chicken stock, 500 ml`). Anything that is not the leading quantity segment is unquantified (spec Terms; ARCHITECTURE R2).
- Detecting section headers (spec Q10) and splitting compound lines such as `salt and pepper` (spec Q2).
- US-customary volume units (spec Q4, RK5).
- Any logging. The engine has no logger, so it cannot log (AD12).

## Architecture Decisions

A bare `Q<n>` in this section and the ones after it means the spec's Open Question of that number. This design's own questions are cited as "design Q1" to "design Q3" (see Open Questions).

| Decision | Choice | Alternatives Rejected | Rationale | Consequences |
|----------|--------|-----------------------|-----------|--------------|
| AD1 — Result-type shapes (spec Decision Point 1) | F3-owned sealed types in `Quantity.kt`. **`CanonicalKey`** is `Derived(value: String)` or `data object Absent`. **`Quantity`** is `Measured(amount, unit: MeasureUnit)`, `Counted(amount, countUnit: CountUnit?)` or `data object Unquantified`. The operation outcomes are also sealed: `Conversion` (`Converted` / `NotConvertible`), `Scaling` (`Scaled` / `InvalidRatio`), `MergeResult` (`Merged` / `NotMergeable`) and `ColumnDecode` (`Decoded` / `Inconsistent`), each failure being a `data object`. `Unquantified` carries **no** reason enum | Reusing `Lookup<String>` for the key result: `Lookup.Found` means "a table hit", but most keys come from the rule, so callers would misread where a key came from, and the public API would be tied to F2's table semantics. A reason enum on `Unquantified`: no consumer needs it, and equal lines with different causes would compare unequal, which complicates merge (Q7) and the corpus's expected values. Nullable returns and `kotlin.Result`: both are ruled out by F2 AD1's rules | F2 AD1's rules hold: every `when` is exhaustive with no `else`, there is no nullable absence and no `kotlin.Result`. A `data object` has no fields, so R2 AC5's "no string field" and R4 AC5's "no numeric property" are structural facts. `Measured` and `Counted` check `amount.isFinite() && amount > 0.0` in `init`, and `Derived` checks `value.isNotBlank()` (constant messages). So a `MASS` 0 g, a `COUNT` 0 and a `""` key **cannot be constructed** | R2 AC5 and R4 AC5 are asserted three ways: construction of the zero or empty value fails, the absence value is unequal to every measured or derived value the test builds, and reflection finds no `String`/`Double` field on `Absent`/`Unquantified`. `CountUnit?` is nullable only for the modelled bare count (`3 eggs`, R4 AC3) |
| AD2 — Numeric representation (Decision Point 2) | **`Double`, in the unit the line stated** (`8 oz` stays 8 oz). Amounts are parsed only from validated digit runs: at most 6 integer digits and at most 6 fractional digits (AD9). The column mapping (AD11) carries the same `Double` | Exact rationals (`BigInteger` pairs): R4 AC6 needs `Double` → quantity → `Double` to round-trip to an **equal** quantity. Starting from a rational such as 1/3, that trip lands on a different, non-equal rational. `BigDecimal`: 1/3 has no finite form, and the F1 column is `Double?` anyway | Parsing is one code path doing plain IEEE arithmetic on the JVM, so equal line text gives bit-identical doubles on every route (CFC-1, R1 AC2). IEEE addition and multiplication are commutative, so `merge(a, b) == merge(b, a)` exactly (R7 AC7) | IEEE addition is **not associative**. Folding three or more quantities can differ in the last bit depending on order (spec RK4). F3's merge is pairwise, so F11 must fold in a fixed, documented order (DR3). Every arithmetic result is checked to be finite and `> 0` before it is wrapped (AD12) |
| AD3 — Unit of scaled and merged quantities (Decision Point 3) | **Scaling keeps the input unit** (R6 AC1: 400 g × 1.5 = 600 g). **Merge keeps the shared unit when both inputs use the same unit.** When the units differ within one dimension, merge converts both inputs to the **base unit** (`G` for `MASS`, `ML` for `VOLUME`) and adds them there. `COUNT` merges only when the count units are equal, so its unit never changes | Always the larger input unit (1.5 kg): this needs a tie rule and a division, which adds a rounding step. Always the base unit: `2 tbsp + 1 tbsp` would come out as `45 ml`, so a same-unit merge would change what the user wrote. A display-unit normaliser: presentation belongs to F11 | Same unit: `a + b`, exact and commutative. Mixed units: `toBase(a) + toBase(b)`, also commutative. R7 AC1: 1000 g + 500 g = 1500 g | F11 converts to a display unit with `convertTo` (I2) if it wants 1.5 kg |
| AD4 — Enforcing R1's single raw-text entry point (Decision Point 4) | **A sealed capability type plus source inspection.** `internal sealed interface ScannedLine { val text: String }` is declared in `IngredientEngine.kt`, and its only implementation is `private class Scanned` in the same file. The only thing that creates a `Scanned` is the private `scan(line)` called from `IngredientEngine.parse`. `QuantityParser.read` and `CanonicalKeyRule.resolve` take a `ScannedLine`, never a `String`. Kotlin allows direct inheritors of a sealed type only in the same package **and** the same module, so no other feature's package can implement `ScannedLine`. **Plus** `EntryPointTest` scans F3's main sources and finds every non-`private` `fun` that has a `String`/`CharSequence` parameter or receiver, or a non-`private` property of a function type that takes a `String`. It asserts that the set is exactly `{IngredientEngine.parse}`, with negative controls (I8) | `private` members in a single file: that puts the scanner, parser, rule and operations into one file of roughly 600 lines, and the spec's file split is lost. `internal` functions that take `String`, guarded only by source inspection: they would still be non-private declarations that accept raw text, which is exactly what R1 AC1 forbids | The rule and parser stay in their own files (spec New Files) but have no input type that raw text can reach, except through `parse`. The inspection covers declarations that the sealed type does not, such as a future `fun String.toKey()` | Style rule, stated in `EntryPointTest`'s KDoc: any helper that takes a `String` must be marked `private` explicitly, even inside a private class, because the regex cannot see nesting. A false positive is fixed by adding `private` (DR4) |
| AD5 — Engine construction (Decision Point 5) | **A class, built once per `AliasTable`.** The public constructor is `IngredientEngine(aliases: AliasTable)`, which delegates to `internal constructor(aliasLookup: (String) -> Lookup<String>)`. The operations (convert, scale, merge, column mapping) do not use the table, so they are **top-level extension functions** that need no engine | Free functions that take the table on every call: each caller would have to thread the table through, and there would be no single object to compare in R1 AC2 ("two separately constructed engine instances"). A `ReferenceDataStore` parameter or a `suspend` API: both are forbidden (spec Always Do; F2 AD2) | The engine holds no mutable state, so two instances over one table are interchangeable and safe to share across threads. The `internal` constructor is a **test seam**. `CanonicalKeyRuleTest` wraps `table::lookup` to count hits (R2 AC3), `ShippedKeyReconciliationTest` records the queries (R3 AC2), and `EngineErrorHygieneTest` injects a lookup that throws (R10 AC2) | `AliasTable` is `final` with an `internal` constructor (verified in `ReferenceTables.kt`), so a function seam is the only way to observe lookups without changing F2. F11 can merge saved rows (read back with `QuantityColumns.decode()`, I5) without building an engine |
| AD6 — Corpus file format (Decision Point 6) | **UTF-8 TSV** at `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv`, read from the classpath by a plain-Kotlin loader, `ie.pantry.testutil.ParseFidelityCorpus`, with no `org.json`. The expected quantity is written **as F1's column triple** (amount, unit token, dimension) (DM8) | JSON through `org.json`: it would work, since every consumer is under Robolectric anyway, but a 50-plus-row hand-authored file reads and diffs worse as JSON, and the loader would depend on the Android `org.json`. A separate expected dimension/amount/unit/count-unit schema: this would duplicate the AD11 mapping in a second vocabulary | One row per line, so each hand-written row is easy to review (RK6). Writing the column triple means that "byte-identical" in CFC-1 is equality of exactly what F6 will persist | A field may never be empty: absence is written as `<absent>` or `-` (CFC-2 in data). Lines must not contain tabs, and the loader rejects a row with the wrong number of fields |
| AD7 — Input-length cap and scanning style (Decision Point 7; spec RK7) | **`MAX_LINE_CHARS = 1_000`**, checked on the raw `line.length` before any work. A longer line returns `ParsedLine(CanonicalKey.Absent, Quantity.Unquantified)` without being scanned. Scanning and parsing are **hand-written, index-based and single-pass**. The engine uses no `Regex` | Truncating to the cap: it would silently change the key of a crafted line. No cap: every step is already linear, but the cap bounds NFD expansion and allocation for untrusted F5 content. A regex-based parser: backtracking is exactly the hazard RK7 names | Real ingredient lines are well under 200 characters. The 10,000-character case in R1 AC3 hits the cap | A line of 1,001 or more characters is "couldn't read" (absent key and unquantified), which is honest under CFC-2. The corpus contains no line near the cap |
| AD8 — Text normalisation (Q2 folding) | The private `scan(line)` runs these steps in order. **N1** check the cap (AD7). **N2** `java.text.Normalizer.normalize(line, NFD)`, then drop every char whose `Character.getType` is `NON_SPACING_MARK` (`é` → `e`). **N3** `lowercase(Locale.ROOT)`. **N4** map each char: letters, digits, `, ( ) / .` and the vulgar fractions `¼ ½ ¾ ⅓ ⅔ ⅛` are kept; `-`, `–` and `—` become `-`; `×` becomes ` x `; the apostrophes `'` and `’` are **removed**; everything else (whitespace, control characters, emoji, `: ; & ! ?` and other symbols) becomes a space. **N5** a `-` with a letter on both sides becomes a space (`self-raising` → `self raising`); any other `-` is kept for the quantity parser (ranges and signs, AD9). **N6** collapse runs of whitespace and trim | NFKD: it would decompose `½` into `1⁄2`, which the fraction grammar would then have to accept as well. Folding every hyphen early: `2-3` would become `2 3`, so the range would be lost | The alias table is only ever queried with folded text (spec Q1: "the table is only ever given normalised text"). A line made only of emoji or control characters becomes empty, which gives an absent key and unquantified (R1 AC3) | **[ASSUMPTION]** Removing apostrophes (`baker's` → `bakers`) and turning `&` and `:` into spaces changes keys (`For the sauce:` → `for the sauce`). Routed to design Q1 |
| AD9 — Quantity-segment grammar (Q4, Q6; R4) | Specified in full under FC3. In summary: only a **leading** segment is read. A line that opens with a **lead word** (`a`, `an`, `some`, `few`, `several`) and has no digit is **unquantified**. **Article amounts are never read as 1** (`a tin of chickpeas` is unquantified). Ranges, signs, a zero, a decimal comma, a malformed glued token, an amount of 7 or more integer digits, a zero denominator, two numbers in a row that do not form a mixed number, and `<amount> <unknown word> of` are all **unquantified**, but their words are **still consumed**, so no unit or number is left in the key | Reading `a`/`an` as 1: the spec's Never Do forbids defaulting a missing amount to 1, and ARCHITECTURE R2 says to drop the number when unsure. Leaving an unreadable segment in the name phrase: `0 g sugar` would then give the key `0 g sugar` | Confident forms are read, and every other form is unquantified without leaving junk in the key | **[ASSUMPTION]** The lead words and the `<unknown word> of` rule add quantity-segment vocabulary beyond Q4, and that vocabulary changes keys (spec RK9). Routed to design Q1. Article-only amounts are routed to design Q2 |
| AD10 — Key resolution order (Q1, Q2, Q3) | `CanonicalKeyRule.resolve` works on the text after the quantity segment. **K1** drop parenthesised spans; an unbalanced `(` drops to the end of the line, and a stray `)` becomes a space. **K2** cut at the first comma. **K3** turn any remaining `-` into a space, then collapse and trim. The result is the **name phrase**, and an empty name phrase gives `Absent`. **K4** stage-1 lookup of the name phrase; a hit returns `Derived(hit)`. **K5** the protected-phrase map (`chopped tomato` and `chopped tomatoes` → `chopped tomato`); a hit returns `Derived` **directly**, with no stripping, singularising or stage-2 lookup (spec Q2 "returns … directly"). **K6** remove the phrases `to taste`, `to serve` and `for garnish`, then the 22 preparation and size words, at word boundaries; an empty result gives `Absent`. **K7** singularise the **last** word by the Q2 suffix rules. **K8** stage-2 lookup of the rule output; a hit returns `Derived(hit)`, otherwise `Derived(rule output)` | Stage-1 only after the protected check: a future alias whose variant is a protected phrase would be shadowed by F3's code, whereas the data should win. Protected phrases stored as alias self-maps: forbidden by F2 R5 and F2 AD10 (spec Q3) | This follows spec Q1 and Q2 exactly, and puts the table's data ahead of F3's built-in exceptions | The word lists are `private` constants in `CanonicalKeyRule.kt`. Changing them is Ask First (spec Boundaries). R3's fixed-point test over every shipped key is the regression net |
| AD11 — Column mapping and unit tokens (R4 AC6) | `Quantity.toColumns(): QuantityColumns` and `QuantityColumns.decode(): ColumnDecode`, which is a member function, so no non-private function takes a `String` parameter (AD4). The tokens are the lowercase `MeasureUnit`/`CountUnit` tokens (DM4, DM5), and a **bare count's token is `each`** (`BARE_COUNT_TOKEN`). `Unquantified` ↔ (`null`, `null`, `UNQUANTIFIED`) | A null unit for a bare count: that breaks F1's documented column contract ("Null iff dimension is UNQUANTIFIED"). A `CountUnit.NONE` enum value: it would make "no count unit" look like a unit, which is less honest than `null` in the model | One token per unit, unique across both dimension families and `each`. `decode` returns `Inconsistent` for any triple the engine could not have produced: an unknown token, a token whose dimension does not match, a null or non-positive amount on a measured dimension, or a non-null amount or unit on `UNQUANTIFIED` | F6 persists through this mapping and F11 reads back through it. A corrupt row is a typed `Inconsistent`, never an exception |
| AD12 — Totality and failure discipline (R1 AC3, R10; CFC-4) | `parse` wraps its body in `try { … } catch (_: Exception) { UNREADABLE }`, where `UNREADABLE = ParsedLine(CanonicalKey.Absent, Quantity.Unquantified)`. The exception is not bound, kept, chained or logged. `Error` is not caught, and nothing in the engine recurses. Every operation checks that its arithmetic result is finite and `> 0` before constructing a value, and otherwise returns its typed failure. The main sources contain no `throw`, no `error(…)`, no logging and no `printStackTrace`. The only throwing sites are the `init` `require`s in AD1, and each has a constant message | No catch-all (rely on the grammar being total): one parser bug on an F5-imported line would crash the save path. A distinct `ParseFailed` outcome: the spec defines five failure values, and an internal defect is still honestly "unknown" | CFC-4's per-feature AC: F3 constructs no content-bearing exception. The one JDK call whose exception embeds input, `String.toDouble()` (its `NumberFormatException` message includes the string), is only ever given a validated digit run, and the catch-all discards anything it throws | A masked bug shows up as an absent key or unquantified value. Tests that assert exact expected values (the corpus and R2/R4) catch it as a mismatch (DR7). F3 has no third-party exceptions to re-wrap, so the re-wrapping rule of CFC-4's F4-defined typed-error surface has nothing to apply to in F3 |
| AD13 — Package and file split (spec Project Structure `[ASSUMPTION]`) | Package `ie.pantry.domain.ingredient`, with the spec's five files plus **`QuantityOperations.kt`**. Conversion, scaling and merge are placed there instead of in `IngredientEngine.kt` | All operations in `IngredientEngine.kt`, as sketched in the spec: that file already holds the entry point, the scanner and the sealed gate (AD4), and the operations share none of their private state | Each file has one reason to change | Refines the spec's New Files. No test file changes |
| AD14 — CFC-1 enforcement artifacts (CFC-1 Enforcement names "the shared parse-fidelity test corpus owned by F3") | F3 ships three things. (1) The corpus TSV and its extension fixture. (2) `ParseFidelityCorpus.load()` in `ie.pantry.testutil`. (3) `ShippedReferenceTables.aliasTable()` in `ie.pantry.testutil`, so that F6's and F11's route tests build **the same** table without copying F2 loader code. `EntryPointDeterminismTest` proves cross-instance equality. `ParseFidelityCorpusCrossPackageTest` is the reference usage pattern for downstream route tests. **Byte-identical** is defined as `ParsedLine ==` together with `toColumns() ==`, using exact `Double` equality and no tolerance | Putting the loader in `ie.pantry.domain.ingredient` test sources: other packages could still import it, but R8 AC3 asks for independence from F3 test classes, and `testutil` is the shared home F1 and F2 already use | F3 cannot drive routes that do not exist yet (F6 ships in Milestone 2), so it supplies the fixture, the loader and the comparison definition, and each route owner asserts against them | Expected-versus-actual comparisons in F3's own corpus test use a tolerance of 1e-9 on the amount, because the developer writes the expected values as decimals. Route-versus-route comparisons (F6, F11) use exact equality |

## Component Design

### FC1 — Result and unit model

**Responsibility:** Define the typed key, quantity, outcome and unit values, and the mapping to and from F1's columns.

**Location:** `app/src/main/java/ie/pantry/domain/ingredient/Quantity.kt`, `app/src/main/java/ie/pantry/domain/ingredient/Units.kt`

**Key classes/functions:**
- `CanonicalKey`, `Quantity`, `ParsedLine`, `Conversion`, `Scaling`, `MergeResult`, `QuantityColumns`, `ColumnDecode`: sealed interfaces and data classes (DM1–DM3, DM6, DM7; AD1).
- `Quantity.toColumns()` is a top-level extension and `QuantityColumns.decode()` is a member function (I5; AD11).
- `sealed interface AmountUnit`, implemented by `enum class MeasureUnit` and `enum class CountUnit` (DM4, DM5). Each carries its column `token` and its `internal val spellings`. `MeasureUnit` also carries its `dimension` and `factorToBase`. `const val BARE_COUNT_TOKEN = "each"`.

### FC2 — Entry point and scanner

**Responsibility:** Accept raw line text, normalise it into the sealed `ScannedLine`, and assemble one `ParsedLine` from the parser and the rule.

**Location:** `app/src/main/java/ie/pantry/domain/ingredient/IngredientEngine.kt`

**Key classes/functions:**
- `class IngredientEngine`, with a public constructor `(AliasTable)` and an internal constructor `(aliasLookup)` (AD5). `fun parse(line: String): ParsedLine` is the only non-private declaration in the package that takes raw text (I1; AD4).
- `internal sealed interface ScannedLine` and `private class Scanned(override val text: String) : ScannedLine` (AD4).
- `private fun scan(line: String): ScannedLine?` runs AD8's steps N1–N6. It returns `null` only for a line over the cap, and that `null` never leaves the file.
- `internal const val MAX_LINE_CHARS = 1_000`, and a private `UNREADABLE` value (AD7, AD12).
- `parse` = `scan` → `QuantityParser.read(scanned)` → `CanonicalKeyRule.resolve(scanned, read.nameStart, aliasLookup)` → `ParsedLine(key, read.quantity)`, all inside AD12's catch-all.

### FC3 — Quantity parser

**Responsibility:** Read the leading quantity segment of a `ScannedLine` into a `Quantity` and report where the name text starts.

**Location:** `app/src/main/java/ie/pantry/domain/ingredient/QuantityParser.kt`

**Key classes/functions:**
- `internal object QuantityParser { fun read(line: ScannedLine): QuantityRead }`, where `internal data class QuantityRead(val quantity: Quantity, val nameStart: Int)` (I6).
- Private vocabulary: `LEAD_WORDS = a, an, some, few, several`; `VAGUE_MEASURES = pinch(es), handful(s), knob(s), dash(es), splash(es)` (Q4); `RANGE_WORDS = to`; `MULTIPLIER = x`; unit spellings are read from `MeasureUnit.spellings` and `CountUnit.spellings`.
- `private` word cursor: an index over `text`. A "word" is a maximal run of non-space characters. Inside a word that starts with a digit or a vulgar fraction, the cursor splits the word at each digit↔letter boundary (`500g` → `500`, `g`; `2x400g` → `2`, `x`, `400`, `g`).

**Grammar** (read from the start; the first rule that matches wins; *consumed* means the words become part of the quantity segment, so they never reach the name phrase):

| # | Leading form | Result | Consumed |
|---|---|---|---|
| G1 | No digit or vulgar fraction in the first word, and the word is not a lead word | `Unquantified` | nothing |
| G2 | One or more lead words, then optionally a vague measure, count unit or measure unit, then optionally `of` (`a pinch of`, `some`, `a tin of`) | `Unquantified` (article amounts are never read as 1; design Q2) | all of those words |
| G3 | `-` directly followed by a digit (`-2 eggs`) | `Unquantified` (negative) | the signed amount, and a following unit word and `of` |
| G4 | AMOUNT, then `-` or `to`, then AMOUNT (`2-3`, `2 to 3`) | `Unquantified` (range, Q6) | both amounts, and a following unit word and `of` |
| G5 | DIGITS `,` DIGITS with no space (`1,5`) | `Unquantified` (decimal comma, Q6) | the token, and a following unit word and `of` |
| G6 | AMOUNT `x` AMOUNT MEASURE_UNIT, then optionally a count unit (`2 x 400g tins`) | `Measured(n × m, unit)` (Q6) | all of it, then an optional `of` |
| G7 | AMOUNT, then optionally a count unit, then `(` AMOUNT MEASURE_UNIT [`each`] `)`, then optionally a count unit (`1 (400 g) tin`, `1 tin (400g)`) | `Measured(count × size, unit)` (Q6) | all of it, then an optional `of` |
| G8 | AMOUNT MEASURE_UNIT, with an optional trailing `.` (`500g`, `1½ tsp`, `2 fl oz`), then optionally a count unit (`400g tin`) | `Measured(amount, unit)` | all of it, then an optional `of` |
| G9 | AMOUNT COUNT_UNIT (`3 cloves`, `1 bunch`) | `Counted(amount, unit)` | all of it, then an optional `of` |
| G10 | AMOUNT VAGUE_MEASURE (`2 pinches`) | `Unquantified` | all of it, then an optional `of` |
| G11 | AMOUNT WORD `of`, where WORD is not a known unit (`3 splodges of`) | `Unquantified` (unknown unit) | all three words |
| G12 | AMOUNT followed by a second numeric word that does not complete a mixed number | `Unquantified` (ambiguous) | both numeric words, and a following unit word and `of` |
| G13 | AMOUNT on its own (`3 eggs`, `2 large onions`) | `Counted(amount, null)` | the amount |

- **AMOUNT** is one of: DIGITS; DIGITS `.` DIGITS; DIGITS `/` DIGITS; a vulgar fraction; DIGITS followed by a vulgar fraction (`1½`); DIGITS, a space, then a fraction (`1 1/2`, `1 ½`). DIGITS has at most 6 characters and the fractional part at most 6.
- **Validity** is checked after the structure has matched. The value must be finite and `> 0`, and the denominator must not be 0. `0 g` and `1/0 g` are structurally G8, so they are `Unquantified` with `g` consumed, and the key is `Absent`.
- **Malformed** input is any word that starts with a digit or a vulgar fraction and does not split into one of the accepted shapes (`1e999`, `½½`, a 20-digit run). It is `Unquantified`, the whole word is consumed, and so is a following unit word.
- Multi-word spellings (`fl oz`, `fluid ounce`) are checked before single-word ones.

### FC4 — Canonical-key rule

**Responsibility:** Turn the name text of a `ScannedLine` into a `CanonicalKey` by AD10's steps K1–K8.

**Location:** `app/src/main/java/ie/pantry/domain/ingredient/CanonicalKeyRule.kt`

**Key classes/functions:**
- `internal object CanonicalKeyRule { fun resolve(line: ScannedLine, nameStart: Int, aliasLookup: (String) -> Lookup<String>): CanonicalKey }` (I6).
- `private val PREPARATION_WORDS` holds the 22 words from spec Q2. `private val PREPARATION_PHRASES = listOf("to taste", "to serve", "for garnish")`. `private val PROTECTED_PHRASES = mapOf("chopped tomato" to "chopped tomato", "chopped tomatoes" to "chopped tomato")` (Q2, Q3).
- `private fun singularise(word: String): String` applies Q2's rules: `-ies`→`-y`; `-oes`→`-o`; `-ches`, `-shes`, `-sses`, `-xes` drop `-es`; otherwise a final `-s` is dropped unless the word ends in `-ss`, `-us` or `-is`.
- Every table query is made with `aliasLookup(...)`, and each result is matched with an exhaustive `when` on `Lookup`.

### FC5 — Quantity operations

**Responsibility:** Convert, scale and merge already-parsed values, returning typed outcomes.

**Location:** `app/src/main/java/ie/pantry/domain/ingredient/QuantityOperations.kt`

**Key classes/functions:** these are top-level extension functions (I2–I4).
- `Quantity.convertTo(target: AmountUnit): Conversion`. A `Measured` value converts to a `MeasureUnit` of the same dimension as `amount × from.factorToBase / to.factorToBase`. A `Counted` value converts only to an equal `CountUnit`, which is the identity. Every other combination gives `NotConvertible`.
- `Quantity.scaledBy(ratio: Double): Scaling`
- `ParsedLine.mergeWith(other: ParsedLine): MergeResult` and `ParsedLine.canMergeWith(other: ParsedLine): Boolean = mergeWith(other) is MergeResult.Merged`. Defining the predicate this way means it can never disagree with the merge.

### FC6 — Shipped-key reconciliation data

**Responsibility:** Apply the Q3 alias additions to F2's asset and record them in the provenance file.

**Location:** `app/src/main/assets/reference/aliases.json`, `docs/reference-data-provenance.md`

**Key changes:**
- Append `{"variant": "oat", "canonicalKey": "oats"}` and `{"variant": "baked bean", "canonicalKey": "baked beans"}` (DM9). Both targets are staples keys, neither is a self-map and neither forms a chain, so F2's `ShippedDatasetsTest` passes unchanged (checked against its `aliasSelfMaps`, `aliasChains` and `unresolvedAliasTargets` helpers).
- In `## Aliases`, extend the `- **Source:**` line to name the two F3 reconciliation entries. Keep the `**Retrieved:**` date format, so `ProvenanceRecordTest` stays green.

### FC7 — Parse-fidelity corpus

**Responsibility:** Hold the hand-transcribed lines and their expected results, and load them for any test package.

**Location:** `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv`, `app/src/test/resources/ingredient/parse_fidelity_corpus_with_f5_entry.tsv`, `app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt`

**Key classes/functions:**
- `object ParseFidelityCorpus { const val DEFAULT_RESOURCE = "ingredient/parse_fidelity_corpus.tsv"; fun load(resource: String = DEFAULT_RESOURCE): List<CorpusEntry> }` (I7).
- `data class CorpusEntry` and `enum class CorpusOrigin { HAND_TRANSCRIBED, F5_HARVESTED }`, `enum class LineKind { INGREDIENT, NON_INGREDIENT }` (DM8).

### FC8 — Verification harness

**Responsibility:** Provide the shared shipped-table helper and the test classes listed in Testing Strategy.

**Location:** `app/src/test/java/ie/pantry/testutil/ShippedReferenceTables.kt` and `app/src/test/java/ie/pantry/domain/ingredient/*Test.kt`

**Key classes/functions:**
- `object ShippedReferenceTables` loads each shipped table once per call through `DatasetLoader.loadDataset(AndroidAssetSource(context.assets), ReferenceDataset.X, DatasetParser::parseX)`, the same path `ShippedDatasetsTest` uses. It must run under Robolectric (I8).

## Data Models

| Model | Field | Type | Constraints | Description |
|-------|-------|------|-------------|-------------|
| DM1 `CanonicalKey.Derived` | value | String | Required; `init` `require(value.isNotBlank())` with a constant message | Rule output or alias target, in F2 surface form (lowercase, trimmed, single-spaced) |
| DM1 `CanonicalKey.Absent` | — | `data object` | No fields | Explicit key-absent (R2 AC5; CFC-2) |
| DM2 `Quantity.Measured` | amount | Double | Finite, `> 0` (`init` `require`) | The number as stated, in `unit` |
| DM2 `Quantity.Measured` | unit | MeasureUnit | Required | `MASS` or `VOLUME` unit |
| DM2 `Quantity.Measured` | dimension | QuantityDimension | Derived (`unit.dimension`); not a constructor property | F1 enum, reused |
| DM2 `Quantity.Counted` | amount | Double | Finite, `> 0` | `Double`, so that ×1.5 scaling and `½ tin` work |
| DM2 `Quantity.Counted` | countUnit | CountUnit? | Nullable only for a bare count (`3 eggs`) | The counted noun (spec Terms) |
| DM2 `Quantity.Counted` | dimension | QuantityDimension | Always `COUNT` | — |
| DM2 `Quantity.Unquantified` | dimension | QuantityDimension | Always `UNQUANTIFIED`; `data object`, no numeric or unit field | Explicit unquantified (R4 AC5; CFC-2) |
| DM3 `ParsedLine` | key | CanonicalKey | Required | — |
| DM3 `ParsedLine` | quantity | Quantity | Required | — |
| DM6 `Conversion.Converted` | quantity | Quantity | Never `Unquantified` | `NotConvertible` is a `data object` |
| DM6 `Scaling.Scaled` | quantity | Quantity | Same dimension and unit as the input | `InvalidRatio` is a `data object` |
| DM6 `MergeResult.Merged` | line | ParsedLine | `line.key` is the shared `Derived` key | `NotMergeable` is a `data object` |
| DM7 `QuantityColumns` | amount | Double? | Null iff `dimension == UNQUANTIFIED` | Mirrors `RecipeIngredient.quantityAmount` |
| DM7 `QuantityColumns` | unit | String? | Null iff `UNQUANTIFIED`; otherwise a DM4 or DM5 token, or `each` | Mirrors `RecipeIngredient.quantityUnit` |
| DM7 `QuantityColumns` | dimension | QuantityDimension | Required | Mirrors `RecipeIngredient.dimension` |
| DM7 `ColumnDecode` | — | sealed | `Decoded(quantity)` or `data object Inconsistent` | Typed read-back (AD11) |

**DM4 `MeasureUnit`** (Q4 factors. The base units are `G` and `ML`. Every spelling may take a trailing `.`):

| Enum | token | dimension | factorToBase | spellings |
|---|---|---|---|---|
| G | `g` | MASS | 1.0 | g, gram, grams |
| KG | `kg` | MASS | 1000.0 | kg, kgs, kilo, kilos, kilogram, kilograms |
| MG | `mg` | MASS | 0.001 | mg, milligram, milligrams |
| OZ | `oz` | MASS | 28.349523125 | oz, ounce, ounces |
| LB | `lb` | MASS | 453.59237 | lb, lbs, pound, pounds |
| ML | `ml` | VOLUME | 1.0 | ml, mls, millilitre(s), milliliter(s) |
| L | `l` | VOLUME | 1000.0 | l, litre(s), liter(s) |
| CL | `cl` | VOLUME | 10.0 | cl, centilitre(s), centiliter(s) |
| TSP | `tsp` | VOLUME | 5.0 | tsp, tsps, teaspoon(s) |
| TBSP | `tbsp` | VOLUME | 15.0 | tbsp, tbsps, tablespoon(s) |
| CUP | `cup` | VOLUME | 250.0 | cup, cups |
| FL_OZ | `fl_oz` | VOLUME | 28.4130625 | fl oz, fluid ounce(s) |
| PINT | `pint` | VOLUME | 568.26125 | pint, pints |

**DM5 `CountUnit`** (Q4): `CLOVE` `clove`, `TIN` `tin`, `CAN` `can`, `BUNCH` `bunch`, `BULB` `bulb`, `SLICE` `slice`, `SPRIG` `sprig`, `STICK` `stick`, `SHEET` `sheet`, `PACKET` `packet`. The spellings are the singular and the regular plural (`bunches`). A bare count uses `BARE_COUNT_TOKEN = "each"` in the columns. All 24 tokens (13 + 10 + `each`) are unique, and `QuantityParserTest` asserts this.

**DM8 Corpus row** (`parse_fidelity_corpus.tsv`). UTF-8. Lines starting with `#` are comments. The first non-comment line is exactly the header below. Each row has 9 tab-separated fields, and **no field may be empty**:

| Column | Type in `CorpusEntry` | Rule |
|---|---|---|
| `origin` | `CorpusOrigin` | `HAND_TRANSCRIBED` or `F5_HARVESTED` |
| `source_url` | `String` | The page URL (Q9) |
| `retrieved` | `LocalDate` | `YYYY-MM-DD` (Q9) |
| `line_kind` | `LineKind` | `INGREDIENT` or `NON_INGREDIENT` (a section header, Q10) |
| `line` | `String` | Verbatim line text; must not contain a tab |
| `expected_key` | `CanonicalKey` | A key, or the literal `<absent>` → `CanonicalKey.Absent` |
| `expected_dimension` | `QuantityDimension` | `MASS`, `VOLUME`, `COUNT` or `UNQUANTIFIED` |
| `expected_amount` | `Double?` | A decimal or `a/b`; `-` iff `UNQUANTIFIED` |
| `expected_unit` | `String?` | A DM4 or DM5 token or `each`; `-` iff `UNQUANTIFIED` |

`CorpusEntry(origin, sourceUrl, retrieved, lineKind, line, expectedKey: CanonicalKey, expectedColumns: QuantityColumns, row: Int)`. The loader fails with `IllegalStateException("parse_fidelity_corpus: malformed row $row")`, which names only the row number (test code, but kept content-free by the same discipline as R10).

**DM9 `aliases.json` additions:** `AliasEntry("oat", "oats")` and `AliasEntry("baked bean", "baked beans")`. They use F2's existing object-with-`entries` layout (F2 AD8), unchanged.

**Relationships:**
- `ParsedLine` has one `CanonicalKey` and one `Quantity`.
- `Quantity.Measured` has one `MeasureUnit`, and `Quantity.Counted` has zero or one `CountUnit`.
- `QuantityColumns` is the 1:1 value image of a `Quantity` (AD11), and F6 stores it in `RecipeIngredient`.

**Persistence:** none in F3. The engine reads and writes no storage (spec Never Do). `QuantityColumns` is the value that F6 persists through F1's Room entity. The corpus is a test resource and is not packaged into the APK.

## Interfaces

```kotlin
package ie.pantry.domain.ingredient

import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.reference.AliasTable
import ie.pantry.data.reference.Lookup

// ---- I1: the single raw-text entry point (IngredientEngine.kt) ----
class IngredientEngine internal constructor(private val aliasLookup: (String) -> Lookup<String>) {

    /** Engines receive tables only (F2 AD2): the caller unwraps `ReferenceDataStore.aliases()`. */
    constructor(aliases: AliasTable) : this(aliases::lookup)

    /**
     * Parses one free-text ingredient line into a canonical key and a quantity.
     *
     * @param line raw line text, from any route (import confirm, manual entry, post-save edit)
     * @return a [ParsedLine]; `CanonicalKey.Absent` when no name text remains, and
     *   `Quantity.Unquantified` when no leading amount is confidently read
     */
    fun parse(line: String): ParsedLine
}

// ---- I2–I4: operations on already-parsed values (QuantityOperations.kt) ----
/** Within-dimension conversion. Returns `NotConvertible` across dimensions, across count units, and for `Unquantified`. */
fun Quantity.convertTo(target: AmountUnit): Conversion

/** Multiplies by [ratio]. Returns `InvalidRatio` when the ratio is not finite or not > 0, or when the product is not finite or not > 0. `Unquantified` scales to itself. */
fun Quantity.scaledBy(ratio: Double): Scaling

/** Merges two lines with the same `Derived` key and comparable quantities (AD3). Returns `NotMergeable` otherwise. */
fun ParsedLine.mergeWith(other: ParsedLine): MergeResult

/** R7's predicate; true iff [mergeWith] returns `Merged`. */
fun ParsedLine.canMergeWith(other: ParsedLine): Boolean

// ---- I5: F1 column mapping (Quantity.kt) ----
fun Quantity.toColumns(): QuantityColumns

data class QuantityColumns(val amount: Double?, val unit: String?, val dimension: QuantityDimension) {
    /** Reads a stored triple back. Returns `Inconsistent` for any triple the engine could not have produced. */
    fun decode(): ColumnDecode
}
```

```kotlin
// ---- I6: package-internal seams (never take a String; AD4) ----
internal sealed interface ScannedLine { val text: String }                  // IngredientEngine.kt
internal data class QuantityRead(val quantity: Quantity, val nameStart: Int) // QuantityParser.kt
internal object QuantityParser { fun read(line: ScannedLine): QuantityRead }
internal object CanonicalKeyRule {
    fun resolve(line: ScannedLine, nameStart: Int, aliasLookup: (String) -> Lookup<String>): CanonicalKey
}

// ---- I7: shared corpus loader (app/src/test/java/ie/pantry/testutil/ParseFidelityCorpus.kt) ----
object ParseFidelityCorpus {
    const val DEFAULT_RESOURCE = "ingredient/parse_fidelity_corpus.tsv"
    /** Reads [resource] from the test classpath. @throws IllegalStateException naming only the row number. */
    fun load(resource: String = DEFAULT_RESOURCE): List<CorpusEntry>
}

// ---- I8: shipped tables for tests (app/src/test/java/ie/pantry/testutil/ShippedReferenceTables.kt; Robolectric only) ----
object ShippedReferenceTables {
    fun aliasTable(): AliasTable
    fun staplesTable(): StaplesTable
    fun seasonalityTable(): SeasonalityTable
    fun sectionOrderTable(): SectionOrderTable
}
```

**Contracts:**
- **I1** — *Pre:* none. Any `String` is accepted, including `""`. *Post:* the function never throws an `Exception` (AD12). Equal `line` values and equal alias tables give equal `ParsedLine` values, across instances and threads (R1 AC2). A `Derived` key is in F2 surface form. A `Measured` or `Counted` amount was read from the line (R4). *Side effects:* none. The only external call is `aliasLookup`, at most twice per line (K4, K8). *Nullability:* none in the result.
- **I2** — `Measured` → a `MeasureUnit` of the same dimension gives `Converted(Measured(amount × fromFactor / toFactor, target))`. `Counted(u)` → `CountUnit` `u` gives `Converted(self)`. Everything else gives `NotConvertible` (R5 AC2–AC3). A non-finite result gives `NotConvertible`. Never throws.
- **I3** — `ratio.isNaN() || ratio.isInfinite() || ratio <= 0.0` gives `InvalidRatio` (R6 AC3). `Unquantified` gives `Scaled(Unquantified)` (R6 AC2). No rounding (R6 AC1). Never throws.
- **I4** — Returns `Merged` iff both keys are `Derived` and equal (so `Absent` never merges, R7 AC6), and one of these holds: both quantities are `Unquantified` (the result is `Unquantified`, Q7); both are `Measured` in the same dimension (AD3) with a finite sum; both are `Counted` with equal `countUnit`, both-`null` included (Q5), and a finite sum. It is symmetric (R7 AC7). Never throws.
- **I5** — `q.toColumns().decode() == ColumnDecode.Decoded(q)` for every `q` the engine can produce (R4 AC6). `Unquantified.toColumns() == QuantityColumns(null, null, UNQUANTIFIED)`.
- **I6** — `nameStart` is in `0..text.length`. `resolve` reads `text.substring(nameStart)` only.
- **I7** — Entries come back in file order. The loader does not depend on any class in `ie.pantry.domain.ingredient`'s **test** sources (R8 AC3). It uses F3 main types (`CanonicalKey`, `QuantityColumns`) and F1's `QuantityDimension`.
- **I8** — Each call loads from the real assets and fails the test with `assertIs<LoadResult.Ready<…>>` if the load is not `Ready`. It requires a Robolectric application context.

## Error Handling

- **Strategy:** sealed-type outcome values (AD1). No F3 operation reports an expected failure by throwing. `parse` has a catch-all for defects (AD12).

| Condition | Outcome | Where decided | Caught where |
|---|---|---|---|
| No name text after K1–K3 or after K6 | `CanonicalKey.Absent` | FC4 | — |
| No confident leading amount (G1–G5, G10–G12, invalid value, malformed word) | `Quantity.Unquantified` | FC3 | — |
| Line longer than 1,000 characters | `UNREADABLE` (absent key, unquantified) | FC2 N1 | — |
| Any `Exception` inside `parse` (a defect, a throwing lookup seam, `toDouble` on an unexpected input) | `UNREADABLE`; the exception is not bound, kept, chained or logged | FC2 | `parse` |
| Converting across dimensions or count units, converting `Unquantified`, a non-finite result | `Conversion.NotConvertible` | FC5 | — |
| A ratio ≤ 0, NaN or ±∞; a product that is not finite or not > 0 | `Scaling.InvalidRatio` | FC5 | — |
| Keys differ or absent; dimensions differ; count units differ; measured with unquantified; a non-finite sum | `MergeResult.NotMergeable` | FC5 | — |
| A stored column triple the engine could not have produced | `ColumnDecode.Inconsistent` | FC1 | — |
| A caller constructs `Measured`/`Counted` with an amount ≤ 0 or not finite, or `Derived` with a blank value | `IllegalArgumentException` with a constant message (contract violation by the caller) | FC1 `init` | not caught; the message is constant, so no content can leak |

- **Custom exceptions / error types:** none. The six failure values (`Absent`, `Unquantified`, `NotConvertible`, `InvalidRatio`, `NotMergeable`, `Inconsistent`) are all `data object`s, so each `toString()` is its own class name and contains no input (R10 AC1; CFC-4). `Inconsistent` is a sixth failure value alongside the spec's five. It serves R4 AC6's read-back direction and follows the same content-free rule.
- **Logging:** none. F3 has no logger dependency (R9 forbids `android.util.Log`, and no JVM logger is in the catalog). `EngineErrorHygieneTest` asserts that `Log`, `println`, `printStackTrace` and `System.err` do not appear in the main sources.
- **User-facing errors:** none from F3. Callers turn `Absent` and `Unquantified` into UI states (F6, F11) under CFC-2. F6 decides what to do with a key-absent line on save (spec Q8).
- **Third-party exceptions (CFC-4 re-wrapping):** F3 calls only the Kotlin stdlib and the JDK (`Normalizer`, `Character`, `String.toDouble` on validated digit runs). None of these propagates out of `parse`, because of the catch-all.

## Testing Strategy

- **Framework:** JUnit 4 (`junit:junit:4.13.2`), `kotlin.test` assertions (`kotlin-test-junit`), and Robolectric 4.14.1 (`sdk=35` from the existing `app/src/test/resources/robolectric.properties`) **only** for tests that load shipped assets (R9 AC2).
- **Sandbox prerequisite:** in a network-isolated sandbox, Robolectric resolves its SDK jar from `.toolchain/m2/repository` (see File Structure). That directory is absent on developer machines and CI, where Robolectric uses its normal resolution.
- **Test location:** `app/src/test/java/ie/pantry/domain/ingredient/` mirrors the main package. The shared helpers and the cross-package test are in `app/src/test/java/ie/pantry/testutil/`. The corpus is in `app/src/test/resources/ingredient/`, read from the classpath the same way as F2's `reference/fixtures/`.
- **Mocking approach:** no mocking library. Fixture tables are built as `AliasTable(listOf(AliasEntry(…)))` through the `internal` constructor. Lookup observation goes through `IngredientEngine`'s `internal` constructor: a counting lambda, a recording lambda, or a throwing lambda (AD5).
- **Coverage expectations:** every public function in I1–I5 has at least one happy-path and one failure-path test. Every GIVEN/WHEN/THEN in spec R1–R10 is asserted by the class listed below, except R8 AC5, which is confirmed at review. Every grammar row G1–G13 has at least one test line. Every `MeasureUnit` and `CountUnit` has a parse test and a round-trip test.
- **Fixtures / test data:** fixture tables are built per test, inline. Shipped tables come from `ShippedReferenceTables` (I8) and are loaded per test class in a `companion`-level lazy. Sentinel lines use the existing `ie.pantry.testutil.Sentinels.INGREDIENT`, and thrown values are checked with `assertNoSentinel()`. Because F3 **lowercases** its input, the value-`toString()` checks compare `ignoreCase = true` against every `Sentinels.all` value.
- **Naming convention:** backtick-quoted descriptive names, as in F1 and F2 (for example `` `unquantified is not a zero measured quantity`() ``).

**Test classes and what each must cover:**

| Test class | Runner | Covers | Notes |
|---|---|---|---|
| `EntryPointTest` | plain JVM | R1 AC1, AC3 | **AC1:** `rawTextAcceptors(sourceText)` runs a regex over the concatenated sources of `app/src/main/java/ie/pantry/domain/ingredient/*.kt` (found with `RepoPaths.repoRoot()`). It collects every `fun` declaration without an explicit `private` modifier that has a `String`/`CharSequence` (optionally `?`) parameter or a `String.`/`CharSequence.` receiver, across multi-line parameter lists, plus every non-`private` `val`/`var` whose type contains `(String)`. The result must equal `["IngredientEngine.parse"]`. **Negative controls:** the same function run on inline snippets flags `internal fun keyOnly(line: String)`, `fun String.quantityOnly()` and a three-line parameter list, and does not flag `private fun helper(line: String)`. Also asserted: `ScannedLine` is declared `sealed`, its only implementer is `private class`, and Java reflection over `IngredientEngine::class.java.methods` finds exactly one public method with a `String` parameter, `parse`. **AC3:** the eleven R1 AC3 cases (the 10,000-character line is `"a".repeat(10_000)`) each return a `ParsedLine`, and none throws. Expected values: `""`, whitespace, control characters, emoji, `1/0 g`, `0 g`, `1e999 g`, `99999999999999999999 g`, `½½ tsp` and the 10,000-character line give `Absent`; `-2 eggs` gives `Derived("egg")`; every case gives `Unquantified` |
| `CanonicalKeyRuleTest` | plain JVM | R2 AC1, AC2, AC3, AC5, AC6 | Fixture tables. **AC3:** an engine over `{ v -> table.lookup(v).also { if (it is Lookup.Found) hits++ } }` gives `hits == 2` after the three lines, with `3 carrots` contributing none. **AC5:** `Absent` for `2 tbsp`, `500 g`, `""` and `, chopped`. `assertFailsWith<IllegalArgumentException> { CanonicalKey.Derived("") }`. `Absent` is unequal to `Derived` of every key in the class. `CanonicalKey.Absent::class.java.declaredFields` contains no `String` field. Also covers the protected phrase and a stage-1 hit that wins over the rule |
| `QuantityParserTest` | plain JVM | R4 AC1–AC6; DM4, DM5 token uniqueness | Parses through `IngredientEngine(emptyTable).parse`. Amounts are compared with a tolerance of 1e-9. **AC5:** the nine lines give `Unquantified`. `Measured(0.0, G)` and `Counted(0.0, null)` fail to construct. `Unquantified` is unequal to `Measured(1.0, G)` and `Counted(1.0, null)`. Reflection finds no `double` field on `Quantity.Unquantified`. **AC6:** for each `MeasureUnit`, each `CountUnit` and the bare count, `decode(toColumns(q)) == Decoded(q)`. `Unquantified` maps to `(null, null, UNQUANTIFIED)`. Six inconsistent triples give `Inconsistent` |
| `ConversionScalingMergeTest` | plain JVM | R5, R6, R7 | R5 AC1 uses Q4 factors with a tolerance of 1e-9. R7 AC7's symmetry is checked on six mergeable pairs covering same-unit, mixed-unit, count and unquantified. Also checked: the predicate agrees with `mergeWith` on every pair in the class, and the non-finite guards (`Measured(1e308, G).scaledBy(1e10)` gives `InvalidRatio`) |
| `EntryPointDeterminismTest` | Robolectric | R1 AC2 | Two `IngredientEngine(ShippedReferenceTables.aliasTable())` instances, each built from its **own** table load. Every corpus line must give `parse(line) ==` and `toColumns() ==` across the two, with exact equality (AD14) |
| `ShippedKeyReconciliationTest` | Robolectric | R2 AC4; R3 AC1, AC2 | **R3 AC1:** every staples key, seasonality key, substitution, section-mapping key and alias target K satisfies `parse(K).key == Derived(K)`. On failure the test lists the offenders, and there is no allow-list. **R3 AC2:** for each variant V, a recording engine over the shipped table parses V, and the recording must contain `(V, Found(_))` |
| `ParseFidelityCorpusTest` | Robolectric | R8 AC1, AC2, AC4 | ≥50 `HAND_TRANSCRIBED` entries from ≥5 distinct `source_url` hosts (Q9). Each parses to its expected key (`==`) and columns (dimension and unit `==`, amount within 1e-9). Coverage floor: each of `MASS`, `VOLUME`, `COUNT` and `UNQUANTIFIED` is present; ≥1 entry has a `Found` hit under a counting engine; ≥1 entry is `NON_INGREDIENT` or has key `<absent>`; ≥1 entry has a non-integer expected amount. **AC4:** loading `parse_fidelity_corpus_with_f5_entry.tsv` gives exactly one `F5_HARVESTED` entry that `filter { it.origin == F5_HARVESTED }` finds. R8 AC5 (expected values written by hand) is confirmed at review, and Implementation Sequence step 2 orders the corpus before the parser |
| `ParseFidelityCorpusCrossPackageTest` (in `ie.pantry.testutil`) | Robolectric | R8 AC3 | Imports only `ParseFidelityCorpus`, `ShippedReferenceTables` and F3 **main** types. It loads the corpus, drives each entry through `IngredientEngine(ShippedReferenceTables.aliasTable()).parse`, and compares the result with the entry's expected values. This is the template F6's and F11's CFC-1 route tests copy |
| `EngineErrorHygieneTest` | plain JVM | R10 AC1, AC2 | **AC1:** sentinel-bearing lines reach every failure value (key-absent; unquantified through G2–G5, G10–G12, zero and malformed; R1's numeric edge cases). Then `convertTo`, `scaledBy`, `mergeWith` and `decode` produce each of their failures from those values. No exception escapes, and no failure value's `toString()` contains a sentinel (ignoring case). **AC2:** source inspection finds no `throw` and no `error(`. Every `require`/`check`/`requireNotNull`/`checkNotNull` has a lazy message that is a string literal with no `$`. There is no `Log`, `println`, `printStackTrace` or `System.err`. An engine built with a lookup that throws `RuntimeException(Sentinels.INGREDIENT)` returns `ParsedLine(CanonicalKey.Absent, Quantity.Unquantified)` for a line and throws nothing. `assertNoSentinel()` passes on the `IllegalArgumentException`s from constructing `Measured(0.0, G)` and `Derived("")` |
| `EnginePurityTest` | plain JVM | R9 AC1, AC2 | No main file contains `android.` or `org.json` anywhere, whether in an import or a fully qualified name. The only `import ie.pantry.data.` lines are for `reference.AliasTable`, `reference.Lookup` and `db.entity.QuantityDimension`. **AC2:** reflection shows that `EntryPointTest`, `CanonicalKeyRuleTest`, `QuantityParserTest`, `ConversionScalingMergeTest`, `EngineErrorHygieneTest` and `EnginePurityTest` have no `@RunWith(RobolectricTestRunner::class)` |

## File Structure

```
pantry/                                                        (repository root)
├── docs/
│   └── reference-data-provenance.md                           — MODIFIED (FC6): `## Aliases` Source line names the two Q3 entries
└── app/src/
    ├── main/
    │   ├── assets/reference/aliases.json                      — MODIFIED (FC6): + oat → oats, baked bean → baked beans (DM9)
    │   └── java/ie/pantry/domain/ingredient/                  — NEW package (first `domain` package)
    │       ├── IngredientEngine.kt                            — FC2: IngredientEngine, parse, ScannedLine (sealed) + private Scanned, scan (N1–N6), MAX_LINE_CHARS, UNREADABLE
    │       ├── QuantityParser.kt                              — FC3: QuantityParser.read, QuantityRead, grammar G1–G13, lead/vague vocabularies
    │       ├── CanonicalKeyRule.kt                            — FC4: CanonicalKeyRule.resolve, K1–K8, preparation words/phrases, protected phrases, singularise
    │       ├── QuantityOperations.kt                          — FC5: convertTo, scaledBy, mergeWith, canMergeWith (AD13)
    │       ├── Quantity.kt                                    — FC1: CanonicalKey, Quantity, ParsedLine, Conversion, Scaling, MergeResult, QuantityColumns + decode, ColumnDecode, toColumns
    │       └── Units.kt                                       — FC1: AmountUnit, MeasureUnit, CountUnit, BARE_COUNT_TOKEN
    └── test/
        ├── java/ie/pantry/
        │   ├── domain/ingredient/                             — NEW (FC8)
        │   │   ├── EntryPointTest.kt                          — R1 AC1, AC3 (JVM)
        │   │   ├── EntryPointDeterminismTest.kt               — R1 AC2 (Robolectric)
        │   │   ├── CanonicalKeyRuleTest.kt                    — R2 fixture-table criteria (JVM)
        │   │   ├── ShippedKeyReconciliationTest.kt            — R2 AC4, R3 (Robolectric)
        │   │   ├── QuantityParserTest.kt                      — R4 (JVM)
        │   │   ├── ConversionScalingMergeTest.kt              — R5, R6, R7 (JVM)
        │   │   ├── ParseFidelityCorpusTest.kt                 — R8 AC1, AC2, AC4 (Robolectric)
        │   │   ├── EngineErrorHygieneTest.kt                  — R10 (JVM)
        │   │   └── EnginePurityTest.kt                        — R9 (JVM)
        │   └── testutil/
        │       ├── ParseFidelityCorpus.kt                     — NEW (FC7): loader, CorpusEntry, CorpusOrigin, LineKind (I7)
        │       ├── ParseFidelityCorpusCrossPackageTest.kt     — NEW (FC8): R8 AC3 (Robolectric)
        │       └── ShippedReferenceTables.kt                  — NEW (FC8): shipped-table helper (I8; CFC-1 shared table)
        └── resources/ingredient/                              — NEW
            ├── parse_fidelity_corpus.tsv                      — FC7: ≥50 hand-transcribed rows (DM8)
            └── parse_fidelity_corpus_with_f5_entry.tsv        — FC7: copy of the corpus + one F5_HARVESTED row (R8 AC4)
```

These files are not changed: `PantryDatabase`, the entities, `app/schemas/`, `AppContainer`, `AndroidManifest.xml`, `gradle/libs.versions.toml`, `ShippedDatasetsTest` and every other F1/F2 source.

`app/build.gradle.kts` gained 11 lines, all inside the existing `tasks.withType<Test>` block at line 58 of the baseline (`d3fb8b2`). They set Robolectric's `maven.repo.local` system property to `.toolchain/m2/repository`, applied only when that directory exists. The property is therefore set on every `:app` Test task, not only F3's: F1's and F2's Robolectric tests (including `ShippedDatasetsTest`) are affected in the same way. Where the directory is absent, which covers the target developer machine and CI, the block sets nothing. This is an environment-conditional test-runtime setting, and its no-op behaviour is established by code review rather than by a test. It exists so Robolectric can resolve its SDK jar in a network-isolated sandbox.

## Dependencies

| Package | Purpose |
|---------|---------|
| (none added) | Adding a dependency is Ask First (spec Boundaries). `app/build.gradle.kts` gains a test-only, sandbox-conditional resolver setting (see File Structure); it adds no artifact |
| JDK `java.text.Normalizer`, `java.util.Locale` (platform) | NFD diacritic folding and root-locale lowercasing (AD8). Available on Android since API 1 and on the JVM, so no library is needed |
| `junit:junit:4.13.2`, `org.jetbrains.kotlin:kotlin-test-junit:2.1.0` (existing) | Test framework and assertions |
| `org.robolectric:robolectric:4.14.1`, `androidx.test:core:1.6.1` (existing) | Only for loading shipped assets (`ShippedReferenceTables`) |

A units or natural-language library was rejected: the Q4 table is 23 units, and a library would add both an Ask First dependency and parsing behaviour that nobody on the project controls (spec Ask First).

## Integration Points

| Existing Module | Direction | Change Required | Details |
|-----------------|-----------|-----------------|---------|
| `ie.pantry.data.reference.AliasTable` | Calls into | No | `IngredientEngine(aliases)` binds `aliases::lookup`, a synchronous exact-match lookup (F2 AD2) |
| `ie.pantry.data.reference.Lookup` | Calls into | No | Each lookup result is matched with an exhaustive `when` (`Found` / `Absent`) |
| `ie.pantry.data.db.entity.QuantityDimension` | Calls into | No | Reused as `Quantity.dimension` and in `QuantityColumns` (spec Always Do) |
| `ie.pantry.data.db.entity.RecipeIngredient` | Value contract only | No | `QuantityColumns` mirrors `quantityAmount`/`quantityUnit`/`dimension` (AD11). F3 does **not** import it, because it pulls in `androidx.room` annotations (R9 AC1) |
| `app/src/main/assets/reference/aliases.json` | Data edit | Yes (FC6, Ask First, developer-confirmed in spec Q3) | Two appended entries. F2's `ShippedDatasetsTest` must stay green |
| `docs/reference-data-provenance.md` | Doc edit | Yes (FC6) | `## Aliases` Source line. `ProvenanceRecordTest` must stay green |
| `app/build.gradle.kts` | Build config | Yes (File Structure) | Adds 11 lines inside the existing `tasks.withType<Test>` block, setting `maven.repo.local` only when `.toolchain/m2/repository` exists. Listed in the approved spec's Modified Files (spec backport); see File Structure |
| `ie.pantry.data.reference.DatasetLoader` / `DatasetParser` / `AndroidAssetSource` / `ReferenceDataset` (`internal`) | Test-only calls into | No | `ShippedReferenceTables` repeats `ShippedDatasetsTest`'s loading path. `internal` is visible to the module's test source set |
| `ie.pantry.testutil.Sentinels`, `assertNoSentinel()`, `RepoPaths` | Test-only calls into | No | R10 and the source-inspection tests |
| `ie.pantry.data.reference.ReferenceDataStore.aliases()` | Called by (future F6, F11) | No | Callers unwrap `LoadResult<AliasTable>` and handle `LoadFailed` themselves before building an engine (F2 AD2; spec Modified Files) |
| F6 recipe form (future) | Called by | No (F6's spec) | Calls `parse` on every route and persists `toColumns()` plus the key. It handles `CanonicalKey.Absent` (spec Q8). Its CFC-1 route tests use `ParseFidelityCorpus` + `ShippedReferenceTables` (AD14) |
| F11 list generation (future) | Called by | No (F11's spec) | Reads rows back with `QuantityColumns.decode()`, scales with `scaledBy`, and merges with `mergeWith` **in a fixed fold order** (DR3) |

The one load-bearing touch is `aliases.json`: it is shipped data read by every future consumer of the alias table. The change is append-only, and both F2's and F3's shipped-content tests guard it.

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| DR1 | A grammar row reads a wrong number with confidence (spec RK1), for example G13 reading `2 400g tins` as `COUNT 2` if G12 is implemented after G13 | Med | High | The table order in FC3 is normative. `QuantityParserTest` has one line per row, including the precedence cases (`2 400g tins` → G12). The ≥50-line corpus is authored before the parser (Implementation Sequence step 2) |
| DR2 | A `private class` in `IngredientEngine.kt` implementing an `internal sealed interface` fails to compile, or leaks through an exposure error, under Kotlin 2.1.0 | Low | Med | Proven first, in Implementation Sequence step 3, by a skeleton that compiles. Fallback: declare `ScannedLine` as a `sealed class` with a `private` constructor in the same file (same gate) |
| DR3 | F11 folds three or more merges in different orders and gets `Double` results that differ in the last bit (spec RK4) | Med | Med | AD2 and AD3 guarantee pairwise commutativity exactly. The Integration Points row puts "fold in a fixed order" on F11. Recorded here so F11's Design has to pick up the obligation |
| DR4 | `EntryPointTest`'s regex misses a declaration form (a default-argument parameter containing parentheses, an `operator fun invoke`) or flags a legitimate one | Med | Low | Negative-control snippets; the AD4 explicit-`private` style rule; the sealed `ScannedLine` gate stops the rule and parser being reached even if the regex misses something |
| DR5 | Corpus expected values are copied from engine output (spec RK6) | Med | High | Implementation Sequence step 2 authors and commits the corpus before FC3 or FC4 exists, so the order is visible in `git log`. R8 AC5 is confirmed at review |
| DR6 | AD8's folding or AD9's extra vocabulary silently changes a shipped key (for example a future staples key that starts with `some`) | Low | Med | `ShippedKeyReconciliationTest` runs over every shipped key on every build. Design Q1 asks the developer to confirm the vocabulary. Changes are Ask First |
| DR7 | AD12's catch-all hides a parser defect as `UNREADABLE` | Low | Med | Every corpus and R2/R4 line asserts an exact expected value, so a hidden defect shows up as a mismatch. `EngineErrorHygieneTest` is the only test that deliberately triggers the fallback |
| DR8 | A classpath read of `ingredient/*.tsv` behaves differently under Robolectric's sandbox classloader | Low | Low | F2 already reads `reference/fixtures/*.json` from the test classpath under Robolectric. `ParseFidelityCorpusCrossPackageTest` exercises the same read from a second package |
| DR9 | The corpus page list (spec Q9) is not supplied before corpus authoring, which stalls R8 | Med | Med | Design Q3 below blocks the Tasks phase on it. The FC1–FC5 steps can proceed in parallel |

## Implementation Sequence

1. **FC1 model** (`Units.kt`, `Quantity.kt`) with the `QuantityParserTest` round-trip, token-uniqueness and construction-guard tests, plus `ConversionScalingMergeTest`'s construction-level cases. Everything else depends on these types.
2. **FC7 corpus authoring and loader** (`parse_fidelity_corpus.tsv`, the extension fixture, `ParseFidelityCorpus.kt`). Authoring the TSV **runs in parallel with step 1 and must finish before step 4 starts** (DR5, RK6), and it needs the design Q3 page list. The loader follows step 1, because it uses FC1's `CanonicalKey` and `QuantityColumns`.
3. **FC2 skeleton and guards.** `IngredientEngine.kt` with the sealed `ScannedLine`, `scan` (N1–N6), and a `parse` that returns `UNREADABLE` from `QuantityParser` and `CanonicalKeyRule` stubs taking `ScannedLine`. Add `EntryPointTest` (AC1 and the AC3 totality cases, whose expected values are already correct for the edge cases), `EnginePurityTest` and `EngineErrorHygieneTest`'s source-inspection half. This step retires DR2 and puts R1, R9 and R10 guards in place before any parsing logic exists.
4. **FC3 quantity parser** with `QuantityParserTest` (R4 AC1–AC5), grammar rows G1–G13 in table order. This is the highest-risk step (DR1), so it comes first among the logic steps.
5. **FC4 canonical-key rule** with `CanonicalKeyRuleTest`. It can run in parallel with step 4 once step 3 is in, because the rule only needs `nameStart`.
6. **FC5 operations** with `ConversionScalingMergeTest`. This can start any time after step 1, in parallel with steps 3–5.
7. **FC6 reconciliation and shipped checks.** The `aliases.json` and provenance edits, `ShippedReferenceTables.kt`, `ShippedKeyReconciliationTest`, and a re-run of `ShippedDatasetsTest` and `ProvenanceRecordTest`. Needs steps 4 and 5.
8. **CFC-1 artifacts.** `EntryPointDeterminismTest`, `ParseFidelityCorpusTest` and `ParseFidelityCorpusCrossPackageTest`, plus the rest of `EngineErrorHygieneTest` (sentinel values and the throwing seam). Needs steps 2 and 7. Any mismatch between the corpus and the parser is fixed on the side the developer judges wrong, and never by copying engine output into the corpus.
9. **Closeout.** The spec's Commands: the F3 test filter, `ShippedDatasetsTest`, the full `./gradlew :app:testDebugUnitTest`, `./gradlew lintDebug`, and the purity `grep`. `git status app/schemas` stays clean.

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: Confirm the key-affecting vocabulary this design adds beyond spec Q2 and Q4, because it fixes canonical keys for saved recipes once F6 ships (spec RK9, same class as the Ask First word-list rule). The additions are: the lead words `a, an, some, few, several` are consumed into the quantity segment (AD9, G2); `<amount> <unknown word> of` is consumed (G11); apostrophes are removed, and `& : ;` and other symbols become spaces (AD8 N4), so `For the sauce:` gets the key `for the sauce`.
  - **Resolution:** Confirmed as written by the developer (2026-09-29). AD8 N4's folding and AD9's G2/G11 vocabulary stand as the shipped default; no change.
- [x] Q2: Article-only amounts (`a tin of chickpeas`, `an onion`) are **unquantified**, not 1 (AD9, G2). This follows the spec's Never Do ("never default a missing amount to … 1") and ARCHITECTURE R2. The cost is that `an onion` shows as unquantified on the list. Confirm, or rule that a written `a`/`an` counts as an amount read from the line.
  - **Resolution:** Confirmed as written by the developer (2026-09-29). Article-only amounts stay unquantified; a written `a`/`an` is never read as a count of 1.
- [x] Q3: Spec Q9 needs the developer's list of recipe source pages (≥5 distinct domains, ≥1 US-customary site) before the Tasks phase. It has not been supplied yet, and corpus authoring (Implementation Sequence step 2) cannot start without it.
  - **Resolution:** Developer-supplied page list (retrieved 2026-09-29), 5 distinct domains including 3 US-customary sources, satisfying spec Q9's floor:
    - `https://www.bbc.co.uk/food/recipes/vegetarianchilli_6544`
    - `https://www.bbc.co.uk/food/recipes/vegetarian_shepherds_pie_73637`
    - `https://www.bbc.co.uk/food/recipes/marry_me_chickpeas_69231`
    - `https://www.bbc.co.uk/food/recipes/falafel_wraps_59124`
    - `https://www.bbc.co.uk/food/recipes/veggie_meatball_biryani_21364`
    - `https://thefirstmess.com/2018/04/04/quick-smoky-red-lentil-stew-vegan-recipe/`
    - `https://www.bonappetit.com/recipe/greek-salad-recipe` (US-customary)
    - `https://www.allrecipes.com/marry-me-chickpeas-recipe-11688105` (US-customary)
    - `https://www.seriouseats.com/roasted-sweet-potato-soup-recipe-8735317` (US-customary)

    This list unblocks Implementation Sequence step 2 (corpus authoring) in the Tasks phase.

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

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes                                                                                                                             |
|------|------------|-------|-------------|-----------|----------|--------|-----------------------------------------------------------------------------------------------------------------------------------|
| 1    | 2026-09-29 | 0     | 0           | 0         | 0        | 15     | converged (0 HIGH); tags=d0u0c0                                                                                                   |
| 2    | 2026-09-29 | —     | —           | —         | —        | —      | skipped (mechanical: resolved design Open Questions Q1-Q3 with developer-confirmed answers; no new interface/contract/AC content) |
| 3    | 2026-10-04 | 5     | 0           | 11        | 0        | 9      | tags=d3u0c2 upstream-panel 741e0809 |

### Sealed dispositions

- `[SEAL-01]` **AD10 orders stage-1 alias lookup (K4) before the…** (pass 1, accepted-as-risk) — Defense: K4-before-K5 ordering is a genuine design call, not left ambiguous — AD10's Rationale cell already states it ("the data should win"). Tagging it `[ASSUMPTION]` is a labeling nicety with no behavioral ambiguity to resolve.
- `[SEAL-02]` **`QuantityColumns` | `RecipeIngredient` is a…** (pass 1, accepted-as-risk) — Defense: F6 doesn't exist yet; naming F6's own test/fixture in F3's Integration Points row would be a forward reference into an unwritten artifact. Per spec Non-Goals, F6 owns its own route tests.
- `[SEAL-03]` **`EntryPointTest`'s regex enforcement of R1 has a blind spot…** (pass 1, accepted-as-risk) — Defense: AD4's Consequences cell already documents the sealed-type-is-primary / regex-is-secondary relationship ("A false positive is fixed by adding private (DR4)"), and FC2 states `ScannedLine` is package-and-module-scoped so no other feature can implement it. A one-sentence restatement is a clarity nicety, not a correctness gap.
- `[SEAL-04]` **DR2's `ScannedLine` fallback mechanism is recorded only as…** (pass 1, accepted-as-risk) — Defense: DR2 is already cross-referenced from AD4's row via Implementation Sequence step 3; duplicating the prose is a presentation preference.
- `[SEAL-05]` **The `ignoreCase = true` sentinel-comparison note isn't…** (pass 1, accepted-as-risk) — Defense: cosmetic — Testing Strategy's `ignoreCase` note and Error Handling's R10 discipline already state the same rule independently and consistently.
- `[SEAL-06]` **`ParseFidelityCorpus.load()`'s documented malformed-row…** (pass 1, accepted-as-risk) — Defense: the failure mode is already stated in DM8/I7's contract; adding the assertion is a straightforward Tasks/Implementation-phase addition, not a design gap.
- `[SEAL-07]` **AD5's "safe to share across threads" claim is untested by…** (pass 1, accepted-as-risk) — Defense: the claim rests on the engine holding no mutable state, which Data Models/Component Design already establish structurally. A concurrency test is a Testing Strategy enhancement.
- `[SEAL-08]` **AD9's numeric cap (6 integer/6 fractional digits) has no…** (pass 1, accepted-as-risk) — Defense: the boundary rule is already fully specified in AD9/FC3; a boundary test line is a Tasks-phase coverage refinement.
- `[SEAL-09]` **Duplicate corpus rows with identical line text but…** (pass 1, accepted-as-risk) — Defense: a corpus-authoring hygiene concern best enforced when the corpus is authored (Implementation Sequence step 2); DM8 already requires human-authored expected values, the primary safeguard against drift.
- `[SEAL-10]` **Grammar row G12 has no concrete example line, unlike…** (pass 1, accepted-as-risk) — Defense: cosmetic completeness gap; G12's rule text is unambiguous without an example.
- `[SEAL-11]` **R1 AC3's edge-case list omits a lone/unpaired UTF-16…** (pass 1, accepted-as-risk) — Defense: AD12's catch-all and the Normalizer/Character-based scanner already cover any malformed input, surrogate or not; no new design behavior needed.
- `[SEAL-12]` **`EnginePurityTest` AC2 hardcodes six JVM-runner test class…** (pass 1, accepted-as-risk) — Defense: the six classes are exactly this design's File Structure; if a later phase adds a seventh, generalizing the check belongs with that phase's own touch of the file, not this design.
- `[SEAL-13]` **AD8 N4's apostrophe-removal/symbol-folding behavior is…** (pass 1, accepted-as-risk) — Defense: already tracked as this design's own Open Question Q1, which is unresolved and blocks progression to Tasks until the developer confirms it. No separate disposition needed.
- `[SEAL-14]` **`MAX_LINE_CHARS` is declared `internal` rather than…** (pass 1, accepted-as-risk) — Defense: it is an `Int` constant with no `String` parameter, so it falls outside AD4's raw-text-acceptor boundary regardless of visibility; tightening it is an Implementation-phase cleanliness nicety.
- `[SEAL-15]` **`ParseFidelityCorpus.load()` has no stated cap on resource…** (pass 1, accepted-as-risk) — Defense: the corpus is developer-authored, hand-transcribed fixture content (spec R8, Q9), not attacker-controlled input; AD7's cap and RK7's threat model apply to imported/untrusted runtime lines, not the committed corpus file.
- `[SEAL-16]` **The no-op claim on an absent directory is unverified** (pass 3, accepted-as-risk) — Defense: the guarded block is verified by code review, and the spec states the closeout reading. A Gradle test of the absent case would add a build variant with no behavioural value. Synthesizer-judged, not user-confirmed.
- `[SEAL-17]` **Conditional build config on a gitignored local path weakens…** (pass 3, accepted-as-risk) — Defense: the developer approved the conditional block at the Phase-4 completion gate. Moving to an opt-in is a future migration, not part of this backport. Synthesizer-judged within the user's decision, not user-confirmed.
- `[SEAL-18]` **Host-dependent test behaviour has no AD or Risks row** (pass 3, accepted-as-risk) — Defense: the decision is recorded in File Structure, Dependencies and Integration Points, and it is a sandbox-only Minor deviation. Synthesizer-judged, not user-confirmed.
- `[SEAL-19]` **Backport process record sits in File Structure** (pass 3, accepted-as-risk) — Defense: one sentence serves as the audit link to the completion-gate decision. Synthesizer-judged, not user-confirmed.
- `[SEAL-20]` **The trust model for a locally resolved SDK jar is not…** (pass 3, accepted-as-risk) — Defense: the directory is gitignored and local-write access already implies full local compromise, the same rationale as the spec's SEAL-09. Synthesizer-judged, not user-confirmed.
- `[SEAL-21]` **Suite-wide scope splits the resolution path of F1 and F2…** (pass 3, accepted-as-risk) — Defense: the spec's Modified Files states the suite-wide scope, and CI uses the normal resolution path. This is the disclosed sandbox-evidence caveat. Synthesizer-judged, not user-confirmed.
- `[SEAL-22]` **Sandbox versus environment wording drifts between the…** (pass 3, accepted-as-risk) — Defense: the spec is the authority for the label, and the drift is cosmetic. Synthesizer-judged, not user-confirmed.
- `[SEAL-23]` **The isDirectory check is evaluated at configuration time** (pass 3, accepted-as-risk) — Defense: the spec states configuration time, and closeout runs in a fresh configuration. Synthesizer-judged, not user-confirmed.
- `[SEAL-24]` **The present-tense Robolectric resolution claim should carry…** (pass 3, accepted-as-risk) — Defense: the spec records the Phase-4 verification of the resolver, and the design defers to it. Synthesizer-judged, not user-confirmed.

### Deferred dispositions

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `1f5888770b769298`
- **Hash basis:** v2