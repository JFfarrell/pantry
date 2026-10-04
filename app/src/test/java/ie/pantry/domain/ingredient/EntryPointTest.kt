package ie.pantry.domain.ingredient

import ie.pantry.data.reference.AliasTable
import ie.pantry.testutil.RepoPaths
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** R1 AC1, AC3: the single raw-text entry point, and totality over edge-case lines. Plain JVM
 * (R9 AC2) — this test never loads a shipped table. */
class EntryPointTest {

    private val emptyTable = AliasTable(emptyList())

    // ---- AC1: only IngredientEngine.parse accepts raw line text ----

    @Test
    fun `only IngredientEngine parse accepts raw line text`() {
        val sourceDir = RepoPaths.repoRoot().resolve("app/src/main/java/ie/pantry/domain/ingredient")
        val sourceText = sourceDir.listFiles { f -> f.extension == "kt" }
            .orEmpty()
            .joinToString("\n") { it.readText() }

        assertEquals(listOf("IngredientEngine.parse"), rawTextAcceptors(sourceText))
    }

    @Test
    fun `raw text detector flags internal helpers String receivers and multi line parameter lists`() {
        val internalHelper = "internal fun keyOnly(line: String): CanonicalKey = CanonicalKey.Absent"
        assertTrue(rawTextAcceptors(internalHelper).isNotEmpty(), "internal fun with a String parameter must be flagged")

        val stringReceiver = "fun String.quantityOnly(): Quantity = Quantity.Unquantified"
        assertTrue(rawTextAcceptors(stringReceiver).isNotEmpty(), "a String receiver function must be flagged")

        val multiLineParams = """
            fun helper(
                line: String,
                other: Int,
            ): Unit {}
        """.trimIndent()
        assertTrue(rawTextAcceptors(multiLineParams).isNotEmpty(), "a multi-line parameter list carrying String must be flagged")
    }

    @Test
    fun `raw text detector ignores a private helper`() {
        val privateHelper = "private fun helper(line: String): CanonicalKey = CanonicalKey.Absent"
        assertTrue(rawTextAcceptors(privateHelper).isEmpty(), "a private fun must never be flagged")
    }

    @Test
    fun `scanned line is sealed with a single private implementer`() {
        assertTrue(ScannedLine::class.java.isInterface)
        assertTrue(Modifier.isAbstract(ScannedLine::class.java.modifiers))

        val pkgDir = RepoPaths.repoRoot().resolve("app/src/main/java/ie/pantry/domain/ingredient")
        val implementors = pkgDir.listFiles { f -> f.extension == "kt" }.orEmpty()
            .flatMap { it.readLines() }
        // Source-level check: exactly one `: ScannedLine` implementation, and it is `private`.
        val implementerLines = implementors.filter { it.contains(": ScannedLine") && (it.contains(" class ") || it.trimStart().startsWith("class ")) }
        assertEquals(1, implementerLines.size, "expected exactly one ScannedLine implementer, found: $implementerLines")
        assertTrue(implementerLines.single().trimStart().startsWith("private "), "the ScannedLine implementer must be private")
    }

    @Test
    fun `IngredientEngine exposes exactly one public method taking a String`() {
        val publicStringMethods = IngredientEngine::class.java.methods.filter { method ->
            Modifier.isPublic(method.modifiers) && method.parameterTypes.any { it == String::class.java }
        }
        assertEquals(1, publicStringMethods.size, "expected exactly one public String-taking method, found: $publicStringMethods")
        assertEquals("parse", publicStringMethods.single().name)
    }

    // ---- AC3: totality over R1's numeric and structural edge cases ----

