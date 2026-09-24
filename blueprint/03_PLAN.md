# Implementation Plan: Pantry

**Scope:** `blueprint/01_SCOPE.md`
**Architecture:** `blueprint/02_ARCHITECTURE.md`

## Feature Breakdown

### F1: App Skeleton, Build Configuration & Room Foundation

- **Description:** Stand up the single-module Android app — Gradle Kotlin DSL with a version catalog, `minSdk` 26 / `compileSdk` 35 / JDK 17, Compose + Material 3, the manual constructor-injection app container, and the Room database with every entity, DAO, exported schema and a written migration path. No user-facing feature ships here; this is the durable substrate every later feature writes through.
- **Component:** C3 — Catalogue & Persistence (builds); the app-scoped DI container and build configuration that C1–C10 all sit inside.
- **Acceptance Criteria:**
  - The project builds a debug and a release APK from a clean checkout with `./gradlew assembleDebug assembleRelease`; the release build has `minifyEnabled false` and the manifest declares `android:allowBackup="false"`.
  - Room entities exist for `Recipe`, `RecipeIngredient`, `ShoppingListSelectionEntry`, `ShoppingList`, `ShoppingListItem`, `RetailerAssistSession` and `NutritionCacheEntry`, with exported schema JSON committed under version control.
  - DAO reads for recipes, selection entries, lists and the assist pointer are exposed as `Flow` and emit an updated value when the underlying row changes — asserted by a Robolectric DAO test.
  - Every recipe-backed `Flow` the DAO layer exposes excludes any `Recipe` row whose `pendingDeletionAt` is non-null, and emits it again once that column is cleared — asserted by a Robolectric DAO test that seeds a row, sets the flag and asserts the `Flow` no longer emits it, then clears the flag and asserts it reappears.
  - `Recipe.updatedAt` is bumped by the persistence layer on any write to a `Recipe` row or one of its child `RecipeIngredient` rows, including deletion of a child row — asserted by a DAO test.
  - A Robolectric-based migration-test harness is established, asserting data-preserving round-trips against a populated fixture (not merely that a migration resolves); at schema version 1 there is no prior version to round-trip against, so this AC is the harness existing and passing vacuously, exercised for real against the first actual schema migration whenever one is introduced (R9).
  - An instrumented or Robolectric test confirms the database is readable with the network disabled and after a simulated process restart.
  - No exception constructed by the persistence layer (DAO or database-open/migration code) embeds a recipe title, ingredient line, or any other row content in its message, caught or uncaught. [CFC-4]
  - Writing a recipe's thumbnail always decodes, downsamples and re-encodes the source bytes as JPEG before writing the file, regardless of whether the bytes came from an import fetch or the device photo picker — asserted by a test that persists a recipe with a thumbnail from each source and confirms the written file is a downsampled JPEG in both cases. The decode is bounds-aware — dimensions are read and a sample size computed before decoding at scale, never at native resolution — asserted by a test with a large-dimension source image that would exceed a naive decode's memory budget.
  - Source bytes that fail to decode as an image, and a local write failure while writing the thumbnail file, each degrade to the same explicit no-image state a missing image produces — never a hang or a crash, and the recipe itself still saves — asserted by a test for each failure mode.
  - Replacing an already-saved recipe's thumbnail deletes the old file once the new one is written; no orphaned thumbnail file survives its recipe after a replace — asserted by a test that replaces a thumbnail and confirms the old file no longer exists on disk.

### F2: Reference Data Store & v1 Dataset Curation

- **Description:** Ship the four developer-curated read-only datasets as JSON assets in the APK, plus the loader that reads them once, caches them in memory and answers lookups by canonical key. Curates the v1 content of each dataset: the ~170-entry staples nutrition table, the alias table seeded with known exceptions, a first Republic-of-Ireland seasonality/substitution slice, and the complete fixed Tesco Ireland section-ordering list. Resolves `[DEF-01]` by making dataset curation a single scheduled feature rather than unbounded background work: the section-ordering list must be complete at v1 (G3 depends on it wholesale), while the seasonality and alias datasets ship at partial coverage and grow through F17.
- **Component:** C2 — Reference Data Store (builds).
- **Acceptance Criteria:**
  - `StaplesEntry`, `AliasEntry`, `SeasonalityEntry` and `SectionOrderEntry` datasets load from bundled JSON assets and are queryable by canonical key.
  - A lookup miss returns an explicit absence value distinguishable from a zero, an empty record or a default — asserted by a unit test per dataset.
  - The staples table contains approximately 170 raw-ingredient entries, matching SCOPE's stated figure; the section-ordering list assigns a section and a walk-order index to every section it names and is internally complete (no section without an index, no index collision).
  - The seasonality/substitution dataset ships at v1 with a developer-curated seed slice covering common Irish fruit, vegetable and herb staples — no fixed numeric target (Q1, resolved) — and is versioned in git as plain JSON, so a coverage increase is a data edit with no schema migration. F2's own bar is this seed slice existing and loading correctly, not any particular coverage level; growing coverage to match what the developer's own recipes (imported or hand-entered, once F5/F6 exist) actually exercise is F17's post-release criterion, not F2's.
  - Dataset loading is lazy and off the main thread; a Compose UI test asserts first paint is not blocked by dataset load on a cold start.
  - The datasets are read-only at runtime: no API on this component mutates loaded data, verified by inspection of the public surface.

### F3: Ingredient Normalisation & Quantity Engine

- **Description:** The pure-Kotlin correctness centre of the project. Parses a free-text ingredient line into a canonical ingredient key (rule-derived: case-fold, strip preparation/quantity words, singularise, then the C2 alias table for exceptions) plus a typed dimensioned quantity (mass / volume / count / unquantified), performs within-dimension conversion and servings scaling, and answers the merge-compatibility predicate. No Android dependencies.
- **Component:** C4 — Ingredient Normalisation & Quantity Engine (builds); C2 — Reference Data Store (uses, for the alias table).
- **Acceptance Criteria:**
  - Two lines with the same canonical key and the same unit dimension merge (1 kg + 500 g → 1.5 kg); two lines differing in canonical key or in dimension never merge (400 g tomatoes and 2 tbsp tomato purée stay separate) — asserted by unit tests covering both directions.
  - A quantity the parser does not confidently recognise yields an explicit *unquantified* value; no code path in this component can emit an invented number for an unrecognised quantity. [CFC-2]
  - The canonical-key rule is applied first and the C2 alias table consulted only for exceptions; a unit test asserts an irregular plural and a brand-name-to-generic variant both resolve via the table while a regular plural resolves via the rule alone.
  - Scaling by a servings ratio multiplies a dimensioned quantity proportionally and leaves an *unquantified* value unquantified.
  - A unit-test corpus of at least 50 real ingredient lines, hand-transcribed from real recipe web pages the developer selects independently of F5 (F5 does not exist yet at this build position), runs on the JVM and passes; this corpus is the shared parse-fidelity fixture named by [CFC-1] and is extended with lines actually harvested through F5's import pipeline once F5 ships.
  - The module compiles with no `android.*` import — asserted by a build-level check or an inspection test.
  - No exception constructed by this component's parsing or matching logic embeds the raw ingredient-line text in its message, caught or uncaught. [CFC-4]

### F4: External Data Gateway

- **Description:** The app's single HTTP call site, carrying all three of its data-fetching call types — the recipe page fetch, that page's thumbnail image fetch, and the ingredient nutrition lookup: an OkHttp client behind one policy boundary carrying timeouts, retry/back-off, a transport-level raw-byte cap, redirect limits, anonymous request construction, and the connection-target validation that keeps a shared URL from probing the device's own network. Every failure maps to a typed error.
- **Component:** C7 — External Data Gateway (builds).
- **Acceptance Criteria:**
  - Every connection target — the initial request and every redirect hop — is validated as `http(s)` before connection; a redirect to `intent://` or `content://` is refused with a typed error, asserted by a test.
  - The resolved address of every connection is checked on both IP families against loopback (`127.0.0.0/8`, `::1`), link-local (`169.254.0.0/16`, `fe80::/10`) and private ranges (RFC 1918, `fc00::/7`) and refused if it matches — asserted for both a direct URL and a redirect chain.
  - A timeout, a non-2xx response and an oversized response each return a distinct typed error within the configured timeout, never a hang and never an exception escaping the component.
  - A transient failure (a connection-level failure or a 5xx response) is retried a bounded number of times with back-off before surfacing a typed error; the timeout AC above bounds each individual attempt, not the retried call as a whole — asserted by a test that forces repeated transient failures and confirms the call eventually surfaces a typed error rather than retrying forever, and a test that a non-retryable failure (e.g. a 4xx response) surfaces its typed error immediately with no retry.
  - No request carries a credential, an API key, or any Pantry-owned data: the recipe fetch sends only the user-supplied URL as the GET target, the thumbnail image fetch sends only the page-supplied image URL as the GET target, and the nutrition lookup sends only a URL-encoded ingredient term built via a URL-builder API — asserted by a request-inspection test covering all three call types.
  - No typed error produced here embeds the requested URL, response body, or any fragment of fetched content in its message; a third-party library exception is re-wrapped before it can propagate unchanged. [CFC-4]

### F5: Recipe Import Pipeline

