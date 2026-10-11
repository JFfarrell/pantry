package ie.pantry.ui.recipeimport

import ie.pantry.R
import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.recipeimport.Completeness
import ie.pantry.recipeimport.ImportException
import ie.pantry.recipeimport.ImportException.Kind
import ie.pantry.testutil.Sentinels
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** R5 AC4 and R2 AC4/AC5: the message for each failure, and the rules every import string must obey. */
@RunWith(RobolectricTestRunner::class)
class ImportMessagesTest {

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun fetch(category: Category, status: Int? = null) =
        ImportException(Kind.FETCH_FAILED, category, status, null)

    private fun messageFor(error: ImportException): Int = ImportMessages.of(failureOf(error))

    @Test
    fun `each failure and gateway category maps to its Q6 message`() {
        val expected = mapOf(
            fetch(Category.TIMEOUT) to R.string.import_error_timeout,
            fetch(Category.CONNECTION_FAILED) to R.string.import_error_connection,
            fetch(Category.ADDRESS_REFUSED) to R.string.import_error_address_refused,
            fetch(Category.SCHEME_REFUSED) to R.string.import_error_redirect_refused,
            fetch(Category.TOO_MANY_REDIRECTS) to R.string.import_error_too_many_redirects,
            fetch(Category.INVALID_REQUEST) to R.string.import_error_bad_link,
            fetch(Category.UNEXPECTED) to R.string.import_error_unexpected,
            fetch(Category.RESPONSE_TOO_LARGE) to R.string.import_error_too_large,
            ImportException(Kind.NOT_HTML, null, null, null) to R.string.import_error_not_html,
            ImportException(Kind.UNSUPPORTED_ENCODING, null, null, null) to R.string.import_error_unsupported_encoding,
            ImportException(Kind.UNREADABLE_CONTENT, null, null, null) to R.string.import_error_unreadable,
            ImportException(Kind.CONTENT_TOO_LARGE, null, null, null) to R.string.import_error_too_large,
            ImportException(Kind.NOT_RECOGNISED, null, null, null) to R.string.import_error_not_recognised,
        )
        for ((error, id) in expected) assertEquals(id, messageFor(error), error.toString())
        assertEquals(Category.values().toSet() - Category.HTTP_STATUS, expected.keys.mapNotNull { it.gatewayCategory }.toSet(), "every category but HTTP_STATUS, which has its own test")
    }

    @Test
    fun `http statuses map to their message at each boundary`() {
        val expected = mapOf(
            401 to R.string.import_error_http_denied,
            402 to R.string.import_error_http_other,
            403 to R.string.import_error_http_denied,
            404 to R.string.import_error_http_404,
            499 to R.string.import_error_http_other,
            500 to R.string.import_error_http_5xx,
            599 to R.string.import_error_http_5xx,
            600 to R.string.import_error_http_other,
        )
        for ((status, id) in expected) assertEquals(id, messageFor(fetch(Category.HTTP_STATUS, status)), "status $status")
    }

    private fun othersThan(id: Int): List<String> = ImportMessages.ALL.filter { it != id }.map(::text)

    @Test
    fun `the decline message differs from every other import message`() {
        val decline = text(R.string.import_error_not_recognised)
        assertFalse(othersThan(R.string.import_error_not_recognised).contains(decline))
    }

    @Test
    fun `the unreadable message differs from every other import message`() {
        val unreadable = text(R.string.import_error_unreadable)
        assertFalse(othersThan(R.string.import_error_unreadable).contains(unreadable))
    }

    @Test
    fun `not html differs from unsupported encoding and no link differs from invalid url`() {
        assertNotEquals(text(R.string.import_error_not_html), text(R.string.import_error_unsupported_encoding))
        assertNotEquals(text(R.string.import_notice_no_link), text(R.string.import_error_invalid_url))
    }

    @Test
    fun `recipe found differs from every partly found notice`() {
        val found = text(R.string.import_notice_recipe_found)
        val partly = listOf(
            R.string.import_notice_partly_ingredients, R.string.import_notice_partly_ingredients_title,
            R.string.import_notice_partly_ingredients_method, R.string.import_notice_partly_method,
            R.string.import_notice_partly_method_title,
        ).map(::text)
        assertEquals(5, partly.toSet().size)
        for (notice in partly) assertNotEquals(found, notice)
        // each completeness shape reaches its own notice
        val shapes = listOf(
            Completeness.Partial(foundTitle = false, foundIngredients = true, foundMethod = false) to R.string.import_notice_partly_ingredients,
            Completeness.Partial(foundTitle = true, foundIngredients = true, foundMethod = false) to R.string.import_notice_partly_ingredients_title,
            Completeness.Partial(foundTitle = false, foundIngredients = true, foundMethod = true) to R.string.import_notice_partly_ingredients_method,
            Completeness.Partial(foundTitle = false, foundIngredients = false, foundMethod = true) to R.string.import_notice_partly_method,
            Completeness.Partial(foundTitle = true, foundIngredients = false, foundMethod = true) to R.string.import_notice_partly_method_title,
        )
        for ((shape, id) in shapes) assertEquals(id, ImportMessages.draftNotice(shape), shape.toString())
        assertEquals(R.string.import_notice_recipe_found, ImportMessages.draftNotice(Completeness.Complete))
    }

    @Test
    fun `no import message has a format argument a sentinel a tag or a url`() {
        assertTrue(ImportMessages.ALL.isNotEmpty())
        for (id in ImportMessages.ALL) {
            val message = text(id)
            assertFalse(message.contains('%'), "format argument in '$message'")
            assertFalse(message.contains('<'), "tag in '$message'")
            for (sentinel in Sentinels.all) assertFalse(message.contains(sentinel, ignoreCase = true), "sentinel in '$message'")
            if (id == R.string.import_error_invalid_url) {
                assertTrue(message.contains("http://") && message.contains("https://"), message)
            } else {
                assertFalse(message.contains("http", ignoreCase = true), "url in '$message'")
            }
        }
    }

    @Test
    fun `ALL lists every import string resource`() {
        val declared = R.string::class.java.fields.filter { it.name.startsWith("import_") }.map { it.getInt(null) }
        assertEquals(declared.toSet(), ImportMessages.ALL.toSet())
        assertEquals(declared.size, ImportMessages.ALL.size, "no duplicates")
        assertTrue(ImportMessages.ALL.size >= 34)
    }
}
