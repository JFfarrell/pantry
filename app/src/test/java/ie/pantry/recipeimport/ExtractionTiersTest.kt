package ie.pantry.recipeimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import ie.pantry.testutil.Sentinels
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element
import org.jsoup.select.NodeVisitor
import ie.pantry.data.gateway.BodyEncoding
import ie.pantry.data.gateway.FetchedBody
import org.jsoup.nodes.TextNode

class ExtractionTiersTest {

    private fun fragment(html: String): Element = Jsoup.parseBodyFragment(html).body()

    private fun lines(html: String, selector: String = "li"): List<String> =
        fragment(html).select(selector).mapNotNull { LineText.of(it) }.filter { it.isNotEmpty() }

    // ---- T8: LineText and the bounded walk ----

    @Test
    fun `normalise folds nbsp collapses ascii whitespace and trims`() {
        assertEquals("2 tbsp olive oil", LineText.of(fragment("<p>&nbsp;2&#160;tbsp\t <b>olive</b>\n oil </p>").selectFirst("p")!!))
        assertEquals("a b", LineText.normalise("  a\u000B\u000C\r\n b "))
        assertEquals("", LineText.normalise(" \t  "))
    }

    @Test
    fun `normalise keeps other unicode spaces and list markers`() {
        assertEquals("1 cup", LineText.normalise("1 cup"))
        assertEquals("1 cup", LineText.normalise("1 cup"))
        assertEquals("• 2 eggs", LineText.normalise("•  2 eggs"))
        assertEquals("- 2 eggs", LineText.normalise("-  2 eggs"))
    }

    @Test
    fun `element text decodes entities drops tags and breaks at br and block boundaries`() {
        val element = fragment("<div>Tom &amp; Jerry<br>second<p>third</p><span>a</span><span>b</span></div>").selectFirst("div")!!
        assertEquals("Tom & Jerry second third ab", LineText.of(element))
        val script = fragment("<p>keep<script>var x = 1;</script><style>p{}</style></p>").selectFirst("p")!!
        assertEquals("keep", LineText.of(script))
    }

    @Test
    fun `empty lines after normalisation are dropped`() {
        assertEquals(listOf("a", "b"), lines("<ul><li>a</li><li>   </li><li></li><li>b</li></ul>"))
    }

    @Test
    fun `a line whose raw text passes 4096 characters is dropped not truncated`() {
        fun item(text: String) = fragment("<p>$text</p>").selectFirst("p")!!
        assertEquals("a".repeat(4096), LineText.of(item("a".repeat(4096))))
        assertNull(LineText.of(item("a".repeat(4097))))
        assertNull(LineText.of(item("a".repeat(4090) + " ".repeat(10))))
    }

    private fun chain(depthOfDeepest: Int): Document {
        // html is depth 1 and body depth 2, so the deepest div of a chain sits at `depthOfDeepest`.
        // Built bottom-up so each append touches a detached parent, not a thousand-deep ancestor chain.
        var top = Element("div").also { it.appendText("deepest") }
        repeat(depthOfDeepest - 3) { top = Element("div").also { parent -> parent.appendChild(top) } }
        val document = Document("https://example.ie/")
        document.appendElement("html").appendElement("body").appendChild(top)
        return document
    }

    private fun visitedDepths(document: Document, job: Job = Job()): List<Int> {
        val depths = mutableListOf<Int>()
        BoundedDomWalk.walk(document, job, enter = { node, depth ->
            if (node is Element) depths += depth
            true
        })
        return depths
    }

    @Test
    fun `the walk visits depth 512 and skips depth 513`() {
        assertEquals(512, visitedDepths(chain(512)).max())
        assertEquals(512, visitedDepths(chain(513)).max())
        var sawText = false
        BoundedDomWalk.walk(chain(513), Job(), enter = { node, _ ->
            if (node is TextNode && node.wholeText == "deepest") sawText = true
            true
        })
        assertEquals(false, sawText)
    }

    @Test
    fun `the walk is iterative on a very deep chain`() {
        val depths = visitedDepths(chain(100_000))
        assertEquals(512, depths.max())
        assertEquals(513, depths.size) // the document (depth 0) and one element at each depth 1..512
    }