- **Description:** Turns a shared or pasted `http(s)` URL into an editable in-memory recipe draft: share-target and paste intent handling, URL validation, triggering the fetch through C7, layered extraction (JSON-LD `schema.org/Recipe` → microdata/RDFa → heuristic fallback), image selection under the structured-metadata-before-inline-`<img>` preference order with the image fetched during the same import and carried on the draft as validated in-memory bytes, and capture of `sourceUrl` + `fetchedAt`. Also carries the user-initiated share/export of a declined page's URL, the only feedback path for a possible misclassification.
- **Component:** C1 — Recipe Import (builds); C7 — External Data Gateway (uses); C9 — UI Shell & Accessibility Layer (builds the decline-error surface and its retry/share-export controls). This feature stops at the in-memory draft; the draft's confirm-and-parse-and-persist step — ARCHITECTURE's C1→C4 and C1→C3 edges — is executed by F6 (C9) rather than here, since F6 is the single shared confirmation path for every entry route (import, manual, edit) per `[CFC-1]`.
- **Acceptance Criteria:**
  - A page carrying a JSON-LD `Recipe` produces a draft with title, ingredient lines and method populated; a page with only microdata produces a draft via the microdata path; a page with neither produces a heuristic draft.
  - A page from which *nothing* is extracted is declined with a clear, specific "not recognised as a recipe" error whose wording is distinguishable from the parse-badly path, and the user can immediately try another URL without restarting the app — asserted by a test over both fixture pages.
  - Any page from which something is extracted, however incomplete, yields an editable draft; no import path terminates without either an editable draft or a typed error — asserted by tests over timeout, 404, non-HTML and malformed-HTML fixtures.
  - A non-`http(s)` URL or a non-HTML payload is rejected with a plain error before any parse is attempted.
  - Decompressed-content size is capped as it streams during parsing (not only after parsing completes) and JSON-LD parse/recursion depth is bounded — asserted with a gzip-bomb fixture and a deeply-nested JSON-LD fixture.
  - `sourceUrl` and `fetchedAt` are captured on the draft; cooking time is captured when the page states one and left explicitly absent otherwise, never defaulted. [CFC-2]
  - Where the page offers a usable image under the preference order, it is fetched during the same import and carried on the draft as validated in-memory bytes — no file is written here, since the thumbnail file is produced by C3 when F6 persists the recipe; where none is available the draft carries an explicit no-image state.
  - A candidate image whose fetched bytes fail to decode as a genuine raster image is treated as an explicit no-image state, the same as a page offering no image at all — never retried as HTML and never carried on the draft unvalidated — asserted by a test with a fetched-bytes fixture that isn't a valid image.
  - From a declined page the user can share or export that URL through the system share sheet; the app itself transmits nothing about the decline on its own.
  - No exception constructed in this component embeds the URL, fetched HTML, or an ingredient line in its message. [CFC-4]
  - At least 20 real ingredient lines actually harvested by this feature's import pipeline (distinct from F3's original hand-curated seed corpus) are added to the shared parse-fidelity corpus F3 owns, discharging F3's own true-up commitment once F5 ships. [CFC-1]

### F6: Recipe Form — Import Draft, Manual Entry, Post-Save Edit & Delete

