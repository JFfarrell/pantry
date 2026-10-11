package ie.pantry.recipeimport

import ie.pantry.testutil.Sentinels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportUrlTest {

    private fun valid(text: String): String {
        val check = ImportUrl.check(text)
        assertIs<UrlCheck.Valid>(check, "expected Valid for the input")
        return check.url.value
    }

    private fun reason(text: String): InvalidReason {
        val check = ImportUrl.check(text)
        assertIs<UrlCheck.Invalid>(check, "expected Invalid for the input")
        return check.reason
    }

    @Test
    fun `check trims surrounding whitespace and returns the validated url`() {
        assertEquals("https://example.ie/stew", valid("  https://example.ie/stew  "))
    }

    @Test
    fun `check rejects every R1 rejection input before any fetch`() {
        assertEquals(InvalidReason.NO_URL, reason("ftp://example.ie/a"))
        assertEquals(InvalidReason.NO_URL, reason("intent://x#Intent;end"))
        assertEquals(InvalidReason.NO_URL, reason("content://x/y"))
        assertEquals(InvalidReason.NO_URL, reason("javascript:alert(1)"))
        assertEquals(InvalidReason.NO_URL, reason("www.example.ie/stew"))
        assertEquals(InvalidReason.NO_URL, reason("   "))
        assertEquals(InvalidReason.NO_HOST, reason("https://"))
        assertEquals(InvalidReason.USERINFO, reason("https://user:pw@example.ie/a"))
    }

    @Test
    fun `a scheme less input is rejected not prefixed`() {
        assertEquals(InvalidReason.NO_URL, reason("example.ie/stew"))
        assertNull(ImportUrl.pick("www.example.ie/stew"))
    }

    @Test
    fun `pick returns the first url at a boundary and drops trailing punctuation`() {
        assertEquals("https://example.ie/stew/", ImportUrl.pick("(see https://example.ie/stew/).")?.text)
        assertEquals("https://example.ie/stew", ImportUrl.pick("https://example.ie/stew,")?.text)
        assertEquals("https://example.ie/stew", ImportUrl.pick("Try this https://example.ie/stew so good")?.text)
    }

    @Test
    fun `pick ignores urls that do not start at a boundary`() {
        assertNull(ImportUrl.pick("xhttps://a"))
        assertNull(ImportUrl.pick("url=https://a"))
        assertNull(ImportUrl.pick("ftp://x/?u=https://evil"))
        assertNull(ImportUrl.pick("intent://x#Intent;S.browser_fallback_url=https://evil;end"))
    }

    @Test
    fun `pick matches an upper case scheme and an angle bracketed url`() {
        assertEquals("HTTPS://example.ie/stew", ImportUrl.pick("HTTPS://example.ie/stew")?.text)
        assertEquals("https://example.ie/stew", ImportUrl.pick("<https://example.ie/stew>")?.text)
    }

    @Test
    fun `pick keeps a balanced closing bracket and drops an unbalanced one`() {
        assertEquals("https://example.ie/Foo_(bar)", ImportUrl.pick("https://example.ie/Foo_(bar)")?.text)
        assertEquals("https://example.ie/Foo_(bar)", ImportUrl.pick("(https://example.ie/Foo_(bar))")?.text)
        assertEquals("https://example.ie/a", ImportUrl.pick("[https://example.ie/a]")?.text)
        assertEquals("https://example.ie/a[1]", ImportUrl.pick("https://example.ie/a[1]")?.text)
    }

    @Test
    fun `an apostrophe ends the url only when a quote opened it`() {
        assertEquals("https://example.ie/stew", ImportUrl.pick("'https://example.ie/stew'")?.text)
        assertEquals("https://example.ie/grandma's-stew", ImportUrl.pick("https://example.ie/grandma's-stew")?.text)
    }

    @Test
    fun `userinfo is an at sign before the first path query fragment or backslash`() {
        assertEquals("https://example.ie/a@b", valid("https://example.ie/a@b"))
        assertEquals("https://example.ie?x=@y", valid("https://example.ie?x=@y"))
        assertEquals(InvalidReason.USERINFO, reason("https://a@example.ie/"))
        assertEquals(InvalidReason.USERINFO, reason("https://a@example.ie"))
        assertEquals("https://ex\\ample@b/", valid("https://ex\\ample@b/"))
    }

    @Test
    fun `the picker takes the first match and does not skip past a userinfo url`() {
        assertEquals(
            InvalidReason.USERINFO,
            reason("https://u@evil.example/a then https://example.ie/stew"),
        )
    }

    @Test
    fun `a url reaching the scan limit is over limit for pick and check`() {
        val prefix = "https://example.ie/"
        val exact = prefix + "a".repeat(ImportUrl.SCAN_LIMIT - prefix.length)
        val longer = exact + "b".repeat(50)
        for (url in listOf(exact, longer)) {
            val picked = ImportUrl.pick(url)
            assertEquals(url, picked?.text)
            assertTrue(picked!!.overLimit)
            assertEquals(InvalidReason.OVER_LIMIT, reason(url))
        }
        val within = prefix + "a".repeat(ImportUrl.SCAN_LIMIT - prefix.length - 5)
        assertFalse(ImportUrl.pick(within)!!.overLimit)
    }

    @Test
    fun `a link starting after the scan limit is not found`() {
        val text = " ".repeat(ImportUrl.SCAN_LIMIT + 1) + "https://example.ie/stew"
        assertNull(ImportUrl.pick(text))
        assertEquals(InvalidReason.NO_URL, reason(text))
    }

    @Test
    fun `hostile bracket text is scanned in linear time`() {
        fun hostile(n: Int) = "https://a/" + ")]".repeat(n)
        fun timeOf(text: String): Long {
            val start = System.nanoTime()
            ImportUrl.pick(text)
            return System.nanoTime() - start
        }
        timeOf(hostile(1_000))
        val n = 250_000
        val one = timeOf(hostile(n))
        val two = timeOf(hostile(2 * n))
        assertTrue(two < 4 * maxOf(one, 5_000_000L), "2N took $two ns against $one ns for N")
        assertTrue(two < 5_000_000_000L)
    }

    @Test
    fun `url intake types print no url`() {
        val validated = (ImportUrl.check(Sentinels.URL) as UrlCheck.Valid)
        val picked = ImportUrl.pick(Sentinels.URL)!!
        for (text in listOf(validated.url.toString(), validated.toString(), picked.toString())) {
            assertFalse(text.contains("SENTINEL"), text)
            assertFalse(text.contains("sentinel"), text)
        }
    }
}