    @Test
    fun `the walk checks cancellation every 1024 nodes`() {
        val document = Document("https://example.ie/")
        val body = document.appendElement("html").appendElement("body")
        repeat(5_000) { body.appendElement("p") }
        val job = Job().apply { cancel() }
        var visits = 0
        assertFailsWith<CancellationException> {
            BoundedDomWalk.walk(document, job, enter = { _, _ -> visits++; true })
        }
        assertTrue(visits <= 1024, "visited $visits")
        // An uncancelled job completes the same walk.
        var all = 0
        BoundedDomWalk.walk(document, Job(), enter = { _, _ -> all++; true })
        assertTrue(all > 5_000)
    }

    // ---- T9: JSON-LD block scan ----

    private fun fixture(name: String): Document = Jsoup.parse(String(ImportFixtures.page(name), Charsets.UTF_8))

    private fun ld(block: String, type: String = "application/ld+json"): String = "<script type=\"$type\">$block</script>"

    private fun pageOf(vararg scripts: String): Document =
        Jsoup.parse("<html><head>${scripts.joinToString("")}</head><body></body></html>")

    private val recipeBlock = """{"@type":"Recipe","name":"Found","recipeIngredient":["1 egg"]}"""

    private fun assertIrishStew(scan: JsonLdScan) {
        assertEquals(1, scan.recipes.size)
        val recipe = scan.recipes.single().recipe
        assertEquals("Irish Stew", (recipe.title as DraftValue.Stated).value)
        assertEquals(listOf("500 g lamb", "2 onions"), recipe.lines)
    }

    @Test
    fun `a top level Recipe object is found`() = assertIrishStew(JsonLdExtractor.recipes(fixture("jsonld-object.html")))

    @Test
    fun `a Recipe inside an array is found`() = assertIrishStew(JsonLdExtractor.recipes(fixture("jsonld-array.html")))

    @Test
    fun `a Recipe inside a graph is found`() = assertIrishStew(JsonLdExtractor.recipes(fixture("jsonld-graph.html")))

    @Test
    fun `an at type array including Recipe is found`() = assertIrishStew(JsonLdExtractor.recipes(fixture("jsonld-type-array.html")))

    @Test
    fun `an invalid block is skipped without an escaping exception`() {
        val scan = JsonLdExtractor.recipes(pageOf(ld("{not json"), ld("""{"a":"""), ld(recipeBlock)))
        assertEquals(1, scan.recipes.size)
    }

    @Test
    fun `a block over 512 KiB is skipped and still counts toward twenty`() {
        val pad = "a".repeat(JSON_LD_BLOCK_CAP_CHARS)
        val over = """["$pad"]"""
        assertTrue(over.length > JSON_LD_BLOCK_CAP_CHARS)
        assertEquals(1, JsonLdExtractor.recipes(pageOf(ld(over), ld(recipeBlock))).recipes.size)

        val head = """{"@type":"Recipe","name":"Big","recipeIngredient":["1 egg"],"pad":""""
        val exact = head + "a".repeat(JSON_LD_BLOCK_CAP_CHARS - head.length - 2) + "\"}"
        assertEquals(JSON_LD_BLOCK_CAP_CHARS, exact.length)
        assertEquals(1, JsonLdExtractor.recipes(pageOf(ld(exact))).recipes.size)
        assertEquals(0, JsonLdExtractor.recipes(pageOf(ld(exact + " "))).recipes.size)

        val skipped = Array(19) { ld(over) } + ld(over) + ld(recipeBlock)
        assertEquals(0, JsonLdExtractor.recipes(pageOf(*skipped)).recipes.size, "skipped blocks count toward twenty")
    }

    @Test
    fun `an over deep block is skipped and the next block is read`() {
        val deep = ImportFixtures.deepJsonLd(65, viaGraph = false)
        val scan = JsonLdExtractor.recipes(pageOf(ld(deep), ld(recipeBlock)))
        assertEquals(1, scan.recipes.size)
        assertEquals(1, JsonLdExtractor.recipes(pageOf(ld(ImportFixtures.deepJsonLd(64, viaGraph = false)))).recipes.size)
    }

