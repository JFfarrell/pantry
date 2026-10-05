# Design: Recipe Import Pipeline

**Spec:** `specs/F5-recipe-import-pipeline/01_spec.md`

## Goals and Non-Goals

**Goals:**
- Turn a pasted or shared `http(s)` link into an `ImportDraft` or one typed, content-free `ImportException`, with Cancel and a link-carrying share as the only other endings (R1, R2, R4, R5).
- Fetch only through `AppContainer.gateway`, and defend everything after F4's raw-byte cap: media type, encoding, a streaming 10 MiB decompressed cap, a 64-deep JSON-LD bound, a 20-block limit and a bounds-first image decode (R2, R3, R7).
- Model every optional draft field as a stated value or an explicit absence (R6; CFC-2).
- Build the import screen as `MainActivity`'s content: input, progress with Cancel, per-category errors with Retry, the decline surface with Share link, and the notice-only draft-produced state (R1, R2, R5, R8, R11; spec Q1, Q6, Q12).
- Re-wrap every third-party exception by F4's five-point rule, so no URL, HTML or ingredient text reaches an error, a message or a log (R9; CFC-4).
- Commit page snapshots and at least 20 `F5_HARVESTED` corpus rows that F5's own extraction reproduces byte for byte (R10; CFC-1).

**Non-Goals:**
- Parsing ingredient lines (`IngredientEngine`), persisting a recipe, or writing a thumbnail file. F6 confirms, parses and persists (spec Never Do; PLAN F5 Component).
- Rendering any draft field, editing a draft, or a "Skip picture" action during the image phase (spec Q1; AD18).
- A navigation library, Hilt, or a fourth new dependency (spec Ask First).
- `ACTION_VIEW` deep links (`[SEAL-01]`), F14's generic fetch-failure surface (spec Q6), or F14's onboarding beyond one line of field supporting text (`[SEAL-09]`).
- Stripping tracking or token query parameters from `sourceUrl` (`[SEAL-17]`; DR11).
- Any change to `ie.pantry.data.gateway` main sources, including `finalUrl` (spec Q10).

## Architecture Decisions

A bare `Q<n>` here is the spec's Open Question. This design's own questions are "design Q1" onward (see Open Questions). `FC<n>` names a component in this design. `C1`, `C7` and `C9` are ARCHITECTURE components.

