package ie.pantry.domain.ingredient

import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.testutil.CorpusOrigin
import ie.pantry.testutil.LineKind
import ie.pantry.testutil.ParseFidelityCorpus
import ie.pantry.testutil.ShippedReferenceTables
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R8: the shared parse-fidelity corpus, loaded and checked at the loader level (T5). The
 * alias-hit coverage floor (R8 AC2's fourth clause) needs the shipped alias table and is
 * completed in T15, alongside the CFC-1 enforcement tests. Robolectric-annotated because T15
 * extends this class with checks that load the shipped alias table. */
@RunWith(RobolectricTestRunner::class)
class ParseFidelityCorpusTest {

    private val entries = ParseFidelityCorpus.load()

    @Test
    fun `corpus holds at least fifty hand transcribed entries from at least five hosts`() {
        val handTranscribed = entries.filter { it.origin == CorpusOrigin.HAND_TRANSCRIBED }
        assertTrue(handTranscribed.size >= 50, "expected >= 50 HAND_TRANSCRIBED entries, found ${handTranscribed.size}")

        val hosts = handTranscribed.map { URI(it.sourceUrl).host }.toSet()
        assertTrue(hosts.size >= 5, "expected >= 5 distinct hosts, found $hosts")

        for (entry in handTranscribed) {
            assertTrue(entry.line.isNotBlank())
            assertTrue(entry.sourceUrl.isNotBlank())
        }
    }

    @Test
    fun `absence markers load as explicit absence values`() {
        val absentKeyEntries = entries.filter { it.expectedKey == CanonicalKey.Absent }
        assertTrue(absentKeyEntries.isNotEmpty(), "expected at least one <absent> key entry")

        val unquantifiedEntries = entries.filter { it.expectedColumns.dimension == QuantityDimension.UNQUANTIFIED }
        assertTrue(unquantifiedEntries.isNotEmpty(), "expected at least one unquantified entry")
        for (entry in unquantifiedEntries) {
            assertEquals(null, entry.expectedColumns.amount)
            assertEquals(null, entry.expectedColumns.unit)
        }
    }

    @Test
    fun `corpus meets the expected outcome coverage floor`() {
        val dimensions = entries.map { it.expectedColumns.dimension }.toSet()
        for (dimension in QuantityDimension.entries) {
            assertTrue(dimension in dimensions, "expected at least one $dimension entry")
        }

        val nonIngredientOrAbsent = entries.any { it.lineKind == LineKind.NON_INGREDIENT || it.expectedKey == CanonicalKey.Absent }
        assertTrue(nonIngredientOrAbsent, "expected at least one NON_INGREDIENT or key-absent entry")

        val fractional = entries.any { amt -> amt.expectedColumns.amount?.let { it != Math.floor(it) } == true }
        assertTrue(fractional, "expected at least one non-integer amount")
    }

    @Test
    fun `extension fixture adds exactly one F5 harvested entry filterable by origin`() {
        val extended = ParseFidelityCorpus.load("ingredient/parse_fidelity_corpus_with_f5_entry.tsv")
        val f5Entries = extended.filter { it.origin == CorpusOrigin.F5_HARVESTED }
        assertEquals(1, f5Entries.size)
    }

    // ---- T15: CFC-1 corpus enforcement (R8 AC1, AC2's alias-hit floor) ----

    @Test
    fun `every corpus line parses to its expected key and columns against the shipped alias table`() {
        val engine = IngredientEngine(ShippedReferenceTables.aliasTable())
        val mismatches = mutableListOf<String>()
        for (entry in entries) {
            val result = engine.parse(entry.line)
            if (result.key != entry.expectedKey) {
                mismatches.add("row ${entry.row} \"${entry.line}\": key expected ${entry.expectedKey}, got ${result.key}")
                continue
            }
            val columns = result.quantity.toColumns()
            val expected = entry.expectedColumns
            val dimensionOk = columns.dimension == expected.dimension
            val unitOk = columns.unit == expected.unit
            val amountOk = when {
                expected.amount == null || columns.amount == null -> expected.amount == columns.amount
                else -> Math.abs(expected.amount - columns.amount) < 1e-9
            }
            if (!dimensionOk || !unitOk || !amountOk) {
                mismatches.add("row ${entry.row} \"${entry.line}\": columns expected $expected, got $columns")
            }
        }
        assertTrue(mismatches.isEmpty(), "corpus mismatches (fix the parser or the corpus, never the other way from engine output):\n${mismatches.joinToString("\n")}")
    }

    @Test
    fun `at least one corpus entry resolves through the alias table`() {
        val table = ShippedReferenceTables.aliasTable()
        var hits = 0
        val recordingEngine = IngredientEngine { variant -> table.lookup(variant).also { if (it is ie.pantry.data.reference.Lookup.Found) hits++ } }
        for (entry in entries) {
            recordingEngine.parse(entry.line)
        }
        assertTrue(hits >= 1, "expected at least one corpus entry to resolve through the shipped alias table")
    }
}