    @Test
    fun `the twenty first block is never read`() {
        val filler = Array(20) { ld("""{"@type":"Thing"}""") }
        assertEquals(0, JsonLdExtractor.recipes(pageOf(*filler, ld(recipeBlock))).recipes.size)
        val invalid = Array(20) { ld("{broken") }
        assertEquals(0, JsonLdExtractor.recipes(pageOf(*invalid, ld(recipeBlock))).recipes.size)
        val nineteen = Array(19) { ld("{broken") }
        assertEquals(1, JsonLdExtractor.recipes(pageOf(*nineteen, ld(recipeBlock))).recipes.size)
    }

    @Test
    fun `the script type is matched trimmed and case insensitively`() {
        val scan = JsonLdExtractor.recipes(pageOf(ld(recipeBlock, type = "  Application/LD+JSON ")))
        assertEquals(1, scan.recipes.size)
        assertEquals(0, JsonLdExtractor.recipes(pageOf(ld(recipeBlock, type = "application/json"))).recipes.size)
    }

    private fun nested(recipeDepth: Int): JsonElement {
        var element: JsonElement = JsonObject(mapOf("@type" to JsonPrimitive("Recipe"), "name" to JsonPrimitive("Deep")))
        repeat(recipeDepth - 1) { element = JsonArray(listOf(element)) }
        return element
    }

    @Test
    fun `the json ld walk does not visit nodes past depth 64`() {
        assertEquals(1, JsonLdExtractor.recipeNodes(nested(64)).size)
        assertEquals(0, JsonLdExtractor.recipeNodes(nested(65)).size)
        assertEquals(0, JsonLdExtractor.recipeNodes(nested(66)).size)
    }

    @Test
    fun `json ld scan and extracted recipe print no content`() {
        val block = """{"@type":"Recipe","name":"${Sentinels.TITLE}","recipeIngredient":["${Sentinels.INGREDIENT}"]}"""
        val scan = JsonLdExtractor.recipes(pageOf(ld(block)))
        assertEquals(1, scan.recipes.size)
        for (text in listOf(scan.toString(), scan.recipes.single().toString(), scan.recipes.single().recipe.toString())) {
            assertTrue(!text.contains("SENTINEL"), text)
        }
    }

    // ---- T10: JSON-LD Recipe mapping ----

    private fun scanOf(json: String): JsonLdScan = JsonLdExtractor.recipes(pageOf(ld(json)))

    private fun recipeOf(json: String): ExtractedRecipe = scanOf(json).recipes.single().recipe

    private fun described(value: DraftValue<Int>): String = if (value is DraftValue.Stated) "Stated(${value.value})" else "Absent"

    private fun quoted(items: List<String>): String = items.joinToString(",", "[", "]") { "\"$it\"" }

    @Test
    fun `json ld title and lines are entity decoded tag free and normalised`() {
        val recipe = recipeOf(
            """{"@type":"Recipe","name":" Tom &amp; <b>Jerry</b>\u00A0Stew ","recipeIngredient":["<b>2</b>&nbsp;tbsp olive &amp; oil","  salt \n pepper  ","   "]}""",
        )
        assertEquals("Tom & Jerry Stew", (recipe.title as DraftValue.Stated).value)
        assertEquals(listOf("2 tbsp olive & oil", "salt pepper"), recipe.lines)
    }

    @Test
    fun `ingredients is used when recipeIngredient is missing`() {
        assertEquals(listOf("1 egg"), recipeOf("""{"@type":"Recipe","name":"X","ingredients":["1 egg"]}""").lines)
        assertEquals(listOf("2 eggs"), recipeOf("""{"@type":"Recipe","name":"X","recipeIngredient":["2 eggs"],"ingredients":["1 egg"]}""").lines)
    }

    @Test
    fun `a string recipeInstructions splits on line breaks`() {
        val recipe = recipeOf("""{"@type":"Recipe","name":"X","recipeInstructions":"Chop\nFry the onion\r\n\r\n  Serve  \u00A0"}""")
        assertEquals(listOf("Chop", "Fry the onion", "Serve"), recipe.steps)
        val strings = recipeOf("""{"@type":"Recipe","name":"X","recipeInstructions":["Chop","","Fry"]}""")
        assertEquals(listOf("Chop", "Fry"), strings.steps)
    }

