package ie.pantry.domain.ingredient

import ie.pantry.testutil.ParseFidelityCorpus
import ie.pantry.testutil.ShippedReferenceTables
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R1 AC2: two separately constructed engine instances over the shipped alias table give equal
 * results for every corpus line (CFC-1's byte-identical comparison). */
@RunWith(RobolectricTestRunner::class)
class EntryPointDeterminismTest {

    @Test
    fun `two engines over separately loaded shipped tables give identical results for every corpus line`() {
        val engineA = IngredientEngine(ShippedReferenceTables.aliasTable())
        val engineB = IngredientEngine(ShippedReferenceTables.aliasTable())

        for (entry in ParseFidelityCorpus.load()) {
            val resultA = engineA.parse(entry.line)
            val resultB = engineB.parse(entry.line)
            assertEquals(resultA, resultB, "row ${entry.row} \"${entry.line}\" differed between instances")
            assertEquals(resultA.quantity.toColumns(), resultB.quantity.toColumns(), "row ${entry.row} columns differed between instances")
        }
    }
}