- **Description:** One Compose recipe form serving all three entry points (`[DEF-03]`): confirming an import draft, creating a recipe entirely by hand, and editing an already-saved recipe. Same fields in every case, pre-populated from the draft, from nothing, or from the stored row respectively; ingredient lines route through C4 on save by the same path regardless of entry point; the device photo picker attaches or replaces a thumbnail. Also carries deletion as a single two-stage remove-then-undo action. Since this feature ships in Milestone 2 and F7's full browse/compare recipe detail screen doesn't ship until Milestone 3, F6 builds a minimal recipe-access surface (e.g. a plain title/summary view reached from the catalogue) sufficient to host the edit and delete entry points; F7 later supersedes it with the full detail screen, and edit/delete are re-hosted there without any change to this feature's contract.
- **Component:** C9 — UI Shell & Accessibility Layer (builds the form and the minimal recipe-access surface); C3 — Catalogue & Persistence (uses); C4 — Ingredient Normalisation & Quantity Engine (uses).
- **Acceptance Criteria:**
  - The same form composable is used for all three entry points, differing only in its initial state — asserted by a Compose test that opens it from an import draft, from manual entry and from a saved recipe's edit action.
  - A saved recipe persists across app restart and device reboot and is readable with the network off.
  - A manually entered recipe can carry the same optional fields an import can (cooking time, servings/yield, thumbnail from the device photo picker) and is indistinguishable downstream from an imported recipe except for having no `sourceUrl`/`fetchedAt`.
  - An optional field (cooking time, yield, thumbnail) left blank on manual entry or cleared on a post-save edit persists as an explicit absence, never a coerced zero, empty string or default value — asserted by a test on both the manual-entry and post-save-edit paths. [CFC-2]
  - Ingredient lines typed by hand, ingredient lines confirmed from an import draft, and ingredient lines changed in a post-save edit are all parsed by the same C4 entry point — asserted by a test that drives identical text (drawn from F3's shared parse-fidelity corpus) through all three routes and confirms the resulting canonical keys and quantities are byte-identical. [CFC-1]
  - Saving requires a title and at least one ingredient line on every path; a save attempt missing either is refused with an inline message.
  - A post-save edit updates the recipe and bumps `updatedAt` but does not change `sourceUrl` or `fetchedAt` — asserted by a test.
  - Deleting a recipe removes it from the catalogue and from the shopping-list selection immediately and offers an Undo affordance that stays available long enough for an assistive-tech user to act on; Undo restores the recipe and its selection membership exactly.
  - If the app leaves the foreground for any reason before the undo window elapses — backgrounded, or the process killed — the pending deletion is discarded in full and the recipe (and its selection membership) reappears the moment the app is next brought to the foreground, whether that is a resume from the background or a fresh relaunch — asserted by a test that kills and relaunches during the window and confirms both the recipe row and its selection-list membership reappear, and a separate test that merely backgrounds and foregrounds during the window and confirms the same. Navigating between screens *within* the running app during the window (e.g. the forced hop from the recipe-access surface back to the catalogue that the delete action itself causes) is not backgrounding and does not discard the pending deletion — asserted by a test that performs that navigation, waits, and confirms the deletion still finalizes on schedule.
  - If the undo window elapses with no Undo tap while the app stays in the foreground continuously, the pending deletion is finalized: the recipe row and its thumbnail file are both actually deleted — asserted by a test that lets the window elapse without an Undo tap and without backgrounding the app, then checks the row and the thumbnail file are gone.
  - Two recipes deleted moments apart each keep their own independent undo window and finalize (or get discarded) on their own schedule, not a single shared timer — asserted by a test that tap-deletes two recipes in quick succession, undoes only one, and confirms the other still finalizes independently.
  - No exception constructed here embeds a recipe title, ingredient line or URL in its message. [CFC-4]

### F7: Catalogue Browse, Search, Filter & Recipe Detail

- **Description:** The browse/compare surface: a card list showing thumbnail, cooking time, energy (kcal), the aggregate seasonality indicator and the nutrition-caveat indicator, with an independent and combinable name search and seasonality-status filter; plus the recipe detail screen carrying full ingredients, method and the nutrition breakdown. Defines the two indicator families (icon or text, never colour alone) that every later screen reuses.
- **Component:** C9 — UI Shell & Accessibility Layer (builds); C3 (reactive reads), C6 — Nutrition Service and C10 — Seasonality & Substitution Service (uses).
- **Acceptance Criteria:**
  - Each card shows cooking time, kcal, a seasonality indicator and a nutrition-caveat indicator without the recipe being opened; the seasonality and caveat indicators are visually distinct from each other and from the no-caveat state, and each is distinguishable by icon or text without colour. [CFC-3]
  - A recipe with no stated cooking time shows an explicit no-time state, and a recipe with no usable image shows an explicit no-image state — never a blank, an invented figure, or a placeholder that could be mistaken for the recipe's own picture. [CFC-2]
  - Typing part of a name filters the list; selecting one or more of *in season* / *out of season* / *partial* filters the list; the two filters combine, and clearing the name search leaves any active season filter untouched — asserted in both directions by a Compose test.
  - A combination matching nothing shows an explicit "no recipes match" empty state, not a blank screen.
  - The detail screen shows full ingredients, method, and the nutrition breakdown including which figures are estimated and what proportion of ingredients contributed real data.
  - The detail screen shows, beside each out-of-season ingredient, its substitution suggestion where C10 returns one, with the no-allergen-safety-guarantee disclaimer visible alongside it; an out-of-season ingredient with no available substitution, and an in-season or unknown ingredient, show no substitution UI at all — asserted by a test covering all three cases. [CFC-2]
  - An edit or deletion of a recipe propagates into the list and detail views without manual refresh, via the C3 `Flow` reads.
  - Browsing, searching, filtering and opening a recipe's detail screen are fully usable with the network disabled — asserted by a test that runs with no gateway available.
  - No exception constructed while rendering the card list or the detail screen embeds a recipe title, ingredient line, or method text in its message, caught or uncaught. [CFC-4]

### F8: Seasonality & Substitution Service

- **Description:** Pure-domain evaluation of an ingredient's seasonality against the bundled Republic-of-Ireland growing calendar by canonical key and current date, retrieval of substitutions for out-of-season ingredients, and computation of a recipe's three-state aggregate under the strict precedence rule.
- **Component:** C10 — Seasonality & Substitution Service (builds); C2 — Reference Data Store (uses).
- **Acceptance Criteria:**
  - The aggregate rule evaluates in strict order — *out of season* if any recognised ingredient is out of season regardless of unknowns; otherwise *partial* if any ingredient is unrecognised; otherwise *in season* — asserted by a unit test covering all four recognised/unrecognised × in/out input combinations.
  - An ingredient absent from the reference returns *unknown*, never in-season or out-of-season. [CFC-2]
  - *Unknown* and *out of season* are separate values in the returned type and cannot be collapsed by a caller.
  - An out-of-season ingredient whose reference entry names a substitution returns at least one substitution suggestion; an out-of-season ingredient with no substitution defined returns none rather than an invented one — asserted by a unit test covering both cases against a fixture reference entry.
  - Every returned substitution is accompanied by the no-allergen-safety-guarantee disclaimer text, which the caller cannot render the substitution without.
  - Evaluation is date-driven and testable with an injected clock; a unit test asserts the same ingredient reports differently in two different months.
  - The component compiles with no `android.*` import.
  - No exception constructed during seasonality evaluation or substitution retrieval embeds the ingredient's canonical key or term in its message, caught or uncaught. [CFC-4]

### F9: Nutrition Service — Staples Path & Disclosure Record

- **Description:** Per-recipe nutrition totals computed entirely offline against the bundled staples table, with the disclosure record that makes G4's honesty structural: which figures rest on an estimated weight conversion and what proportion of a recipe's ingredients contributed real data. Unmatched ingredients contribute nothing and increment the unmatched count. The external lookup path is deliberately not in this feature (see F16).
- **Component:** C6 — Nutrition Service (builds); C2 — Reference Data Store (uses).
- **Acceptance Criteria:**
  - A recipe whose ingredients all match the staples table yields per-recipe totals plus a disclosure record reporting a full match proportion.
  - An unmatched ingredient contributes exactly zero to every total and increments the unmatched count; a unit test asserts the totals for a recipe with one unmatched ingredient equal the totals for the same recipe with that ingredient removed. [CFC-2]
  - A total is never returned without its disclosure record attached — the return type makes an aggregate-without-disclosure unrepresentable.
  - A figure resting on an estimated weight conversion is flagged as estimated in the disclosure record.
  - Every figure in this feature is produced with the network disabled — asserted by a test that runs with no gateway available.
  - No exception constructed while matching an ingredient against the staples table or computing totals embeds the ingredient's text in its message, caught or uncaught. [CFC-4]

### F10: Shopping-List Selection

- **Description:** Maintains the set of recipes chosen for the next shop with a per-entry servings figure, reachable from both the card and the recipe detail screen with nothing to designate first. Defaults servings to the recipe's stated yield, or to a 1× baseline where the recipe states none, and thereafter preserves the user's own value across an edit of the source recipe. Persisted so it survives restarts.
- **Component:** C5 — Shopping List Selection & Generation (builds the selection half); C3 (uses); C9 (affordances).
- **Acceptance Criteria:**
  - A recipe can be added to and removed from the selection from the card view and from its detail screen, with no day, meal or other designation required at any point.
  - Adding a recipe sets its servings to the recipe's stated yield, or to a 1× baseline when the recipe states none; the user can change it afterwards.
  - Editing a source recipe updates the selection entry's displayed nutrition and cooking time while leaving the user's servings figure unchanged; where the edited recipe now carries no cooking time or an unmatched/estimated nutrition figure, the selection entry displays the same explicit absence/caveat state the card does, never a coerced zero or a stale prior value — asserted by a test. [CFC-2]
  - Deleting a source recipe drops its selection entry with a visible notice, never leaving stale data on screen.
  - The selection survives app restart and process death — asserted by a persistence test.
  - The selection is fully usable with the network disabled.
  - Every status indicator this screen renders (nutrition-caveat, missing-cooking-time) is drawn from the shared indicator vocabulary and distinguishable by icon, shape or text, never colour alone. [CFC-3]

### F11: Shopping-List Generation

- **Description:** Collapses the selection into one merged, scaled, walk-ordered shopping list: scale each recipe's contributions by servings ÷ yield through C4, group by canonical key, merge only where C4's compatibility predicate allows, assign each entry a supermarket section from C2's ordering list, and emit a stable sequence with a terminal bucket for unknown sections. The generated list is persisted as a snapshot and is the artefact the user shops from. Also builds the plain list screen itself — the walk-ordered, sectioned view of the generated list — independently of F12's retailer-assist walkthrough, so the list stays fully usable on its own if the walkthrough is skipped or a retailer page fails to load.
- **Component:** C5 — Shopping List Selection & Generation (builds the generation half); C9 — UI Shell & Accessibility Layer (builds the plain list screen); C4, C2 (uses); C3 (persists).
- **Acceptance Criteria:**
  - The same ingredient appearing in several selected recipes appears once on the list with its quantities combined, where the units are physically comparable.
  - Non-comparable quantities of a nominally similar ingredient remain separate entries — asserted with the 400 g tomatoes / 2 tbsp tomato purée case.
  - A 4-serving recipe selected for 6 servings contributes 1.5× its quantities; a recipe with no stated yield contributes at its 1× baseline.
  - An ingredient whose quantity cannot be parsed or compared appears marked as unquantified and carries no number; an entry whose section is unknown lands in the explicit terminal bucket rather than being dropped or guessed into a section. [CFC-2]
  - Entries are grouped and ordered by supermarket section in a stable sequence, not recipe order; regenerating from an unchanged selection produces a byte-identical order — asserted by a repeat-generation test.
  - A generated list is a snapshot: a later edit or deletion of a source recipe changes the live selection but leaves an already-generated list unaltered — asserted by a test.
  - The generated list is persisted and fully readable with the network disabled.
  - Indicators for unquantified and unknown-section entries are distinguishable by icon or text, not colour alone. [CFC-3]
  - The generated list renders as a plain, itemized, section-grouped view — reachable without entering the retailer-assist walkthrough and fully operable if the walkthrough is never started — asserted by a Compose test that generates a list and confirms every entry, its section grouping and its unquantified/unknown-section indicators are visible without opening the walkthrough.
  - No exception constructed during scaling, merging, section assignment or rendering the list screen embeds a recipe title, ingredient line or URL in its message. [CFC-4]
  - The same canonical ingredient contributed by two selected recipes merges identically regardless of whether either recipe's ingredient lines came from an import, manual entry or a post-save edit — asserted by a test that selects one recipe of each origin sharing a common ingredient and checks the merged entry is indistinguishable from a same-origin control. [CFC-1]

### F12: Retailer Assist Walkthrough

- **Description:** Walks the user through a finished list one item at a time, building a Tesco Ireland (`tesco.ie`) search URL from each item's text and opening it in an Android Custom Tab, with advance / skip / back moving only a durable local pointer. Opens with the `[DEF-05]` feasibility spike: determine whether Custom Tabs' bottom toolbar can host the advance/skip/back controls persistently across the retailer's page; if it cannot, the committed fallback is returning to Pantry's own walkthrough screen between items (still one tap per step, still a verifiable address bar on the retailer's page), and the spike's outcome is recorded in the feature's SDD design rather than left open.
- **Component:** C8 — Retailer Assist (builds); C3 (durable pointer); C9 (walkthrough controls).
- **Acceptance Criteria:**
  - The Custom Tabs toolbar spike is completed before any walkthrough UI is built, and its outcome — bottom-toolbar controls or the return-to-app fallback — is recorded with the device/browser configurations it was checked against.
  - Each step opens the retailer's own search results for that item in a Custom Tab; the search URL is built via a URL-builder API with the item text URL-encoded, never by raw string concatenation — asserted by a test over an item containing spaces, an ampersand and a non-ASCII character.
  - Advance, skip and back move only the local pointer; the implementation issues no add-to-basket request, performs no DOM access or script injection, and never navigates to checkout — verifiable by inspection, and asserted by a test that the app's controls produce no network call of their own.
  - No interface string anywhere in the walkthrough asserts that an item is in the retailer's basket; progress wording refers only to the user's position in the local list — asserted by a string-inspection test over the walkthrough's resource strings.
  - The pointer survives backgrounding and process death: relaunching resumes at the same position with the same skipped items, not at the start.
  - Progress ("item 5 of 30") is exposed as text a screen reader reads, and the controls are operable one-handed with no multi-finger gesture. [CFC-3]
  - If the user is not logged in, the retailer's own login flow appears in the Custom Tab with its address bar and TLS indicator visible; Pantry stores no retailer credential.
  - On a device with no Custom Tabs-capable browser, the step falls back to an external browser `Intent`, never to a `WebView` — asserted by a test with the Custom Tabs service unavailable.
  - A failed retailer page load leaves advance / skip / back and the local pointer fully usable; the error surface is the browser's own.
  - No exception constructed here embeds the item text or the built URL in its message. [CFC-4]

### F13: Accessibility Conformance Pass

- **Description:** The consolidated G8 accessibility gate over every screen that has landed: content descriptions on every interactive element, font-scaling without truncation, contrast checks, no colour-only indicator anywhere, and a screen-reader run of the full core flow. ARCHITECTURE's R7 commits to font-scaling and screen-reader checks running informally as each screen lands during development; two earlier features carry a formal AC of that kind — F12's screen-reader-exposed progress text and F14's own accessibility-bar self-certification (content descriptions, font scaling, no colour-only cues on its own screen) — each a narrow, single-screen check rather than an app-wide sweep. F13 is this plan's one formal, scheduled, app-wide accessibility verification checkpoint — it owns catching and fixing whatever its first end-to-end sweep finds, not merely tidying up gaps an earlier gate already closed.
- **Component:** C9 — UI Shell & Accessibility Layer (builds/hardens).
- **Acceptance Criteria:**
  - Every interactive element on every screen carries a content description — asserted by a Compose test sweep over the core-flow screens.
  - Every screen renders without truncation or overlap at the largest system font scale — verified on the orientation, import, form, browse, detail, selection, list and walkthrough screens.
  - No indicator on F7, F10, F11, F12 or F14's screen — the five screen-owning CFC-3 participants this sweep verifies (F13 itself is CFC-3's sixth participant, as verifier rather than renderer; F15 is CFC-3's seventh, self-certifying its own screen the same way F14 does elsewhere, since F15 ships after this sweep and isn't covered by it) — is conveyed by colour alone; each is also distinguishable by shape, icon or text — verified against the indicator vocabulary inventory. [CFC-3]
  - The complete core flow (capture a recipe, browse and choose, generate a list, step through the walkthrough) is exercised end to end with TalkBack enabled and completes without a dead end, with the result recorded.
  - Text and control contrast on the walkthrough controls and both indicator families meets the Material 3 contrast tokens at their rendered sizes.

### F14: First-Run Orientation & Network Error Surfaces

- **Description:** A first-run flow that orients a user who is not the developer to the core flow — import a recipe or enter one by hand, choose recipes, generate a shopping list, shop the list — plus the plain, non-crashing error surfaces for all three network-dependent actions.
- **Component:** C9 — UI Shell & Accessibility Layer (builds).
- **Acceptance Criteria:**
  - On first launch the user is shown an orientation covering capture (both import and manual entry), choosing recipes, generating a list and shopping it, without needing external instructions; it is dismissible and does not reappear on subsequent launches.
  - A failed or timed-out recipe fetch and a failed retailer page load each show a plain message and leave the app usable — never a silent hang and never a crash; asserted by tests that force each failure.
  - A generic failed-lookup error surface exists and is reachable by any C7-backed call; it is asserted here against an injected gateway failure, since no nutrition network call exists in the MVP. F16's own per-ingredient lookup failures deliberately do not raise this surface — a single ingredient's lookup failing is not a page-level failure the way a recipe fetch or retailer page load is, so it instead degrades silently into the disclosure record's unmatched/estimated reporting (F16's own AC), which F7's detail screen already renders — an honest, visible signal of the degraded match proportion in place of a blocking error.
  - No error message shown to the user contains a raw URL, HTML fragment or ingredient line. [CFC-4]
  - The orientation flow itself meets the accessibility bar: content descriptions, font scaling, no colour-only cues. [CFC-3]