    @Test
    fun `step and section instruction forms flatten in order without section names`() {
        val recipe = JsonLdExtractor.recipes(fixture("jsonld-instructions.html")).recipes.single().recipe
        assertEquals(listOf("Chop", "Fry", "Serve"), recipe.steps)
        val steps = recipeOf(
            """{"@type":"Recipe","name":"X","recipeInstructions":[{"@type":"HowToStep","text":"One"},{"@type":"HowToDirection","text":"Two"},{"@type":"HowToStep","name":"Three"}]}""",
        )
        assertEquals(listOf("One", "Two", "Three"), steps.steps)
    }

    @Test
    fun `HowToTip is skipped`() {
        val recipe = recipeOf(
            """{"@type":"Recipe","name":"X","recipeInstructions":[{"@type":"HowToTip","text":"Tip"},{"@type":"HowToStep","text":"Cook"}]}""",
        )
        assertEquals(listOf("Cook"), recipe.steps)
    }

    @Test
    fun `json ld times and yield are read through RecipeValues`() {
        val stated = recipeOf("""{"@type":"Recipe","name":"X","totalTime":"PT1H15M","cookTime":"PT10M","recipeYield":"Serves 4"}""")
        assertEquals("Stated(75)", described(stated.cookingTime))
        assertEquals("Stated(4)", described(stated.servings))
        val cook = recipeOf("""{"@type":"Recipe","name":"X","totalTime":"PT0M","cookTime":"PT40M","recipeYield":["4","4 servings"]}""")
        assertEquals("Stated(40)", described(cook.cookingTime))
        assertEquals("Stated(4)", described(cook.servings))
        val neither = recipeOf("""{"@type":"Recipe","name":"X"}""")
        assertEquals("Absent", described(neither.cookingTime))
        assertEquals("Absent", described(neither.servings))
    }

    @Test
    fun `only the first 200 lines and 200 steps are kept`() {
        val lines = quoted(List(201) { "line $it" })
        val steps = quoted(List(250) { "step $it" })
        val recipe = recipeOf("""{"@type":"Recipe","name":"X","recipeIngredient":$lines,"recipeInstructions":$steps}""")
        assertEquals(200, recipe.lines.size)
        assertEquals("line 199", recipe.lines.last())
        assertEquals(200, recipe.steps.size)
        assertEquals("step 199", recipe.steps.last())
    }

    @Test
    fun `a json ld string longer than 4096 raw characters is dropped`() {
        val items = quoted(listOf("a".repeat(4097), "b".repeat(4096), "c".repeat(4090) + " ".repeat(10), "ok"))
        val recipe = recipeOf("""{"@type":"Recipe","name":"X","recipeIngredient":$items}""")
        assertEquals(listOf("b".repeat(4096), "ok"), recipe.lines)
        val title = recipeOf("""{"@type":"Recipe","name":"${"t".repeat(4097)}","recipeIngredient":["1 egg"]}""")
        assertEquals("Absent", if (title.title is DraftValue.Absent) "Absent" else "Stated")
    }

    @Test
    fun `only the first 1000 array elements are examined`() {
        val items = List(1000) { "" } + "late egg"
        val recipe = recipeOf("""{"@type":"Recipe","name":"X","recipeIngredient":${quoted(items)}}""")
        assertEquals(emptyList(), recipe.lines)
        val within = List(999) { "" } + "last egg"
        assertEquals(listOf("last egg"), recipeOf("""{"@type":"Recipe","name":"X","recipeIngredient":${quoted(within)}}""").lines)
    }

    private fun imageOf(value: String): String? =
        scanOf("""{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"],"image":$value}""").recipes.single().image

    @Test
    fun `the image string comes from a string an array or an ImageObject`() {
        assertEquals("https://example.ie/a.jpg", imageOf("\"https://example.ie/a.jpg\""))
        assertEquals("https://example.ie/b.jpg", imageOf("[\"https://example.ie/b.jpg\",\"https://example.ie/c.jpg\"]"))
        assertEquals("https://example.ie/d.jpg", imageOf("""{"@type":"ImageObject","url":"https://example.ie/d.jpg"}"""))
        assertEquals("https://example.ie/e.jpg", imageOf("""{"@type":"ImageObject","contentUrl":"https://example.ie/e.jpg"}"""))
        assertEquals("https://example.ie/f.jpg", imageOf("""[{"@type":"ImageObject","url":"https://example.ie/f.jpg"}]"""))
        assertNull(imageOf("\"\""))
        assertNull(imageOf("""{"@type":"ImageObject"}"""))
        assertNull(scanOf("""{"@type":"Recipe","name":"X"}""").recipes.single().image)
    }

