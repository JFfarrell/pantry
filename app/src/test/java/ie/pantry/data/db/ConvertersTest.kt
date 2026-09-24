package ie.pantry.data.db

import ie.pantry.data.db.entity.NutritionBasis
import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.db.entity.SelectionSnapshot
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** R3 AC3 storage side: each converter round-trips, and an absent value stays absent. */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `instant round-trips through epoch millis`() {
        val instant = Instant.ofEpochMilli(1_758_700_000_123L)

        val column = converters.instantToEpochMillis(instant)

        assertEquals(1_758_700_000_123L, column)
        assertEquals(instant, converters.epochMillisToInstant(column))
    }

    @Test
    fun `null instant round-trips as null`() {
        assertNull(converters.instantToEpochMillis(null))
        assertNull(converters.epochMillisToInstant(null))
    }

    @Test
    fun `quantity dimension round-trips by name`() {
        for (dimension in QuantityDimension.entries) {
            val column = converters.quantityDimensionToString(dimension)

            assertEquals(dimension.name, column)
            assertEquals(dimension, converters.stringToQuantityDimension(column))
        }
    }

    @Test
    fun `nutrition basis round-trips by name for both bases`() {
        assertEquals(setOf("PER_100G", "PER_100ML"), NutritionBasis.entries.map { it.name }.toSet())
        for (basis in NutritionBasis.entries) {
            val column = converters.nutritionBasisToString(basis)

            assertEquals(basis.name, column)
            assertEquals(basis, converters.stringToNutritionBasis(column))
        }
    }

    @Test
    fun `empty long list encodes as empty string and decodes to empty list`() {
        assertEquals("", converters.longListToString(emptyList()))
        assertEquals(emptyList(), converters.stringToLongList(""))
    }

    @Test
    fun `long list round-trips in order`() {
        val ids = listOf(7L, 3L, 9_000_000_000L)

        val column = converters.longListToString(ids)

        assertEquals("7,3,9000000000", column)
        assertEquals(ids, converters.stringToLongList(column))
    }

    @Test
    fun `selection snapshots round-trip including null servings`() {
        val snapshots = listOf(
            SelectionSnapshot(recipeId = 1L, servings = 4),
            SelectionSnapshot(recipeId = 2L, servings = null),
            SelectionSnapshot(recipeId = 3L, servings = 0),
        )

        val column = converters.snapshotsToString(snapshots)

        assertEquals("1:4|2:|3:0", column)
        // A null servings must stay null, never become 0 or a default (R3 AC3).
        assertEquals(snapshots, converters.stringToSnapshots(column))
    }

    @Test
    fun `empty snapshot list round-trips`() {
        assertEquals("", converters.snapshotsToString(emptyList()))
        assertEquals(emptyList(), converters.stringToSnapshots(""))
    }
}