    @Test
    fun `every R1 edge case returns a typed result without throwing`() {
        val engine = IngredientEngine(emptyTable)
        val lines = listOf(
            "",
            "   ",
            "a".repeat(10_000),
            "\u0000\u0001\u0002",
            "🍕🥕",
            "1/0 g",
            "0 g",
            "-2 eggs",
            "1e999 g",
            "99999999999999999999 g",
            "½½ tsp",
        )
        for (line in lines) {
            val result = engine.parse(line)
            assertEquals(Quantity.Unquantified, result.quantity, message = "line ${line.take(20)}... must be unquantified")
        }
    }

    @Test
    fun `edge cases with no name text give an absent key`() {
        val engine = IngredientEngine(emptyTable)
        val absentCases = listOf(
            "",
            "   ",
            "a".repeat(10_000),
            "\u0000\u0001\u0002",
            "🍕🥕",
            "1/0 g",
            "0 g",
            "1e999 g",
            "99999999999999999999 g",
            "½½ tsp",
        )
        for (line in absentCases) {
            assertEquals(CanonicalKey.Absent, engine.parse(line).key, message = "line ${line.take(20)}... must give an absent key")
        }
    }

    // ---- T11: -2 eggs' key, deferred here from T6 since it needs the full parser and rule ----

    @Test
    fun `a negative count line keeps its name as the key`() {
        val engine = IngredientEngine(emptyTable)
        val result = engine.parse("-2 eggs")
        assertEquals(Quantity.Unquantified, result.quantity)
        assertEquals(CanonicalKey.Derived("egg"), result.key)
    }

    // ---- Test-local static scanner mirroring AD4's rawTextAcceptors contract. This is a
    // best-effort secondary check; the real enforcement is the sealed ScannedLine gate (AD4). ----

    private fun rawTextAcceptors(sourceText: String): List<String> {
        val results = mutableListOf<String>()
        var currentType: String? = null
        val funPattern = Regex(
            """(?m)^([ \t]*)(internal |public )?(private )?fun\s+(?:<[^>]*>\s*)?(?:(\w+)\.)?(\w+)\s*\(""",
        )
        val typePattern = Regex("""(?m)^\s*(?:internal\s+|private\s+|public\s+)?(?:sealed\s+|open\s+|abstract\s+|final\s+|data\s+)*(?:class|object|interface)\s+(\w+)""")
        val propPattern = Regex(
            """(?m)^([ \t]*)(internal |public )?(private )?(val|var)\s+(\w+)\s*:\s*\(([^)]*)\)\s*->""",
        )

        // Track the nearest preceding top-level type declaration for qualification.
        val typeStarts = typePattern.findAll(sourceText).map { it.range.first to it.groupValues[1] }.toList()
        fun typeFor(index: Int): String? = typeStarts.lastOrNull { it.first <= index }?.second

        for (match in funPattern.findAll(sourceText)) {
            val isPrivate = match.groupValues[3] == "private "
            if (isPrivate) continue

            val closeIdx = sourceText.indexOf(')', match.range.last)
            if (closeIdx < 0) continue
            val paramList = sourceText.substring(match.range.last + 1, closeIdx)
            val receiverType = match.groupValues[4]
            val funcName = match.groupValues[5]

            val receiverIsStringLike = receiverType == "String" || receiverType == "CharSequence"
            val paramIsStringLike = Regex("""(?::|,)\s*(String|CharSequence)\??(\s*[,=)]|\s*$)""").containsMatchIn(": $paramList)")

            if (receiverIsStringLike || paramIsStringLike) {
                currentType = typeFor(match.range.first)
                val name = if (currentType != null) "$currentType.$funcName" else funcName
                results.add(name)
            }
        }

        for (match in propPattern.findAll(sourceText)) {
            val isPrivate = match.groupValues[3] == "private "
            if (isPrivate) continue
            val funcType = match.groupValues[6]
            if (funcType.contains("String") || funcType.contains("CharSequence")) {
                val propName = match.groupValues[5]
                val type = typeFor(match.range.first)
                results.add(if (type != null) "$type.$propName" else propName)
            }
        }

        return results
    }
}