    @Test
    fun `an at id image reference is followed one hop within its block`() {
        val scan = JsonLdExtractor.recipes(fixture("jsonld-image-id.html"))
        assertEquals("https://example.ie/stew.jpg", scan.recipes.single().image)
        val chained = scanOf(
            """{"@graph":[{"@id":"#a","@type":"ImageObject"},{"@id":"#b","image":{"@id":"#a"}},{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"],"image":{"@id":"#b"}}]}""",
        )
        assertNull(chained.recipes.single().image)
        val missing = scanOf("""{"@graph":[{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"],"image":{"@id":"#nowhere"}}]}""")
        assertNull(missing.recipes.single().image)
    }

    // ---- T11: microdata then RDFa ----

    private fun microdataOf(html: String): ExtractedRecipe? =
        MicrodataExtractor.recipe(Jsoup.parse("<html><body>$html</body></html>"))

    private fun title(recipe: ExtractedRecipe?): String? = (recipe?.title as? DraftValue.Stated)?.value

    @Test
    fun `a microdata Recipe gives title lines and method`() {
        val recipe = MicrodataExtractor.recipe(fixture("microdata.html"))!!
        assertEquals("Irish Stew", title(recipe))
        assertEquals(listOf("500 g lamb", "2 onions"), recipe.lines)
        assertEquals(listOf("Brown the lamb", "Simmer for an hour"), recipe.steps)
    }

    @Test
    fun `an itemprop belongs to its nearest enclosing itemscope`() {
        val recipe = MicrodataExtractor.recipe(fixture("microdata.html"))!!
        assertEquals("Irish Stew", title(recipe))
        assertTrue(recipe.lines.none { it.contains("not an ingredient") })
        val nutritionFirst = microdataOf(
            """<div itemscope itemtype="https://schema.org/Recipe"><div itemprop="nutrition" itemscope><span itemprop="name">Facts</span></div><h1 itemprop="name">Real</h1></div>""",
        )
        assertEquals("Real", title(nutritionFirst))
    }

    @Test
    fun `microdata times and yield follow the R6 rules`() {
        val recipe = MicrodataExtractor.recipe(fixture("microdata.html"))!!
        assertEquals("Stated(60)", described(recipe.cookingTime))
        assertEquals("Stated(4)", described(recipe.servings))
        val cookOnly = microdataOf(
            """<div itemscope itemtype="http://schema.org/Recipe"><time itemprop="cookTime" datetime="PT25M"></time><span itemprop="recipeYield">4-6</span></div>""",
        )!!
        assertEquals("Stated(25)", described(cookOnly.cookingTime))
        assertEquals("Absent", described(cookOnly.servings))
    }

    @Test
    fun `microdata instructions read li then p then the whole text`() {
        fun steps(inner: String) = microdataOf(
            """<div itemscope itemtype="https://schema.org/Recipe"><div itemprop="recipeInstructions">$inner</div></div>""",
        )!!.steps
        assertEquals(listOf("One", "Two"), steps("<ol><li>One</li><li>Two</li></ol><p>ignored</p>"))
        assertEquals(listOf("Alpha", "Beta"), steps("<p>Alpha</p><p>Beta</p>"))
        assertEquals(listOf("Just one block of text"), steps("Just one  block of\ntext"))
    }

    @Test
    fun `rdfa under a schema org vocab gives a recipe`() {
        val recipe = MicrodataExtractor.recipe(fixture("rdfa-vocab.html"))!!
        assertEquals("Irish Stew", title(recipe))
        assertEquals(listOf("500 g lamb", "2 onions"), recipe.lines)
        assertEquals(listOf("Brown the lamb and simmer."), recipe.steps)
        assertNull(microdataOf("""<div vocab="https://example.org/" typeof="Recipe"><h1 property="name">Other vocab</h1></div>"""))
    }

