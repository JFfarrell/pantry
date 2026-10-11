package ie.pantry.recipeimport

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import ie.pantry.data.gateway.GatewayException
import ie.pantry.testutil.TestGateways
import java.io.File
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.jsoup.Jsoup
import org.junit.After

class DeclineTest {

    private val routed = RoutedServer()

    @After
    fun tearDown() = routed.close()

    private fun draft(title: Boolean, lines: Boolean, method: Boolean) = ImportDraft(
        title = if (title) DraftValue.Stated("Stew") else DraftValue.Absent,
        ingredientLines = if (lines) listOf("1 onion") else emptyList(),
        method = if (method) DraftValue.Stated(listOf("Cook it")) else DraftValue.Absent,
        cookingTime = DraftValue.Absent,
        servings = DraftValue.Absent,
        image = DraftImage.NoImage(NoImageCause.NO_CANDIDATE),
        tier = ExtractionTier.JSON_LD,
        sourceUrl = "https://example.ie/stew",
        fetchedAt = Instant.EPOCH,
    )

    @Test
    fun `a draft with title ingredients and method is complete`() {
        assertEquals(Completeness.Complete, draft(title = true, lines = true, method = true).completeness)
    }

    @Test
    fun `each partial draft shape names what was found`() {
        val shapes = listOf(
            Triple(false, true, false),
            Triple(true, true, false),
            Triple(false, true, true),
            Triple(false, false, true),
            Triple(true, false, true),
        )
        for ((title, lines, method) in shapes) {
            assertEquals(
                Completeness.Partial(title, lines, method),
                draft(title, lines, method).completeness,
                "shape title=$title ingredients=$lines method=$method",
            )
        }
    }

    private fun extract(name: String): Extraction? =
        RecipeExtractor.extract(Jsoup.parse(String(ImportFixtures.page(name), Charsets.UTF_8)), Job())

    @Test
    fun `the nothing extracted page gives no extraction`() {
        assertNull(extract("nothing-extracted.html"))
    }

    @Test
    fun `a title only structured recipe with no heuristic list gives no extraction`() {
        val page = Jsoup.parse("""<script type="application/ld+json">{"@type":"Recipe","name":"Only a title"}</script><p>Nothing else</p>""")
        assertNull(RecipeExtractor.extract(page, Job()))
    }

    @Test
    fun `the parse badly page gives a heuristic extraction with no title or method`() {
        val extraction = extract("parse-badly.html")!!
        assertEquals(ExtractionTier.HEURISTIC, extraction.tier)
        assertEquals(listOf("1 onion", "2 eggs"), extraction.recipe.lines)
        assertEquals(DraftValue.Absent, extraction.recipe.title)
        assertEquals(emptyList(), extraction.recipe.steps)
    }

    // ---- T20: importer-level decline and draft-or-error ----

    @Test
    fun `the nothing extracted page is NOT_RECOGNISED with no image request`() = runTest {
        val withImage = String(ImportFixtures.page("nothing-extracted.html"))
            .replace("<title>", """<meta property="og:image" content="${routed.absolute("/pic.jpg")}"><title>""")
        routed.page("/nothing", withImage.toByteArray())
        routed.page("/pic.jpg", ByteArray(10), contentType = "image/jpeg")
        val result = ImportFixtures.importer(routed.server).importRecipe(routed.url("/nothing")) {}
        assertEquals(ImportException.Kind.NOT_RECOGNISED, assertIs<ImportResult.Failed>(result).error.kind)
        assertEquals(0, routed.requests("/pic.jpg"))
        assertEquals(1, routed.totalRequests)
    }

    @Test
    fun `the parse badly page is a partial draft with no error`() = runTest {
        routed.page("/bad", ImportFixtures.page("parse-badly.html"))
        val result = ImportFixtures.importer(routed.server).importRecipe(routed.url("/bad")) {}
        val draft = assertIs<ImportResult.Drafted>(result).draft
        assertEquals(Completeness.Partial(foundTitle = false, foundIngredients = true, foundMethod = false), draft.completeness)
    }

    @Test
    fun `every fixture ends in exactly one of a draft or a typed error`() = runTest {
        val directory = File(ImportFixtures::class.java.getResource("/recipeimport/pages")!!.toURI())
        val names = directory.list().orEmpty().filter { it.endsWith(".html") }
        assertTrue(names.isNotEmpty())
        val importer = ImportFixtures.importer(routed.server)
        for (name in names) {
            routed.page("/$name", ImportFixtures.page(name))
            val result = importer.importRecipe(routed.url("/$name")) {}
            when (result) {
                is ImportResult.Drafted -> Unit
                is ImportResult.Failed -> assertEquals(ImportException.Kind.NOT_RECOGNISED, result.error.kind, name)
            }
        }
        val malformed = importer.importRecipe(routed.url("/malformed.html")) {}
        assertIs<ImportResult.Drafted>(malformed, "the malformed page gives a draft")

        routed.page("/json", ImportFixtures.page("heuristic.html"), contentType = "application/json")
        assertEquals(ImportException.Kind.NOT_HTML, assertIs<ImportResult.Failed>(importer.importRecipe(routed.url("/json")) {}).error.kind)
        routed.route("/missing") { MockResponse().setResponseCode(404) }
        assertEquals(404, assertIs<ImportResult.Failed>(importer.importRecipe(routed.url("/missing")) {}).error.statusCode)
        routed.route("/silent") { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val timeout = assertIs<ImportResult.Failed>(importer.importRecipe(routed.url("/silent")) {}).error
        assertEquals(GatewayException.Category.TIMEOUT, timeout.gatewayCategory)
    }
}