### F15: Release Readiness & Distribution Gates

- **Description:** Everything G8 requires that is not a screen: the in-app and store-listing privacy disclosure, the nutrition-source attribution home, the best-effort no-SLA support statement naming one channel, the Play data-safety declaration, content rating and target-API-level verification, the second-device check, and the pre-release migration test run. These are shipping gates, not deferrable breadth.
- **Component:** C9 — UI Shell & Accessibility Layer (in-app disclosure, support-statement and attribution surfaces); the release process around C3's migrations.
- **Acceptance Criteria:**
  - A privacy disclosure is reachable in-app and drafted for the store listing, and accurately states that no personal data is collected or transmitted beyond the outbound requests the app actually makes.
  - A support statement is reachable in-app, names exactly one channel (e.g. a public issue tracker), and states plainly that support is best-effort with no SLA.
  - The nutrition-source attribution surface exists in-app and renders the attribution carried on any externally sourced figure; with only the bundled staples table in use it renders correctly with nothing external to credit.
  - The privacy-disclosure, support-statement and attribution screens each carry content descriptions on every interactive element, render without truncation at the largest system font scale, and use no colour-only cue — self-certified here the same way F14 self-certifies its own screen, since F15 ships after F13's app-wide sweep and is not covered by it. [CFC-3]
  - The app launches and completes the full core flow without crashing on at least one device or emulator profile other than the developer's primary device, with the profile recorded.
  - The migration test suite from F1 is run green immediately before submission, and `compileSdk`/`targetSdk`, the data-safety declaration and the content rating are each re-verified against Play's *current* policy text at that moment (R11, R12) — not against the values recorded in this plan.
  - The release is submitted through Play Console's staged rollout, with the developer's own post-update check on their own data recorded as the primary defence against a silent round-trip defect. Before the public staged rollout, the build is opportunistically run through one of Play Console's internal or closed testing tracks if the developer's account has one already set up — a cheap, optional earlier checkpoint, not a new gate this plan requires standing up.
  - A sweep of exception-construction sites across F1, F3–F9, F11, F12 and F14 — every CFC-4 participant built by this point — confirms no user-supplied or imported content reaches an error message. F16 is not swept here: it ships post-MVP, after this feature, and self-certifies against the same contract via its own `[CFC-4]`-tagged AC bullet when it ships.

### F16: External Nutrition Lookup & Licence Compliance

- **Description:** Adds the second outbound path: for an ingredient the staples table misses, look it up anonymously against the external food-data source through C7, cache the result locally with its source attribution, licence tag and fetch time, and feed it into C6's aggregation. Raises the match proportion reported in the disclosure record without changing any honesty rule.
- **Component:** C6 — Nutrition Service (extends); C7 — External Data Gateway (uses); C3 — Catalogue & Persistence (cache).
- **Acceptance Criteria:**
  - Resolution order is staples table → local cache → external lookup; a staples hit issues no network call, and a cache hit issues no network call — asserted by tests.
  - A cached entry carries its source attribution, licence tag, fetch time and estimated-conversion flag, and the attribution renders in the F15 attribution surface.
  - The cache is excluded from Android auto-backup and is never exported, published or merged into the bundled staples table — asserted by a manifest/inspection check and a test that the bundled dataset is unchanged by any lookup.
  - With the network unavailable, nutrition still resolves from the staples table and cache and the disclosure record reports the lower match proportion; no screen is blocked. [CFC-2]
  - A lookup failure or timeout degrades to unmatched rather than failing the screen, and the unmatched ingredient still contributes nothing to the totals.
  - The lookup request carries only a URL-encoded ingredient term — no key, no credential, no recipe or selection data.
  - No exception constructed during the external lookup, cache read/write or attribution rendering embeds the ingredient term, a response body fragment, or the licence/attribution text in its message — self-certifying against `[CFC-4]` independently of F15's MVP-time sweep, since this feature ships after it. [CFC-4]

### F17: Reference Dataset Coverage Expansion

- **Description:** A scheduled post-release curation pass that raises seasonality, substitution and alias coverage from the v1 slice shipped in F2, driven by the *unknown*-state and unmatched-key occurrences the developer actually encounters in their own catalogue. Closes out `[DEF-01]`'s long tail as bounded, repeatable data work rather than an open-ended prerequisite (R5).
- **Component:** C2 — Reference Data Store (data only).
- **Acceptance Criteria:**
  - Seasonality coverage increases against the agreed v1 baseline, measured as the number of canonical keys in the dataset before and after.
  - Alias entries are added for every canonical-key collision or split observed in the developer's own catalogue during the expansion window.
  - The expansion is a JSON data edit with no schema change and no Room migration — asserted by the absence of a schema version bump in the release.
  - Every existing F2, F3, F8 and F11 test still passes against the expanded datasets, confirming no over-merge (R6) was introduced.
  - An ingredient still absent after the pass continues to return *unknown*, unchanged in behaviour. [CFC-2]

## MVP Definition

**MVP includes:** F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15
**Post-MVP:** F16, F17

The MVP is the first publicly distributable release, not a private developer build. That is what makes it large: Q2 resolved toward distribution, so every G8 Success Criterion is a gate on shipping at all rather than deferrable breadth (Constraints/Timeline), which pulls F13, F14 and F15 inside the line. Everything else in the MVP is one link of the chain the Problem Statement describes — capture (G1), compare (G2, G4, G7), combine (G3), shop (G5) — and the chain delivers nothing useful if any link is missing.

| Feature | In MVP? | Rationale |
|---------|---------|-----------|
| F1 | Yes | G6's durability promise and every other feature's substrate. |
| F2 | Yes | G3's section ordering and G7's calendar are bundled data with no runtime substitute. |
| F3 | Yes | G3's correctness centre; G4 and G7 both key off its canonical keys. |
| F4 | Yes | Without it G1's import path — the app's reason for existing — cannot fetch. |
| F5 | Yes | G1's capture promise. |
| F6 | Yes | G1's correct-, salvage- and delete-a-recipe promises, and the only way a recipe reaches the catalogue. |
| F7 | Yes | G2's browse/compare surface — the cards and detail screen the selection affordances live on, and the indicator vocabulary G2/G8 require; the selection mechanism itself is F10's. |
| F8 | Yes | G7, and the seasonality indicator G2's card requires. |
| F9 | Yes | G4's figures and disclosure rule, entirely offline. |
| F10 | Yes | G2's selection step — the bridge from browsing to a list. |
| F11 | Yes | G3 in full. |
| F12 | Yes | G5; without it the chain stops at a list and the Problem Statement's last step stays manual. |
| F13 | Yes | G8 accessibility criteria are Play-listing gates, not breadth. |
| F14 | Yes | G8's first-run orientation and graceful-network-failure criteria, same gating logic. |
| F15 | Yes | G8's store-policy, privacy-disclosure, support-statement and second-device criteria are literal submission gates. |
| F16 | No | G4 says nutrition is available *where the underlying data supports it*; the bundled staples table supports it, and C6 is designed to degrade to a lower match proportion rather than fail. Deferring keeps the MVP to one outbound path and defers the ODbL-compliance surface (R3) until the core chain is proven. |
| F17 | No | R5 makes dataset coverage a runtime variable, not a prerequisite: an absent key returns *unknown* (G7), so the app is shippable at v1 coverage and improves as data grows. |