    @Test
    fun `rdfa with schema prefixed properties gives a recipe`() {
        val recipe = MicrodataExtractor.recipe(fixture("rdfa-prefix.html"))!!
        assertEquals("Irish Stew", title(recipe))
        assertEquals(listOf("500 g lamb", "2 onions"), recipe.lines)
        assertEquals(listOf("Brown the lamb", "Simmer"), recipe.steps)
        val legacy = microdataOf("""<div itemscope itemtype="https://schema.org/Recipe"><span itemprop="ingredients">1 egg</span></div>""")!!
        assertEquals(listOf("1 egg"), legacy.lines)
    }

    @Test
    fun `rdfa meta time and link values follow the value rules`() {
        val vocab = MicrodataExtractor.recipe(fixture("rdfa-vocab.html"))!!
        assertEquals("Stated(60)", described(vocab.cookingTime))
        assertEquals("Stated(4)", described(vocab.servings))
        val prefixed = MicrodataExtractor.recipe(fixture("rdfa-prefix.html"))!!
        assertEquals("Stated(60)", described(prefixed.cookingTime))
        assertEquals("Stated(6)", described(prefixed.servings))
    }

    @Test
    fun `rdfa is tried only when there is no microdata Recipe`() {
        val recipe = MicrodataExtractor.recipe(fixture("microdata-and-rdfa.html"))!!
        assertEquals("Microdata Stew", title(recipe))
        assertEquals(listOf("microdata lamb"), recipe.lines)
        assertNull(microdataOf("<p>Nothing here</p>"))
    }

    // ---- T12: heuristic tier ----

    private fun heuristic(html: String, head: String = ""): ExtractedRecipe? =
        HeuristicExtractor.recipe(Jsoup.parse("<html><head>$head</head><body>$html</body></html>"))

    @Test
    fun `an ingredient heading followed by a list gives its items`() {
        val recipe = HeuristicExtractor.recipe(fixture("heuristic.html"))!!
        assertEquals(listOf("500 g lamb", "2 onions", "1 litre stock"), recipe.lines)
        assertEquals(listOf("Brown the lamb", "Add the stock and simmer"), recipe.steps)
        val nested = heuristic("<h3>Ingredients</h3><div><ul><li>a</li><li>b</li></ul></div>")!!
        assertEquals(listOf("a", "b"), nested.lines)
    }

    @Test
    fun `a run of block siblings gives one item each`() {
        val recipe = HeuristicExtractor.recipe(fixture("heuristic-runs.html"))!!
        assertEquals(listOf("500 g lamb", "2 onions", "1 litre stock"), recipe.lines)
        assertEquals(listOf("Brown the lamb.", "Simmer for an hour."), recipe.steps)
    }

    @Test
    fun `the list stops at the next heading of the same or higher level`() {
        val recipe = heuristic("<h2>Ingredients</h2><p>a</p><p>b</p><h2>Notes</h2><ul><li>c</li><li>d</li></ul>")!!
        assertEquals(listOf("a", "b"), recipe.lines)
        val deeper = heuristic("<h2>Ingredients</h2><h3>Dry</h3><ul><li>flour</li><li>salt</li></ul><h2>Method</h2><ul><li>mix</li><li>bake</li></ul>")!!
        assertEquals(listOf("flour", "salt"), deeper.lines)
        assertEquals(listOf("mix", "bake"), deeper.steps)
        assertNull(heuristic("<h2>Ingredients</h2><h2>Method</h2><ul><li>mix</li><li>bake</li></ul>"))
    }

    @Test
    fun `fewer than two items is not an ingredient list`() {
        assertNull(heuristic("<h2>Ingredients</h2><ul><li>only one</li></ul>"))
        assertNull(heuristic("<h2>Ingredients</h2><ul><li>one</li><li>  </li></ul>"))
    }

    @Test
    fun `method headings match whole words only`() {
        val html = "<h2>Ingredients</h2><ul><li>a</li><li>b</li></ul>"
        assertEquals(listOf("x", "y"), heuristic("$html<h2>Instructions</h2><ul><li>x</li><li>y</li></ul>")!!.steps)
        assertEquals(listOf("x", "y"), heuristic("$html<h2>Steps</h2><ul><li>x</li><li>y</li></ul>")!!.steps)
        assertEquals(emptyList(), heuristic("$html<h2>Stepsister</h2><ul><li>x</li><li>y</li></ul>")!!.steps)
        assertEquals(emptyList(), heuristic("$html<h2>Methodology</h2><ul><li>x</li><li>y</li></ul>")!!.steps)
    }

