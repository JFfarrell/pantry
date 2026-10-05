# Feature: Recipe Import Pipeline

**PLAN feature identifier:** `F5`

## Objective

**From PLAN F5:** `blueprint/03_PLAN.md` → `### F5: Recipe Import Pipeline` (Description, Component, Acceptance Criteria).

F5 is the first feature to pass untrusted page and image bytes from F4's transport boundary into Pantry's own code, so it alone owns the decompression, content-type, parse-depth and image-decode defences, and it defines the in-memory draft model that F6's form takes over.

## Requirements

### R1: Accept a recipe URL by share or paste

As a home cook reading a recipe in my browser, I want to share the page to Pantry or paste its link into Pantry, so that I can import it without retyping the link.

**Acceptance Criteria:**

- GIVEN the import screen with an empty URL field
  WHEN the user pastes `  https://example.ie/stew  ` (leading and trailing whitespace) and taps Import
  THEN the importer is called with exactly `https://example.ie/stew`, and the screen shows an in-progress state.

- GIVEN another app sends an `Intent.ACTION_SEND` with type `text/plain` whose `EXTRA_TEXT` is `Try this https://example.ie/stew so good`
  WHEN `MainActivity` receives it, either on a cold start (`onCreate`) or while already running (`onNewIntent`)
  THEN the URL field shows `https://example.ie/stew` and the screen is in the input state; no import starts and the MockWebServer request count stays zero until the user taps Import, which then imports exactly `https://example.ie/stew`. The first `http(s)` URL in the shared text is used, and nothing else from the shared text reaches the field or the importer. The share fills the field with the picked URL and runs no validation and no fetch. A constant message, exposed as a live region, announces that a link was received and the user should tap Import. **[User-directed — Q3]**

- GIVEN a shared `EXTRA_TEXT` that has no `http(s)` URL, or an `EXTRA_TEXT` that is missing
  WHEN `MainActivity` receives it
  THEN the import screen shows a plain "no link found in what you shared" message, distinct from the invalid-URL message, and makes no gateway call. An import that is already running is left running and the field is left unchanged. **[User-directed — Q3]**

- GIVEN each of the inputs `ftp://example.ie/a`, `intent://x#Intent;end`, `content://x/y`, `javascript:alert(1)`, `www.example.ie/stew` (no scheme), `   ` (blank), `https://` (no host) and `https://user:pw@example.ie/a` (userinfo)
  WHEN the user submits it
  THEN the import is rejected with the plain invalid-URL message before any gateway call is made. The MockWebServer request count is zero. A scheme-less input is rejected, not completed with a guessed `https://` prefix. The invalid-URL message states that the link must start with `http://` or `https://`. A URL carrying userinfo is rejected, so `sourceUrl` and the declined-URL share (R8) can never carry a credential. **[User-confirmed — Q4]**