| Phase | Features | Goal |
|-------|----------|------|
| MVP | F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15 | The whole chain — a recipe found on the web reaches the catalogue, is compared honestly, collapses into one merged walk-ordered list, and is shopped against Tesco Ireland — in a build that meets every Play listing gate. |
| Phase 2 | F16 | Raises nutrition match coverage by adding the external food-data lookup, with its cache, attribution and licence handling. |
| Phase 3 | F17 | Raises seasonality, substitution and alias coverage from real observed usage. |

## Feature Dependencies

```
The spine — the critical path from foundation to first release:

  F1 -> F4 -> F5 -> F6 -> F7 -> F10 -> F11 -> F12 -> F14 -> F13 -> F15

(F14 sits between F12 and F13 on the spine, per the Implementation Order swap:
F13's sweep covers F14's screen, so F14 must build first.)

Feeding into that spine:

  F1 -> F2 -> F3, which feeds three ways:
    - F3 -> F6      (parsing on save)
    - F3 -> F8 -> F7    (seasonality indicator on the card)
    - F3 -> F9 -> F7    (nutrition figure + caveat on the card)
    - F3 -> F11         (scale + merge)
  F2 -> F11        (section ordering)
  F5 -> F14        (fetch-failure surface)
  F11, F12 -> F14        (the flow being oriented to)
  F5, F6, F7, F10, F11, F12, F14 -> F13        (the screens being conformance-checked, F14 included)

Post-MVP, hanging off already-shipped features:

  F4, F9 -> F16        F2 -> F17
```

The table below is the authoritative, complete edge list; the diagram above is a
reading aid and does not draw every edge as a separate arrow.

| Feature | Depends On | Reason |
|---------|-----------|--------|
| F1 | None | Foundation — the module, build config and database every other feature writes through. |
| F2 | F1 | Datasets ship as assets inside the app module and load through its DI container. |
| F3 | F1, F2 | The canonical-key rule consults C2's alias table for its exceptions (ARCHITECTURE C4 → C2). |
| F4 | F1 | Needs the module and DI container; no other feature's output. |
| F5 | F4 | C1 delegates every HTTP call to C7 — it is the app's sole call site. |
| F6 | F1, F3, F5 | Persists through C3 (F1), parses every ingredient line through C4 (F3), and its import-draft entry point renders F5's draft model. |
| F7 | F6, F8, F9 | Needs saved recipes to browse, and SC-G2 requires both the seasonality indicator (F8) and the nutrition/caveat indicator (F9) on the card. |
| F8 | F2, F3 | Looks up C2's seasonality reference (F2) by the canonical key C4 derives (F3). |
| F9 | F2, F3 | Resolves against C2's staples table (F2) by C4's canonical key (F3). |
| F10 | F7 | The add/remove affordances live on the card and the detail screen, both of which F7 builds. |
| F11 | F10, F3, F2 | Generates from the selection (F10), scales and merges via C4 (F3), and sections via C2's ordering list (F2). |
| F12 | F11 | The walkthrough queue is built from a generated list. |
| F13 | F5, F6, F7, F10, F11, F12, F14 | It is the conformance sweep over every screen those features build; it cannot verify a screen that does not exist. |
| F14 | F5, F11, F12 | Orients the user to a core flow that must exist end to end, and surfaces the failure modes of the network paths that exist in the MVP — F5's recipe fetch and F12's retailer page load — plus the generic gateway-failure surface F16 later reuses. |
| F15 | F13, F14 | Submission gates presuppose an accessible, orientable build; F15 verifies against the finished app. |
| F16 | F9, F4 | Extends C6's resolution chain (F9) with a second C7 call site (F4). |
| F17 | F2, F3, F8, F11 | Pure data expansion of the datasets F2 established, regression-checked against F3, F8 and F11's existing test suites (F17's own AC). |

The graph is acyclic: every dependency is satisfied by a feature earlier in the Implementation Order below — verifiable as a valid topological sort — so no cycle is constructible. F-id numbering does not by itself imply build order: F7 depends on the higher-numbered F8 and F9, both of which build before F7 (order 7 and 8, against F7's order 9).

## Implementation Order

This section is the build-order signal `[DEF-02]` routed here. Three forces set it: the Timeline constraint's preference for a working end-to-end chain early over breadth; the Team constraint (one person, no parallelism to exploit, so the ordering optimises for early risk retirement rather than for concurrency); and the fact that every G8 item is a shipping gate, which pins F13–F15 at the end but *inside* the MVP rather than after it.

| Order | Feature | Rationale |
|-------|---------|-----------|
| 1 | F1: App Skeleton, Build Configuration & Room Foundation | Nothing else can be written or tested without it, and R9's migration discipline is only cheap if it starts at schema version 1. |
| 2 | F2: Reference Data Store & v1 Dataset Curation | Data work with no code dependency beyond the module; doing it second stops it becoming the unbounded blocker R5 warns about, and unblocks both domain engines. |
| 3 | F3: Ingredient Normalisation & Quantity Engine | The project's highest concentration of correctness risk (R2, R6) in the cheapest place to test it — pure JVM, no device. Retiring it early means every downstream feature builds on a parser proven against a hand-curated seed corpus, later trued up against real F5-harvested lines. |
| 4 | F4: External Data Gateway | Small, self-contained, and carries R10's security posture; building it before its first caller keeps the SSRF and size-cap controls from being retrofitted under feature pressure. |
| 5 | F5: Recipe Import Pipeline | First user-visible value, and the earliest point at which real imported ingredient lines can be harvested to grow F3's test corpus and F2's alias table. |
| 6 | F6: Recipe Form — Import Draft, Manual Entry, Post-Save Edit & Delete | Closes G1 and makes the catalogue non-empty, which every subsequent feature needs to demonstrate at all. Consolidating all three entry points here (`[DEF-03]`) is cheaper now than reconciling three divergent forms later. |
| 7 | F8: Seasonality & Substitution Service | Pure domain, no UI, and a prerequisite of F7's card. Sequenced before F7 so the card is built once against real indicator inputs rather than stubs. |
| 8 | F9: Nutrition Service — Staples Path & Disclosure Record | Same reasoning as F8, and the disclosure record's shape must exist before F7 decides how to render a caveat. |
| 9 | F7: Catalogue Browse, Search, Filter & Recipe Detail | Assembles F6, F8 and F9 toward G2 (the browse/compare portion; G2's "choose" criterion completes at F10), and is where the indicator vocabulary [CFC-3] is defined once for every later screen (R7 — deciding it here rather than per-screen is what keeps accessibility from forcing rework). |
| 10 | F10: Shopping-List Selection | Small, and the bridge without which F11 has no input. |
| 11 | F11: Shopping-List Generation | Where F3's engine finally pays off in user-visible output; sequenced after F10 because it consumes the selection. |
| 12 | F12: Retailer Assist Walkthrough | Completes the chain. Its `[DEF-05]` Custom Tabs spike is the last unretired technical unknown, and it is deliberately scheduled where a fallback (return-to-app between items) is still affordable rather than where it would force a redesign. |
| 13 | F14: First-Run Orientation & Network Error Surfaces | Orients a user to a core flow that must be complete before it can be described; the error surfaces cover paths that all exist by now. Built before F13 so F13's sweep can cover it (see F13's Description). |
| 14 | F13: Accessibility Conformance Pass | Must follow the last screen it verifies. This plan's one formal, app-wide accessibility checkpoint (see Description for what that means and why). |
| 15 | F15: Release Readiness & Distribution Gates | Last by necessity — R11 and R12 require the policy and target-API checks to be made against Play's *current* text immediately before submission, so doing them earlier would have to be redone. |
| 16 | F16: External Nutrition Lookup & Licence Compliance | First post-release increment: the honesty machinery is already proven offline, so adding a source raises match coverage without reopening any rule, and the ODbL surface (R3) is entered only once the core is stable. |
| 17 | F17: Reference Dataset Coverage Expansion | Driven by real observed *unknown* and unmatched occurrences, which only exist after the app has been used against a real catalogue. |

## Milestones

<!--
Marking a feature **Done**: (a) flip its milestone checkbox here, `- [ ] F<n>` ->
`- [x] F<n>` — that flip IS "Done."

(b) Mark Done only once the feature's downstream SDD cycle has **shipped**: its
`specs/F<n>-*/` spec/design/tasks are all approved AND every task box in tasks.md
is ticked AND tasks.md's `## Approval` hash matches its content (the
`is_shipped` / `classify_spec` condition). That classifier is NOT CLI-exposed, so
**no single command confirms it** — confirm with two channels:
  - Machine (partial assist): `validate_spec.py specs/F<n>-*/ --phase tasks` —
    PASSED means design.md is approved/unmodified and tasks.md is structurally
    valid. It does NOT assert tasks.md's own approval, its hash coherence, or
    box-completeness. (If the glob matches more than one dir, use the full path
    `specs/F<n>-<slug>/`.)
  - Human read: in `specs/F<n>-*/`, confirm spec.md, design.md, AND tasks.md each
    show `- [x] Approved`, and every task box in tasks.md reads `- [x]` (no command
    asserts box-completeness). Residual: the machine step re-checks only design.md's
    hash coherence; spec.md's and tasks.md's own coherence are re-checked by neither
    channel (tasks.md is terminal). A substantive post-approval edit to any artifact
    is a re-review (re-run the SDD loop), never something to force by re-stamping.