    @Test
    fun `a method alone does not make a heuristic recipe`() {
        assertNull(heuristic("<h2>Method</h2><ol><li>mix</li><li>bake</li></ol>"))
    }

    @Test
    fun `the heuristic title is the first h1 else og title else title`() {
        val list = "<h2>Ingredients</h2><ul><li>a</li><li>b</li></ul>"
        val og = """<meta property="og:title" content=" OG  Title "><title>Doc Title</title>"""
        assertEquals("Heading", title(heuristic("<h1>Heading</h1><h1>Second</h1>$list", og)))
        assertEquals("OG Title", title(heuristic(list, og)))
        assertEquals("Doc Title", title(heuristic(list, "<title>Doc Title</title>")))
        assertNull(title(heuristic(list)))
    }

    @Test
    fun `the heuristic tier never states cooking time or yield`() {
        val recipe = HeuristicExtractor.recipe(fixture("heuristic.html"))!!
        assertEquals("Absent", described(recipe.cookingTime))
        assertEquals("Absent", described(recipe.servings))
    }

    // ---- T13: tier order, qualify rule, title carry-over ----

    private fun extract(name: String): Extraction? = RecipeExtractor.extract(fixture(name), Job())

    private fun extractedTitle(extraction: Extraction?): String? = (extraction?.recipe?.title as? DraftValue.Stated)?.value

    @Test
    fun `json ld wins over microdata and heuristic on one page`() {
        val extraction = extract("three-tier.html")!!
        assertEquals(ExtractionTier.JSON_LD, extraction.tier)
        assertEquals("JSON Title", extractedTitle(extraction))
        assertEquals(listOf("json lamb", "json onion"), extraction.recipe.lines)
        assertEquals(listOf("json step"), extraction.recipe.steps)
    }

    @Test
    fun `each lower tier records its own tier`() {
        assertEquals(ExtractionTier.JSON_LD, extract("jsonld-object.html")!!.tier)
        assertEquals(ExtractionTier.MICRODATA, extract("microdata.html")!!.tier)
        assertEquals(ExtractionTier.MICRODATA, extract("rdfa-vocab.html")!!.tier)
        assertEquals(ExtractionTier.HEURISTIC, extract("heuristic.html")!!.tier)
    }

    @Test
    fun `invalid json ld with microdata gives a microdata extraction`() {
        val extraction = extract("invalid-jsonld-with-microdata.html")!!
        assertEquals(ExtractionTier.MICRODATA, extraction.tier)
        assertEquals("Micro Title", extractedTitle(extraction))
    }

    @Test
    fun `a partial structured recipe takes no field from a lower tier`() {
        val extraction = extract("jsonld-lines-heuristic-method.html")!!
        assertEquals(ExtractionTier.JSON_LD, extraction.tier)
        assertEquals(listOf("json lamb"), extraction.recipe.lines)
        assertEquals(emptyList(), extraction.recipe.steps)
    }

    @Test
    fun `a title only structured recipe falls through to the first qualifying tier`() {
        assertEquals(ExtractionTier.HEURISTIC, extract("structured-no-ingredients.html")!!.tier)
        assertEquals(ExtractionTier.MICRODATA, extract("carried-title.html")!!.tier)
        val titleOnlyMicrodata = Jsoup.parse(
            """<div itemscope itemtype="https://schema.org/Recipe"><h1 itemprop="name">Only a title</h1></div><h2>Ingredients</h2><ul><li>a</li><li>b</li></ul>""",
        )
        assertEquals(ExtractionTier.HEURISTIC, RecipeExtractor.extract(titleOnlyMicrodata, Job())!!.tier)
    }