- GIVEN the same URL-picking rule is applied to the URL field's content when the user taps Import and to shared text when a share arrives, and the text `(see https://example.ie/stew/).`, `https://example.ie/stew,` or `Try this https://example.ie/stew so good`
  WHEN the URL is picked
  THEN the URL is `https://example.ie/stew/`, `https://example.ie/stew` and `https://example.ie/stew` respectively: the first `http(s)` URL starts at the start of the text or right after whitespace or one of `(`, `[`, `<`, `"` and `'`, with a case-insensitive scheme (so `xhttps://a`, `url=https://a`, `ftp://x/?u=https://evil` and `intent://` fallback text do not match, and `HTTPS://example.ie/stew` and `<https://example.ie/stew>` do), and ends at whitespace or a closing quote or `>`. Trailing `.`, `,`, `;`, `:`, `!` and `?` are always dropped, and a trailing `)` or `]` is dropped only when unbalanced, so `https://example.ie/Foo_(bar)` keeps its closing bracket. The picker takes the first match and validation then rejects it if it carries userinfo, with no skipping to a later URL. Userinfo is detected as an `@` before the first `/`, `?`, `#` or `\` of the authority, on the same canonical form the gateway parses. Only the first 2048 characters of the text are scanned, and a URL that runs up to that limit is rejected as invalid rather than truncated and fetched; the field holds the full picked text, never truncated. A userinfo or over-limit URL shows the invalid-URL message only when the user taps Import, never on share arrival. A paste into the field is not rewritten, so the field keeps what was typed; after a successful Import tap the field shows the picked URL. On the Import tap, input with no `http(s)` match shows the invalid-URL message, not the no-link message, which is for shares only. **[User-confirmed — Q3, Q4]**

- GIVEN a share intent arrives while the screen is in the input state, in-progress, showing an error (the invalid-URL message, the no-link message, or a gateway failure showing Retry), showing a draft-produced notice, or showing the decline surface, and the user rotates the device or taps Import twice in quick succession
  WHEN each event is handled
  THEN a share carrying a link, arriving in the input, error, draft-produced or decline state, puts the picked URL in the URL field, clears any earlier error and its Retry, and shows the input state, and a share carrying a link during an in-progress import cancels that import and fills the field the same way. Each such share is announced by the live-region "link received, tap Import" message, and a share that cancels a running import is also announced as having cancelled it. A share with no link arriving outside the input state shows the no-link message and leaves the current state otherwise unchanged: a running import keeps running, and on the decline and draft-produced surfaces the no-link message overlays them and the state is unchanged. The asymmetry is deliberate: a share with no link is a failed attempt and must not destroy work in progress. No share causes a gateway request by itself: the request count rises only after the user taps Import. Rotation keeps the screen state and does not replay the share intent, and a second tap on Import while an import runs starts no second import. **[User-directed — Q3]**

### R2: Fetch through the gateway and accept only HTML

As the developer, I want every import fetch to go through F4's `ExternalDataGateway`, and only an HTML payload to reach the parser, so that the app keeps one auditable HTTP call site and never parses bytes it did not ask for.

**Acceptance Criteria:**

- GIVEN a valid URL served by a MockWebServer built with `TestGateways.against(server)`
  WHEN an import runs
  THEN the page is fetched through the container's single gateway instance, observed as exactly one request to the MockWebServer for the page path. No source file under the import packages imports `okhttp3`, calls `Jsoup.connect`, or uses any other HTTP API. `SingleCallSiteTest`'s forbidden list is extended with `Jsoup.connect` to assert this.

- GIVEN fixtures that make the gateway return `GatewayResult.Failed` for a timeout (`TIMEOUT`), a 404 (`HTTP_STATUS`, `statusCode` 404) and a refused private address (`ADDRESS_REFUSED`)
  WHEN an import runs against each
  THEN each import ends in a typed import error carrying the gateway's `GatewayException.Category`, never a hang or an exception escaping the importer. The import screen leaves the in-progress state and shows a plain, content-free message for that category. For the transient categories (`TIMEOUT`, `CONNECTION_FAILED` and a 5xx `HTTP_STATUS`) the screen also offers Retry, which imports the same URL again through one new `fetchPage` call. **[User-directed — Q6]**

- GIVEN a `200` response whose `Content-Type` is `application/json`, `image/png` or `application/pdf`, or which has no `Content-Type` header at all
  WHEN an import runs
  THEN it is rejected with the plain not-HTML error before any HTML or JSON-LD parse is attempted. A parser test seam records zero invocations. Only `text/html` and `application/xhtml+xml`, with any parameters, are accepted. **[User-confirmed — Q5]**

- GIVEN a `200` HTML response whose `FetchedBody.encoding` is `BodyEncoding.OTHER` (for example `Content-Encoding: br`)
  WHEN an import runs
  THEN it is rejected with the typed unsupported-encoding error before any parse is attempted. The media-type check runs before the encoding check, so a non-HTML response is always reported as not-HTML. Its message says the page uses a compression Pantry cannot read, and it differs from the not-HTML message.

- GIVEN a `200` HTML response served with `Content-Encoding: gzip` whose body is truncated or not valid gzip
  WHEN an import runs
  THEN it ends in the typed unreadable-content error, with a plain message distinct from every other import message, and no draft.

- GIVEN an import in progress against a server that never responds
  WHEN the user taps Cancel
  THEN the gateway call is cancelled, the screen returns to the URL input state at once with the URL still in the field, and no error and no draft is produced. This picks up the cancel affordance routed to F5 by F4's `[DEF-19]`.

### R3: Bound untrusted content while parsing

As the developer, I want decompressed size and JSON-LD nesting bounded while the page is being read and parsed, so that a hostile page cannot exhaust memory or the stack after passing F4's raw-byte cap.

**Acceptance Criteria:**

- GIVEN a gzip-bomb fixture: a body smaller than F4's 5 MiB raw cap, served with `Content-Encoding: gzip`, that decompresses to much more than the decompressed-content cap
  WHEN an import runs
  THEN decompression stops as soon as the cap is crossed. The bytes produced by the decompressing stream never exceed the cap plus one read buffer, as asserted by a counting stream seam, and the import ends in the typed content-too-large error with no draft. The cap is enforced on the decompressing stream as it is read, not by checking a fully inflated buffer afterwards. **[User-confirmed — Q7 for the cap value]**

- GIVEN deeply nested JSON-LD fixtures: one `<script type="application/ld+json">` block nested exactly one level deeper than the JSON-LD depth bound, one nested at the bound, and one nested far beyond it (including deeply nested `@graph` arrays)
  WHEN an import runs
  THEN the block one level over the bound and the far-deeper block are skipped without a `StackOverflowError` or any escaping exception, and extraction continues with the next block or tier as if the block were absent. The block at the bound is parsed. The extractor's own walk over a parsed block (`@graph`, arrays, nested objects) is bounded by the same depth value. **[User-confirmed — Q7 for the depth value]**

- GIVEN a page with 21 `<script type="application/ld+json">` blocks, where only the 21st carries a `Recipe`
  WHEN an import runs
  THEN the 21st block is not examined and the import falls through to the next tier.

- GIVEN a page containing `<script>` elements (other than JSON-LD data blocks), inline event handlers and `<iframe>` elements
  WHEN an import runs
  THEN no page script is executed and no sub-resource other than the single selected image (R7) is fetched. The MockWebServer request count is exactly the page request plus at most one image request.

### R4: Layered extraction into a draft

As a home cook, I want Pantry to use the best structured data a page offers, and to fall back to a best-effort guess, so that most pages give me a mostly correct draft to check.

**Acceptance Criteria:**

- GIVEN a page carrying a JSON-LD `Recipe`
  WHEN an import runs
  THEN it produces a draft with title, ingredient lines and method populated, and the draft records its extraction tier as JSON-LD. The fixtures cover a top-level `Recipe` object, an array containing a `Recipe`, a `@graph` containing a `Recipe`, and an `@type` given as an array that includes `"Recipe"`.

- GIVEN a page with only microdata
  WHEN an import runs
  THEN it produces a draft via the microdata path (the tier is recorded as microdata), with title, ingredient lines and method populated. Cooking time and yield on this tier follow the same rules as R6, read from the `itemprop` values `totalTime`, `cookTime` and `recipeYield`; a value that is missing or fails those rules is explicitly absent. The image is never read from `itemprop` on this tier: image selection always follows R7's three-tier order, whichever tier supplied the content.

- GIVEN a page carrying only RDFa `schema.org/Recipe` properties
  WHEN an import runs
  THEN it produces a draft via the same second tier as microdata, with the same field rules. **[ASSUMPTION — ARCHITECTURE C1 names "microdata/RDFa" as one tier]**

- GIVEN a page with neither JSON-LD nor microdata/RDFa, but with an identifiable ingredient list and method
  WHEN an import runs
  THEN it produces a heuristic draft (the tier is recorded as heuristic).

- GIVEN a page whose only JSON-LD block is syntactically invalid JSON, and which also carries microdata
  WHEN an import runs
  THEN the invalid block is skipped without an escaping exception and the draft comes from the microdata tier.

- GIVEN a structured tier yields a `Recipe` with at least one ingredient line or method step, but some other fields missing (for example no method)
  WHEN the draft is built
  THEN the draft takes every field except the image from that one tier (the image follows R7 page-wide), and the missing fields stay explicitly absent. No field is merged in from a lower tier, except the title carried across a fall-through (below). **[ASSUMPTION — Decision Point 3]**

- GIVEN a structured tier yields a `Recipe` with a title but neither an ingredient line nor a method step, and the page's heuristic tier does find an ingredient list
  WHEN an import runs
  THEN the structured result does not win: extraction falls through to the next tier, and the draft comes from the first tier that has an ingredient line or method step. **[User-directed — Q2]**

- GIVEN a structured tier yields a `Recipe` with a title but neither an ingredient line nor a method step, and the page's heuristic tier finds no ingredient list either
  WHEN an import runs
  THEN the page is declined with the not-recognised error, the same as a page with nothing extracted. **[User-directed — Q2]**

- GIVEN the heuristic tier
  WHEN it looks for a recipe
  THEN an ingredient list is a heading whose text matches "ingredient" (case-insensitive) followed by a list container (`ul`/`ol`) or a run of block elements holding at least 2 items, and a method is the same for "method", "directions", "instructions" or "steps". A heuristic draft requires an ingredient list; a method alone does not make a heuristic draft (user-directed). The heuristic title is the page's first `h1`, else `og:title`, else `<title>`, and is only used on a heuristic draft that already has an ingredient list. When the draft got here by falling through from a structured `Recipe` that stated a title, that structured title is kept instead, whichever lower tier (microdata, RDFa or heuristic) then supplies the draft, and the earliest tier that stated a title wins; this is the one field carried across tiers (user-directed). No other tier ever takes its title from the heuristic sources. **[User-directed — Q2]**

- GIVEN any extraction tier
  WHEN it produces ingredient lines
  THEN each line is the element's text content with HTML entities decoded, tags removed, internal whitespace (including U+00A0, the non-breaking space, which is treated as a space) collapsed and ends trimmed, and is otherwise unaltered: no quantity, unit or word is removed, rewritten or reordered, and list markers such as `•` and `-` are kept. Lines that are empty after normalisation are dropped. This is text extraction, not parsing: whitespace in HTML is not significant, and the quantity and key semantics stay with C4. This keeps CFC-1's "no route-specific pre-cleaning" true for the import route. **[User-confirmed — Q8]**

- GIVEN timeout, 404, non-HTML and malformed-HTML fixtures (the malformed page has unclosed tags and a truncated `<script type="application/ld+json">` but a heuristically identifiable ingredient list), plus every draft-producing fixture in R4 and R5
  WHEN each is imported
  THEN any page from which something is extracted, however incomplete, yields an editable draft (an `ImportDraft` that F6's form takes as its initial state), and no import path terminates without either an editable draft or a typed error. The malformed-HTML fixture yields a draft. The timeout, 404 and non-HTML fixtures each yield their typed error. No import ends in neither, in both, or in an escaping exception. The one exception is a user's Cancel (R2) or a share carrying a link (R1), which end the import by the user's choice with neither. This Cancel carve-out is a deviation from PLAN F5's universal wording, picked up from F4's `[DEF-19]`; PLAN F5's criterion should be trued up to read "other than a user's Cancel or a share carrying a link" at the next PLAN amendment. No empty draft is produced on an error or decline: PLAN permits "an editable draft or a typed error", and the hand-off from a decline to hand entry is F6's manual-entry route.

### R5: Decline a page with nothing extractable

As a home cook who shared the wrong page, I want a clear "not recognised as a recipe" message, and the ability to try another link at once, so that a non-recipe page is a quick detour rather than a dead end.

**Acceptance Criteria:**

- GIVEN a nothing-extracted fixture (an HTML article with a `<title>`, but no JSON-LD, no microdata/RDFa and no heuristically identifiable ingredient list) and a parse-badly fixture (a heuristic page that yields an ingredient list of at least 2 items but no title and no method)
  WHEN each is imported
  THEN a page from which *nothing* is extracted is declined with a clear, specific "not recognised as a recipe" error whose wording is distinguishable from the parse-badly path — asserted by a test over both fixture pages. Concretely, the first fixture ends in the typed not-recognised error and the decline surface, and the second ends in a draft with no error and with the "partly found" notice (below) in place of "Recipe found".

- GIVEN the decline surface, and separately the draft-produced state
  WHEN the user taps "Try another link" (decline) or "Import another" (draft-produced), enters a different valid URL and taps Import
  THEN the user can immediately try another URL without restarting the app. The follow-up import starts in the same activity instance with no restart and no back-stack reset.

- GIVEN a draft that has an ingredient line but is missing its title or method, and a draft that has title, ingredient lines and method
  WHEN the draft-produced notice is chosen
  THEN any draft missing at least one of title, ingredients and method shows a "partly found" notice naming which of the three were found, including a structured draft that has a method but no ingredients, and a complete draft shows "Recipe found". The two messages differ.

- GIVEN the decline message and every other import message (invalid URL, not HTML, unsupported encoding, unreadable content, content too large, each gateway-failure message, and the draft-produced notices)
  WHEN their string resources are compared
  THEN the decline message differs from each of them.

- GIVEN the decline surface is showing
  WHEN the user taps Retry
  THEN the same URL is imported again through one new `fetchPage` call.

### R6: Draft provenance and explicit absence

As the developer of F6, I want the draft to carry where and when it came from, and to mark every unstated optional field as absent, so that the form can persist an honest recipe without guessing.

**Acceptance Criteria:**

- GIVEN successful imports of a page that states a cooking time and a page that does not
  WHEN each draft is built
  THEN `sourceUrl` and `fetchedAt` are captured on the draft; cooking time is captured when the page states one and left explicitly absent otherwise, never defaulted. [CFC-2]

- GIVEN a successful import of `https://example.ie/stew`, which the server redirects once, with an injected `Clock` fixed at `2026-10-04T10:00:00Z`
  WHEN the draft is built
  THEN `sourceUrl` equals the validated URL the test passed (shown here as `https://example.ie/stew`; tests use the MockWebServer's own `http://127.0.0.1:<port>/...` URL), not the redirect target, and `fetchedAt` equals `2026-10-04T10:00:00Z`, the injected clock's instant read when the page fetch returned. **[User-confirmed — Q13]**

- GIVEN a JSON-LD `Recipe` with `"totalTime": "PT1H15M"`
  WHEN the draft is built
  THEN cooking time is stated as 75 minutes. Given `"cookTime": "PT40M"` and no `totalTime`, it is 40 minutes. **[User-confirmed — Q9]**

- GIVEN a JSON-LD `Recipe` with no time property, with `"totalTime": ""`, with `"totalTime": "PT0M"` or with an unparseable duration such as `"about an hour"`, and given any heuristic-tier draft, which never reads a cooking time even when the page states one (user-directed; PLAN F5's "captured when the page states one" should be trued up to read "on the structured tiers")
  WHEN the draft is built
  THEN cooking time is the explicit absent state, distinguishable from zero minutes. It is never `0`, `null`-as-zero or a default. `P1DT2H` gives absent, and `PT30S` rounds to the nearest whole minute and is absent if that is zero.

- GIVEN a `recipeYield` of `"4"`, the number `4`, `"Serves 4"` or `["4", "4 servings"]`, and separately a `recipeYield` that is missing, `"4-6"`, `"a crowd"`, `"2 dozen"` or `"12 cookies"`
  WHEN the draft is built
  THEN yield is stated as 4 servings in the first group and is the explicit absent state in the second group. It is never guessed from a range, from a quantity with a unit, or from other text; only a bare integer or an integer beside "serves", "servings" or "portions" counts. **[User-confirmed — Q9]**

- GIVEN every draft-producing fixture in R4 and R5
  WHEN its draft is inspected
  THEN every optional field (title, method, cooking time, yield, image) is either a stated value or an explicit absent state, the ingredient-line list is present, and an empty list is the explicit absence of ingredients, and the draft exposes no nullable field whose `null` would mean "absent". Every absent value this feature produces or renders is represented and displayed as an explicit absence state, distinguishable from a zero, a default and an empty value, and is never substituted with a guessed or placeholder value. [CFC-2]

### R7: Thumbnail image selection, fetch and validation

As a home cook, I want the recipe's own picture to come along with the import when the page offers one, and no picture otherwise, so that my catalogue is easy to scan and does not show a broken image. The picture is chosen by a fixed preference order and can occasionally be the wrong one. PLAN F6 commits only that the device photo picker can attach or replace a thumbnail and that the user can clear an optional field on post-save edit, so F5 builds nothing for it. PLAN F6 true-up note: removing the imported picture at draft-confirm time is not yet an F6 commitment.

**Acceptance Criteria:**

- GIVEN pages offering an image at each tier of ARCHITECTURE C1's preference order: a JSON-LD `Recipe.image` (as a string, an array whose first element is used, and an `ImageObject` with `url`), an `og:image` meta tag, and inline `<img>` elements only
  WHEN an import runs
  THEN exactly one candidate is chosen from the first tier that has a hit, whichever tier the content came from. The inline tier picks the `<img>` with the largest declared `width`×`height`, and the first in document order when none declares a size. A relative candidate URL is resolved against the page's `<base href>` if present, else against the supplied URL. **[User-confirmed — Q10]**

- GIVEN a page whose selected candidate serves a valid JPEG, and a page with no candidate at any tier
  WHEN each is imported
  THEN where the page offers a usable image under the preference order, it is fetched during the same import and carried on the draft as validated in-memory bytes — no file is written here, since the thumbnail file is produced by C3 when F6 persists the recipe; where none is available the draft carries an explicit no-image state. Image selection and fetch run only after extraction has produced a draft, so a declined page makes no image request. Concretely, the first import makes one request to the MockWebServer for the candidate image path and its draft carries those bytes, the second makes no image request, and neither creates a file under the test `filesDir`.

- GIVEN a selected candidate that serves HTML bytes labelled `image/jpeg` (a fetched-bytes fixture that isn't a valid image)
  WHEN an import runs
  THEN a candidate image whose fetched bytes fail to decode as a genuine raster image is treated as an explicit no-image state, the same as a page offering no image at all — never retried as HTML and never carried on the draft unvalidated. Concretely, exactly one image request was made, and the HTML/JSON-LD parser was not invoked on the image bytes.

- GIVEN a candidate image whose header declares dimensions far larger than the thumbnail size (a decompression-bomb-style image)
  WHEN it is validated
  THEN validation reads the bounds first and decodes at a computed sample size, never at native resolution, and completes within the test JVM's 1 GiB heap (`app/build.gradle.kts`'s `maxHeapSize`).

- GIVEN an image fetch that returns `Fetched` with `BodyEncoding.GZIP` whose body decompresses past the decompressed cap, and one with `BodyEncoding.OTHER`
  WHEN an import runs
  THEN the same streaming decompressed cap as R3 applies to the image bytes, and both cases give the explicit no-image state with the draft still produced. The image's `mediaType` is not trusted in either direction: only the decode check decides, so a valid JPEG labelled `text/plain` is accepted.

- GIVEN an image fetch that ends in `GatewayResult.Failed` (any category) or an image candidate whose scheme is not `http(s)` (for example a `data:` URI)
  WHEN an import runs
  THEN the import still yields its draft, with the explicit no-image state. No lower tier is tried, and the import does not end in an error. **[User-confirmed — Q10]**

### R8: Share or export a declined page's URL

As a home cook whose recipe page was declined, I want to send that link somewhere myself, so that I can report a possible misclassification or keep the link, without Pantry sending anything on its own.

**Acceptance Criteria:**

- GIVEN the decline surface for `https://example.ie/not-a-recipe`
  WHEN the user taps Share link
  THEN from a declined page the user can share or export that URL through the system share sheet: the app starts an `Intent.createChooser` wrapping an `ACTION_SEND` of type `text/plain` whose `EXTRA_TEXT` is exactly that URL, as asserted with Robolectric's started-activity shadow. The decline surface also shows a constant line saying that sharing the link is how the user can tell whoever they choose that Pantry did not recognise the page, and that Pantry itself sends nothing.

- GIVEN the decline surface, before and after the user taps Share link
  WHEN the MockWebServer request count and the started intents are inspected
  THEN the app itself transmits nothing about the decline on its own: the decline caused no request beyond the original page fetch and no started intent, and the only outbound action is the chooser that the user's tap started.

### R9: No URL, HTML or ingredient line in any error

As the developer, I want every error built by the import pipeline to be content-free, so that a crash report or log can never carry a recipe URL, page content or ingredient text.

**Acceptance Criteria:**

- GIVEN content-bearing fixtures using `ie.pantry.testutil.Sentinels`: an import of `Sentinels.URL`, a page whose HTML, JSON-LD and ingredient lines contain `Sentinels.TITLE` and `Sentinels.INGREDIENT`, an invalid JSON-LD block containing `Sentinels.INGREDIENT`, a truncated gzip body, and a non-image image body
  WHEN every failure path in R1 to R7 is driven, including invalid URL, not HTML, unsupported encoding, unreadable content, content too large, not recognised, each gateway failure, invalid JSON-LD, the depth bound, gzip corruption and image decode failure
  THEN the sentinel check (F4's `assertNoSentinel()` where the import error is a `Throwable`, or an equivalent helper over the error's rendered fields otherwise, per Decision Point 1) passes on every import error and on every exception observed at the importer's boundary: no exception or error message this feature constructs contains raw user-supplied or imported content, and any third-party exception carrying such content is re-wrapped into a typed error before propagating. [CFC-4]

- GIVEN the main sources under `ie.pantry.recipeimport` and `ie.pantry.ui.recipeimport`, and the sentinel fixtures above
  WHEN each exception type those sources construct is built on its failure path
  THEN no exception constructed in this component embeds the URL, fetched HTML, or an ingredient line in its message. [CFC-4]

- GIVEN a jsoup exception, a `kotlinx.serialization` `SerializationException` and a `java.util.zip.ZipException`, each with a sentinel in its message
  WHEN the import pipeline re-wraps them (where the import error is a `Throwable`, per Decision Point 1; otherwise the stack-frame clause does not apply and the other four points do)
  THEN the result follows F4's five-point re-wrapping rule (`GatewayException.kt`, `gatewayFailure` KDoc): the category comes from type checks only, the cause is not chained, suppressed exceptions are not copied, stack frames are copied, and nothing is logged.

- GIVEN every user-visible import message (invalid URL, not HTML, unsupported encoding, unreadable content, content too large, not recognised, each gateway category)
  WHEN it is rendered for a sentinel import
  THEN the rendered text contains no URL, HTML fragment or ingredient line. Each message is a constant string resource.

### R10: Grow F3's parse-fidelity corpus with harvested lines

As the developer of F3, F6 and F11, I want real ingredient lines produced by this import pipeline in the shared corpus, so that the parser is proven against the text the import route actually supplies, not only hand-transcribed text.

**Acceptance Criteria:**

- GIVEN `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv` (the default resource `ParseFidelityCorpus.load()` reads)
  WHEN `HarvestedCorpusTest` loads it
  THEN it holds at least 20 rows with origin `F5_HARVESTED` that are ingredient lines (not section headers), from at least 3 distinct source hosts **[ASSUMPTION — Q11]**, each with its source URL and retrieval date, and no row repeats the `line` text of a `HAND_TRANSCRIBED` row (so the harvest adds distinct evidence rather than re-listing the seed corpus): at least 20 real ingredient lines actually harvested by this feature's import pipeline (distinct from F3's original hand-curated seed corpus) are added to the shared parse-fidelity corpus F3 owns, discharging F3's own true-up commitment once F5 ships. [CFC-1]

- GIVEN each `F5_HARVESTED` row and the committed page snapshot for its source URL under `app/src/test/resources/recipeimport/harvest/`
  WHEN `HarvestedCorpusTest` runs F5's extraction over that snapshot
  THEN the row's `line` text is byte-identical to a line F5's extraction produced from that snapshot, and its expected values were written by the developer from the source line, not copied from engine output (F3 RK6). The snapshots are real third-party pages, committed with their source URL and retrieval date. **[User-directed — Decision Point 6]**

- GIVEN the extended corpus and the shipped alias table
  WHEN F3's existing `ParseFidelityCorpusTest` runs, unmodified
  THEN every `F5_HARVESTED` row parses to its expected key and quantity: an ingredient line supplied by this feature is parsed by the single shared C4 entry point, and identical line text produces an identical canonical key and quantity regardless of which input route supplied it. [CFC-1]

- GIVEN the main sources under `ie.pantry.recipeimport` and `ie.pantry.ui.recipeimport`
  WHEN a source-inspection test scans them
  THEN none references `IngredientEngine`. F5 does not itself parse (PLAN F5 Component note); F6 parses on confirm.

### R11: Accessible import states

As a home cook using TalkBack or a large font, I want every import state announced and every control labelled, so that I can import a recipe without sighted help.

**Acceptance Criteria:**

- GIVEN the import screen in its input, in-progress, error, decline and draft-produced states
  WHEN it is inspected with Compose semantics
  THEN the URL field has a visible label and, when invalid, an associated error text; the in-progress indicator and every message are exposed as live regions so a state change is announced; a share carrying a link triggers the constant live-region message saying a link was received and the user should tap Import, and a share carrying a link that cancels a running import is announced as having cancelled it (R1); Cancel, Retry, Try another link, Import another, Share link and Import each have a content description or text label and a touch target of at least 48dp. At 200% font scale and in landscape, the URL field, error text and the Cancel, Retry and Share controls stay reachable and unclipped.

## Project Structure

```
pantry/                                                  (repository root)
├── gradle/libs.versions.toml                            MODIFIED: jsoup, kotlinx-serialization-json, lifecycle-viewmodel-compose
└── app/
    ├── build.gradle.kts                                 MODIFIED: implementation lines
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                      MODIFIED: ACTION_SEND text/plain intent-filter on MainActivity
        │   ├── res/values/strings.xml                   MODIFIED: constant import messages
        │   └── java/ie/pantry/
        │       ├── recipeimport/                        NEW: C1 pipeline (validate, fetch via C7, bound, extract, image, draft)
        │       ├── ui/recipeimport/                     NEW: C9 import screen, decline surface, ViewModel
        │       ├── ui/MainActivity.kt                   MODIFIED: share-intent handling; import screen as content
        │       └── di/AppContainer.kt                   MODIFIED: one RecipeImporter over the existing gateway + Clock
        └── test/
            ├── java/ie/pantry/
            │   ├── recipeimport/                        NEW: pipeline tests (JVM + MockWebServer; Robolectric for image decode)
            │   ├── ui/recipeimport/                     NEW: Compose + Robolectric screen and intent tests
            │   └── data/gateway/SingleCallSiteTest.kt   MODIFIED: forbid Jsoup.connect
            └── resources/
                ├── recipeimport/                        NEW: HTML, JSON-LD and image fixtures
                └── ingredient/parse_fidelity_corpus.tsv MODIFIED: 20 or more F5_HARVESTED rows
```

**[ASSUMPTION — the Design phase fixes the package and file split.]** The Design phase also fixes the `ImportDraft` shape for the method (steps or text), the accepted `recipeInstructions` forms (string, `HowToStep`, nested `HowToSection`) and an image given as an `@id` reference, the DOM memory cost of the 10 MiB cap on a low-end device, the import field's keyboard and autofocus polish, and `@GraphicsMode(NATIVE)` for the image-decode tests, the fixture sizes under `TestGateways.against`'s 64 KiB default raw cap (or a `GatewayPolicy()` override), the snapshot-to-row path rule and the no-tab, no-leading-`#` constraint on harvested corpus lines, and reading `EXTRA_TEXT` as a `CharSequence`. The C1 package is `ie.pantry.recipeimport`. `import` is a Kotlin soft keyword, and the package is named `ie.pantry.recipeimport` to avoid confusion with the `import` statement. It is a top-level sibling of `data`, `domain` and `ui`, because C1 is a feature service, not a pure engine: image validation uses `android.graphics.BitmapFactory`, so C1 does not belong under `domain/` (ARCHITECTURE System Overview; F3's no-`android.*` rule for `domain/ingredient`). The C9 surfaces go in `ie.pantry.ui.recipeimport`, beside the existing `ie.pantry.ui` and `ie.pantry.ui.theme`. Today `MainActivity` renders only `PlaceholderScreen`, there is no navigation library, and there is no direct ViewModel dependency.

### New Files

- `app/src/main/java/ie/pantry/recipeimport/ImportUrl.kt`: input trimming, shared-text URL picking and `http(s)` validation (R1).
- `app/src/main/java/ie/pantry/recipeimport/RecipeImporter.kt`: the `suspend` pipeline over `ExternalDataGateway` and an injected `Clock` (R2, R6, R7).
- `app/src/main/java/ie/pantry/recipeimport/ImportDraft.kt`: the draft model F6 renders, with explicit absent states for title, method, cooking time, yield and image, plus the extraction tier (R4, R6, R7).
- `app/src/main/java/ie/pantry/recipeimport/ImportResult.kt`: the success/failure result and the content-free import error with its categories (R2, R3, R5, R9).
- `app/src/main/java/ie/pantry/recipeimport/BoundedHtmlReader.kt`: media-type check, streaming decompression under the cap, and charset decoding (R2, R3).
- `app/src/main/java/ie/pantry/recipeimport/JsonLdExtractor.kt`, `MicrodataExtractor.kt`, `HeuristicExtractor.kt`: the three tiers (R3 depth bound, R4).
- `app/src/main/java/ie/pantry/recipeimport/ImageCandidateSelector.kt` and `ImageValidator.kt`: the three-tier image lookup and the bounds-aware decode check (R7).
- `app/src/main/java/ie/pantry/ui/recipeimport/ImportViewModel.kt` and `ImportScreen.kt`: URL input, progress with Cancel, error messages, the decline surface with Retry, Try another link and Share link, and the draft-produced state (R1, R2, R5, R8).
- `app/src/test/java/ie/pantry/recipeimport/ImportUrlTest.kt` (R1), `RecipeImporterFetchTest.kt` (R2), `ContentBoundsTest.kt` (R3), `ExtractionTiersTest.kt` (R4), `DeclineTest.kt` (R5), `DraftProvenanceTest.kt` (R6), `ImageSelectionTest.kt` (R7), `ImportErrorHygieneTest.kt` (R9), `HarvestedCorpusTest.kt` (R10).
- `app/src/test/java/ie/pantry/ui/recipeimport/ImportScreenTest.kt`: R1's share intents, R2's cancel, R5's decline flow, R8's chooser and R11's accessibility semantics.
- `app/src/test/resources/recipeimport/` (including `harvest/` snapshots for R10): the JSON-LD (four shapes), microdata, RDFa, heuristic, parse-badly, nothing-extracted, invalid-JSON-LD, deep-JSON-LD and malformed-HTML pages, plus the non-image image body. The gzip bomb may be generated in test code instead (Decision Point 5).

### Modified Files

- `gradle/libs.versions.toml` and `app/build.gradle.kts`: jsoup and the `kotlinx-serialization-json` runtime artifact (ARCHITECTURE Technology Choices; the Kotlin serialization compiler plugin is added only if Design shows `@Serializable` is needed), and `androidx.lifecycle:lifecycle-viewmodel-compose` for the MVVM ViewModel (ARCHITECTURE App architecture). All go through catalog aliases (F1 R1).
- `app/src/main/AndroidManifest.xml`: an `<intent-filter>` with `android.intent.action.SEND`, `android.intent.category.DEFAULT` and `mimeType="text/plain"` on the already-exported `MainActivity`, plus whatever `launchMode` Decision Point 4 settles.
- `app/src/main/java/ie/pantry/ui/MainActivity.kt`: reads share intents in `onCreate` and `onNewIntent`, and shows the import screen in place of `PlaceholderScreen` **[ASSUMPTION — Q1]**.
- `app/src/test/java/ie/pantry/ui/FirstPaintNotBlockedTest.kt`: F2's R6 test, which today asserts that the placeholder is displayed while a dataset read is blocked. The import screen must still render the app name `Pantry` exactly once, and the test's wording and any selector tied to `PlaceholderScreen` are updated to name the import screen. This is F2's test, so it is Ask First. F2 R6's intent (lazy, off-main-thread, load-once, first paint not blocked) is unchanged, but its "placeholder" wording (its fifth acceptance criterion and the matching Success Criterion in F2's `01_spec.md`) is amended to name the import screen.
- `app/src/main/java/ie/pantry/di/AppContainer.kt`: holds the `Clock` that `production()` already receives, and builds one `RecipeImporter` over the existing `gateway`. Any new constructor parameter has a default, so existing `AppContainer` call sites (`AppContainerTest`, `FirstPaintNotBlockedTest`'s `BlockingReadPantryApplication`) compile unchanged.
- `app/src/main/res/values/strings.xml`: the constant import messages (R9).
- `app/src/test/java/ie/pantry/data/gateway/SingleCallSiteTest.kt`: adds `Jsoup.connect` to the forbidden-API list (R2). This is F4's test, so it is Ask First.
- `app/src/test/resources/ingredient/parse_fidelity_corpus.tsv`: 20 or more appended `F5_HARVESTED` rows (R10).

No change to `PantryDatabase`, its entities or DAOs, `RecipeRepository`, `ThumbnailProcessor`/`ThumbnailStore`, `app/schemas/`, `app/src/main/assets/`, the `ie.pantry.data.gateway` main sources, or any `ie.pantry.domain.ingredient` source.

## Commands

```bash
# F5's own tests, plus the F3/F4 tests it touches
./gradlew :app:testDebugUnitTest --tests 'ie.pantry.recipeimport.*' --tests 'ie.pantry.ui.recipeimport.*' --tests 'ie.pantry.data.gateway.SingleCallSiteTest' --tests 'ie.pantry.domain.ingredient.ParseFidelityCorpusTest'

# Full suite (no F1-F4 regressions)
./gradlew :app:testDebugUnitTest

# Static analysis
./gradlew lintDebug

# Merged release manifest: still exactly one android.permission (INTERNET)
./gradlew :app:processReleaseMainManifest && grep -c 'uses-permission android:name="android.permission' app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml

# Quick cross-checks (asserted by tests): no HTTP outside the gateway, no parsing in C1
grep -rn 'Jsoup.connect\|import okhttp3\.' app/src/main/java/ie/pantry/recipeimport app/src/main/java/ie/pantry/ui/recipeimport || echo "clean"
grep -rn 'IngredientEngine' app/src/main/java/ie/pantry/recipeimport app/src/main/java/ie/pantry/ui/recipeimport || echo "clean"
```

## Boundaries

### Always Do

- Fetch the page and the image only through `AppContainer.gateway` (`fetchPage`, `fetchImage`). Branch on `GatewayResult` and `GatewayException.Category` with an exhaustive `when` (F4 design Integration Points).
- Treat `FetchedBody.bytes` as still content-encoded: gunzip `BodyEncoding.GZIP` under the streaming decompressed cap, and treat `BodyEncoding.OTHER` as unreadable (F4 design AD2).
- Check the media type before any parse, and bound JSON-LD depth before any recursive parse or walk.
- Represent every optional draft field as a stated value or an explicit absent state, in the style of `ThumbnailOutcome.NoImage` and F3's unquantified value (CFC-2).
- Build every import error from enums, integers and class simple-names only, with no `String` constructor parameter. Re-wrap jsoup, serialization, zip and decode exceptions using F4's five-point rule (CFC-4).
- Let `CancellationException` propagate. Never catch it and turn it into an import error.
- Validate image bytes with a bounds-aware decode (bounds first, then a sample size) before they reach the draft (ARCHITECTURE C1 Key Concerns).
- Inject `Clock` for `fetchedAt`. Never call `Instant.now()` directly.
- Write tests in the F1-F4 style: JUnit 4, `kotlin.test` assertions, backtick-quoted names, `TestGateways.against(server)` for network tests and `assertNoSentinel()` for hygiene.

### Ask First

- The edit to F4's `SingleCallSiteTest` (R2) and to F2's `FirstPaintNotBlockedTest` (Q1).
- Adding `finalUrl` (or any field) to F4's `FetchedBody`/`GatewayResult`, or any other change to `ie.pantry.data.gateway` (Q10; F4 design Q4 left `finalUrl` as an additive change for F5 to request).
- Any change to F3's `IngredientEngine`, `QuantityParser`, `CanonicalKeyRule` or F2's alias table that a failing `F5_HARVESTED` corpus row seems to need. Fix the parser or correct the corpus row; never edit the expected values to match engine output.
- Adding a navigation library, Hilt or any dependency beyond jsoup, `kotlinx-serialization-json` and `lifecycle-viewmodel-compose`.
- Any decompressed-content cap, JSON-LD depth bound or accepted media type other than what Q5 and Q7 settle.
- Adding any Android permission, or any manifest change beyond the `ACTION_SEND` intent-filter and `MainActivity`'s `launchMode`.

### Never Do

- Never call C4 (`IngredientEngine`) or C3's persistence (`RecipeRepository`, any DAO, `ThumbnailStore`) from F5. Reusing `ThumbnailProcessor`'s decode logic for validation is a Decision Point, not a persistence call. Confirm, parse and persist belong to F6 (PLAN F5 Component).
- Never write the image or any other file to disk. The draft carries in-memory bytes only.
- Never call `Jsoup.connect`, `HttpURLConnection` or any HTTP API other than the gateway, and never fetch any sub-resource except the one selected image.
- Never execute page script, and never use a `WebView` to render or extract a page.
- Never guess a scheme, cooking time, yield, title or image. Unstated means explicitly absent. The only title not stated by the page's structured data is R4's defined heuristic source.
- Never pre-clean an ingredient line beyond R4's text-extraction normalisation (CFC-1).
- Never put a URL, host, HTML fragment, JSON-LD fragment, title or ingredient line in an exception message, an import error, a user-visible message or a log line (CFC-4).
- Never send anything about a decline automatically. Sharing is only by the user's tap through the system share sheet (R8).
- Never build F6's recipe form, F14's orientation flow or F14's generic failed-lookup surface here.
- Never tag an F5 criterion with CFC-3. PLAN's CFC-3 participant list does not include F5.

### Network Exposure Triage

**Branch (a) — no new surface.** Introduces no new domain, route or port; the surfaces it touches already existed and are unchanged. Checked:

1. **Inbound listeners:** F5 adds no `ServerSocket`, bound port or listener in shipped code. MockWebServer binds loopback ports only inside JVM unit tests, as a `testImplementation` dependency that is not in the APK.
2. **Domains and DNS:** no domain, DNS record, hostname or public endpoint is registered or routed.
3. **Exported components:** no new activity, service, receiver or provider is added. `MainActivity` was already `exported="true"` as the launcher. F5 adds a `SEND` `text/plain` intent-filter to it, which is a new on-device IPC entry point, not a network surface. Any app can deliver text through it, and that text is untrusted input. R1's URL picking (a 2048-character scan bound) and scheme and host validation run before any gateway call, but they are not the network control. A share only fills the URL field: no fetch starts without the user's tap on Import, so a share alone causes no network request. The control on the fetch the user then starts is F4's address policy (loopback, link-local and private ranges refused on every hop), which the share route does not bypass.
4. **Permissions:** none are added. `INTERNET` (F4) stays the only `android.permission.*`, as checked by `ManifestPolicyTest` and the release-manifest grep.
5. **Outbound traffic:** only F4's existing `fetchPage`/`fetchImage` call types, to the user-supplied URL and one page-supplied image URL per import. Each hop is still limited by F4's scheme and address checks and its anonymous request construction.
6. **Share-export:** the decline surface's Share link starts an on-device system chooser on the user's tap. Pantry itself opens no connection for it.

## Open Questions

> All questions must be resolved before proceeding to the next phase.

- [x] Q1: Where does a successful draft go before F6 ships its form? F6 is built next, but F5 must end somewhere visible. **[User-confirmed]** F5's import screen becomes `MainActivity`'s content in place of `PlaceholderScreen`, and the ViewModel exposes a draft-produced state holding the `ImportDraft`. The screen shows only a constant "Recipe found" (or "partly found", R5) notice and an "Import another" control, and does not render any draft field. The screen keeps rendering the app name `Pantry` once, so F2's `FirstPaintNotBlockedTest` stays meaningful (see Modified Files). F6 then connects that state to its form. F5 alone is therefore not shippable to users: "Import another" discards the draft, and F6 follows directly. This keeps F5 from building a throwaway draft view, and it means F5 renders no draft absences.
- [x] Q2: What counts as "something extracted" for the decline rule? Every HTML page has a `<title>`, so "any field" would mean nothing is ever declined. **[User-confirmed]** A page counts as something extracted only if a structured tier yields a `Recipe` with at least one ingredient line or method step, or the heuristic tier identifies an ingredient list of at least 2 items. A title alone, from any tier, is not enough (user-directed; PLAN F5's "something extracted, however incomplete" should be trued up to mean at least one ingredient line or method step). A `<title>` or `og:title` on its own is not enough (SCOPE Non-Goals: "no heuristic fallback able to identify one").
- [x] Q3: Should a share intent start the import at once, or fill in the URL field and wait for Import? Which URL is used when the shared text holds several? **[User-directed]** A share does not start the import. It fills the URL field with the first `http(s)` URL picked from `EXTRA_TEXT` and shows the input state, and the import starts only when the user taps Import. A share carrying a link that arrives while an import is running cancels that import and fills the field. No share causes a gateway request by itself. URL picking runs on the field's content when the user taps Import, and on shared text when a share arrives (R1). A paste is not rewritten, so the field keeps what was typed, and after a successful Import tap the field shows the picked URL. PLAN true-up note: PLAN F5's "triggering the fetch" and ARCHITECTURE's C9-to-C1 "start an import from a share intent or paste" should read that a share fills the URL field and the user's Import tap starts the fetch. F6 must revisit share-during-draft and share-cancels-import once the draft carries editable state.
- [x] Q4: How is input normalised (F4 `[DEF-15]` routed this to F5)? **[User-confirmed]** Leading and trailing whitespace are trimmed. A scheme-less input is rejected, not prefixed with `https://` (ARCHITECTURE C1: "reject … rather than guessing"). A URL with userinfo (`user:pw@host`) is rejected rather than passed on, so a credential can never reach `sourceUrl` or the share-export (this settles what F4 Q8 left to F5).
- [x] Q5: Which media types count as HTML? **[User-confirmed]** `text/html` and `application/xhtml+xml`, case-insensitive, with any parameters. A response with no `Content-Type` is rejected as not HTML rather than sniffed.
- [x] Q6: Which surface renders gateway-failure messages (timeout, 404, refused address, connection failure) in F5? PLAN puts the fetch-failure surface in F14 (`F5 -> F14`), but F14 ships in Milestone 5. **[User-confirmed]** F5's import screen shows one constant, content-free plain-language message per gateway category, with `HTTP_STATUS` worded separately for 404, for 401 and 403, and for 5xx, and one generic "the site returned an error" message for every other status. Retry is offered for exactly the categories R2 names: `TIMEOUT`, `CONNECTION_FAILED` and 5xx `HTTP_STATUS`; the generic other-status message and the 404, 401/403 and `ADDRESS_REFUSED` messages offer none. For `ADDRESS_REFUSED` the message says the link, or a redirect it follows, points to a private or local network address and Pantry does not fetch those, which picks up F4's `[DEF-16]`. **[User-directed]** F14 later owns the conformant fetch-failure surface and may replace these messages.
- [x] Q7: What are the bound values? **[User-confirmed]** The decompressed-content cap is 10 MiB, applied to the decompressed (or identity) HTML stream. The JSON-LD nesting depth bound is 64, counted as the nesting of `{` and `[` with the outermost value at depth 1. At most 20 JSON-LD blocks are examined per page, and a 21st and later block is ignored (R3).
- [x] Q8: Exactly which text normalisation of an ingredient line is allowed under CFC-1's "no route-specific pre-cleaning"? **[User-confirmed]** Only these: HTML entity decoding, tag removal, folding U+00A0 (the non-breaking space) to a space, collapsing internal whitespace runs to one space, and trimming. List markers such as `•` and `-` are kept. Empty lines after normalisation are dropped.
- [x] Q9: Which properties feed cooking time and yield? **[User-confirmed]** Cooking time is `totalTime`, else `cookTime` (also when `totalTime` is present but zero or unparseable), as an ISO 8601 duration rounded to whole minutes. `prepTime` is not added, and a zero or unparseable duration is absent. Yield is read from a `recipeYield` value, or from its first element when it is an array: a bare integer (a string or a number), or an integer beside "serves", "servings" or "portions". Anything else, including a range or a quantity with a unit, is absent. Seconds in a duration are rounded to the nearest whole minute, and a duration with a year, month, week or day designator (such as `P1DT2H`) is absent. The heuristic tier never produces cooking time or yield.
- [x] Q10: How are image candidates resolved, and what happens when the chosen candidate fails? F4 does not return the post-redirect URL (F4 design Q4 deferred `finalUrl` until F5 needs it). **[User-confirmed]** A relative candidate is resolved against `<base href>`, else the supplied URL, and `finalUrl` is not requested from F4. A fetch failure, a non-`http(s)` candidate or a decode failure gives the explicit no-image state with no fall-through to a lower tier, since the preference order "stops at the first hit" (ARCHITECTURE C1). PLAN F6 commits that the device photo picker can attach or replace a thumbnail and that the user can clear an optional field on post-save edit, so F5 builds nothing for it; removing the imported picture at draft-confirm time is not yet an F6 commitment (R7's PLAN F6 true-up note).
- [x] Q11: Must the at least 20 harvested lines come from more than one site? **[User-confirmed]** At least 3 distinct hosts, so the corpus exercises more than one site's extraction. PLAN sets no host floor.
- [x] Q12: What does the user see during a long import? F4's worst case is about 62.5 s for the page fetch, and the image fetch can add up to the same again. **[User-confirmed]** An indeterminate progress indicator with Cancel (R2), whose text changes to a "fetching picture" status once the page is extracted and the image fetch begins, and no separate import-wide timeout beyond the gateway's own bounds. Cancel during the image phase cancels the whole import, as at any other point.
- [x] Q13: Which URL and instant are recorded as provenance? **[User-confirmed]** `sourceUrl` is the validated URL the user supplied, not the post-redirect URL, which F4 does not return in any case. `fetchedAt` is read from the injected `Clock` when `fetchPage` returns `Fetched`, not when the import starts or when the image fetch ends.

## Decision Points

1. The import error's shape: one `Throwable` class with a category enum that wraps a `GatewayException` category for fetch failures, mirroring F4 AD1, or a sealed result with no `Throwable`. Either choice must support `assertNoSentinel()`.
2. How the JSON-LD depth bound is enforced. `kotlinx.serialization`'s `Json.parseToJsonElement` has no depth option, so a successful parse does not by itself prove the bound. The options are a bracket-depth pre-scan of the raw block, or a custom bounded reader. Either way the extractor's own recursive walk over `@graph` and nested objects is bounded too (R3).
3. Tier selection: first `Recipe`-yielding tier wins outright (R4's assumption), or per-field fill-in from lower tiers. The second changes R4's partial-tier criterion.
4. Share-intent plumbing: the `launchMode` for `MainActivity` (`singleTop` or `singleTask`, since the default cannot satisfy R1's share-while-running criterion, which needs `onNewIntent` and must hold on a real cross-task share and not only on a direct `onNewIntent` call in Robolectric), and how the intent reaches the ViewModel without being replayed after a configuration change.
5. Fixture production: whether the gzip bomb and the deeply nested JSON-LD are committed files or generated in test code, and whether the image validator reuses `ThumbnailProcessor`'s bounds-first decode or has its own decode-bounds check.
6. **Settled by the user:** R10's "actually harvested by this feature's import pipeline" provenance is verified by committed page snapshots that `HarvestedCorpusTest` runs the extractor over. The copyright question of committing third-party HTML is accepted by the user; the Design phase decides snapshot size and trimming.
7. Charset handling: trust jsoup's detection (`Content-Type` charset, then `<meta charset>`, then UTF-8) over the bounded stream, or decode explicitly first.

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| RK1 | `kotlinx.serialization` or jsoup exception messages carry JSON-LD or HTML fragments, including ingredient text, into a crash report | High | High | R9's sentinel tests over invalid JSON-LD and jsoup failures; F4's five-point re-wrap rule; no `String` constructor parameter on import errors |
| RK2 | The decline rule is too loose, so every page "extracts" a `<title>` and nothing is declined, or too strict, so a thin real recipe is declined and the user is dead-ended | Med | High | Q2 pins the threshold; R5's paired parse-badly and nothing-extracted fixtures pin both sides |
| RK3 | F5-harvested lines expose F3 parser defects (for example `½`, `1 x 400g tin` or site-specific bullets), and the corpus test blocks F5 on F3 work | Med | Med | Ask First on any engine change; the corpus rule "fix the parser or the corpus, never from engine output"; harvest early in the Implement phase to surface defects soon |
| RK4 | A decompression bomb, a deep JSON-LD block or a huge-dimension image exhausts memory or the stack on a low-end device after F4's 5 MiB raw cap is passed | Med | High | R3's streaming cap and depth bound; R7's bounds-first decode under the 1 GiB test heap |
| RK5 | Relative image URLs resolve against the pre-redirect URL, so the wrong image or no image is fetched on sites that redirect | Med | Low | Q10: `<base href>` first; the failure degrades to the explicit no-image state, never a wrong error; an additive F4 `finalUrl` is the escalation path |
| RK6 | Line normalisation in extraction quietly turns into route-specific pre-cleaning (stripping "approx.", unicode fractions), breaking CFC-1's single-path guarantee | Med | High | R4's normalisation criterion; Q8 lists the allowed operations; Never Do |
| RK7 | Without F6 in place, a successful draft has nowhere to go, so F5 cannot be exercised end to end on a device | High | Low | Q1's draft-produced state; F6 is next in build order and consumes `ImportDraft` |
| RK8 | Gateway-failure messaging built here diverges from F14's later surface, so the work is done twice or the app is inconsistent | Med | Low | Q6: constant per-category strings that F14 may replace; no shared surface built here |
| RK9 | A worst-case slow site holds the import screen for about 2 minutes (page plus image retries), which reads as a hang | Low | Med | R2's Cancel; Q12's progress indicator; image failure never fails the import |

## Success Criteria

- [ ] A shared `http(s)` URL fills the URL field without fetching, a pasted or shared `http(s)` URL is imported when the user taps Import, and every other input is rejected with a plain message before any gateway call.
- [ ] Every import fetch goes through the single F4 gateway, and no import source uses another HTTP API or `Jsoup.connect`.
- [ ] Non-HTML payloads and unreadable encodings are rejected before any parse, and the gzip-bomb and deep-JSON-LD fixtures are contained without an escaping exception.
- [ ] JSON-LD, microdata, RDFa and heuristic fixtures each produce a draft from the expected tier, and a page with nothing extractable is declined with distinct wording and can be followed at once by another URL.
- [ ] Apart from a user's Cancel or a share carrying a link, every import that does not produce a draft ends in a typed error, and no fixture (timeout, 404, refused address, non-HTML, malformed HTML, cancel) hangs or crashes.
- [ ] Drafts carry `sourceUrl` and `fetchedAt`, and title, method, cooking time, yield and image are each a stated value or an explicit absence.
- [ ] Image selection follows the three-tier order, valid images arrive as in-memory bytes, and absent, failed or non-decodable images give the explicit no-image state with no file written.
- [ ] A declined URL can be shared through the system chooser, and the decline itself sends nothing.
- [ ] Sentinel tests pass on every failure path, with no URL, HTML or ingredient text in any error or user-visible message.
- [ ] At least 20 `F5_HARVESTED` lines are in the shared corpus and pass F3's unmodified corpus test.
- [ ] `INTERNET` remains the only Android permission.
- [ ] All tests pass.
- [ ] Every import state is accessible: labelled controls, announced state changes, 48dp touch targets (R11).
- [ ] No regressions in existing functionality: the full `./gradlew :app:testDebugUnitTest` suite for F1 to F4 stays green, including F2's `FirstPaintNotBlockedTest` after its listed edit.

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
| 1    | 2026-10-04 | 1     | 0           | 32        | 5        | 1      | —                  |
| 2    | 2026-10-04 | 4     | 0           | 24        | 4        | 1      | —                  |
| 3    | 2026-10-04 | 1     | 0           | 21        | 3        | 0      | —                  |
| 4    | 2026-10-05 | 0     | 0           | 0         | 0        | 35     | converged (0 HIGH) |
| 5    | 2026-10-05 | 0     | 0           | 0         | 0        | 14     | converged (0 HIGH) |
| 6    | 2026-10-05 | 0     | 0           | 0         | 1        | 17     | converged (0 HIGH) |

### Sealed dispositions

- `[SEAL-01]` **ACTION_VIEW unsupported** (pass 1, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed. PLAN asks only for share and paste, so ACTION_VIEW is out of F5 scope and can be a later feature.
- `[SEAL-02]` **Share chooser lists Pantry itself** (pass 2, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed. The chooser is a system surface, the loop is harmless and user-initiated, and excluding Pantry can be a Design option.
- `[SEAL-03]` **No-link-found message missing from R5 and R9 message lists** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass; the Tasks phase (tasks.md) adds it to the message-distinctness and sentinel-render tests, and the gateway-and-import message list is a constant set.
- `[SEAL-04]` **Which typed error each third-party exception maps to** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the mapping is a Design detail (design.md); R9 already requires re-wrapping and R4 requires invalid JSON-LD to be skipped.
- `[SEAL-05]` **cookTime fallback on zero or unparseable totalTime has no AC** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Q9 states the rule and the user confirms it at the gate; the Tasks phase (tasks.md) adds the fixture.
- `[SEAL-06]` **Zero or negative yield** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; R6 limits yield to a bare integer or serves/servings/portions; the zero edge is a fixture for tasks.md, and F9 owns its own divisor guard.
- `[SEAL-07]` **PT30S rounding tie** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the half-up rounding rule is pinned in Design (design.md) with the fixture.
- `[SEAL-08]` **Charset handling has no AC** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Decision Point 7 stays open for Design (design.md), which also owns the Windows-1252 fixture.
- `[SEAL-09]` **First-use hint on the import screen** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; UI copy and layout belong to Design (design.md); F14 owns onboarding per PLAN.
- `[SEAL-10]` **Empty ingredient list vs CFC-2 phrasing** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the CFC-2 tagged AC text is fixed by PLAN and the empty list is the stated absence for ingredients; wording is refined in design.md.
- `[SEAL-11]` **PLAN deviations scattered inline** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; each deviation is flagged where it applies; the user reviews them at the approval gate and the PLAN amendment is a separate project-blueprint step.
- `[SEAL-12]` **Fetching-picture status has no AC** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; R11 already requires state announcements; the exact AC is added in tasks.md.
- `[SEAL-13]` **DP4 singleTop cannot serve a cross-task share** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Decision Point 4 is settled in Design (design.md); the spec already requires the criteria to hold on a real cross-task share.
- `[SEAL-14]` **No AC for manifest intent-filter wiring** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the manifest assertion is added as a task in tasks.md using PackageManager.
- `[SEAL-15]` **Fixed Clock cannot show when fetchedAt is read** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Design (design.md) names MutableClock (see DEF-03); Q13 fixes the rule.
- `[SEAL-16]` **Q6 wording variants lack ACs** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the wording is user-directed in Q6; the per-variant assertions are added in tasks.md.
- `[SEAL-17]` **Credential claim covers only userinfo** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; userinfo is the credential channel F4 Q8 left to F5; a query-string token residual risk is noted for Design (design.md).
- `[SEAL-18]` **Apostrophe ends the URL** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; URL-end handling detail goes to Design (design.md) with a fixture in tasks.md.
- `[SEAL-19]` **Heuristic yield has no AC** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Q9 states that the heuristic tier never produces yield; the AC is added in tasks.md.
- `[SEAL-20]` **import keyword rationale in Project Structure** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the rationale is non-load-bearing, the package name ie.pantry.recipeimport stands, and Design (design.md) fixes the package split and should drop the rationale.
- `[SEAL-21]` **userinfo rule claims the gateway canonical form** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the rule is F5's own @-before-delimiter check, restated in Design (design.md).
- `[SEAL-22]` **No AC bounding the HTML DOM itself** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Design (design.md) already owns DOM memory cost per the Project Structure note.
- `[SEAL-23]` **Normalisation duplicates C4 and Q8 omits the NBSP fold** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Q8 asks the user to confirm this at the approval gate and the user decides there; a Q8 wording fix follows that decision.
- `[SEAL-24]` **Retry as one new fetchPage call is not observable** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Design (design.md) restates it as a request-count delta on the MockWebServer.
- `[SEAL-25]` **Scheme-less shared link gets no-link-found** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; message wording is copy detail for design.md.
- `[SEAL-26]` **Meta-prose inside R4 AC** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the Q8 sentence moves into Q8 once the user resolves it at the gate.
- `[SEAL-27]` **Fall-through AC wording vs heuristic rule** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; R4's heuristic AC and Q2 state the real rule and take precedence; wording aligned in design.md.
- `[SEAL-28]` **Do skipped JSON-LD blocks count toward the 20** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; counting convention is Design (design.md) detail.
- `[SEAL-29]` **R11 clause omits two controls** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; tasks.md names every control in the R11 assertion.
- `[SEAL-30]` **URL field contents after each terminal state** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; UI state table belongs to Design (design.md).
- `[SEAL-31]` **statusCode carried by the typed error** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the error shape is Decision Point 1, settled in design.md.
- `[SEAL-32]` **System Back during an import** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Back behaviour is a Design (design.md) decision.
- `[SEAL-33]` **RDFa property attribute wording** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; extractor mapping detail for design.md.
- `[SEAL-34]` **DP3 still reads as open** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; R4 states the settled behaviour and DP3 records the choice; wording tidied in design.md.
- `[SEAL-35]` **Parse-badly fixture needs no title anywhere** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; fixture construction detail for tasks.md.
- `[SEAL-36]` **Too-large message sharing** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; message set is fixed in design.md.
- `[SEAL-37]` **Single gateway instance wording** (pass 4, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; test wiring wording is design.md detail.
- `[SEAL-38]` **Share arrival in the error state has no stated outcome** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass at the 5-pass cap; the UI state table for shares in each state belongs to Design (design.md) with the ACs added in tasks.md (see SEAL-30).
- `[SEAL-39]` **When validation fires for a shared URL** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; R1 already says validation rejects at Import; Design (design.md) fixes the field contents for an over-limit run.
- `[SEAL-40]` **Paste-side picking trigger unstated** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Design (design.md) decides whether picking runs on paste or at Import; Q3 and R1 are consistent on the picked URL.
- `[SEAL-41]` **No assistive-technology cue for a shared link** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; R11 requires announced state changes; the exact cue text is Design copy (design.md) with its AC in tasks.md.
- `[SEAL-42]` **R7 and Q10 overclaim F6 remove capability** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the user confirmed Q10 and F6 owns picture attach and replace; any remove-at-draft gap is an F6 spec matter (F6 spec phase).
- `[SEAL-43]` **Resolved Qs still tagged ASSUMPTION on ACs** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the Open Questions list is authoritative and every Q is ticked; inline tags are advisory and can be retagged during Design (design.md).
- `[SEAL-44]` **No-link share asymmetry unexplained** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; closed by the Design state table (design.md), see SEAL-03.
- `[SEAL-45]` **PLAN and ARCHITECTURE share-start wording needs a true-up** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the PLAN true-up list is handled in a project-blueprint amendment after approval; this is added to that list.
- `[SEAL-46]` **Q10 keeps a leftover confirm prompt** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Q10 is ticked and user-confirmed so the leftover sentence is inert; Design (design.md) drops it.
- `[SEAL-47]` **Cancel carve-out wording says any share during the import** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Q3 and R1 state the real behaviour (a share carrying a link cancels); the carve-out wording is aligned in Design (design.md).
- `[SEAL-48]` **DP4 references R5's same-instance criterion** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; Decision Point 4 is settled in Design (design.md), which will restate the criteria it must satisfy.
- `[SEAL-49]` **Triage item 3 says validation runs first** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; the control named is F4's address policy; the ordering wording is tidied in Design (design.md).
- `[SEAL-50]` **Share during a draft discards it** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; acceptable while the draft is notice-only; F6 must revisit this once the draft is editable (F6 spec phase).
- `[SEAL-51]` **Other Unicode spaces not named in the whitespace rule** (pass 5, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; U+2009 and U+202F handling is a normalisation fixture decision for Design (design.md).
- `[SEAL-52]` **Constant live-region cue does not re-announce on a repeat…** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; announcement mechanics are a Design decision (design.md) and the repeat-share test is added in tasks.md.
- `[SEAL-53]` **Lifetime of transient messages unspecified** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; the clearing rule per message is a Design state table (design.md), see SEAL-30.
- `[SEAL-54]` **No-link share in the error state is ambiguous** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; Design (design.md) settles replace versus stack and the fate of Retry in its state table.
- `[SEAL-55]` **Inline Q tags incomplete or mismatched with headers** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; the Open Questions list is the authoritative record and is fully ticked; tags are advisory and Design (design.md) retags them.
- `[SEAL-56]` **Over-limit share field content ambiguous** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; the 2048-character edge is a Design detail (design.md); the invariant that the URL is never truncated and fetched is stated in R1.
- `[SEAL-57]` **Two announcements on a share that cancels an import** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; priority or a combined message is Design copy (design.md) with its test in tasks.md.
- `[SEAL-58]` **R7 and Q10 say commits only for F6** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; the F6 commitments are restated accurately when F6 is specified (F6 spec phase); the draft-time removal gap is already flagged.
- `[SEAL-59]` **R1 state-matrix AC is bundled** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; tasks.md splits the matrix into separate tests, one per state and event.
- `[SEAL-60]` **New constant strings missing from R9 and R5 lists** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; tasks.md extends the R9 sentinel-render and R5 distinctness tests to every constant string, a larger set than SEAL-03 records.
- `[SEAL-61]` **Asymmetry rationale and link-carrying discard** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; the user chose replacement on a link-carrying share and SEAL-50 covers drafts; wording is tidied in design.md.
- `[SEAL-62]` **R11 large-font clause omits three controls** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; consistent with SEAL-29; tasks.md names every control.
- `[SEAL-63]` **True-up list omits ARCHITECTURE Data Flow and C1 Boundary** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; the full amendment list is compiled in the project-blueprint amendment after approval and these two entries are added to it.
- `[SEAL-64]` **Weak import keyword rationale** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; the package name is fixed and the rationale is non-load-bearing; Design (design.md) states the Java reserved-word reason.
- `[SEAL-65]` **Design-assumption tag covers the package name** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; the package name is used as fact throughout; Design (design.md) rescopes the tag to the file split.
- `[SEAL-66]` **R11 cancel announcement reads as share-only** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; whether the Cancel tap is announced is a Design copy decision (design.md).
- `[SEAL-67]` **One exception versus two in the cancel wording** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; wording only; the behaviour is stated in R1 and aligned in design.md.
- `[SEAL-68]` **Ambiguous successful Import tap in Q3** (pass 6, accepted-as-risk) — Defense: synthesizer-judged, not user-confirmed; exit-capable pass after the user-authorized sixth pass; R1 states the field shows the picked URL after validation passes; wording aligned in design.md.

### Deferred dispositions

- `[DEF-01]` **ImportDraft shape and instruction forms unspecified** → design.md (pass 1) — Routed because: draft field types and recipeInstructions forms are design decisions. Noted in Project Structure.
- `[DEF-02]` **Input field polish unspecified** → design.md (pass 1) — Routed because: keyboard type and autofocus are UI design detail. Noted in Project Structure.
- `[DEF-03]` **Existing ImageFixtures and MutableClock unmentioned** → design.md (pass 1) — Routed because: test helper reuse belongs in the Design testing strategy.
- `[DEF-04]` **Image tests need GraphicsMode NATIVE** → design.md (pass 1) — Routed because: test configuration is a Design testing-strategy detail. Noted in Project Structure.
- `[DEF-05]` **About 45 ACs, no slicing** → tasks.md (pass 1) — Routed because: slicing the work is the Tasks phase's job.
- `[DEF-06]` **Cancel in image phase discards the draft** → design.md (pass 2) — Routed because: whether to offer a Skip picture action is a Design decision.
- `[DEF-07]` **Behaviours beyond PLAN inflate AC count** → tasks.md (pass 2) — Routed because: task slicing can mark the extras as non-blocking; see DEF-05.
- `[DEF-08]` **Retry on decline rarely useful** → design.md (pass 2) — Routed because: whether the decline surface keeps Retry is a Design detail.
- `[DEF-09]` **SingleCallSiteTest substring scan and Jsoup.parse(URL)** → design.md (pass 2) — Routed because: how the forbidden list is phrased and extended is Design detail.
- `[DEF-10]` **R3 fixture sizes vs 64 KiB test cap** → design.md (pass 3) — Routed because: fixture sizing under TestGateways is test-strategy detail. Noted in Project Structure.
- `[DEF-11]` **Snapshot binding and corpus line constraints** → design.md (pass 3) — Routed because: path rule and corpus-line constraints are Design detail. Noted in Project Structure.
- `[DEF-12]` **EXTRA_TEXT may be a CharSequence** → design.md (pass 3) — Routed because: intent-extra reading is Design detail. Noted in Project Structure.
- `[DEF-13]` **Import-tap message for a link beyond char 2048** → design.md (pass 6) — Routed because: the wording of the over-scan-limit message is Design copy.

<!-- Auto-populated by archive_pass.py when a Deferred-disposed row is promoted; remains empty until first deferral. -->

### Latest pass detail

| Severity | Source | Concern | Disposition | Notes |
|----------|--------|---------|-------------|-------|

## Approval

- [x] Approved to proceed to next phase
- **Content Hash:** `40a2c8a0d1ae37f1`
- **Hash basis:** v2
