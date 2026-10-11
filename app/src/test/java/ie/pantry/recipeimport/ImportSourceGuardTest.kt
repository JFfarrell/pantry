package ie.pantry.recipeimport

import ie.pantry.testutil.RepoPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AD16: static guards over the main sources of both import packages, scanned with comments and KDoc stripped and
 * matched on word boundaries, so a documentation mention or an identifier such as `Dialog.` or `DaoLike` is never
 * flagged. The message check shares F4's blind spot (a message passed in by name is not seen); `ImportException`'s
 * `String`-free constructor and `ImportErrorHygieneTest` close it.
 */
class ImportSourceGuardTest {

    private val mainRoot = File(RepoPaths.repoRoot(), "app/src/main/java")
    private val packages = listOf("ie/pantry/recipeimport", "ie/pantry/ui/recipeimport")

    private fun sources(): List<File> = packages.flatMap { pkg ->
        File(mainRoot, pkg).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Source with comments and KDoc removed. String and character literals are kept as they are. */
    private fun stripped(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            when {
                c == '/' && next == '/' -> while (i < text.length && text[i] != '\n') i++
                c == '/' && next == '*' -> {
                    var depth = 1
                    i += 2
                    while (i < text.length && depth > 0) {
                        if (text.startsWith("/*", i)) {
                            depth++
                            i += 2
                        } else if (text.startsWith("*/", i)) {
                            depth--
                            i += 2
                        } else {
                            if (text[i] == '\n') out.append('\n')
                            i++
                        }
                    }
                }
                text.startsWith("\"\"\"", i) -> {
                    val end = text.indexOf("\"\"\"", i + 3).let { if (it < 0) text.length else it + 3 }
                    out.append(text, i, end)
                    i = end
                }
                c == '"' -> {
                    out.append(c)
                    i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\' && i + 1 < text.length) out.append(text[i++])
                        out.append(text[i++])
                    }
                    if (i < text.length) out.append(text[i++])
                }
                c == '\'' -> {
                    val end = text.indexOf('\'', i + (if (next == '\\') 3 else 2))
                    if (end in 0..i + 6) {
                        out.append(text, i, end + 1)
                        i = end + 1
                    } else {
                        out.append(c)
                        i++
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun matches(code: String, token: Regex): Boolean = token.containsMatchIn(code)

    private fun offenders(tokens: Map<String, Regex>): List<String> = sources().flatMap { file ->
        val code = stripped(file.readText())
        tokens.filter { (_, regex) -> matches(code, regex) }.keys.map { "${file.name}: $it" }
    }

    @Test
    fun `import packages use no other http api url object or web view`() {
        assertTrue(sources().size >= 18, "expected the import sources, found ${sources().size}")
        val tokens = mapOf(
            "import okhttp3" to Regex("""\bimport\s+okhttp3\."""),
            "java.net.URL" to Regex("""\bjava\.net\.URL\b"""),
            ".toURL()" to Regex("""\.toURL\(\)"""),
            "WebView" to Regex("""\bWebView\b"""),
            "Jsoup.connect" to Regex("""\bJsoup\.connect\b"""),
            "HttpConnection" to Regex("""\borg\.jsoup\.helper\.HttpConnection\b"""),
        )
        assertEquals(emptyList(), offenders(tokens))
    }

    @Test
    fun `import packages never reference the ingredient engine or persistence`() {
        val tokens = mapOf(
            "IngredientEngine" to Regex("""\bIngredientEngine\b"""),
            "RecipeRepository" to Regex("""\bRecipeRepository\b"""),
            "ThumbnailStore" to Regex("""\bThumbnailStore\b"""),
            "Dao" to Regex("""\bDao\b"""),
        )
        assertEquals(emptyList(), offenders(tokens))
    }

    @Test
    fun `import packages do not log or read the system clock`() {
        val tokens = mapOf(
            "Log." to Regex("""\bLog\."""),
            "println" to Regex("""\bprintln\b"""),
            "printStackTrace" to Regex("""\bprintStackTrace\b"""),
            "Instant.now(" to Regex("""\bInstant\.now\("""),
        )
        assertEquals(emptyList(), offenders(tokens))
    }

    private fun balanced(text: String, open: Int, openChar: Char, closeChar: Char): String {
        var depth = 0
        var inString = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else if (c == '"') {
                inString = true
            } else if (c == openChar) {
                depth++
            } else if (c == closeChar && --depth == 0) {
                return text.substring(open, i + 1)
            }
            i++
        }
        return text.substring(open)
    }

    private val literal = Regex("""^"[^"$\\]*"$""")
    private val literalLambda = Regex("""^\{\s*"[^"$\\]*"\s*\}$""")

    /** Every `throw T(...)`, `require`, `check` and `error` whose message is not a plain literal. */
    private fun badMessages(code: String): List<String> {
        val bad = mutableListOf<String>()
        for (match in Regex("""\b(throw\s+[\w.]+|require|check|error)\(""").findAll(code)) {
            val before = code.substring(maxOf(0, match.range.first - 10), match.range.first)
            if (match.value.startsWith("check") && (before.endsWith("ImportUrl.") || before.endsWith("fun "))) continue
            val open = match.range.last
            val arguments = balanced(code, open, '(', ')')
            val inner = arguments.substring(1, arguments.length - 1).trim()
            val after = code.substring(open + arguments.length).trimStart()
            val lambda = if (after.startsWith("{")) balanced(after, 0, '{', '}').trim() else ""
            val isThrow = match.value.startsWith("throw")
            val argumentOk = if (isThrow) inner.isEmpty() || literal.matches(inner) else true
            val lambdaOk = lambda.isEmpty() || literalLambda.matches(lambda)
            val inlineOk = isThrow || '$' !in inner
            if (!argumentOk || !lambdaOk || !inlineOk) bad += match.value + inner.take(40) + lambda.take(40)
        }
        return bad
    }

    @Test
    fun `import exception messages are dollar free literals`() {
        val offenders = sources().flatMap { file -> badMessages(stripped(file.readText())).map { "${file.name}: $it" } }
        assertEquals(emptyList(), offenders)
        assertTrue(sources().any { "require(" in stripped(it.readText()) }, "the scan must have something to scan")
    }

    @Test
    fun `every catch of Exception is preceded by a cancellation rethrow`() {
        val rethrow = Regex("""catch \(e: CancellationException\) \{\s*throw e\s*\}\s*catch \(e: Exception\)""")
        var total = 0
        val offenders = mutableListOf<String>()
        for (file in sources()) {
            val code = stripped(file.readText())
            val all = Regex("""catch \(e: Exception\)""").findAll(code).count()
            total += all
            if (rethrow.findAll(code).count() != all) offenders += file.name
        }
        assertEquals(emptyList(), offenders)
        assertTrue(total >= 2, "expected the importer's two defensive catches, found $total")
    }

    @Test
    fun `comments are stripped and tokens match on word boundaries`() {
        val snippet = """
            // Log.d("x") and println("y") in a line comment
            /** KDoc mentioning Instant.now( and java.net.URL and Dao */
            /* block with /* nested */ printStackTrace */
            val a = "a // not a comment Log.d"
            fun f() { Dialog.show(); DaoLike(); val d = Dao.insert() }
        """.trimIndent()
        val code = stripped(snippet)
        assertFalse(Regex("""\bprintStackTrace\b""").containsMatchIn(code))
        assertFalse(Regex("""\bInstant\.now\(""").containsMatchIn(code))
        assertFalse(Regex("""\bjava\.net\.URL\b""").containsMatchIn(code))
        assertFalse(Regex("""\bprintln\b""").containsMatchIn(code), "a line comment is stripped")
        assertTrue(Regex("""\bLog\.""").containsMatchIn(code), "a string literal is kept")
        assertFalse(Regex("""\bLog\.d\("x"\)""").containsMatchIn(code))
        assertFalse(Regex("""\bLog\.""").containsMatchIn(code.replace("not a comment Log.d", "")), "Dialog. is not Log.")
        assertTrue(Regex("""\bDao\b""").containsMatchIn(code), "Dao.insert is flagged")
        assertFalse(Regex("""\bDao\b""").containsMatchIn(code.replace("Dao.insert()", "")), "DaoLike is not flagged")
        assertEquals(emptyList(), badMessages("""require(x) { "constant" }; throw e; throw Failure()"""))
        assertTrue(badMessages("""require(x) { "n=$1" }""".replace("$1", "\$" + "n")).isNotEmpty())
        assertTrue(badMessages("""throw IllegalStateException(message)""").isNotEmpty())
    }
}