    @Test
    fun `the earliest stated structured title is carried to the lower tier`() {
        val carried = extract("carried-title.html")!!
        assertEquals("Earliest Title", extractedTitle(carried))
        assertEquals(listOf("micro lamb"), carried.recipe.lines)
        val heuristic = extract("structured-no-ingredients.html")!!
        assertEquals("Carried Title", extractedTitle(heuristic))
        assertEquals(listOf("heuristic lamb", "heuristic onion"), heuristic.recipe.lines)
        val microToHeuristic = Jsoup.parse(
            """<div itemscope itemtype="https://schema.org/Recipe"><h1 itemprop="name">Micro Only Title</h1></div><h1>H1</h1><h2>Ingredients</h2><ul><li>a</li><li>b</li></ul>""",
        )
        assertEquals("Micro Only Title", extractedTitle(RecipeExtractor.extract(microToHeuristic, Job())))
    }

    @Test
    fun `a heuristic title is used only when no structured tier stated one`() {
        assertEquals("Irish Stew", extractedTitle(extract("heuristic.html")))
        val untitled = Jsoup.parse("""<h1>Heading</h1>${ldScript("""{"@type":"Recipe","recipeIngredient":["x"]}""")}""")
        assertEquals(ExtractionTier.JSON_LD, RecipeExtractor.extract(untitled, Job())!!.tier)
        assertNull(extractedTitle(RecipeExtractor.extract(untitled, Job())))
    }

    private fun ldScript(json: String) = ld(json)

    @Test
    fun `malformed html with a truncated json ld block gives a heuristic extraction`() {
        val extraction = extract("malformed.html")!!
        assertEquals(ExtractionTier.HEURISTIC, extraction.tier)
        assertEquals(listOf("heuristic lamb", "heuristic onion"), extraction.recipe.lines)
    }

    private fun scriptElement(json: String): Element =
        Element("script").attr("type", "application/ld+json").appendChild(DataNode(json))

    /** A page with a shallow heuristic list and a JSON-LD script whose own depth is [scriptDepth] (`html` is 1). */
    private fun pageWithScriptAt(scriptDepth: Int): Document {
        val document = Jsoup.parse("<h2>Ingredients</h2><ul><li>shallow a</li><li>shallow b</li></ul>")
        var top: Element = scriptElement("""{"@type":"Recipe","name":"Deep","recipeIngredient":["deep lamb"]}""")
        repeat(scriptDepth - 3) { top = Element("div").also { wrapper -> wrapper.appendChild(top) } }
        document.body().appendChild(top)
        return document
    }

    @Test
    fun `a recipe below depth 512 is skipped and one at 512 is read`() {
        val read = RecipeExtractor.extract(pageWithScriptAt(512), Job())!!
        assertEquals(ExtractionTier.JSON_LD, read.tier)
        assertEquals(listOf("deep lamb"), read.recipe.lines)
        val skipped = RecipeExtractor.extract(pageWithScriptAt(513), Job())!!
        assertEquals(ExtractionTier.HEURISTIC, skipped.tier)
    }

    @Test
    fun `a maximal density page parses with fewer than 400000 nodes`() {
        val html = "<html><body>" + "<b>x</b>".repeat(99_995) + "</body></html>"
        val bytes = html.toByteArray()
        assertEquals(199_990, bytes.count { it == '<'.code.toByte() } - 4)
        val document = BoundedHtmlReader.parse(FetchedBody(bytes, BodyEncoding.IDENTITY, "text/html"), "https://example.ie/", DECOMPRESSED_CAP_BYTES, Job()) {}
        var nodes = 0
        document.traverse(NodeVisitor { _, _ -> nodes++ })
        assertTrue(nodes < 400_000, "nodes $nodes")
        assertNull(RecipeExtractor.extract(document, Job()))
    }

    @Test
    fun `extraction checks cancellation between tiers`() {
        val job = Job().apply { cancel() }
        assertFailsWith<CancellationException> { RecipeExtractor.extract(fixture("heuristic.html"), job) }
        assertFailsWith<CancellationException> { RecipeExtractor.extract(fixture("nothing-extracted.html"), job) }
    }

    @Test
    fun `extraction prints no content`() {
        val page = Jsoup.parse(
            ld("""{"@type":"Recipe","name":"${Sentinels.TITLE}","recipeIngredient":["${Sentinels.INGREDIENT}"]}""") + "<p>${Sentinels.URL}</p>",
        )
        val extraction = RecipeExtractor.extract(page, Job())!!
        assertTrue(!extraction.toString().contains("SENTINEL"), extraction.toString())
    }
}
