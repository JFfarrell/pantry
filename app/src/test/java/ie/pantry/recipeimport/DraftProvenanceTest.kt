package ie.pantry.recipeimport

import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.RepoPaths
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class DraftProvenanceTest {

    private val routed = RoutedServer()

    @After
    fun tearDown() = routed.close()

    @Test
    fun `import draft declares no nullable property`() {
        val source = File(RepoPaths.repoRoot(), "app/src/main/java/ie/pantry/recipeimport/ImportDraft.kt").readText()
        val code = source
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }
        val declaration = Regex("""\b(?:val|var)\s+\w+\s*:\s*([^=,){\n]+)""")
        val types = declaration.findAll(code).map { it.groupValues[1].trim() }.toList()
        assertTrue(types.size >= 10, "the scan found only ${types.size} declarations")
        val nullable = types.filter { it.endsWith("?") }
        assertTrue(nullable.isEmpty(), "nullable declared types: $nullable")
    }

    private fun stated(value: Int): DraftValue<Int> = DraftValue.Stated(value)

    private fun assertMinutes(expected: DraftValue<Int>, iso: String?) {
        val actual = RecipeValues.minutes(iso)
        assertEquals(describe(expected), describe(actual), "duration $iso")
    }

    private fun describe(value: DraftValue<Int>): String =
        if (value is DraftValue.Stated) "Stated(${value.value})" else "Absent"

    private fun assertYield(expected: DraftValue<Int>, actual: DraftValue<Int>, what: String) =
        assertEquals(describe(expected), describe(actual), "yield $what")

    @Test
    fun `total time PT1H15M is 75 minutes`() {
        assertEquals(describe(stated(75)), describe(RecipeValues.cookingMinutes("PT1H15M", null)))
        assertEquals(describe(stated(75)), describe(RecipeValues.cookingMinutes("PT1H15M", "PT40M")))
    }

    @Test
    fun `cook time is used when total time is missing zero or unparseable`() {
        assertEquals(describe(stated(40)), describe(RecipeValues.cookingMinutes(null, "PT40M")))
        assertEquals(describe(stated(40)), describe(RecipeValues.cookingMinutes("PT0M", "PT40M")))
        assertEquals(describe(stated(40)), describe(RecipeValues.cookingMinutes("about an hour", "PT40M")))
        assertEquals(describe(DraftValue.Absent), describe(RecipeValues.cookingMinutes(null, null)))
    }

    @Test
    fun `missing empty zero day designated and unparseable durations are absent`() {
        for (iso in listOf(null, "", "PT", "PT0M", "about an hour", "P1DT2H", "P1D", "PT-5M", "P1Y", "PT1W", "PT29S", "PT0.5S", "PT1,5S")) {
            assertMinutes(DraftValue.Absent, iso)
        }
    }

    @Test
    fun `seconds round half up to whole minutes and zero is absent`() {
        assertMinutes(stated(1), "PT30S")
        assertMinutes(stated(2), "PT90S")
        assertMinutes(DraftValue.Absent, "PT29S")
        assertMinutes(stated(30), "PT29M30S")
    }

    @Test
    fun `durations beyond the digit limits are absent and designators are case insensitive`() {
        assertMinutes(DraftValue.Absent, "PT99999H")
        assertMinutes(DraftValue.Absent, "PT100000M")
        assertMinutes(stated(30), "pt30m")
        assertMinutes(stated(60), "pT1h")
        assertMinutes(stated(9999 * 60), "PT9999H")
    }

    @Test
    fun `a bare integer or serves servings portions yield is stated`() {
        val json = Json
        val cases: List<Pair<String, DraftValue<Int>>> = listOf(
            "\"4\"" to RecipeValues.servings(json.parseToJsonElement("\"4\"")),
            "4" to RecipeValues.servings(json.parseToJsonElement("4")),
            "Serves 4" to RecipeValues.servings(json.parseToJsonElement("\"Serves 4\"")),
            "array" to RecipeValues.servings(json.parseToJsonElement("[\"4\", \"4 servings\"]")),
            "Serves: 4" to RecipeValues.servings("Serves: 4"),
            "servings: 4" to RecipeValues.servings("servings: 4"),
            "4 portions" to RecipeValues.servings("4 portions"),
        )
        for ((what, actual) in cases) assertYield(stated(4), actual, what)
        assertYield(stated(1000), RecipeValues.servings(json.parseToJsonElement("1000")), "1000")
        assertYield(stated(Int.MAX_VALUE), RecipeValues.servings(JsonPrimitive(Int.MAX_VALUE)), "Int.MAX_VALUE")
    }

    @Test
    fun `ranges units words zero negatives and non integers give an absent yield`() {
        val json = Json
        assertYield(DraftValue.Absent, RecipeValues.servings(null as kotlinx.serialization.json.JsonElement?), "missing")
        for (text in listOf("4-6", "a crowd", "2 dozen", "12 cookies", "0", "", "99999999999")) {
            assertYield(DraftValue.Absent, RecipeValues.servings(text), text)
        }
        for (raw in listOf("0", "-1", "4.0", "${Int.MAX_VALUE.toLong() + 1}", "null", "true", "{}", "[]")) {
            assertYield(DraftValue.Absent, RecipeValues.servings(json.parseToJsonElement(raw)), raw)
        }
    }

    // ---- T20: importer-level provenance ----

    private fun draftOf(importer: RecipeImporter, path: String = "/stew"): ImportDraft =
        assertIs<ImportResult.Drafted>(kotlinx.coroutines.runBlocking { importer.importRecipe(routed.url(path)) {} }).draft

    private fun ld(json: String) = """<html><head><script type="application/ld+json">$json</script></head><body></body></html>""".toByteArray()

    @Test
    fun `sourceUrl is the validated url not the redirect target`() = runTest {
        routed.route("/start") { MockResponse().setResponseCode(302).setHeader("Location", "/stew") }
        routed.page("/stew", ImportFixtures.page("heuristic.html"))
        val draft = draftOf(ImportFixtures.importer(routed.server), "/start")
        assertEquals(routed.absolute("/start"), draft.sourceUrl)
        assertEquals(1, routed.requests("/stew"))
    }

    @Test
    fun `fetchedAt is the clock instant when the page fetch returned`() = runTest {
        val a = Instant.parse("2026-02-01T00:00:00Z")
        val b = Instant.parse("2026-02-01T00:00:05Z")
        val c = Instant.parse("2026-02-01T00:00:09Z")
        val clock = MutableClock(a)
        routed.page("/stew", ld("""{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"],"image":"/pic.jpg"}""")) { clock.set(b) }
        routed.page("/pic.jpg", ByteArray(50) { 1 }, contentType = "image/jpeg") { clock.set(c) }
        val importer = ImportFixtures.importer(routed.server, clock, imageCheck = { DraftImage.Validated(it, 1, 1) })
        val draft = draftOf(importer)
        assertIs<DraftImage.Validated>(draft.image)
        assertEquals(1, routed.requests("/pic.jpg"))
        assertEquals(b, draft.fetchedAt)
        assertEquals(c, clock.instant())
    }

    @Test
    fun `a stated cooking time is captured and a missing one is absent`() = runTest {
        routed.page("/timed", ld("""{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"],"totalTime":"PT45M","recipeYield":"Serves 6"}"""))
        routed.page("/untimed", ld("""{"@type":"Recipe","name":"X","recipeIngredient":["1 egg"]}"""))
        val importer = ImportFixtures.importer(routed.server)
        val timed = draftOf(importer, "/timed")
        assertEquals("Stated(45)", describe(timed.cookingTime))
        assertEquals("Stated(6)", describe(timed.servings))
        val untimed = draftOf(importer, "/untimed")
        assertEquals("Absent", describe(untimed.cookingTime))
        assertEquals("Absent", describe(untimed.servings))
    }

    private fun fixtureNames(): List<String> {
        val directory = File(ImportFixtures::class.java.getResource("/recipeimport/pages")!!.toURI())
        return directory.list().orEmpty().filter { it.endsWith(".html") }.sorted()
    }

    /** Fixtures that are not recipes, or are bodies for other tests, so they end in `NOT_RECOGNISED`. */
    private val notRecipeFixtures = setOf(
        "nothing-extracted.html", "windows-1252-meta.html", "windows-1252-header.html",
        "image-inline.html", "image-base-href.html", "image-og.html",
    )

    @Test
    fun `every draft producing fixture has only stated or absent optional fields`() = runTest {
        val names = fixtureNames()
        assertTrue(names.isNotEmpty())
        val importer = ImportFixtures.importer(routed.server)
        var drafted = 0
        for (name in names) {
            routed.page("/$name", ImportFixtures.page(name))
            val result = importer.importRecipe(routed.url("/$name")) {}
            if (name in notRecipeFixtures) {
                assertEquals(ImportException.Kind.NOT_RECOGNISED, assertIs<ImportResult.Failed>(result, name).error.kind, name)
                continue
            }
            val draft = assertIs<ImportResult.Drafted>(result, name).draft
            drafted++
            when (val title = draft.title) {
                is DraftValue.Stated -> assertTrue(title.value.isNotBlank(), "$name title")
                DraftValue.Absent -> Unit
            }
            when (val method = draft.method) {
                is DraftValue.Stated -> assertTrue(method.value.isNotEmpty() && method.value.all { it.isNotEmpty() }, "$name method")
                DraftValue.Absent -> Unit
            }
            for (value in listOf(draft.cookingTime, draft.servings)) {
                if (value is DraftValue.Stated) assertTrue(value.value >= 1, "$name numeric field")
            }
            assertTrue(draft.ingredientLines.all { it.isNotEmpty() }, "$name lines")
            assertIs<DraftImage>(draft.image)
        }
        assertTrue(drafted >= 15, "only $drafted fixtures produced a draft")
    }

    @Test
    fun `a heuristic page stating time and yield leaves both absent`() = runTest {
        routed.page("/h", ImportFixtures.page("heuristic.html"))
        val draft = draftOf(ImportFixtures.importer(routed.server), "/h")
        assertEquals(ExtractionTier.HEURISTIC, draft.tier)
        assertEquals("Absent", describe(draft.cookingTime))
        assertEquals("Absent", describe(draft.servings))
    }
}