(c) Flipping to `[x]` makes the `### F<n>:` row and its bullets byte-frozen
(Closed-Feature-Row Immutability) — route later substantive changes to a new or
in-flight amending feature, never an in-place edit of the closed row.

(d) A milestone tick requires NO PLAN re-approval — it is hash-neutral.

(e) If a feature was marked Done in error (or its cycle never actually shipped),
un-tick it (`- [x] F<n>` -> `- [ ] F<n>`); that is hash-neutral too and re-opens
the row (a row is closed only while its box reads `[x]`). Un-ticking is for the
marked-in-error case ONLY; a change to a genuinely-shipped Done feature goes through
an amending feature (the (c) path).

Examples use the `F<n>` placeholder (e.g. `- [x] F<n>: <name>`); never write a
checked `- [x] F<digit>` literal here.
-->

No milestone carries a target date: the Timeline constraint states no deadline and no external event, and a solo developer's calendar is not a planning input this document can honestly pin. Timing is expressed as sequence position only.

### Milestone 1: Foundations — build order 1–4

- [ ] F1: App Skeleton, Build Configuration & Room Foundation
- [ ] F2: Reference Data Store & v1 Dataset Curation
- [ ] F3: Ingredient Normalisation & Quantity Engine
- [ ] F4: External Data Gateway
- **Deliverable:** A building, installable shell with a durable, migration-tested database, the bundled datasets loading, a fully unit-tested merge/scale/normalise engine, and a network gateway with its SSRF and size-cap controls proven. Demonstrable as a green JVM test suite rather than a screen — deliberately, since this is where the project's correctness and security risk is cheapest to retire.

### Milestone 2: Capture — build order 5–6

- [ ] F5: Recipe Import Pipeline
- [ ] F6: Recipe Form — Import Draft, Manual Entry, Post-Save Edit & Delete
- **Deliverable:** G1 end to end. A recipe shared from the phone's browser, pasted as a URL, or typed by hand lands in a local catalogue, is correctable at import and at any time afterwards, and is deletable with undo. Demonstrable by sharing a real recipe page into the app.

### Milestone 3: Compare — build order 7–9

- [ ] F8: Seasonality & Substitution Service
- [ ] F9: Nutrition Service — Staples Path & Disclosure Record
- [ ] F7: Catalogue Browse, Search, Filter & Recipe Detail
- **Deliverable:** G2 (browse/compare), G4 (offline path) and G7. The catalogue browses as cards carrying time, energy, seasonality and caveat indicators, searchable by name and filterable by season, with a detail screen behind each. The indicator vocabulary every later screen reuses is fixed here. G2's own "choose" criterion — putting a chosen recipe into the shopping-list selection — depends on F10's selection mechanism and isn't met until Milestone 4.

### Milestone 4: Shop — build order 10–12

- [ ] F10: Shopping-List Selection
- [ ] F11: Shopping-List Generation
- [ ] F12: Retailer Assist Walkthrough
- **Deliverable:** The complete chain from the Problem Statement: chosen recipes collapse into one merged, scaled, walk-ordered list, and that list is walked item by item against Tesco Ireland's own site with every basket action left to the user. G3 and G5 are met, and G2 is now fully met — F10 supplies the selection mechanism G2's own "choose" criterion depends on; the app is functionally whole, though not yet shippable.

### Milestone 5: Distribution Readiness — build order 13–15

- [ ] F14: First-Run Orientation & Network Error Surfaces
- [ ] F13: Accessibility Conformance Pass
- [ ] F15: Release Readiness & Distribution Gates
- **Deliverable:** A build that clears every G8 gate — screen-reader-usable and font-scale-safe, orienting to a first-time user, non-crashing on a second device, honest in its privacy disclosure and support statement, and passing Play's data-safety, content-rating and target-API requirements as they read at submission time. This is the first release.

### Milestone 6: Post-Launch Increments — build order 16–17

- [ ] F16: External Nutrition Lookup & Licence Compliance
- [ ] F17: Reference Dataset Coverage Expansion
- **Deliverable:** Higher nutrition match coverage with correct attribution and licence handling, and seasonality/alias datasets grown from real observed gaps. F15's own `### F15:` row is byte-frozen once ticked Done (Closed-Feature-Row Immutability) and is not literally re-executed; what R12 actually requires — re-checking `compileSdk`/`targetSdk`, the data-safety declaration and the content rating against Play's current policy text — is the developer's own release-time practice for every subsequent submission, the same practice F15 established once, not a re-opened AC on F15 itself.

## Open Questions

> All questions must be resolved before proceeding to feature development.

- [x] Q1: What is the v1 coverage target for the seasonality/substitution dataset that F2 must hit before F7's card can ship — a count of canonical keys, a named list of crops, or "whatever the developer's own catalogue exercises"? SCOPE `[SEAL-03]` deliberately left this unpinned and ARCHITECTURE R5 routed the sizing decision here, but F2 cannot define done without a number or a rule.
  - **Resolution:** Resolved — "whatever the developer's own catalogue exercises." No fixed numeric target or named crop list; F2 curates seasonality/substitution entries for the canonical keys that actually surface while building and testing the app, F7's card renders *unknown* for anything else (G7 already makes this a safe, honest state), and F17 grows coverage post-release from real observed usage. This is the fastest path to a working end-to-end chain (Timeline constraint) and matches the same "grow from real gaps" shape F17 already commits to, rather than curating ahead of demonstrated need.
- [x] Q2: Is deferring the external nutrition lookup (F16) out of the MVP acceptable, given that the first release would then show nutrition only for ingredients present in the bundled ~170-entry staples table and would report a correspondingly lower match proportion on many recipes? The alternative is pulling F16 into the MVP at the cost of entering the ODbL compliance surface (R3) and a second outbound path before the core chain has shipped.
  - **Resolution:** Resolved — defer to post-MVP, as already reflected in the MVP Definition table. The first release shows nutrition only from the bundled staples table; G4 already requires C6 to degrade gracefully to a lower match proportion rather than fail, so this is a supported, honest state rather than a gap. Deferring keeps the MVP to one outbound network path and keeps the ODbL attribution/licence-handling surface (R3) out of the critical path until the core chain has shipped.

## Cross-Feature Contracts

These are the three cross-cutting integration points SCOPE `[DEF-04]` routed to ARCHITECTURE (pass 11), which ARCHITECTURE's own Component boundaries then implicitly carry forward without further re-routing, plus the error-hygiene invariant ARCHITECTURE's Data Flow section states as a whole-system rule. Each binds features that ship in different milestones, so none can be left to a single feature's SDD cycle to discover.

### CFC-1: One ingredient-parsing path for every input route

