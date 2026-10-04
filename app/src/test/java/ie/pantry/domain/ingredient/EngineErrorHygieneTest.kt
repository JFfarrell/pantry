package ie.pantry.domain.ingredient

import ie.pantry.data.reference.AliasTable
import ie.pantry.testutil.Sentinels
import ie.pantry.testutil.RepoPaths
import ie.pantry.testutil.assertNoSentinel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.Test

/** R10, CFC-4: no exception or failure value carries any input text. The source-inspection
 * half and the constructor-guard cases are built here (T7); the sentinel and throwing-seam
 * behaviour cases are completed in T16. */
class EngineErrorHygieneTest {

    @Test
    fun `main sources contain no throw error call or logging`() {
        for (file in mainSourceFiles()) {
            val text = file.readText()
            assertFalse(Regex("""\bthrow\b""").containsMatchIn(text), "${file.name} must not contain throw")
            assertFalse(text.contains("error("), "${file.name} must not call error(")
            assertFalse(text.contains("Log."), "${file.name} must not log")
            assertFalse(text.contains("println("), "${file.name} must not println")
            assertFalse(text.contains("printStackTrace"), "${file.name} must not printStackTrace")
            assertFalse(text.contains("System.err"), "${file.name} must not write to System.err")
        }
    }

    @Test
    fun `every require and check message is a constant literal`() {
        val callPattern = Regex("""\b(?:require|check)\((?:[^()]|\([^()]*\))*\)\s*\{\s*([^}]*?)\s*\}""")
        val constantRef = Regex("""[A-Z][A-Z0-9_]*""")
        val literal = Regex(""""[^"$]*"""")
        var checked = 0
        for (file in mainSourceFiles()) {
            for (match in callPattern.findAll(file.readText())) {
                checked++
                val body = match.groupValues[1]
                assertTrue(
                    constantRef.matches(body) || literal.matches(body),
                    "${file.name}: require/check message is not a constant: ${match.value}",
                )
            }
        }
        assertTrue(checked > 0, "no require/check call sites were found, so the scan proves nothing")
    }

    @Test
    fun `constructor guard exceptions carry no sentinel`() {
        val fromMeasured = assertFailsWith<IllegalArgumentException> { Quantity.Measured(0.0, MeasureUnit.G) }
        fromMeasured.assertNoSentinel()

        val fromDerived = assertFailsWith<IllegalArgumentException> { CanonicalKey.Derived("") }
        fromDerived.assertNoSentinel()
    }

    private fun mainSourceFiles(): List<java.io.File> {
        val dir = java.io.File(RepoPaths.repoRoot(), "app/src/main/java/ie/pantry/domain/ingredient")
        return dir.listFiles { f -> f.extension == "kt" }?.toList().orEmpty()
    }

    // ---- T16: sentinel and throwing-seam behaviour cases ----

    private val sentinelLines = listOf(
        "a ${Sentinels.INGREDIENT}",
        "-2 ${Sentinels.INGREDIENT}",
        "2-3 ${Sentinels.INGREDIENT}",
        "1,5 kg ${Sentinels.INGREDIENT}",
        "2 pinches ${Sentinels.INGREDIENT}",
        "3 splodges of ${Sentinels.INGREDIENT}",
        "2 400g tins ${Sentinels.INGREDIENT}",
        "0 g ${Sentinels.INGREDIENT}",
        "1e999 g ${Sentinels.INGREDIENT}",
        "1/0 g ${Sentinels.INGREDIENT}",
        "${Sentinels.INGREDIENT} \u0000\u0001",
        "½½ tsp ${Sentinels.INGREDIENT}",
    )

    private fun assertNoSentinelIgnoreCase(text: String) {
        for (sentinel in Sentinels.all) {
            assertFalse(text.contains(sentinel, ignoreCase = true), "sentinel '$sentinel' leaked into: $text")
        }
    }

    @Test
    fun `sentinel lines reach every absence value without leaking`() {
        // A Derived key is a success value and legitimately carries parsed input content (that
        // is R2's whole point); R10 constrains the field-less failure/absence values only, so
        // this checks Unquantified (always produced by these lines) and Absent where it occurs.
        val engine = IngredientEngine(AliasTable(emptyList()))
        for (line in sentinelLines) {
            val result = engine.parse(line)
            assertEquals(Quantity.Unquantified, result.quantity, "line: $line")
            assertNoSentinelIgnoreCase(result.quantity.toString())
            if (result.key == CanonicalKey.Absent) {
                assertNoSentinelIgnoreCase(result.key.toString())
            }
        }
    }

    @Test
    fun `operation failures built from sentinel lines carry no sentinel`() {
        val engine = IngredientEngine(AliasTable(emptyList()))
        val parsedLines = sentinelLines.map { engine.parse(it) }

        for (line in parsedLines) {
            // Every line here is Unquantified, so convertTo always gives the field-less
            // NotConvertible, and scaledBy(NaN) always gives the field-less InvalidRatio
            // regardless of quantity — both safe to check unconditionally.
            val conversion = line.quantity.convertTo(MeasureUnit.G)
            assertNoSentinelIgnoreCase(conversion.toString())

            val scaling = line.quantity.scaledBy(Double.NaN)
            assertNoSentinelIgnoreCase(scaling.toString())
        }

        // A Merged result is a success value and may legitimately carry a Derived key's parsed
        // content; only the field-less NotMergeable failure value is in R10's scope here.
        for (a in parsedLines) {
            for (b in parsedLines) {
                val merge = a.mergeWith(b)
                if (merge is MergeResult.NotMergeable) {
                    assertNoSentinelIgnoreCase(merge.toString())
                }
            }
        }

        val inconsistentColumns = QuantityColumns(1.0, Sentinels.INGREDIENT, ie.pantry.data.db.entity.QuantityDimension.UNQUANTIFIED)
        assertNoSentinelIgnoreCase(inconsistentColumns.decode().toString())
    }

    @Test
    fun `a throwing alias lookup yields an unreadable line and nothing escapes`() {
        val throwingEngine = IngredientEngine { throw RuntimeException(Sentinels.INGREDIENT) }

        val result = throwingEngine.parse("onion")

        assertEquals(ParsedLine(CanonicalKey.Absent, Quantity.Unquantified), result)
    }
}
