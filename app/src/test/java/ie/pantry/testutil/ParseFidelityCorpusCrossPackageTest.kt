package ie.pantry.testutil

import ie.pantry.domain.ingredient.IngredientEngine
import ie.pantry.domain.ingredient.toColumns
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R8 AC3: proves the corpus loads and drives from outside F3's own test package, with no
 * dependency on any F3 test class — this is the reference usage pattern F6's and F11's CFC-1
 * route tests copy. */
@RunWith(RobolectricTestRunner::class)
class ParseFidelityCorpusCrossPackageTest {

    @Test
    fun `corpus loads from outside the engine package with every expected value`() {
        val entries = ParseFidelityCorpus.load()
        assertEquals(true, entries.isNotEmpty())
        for (entry in entries) {
            assertEquals(true, entry.line.isNotEmpty())
        }
    }

    @Test
    fun `each corpus entry driven through the engine matches its expected values`() {
        // Expected-versus-actual uses a 1e-9 amount tolerance (AD14); route-versus-route
        // comparisons elsewhere use exact equality.
        val engine = IngredientEngine(ShippedReferenceTables.aliasTable())
        for (entry in ParseFidelityCorpus.load()) {
            val result = engine.parse(entry.line)
            assertEquals(entry.expectedKey, result.key, "row ${entry.row} \"${entry.line}\"")
            val columns = result.quantity.toColumns()
            val expected = entry.expectedColumns
            assertEquals(expected.dimension, columns.dimension, "row ${entry.row} dimension")
            assertEquals(expected.unit, columns.unit, "row ${entry.row} unit")
            if (expected.amount == null || columns.amount == null) {
                assertEquals(expected.amount, columns.amount, "row ${entry.row} amount")
            } else {
                assertTrue(
                    Math.abs(expected.amount - columns.amount) < 1e-9,
                    "row ${entry.row} amount expected ${expected.amount}, got ${columns.amount}",
                )
            }
        }
    }
}