- **Participating features:** F3, F5, F6, F11
- **Contract:** Every ingredient line — whether confirmed from an import draft, typed by hand during manual entry, or changed in a post-save edit — is parsed into a canonical key and a dimensioned quantity by exactly one C4 entry point, with no route-specific parsing, pre-cleaning or fallback anywhere. This cannot be a single feature's concern because the parser (F3) ships in Milestone 1, the import producer (F5, which supplies raw lines but does not itself call C4) and the two UI callers (F6, which does) in Milestone 2, and the consumer (F11) in Milestone 4 — three milestones apart, so the contract must be committed before any of them authors its spec. It also cannot be expressed as a dependency edge: the requirement is not that one ships first but that all four agree on a single code path.
- **Per-feature AC:** An ingredient line supplied by this feature is parsed by the single shared C4 entry point, and identical line text produces an identical canonical key and quantity regardless of which input route supplied it. F5 does not itself parse (see F5's Component note); its tagged bullet instead covers a related obligation — feeding real harvested lines into F3's shared corpus.
- **Enforcement:** The shared parse-fidelity test corpus owned by F3 — the same fixture lines are driven through the import-confirm route, the manual-entry route and the post-save-edit route, and the resulting canonical keys and quantities must be byte-identical.

### CFC-2: Absence is explicit and never collapses to a default

- **Participating features:** F3, F5, F6, F7, F8, F9, F10, F11, F16, F17
- **Contract:** Every absence the system can encounter — *unquantified* quantity, *unmatched* ingredient, *unknown* seasonality, *unknown* supermarket section, *unknown* cooking time, *no image* — is represented as its own explicit value and is never collapsed into a zero, a default, an empty string, a placeholder or a silent omission by any producer or any renderer. This is ARCHITECTURE's first Data Flow invariant and is what makes G3's, G4's and G7's honesty criteria enforceable rather than aspirational. It spans Milestones 1 through 6 and has no single owner: a producer can represent absence correctly and a renderer can still flatten it, so both sides must carry the obligation.
- **Per-feature AC:** Every absent value this feature produces or renders is represented and displayed as an explicit absence state, distinguishable from a zero, a default and an empty value, and is never substituted with a guessed or placeholder value.
- **Enforcement:** Per-feature unit and Compose tests asserting the explicit-absence value on both the producing and the rendering side; no owning feature — the invariant is co-enforced by each participating feature's own tests and re-checked during that feature's own SDD-phase code review, not by this blueprint's own document-review panel (which reviews this plan, not application code).

### CFC-3: One indicator vocabulary, never colour alone

- **Participating features:** F7, F10, F11, F12, F13, F14, F15
- **Contract:** All status indicators in the app draw from one vocabulary defined once — the seasonality family and the nutrition-caveat family must be visually distinct from each other and from the no-caveat state, and every indicator anywhere (including unquantified/unknown-section list entries and the walkthrough's progress) is distinguishable by icon, shape or text and never by colour alone. Defining it once is R7's stated mitigation against late accessibility rework. It spans Milestones 3 through 5 and cannot be a dependency edge: F13 verifies the vocabulary but does not author the indicators, and each screen-owning feature renders its own.
- **Per-feature AC:** Every status indicator this feature renders is drawn from the shared indicator vocabulary and is distinguishable by icon, shape or text without reliance on colour.
- **Enforcement:** The accessibility conformance inventory and Compose test sweep owned by F13, which enumerates every indicator in the app and asserts a non-colour distinguishing attribute on each.

### CFC-4: No user or imported content in any error message

- **Participating features:** F1, F3, F4, F5, F6, F7, F8, F9, F11, F12, F14, F15, F16
- **Contract:** No exception constructed anywhere in Pantry's own code embeds raw user-supplied or imported content — a recipe URL, an ingredient line, a recipe title, a fragment of fetched HTML, a retailer search term — in its own message, whether it is caught and converted to a typed error or left to propagate as an unhandled crash; a third-party library exception that might carry such content is re-wrapped before it can reach a caller unchanged. This is what makes ARCHITECTURE's claim that Android vitals never carries recipe or list content true rather than assumed. It spans Milestones 1 through 6 and is enforceable only across every component that touches such content, not by any one of them.
- **Per-feature AC:** No exception or error message this feature constructs contains raw user-supplied or imported content, and any third-party exception carrying such content is re-wrapped into a typed error before propagating.
- **Enforcement:** Per-feature tests asserting error messages against content-bearing fixtures (a recipe URL, an ingredient line, fetched HTML) at every participating feature; F4 defines the typed-error surface and its re-wrapping rule that the rest re-use. F15's pre-submission verification (Milestone 5) re-checks the invariant end to end rather than through separate CI tooling, which a solo developer's existing build setup does not otherwise establish.

## Panel Review

<!-- Terminal Phase: must NOT contain a ### Deferred dispositions sub-section. archive_pass.py rejects --terminal archives with Deferred rows; validate_blueprint.py hard-fails for PLAN.md specifically. -->
<!-- Populated by the skill across panel-review passes. archive_pass.py manages
     Trajectory and Sealed dispositions automatically; the synthesizer populates
     Latest pass detail per pass.

     This is the last blueprint phase; concerns cannot be deferred forward.
     Disposition vocabulary: Addressed / Sealed / Accepted as risk /
     User input needed / Halt and re-scope. Sealed and Accepted as risk must
     include "Defense: <reason>" in Notes. Severity tags in Latest pass detail
     are bracketed: [HIGH] / [MED] / [LOW], optionally [REGRESSION].

     See SKILL.md "Panel Review section format" for the normative spec. -->

### Trajectory

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes                           |
|------|------------|-------|-------------|-----------|----------|--------|---------------------------------|
| … | … | — | — | — | — | — | 8 earlier passes elided |
| 9    | 2026-09-18 | 1     | 0           | 1         | 0        | 6      | tags=d0u0c0                     |
| 10   | 2026-09-18 | 0     | 0           | 1         | 0        | 4      | converged (0 HIGH); tags=d0u0c0 |
| 11   | 2026-09-21 | 1     | 0           | 1         | 0        | 0      | tags=d0u0c0                     |
| 12   | 2026-09-21 | 2     | 0           | 2         | 0        | 1      | tags=d0u0c0                     |
| 13   | 2026-09-21 | 1     | 0           | 1         | 0        | 0      | tags=d0u0c0                     |
| 14   | 2026-09-21 | 1     | 0           | 1         | 0        | 0      | tags=d0u0c0                     |
| 15   | 2026-09-21 | 3     | 0           | 3         | 0        | 0      | tags=d0u0c0                     |
| 16   | 2026-09-21 | 3     | 0           | 3         | 0        | 0      | tags=d0u0c0                     |
| 17   | 2026-09-21 | 1     | 0           | 1         | 0        | 0      | tags=d0u0c0                     |
| 18   | 2026-09-21 | 1     | 0           | 1         | 0        | 0      | tags=d0u0c0                     |
| 19   | 2026-09-21 | 1     | 0           | 1         | 0        | 0      | tags=d0u0c0                     |
| 20   | 2026-09-21 | 1     | 0           | 1         | 0        | 0      | tags=d0u0c0                     |
| 21   | 2026-09-21 | 1     | 0           | 1         | 0        | 0      | tags=d0u0c0                     |
| 22   | 2026-09-22 | 3     | 0           | 8         | 0        | 5      | tags=d0u0c3                     |
| 23   | 2026-09-22 | 0     | 0           | 0         | 0        | 5      | converged (0 HIGH); tags=d0u0c0; upstream-panel a0f4eb61 |

### Sealed dispositions

- `[SEAL-01]` **Closed-Feature-Row Immutability means an early F2/F3…** (pass 1, accepted-as-risk) — Defense: this is how milestone-tick/closed-row immutability is designed to work everywhere in this workflow (`workflow-overview.md`), not a defect specific to this plan; user confirmed accepting as-is rather than restructuring around it.
- `[SEAL-02]` **F6 asserts "one Compose recipe form" as a specific…** (pass 1, user-directed) — Defense: user was asked and explicitly confirmed one literal shared composable (not a looser "shared code path") is the intended design — the existing wording stands unchanged.
- `[SEAL-03]` **PLAN's "Q1" label reuses a label already used independently…** (pass 1, accepted-as-risk) — Defense: each blueprint document's Open Questions section is independently numbered by established convention across all three phases — not a real collision; user confirmed accepting as-is.
- `[SEAL-04]` **Dropping CFC-4's CI/lint clause removes the plan's only…** (pass 2, accepted-as-risk) — Defense: no CI pipeline exists anywhere else in this plan; per-feature tests plus F15/F16's self-certifying AC bullets match how every other cross-cutting invariant in this plan is enforced. User was asked and confirmed keeping per-feature tests only over standing up new CI tooling.
- `[SEAL-05]` **F17 (data-only) isn't tagged as a CFC-4 participant, unlike…** (pass 2, accepted-as-risk) — Defense: F17 only edits bundled JSON data files — no exception-construction surface exists for CFC-4 to govern, so the absent tag is accurate, not a gap. User was asked and confirmed accepting as-is.
- `[SEAL-06]` **F16's AC4 (network unavailable) and AC5 (lookup…** (pass 3, accepted-as-risk) — Defense: the two conditions (no network attempted vs. a network attempt that failed) are genuinely distinct and worth separate test coverage; user was asked and confirmed keeping them separate.
- `[SEAL-07]` **Each CFC's Enforcement field partially restates its own…** (pass 3, accepted-as-risk) — Defense: intentional redundancy for a reader who reads only the CFC block; user confirmed accepting as-is.
- `[SEAL-08]` **F1's migration-harness AC spends a full sentence explaining…** (pass 3, accepted-as-risk) — Defense: added deliberately in pass 1 to prevent exactly this confusion; user confirmed the extra length is worth it.
- `[SEAL-09]` **F17's AC5 regression check restates an invariant F8's own…** (pass 3, accepted-as-risk) — Defense: a regression safety-net specific to F17's data-expansion change, not true duplication; user confirmed accepting as-is.
- `[SEAL-10]` **Milestone 3 lists F8, F9, F7 instead of ascending F-id…** (pass 5, accepted-as-risk) — Defense: correct per build order, and the document's own "F-id numbering ≠ build order" footnote already covers this; user confirmed accepting as-is.
- `[SEAL-11]` **F17's rationale reuses "unmatched" to mean…** (pass 5, accepted-as-risk) — Defense: unambiguous in local context, and F17's dependency edges (F2, F3, F8, F11 — no F9/F16) already disambiguate the domain; user confirmed accepting as-is.
- `[SEAL-12]` **F13's font-scale AC names "import" as if it were a screen…** (pass 5, accepted-as-risk) — Defense: under-explained rather than contradictory per critic's own assessment, and F13's dependency row already correctly omits F5; user confirmed accepting as-is over spending a 6th pass past the cap.
- `[SEAL-13]` **The same AC bullet doesn't separately itemize F6's "edit"…** (pass 5, accepted-as-risk) — Defense: itemization asymmetry with no effect on what F13 actually verifies; user confirmed accepting as-is.
- `[SEAL-14]` **The Milestones section's "Marking a feature Done" tracking…** (pass 5, accepted-as-risk) — Defense: this is shared `telescoping-sdd` skill template scaffolding applied uniformly to every blueprint PLAN.md, not a choice this plan's author made — out of scope for this artifact to change; user confirmed accepting as-is.
- `[SEAL-15]` **The pass-4 trim of the F13/F14 swap rationale to one…** (pass 5, accepted-as-risk) — Defense: a minor navigation cost, the direct tradeoff of the consolidation this same panel asked for in pass 4; user confirmed accepting as-is.
- `[SEAL-16]` **F2's AC2 bundles three assertions into one run-on bullet** (pass 5, accepted-as-risk) — Defense: nothing in it is incorrect, purely a scannability nicety; user confirmed accepting as-is.
- `[SEAL-17]` **F6's discard and finalization bullets describe two branches…** (pass 7, user-directed) — Defense: kept as two bullets deliberately — each is independently testable and the split makes the two outcomes (discard vs. finalize) easier to scan than one long conditional bullet.
- `[SEAL-18]` **F6's discard AC bundles two test scenarios (kill+relaunch,…** (pass 7, user-directed) — Defense: PLAN.md is the terminal blueprint phase — nothing here defers forward. Both scenarios are kept as named tests because they exercise genuinely different Android lifecycle paths (process death vs. mere backgrounding) that a reader shouldn't have to infer are equivalent from prose alone; the SDD Design phase for F6 can still choose its own concrete test implementation.
- `[SEAL-19]` **The Undo-restore bullet and the involuntary-discard-restore…** (pass 7, user-directed) — Defense: harmless, intentional redundancy — each bullet is a self-contained AC for a different trigger (voluntary Undo vs. involuntary discard); a reader of either alone gets the complete claim.
- `[SEAL-20]` **F6's discard AC bundles two independently-testable…** (pass 7, user-directed) — Defense: consistent with this document's own established style precedent for combining closely-related test scenarios in one AC bullet.
- `[SEAL-21]` **The discard bullet's trigger order ("backgrounded, or the…** (pass 7, user-directed) — Defense: cosmetic ordering with no reader-confusion risk; both triggers are named and handled identically either way.
- `[SEAL-22]` **F6's new negative-case sentence (in-app navigation ≠…** (pass 8, user-directed) — Defense: cross-checked verbatim against C9's Boundary text; the two documents agree.
- `[SEAL-23]` **F6's two named discard tests (kill+relaunch;…** (pass 8, user-directed) — Defense: matches SCOPE G1's exact wording and ARCHITECTURE's sweep-on-foreground-entry mechanism.
- `[SEAL-24]` **ARCHITECTURE's Data Flow diagram discard branch says only…** (pass 8, user-directed) — Defense: the diagram is already Sealed as illustrative, not authoritative (prior pass); a terser echo isn't a contradiction.
- `[SEAL-25]` **F10 (Shopping-List Selection) has no direct dependency edge…** (pass 8, user-directed) — Defense: F10 depends transitively on F6 via F7; the flow-filter mechanism itself is owned by F1, not F6, so no edge is missing.
- `[SEAL-26]` **F6's Description says F7 "supersedes it... and edit/delete…** (pass 9, accepted-as-risk) — Defense: this is narrative continuity prose in F6's Description, not an AC bullet under test; F6's own testable ACs (lines 78-89) don't depend on which screen later hosts the affordance, so no contract is actually unverified.
- `[SEAL-27]` **F6's AC section has no bullet directly asserting the…** (pass 9, accepted-as-risk) — Defense: the surface's existence is an implementation detail in service of the edit/delete ACs, which are themselves fully tested (lines 78, 85-89); adding a bullet just to assert a screen renders would test scaffolding, not behaviour, inconsistent with this plan's established AC style.
- `[SEAL-28]` **The Description's parenthetical "(e.g. a plain…** (pass 9, accepted-as-risk) — Defense: explicitly non-binding via "e.g."; deliberately left to the SDD Design phase to concretize.
- `[SEAL-29]` **F6's Description uses three near-synonymous phrasings for…** (pass 9, accepted-as-risk) — Defense: minor redundancy, no ambiguity risk — all three phrasings clearly denote the same non-F7 surface.
- `[SEAL-30]` **The parenthetical example "reached from the catalogue" sits…** (pass 9, accepted-as-risk) — Defense: the catalogue list itself is F1/F3's Room-backed data, displayed via some minimal UI Milestone 2 already requires for the app to be usable at all; the parenthetical describes reachability, not a claim that F7's catalogue *screen* exists early.
- `[SEAL-31]` **"Sufficient to host the edit and delete entry points" pairs…** (pass 9, accepted-as-risk) — Defense: "e.g." already signals the example is illustrative, not binding; simplifier itself flagged no action needed.
- `[SEAL-32]` **F10's Description, AC, dependency table and Milestone-3…** (pass 10, user-directed) — Defense: correct as written — F10 sits at/after F7's build position (build order 9), so no staging ambiguity exists there the way it did for F6; the explicit F7 anchor F6 needed doesn't generalize to features that ship after F7.
- `[SEAL-33]` **F6's Description uses three near-synonymous phrasings for…** (pass 10, user-directed) — Defense: re-raise of `[SEAL-29]` with no new evidence — disposition unchanged.
- `[SEAL-34]` **SCOPE's SC-G1 ("opened for editing...from its own detail…** (pass 10, user-directed) — Defense: this is exactly what the Milestones section's own preamble states (each milestone is a build-order checkpoint, not a shippable increment) — a plan-wide convention already established before F6 existed, not a gap specific to this feature.
- `[SEAL-35]` **Panel Review's `### Latest pass detail` table is empty…** (pass 10, user-directed) — Defense: expected pipeline state — `archive_pass.py` clears this table after each archive so the next pass starts clean; not a defect.
- `[SEAL-36]` **Considered a third candidate from the same audit, F10 (C5's…** (pass 12, user-directed) — Defense: verified against ARCHITECTURE's C5 Boundary text; the ingredient-text-touching responsibilities (canonical-key grouping, merging) belong entirely to C5's generation half, already covered by F11 — F10 itself has no exception-construction surface that touches raw content, structurally the same situation as F17's existing CFC-4 exemption (`[SEAL-05]`).
- `[SEAL-37]` **CFC-1's narrative folds F11 into "one ingredient-parsing…** (pass 22, accepted-as-risk) — Defense: synthesizer-judged (free-ride branch, this pass already non-terminal) — F11's Per-feature AC already correctly describes merge-equivalence rather than parsing, and the narrative sentence is scene-setting prose, not a per-feature commitment; not worth a further pass to retitle the contract.
- `[SEAL-38]` **Milestone 6 deliverable's F15/R12 explanation reads as…** (pass 22, accepted-as-risk) — Defense: synthesizer-judged — the sentence explains why F15's row stays byte-frozen despite R12's recurring nature, which is exactly the kind of load-bearing clarification a future reader of a closed row needs; not an instruction telling the reader what to conclude or skip, so it doesn't cross into the uncapped case.
- `[SEAL-39]` **F1 bundles the full thumbnail…** (pass 22, accepted-as-risk) — Defense: synthesizer-judged — moving these AC bullets from F1 to F6 mid-pass would restructure both features' component ownership (ARCHITECTURE assigns thumbnail storage to a component F1 already builds) with cascading risk of introducing new inconsistencies; the cost (synthetic fixtures in F1's own JVM tests) is small and consistent with F1's stated Milestone-1 purpose of retiring correctness risk early. Not deferrable — PLAN.md is the terminal artifact.
- `[SEAL-40]` **CFC-2's Per-feature AC clause is repeated near-verbatim…** (pass 22, accepted-as-risk) — Defense: consistent with `[SEAL-07]`'s already-accepted reasoning for the same redundancy shape in CFC Enforcement fields — intentional redundancy for a reader who reads only one part; not worth a further pass to de-duplicate.
- `[SEAL-41]` **F13's Description spends a paragraph distinguishing its…** (pass 22, accepted-as-risk) — Defense: synthesizer-judged — correct and load-bearing content (it's what prevents a reader from double-counting F13's sweep against those features' own self-certifications); a wording trim isn't worth a pass.
- `[SEAL-42]` **F10's new CFC-3 AC and F13 AC3's "five screen-owning…** (pass 23, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass) — F10's Description already establishes the context ("reachable from both the card and the recipe detail screen"), so "this screen" resolves to F7's card/detail screen where F10's selection state renders as an affordance/overlay rather than a separate screen of its own; not worth a pass to spell out an antecedent a reader can already infer.
- `[SEAL-43]` **CFC-3's contract prose illustrates "every indicator…** (pass 23, accepted-as-risk) — Defense: synthesizer-judged (exit-capable pass) — the contract's Participating features line is the authoritative membership list and already includes F10; the prose examples are illustrative, not exhaustive, and F7/F13/F14/F15 aren't separately illustrated there either.
- `[SEAL-44]` **Milestone 3/4 Deliverable and Implementation Order row 9…** (pass 23, accepted-as-risk) — Defense: consistent with this document's established redundancy precedent (`[SEAL-07]`, `[SEAL-40]`) — each restatement serves a different reader path (milestone summary vs. sequencing rationale); not worth a pass to consolidate.
- `[SEAL-45]` **F13 AC3's participant-counting parenthetical duplicates the…** (pass 23, accepted-as-risk) — Defense: the parenthetical exists to disambiguate renderer-vs-verifier-vs-self-certifier roles within the count, not merely to restate it; not worth a pass to trim.
- `[SEAL-46]` **F10 now carries the highest cross-feature-contract-tag…** (pass 23, accepted-as-risk) — Defense: proportionate to what F10 actually renders (two CFC obligations, both genuinely applicable); not scope creep.

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to feature development
- **Content Hash:** `7a79be7f2f4db4c7`
- **Hash basis:** v2
- **CFC Content Hashes:**
  - CFC-1: `1055e5fde8e9a5cc995dd08c9debbb36326b6ac8df1ad5cc5eba00be72148f16`
  - CFC-2: `ee4a5c0bdd23c6aef890160fd0ce63031b32cf1dc841b218ed70eb4c64c76b47`
  - CFC-3: `72e1cc29f5ea159e5c1ac405fb4ad71c4fe6a37786ce53f13ed459d60a3958b6`
  - CFC-4: `0e772a570c8a270ab88dcb718f4aa7864863c740e354154f0b61a3fe3f3163f0`