| Decision | Choice | Alternatives Rejected | Rationale | Consequences |
|----------|--------|-----------------------|-----------|--------------|
| AD1 — Import error shape (Decision Point 1; `[SEAL-04]`, `[SEAL-31]`) | **One `Throwable` class with a kind enum, mirroring F4 AD1.** `class ImportException internal constructor(kind: Kind, gatewayCategory: GatewayException.Category?, statusCode: Int?, causeClass: KClass<out Throwable>?) : RuntimeException(msg, null, false, true)`. No `String` parameter. The message is built inside the class from enum names and an integer. `init` checks `(gatewayCategory != null) == (kind == FETCH_FAILED)` and `(statusCode != null) == (gatewayCategory == HTTP_STATUS)`, with constant messages. It is returned in `ImportResult.Failed`, never thrown by the importer | A sealed non-`Throwable` result: `assertNoSentinel()` would not apply, and R9's stack-frame clause would lapse. Keeping the `GatewayException` as `cause`: breaks rule (c), no chained cause | `assertNoSentinel()` applies unchanged. A fetch failure copies the gateway's category and status code, which the UI needs for Q6's 404, 401/403 and 5xx wording | `kind` covers fetch failure, not-HTML, unsupported encoding, unreadable content, content too large and not recognised (DM6). An invalid URL never becomes an `ImportException`: the importer accepts only a `ValidatedUrl` (AD11), so the invalid-URL path is a UI validation state with a content-free `UrlCheck.Invalid(reason)` |
| AD2 — JSON-LD depth bound (Decision Point 2; R3; `[SEAL-28]`) | **A string-aware bracket-depth pre-scan, then `Json.parseToJsonElement`, then a depth-bounded walk.** `JsonDepth.withinBound(text, 64)` counts `{` and `[` outside strings, honouring `\` escapes, with the outermost value at depth 1, and stops at the first character that would reach 65. **Depth is defined once:** the outermost value is depth 1; a node at depth 64 is visited and its children (depth 65) are not, in both the pre-scan and the walk. **Size:** a block whose text exceeds 512 KiB (`JSON_LD_BLOCK_CAP_CHARS = 512 * 1024`) is skipped before the pre-scan, like an over-deep block and still counted toward the 20 (design Q5; user-confirmed), because a flat `[0,0,…]` has depth 1 yet builds a `JsonElement` tree 10 to 30 times its text size. Only a block that passes both is parsed, and its tree is dropped as soon as its `Recipe`s are mapped (only `ExtractedRecipe`s and image strings are held, including across the picture fetch). Depth counts containers only (`{` and `[`): a scalar property of a depth-64 object is readable; `@graph` counts as an object plus an array; the walk bound is a guard that the pre-scan makes unreachable. The extractor's walk carries a depth argument and does not descend past 64. The first 20 `<script type="application/ld+json">` elements in document order are examined, whether they turn out valid, invalid or too deep. The 21st and later are never read | A custom bounded JSON reader: more code than the problem needs, and a second parser to keep correct. Relying on kotlinx's deep-recursion fallback: it avoids `StackOverflowError` but does not enforce the bound | The pre-scan is linear and allocation-free. A block that fails the size check, the scan or the parse is dropped, so no `SerializationException` text ever leaves the extractor | The type match is `type` trimmed and compared case-insensitively to `application/ld+json`. Counting skipped blocks toward the 20 makes the limit a pure function of document order |
| AD3 — Tier selection (Decision Point 3; R4; `[SEAL-27]`, `[SEAL-34]`) | **The first tier that yields a qualifying recipe wins outright.** A structured recipe qualifies with at least one ingredient line or method step. A heuristic recipe qualifies with an ingredient list of at least 2 items. Order: JSON-LD, then microdata/RDFa, then heuristic. The one cross-tier field is the title: the earliest structured `Recipe` that stated a title supplies it whichever lower tier supplies the draft, and a heuristic title is used only when no structured tier stated one | Per-field fill-in from lower tiers: R4 rules it out, and mixed provenance could not be reported as one tier | Matches R4 and Q2 as written | Within the JSON-LD tier, the first qualifying `Recipe` node in walk order is used. A title-only `Recipe` contributes its title and falls through |
| AD4 — Share plumbing (Decision Point 4; `[SEAL-13]`, `[SEAL-48]`; `[DEF-12]`) | **`android:launchMode="singleTask"` and `android:taskAffinity=""` on `MainActivity`** (design Q7; user-confirmed). An empty affinity stops another app declaring Pantry's affinity to place its activity in Pantry's task (the task-hijack class that `singleTask` on an exported launcher would otherwise invite); share delivery through `onNewIntent` is unaffected. `onCreate` reads the intent only when `savedInstanceState == null`. `onNewIntent` calls `setIntent(intent)` and reads it. Reading: only `Intent.ACTION_SEND` is handled, and `MainActivity` calls `onShareReceived` **only when `intent.action == ACTION_SEND`** (a launcher `ACTION_MAIN` start or a re-delivery of any other intent calls nothing, so no "no link" notice appears on an ordinary launch); a start with `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` skips the read, so a Recents relaunch never replays an old share; text comes from `getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()`, and a `RuntimeException` from unparcelling the extras counts as missing text. The activity passes the text straight to `ImportViewModel.onShareReceived(text: String?)`. The ViewModel outlives rotation, so the share is applied once | `singleTop`: a share from another app starts a new instance in the sender's task, so `onNewIntent` never fires for a cross-task share and two instances could run imports at once. `singleInstance`: forbids any other activity in the task, which F6 and F7 will need. `singleInstancePerTask`: API 31+, below `minSdk` 26. A `SharedFlow` of intents: an extra hop with no benefit, since the activity already holds the ViewModel | With one task and one instance, a share while an import runs reaches the running ViewModel (R1), and "Try another link" and "Import another" stay in the same instance (R5). `getStringExtra` returns null for a `Spanned` extra, which some senders use, so it is not used | The manifest gets `launchMode`, `taskAffinity` and the `SEND` filter (`ShareTargetManifestTest`). A real cross-task share cannot be driven in Robolectric, so the device check in Implementation Sequence step 9 covers it (DR2). Back from Pantry after a share returns to the sending app's task |
| AD5 — Fixture production and image decode reuse (Decision Point 5; `[DEF-10]`) | **The gzip bomb and the deep JSON-LD blocks are generated in test code. Every other page fixture is a committed file.** The image validator is F5's own class, `ImageValidator`, and reuses F1's `internal fun computeSampleSize(width, height, maxEdgePx)` from `ie.pantry.data.thumbnail` with `maxEdgePx = 512` | Committing a bomb: an opaque binary that hides its own parameters. Calling `ThumbnailProcessor.process`: it also rotates and re-encodes to JPEG, which is C3's work. The draft must carry the fetched bytes, not a re-encoding | ARCHITECTURE C1 requires the validation decode to be bounds-aware "in exactly the same way" as C3's. Sharing the sample-size function makes that literally true. `computeSampleSize` is a pure function, so calling it is not a persistence call (spec Never Do) | Fixture sizes under `TestGateways.FAST`'s 64 KiB cap are fixed in Testing Strategy |
| AD6 — Charset (Decision Point 7; `[SEAL-08]`) | **Hand the charset decision to jsoup, with the header charset passed in only when it is valid.** `BoundedHtmlReader` reads the `charset` parameter of `mediaType`. If `Charset.isSupported(name)` returns true (an `IllegalCharsetNameException` counts as false), it is passed as `charsetName`. Otherwise `null` is passed. jsoup 1.18.3 then applies a BOM first (it overrides any header), then `<meta charset>` or `http-equiv` within the first 5 KiB, then UTF-8 | Decoding to a `String` first: duplicates jsoup's BOM and meta handling, and holds a second full copy of the text. Passing an unvalidated header charset: jsoup calls `Charset.forName`, which throws on an unknown name | Verified against jsoup 1.18.3 `DataUtil.detectCharset`. An unknown header charset degrades to detection, never to an error | A `<meta charset>` after the first 5 KiB is missed and the page is read as UTF-8 (DR6). Fixtures: Windows-1252 by `<meta>`, Windows-1252 by header only, and an unknown header charset (Testing Strategy) |
| AD7 — Decode order and the streaming cap (R2, R3) | **Order: media type, then encoding, then a streaming decode through `CappedInputStream`, then jsoup.** The media type's essence (before `;`, trimmed, lower-cased) must be `text/html` or `application/xhtml+xml`. A null `mediaType` is not HTML. `BodyEncoding.OTHER` is unsupported. `IDENTITY` reads the bytes, and `GZIP` reads a `GZIPInputStream` over them. Either way the stream is wrapped in `CappedInputStream(cap = 10 MiB, maxTagBytes)`, which asks its delegate for at most `cap + 1 − produced` bytes per read and throws the internal marker `ContentTooLargeException` once `produced > cap`, or, **for the page only** (`maxTagBytes = 200_000`; the image path passes `Long.MAX_VALUE`, so a low-entropy valid image is never refused for its bytes), once the count of `<` bytes it has produced exceeds `maxTagBytes` (Q1 markup-density bound). It overrides `markSupported()` to `false` and `skip` to read through the counted path, so jsoup's charset sniff cannot double-count `produced` and `skip` cannot bypass the cap. jsoup parses from that stream with `Parser.htmlParser()`. Each read also calls `ensureActive()` on the import's job | Inflating to a buffer and then checking its size: that is the bomb the spec forbids (R3). A size check before parsing only: gzip hides the size | The decompressing stream never yields more than `cap + 1` bytes, so R3's "cap plus one read buffer" holds with room to spare. The cancellation check means a Cancel during a long parse stops it at the next read | The same stream wrapper is used for the image body (R7). The cost of the DOM that a 10 MiB page builds is design Q1 |
| AD8 — Draft shape (`[DEF-01]`; `[SEAL-10]`; R6) | **`ImportDraft` holds non-null fields only.** `title: DraftValue<String>`, `ingredientLines: List<String>` (an empty list is the stated absence of ingredients, per R6), `method: DraftValue<List<String>>` (ordered steps, never an empty `Stated` list), `cookingTime: DraftValue<Int>` (whole minutes, at least 1), `servings: DraftValue<Int>` (any integer ≥ 1 that fits `Int`, per AD10), `image: DraftImage`, `tier: ExtractionTier`, `sourceUrl: String` and `fetchedAt: Instant`. `DraftValue` is `Stated(value)` or the `data object Absent`, in the style of F3's `CanonicalKey.Absent` | Nullable fields: R6 forbids `null` as absence. Method as one text block: F6 would have to split it again, and HowTo data is already step-shaped | Every absence is a value the compiler forces callers to handle (CFC-2) | `toString()` is overridden on every type that can hold a URL, shared text, title, line or bytes — `ImportDraft`, `DraftImage.Validated`, `ValidatedUrl`, `PickedUrl`, `UrlCheck.Valid`, `Screen.Declined`, `Screen.Drafted`, `ImportUiState` (its `field`), and the internal `ExtractedRecipe`, `Extraction`, `JsonLdScan` and `Candidate.Url`, with `DraftValue.Stated` printing no value — to print counts, the tier, sizes and enum names only, so any of them logged by mistake carries no content |
| AD9 — Text normalisation and instruction forms (Q8; `[SEAL-51]`; `[DEF-01]`) | **One function, `LineText.normalise(raw)`, applied to text that is already entity-decoded and tag-free:** U+00A0 becomes a space, runs of ASCII whitespace (`[ \t\n\x0B\f\r]`) collapse to one space, and the ends are trimmed. Nothing else changes: list markers and other Unicode spaces such as U+2009 and U+202F are kept. Element text comes from `LineText.of(element)`, an iterative `NodeTraversor` that appends each `TextNode`'s whole text and a space at each `<br>` and block boundary. A JSON-LD string goes through `Jsoup.parseBodyFragment(s)` first, which decodes entities and drops tags. **`recipeInstructions` forms:** a string (split on line breaks, then one step per non-empty piece); an array of strings; `HowToStep` or `HowToDirection` (`text`, else `name`); `HowToSection` or `ItemList` (their `itemListElement`, flattened in order, with the section's `name` dropped, user-confirmed in design Q2). `HowToTip` is skipped. **Output caps (design Q6; user-confirmed):** at most 200 ingredient lines and 200 steps are kept per draft (later ones are not read), and a single line or step longer than 4 096 characters is dropped, so a hostile block cannot force millions of `parseBodyFragment` calls. The length test is applied to the raw string before parsing; `parseBodyFragment` runs only on a string that contains `&` or `<` (otherwise the string is already plain text); and at most the first 1 000 elements of any array property are examined (derived from Q6's draft caps, so blank or oversize items cannot be used to dodge them) | Folding every Unicode space: Q8 lists only U+00A0, and the rest is C4's business (CFC-1) | Q8 is the whole list. Text extraction stays separate from parsing | The same normalisation applies to titles and method steps |
| AD10 — Duration and yield (Q9; `[SEAL-07]`, `[SEAL-06]`) | **Duration:** `^PT(?:(\d{1,4})H)?(?:(\d{1,5})M)?(?:(\d{1,6}(?:[.,]\d{1,9})?)S)?$`, case-insensitive, with at least one component. Minutes are total seconds divided by 60, **rounded half up**, so `PT30S` gives 1 minute and `PT29S` gives absent. A result of 0 is absent. Any `Y`, `M`-before-`T`, `W` or `D` designator, a sign, or more digits than the pattern allows gives absent. `totalTime` is used if it gives a stated value, else `cookTime`. **Yield:** the value, or the first element of an array. A JSON integer, or a string matching a bare integer, `serves[:]? N`, `N servings`, `N portions`, `servings[:]? N` or `portions[:]? N` (case-insensitive, trimmed), gives `N` when N ≥ 1 and N fits in `Int`. Anything else, including 0, is absent (user-confirmed; no upper bound) | `java.time.Duration.parse`: it accepts days and negative values, which Q9 makes absent. Half-even rounding: gives `PT30S` as absent, which contradicts `[SEAL-07]`'s half-up pin | Pinned and testable | The heuristic tier never sets cooking time or servings (Q9) |
| AD11 — URL picking and validation (Q3, Q4; `[SEAL-18]`, `[SEAL-21]`, `[SEAL-39]`, `[SEAL-40]`, `[SEAL-56]`, `[DEF-13]`) | **`ImportUrl.pick(text)` and `ImportUrl.check(text)`, pure Kotlin.** Pick scans the first 2048 characters for `https?://`, case-insensitive, starting at index 0 or after whitespace or one of `( [ < " '`. The URL ends at whitespace, `"`, `>`, or `'` **only when the URL was opened by `'`**, so `/grandma's-stew` survives. Then trailing `. , ; : ! ?` are dropped, and a trailing `)` or `]` is dropped while unbalanced inside the URL, repeating until stable. A match whose end reaches index 2048 is over-limit: for a share, the run is extended to its natural end in the full text so the field is never truncated; at Import it is rejected. Check: no match gives `Invalid(NO_URL)`; over-limit gives `Invalid(OVER_LIMIT)`; an `@` before the first `/`, `?`, `#` or `\` after `://` gives `Invalid(USERINFO)`; an empty host (authority without `:port`) gives `Invalid(NO_HOST)`; otherwise `Valid(ValidatedUrl)`. Picking runs on share arrival and on the Import tap, never on paste | Picking on paste: the spec keeps the field as typed (R1) | The userinfo rule is F5's own check (`[SEAL-21]`). Scheme case is kept as typed; the gateway lower-cases it | Every `Invalid` reason shows the one invalid-URL message (R1; `[DEF-13]`). A link that starts after character 2048 is `NO_URL` at Import and the no-link notice on a share |
| AD12 — Image candidate (Q10; R7) | **`ImageCandidateSelector.select(document, scan)` over three tiers, stopping at the first hit.** Tier 1: the first JSON-LD `Recipe` node, in walk order, whose `image` resolves to a string: a string, the first array element, an `ImageObject`'s `url` else `contentUrl`, or an `{"@id": …}` reference resolved to a node with that exact `@id` in the same block (one hop, no chains). Tier 2: the first `meta[property=og:image]` (case-insensitive) with non-blank `content`. Tier 3: among `img[src]` with non-blank `src`, the largest positive integer `width`×`height`, first on a tie; if none declares both, the first in document order. HTML candidates resolve with jsoup's `absUrl`, which honours the first `<base href>`. A JSON-LD string resolves inside one guard: `URI(raw)` is built with the `URI(String)` constructor; an absolute `raw` is used as is, and only a relative one builds `URI(document.baseUri())` and calls `base.resolve(parsed)` (so an absolute image URL never fails on the page URL), catching `URISyntaxException` **and** `IllegalArgumentException` (`URI.resolve(String)` throws the latter) and giving `NoImage(BAD_URL)`. A blank resolved value gives `NO_CANDIDATE`. The result must have scheme `http` or `https`, or the draft gets `NoImage(NOT_HTTP)`. Userinfo in an image URL is not checked (AD11): F4's address policy and OkHttp's request construction cover it (accepted risk, DR12) | Falling through when the chosen candidate fails: Q10 forbids it. `java.net.URL` for resolution: it would let F5 sources build a URL object, which the source guard bans (AD16) | The order and stopping rule are ARCHITECTURE C1's | A malformed URL (for example an unencoded space or `|`) gives `NoImage(BAD_URL)`, so an image failure never fails the import. Selection runs only after extraction qualifies, so a declined page makes no image request (R7) |
| AD13 — Threading and cancellation | **The importer is `suspend` and runs on its caller's context. CPU work runs in `withContext(parseDispatcher)`**, with `Dispatchers.Default` by default. `CancellationException` is caught nowhere except for an explicit rethrow ahead of each defensive `catch (e: Exception)`. The ViewModel runs one `importJob` in `viewModelScope`, with a generation counter: a result is applied only when its generation is still current. Cancel sets the input state at once, before the job finishes. **Each new import job first `join()`s the previous job inside `withContext(NonCancellable)`** (so a cancelled second job cannot skip the wait, and a Cancel, Import, Cancel, Import chain still serialises on the tail of the chain), so a cancel-then-import never holds two large DOMs at once; the UI is not blocked by that wait | Running parsing on `Dispatchers.Main`: a 10 MiB parse would freeze the UI. Setting the state when the cancelled job completes: the user would wait for a parse to notice cancellation | R2's "returns to the URL input state at once" holds even while a parse is finishing. A late result cannot overwrite a newer state | `parseDispatcher` is an internal constructor parameter, so tests can use `StandardTestDispatcher` |
| AD14 — UI states and messages (`[SEAL-03]`, `[SEAL-16]`, `[SEAL-25]`, `[SEAL-30]`, `[SEAL-32]`, `[SEAL-36]`, `[SEAL-38]`, `[SEAL-44]`, `[SEAL-53]`, `[SEAL-54]`, `[SEAL-60]`, `[SEAL-61]`) | **One `ImportUiState` with a screen, a field, a notice slot and a focus counter (DM8), driven by the state table in FC8.** A link-carrying share always replaces the screen with the input state; a share with no link only sets the notice and changes nothing else. Notices are not timed: each clears on the next user event (any tap, a field edit or a share). Every message is a constant string resource listed in Error Handling | A snackbar for transient notices: it times out, which an assistive-technology user can miss. One generic "something went wrong" message: Q6 asks for one per category | The asymmetry protects work in progress: a share that carries no link is a failed attempt and must not destroy an import, an error or a draft (R1) | `ImportMessages` maps every screen and notice to a resource id, so the R5 distinctness and R9 render tests can enumerate all of them |
| AD15 — Announcements (`[SEAL-41]`, `[SEAL-52]`, `[SEAL-57]`, `[SEAL-66]`) | **One polite live-region `Text` for notices, one for the status line, and one for errors; the draft-produced notice ("Recipe found" / "partly found") and the decline message and share explainer are each also a polite live region (R11).** Each `Notice` carries a sequence number. When it changes, the notice text is first set to empty for one frame (`withFrameNanos`), then to the constant string, so a repeat of the same notice is a content change that TalkBack announces again. A link-carrying share that cancels an import uses one combined constant ("Import cancelled. New link received. Tap Import to import it.", `import_notice_link_received_cancelled`), so there is one announcement, not two. Cancel shows the notice "Import cancelled." | `View.announceForAccessibility`: deprecated, and invisible to Compose semantics tests | Live regions are testable with Compose semantics (`SemanticsProperties.LiveRegion`) | TalkBack's actual speech is checked by hand on a device (Implementation Sequence step 9; DR3) |
| AD16 — Package split and source guards (`[SEAL-20]`, `[SEAL-64]`, `[SEAL-65]`; `[DEF-09]`) | **Packages `ie.pantry.recipeimport` (C1) and `ie.pantry.ui.recipeimport` (C9 surfaces).** `import` is a reserved word in Java, so `ie.pantry.import` could not be named from Java source or generated Java, and Kotlin would need backticks. The file split is in File Structure. `SingleCallSiteTest`'s forbidden list gains `Jsoup.connect` and `org.jsoup.helper.HttpConnection`. F5's own `ImportSourceGuardTest` bans `java.net.URL`, `.toURL()`, `IngredientEngine`, `RecipeRepository`, `ThumbnailStore`, `Dao`, `WebView`, `Instant.now(`, `Log.`, `println` and `printStackTrace` in both import packages (matched on word boundaries, so `Dialog.` or a `Dao`-containing identifier does not trip them; `ImportUrl.check(` call sites are exempt from the `check(` message rule), and requires every `throw`, `require(`, `check(` and `error(` message there to be a `$`-free literal. The scan is over source with comments and KDoc stripped, so a documentation mention of a banned token does not trip it | A regex for `Jsoup.parse(` with a URL argument: brittle. Without `java.net.URL` in scope, `Jsoup.parse(URL, int)` cannot be called at all | The only `android.*` import in `ie.pantry.recipeimport` is `android.graphics.BitmapFactory` in `ImageValidator.kt` | The `$`-free rule has F4's blind spot (`[DEF-06]` there): a message passed in by name is missed. AD1's `String`-free constructor and `ImportErrorHygieneTest` close it |
| AD17 — Harvest snapshots (Decision Point 6 sizing; `[DEF-11]`) | **Snapshots are committed under `app/src/test/resources/recipeimport/harvest/`, with an index `harvest/snapshots.tsv` (`source_url`, `retrieved`, `path`).** Path rule: `harvest/<host without www.>/<last non-empty path segment, lower-cased, characters outside [a-z0-9_-] replaced by '-'>.html`; a URL with no path segment uses `index`, and a collision on host and segment adds a numeric suffix (`-2`). `snapshots.tsv` is the authority for the binding, and `HarvestedCorpusTest` asserts every `path` is unique. Trimming is mechanical and recorded in a first-line comment: remove `<script>` elements other than `application/ld+json`, `<style>`, `<svg>`, `<noscript>` and HTML comments; keep everything else. Each trimmed snapshot is at most 1 MiB | Committing pages verbatim: several MiB of third-party script that extraction never reads. Re-fetching live in tests: no network in CI or the devcontainer, and pages change | `HarvestedCorpusTest` joins corpus rows to snapshots through the index, so the binding is explicit, not inferred from the URL | Each harvested line has no tab, no line break and no leading `#`. Normalisation (AD9) already rules out the first two; the test asserts all three |
| AD18 — Decline surface and image-phase cancel (`[DEF-08]`, `[SEAL-02]`, `[DEF-06]`) | **The decline surface keeps Retry** (R5 requires it, and a page may have served a consent wall or a transient variant). **Share link's chooser excludes Pantry** with `Intent.EXTRA_EXCLUDE_COMPONENTS`. **No "Skip picture" action**: Cancel during the picture phase cancels the whole import (Q12) | A Skip picture action: new UI and a new cancellation path, for a draft that F5 only announces and does not show | F6 owns draft editing and will revisit the image phase with it | `EXTRA_EXCLUDE_COMPONENTS` is API 24, so it is available at `minSdk` 26 |

**Routed spec items not settled by an AD row:** `[DEF-02]` field polish is in FC8. `[DEF-03]` and `[SEAL-15]` (`MutableClock`), `[DEF-04]` (`@GraphicsMode(NATIVE)`) and `[SEAL-24]` (Retry as a request-count delta) are in Testing Strategy. `[SEAL-37]` (single gateway instance) is in `AppContainerTest`. The spec's wording items (`[SEAL-43]`, `[SEAL-46]`, `[SEAL-47]`, `[SEAL-49]`, `[SEAL-55]`, `[SEAL-67]`, `[SEAL-68]`) need no design choice: this design treats the spec's ticked Open Questions as authoritative, ends an import with neither draft nor error only on Cancel or a link-carrying share, and runs a share's URL picking before any gateway call while relying on F4's address policy as the network control. `[DEF-05]`, `[DEF-07]`, and the per-criterion test splits in `[SEAL-03]`, `[SEAL-05]`, `[SEAL-12]`, `[SEAL-14]`, `[SEAL-19]`, `[SEAL-29]`, `[SEAL-35]`, `[SEAL-59]`, `[SEAL-62]` belong to the Tasks phase. The PLAN, ARCHITECTURE and F6 true-up items (`[SEAL-11]`, `[SEAL-42]`, `[SEAL-45]`, `[SEAL-58]`, `[SEAL-63]`) belong to the project-blueprint amendment and F6's spec, not to this design.

**CFC obligations:**

| CFC | How this design honours it | Verifying artifact |
|-----|----------------------------|--------------------|
| CFC-1 | F5 supplies raw lines and never parses them (AD16 guard). Normalisation is limited to Q8 (AD9). Harvested lines join F3's corpus | `app/src/test/java/ie/pantry/recipeimport/HarvestedCorpusTest.kt` (row floor, snapshot binding) and F3's unmodified `ParseFidelityCorpusTest` over the extended `parse_fidelity_corpus.tsv` |
| CFC-2 | `DraftValue.Absent`, `DraftImage.NoImage` and the empty ingredient list (AD8). The only rendering of draft content is the partly-found notice, which names what was found | `DraftProvenanceTest` (producer side, including the no-nullable-field source check) and `ImportScreenTest` (notice wording) |
| CFC-4 | `ImportException` has no `String` input (AD1). `importFailure` applies F4's five rules (FC2). No logging in either import package (AD16) | `ImportErrorHygieneTest` and `ImportSourceGuardTest` |

F5 is not named in any CFC's Enforcement prose, so no separate enforcement artifact is owed beyond the tests above.

## Component Design

### FC1 — URL intake

**Responsibility:** Pick the first `http(s)` URL from text and decide whether it may be imported.

**Location:** `app/src/main/java/ie/pantry/recipeimport/ImportUrl.kt`

**Key classes/functions:**
- `object ImportUrl` with `pick(text: String): PickedUrl?` and `check(text: String): UrlCheck` (I1; AD11). Pure Kotlin.
- `PickedUrl(text: String, overLimit: Boolean)`, `UrlCheck` (`Valid`, `Invalid`), `InvalidReason` and `@JvmInline value class ValidatedUrl internal constructor(val value: String)` (DM7).
- `const val SCAN_LIMIT = 2048`.

### FC2 — Result and error model

**Responsibility:** Define the content-free import error surface, including its CFC-4 re-wrapping rule.

**Location:** `app/src/main/java/ie/pantry/recipeimport/ImportResult.kt`

**Key classes/functions:**
- `sealed interface ImportResult` with `Drafted(draft)` and `Failed(error)` (DM5).
- `ImportException` with `Kind` (DM6; AD1).
- `internal fun importError(kind: Kind): ImportException`: a decision with no cause (not HTML, unsupported encoding, not recognised).
- `internal fun fetchFailure(error: GatewayException): ImportException`: copies `category` and `statusCode`, and copies the gateway error's stack frames.
- `internal fun importFailure(cause: Throwable): ImportException`: F4's five rules (a) to (e). The visited set is the cause, its chain and its suppressed exceptions. `ContentTooLargeException` anywhere in it gives `CONTENT_TOO_LARGE`; anything else gives `UNREADABLE_CONTENT` (corrupt gzip, a jsoup failure, or a defect). `causeType` is the top-level class's simple name, or null for the marker.
- `internal class ContentTooLargeException : IOException("Content too large")`: an internal marker with a constant message.

### FC3 — Draft model

**Responsibility:** Hold the in-memory draft that F6's form takes as its initial state.

**Location:** `app/src/main/java/ie/pantry/recipeimport/ImportDraft.kt`

**Key classes/functions:**
- `ImportDraft`, `DraftValue`, `DraftImage`, `NoImageCause` and `ExtractionTier` (DM1 to DM4; AD8).
- `ImportDraft.completeness: Completeness`: `Complete`, or `Partial(foundTitle, foundIngredients, foundMethod)`. It drives the notice (R5).

### FC4 — Bounded reader

**Responsibility:** Turn a fetched body into a parsed jsoup `Document` without exceeding the decompressed cap.

**Location:** `app/src/main/java/ie/pantry/recipeimport/BoundedHtmlReader.kt`

**Key classes/functions:**
- `internal object BoundedHtmlReader` with `isHtml(mediaType: String?): Boolean`, `headerCharset(mediaType: String?): String?` (AD6) and `parse(body, baseUri, cap, job, onStream): Document` (I3; AD7).
- `internal fun openBounded(bytes: ByteArray, encoding: BodyEncoding, cap: Long, maxTagBytes: Long, job: Job): CappedInputStream`, shared with FC6 for image bodies.
- `internal class CappedInputStream(delegate: InputStream, cap: Long, maxTagBytes: Long, job: Job) : FilterInputStream`, which exposes `produced: Long` for tests, reports `markSupported() == false`, and counts through `skip` as well as `read`.
- `const val DECOMPRESSED_CAP_BYTES = 10L * 1024 * 1024` (Q7).

### FC5 — Extraction

**Responsibility:** Find the best-tier recipe in a parsed page under R4's rules.

**Location:** `app/src/main/java/ie/pantry/recipeimport/` — `RecipeExtractor.kt`, `JsonLdExtractor.kt`, `MicrodataExtractor.kt`, `HeuristicExtractor.kt`, `LineText.kt`, `RecipeValues.kt`

**Key classes/functions:**
- `internal object RecipeExtractor` with `extract(document, job): Extraction?`: tier order, the qualify rule, and title carry-over (AD3). Calls `job.ensureActive()` between tiers. **Every DOM walk in FC5 is iterative, skips any subtree deeper than 512 elements (design Q6; user-confirmed) and calls `job.ensureActive()` every 1 024 visited nodes**, so a deeply nested page cannot stall Cancel or run quadratic walks. Nearest-enclosing-`itemscope` ownership uses the walk's own scope stack (no `parents()` or `closest()`), selectors are single-element document-wide queries, and `LineText.of` stops collecting once its builder passes 4 096 characters of raw text and returns null, so that line is dropped, not truncated (the 4 096 limit applies to raw collected text for DOM lines and to the raw string for JSON-LD). DOM depth is counted from the `Document` root with `html` at depth 1 (`body` is 2), so the boundary fixtures are a 512-deep and a 513-deep chain counted that way.
- `internal object JsonLdExtractor` with `recipes(document): JsonLdScan`, which parses, walks and drops each of the first 20 blocks in turn and keeps only, per `Recipe` in walk order, its mapped `ExtractedRecipe` and its raw image string (a same-block `@id` reference is followed before the block's tree is dropped; relative-URL resolution stays in AD12), never a `JsonElement` tree, so aggregate memory is the mapped recipes rather than 20 blocks of tree (a hostile block of many tiny `Recipe` nodes is bounded only by the 512 KiB block cap, DR14). It also has `toRecipe(node): ExtractedRecipe`. `internal object JsonDepth` with `withinBound(text, bound = 64)` (AD2).
- `internal object MicrodataExtractor` with `recipe(document): ExtractedRecipe?`. **Microdata:** the first element with `itemscope` whose `itemtype` contains a token ending `schema.org/Recipe` (`http` or `https`, case-insensitive). An `itemprop` belongs to the nearest enclosing `itemscope`, so a nested `NutritionInformation`'s `name` is not the title. **RDFa** (`[SEAL-33]`), tried only when there is no microdata `Recipe`: the first element whose `typeof` is `Recipe` under a `vocab` naming `schema.org`, or is `schema:Recipe`. Its properties are `property` values with or without the `schema:` prefix. Values: `meta` → `content`, `time` → `datetime` else text, `link`/`a` → `href`, otherwise `LineText.of`. Properties read: `name`; `recipeIngredient`, else `ingredients`; `recipeInstructions` (each `li`, else each `p` child, else the whole text as one step); `totalTime`; `cookTime`; `recipeYield`. `image` is never read here (R4).
- `internal object HeuristicExtractor` with `recipe(document): ExtractedRecipe?`. A heading is `h1` to `h6`. An ingredient heading's text contains `ingredient`, case-insensitive. A method heading contains one of the whole words `method`, `directions`, `instructions` or `steps`. The list is the first following sibling that is a `ul` or `ol` (or contains one as its first list descendant) before the next heading of the same or a higher level; its direct `li` children are the items. Failing that, the run of consecutive `p`, `div` or `li` siblings up to that heading gives one item each. At least 2 non-empty items are needed. The title is the first `h1`, else `meta[property=og:title]`, else `<title>`.
- `internal object LineText` with `normalise(raw)` and `of(element)` (AD9). JSON-LD block text is read with `Element.data()`: a `<script>` body is a `DataNode`, which `of(element)` (it collects `TextNode`s) would read as empty.
- `internal object RecipeValues` with `minutes(iso: String?): DraftValue<Int>` and `servings(value: JsonElement?)` / `servings(text: String?)` (AD10).
- `internal class ExtractedRecipe(title: DraftValue<String>, lines: List<String>, steps: List<String>, cookingTime: DraftValue<Int>, servings: DraftValue<Int>)` and `internal class Extraction(tier, recipe, jsonLd: JsonLdScan)` (DM10).

### FC6 — Image selection and validation

**Responsibility:** Turn a page's image candidate into a validated `DraftImage` or an explicit no-image state.

**Location:** `app/src/main/java/ie/pantry/recipeimport/ImageCandidateSelector.kt`, `app/src/main/java/ie/pantry/recipeimport/ImageValidator.kt`

**Key classes/functions:**
- `internal object ImageCandidateSelector` with `select(document, scan: JsonLdScan): Candidate`, where `Candidate` is `Url(value)`, `NotHttp` or `None` (AD12).
- `class ImageValidator(maxEdgePx: Int = 512, decodeObserver)` with `validate(bytes: ByteArray): DraftImage`. It reads bounds with `inJustDecodeBounds`, returns `NoImage(NOT_AN_IMAGE)` for non-positive bounds or for `width × height` (as `Long`) above 100 000 000 pixels (design Q6; user-confirmed; the native decode of a 65 535 × 65 535 header is long and uninterruptible), decodes once at `computeSampleSize(w, h, maxEdgePx)` (calling `decodeObserver(sampleSize, w / sampleSize, h / sampleSize)` immediately before the decode, a no-op in production, so tests can assert the bounds-first decode and that a rejected header never reaches it), recycles the bitmap, and returns `Validated(bytes, w, h)` with the original bytes and the bounds dimensions. An `OutOfMemoryError` during that decode, or a null bitmap, gives `NoImage(NOT_AN_IMAGE)`. Nothing is logged.

### FC7 — Importer

**Responsibility:** Run one import from a validated URL to an `ImportResult`.

**Location:** `app/src/main/java/ie/pantry/recipeimport/RecipeImporter.kt`

**Key classes/functions:**
- `fun interface ImportPipeline`, which the ViewModel depends on (I4).
- `class RecipeImporter internal constructor(gateway, clock, parseDispatcher, imageCheck, seams) : ImportPipeline`, with a public secondary constructor `(gateway, clock)` that `AppContainer` uses. `internal val gateway` is exposed for `AppContainerTest`'s identity check.
- `enum class ImportPhase { FETCHING_PAGE, FETCHING_PICTURE }`.
- `internal class ImportSeams(onParse: () -> Unit, onStream: (CappedInputStream) -> Unit)`, where `onStream` is called for the page's stream and again for the image's stream (so a test can tell an inflate-then-check image path from the streaming cap), with `ImportSeams.PRODUCTION` doing nothing (DM9).

**Pipeline (`importRecipe`), in order:** `onPhase(FETCHING_PAGE)`; `gateway.fetchPage(url.value)` (`Failed` → `fetchFailure`); `fetchedAt = clock.instant()`; media type (→ `NOT_HTML`); encoding (`OTHER` → `UNSUPPORTED_ENCODING`); on `parseDispatcher`: `seams.onParse()`, `BoundedHtmlReader.parse`, `RecipeExtractor.extract` (null → `NOT_RECOGNISED`) and `ImageCandidateSelector.select`; the `Document` goes out of scope; for a `Url` candidate, `onPhase(FETCHING_PICTURE)`, `gateway.fetchImage`, then, on `parseDispatcher`, `openBounded` and `imageCheck` (any `Failed` → `NoImage(FETCH_FAILED)`; `OTHER`, a cap breach or corrupt gzip → `NoImage(UNREADABLE)`); build the draft. The whole body is in `try { … } catch (e: CancellationException) { throw e } catch (e: Exception) { Failed(importFailure(e)) }`.

### FC8 — Import screen

**Responsibility:** Render the import states from user and share events.

**Location:** `app/src/main/java/ie/pantry/ui/recipeimport/` — `ImportViewModel.kt`, `ImportUiState.kt`, `ImportScreen.kt`, `ImportMessages.kt`, `ShareIntents.kt`

**Key classes/functions:**
- `class ImportViewModel(pipeline: ImportPipeline) : ViewModel()` with `state: StateFlow<ImportUiState>`, `onFieldChange`, `onImport`, `onCancel`, `onRetry`, `onTryAnother`, `onImportAnother`, `onShareReceived(text: String?)`, `onBack(): Boolean` (I6).
- `ImportUiState` and its parts (DM8).
- `@Composable fun ImportScreen(state, actions)`: a `Scaffold` whose top bar shows `app_name` once, over a `verticalScroll` column so every control stays reachable at 200% font scale and in landscape (R11). `BackHandler` is enabled while the screen is In progress, Failed or Declined.
- `object ImportMessages` with `@StringRes fun of(screen)`, `of(notice)`, and `val ALL: List<Int>` (every import string, for R5 and R9 tests).
- `object ShareIntents` with `sharedText(intent: Intent?): String?` and `chooserFor(url: String, context: Context): Intent` (AD4, AD18).

**Field polish (`[DEF-02]`):** `OutlinedTextField` labelled "Recipe link", `singleLine`, `KeyboardOptions(keyboardType = Uri, imeAction = Go, autoCorrectEnabled = false, capitalization = None)`, and `KeyboardActions(onGo = onImport)`. Supporting text is the first-use hint, replaced by the field error when there is one. No autofocus on launch or after a share; focus is requested after "Try another link" and "Import another" (`focusSeq`).

**State table** (the field is shown and editable in Input and Failed, shown read-only in In progress, and hidden on Declined and Drafted):

| State ↓ / Event → | Import tap | Cancel or Back | Retry | Try another link / Import another | Share with a link | Share with no link (or missing text) | Field edit |
|---|---|---|---|---|---|---|---|
| **Input** | `check(field)`: `Invalid` → field error, field kept; `Valid` → field = picked URL, In progress (page) | Back: system default | — | — | field = picked text, field error cleared, notice "link received" | notice "no link"; field and field error unchanged | field = text verbatim; field error and notice cleared |
| **In progress** (page or picture) | ignored (button not shown; a second call is a no-op) | job cancelled; Input, field = picked URL, notice "import cancelled" | — | — | job cancelled; Input, field = picked text, notice "import cancelled, new link received" | notice "no link"; import keeps running | not editable |
| **Failed** (message ± Retry) | as from Input (error cleared) | Back: Input, field kept | only when retryable: In progress with the same `ValidatedUrl` | — | Input, field = picked text, error and Retry cleared, notice "link received" | notice "no link" shown beside the error; Retry kept | Input, field = text verbatim; error, Retry and notice cleared (so Retry can never import a URL the field no longer shows) |
| **Declined** | — | Back: Input, field = declined URL | In progress with the same `ValidatedUrl` | Try another link: Input, field empty, focus requested | Input, field = picked text, notice "link received" | notice "no link" over the decline surface; nothing else changes | — |
| **Drafted** | — | Back: system default (draft kept while the activity lives) | — | Import another: draft discarded; Input, field empty, focus requested | draft discarded (`[SEAL-50]`); Input, field = picked text, notice "link received" | notice "no link" over the notice; nothing else changes | — |

Results: `Drafted` → Drafted; `Failed(NOT_RECOGNISED)` → Declined; any other `Failed` → Failed, with Retry exactly when the kind is `FETCH_FAILED` and the category is `TIMEOUT`, `CONNECTION_FAILED`, or `HTTP_STATUS` 500 to 599 (Q6). A notice clears on the next event of any kind in this table. Rotation changes nothing and replays nothing (AD4). Every share event, with or without a link, starts no fetch.

### FC9 — Wiring

**Responsibility:** Connect the import screen and importer to the app's activity, container, manifest and resources.

**Location:** `app/src/main/java/ie/pantry/ui/MainActivity.kt`, `app/src/main/java/ie/pantry/di/AppContainer.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`

**Key classes/functions:**
- `MainActivity`: `private val viewModel: ImportViewModel by viewModels { viewModelFactory { initializer { ImportViewModel((application as PantryApplication).container.recipeImporter) } } }`; `setContent { PantryTheme { ImportScreen(…) } }`; share handling per AD4. `PlaceholderScreen.kt` stays in the tree, unused, for F6 to remove **[ASSUMPTION]**.
- `AppContainer`: two new last constructor parameters: `val clock: Clock = Clock.systemUTC()`, then `val recipeImporter: RecipeImporter = RecipeImporter(gateway, clock)`. A test passes its own `RecipeImporter` (with an injected `parseDispatcher`, `imageCheck` and `ImportSeams`, through the internal constructor, visible to the test source set), which is how `ImportTestApplication` gets its seams; production never passes it. `production(app, clock)` passes its existing `clock` through. Construction still does no I/O.

### FC10 — Tests, fixtures and harvest

**Responsibility:** Prove R1 to R11, including the harvested-corpus evidence.

**Location:** `app/src/test/java/ie/pantry/recipeimport/`, `app/src/test/java/ie/pantry/ui/recipeimport/`, `app/src/test/resources/recipeimport/`, `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv`

**Key classes/functions:**
- `ImportFixtures` (test package `ie.pantry.recipeimport`): `page(name): ByteArray`, `enqueuePage(server, body, contentType, gzip = false)`, `gzip(bytes)`, `bomb(inflatedBytes: Long)`, `deepJsonLd(depth: Int, viaGraph: Boolean)`, `importer(server, clock, imageCheck = …, seams = …)`.
- `ImportTestApplication : PantryApplication` (test package `ie.pantry.ui.recipeimport`): builds `AppContainer` over an in-memory database, a gateway from `TestGateways.against(server)` and a `MutableClock`, taken from a companion `var` that the test sets before launching the activity. The container is lazy, so it is first read after the test sets the gateway.
- `FakePipeline : ImportPipeline`: records each `ValidatedUrl` and suspends on a `CompletableDeferred<ImportResult>` per call.

## Data Models

| Model | Field | Type | Constraints | Description |
|-------|-------|------|-------------|-------------|
| DM1 `ImportDraft` | sourceUrl | String | Equals the `ValidatedUrl.value` the import started with; never the redirect target | Provenance (R6; Q13) |
| DM1 `ImportDraft` | fetchedAt | Instant | `clock.instant()` read when `fetchPage` returned `Fetched` | Provenance (R6; Q13) |
| DM1 `ImportDraft` | tier | ExtractionTier | Required | The tier that supplied every field except the image (and a carried title) |
| DM1 `ImportDraft` | title | DraftValue<String> | `Stated` value non-blank after normalisation | R4's title rules |
| DM1 `ImportDraft` | ingredientLines | List<String> | Each non-empty, normalised (AD9); empty list = no ingredients stated | Raw lines for F6 to parse via C4 |
| DM1 `ImportDraft` | method | DraftValue<List<String>> | `Stated` list non-empty; each step non-empty | Ordered steps (AD9) |
| DM1 `ImportDraft` | cookingTime | DraftValue<Int> | `Stated` ≥ 1 minute, never 0; AD10's digit limits keep it within `Int` | Whole minutes (AD10). Always `Absent` on the heuristic tier |
| DM1 `ImportDraft` | servings | DraftValue<Int> | `Stated` ≥ 1 | The spec's yield (R6), named `servings` because `yield` is a reserved identifier in Kotlin (AD10). Always `Absent` on the heuristic tier |
| DM1 `ImportDraft` | image | DraftImage | Required | DM3 |
| DM2 `DraftValue<T>` | — | sealed interface | `Stated<T>(value: T)` or `data object Absent` | Explicit absence (CFC-2) |
| DM3 `DraftImage` | — | sealed interface | `Validated(bytes: ByteArray, widthPx: Int, heightPx: Int)` with `bytes` the original fetched (decompressed) bytes and dimensions from the bounds read; `NoImage(cause: NoImageCause)` | Image or explicit no-image |
| DM3 `NoImageCause` | — | enum | `NO_CANDIDATE`, `NOT_HTTP`, `BAD_URL`, `FETCH_FAILED`, `UNREADABLE`, `NOT_AN_IMAGE` | Content-free; lets tests tell R7's paths apart |
| DM4 `ExtractionTier` | — | enum | `JSON_LD`, `MICRODATA` (microdata or RDFa, ARCHITECTURE C1's one tier), `HEURISTIC` | R4 |
| DM5 `ImportResult` | — | sealed interface | `Drafted(draft: ImportDraft)` or `Failed(error: ImportException)` | Never both, never neither |
| DM6 `ImportException` | kind | Kind | `FETCH_FAILED`, `NOT_HTML`, `UNSUPPORTED_ENCODING`, `UNREADABLE_CONTENT`, `CONTENT_TOO_LARGE`, `NOT_RECOGNISED` | AD1 |
| DM6 `ImportException` | gatewayCategory | GatewayException.Category? | Non-null iff `kind == FETCH_FAILED` | Copied from F4 |
| DM6 `ImportException` | statusCode | Int? | Non-null iff `gatewayCategory == HTTP_STATUS` | Numeric status only |
| DM6 `ImportException` | causeType | String? | A class simple name; null for decisions and the marker | e.g. `ZipException` |
| DM6 `ImportException` | message | String (inherited) | `Import failure: kind=… gateway=… status=… cause=…` only | No URL, host, HTML, title or line |
| DM7 `ValidatedUrl` | value | String | Only built by `ImportUrl.check`; `http`/`https` scheme, non-empty host, no userinfo, under the scan limit | The URL handed to the gateway |
| DM7 `UrlCheck` | — | sealed interface | `Valid(url: ValidatedUrl)` or `Invalid(reason: InvalidReason)` | AD11 |
| DM7 `InvalidReason` | — | enum | `NO_URL`, `OVER_LIMIT`, `USERINFO`, `NO_HOST` | All render the invalid-URL message |
| DM7 `PickedUrl` | text, overLimit | String, Boolean | `text` is the full run for a share, never truncated | Share-side pick |
| DM8 `ImportUiState` | field | String | Verbatim user text, or a picked URL after a share or a successful Import tap | URL field |
| DM8 `ImportUiState` | screen | Screen | `Input(fieldError: Boolean)`, `InProgress(phase: ImportPhase)`, `Failed(message: Failure, retryable: Boolean)`, `Declined(url: ValidatedUrl)`, `Drafted(draft: ImportDraft)` | FC8 state table |
| DM8 `ImportUiState` | notice | Notice? | `Notice(kind: NoticeKind, seq: Int)`; `NoticeKind` is `LINK_RECEIVED`, `LINK_RECEIVED_IMPORT_CANCELLED`, `NO_LINK`, `IMPORT_CANCELLED`. Null means no notice, which is not draft data | AD14, AD15 |
| DM8 `ImportUiState` | focusSeq | Int | Increments on Try another link and Import another | One-shot focus request |
| DM8 `Failure` | — | enum | One value per row of the Error Handling message table that can appear on the Failed screen | Maps to a string resource |
| DM9 `ImportSeams` | onParse, onStream | () -> Unit, (CappedInputStream) -> Unit | `PRODUCTION` = no-ops; every parameter a `val` | R2's parser seam, R3's counting seam |
| DM10 `ExtractedRecipe` / `Extraction` | — | internal classes | Same field rules as DM1; never leave `ie.pantry.recipeimport` | Tier output before draft assembly |
| DM11 harvest index row | source_url, retrieved, path | String, LocalDate, String | Tab-separated; one row per snapshot; `path` follows AD17's rule | Binds corpus rows to snapshots |


**Relationships:**
- `ImportResult.Drafted` has one `ImportDraft`, which has one `DraftImage`.
- `ImportResult.Failed` has one `ImportException`, which has no cause and no suppressed exceptions.
- `ImportUiState.screen` holds at most one `ImportDraft` (Drafted) or one `ValidatedUrl` (Declined). The ViewModel also keeps the last `ValidatedUrl` privately for Retry.
- Each `F5_HARVESTED` row in `parse_fidelity_corpus.tsv` matches exactly one DM11 row by `(source_url, retrieved)`.

**Persistence:** none. The draft is in memory only and lives in the ViewModel while the activity lives. No file is written (spec Never Do). Snapshots and corpus rows are test resources.

## Interfaces

```kotlin
package ie.pantry.recipeimport

// ---- I1: URL intake (ImportUrl.kt; pure Kotlin) ----
object ImportUrl {
    const val SCAN_LIMIT: Int = 2048
    /** Share side: the first http(s) URL in the first 2048 characters; extended past the limit when it runs into it. Null when none. */
    fun pick(text: String): PickedUrl?
    /** Import side: pick within the first 2048 characters, then validate. Never throws. */
    fun check(text: String): UrlCheck
}

// ---- I2: result, error and re-wrap (ImportResult.kt) ----
sealed interface ImportResult {
    data class Drafted(val draft: ImportDraft) : ImportResult
    data class Failed(val error: ImportException) : ImportResult
}
class ImportException internal constructor(
    val kind: Kind,
    val gatewayCategory: GatewayException.Category?,
    val statusCode: Int?,
    causeClass: KClass<out Throwable>?,
) : RuntimeException(/* built from the fields above */) {
    val causeType: String?
    enum class Kind { FETCH_FAILED, NOT_HTML, UNSUPPORTED_ENCODING, UNREADABLE_CONTENT, CONTENT_TOO_LARGE, NOT_RECOGNISED }
}
internal fun importError(kind: ImportException.Kind): ImportException
internal fun fetchFailure(error: GatewayException): ImportException
internal fun importFailure(cause: Throwable): ImportException

// ---- I3: bounded reader (BoundedHtmlReader.kt) ----
internal object BoundedHtmlReader {
    fun isHtml(mediaType: String?): Boolean
    fun headerCharset(mediaType: String?): String?
    /** @throws Exception any failure; the importer re-wraps it with importFailure. */
    fun parse(body: FetchedBody, baseUri: String, cap: Long, job: Job, onStream: (CappedInputStream) -> Unit): Document
}

// ---- I4: pipeline (RecipeImporter.kt) ----
fun interface ImportPipeline {
    suspend fun importRecipe(url: ValidatedUrl, onPhase: (ImportPhase) -> Unit): ImportResult
}
class RecipeImporter internal constructor(
    internal val gateway: ExternalDataGateway,
    private val clock: Clock,
    private val parseDispatcher: CoroutineDispatcher,
    private val imageCheck: (ByteArray) -> DraftImage,
    private val seams: ImportSeams,
) : ImportPipeline {
    constructor(gateway: ExternalDataGateway, clock: Clock) :
        this(gateway, clock, Dispatchers.Default, ImageValidator()::validate, ImportSeams.PRODUCTION)
    override suspend fun importRecipe(url: ValidatedUrl, onPhase: (ImportPhase) -> Unit): ImportResult
}

// ---- I5: image (ImageValidator.kt; the only android.* user in this package) ----
class ImageValidator(
    private val maxEdgePx: Int = 512,
    private val decodeObserver: (sampleSize: Int, decodedWidth: Int, decodedHeight: Int) -> Unit = { _, _, _ -> },
) {
    fun validate(bytes: ByteArray): DraftImage
}
```

```kotlin
package ie.pantry.ui.recipeimport

// ---- I6: ViewModel (ImportViewModel.kt) ----
class ImportViewModel(private val pipeline: ImportPipeline) : ViewModel() {
    val state: StateFlow<ImportUiState>
    fun onFieldChange(text: String)
    fun onImport()
    fun onCancel()
    fun onRetry()
    fun onTryAnother()
    fun onImportAnother()
    fun onShareReceived(text: String?)
    /** @return true when Back was consumed (In progress, Failed, Declined). */
    fun onBack(): Boolean
}

// ---- I7: share intents (ShareIntents.kt) ----
object ShareIntents {
    /** EXTRA_TEXT of an ACTION_SEND as a CharSequence, or null (other action, missing, or unparcelling failed). */
    fun sharedText(intent: Intent?): String?
    /** createChooser over ACTION_SEND text/plain with EXTRA_TEXT = url, excluding MainActivity. */
    fun chooserFor(url: String, context: Context): Intent
}
```

**Contracts:**
- **I1:** single pass over the scan window: the `)`/`]` balance is tracked with running counters as the URL is scanned, never recounted per dropped character, so cost is linear in the window (the share-side over-limit extension is likewise one pass). Total; never throws; no allocation proportional to text beyond the scan window, except `pick`'s over-limit extension, which is bounded by the text itself. `check(" https://example.ie/stew ")` is `Valid("https://example.ie/stew")`. Every R1 rejection fixture gives `Invalid`.
- **I2:** `toString()`, `message`, `localizedMessage` and `stackTraceToString()` hold only enum names, an integer, a class simple name and stack frames; `cause == null`; `suppressed` is empty. `importFailure` never logs and never reads a message.
- **I3:** `parse` reads at most `cap + 1` bytes from the decompressing stream, then throws `ContentTooLargeException` (possibly wrapped by jsoup in `org.jsoup.UncheckedIOException`; `importFailure` walks the chain). It never executes script or loads a sub-resource.
- **I4 preconditions:** a `ValidatedUrl`, so no invalid input reaches the gateway. **Postconditions:** returns `Drafted` or `Failed`; never null; never throws except `CancellationException` (an `Error` other than the decode `OutOfMemoryError` is not caught, per Error Handling). At most one `fetchPage` and at most one `fetchImage` per call. `fetchImage` is never called when the result is `Failed`. **Side effects:** the two gateway calls only; no file, no log, no persistent state. `onPhase` is called on the caller's context, `FETCHING_PAGE` first, then `FETCHING_PICTURE` only when an image fetch starts.
- **I5:** never throws for any input; never decodes at native resolution when the bounds exceed `maxEdgePx`; `Validated.bytes` is the same array it was given.
- **I6:** every method is main-thread only and idempotent where the state table says "ignored". `onImport` calls the pipeline at most once while a job runs. `state` always holds exactly one `Screen`.
- **I7:** `sharedText` never throws. `chooserFor`'s inner intent carries no extra other than `EXTRA_TEXT`.

## Error Handling

- **Strategy:** a sealed result (`ImportResult`) whose failure side is the `Throwable`-shaped, content-free `ImportException` (AD1). Expected failures never leave the importer as thrown exceptions. Image failures are not errors: they become `DraftImage.NoImage` (R7). Invalid input never reaches the importer (AD11).

| Condition | Outcome | Where decided |
|---|---|---|
| No URL, over-limit, userinfo, no host | `UrlCheck.Invalid(reason)`; field error | FC1 at the Import tap |
| `fetchPage` returns `Failed` (any category) | `FETCH_FAILED` + category (+ status) | FC7 `fetchFailure` |
| Media type missing or not HTML | `NOT_HTML`; parser seam count 0 | FC4 `isHtml`, checked first |
| `BodyEncoding.OTHER` on the page | `UNSUPPORTED_ENCODING`; parser seam count 0 | FC7, after the media type |
| Decompressed bytes exceed 10 MiB, or more than 200 000 `<` bytes (Q1) | `CONTENT_TOO_LARGE` (`causeType` null) | FC4 `CappedInputStream` → FC2 |
| Truncated or invalid gzip, any jsoup or extractor exception | `UNREADABLE_CONTENT` (`causeType` e.g. `ZipException`, `EOFException`) | FC2 `importFailure` |
| JSON-LD block invalid, too deep, or 21st and later | Block skipped; extraction continues | FC5 `JsonLdExtractor` (`SerializationException` and `IllegalArgumentException` caught and dropped; nothing kept) |
| Nothing qualifies at any tier | `NOT_RECOGNISED`; no image request | FC5 → FC7 |
| No candidate / non-`http(s)` / image fetch `Failed` / image `OTHER`, over cap or corrupt / not decodable | `NoImage(NO_CANDIDATE / NOT_HTTP / BAD_URL / FETCH_FAILED / UNREADABLE / NOT_AN_IMAGE)`; draft still produced | FC6, FC7 |
| Coroutine cancellation | Propagates; no state written by the cancelled job | AD13 |
| `Error` (other than the decode `OutOfMemoryError` in FC6) | Not caught | Same rule as F3 and F4 |

- **Custom exceptions / error types:** `ImportException` (public) and the internal marker `ContentTooLargeException : IOException` with a constant message, always converted by `importFailure`.
- **Logging:** none in either import package (AD16). `ImageValidator` does not copy `ThumbnailProcessor`'s `Log.w`.
- **CFC-4 re-wrap targets** (`[SEAL-04]`): jsoup (`org.jsoup.UncheckedIOException`, `ValidationException`, `SelectorParseException`) → `UNREADABLE_CONTENT` unless the chain holds the marker; `kotlinx.serialization.SerializationException` → never reaches the boundary (dropped per block), and `importFailure` maps it to `UNREADABLE_CONTENT` if it ever does; `java.util.zip.ZipException` and `EOFException` → `UNREADABLE_CONTENT`; `BitmapFactory` failures → `NoImage`, never an exception.
- **User-facing errors:** every message is a constant string resource with no format argument (`[SEAL-03]`, `[SEAL-16]`, `[SEAL-25]`, `[SEAL-36]`, `[SEAL-60]`). The copy is design Q3.

| Resource | Shown for | Text | Retry |
|---|---|---|---|
| `import_error_invalid_url` | Any `UrlCheck.Invalid` at Import | That isn't a web link Pantry can use. A link must start with http:// or https://. | — |
| `import_notice_no_link` | Share with no link or missing text | No link found in what you shared. | — |
| `import_notice_link_received` | Share with a link | Link received. Tap Import to import it. | — |
| `import_notice_link_received_cancelled` | Share with a link during an import | Import cancelled. New link received. Tap Import to import it. | — |
| `import_notice_cancelled` | Cancel or Back during an import | Import cancelled. | — |
| `import_status_page` / `import_status_picture` | In progress | Importing the recipe… / Fetching the picture… | — |
| `import_error_not_html` | `NOT_HTML` | This link isn't a web page Pantry can read. | No |
| `import_error_unsupported_encoding` | `UNSUPPORTED_ENCODING` | This page uses a compression Pantry can't read. | No |
| `import_error_unreadable` | `UNREADABLE_CONTENT` | This page's content is damaged, so Pantry couldn't read it. | No |
| `import_error_too_large` | `CONTENT_TOO_LARGE` and gateway `RESPONSE_TOO_LARGE` | This page is too large for Pantry to read. | No |
| `import_error_not_recognised` | `NOT_RECOGNISED` (decline surface) | Pantry didn't recognise this page as a recipe. | Yes (R5) |
| `import_decline_share_explainer` | Decline surface | Sharing the link is how you can tell whoever you choose that Pantry didn't recognise this page. Pantry itself sends nothing. | — |
| `import_error_timeout` | `TIMEOUT` | The site took too long to respond. | Yes |
| `import_error_connection` | `CONNECTION_FAILED` | Pantry couldn't reach the site. Check your connection. | Yes |
| `import_error_http_5xx` | `HTTP_STATUS` 500–599 | The site is having a problem right now. | Yes |
| `import_error_http_404` | `HTTP_STATUS` 404 | The site says this page doesn't exist. | No |
| `import_error_http_denied` | `HTTP_STATUS` 401, 403 | The site won't let Pantry see this page. | No |
| `import_error_http_other` | Any other `HTTP_STATUS` | The site returned an error. | No |
| `import_error_address_refused` | `ADDRESS_REFUSED` | This link, or a redirect it follows, points to a private or local network address. Pantry doesn't fetch those. | No |
| `import_error_redirect_refused` | `SCHEME_REFUSED` (a redirect to `intent://` and similar) | This link redirects somewhere Pantry won't open. | No |
| `import_error_too_many_redirects` | `TOO_MANY_REDIRECTS` | This link redirects too many times. | No |
| `import_error_bad_link` | `INVALID_REQUEST` | Pantry couldn't use this link. | No |
| `import_error_unexpected` | `UNEXPECTED` | Something went wrong fetching this page. | No |
| `import_notice_recipe_found` | Drafted, complete | Recipe found. | — |
| `import_notice_partly_*` (5) | Drafted, partial: `ingredients`, `ingredients_title`, `ingredients_method`, `method`, `method_title` | Recipe partly found: only the ingredients. / …the title and ingredients. / …the ingredients and method. / …the method. / …the title and method. | — |
| Labels | Controls | Recipe link, Import, Cancel, Retry, Try another link, Import another, Share link, Share recipe link (chooser title), and the hint "Paste a recipe link, or share one to Pantry from your browser." | — |

## Testing Strategy

- **Framework:** JUnit 4 (`junit:junit:4.13.2`), `kotlin.test` assertions, `kotlinx-coroutines-test` 1.9.0, `okhttp3.mockwebserver` 4.12.0 through `TestGateways`, Robolectric 4.14.1, and Compose `ui-test-junit4`, as F1 to F4 use.
- **Test location:** `app/src/test/java/ie/pantry/recipeimport/` and `app/src/test/java/ie/pantry/ui/recipeimport/`, mirroring main. Fixtures in `app/src/test/resources/recipeimport/pages/` and `…/recipeimport/harvest/`.
- **Mocking approach:** no mocking library. Real `MockWebServer`s via `TestGateways.against(server)`; `FakePipeline` for ViewModel and screen-state tests; an injected `imageCheck` lambda in plain-JVM pipeline tests, and the real `ImageValidator` under Robolectric `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (`[DEF-04]`) wherever image bytes are decoded.
- **Coverage expectations:** every GIVEN/WHEN/THEN in R1 to R11 is asserted by the class named below. Every `Kind`, every `NoImageCause`, every `InvalidReason` and every row of the FC8 state table has at least one test. Every public function in I1 to I7 has a happy-path and a failure-path test.
- **Fixtures / test data (`[DEF-10]`):** every committed page fixture is under 16 KiB, well inside `FAST`'s 64 KiB raw cap. The gzip bomb is 16 MiB of zeros gzipped in memory (about 16 KiB on the wire). The deep JSON-LD blocks at importer level are depth 64, 65 and 1 000 (each under 20 KiB, inside `FAST`'s 64 KiB raw cap), each also in an `@graph` variant. Depths 10 000 and 1 000 000 are tested on `JsonDepth` directly, with no gateway, because a 10 000-deep `@graph` block is about 65 000 bytes. The decode-assertion PNG bomb is `ImageFixtures.pngBomb(9_000, 9_000)` (81 MP, under the 100 MP ceiling, so it reaches the decode at sample size 16); the over-ceiling and boundary cases use a header-only PNG (`ImageFixtures.pngHeaderOnly(width, height)`: a valid signature and IHDR plus a stub IDAT, so `inJustDecodeBounds` reads the declared size but a full decode would fail). The bomb is tested on `ImageValidator` directly and once through the importer with `TestGateways.FAST.copy(maxBodyBytes = 1 MiB)`. Harvest snapshots run through `BoundedHtmlReader.parse` and `RecipeExtractor.extract` directly, with no gateway and no image request (so no `RecordingDns` retry or backoff per snapshot). Images come from `ImageFixtures.jpeg`, `png` and `garbage`. Sentinels come from `ie.pantry.testutil.Sentinels`.
- **Clock (`[DEF-03]`, `[SEAL-15]`):** `MutableClock` gets `@Volatile` on its private `now` (a one-word change to the shared test utility), because the MockWebServer dispatcher thread sets it. The page dispatcher sets instant B just before answering, and the image dispatcher sets instant C, after the test started at instant A. `fetchedAt` must equal B.
- **Retry (`[SEAL-24]`):** "one new `fetchPage` call" is asserted as a request-count delta. The first import gets three 503s (F4 retries internally), Retry gets a 200, and the delta across Retry is exactly 1 page request.
- **Waiting discipline:** no sleeps. Screen tests use Compose `waitUntil(timeout)`. Plain-JVM tests that cross to MockWebServer threads use one shared helper, `awaitState(timeout) { predicate }`: a poll loop inside `runBlocking` with a real-time `withTimeout`: `runCurrent()` on the `StandardTestDispatcher` main, check `state.value`, a short real `delay`, repeat until the predicate holds. `state.first { … }` is not used, because it suspends and never advances the test dispatcher. `ImportTestApplication` injects `parseDispatcher` (a `StandardTestDispatcher` or `Dispatchers.Unconfined`), so the importer's CPU work never races the Robolectric main looper.
- **Naming convention:** backtick-quoted descriptive names, as in F1 to F4.

| Test class | Runner | Covers | Notes |
|---|---|---|---|
| `ImportUrlTest` | plain JVM | R1 AC1, AC4, AC5; AD11 | Every rejection fixture; the three pick examples; `xhttps://a`, `url=https://a`, `ftp://x/?u=https://evil`, `intent://` fallback text do not match; `HTTPS://…` and `<https://…>` do; `Foo_(bar)` keeps its bracket; `/grandma's-stew` inside and outside quotes; `\` in the authority; a URL ending exactly at 2048 and one running past it (share: full run; check: `Invalid(OVER_LIMIT)` for both); a 1 MB hostile text with many trailing `)` and `]` completes in linear time (the 2N input takes under 4× the N input's time, plus a generous few-second ceiling); a link starting at 2049 |
| `ShareIntentsTest` | Robolectric | AD4; `[DEF-12]` | `SpannableString` `EXTRA_TEXT` read; missing extra; non-`SEND` action; extras whose unparcelling throws (driven by an `Intent` subclass overriding `getCharSequenceExtra` to throw); chooser shape and `EXTRA_EXCLUDE_COMPONENTS` |
| `ImportViewModelTest` | plain JVM, `Dispatchers.setMain(StandardTestDispatcher())` | R1 AC1, AC2, AC3, AC4, AC6; R2 AC2 Retry, AC6; R5 AC2, AC5; FC8 state table | One test per state-and-event cell. R1 AC1: `FakePipeline` records exactly `https://example.ie/stew` for `"  https://example.ie/stew  "`; R1 AC4: eight rejected inputs record zero pipeline calls. Cancel during the picture phase and during a long fake call both return to Input at once, and a cancel that races a completing result discards the result; `FakePipeline` has a non-cancellable mode so AD13's join ordering is observable. The importer-level stalled-parse cancel (a parse stalled by `seams.onParse`, stopped by `CappedInputStream`'s `ensureActive`, no `Failed` produced) is in `ImportFlowTest`. Import, Cancel, Import again runs the second job only after the first has ended (AD13). Double Import gives one pipeline call. Cancel sets Input before the fake completes; a late result is discarded. Repeat share bumps `notice.seq` |
| `RecipeImporterFetchTest` | plain JVM | R2 AC1–AC6 | One request per page import; Timeout (`NO_RESPONSE`), 404, refused address (a second, non-exempt server); `application/json`, `image/png`, `application/pdf` and missing `Content-Type` are rejected; accepted: `application/xhtml+xml`, `TEXT/HTML`, `text/html ; charset="utf-8"`, and the near-miss `text/htmlx` is rejected; an empty body and a gzip of an empty body give `NOT_RECOGNISED` and `UNREADABLE_CONTENT` respectively; `br` encoding; non-HTML with `br` reports not-HTML; truncated and invalid gzip; parser seam count 0 on each rejection; cancel against `NO_RESPONSE` |
| `CappedInputStreamTest` | plain JVM | R3 AC1 | An instrumented delegate: bytes pulled from it ≤ `cap + 1`; each read asks for at most `cap + 1 − produced`; `markSupported()` is false; `skip` is counted; `<` counted across reads; `maxTagBytes = Long.MAX_VALUE` never trips. A bomb large enough that inflate-then-check would exhaust the 1 GiB heap proves streaming |
| `ContentBoundsTest` | plain JVM | R3 AC1–AC4; AD6 | Bomb: `CONTENT_TOO_LARGE`, `onStream`'s `produced ≤ cap + 8192`. Size boundary: a body of exactly 10 MiB parses, 10 MiB + 1 gives `CONTENT_TOO_LARGE` (both generated and gzipped in memory, so they fit `FAST`'s raw cap). Markup-density boundary: a body with exactly 200 000 `<` bytes parses, 200 001 gives `CONTENT_TOO_LARGE` (gzip of repeated `<`). The image path passes `maxTagBytes = Long.MAX_VALUE`: a valid low-entropy image with more than 200 000 `0x3C` bytes is not refused. A JSON-LD block of 512 KiB parses and 512 KiB + 1 is skipped, still counting toward the 20; a gzip-delivered `[0,0,…]` block over the cap is skipped without `OutOfMemoryError`. Depth 64 parsed, 65 and 10 000 skipped, `@graph` variants. 21 blocks. Scripts, inline handlers and iframes: request count = page + at most 1. Charset: Windows-1252 by `<meta>` gives `½ tsp salt` and `crème fraîche`; by header only; `charset=bogus` falls back to detection |
| `ExtractionTiersTest` | plain JVM | R4 AC1–AC11 | Four JSON-LD shapes; microdata; RDFa; heuristic; invalid JSON-LD with microdata; partial tier; title-only fall-through both ways; carried title; `recipeInstructions` forms; `@id` image; normalisation (entities, tags, U+00A0, U+2009 kept, `•` kept, empty dropped); malformed HTML gives a draft. Caps: 201 ingredient lines (200 kept), a 4 097-character line (dropped; a 4 096-character line kept), 1 001 array elements (first 1 000 examined), a 513-deep nested page carrying a recipe (skipped, falls through) and a 512-deep one (read). Rules: nested `itemscope` ownership (a `NutritionInformation` `name` is not the title), heuristic list stops at the next same-or-higher heading, RDFa `vocab` and `schema:` prefixes with `meta`/`time`/`link` value rules, `HowToTip` skipped, an `@id` image resolved one hop only. Density: a page of about 199 999 tiny elements parses and its node count (elements plus text nodes) stays under 400 000 (`CappedInputStreamTest` covers the stream; this covers DR1) |
| `DeclineTest` | plain JVM | R4 AC11; R5 AC1, AC3 | Nothing-extracted gives `NOT_RECOGNISED` and no image request; parse-badly (no `h1`, no `og:title`, no `<title>`, no method) gives a partial draft; completeness for all five partial shapes |
| `DraftProvenanceTest` | plain JVM | R6 AC1–AC6; CFC-2 | Redirect once: `sourceUrl` is the passed URL. `MutableClock` instants A, B, C. Durations including `PT30S` → 1, `PT29S` → absent, `PT0M`, `P1DT2H`, `""`, `about an hour`, the `cookTime` fallback on zero and on unparseable `totalTime`. Yields including `0`. Heuristic never states time or yield even when present. Yield edges: `Int.MAX_VALUE` stated, `Int.MAX_VALUE + 1` absent, `4.0`, `-1`, `"Serves: 4"`, `1000` (stated), `0` (absent). Duration edges: `PT99999H` absent (digit limit), `pt30m` stated, `PT0.5S`, `PT1,5S`, `PT90S` → 2 (half up). Source check: over the property and constructor-parameter declarations in `ImportDraft.kt` only (not KDoc, comments or `?:`), no declared type ends in `?` |
| `ImageValidatorTest` | Robolectric, NATIVE | R7 AC4 | JPEG and PNG valid; garbage and HTML bytes give `NOT_AN_IMAGE`; `pngBomb(9_000, 9_000)` under the 1 GiB heap, plus `decodeObserver` assertions that `sampleSize` is exactly 16 for 9 000 px (`computeSampleSize` halves while `longest / (2 × sampleSize) ≥ maxEdgePx`) and the expected decoded longest edge, 562, is below `2 × maxEdgePx` (F1's rule gives an edge in `[maxEdgePx, 2 × maxEdgePx)`, so it is never asserted ≤ `maxEdgePx`) (NATIVE bitmaps live outside `-Xmx`, so the heap check alone proves nothing). Ceiling boundary with `pngHeaderOnly`: 10 000 × 10 000 calls the observer (the result is left open: a truncated PNG may decode partially), 10 001 × 10 000 and 65 535 × 65 535 give `NOT_AN_IMAGE` with the observer never called; the `OutOfMemoryError` branch is accepted as untested (it cannot be driven without a fake decoder) |
| `ImageSelectionTest` | Robolectric, NATIVE | R7 AC1–AC3, AC5, AC6 | Each tier and form; largest declared and first-undeclared `img`; `<base href>` and relative; JPEG fetched once, bytes on the draft, no file under `filesDir`; HTML labelled `image/jpeg`: one image request, parser seam count unchanged; gzip bomb and `br` on the image; JPEG labelled `text/plain` accepted; image 404 and `data:` give no image with no lower tier tried; a title-only JSON-LD `Recipe` still supplies the tier-1 image; microdata `itemprop="image"` is ignored; a blank `og:image` content or blank `absUrl` gives `NO_CANDIDATE`; an unencoded space and a `|` in a JSON-LD image string give `NoImage(BAD_URL)` with the draft still produced; image over-cap and corrupt gzip: the image stream's `onStream` count shows `produced ≤ cap + 8192` (image `OTHER` is rejected before any stream opens, so only the no-image result is asserted for it); `ImageValidator`'s `decodeObserver` shows `sampleSize > 1` and decoded dimensions below `2 × maxEdgePx` for the PNG bomb |
| `ImportErrorHygieneTest` | Robolectric, NATIVE | R9 AC1–AC4; CFC-4 | Every failure path with `Sentinels.URL`, sentinel HTML, JSON-LD, lines, a truncated gzip and a non-image body: `assertNoSentinel()`, `cause == null`, `suppressed` empty. `importFailure` over jsoup `ValidationException(SENTINEL)`, `SerializationException(SENTINEL)` and `ZipException(SENTINEL)`: kind by type, frames copied. Every `ImportMessages.ALL` string contains no sentinel or `<`, and none contains `http` except `import_error_invalid_url`, which must name `http://` and `https://` (R1 AC4). `UrlCheck.Invalid` for a sentinel userinfo URL prints no sentinel. `toString()` of every type AD8 lists, including `ExtractedRecipe`, `Extraction`, `JsonLdScan`, `Candidate.Url` and `DraftValue.Stated` (and `ValidatedUrl`, `PickedUrl`, `UrlCheck.Valid`, `Screen.Declined` and `ImportUiState` holding a sentinel URL), prints no sentinel |
| `ImportSourceGuardTest` | plain JVM | R2 AC1 (F5 side); R9 AC2; R10 AC4; AD16 | Uses `RepoPaths.repoRoot()` over both import packages |
| `HarvestedCorpusTest` | plain JVM | R10 AC1, AC2; CFC-1 | ≥ 20 `F5_HARVESTED` `INGREDIENT` rows; ≥ 3 hosts; no `line` equal to a `HAND_TRANSCRIBED` line; each row has an index entry and snapshot; each `line` is byte-identical to a line F5's extraction (reader plus extractor) produced from that snapshot; no tab, line break or leading `#`; each snapshot is ≤ 1 MiB, carries the AD17 trim comment, decodes as UTF-8, and `(source_url, retrieved)` is unique across the index. On failure it lists the snapshot's extracted lines (test output only) |
| `ParseFidelityCorpusTest` (F3, unmodified) | plain JVM | R10 AC3 | Runs over the extended corpus |
| `ImportScreenTest` | Robolectric + Compose, `ImportTestApplication` | R1 AC2, AC3, AC6 (cold start, `onNewIntent` via Robolectric's `ActivityController.newIntent`, rotation via `recreate()`); R2 AC6; R5 AC2 (same activity instance); R8 AC1, AC2 (`shadowOf(activity).nextStartedActivity`); R11 | Live-region semantics on notice, status, error, draft-produced and decline text; draft-produced notice wording for the six shapes (`Complete` → `import_notice_recipe_found`, the five partial shapes → `import_notice_partly_*`); labels; touch bounds ≥ 48dp; `@Config(qualifiers = "land")` in one run, and in another font scale 2.0 set by `RuntimeEnvironment.setFontScale(2.0f)` before the activity starts (the two are separate runs, because a qualifier cannot set font scale). On the field, error text, Cancel, Retry, Share link, Try another link and Import another: `performScrollTo().assertIsDisplayed()` plus an unclipped check (node bounds inside the viewport, no `maxLines` truncation of any message). MockWebServer request count 0 after every share |
| `ImportFlowTest` | plain JVM, `Dispatchers.setMain(StandardTestDispatcher())` | R1 AC1 and AC4 (request count 0 for all eight rejected inputs; the importer is called with exactly the trimmed URL); R2 AC2 Retry; R5 AC5 | Real `ImportViewModel` over the real `RecipeImporter` and a `MockWebServer`. Retry's request-count delta ([SEAL-24]) is asserted here. Uses the shared `awaitState` helper with a timeout, never sleeps. Also the stalled-parse cancel and a maximal-density page (`seams.onParse` stall, `ensureActive` stops it) |
| `ShareTargetManifestTest` | Robolectric | AD4; `[SEAL-14]` | `PackageManager.queryIntentActivities(SEND text/plain)` resolves `MainActivity`; its `launchMode` is `LAUNCH_SINGLE_TASK` and its `taskAffinity` `isNullOrEmpty()` (the platform parser reports `""` as null); a launcher `ACTION_MAIN` start calls `onShareReceived` zero times and shows no notice, a start with `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` skips the share read, and an `onNewIntent` re-delivery of a non-SEND intent calls nothing (these three in `ImportScreenTest`); `BackHandler` is enabled in In progress, Failed and Declined only |
| `ImportMessagesTest` | Robolectric | R5 AC4; R2 AC4, AC5 | The decline and unreadable strings differ from every other; not-HTML ≠ unsupported-encoding; no-link ≠ invalid-URL. Message mapping: HTTP statuses 401, 402, 403, 404, 499, 500, 599 and 600 map to the Q6 message rows; gateway `RESPONSE_TOO_LARGE` shows the too-large message. The Retry predicate itself (Retry for 500–599, `TIMEOUT` and `CONNECTION_FAILED` only) is tested at the same boundaries in `ImportViewModelTest`, where the result mapping lives |
| `SingleCallSiteTest` (F4, modified) | plain JVM | R2 AC1 | Forbidden list gains `Jsoup.connect` and `org.jsoup.helper.HttpConnection` |
| `AppContainerTest` (modified) | Robolectric | `[SEAL-37]` | `container.recipeImporter` is one instance; `recipeImporter.gateway === container.gateway` |
| `FirstPaintNotBlockedTest` (F2, modified) | Robolectric | F2 R6 | Renamed to "import screen is displayed while a dataset read is blocked"; still `onNodeWithText("Pantry")` exactly once |

## File Structure

```
pantry/                                                         (repository root)
├── gradle/libs.versions.toml                                   — MODIFIED (FC9): jsoup 1.18.3, kotlinx-serialization-json 1.6.3, lifecycle-viewmodel-compose 2.8.7
└── app/
    ├── build.gradle.kts                                        — MODIFIED (FC9): three implementation lines via catalog aliases
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                             — MODIFIED (FC9): launchMode="singleTask", taskAffinity=""; SEND/DEFAULT/text/plain intent-filter on MainActivity
        │   ├── res/values/strings.xml                          — MODIFIED (FC8): every Error Handling resource
        │   └── java/ie/pantry/
        │       ├── recipeimport/                               — NEW package (C1)
        │       │   ├── ImportUrl.kt                            — FC1: pick, check, ValidatedUrl, UrlCheck
        │       │   ├── ImportResult.kt                         — FC2: ImportResult, ImportException, importError/fetchFailure/importFailure, ContentTooLargeException
        │       │   ├── ImportDraft.kt                          — FC3: ImportDraft, DraftValue, DraftImage, NoImageCause, ExtractionTier, Completeness
        │       │   ├── BoundedHtmlReader.kt                    — FC4: media type, charset, openBounded, CappedInputStream, parse
        │       │   ├── RecipeExtractor.kt                      — FC5: tier order, qualify rule, title carry-over
        │       │   ├── JsonLdExtractor.kt                      — FC5: block scan (20), JsonDepth, walk, Recipe mapping
        │       │   ├── MicrodataExtractor.kt                   — FC5: microdata then RDFa
        │       │   ├── HeuristicExtractor.kt                   — FC5: heading + list rules, heuristic title
        │       │   ├── LineText.kt                             — FC5: Q8 normalisation
        │       │   ├── RecipeValues.kt                         — FC5: ISO duration, yield
        │       │   ├── ImageCandidateSelector.kt               — FC6: three-tier candidate, resolution
        │       │   ├── ImageValidator.kt                       — FC6: bounds-first decode
        │       │   └── RecipeImporter.kt                       — FC7: ImportPipeline, RecipeImporter, ImportPhase, ImportSeams
        │       ├── ui/recipeimport/                            — NEW package (C9)
        │       │   ├── ImportViewModel.kt                      — FC8: state machine
        │       │   ├── ImportUiState.kt                        — FC8: ImportUiState, Screen, Failure, Notice
        │       │   ├── ImportScreen.kt                         — FC8: composables, live regions, BackHandler
        │       │   ├── ImportMessages.kt                       — FC8: state → @StringRes, ALL
        │       │   └── ShareIntents.kt                         — FC8: sharedText, chooserFor
        │       ├── ui/MainActivity.kt                          — MODIFIED (FC9): ViewModel, ImportScreen content, onCreate/onNewIntent share handling
        │       └── di/AppContainer.kt                          — MODIFIED (FC9): clock param + val; recipeImporter val
        └── test/
            ├── java/ie/pantry/
            │   ├── recipeimport/                               — NEW (FC10)
            │   │   ├── ImportFixtures.kt                       — fixture loading, gzip, bomb, deep JSON-LD, importer builder
            │   │   ├── ImportUrlTest.kt, RecipeImporterFetchTest.kt, CappedInputStreamTest.kt, ContentBoundsTest.kt, ExtractionTiersTest.kt,
            │   │   ├── DeclineTest.kt, DraftProvenanceTest.kt, ImageValidatorTest.kt, ImageSelectionTest.kt,
            │   │   └── ImportErrorHygieneTest.kt, ImportSourceGuardTest.kt, HarvestedCorpusTest.kt
            │   ├── ui/recipeimport/                            — NEW (FC10)
            │   │   ├── ImportTestApplication.kt, FakePipeline.kt
            │   │   └── ImportViewModelTest.kt, ImportFlowTest.kt, ImportScreenTest.kt, ShareIntentsTest.kt, ShareTargetManifestTest.kt, ImportMessagesTest.kt
            │   ├── testutil/MutableClock.kt                    — MODIFIED: @Volatile on now
            │   ├── testutil/ImageFixtures.kt                   — MODIFIED: add pngHeaderOnly(width, height)
            │   ├── data/gateway/SingleCallSiteTest.kt          — MODIFIED (Ask First): forbidden list += Jsoup.connect, org.jsoup.helper.HttpConnection
            │   ├── di/AppContainerTest.kt                      — MODIFIED (Ask First, additive): recipeImporter identity and gateway sharing
            │   └── ui/FirstPaintNotBlockedTest.kt              — MODIFIED (Ask First): placeholder wording → import screen
            └── resources/
                ├── recipeimport/pages/*.html                   — NEW: JSON-LD (4 shapes, @id image, instruction forms), microdata, RDFa, heuristic,
                │                                                 parse-badly, nothing-extracted, invalid-JSON-LD, malformed, windows-1252 (×2), image-tier pages, sentinel page, structured-with-no-ingredients (R4 AC7/AC8), carried-title, 21-block, scripts/handlers/iframes (R3 AC4), `<base href>` relative image
                ├── recipeimport/harvest/snapshots.tsv          — NEW: DM11 index
                ├── recipeimport/harvest/<host>/<slug>.html     — NEW: ≥ 3 hosts, trimmed per AD17
                └── ingredient/parse_fidelity_corpus.tsv        — MODIFIED: ≥ 20 appended F5_HARVESTED rows
```

Not changed: any `ie.pantry.data.gateway` main source, `PantryDatabase`, entities, DAOs, `RecipeRepository`, `ThumbnailProcessor`, `ThumbnailStore`, `app/schemas/`, `app/src/main/assets/`, any `ie.pantry.domain.ingredient` source, and `PantryApplication`.

## Dependencies

| Package | Purpose |
|---------|---------|
| `org.jsoup:jsoup:1.18.3` (`implementation`, alias `libs.jsoup`) | HTML parsing, microdata/RDFa and heuristic traversal, charset detection (ARCHITECTURE Technology Choices). MIT licence. Already in `.toolchain/gradle-home`. `Jsoup.connect` is banned (AD16) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3` (`implementation`, alias `libs.kotlinx.serialization.json`) | `Json.parseToJsonElement` for JSON-LD (Technology Choices). Apache 2.0. Pinned to 1.6.3 (user-confirmed, design Q4): its jars are in `.toolchain/gradle-home`, whereas 1.7.3 has only its `.module`/`.pom` cached, so it would need a one-off online fetch. `Json.parseToJsonElement` and the `JsonElement` tree exist unchanged in 1.6.3. No compiler plugin: only the `JsonElement` tree is used, never `@Serializable` |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7` (`implementation`, alias `libs.androidx.lifecycle.viewmodel.compose`) | `ViewModel`, `viewModelScope`, `viewModelFactory` for the MVVM ViewModel (ARCHITECTURE App architecture). Apache 2.0. Cached. `viewModels()` comes from `activity-ktx`, which `activity-compose` 1.9.3 already brings |
| Existing: OkHttp/MockWebServer 4.12.0 (via `TestGateways`), coroutines 1.9.0, Robolectric 4.14.1, Compose BOM 2024.12.01 and `ui-test-junit4` | Unchanged |

No `lifecycle-runtime-compose` (`collectAsState` from Compose runtime is enough), and no fourth artifact (spec Ask First).

## Integration Points

| Existing Module | Direction | Change Required | Details |
|-----------------|-----------|-----------------|---------|
| `ie.pantry.data.gateway.ExternalDataGateway` | Calls into | No | `fetchPage(url.value)` once, `fetchImage(candidate)` at most once. Exhaustive `when` over `GatewayResult` and, in `ImportMessages`, over `GatewayException.Category` |
| `ie.pantry.data.gateway.FetchedBody` / `BodyEncoding` | Calls into | No | `bytes` treated as still encoded; `mediaType` checked first (AD7) |
| `ie.pantry.data.thumbnail.computeSampleSize` | Calls into | No | Pure, `internal`, same module (AD5). No `ThumbnailProcessor` or `ThumbnailStore` call |
| `ie.pantry.di.AppContainer` | Holds | Yes (FC9) | Last two parameters `clock: Clock = Clock.systemUTC()` and `recipeImporter: RecipeImporter` (defaulted, so a test can supply its own). The 3-, 4- and 5-argument call sites in `AppContainerTest`, `BlockingReadPantryApplication` and `RecordingPantryApplication` compile unchanged |
| `ie.pantry.ui.MainActivity` | Called by (platform) / calls into | Yes (FC9) | Content becomes `ImportScreen`; share intents per AD4. Load-bearing: it is the launcher and the first-paint subject of F2's test |
| `app/src/main/AndroidManifest.xml` | Config | Yes (FC9) | `launchMode`, `taskAffinity` and the `SEND` filter only. Still one `uses-permission` (`ManifestPolicyTest`, the release-manifest grep) |
| `SingleCallSiteTest` (F4) | Test edit | Yes — Ask First | Two forbidden tokens added; nothing removed |
| `AppContainerTest` | Test edit | Yes — Ask First | Additive only: asserts `recipeImporter` identity and gateway sharing; no existing assertion changes |
| `FirstPaintNotBlockedTest` (F2) | Test edit | Yes — Ask First | Wording only. The F2 `01_spec.md` R6 wording amendment that the spec names is a separate spec edit, not part of this design's files |
| `ie.pantry.testutil.TestGateways`, `Sentinels`, `assertNoSentinel()`, `ImageFixtures`, `RepoPaths`, `ParseFidelityCorpus` | Test-only calls into | No | Used as they are, except `ImageFixtures` gains the test-only `pngHeaderOnly(width, height)` helper |
| `ie.pantry.testutil.MutableClock` | Test-only | Yes | `@Volatile` on `now` |
| F6 recipe form (future) | Calls into | No (F6's spec) | Observes `ImportUiState.screen` `Drafted(draft)` and takes `ImportDraft` as its initial state; parses `ingredientLines` via C4 on confirm; hands `DraftImage.Validated.bytes` to C3. Must revisit share-during-draft (`[SEAL-50]`) and image-phase cancel (AD18). The shape of the F5-to-F6 draft hand-off (observing `ImportUiState`, a one-shot event or a container-held holder) is deliberately left to F6's design |

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| DR1 | A 10 MiB page builds a jsoup DOM far larger than the source on a low-end device: about 5–10× for ordinary markup (50–100 MB), and worse for dense tag soup (millions of tiny elements at roughly 100–200 bytes each), so an import can throw `OutOfMemoryError` and crash | Med | High | Q1 (user-confirmed) adds a markup-density bound in `CappedInputStream`: at most 200 000 `<` bytes (AD7). Q5 caps a JSON-LD block at 512 KiB (AD2), and Q6 caps DOM walk depth at 512 (FC5). Only the mapped recipe and image string outlive a block (AD2). The `Document` is dropped before the image fetch (FC7). Ordinary recipe pages are 0.2–1.5 MB |
| DR2 | `singleTask` behaviour on a real cross-task share differs from Robolectric's direct `newIntent` | Med | Med | `ShareTargetManifestTest` pins the manifest. Implementation Sequence step 9 shares from a browser on a device, cold and while an import runs |
| DR3 | The empty-then-text live-region trick does not make TalkBack repeat an identical notice on every device | Med | Low | Manual TalkBack check (step 9). Fallback: alternate between two constant wordings of the same notice |
| DR4 | Harvesting needs a networked machine; the devcontainer has none | High | Med | The user saves snapshots on a networked machine (step 5), early, so RK3 parser defects surface before the screen work |
| DR5 | Harvested lines expose F3 parser defects that block `ParseFidelityCorpusTest` (spec RK3) | Med | Med | Step 5 runs F3's engine over candidate lines first; Ask First on any engine change; never edit expected values from engine output |
| DR6 | jsoup reads `<meta charset>` only in the first 5 KiB, so a late declaration gives mojibake lines | Low | Med | No crash; the lines are visibly wrong in F6's form. A header charset, when present, wins (AD6) |
| DR7 | A shared text near the binder limit (about 1 MB) fills the field with a very long over-limit URL, and the `TextField` lags | Low | Low | The spec forbids truncating the field. The Import tap rejects it at once from the first 2048 characters |
| DR8 | A lazy-loading page's first `<img>` is a `data:` placeholder or a 1×1 tracker, so the inline tier gives no image or a useless one | Med | Low | The declared-size rule prefers real images when any declares a size. Q10 accepts no fall-through. F6's photo picker can attach a picture |
| DR9 | A future jsoup or kotlinx upgrade wraps or words exceptions differently | Low | Med | Classification is by type over the whole cause chain (FC2). `ImportErrorHygieneTest` fails loudly |
| DR10 | Cancel during a CPU-bound extraction leaves a background parse running for a moment | Med | Low | The UI updates at once (AD13). `CappedInputStream` and each tier boundary check `ensureActive()` |
| DR11 | `sourceUrl` keeps a token in its query string (`[SEAL-17]`) | Low | Med | Userinfo is rejected (AD11). Query tokens are not stripped: that would change the URL the user chose. Disclosed in Non-Goals |
| DR12 | An image candidate URL with userinfo is not rejected as the page URL is (AD11) | Low | Low | F4's address policy and OkHttp's request construction (which sends no userinfo) cover it; accepted, synthesizer-judged |
| DR13 | `getCharSequenceExtra` unparcels the whole extras bundle of an attacker-controlled intent on the exported activity | Low | Low | AD4's `RuntimeException` catch counts a failed unparcel as missing text; no other extra is read; platform deserialisation limits apply. Accepted, synthesizer-judged |
| DR14 | A hostile 512 KiB JSON-LD block holds tens of thousands of tiny `Recipe` nodes, so retained `ExtractedRecipe`s across 20 blocks can still be large | Low | Med | The 512 KiB block cap and 20-block limit; the trees are dropped per block. Accepted, synthesizer-judged |
| DR15 | No wall-clock budget on the CPU phase (parse, extract, native decode), and jsoup's tree building can be superlinear on nested input; only the user's Cancel stops it | Low | Med | Size, density, depth and pixel bounds; Cancel at any time (AD13). Accepted, synthesizer-judged |

## Implementation Sequence

1. **Build setup.** Add the three catalog aliases and `implementation` lines (jsoup 1.18.3, kotlinx-serialization-json 1.6.3 and lifecycle-viewmodel-compose 2.8.7 jars are in `.toolchain/gradle-home`; run one offline `./gradlew :app:dependencies --offline` here to prove it before going further). Capture the pre-F5 release merged manifest for the closeout diff.
2. **FC1, FC2 and FC3** with `ImportUrlTest`, the unit half of `ImportErrorHygieneTest`, and `RecipeValues` (part of FC5) with its `DraftProvenanceTest` cases. These are pure Kotlin and can be built in parallel.
3. **FC4 bounded reader** with `ContentBoundsTest`'s bomb, charset and `JsonDepth` cases. This retires RK4 and records design Q1's one-off heap observation before extraction is built on it.
4. **FC5 extraction** with `ExtractionTiersTest` and `DeclineTest`, working on parsed fixtures directly. This is the largest and least certain component.
5. **Harvest dry run** (parallel with step 4's later half). The user saves snapshots from at least 3 hosts (DR4), trims them per AD17 and fills `snapshots.tsv`. Run F3's engine over candidate lines to surface parser defects early (DR5).
6. **FC6 image selection and validation**, with `ImageValidatorTest` and the selector half of `ImageSelectionTest`.
7. **FC7 importer** with `RecipeImporterFetchTest`, the rest of `ImageSelectionTest`, `DraftProvenanceTest` and `ImportErrorHygieneTest`. Then `HarvestedCorpusTest` and the appended corpus rows, with F3's `ParseFidelityCorpusTest`.
8. **FC8 screen and FC9 wiring** — ViewModel first (`ImportViewModelTest`), then the composables, strings, `MainActivity`, manifest and `AppContainer`, then `ImportScreenTest`, `ShareIntentsTest`, `ShareTargetManifestTest`, `ImportMessagesTest`, and the Ask First edits to `SingleCallSiteTest`, `FirstPaintNotBlockedTest` and `AppContainerTest`. `ImportSourceGuardTest` last.
9. **Closeout.** The spec's Commands (F5 filter, full suite, `lintDebug`, release-manifest grep); a diff of the release manifest's component lines against step 1 (only `MainActivity`'s `launchMode`, `taskAffinity` and filter change); and a device check with TalkBack: share from a browser on a cold start and during an import, repeat a share, rotate, and use Cancel and Back (DR2, DR3). Pass criteria: a cold and a warm share from a browser each leave exactly one Pantry task in Recents with the field filled and no fetch started; TalkBack speaks "Link received. Tap Import to import it." on each share, including a repeat of the same share; sharing during an import speaks the cancelled-and-received message once; Back during an import returns to the input state; rotation changes nothing.

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: DOM memory cost of the 10 MiB cap (spec Project Structure; `[SEAL-22]`). A dense 10 MiB page can build a DOM of hundreds of MB, more than a low-end device's heap (DR1). **[User-confirmed]** Add a markup-density bound: `CappedInputStream` also counts `<` bytes and stops at 200 000 (about 10× a typical recipe page's element count), giving `CONTENT_TOO_LARGE`. This is a new bound, so it is Ask First under the spec's Boundaries. The user confirmed adding it with the value 200 000. The repeatable check is the maximal-density page test in `ExtractionTiersTest`/`ImportFlowTest`; step 3 also records, as a supplementary one-off manual observation, the retained heap of a 10 MiB ordinary page.
  - **Resolution:** Confirmed: add the bound at 200 000 `<` bytes, page stream only.
- [x] Q4: Which `kotlinx-serialization-json` version? 1.7.3 has no cached jar offline. **[User-confirmed]** Pin 1.6.3, whose jars are cached (Dependencies).
  - **Resolution:** 1.6.3.
- [x] Q5: JSON-LD block size. A flat gzip-delivered block has depth 1 but builds a huge `JsonElement` tree. **[User-confirmed]** Skip any block over 512 KiB before the pre-scan (AD2). This is a new bound under the spec's Ask First.
  - **Resolution:** 512 KiB per block.
- [x] Q6: Further new bounds proposed by the panel. **[User-confirmed]** Adopt all three: DOM walk depth 512 with cancellation checks every 1 024 nodes (FC5); draft caps of 200 lines, 200 steps and 4 096 characters per item (AD9); an image pixel ceiling of 100 000 000 (FC6). New bounds under the spec's Ask First.
  - **Resolution:** All three adopted as stated.
- [x] Q7: `android:taskAffinity`. `singleTask` on an exported launcher with the default affinity invites task hijack. **[User-confirmed]** Add `android:taskAffinity=""` to `MainActivity` (a manifest change beyond the spec's Ask First allowance, approved here).
  - **Resolution:** Added (AD4, FC9).
- [x] Q2: `HowToSection` names. AD9 flattens sections and drops each section's `name` (for example "For the sauce"), so steps from two sections run together with no heading. **[User-confirmed]** Drop them: the name is not a step, and F6's form has no section concept.
  - **Resolution:** Section names dropped.
- [x] Q3: Message copy. The Error Handling table fixes every constant string (Q6 fixes which messages exist; the wording is Design copy per `[SEAL-16]` and `[SEAL-25]`). **[User-confirmed]** As written; any later edit keeps the distinctness rules in `ImportMessagesTest`.
  - **Resolution:** Copy kept as written.

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

| Pass | Date       | HIGHs | Regressions | Addressed | Deferred | Sealed | Notes                           |
|------|------------|-------|-------------|-----------|----------|--------|---------------------------------|
| 1    | 2026-10-05 | 4     | 1           | 42        | 0        | 1      | tags=d0u0c4                     |
| 2    | 2026-10-05 | 2     | 1           | 27        | 0        | 2      | tags=d0u0c2                     |
| 3    | 2026-10-05 | 1     | 0           | 11        | 0        | 8      | tags=d0u0c1                     |
| 4    | 2026-10-05 | 0     | 0           | 0         | 0        | 7      | converged (0 HIGH); tags=d0u0c0 |

### Sealed dispositions

- `[SEAL-01]` **Image candidate URL not checked for userinfo** (pass 1, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; F4's address policy and OkHttp's request construction cover it; recorded as DR12 in 02_design.md.
- `[SEAL-02]` **Design Sealed label SEAL-01 collides with the spec's SEAL-01** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; labels are assigned by archive_pass.py per artifact, and design text refers to that item as DR12, so the bare label is not used in the design body.
- `[SEAL-03]` **getCharSequenceExtra unparcels an attacker-controlled…** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; AD4's RuntimeException catch treats a failed unparcel as missing text and no other extra is read; recorded as DR13 in 02_design.md.
- `[SEAL-04]` **ImportFlowTest stalled-parse observable: late results are…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable only after the next pass; the observable (captured stream produced count, or the importer coroutine ending cancelled) is a test-detail choice for 03_tasks.md.
- `[SEAL-05]` **Low-entropy image fixture not constructible under FAST's…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; CappedInputStreamTest already covers the stream-level case and the fixture construction (gzip delivery or a FAST.copy limit) is a 03_tasks.md detail.
- `[SEAL-06]` **Yield and duration edge cases lack expected outcomes** (pass 3, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; AD10 defines the rules and the per-edge expected values are written with the fixtures in 03_tasks.md.
- `[SEAL-07]` **BackHandler enabled-states has no named assertion mechanism** (pass 3, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the mechanism (for example the onBackPressedDispatcher's enabled callbacks per state) is a test-detail choice for 03_tasks.md.
- `[SEAL-08]` **jsoup tree-building cost bounded only by the density cap;…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; recorded as DR15; the density, size and depth bounds and the user's Cancel limit it, and the maximal-density fixture shape is a 03_tasks.md detail.
- `[SEAL-09]` **No wall-clock budget on the CPU phase** (pass 3, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; recorded as DR15; a deadline would be a new bound under the spec's Ask First and the user can request it at the approval gate.
- `[SEAL-10]` **ImportSourceGuardTest exempts check( call sites but not the…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the exemption covers the ImportUrl.check name and the exact rule is fixed when the guard is written in 03_tasks.md.
- `[SEAL-11]` **SingleCallSiteTest scans comments, so an F5 KDoc mention of…** (pass 3, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; an authoring note for 03_tasks.md that F5 KDoc avoids those tokens.
- `[SEAL-12]` **Density test ceiling of 400 000 nodes is fixture-shape…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass (no HIGH), so MED/LOW are accepted rather than fixed; the exact ceiling and page shape are fixture details for 03_tasks.md.
- `[SEAL-13]` **ContentBoundsTest row omits the importer-level depth 1 000…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass (no HIGH), so MED/LOW are accepted rather than fixed; the Fixtures paragraph is authoritative for depths and 03_tasks.md aligns the test row.
- `[SEAL-14]` **ContentBoundsTest row does not say which depths run at…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass (no HIGH), so MED/LOW are accepted rather than fixed; same alignment, in 03_tasks.md.
- `[SEAL-15]` **DOM depth fixture chain-length arithmetic left to the…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass (no HIGH), so MED/LOW are accepted rather than fixed; fixture construction is a 03_tasks.md detail.
- `[SEAL-16]` **10 000 x 10 000 boundary case asserts only the observer call** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass (no HIGH), so MED/LOW are accepted rather than fixed; the 10 001 x 10 000 reject side pins the ceiling.
- `[SEAL-17]` **4 096 boundary lacks a whitespace-padded…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass (no HIGH), so MED/LOW are accepted rather than fixed; added as a fixture in 03_tasks.md.
- `[SEAL-18]` **awaitState does not say how it obtains the…** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass (no HIGH), so MED/LOW are accepted rather than fixed; helper wiring is an implementation detail for 03_tasks.md.

### Deferred dispositions

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `c53f0923a7d6a4a1`
- **Hash basis:** v2
