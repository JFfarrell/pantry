package ie.pantry.domain.ingredient

import ie.pantry.data.db.entity.QuantityDimension
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

/** R4: quantity-segment parsing, unit tokens (DM4/DM5) and column mapping (R4 AC6). */
class QuantityParserTest {

    @Test
    fun `every unit token is unique across measure units count units and the bare count token`() {
        val tokens = MeasureUnit.entries.map { it.token } +
            CountUnit.entries.map { it.token } +
            BARE_COUNT_TOKEN

        assertEquals(24, tokens.size)
        assertEquals(tokens.size, tokens.toSet().size, "duplicate unit token found: $tokens")
    }

    @Test
    fun `measure unit factors and dimensions match the Q4 table`() {
        val expected = mapOf(
            MeasureUnit.G to (QuantityDimension.MASS to 1.0),
            MeasureUnit.KG to (QuantityDimension.MASS to 1000.0),
            MeasureUnit.MG to (QuantityDimension.MASS to 0.001),
            MeasureUnit.OZ to (QuantityDimension.MASS to 28.349523125),
            MeasureUnit.LB to (QuantityDimension.MASS to 453.59237),
            MeasureUnit.ML to (QuantityDimension.VOLUME to 1.0),
            MeasureUnit.L to (QuantityDimension.VOLUME to 1000.0),
            MeasureUnit.CL to (QuantityDimension.VOLUME to 10.0),
            MeasureUnit.TSP to (QuantityDimension.VOLUME to 5.0),
            MeasureUnit.TBSP to (QuantityDimension.VOLUME to 15.0),
            MeasureUnit.CUP to (QuantityDimension.VOLUME to 250.0),
            MeasureUnit.FL_OZ to (QuantityDimension.VOLUME to 28.4130625),
            MeasureUnit.PINT to (QuantityDimension.VOLUME to 568.26125),
        )

        assertEquals(MeasureUnit.entries.toSet(), expected.keys)
        for ((unit, expectedDimensionAndFactor) in expected) {
            val (dimension, factor) = expectedDimensionAndFactor
            assertEquals(dimension, unit.dimension, "unit $unit has wrong dimension")
            assertEquals(factor, unit.factorToBase, 1e-12, "unit $unit has wrong factorToBase")
            assertTrue(
                dimension == QuantityDimension.MASS || dimension == QuantityDimension.VOLUME,
                "unit $unit must be MASS or VOLUME, never COUNT or UNQUANTIFIED",
            )
        }
    }

    @Test
    fun `every unit spelling belongs to exactly one unit`() {
        val spellingOwners = mutableMapOf<String, MutableSet<String>>()
        for (unit in MeasureUnit.entries) {
            for (spelling in unit.spellings) {
                spellingOwners.getOrPut(spelling) { mutableSetOf() }.add("MeasureUnit.$unit")
            }
        }
        for (unit in CountUnit.entries) {
            for (spelling in unit.spellings) {
                spellingOwners.getOrPut(spelling) { mutableSetOf() }.add("CountUnit.$unit")
            }
        }

        val shared = spellingOwners.filterValues { it.size > 1 }
        assertTrue(shared.isEmpty(), "spelling(s) shared between units: $shared")
    }

    @Test
    fun `measured and counted reject a zero negative or non finite amount`() {
        assertFailsWith<IllegalArgumentException> { Quantity.Measured(0.0, MeasureUnit.G) }
        assertFailsWith<IllegalArgumentException> { Quantity.Measured(-1.0, MeasureUnit.G) }
        assertFailsWith<IllegalArgumentException> { Quantity.Measured(Double.NaN, MeasureUnit.G) }
        assertFailsWith<IllegalArgumentException> { Quantity.Measured(Double.POSITIVE_INFINITY, MeasureUnit.G) }
        assertFailsWith<IllegalArgumentException> { Quantity.Counted(0.0, null) }
        assertFailsWith<IllegalArgumentException> { Quantity.Counted(-1.0, null) }
        assertFailsWith<IllegalArgumentException> { Quantity.Counted(Double.NaN, null) }
    }

    @Test
    fun `quantities report their dimension`() {
        assertEquals(QuantityDimension.MASS, Quantity.Measured(1.0, MeasureUnit.G).dimension)
        assertEquals(QuantityDimension.VOLUME, Quantity.Measured(1.0, MeasureUnit.ML).dimension)
        assertEquals(QuantityDimension.COUNT, Quantity.Counted(1.0, null).dimension)
        assertEquals(QuantityDimension.COUNT, Quantity.Counted(1.0, CountUnit.CLOVE).dimension)
        assertEquals(QuantityDimension.UNQUANTIFIED, Quantity.Unquantified.dimension)
    }

    @Test
    fun `unquantified is not equal to any measured or counted quantity`() {
        assertTrue(Quantity.Unquantified != Quantity.Measured(1.0, MeasureUnit.G))
        assertTrue(Quantity.Unquantified != Quantity.Counted(1.0, null))
    }

    @Test
    fun `unquantified has no numeric or unit field`() {
        val fieldTypes = Quantity.Unquantified::class.java.declaredFields.map { it.type }
        assertTrue(
            fieldTypes.none { it == Double::class.java || it == java.lang.Double.TYPE },
            "Unquantified must carry no Double field, found: $fieldTypes",
        )
    }

    @Test
    fun `every measure unit round trips through the column triple`() {
        for (unit in MeasureUnit.entries) {
            val quantity = Quantity.Measured(3.5, unit)
            val columns = quantity.toColumns()
            assertEquals(unit.token, columns.unit)
            assertEquals(unit.dimension, columns.dimension)
            assertEquals(ColumnDecode.Decoded(quantity), columns.decode())
        }
    }

    @Test
    fun `every count unit and the bare count round trip through the column triple`() {
        for (unit in CountUnit.entries) {
            val quantity = Quantity.Counted(2.0, unit)
            val columns = quantity.toColumns()
            assertEquals(unit.token, columns.unit)
            assertEquals(QuantityDimension.COUNT, columns.dimension)
            assertEquals(ColumnDecode.Decoded(quantity), columns.decode())
        }

        val bare = Quantity.Counted(2.0, null)
        val bareColumns = bare.toColumns()
        assertEquals(BARE_COUNT_TOKEN, bareColumns.unit)
        assertEquals(ColumnDecode.Decoded(bare), bareColumns.decode())
    }

    @Test
    fun `unquantified maps to null null UNQUANTIFIED`() {
        val columns = Quantity.Unquantified.toColumns()
        assertEquals(QuantityColumns(null, null, QuantityDimension.UNQUANTIFIED), columns)
        assertEquals(ColumnDecode.Decoded(Quantity.Unquantified), columns.decode())
    }

    @Test
    fun `triples the engine could not produce decode to inconsistent`() {
        val inconsistentTriples = listOf(
            QuantityColumns(1.0, "not-a-real-token", QuantityDimension.MASS),
            QuantityColumns(1.0, MeasureUnit.ML.token, QuantityDimension.MASS),
            QuantityColumns(null, MeasureUnit.G.token, QuantityDimension.MASS),
            QuantityColumns(0.0, MeasureUnit.G.token, QuantityDimension.MASS),
            QuantityColumns(1.0, null, QuantityDimension.UNQUANTIFIED),
            QuantityColumns(null, "g", QuantityDimension.UNQUANTIFIED),
        )
        for (triple in inconsistentTriples) {
            assertEquals(ColumnDecode.Inconsistent, triple.decode(), "expected Inconsistent for $triple")
        }
    }

    // ---- T8: quantity-segment grammar core (G1, G8, G9, G13) ----

    private val engine = IngredientEngine(ie.pantry.data.reference.AliasTable(emptyList()))

    private fun quantityOf(line: String): Quantity = engine.parse(line).quantity

    @Test
    fun `metric and imperial mass lines parse to MASS`() {
        assertEquals(Quantity.Measured(500.0, MeasureUnit.G), quantityOf("500g plain flour"))
        assertEquals(Quantity.Measured(1.0, MeasureUnit.KG), quantityOf("1 kg potatoes"))
        assertEquals(Quantity.Measured(8.0, MeasureUnit.OZ), quantityOf("8 oz cheddar"))
        assertEquals(Quantity.Measured(1.0, MeasureUnit.LB), quantityOf("1 lb beef mince"))
    }

    @Test
    fun `volume lines parse to VOLUME with fractions read exactly`() {
        assertEquals(Quantity.Measured(150.0, MeasureUnit.ML), quantityOf("150ml milk"))
        assertEquals(Quantity.Measured(1.0, MeasureUnit.L), quantityOf("1 l chicken stock"))
        assertEquals(Quantity.Measured(2.0, MeasureUnit.TBSP), quantityOf("2 tbsp olive oil"))
        assertEquals(Quantity.Measured(1.5, MeasureUnit.TSP), quantityOf("1½ tsp ground cumin"))
        assertEquals(Quantity.Measured(1.0, MeasureUnit.CUP), quantityOf("1 cup rice"))
        assertEquals(Quantity.Measured(0.5, MeasureUnit.TSP), quantityOf("½ tsp salt"))
        assertEquals(Quantity.Measured(0.5, MeasureUnit.TSP), quantityOf("1/2 tsp salt"))
        assertEquals(Quantity.Measured(1.5, MeasureUnit.CUP), quantityOf("1 1/2 cups red lentils"))
    }

    @Test
    fun `counted lines parse to COUNT with their count unit`() {
        assertEquals(Quantity.Counted(3.0, null), quantityOf("3 eggs"))
        assertEquals(Quantity.Counted(3.0, CountUnit.CLOVE), quantityOf("3 cloves garlic, crushed"))
        assertEquals(Quantity.Counted(1.0, CountUnit.TIN), quantityOf("1 tin chickpeas"))
    }

    @Test
    fun `every measure unit spelling parses including trailing dot and fl oz`() {
        assertEquals(Quantity.Measured(3.0, MeasureUnit.TBSP), quantityOf("3 Tbsp. olive oil"))
        assertEquals(Quantity.Measured(2.0, MeasureUnit.FL_OZ), quantityOf("2 fl oz milk"))
        assertEquals(Quantity.Measured(1.0, MeasureUnit.FL_OZ), quantityOf("1 fluid ounce milk"))
    }

    @Test
    fun `every count unit parses in singular and plural`() {
        assertEquals(Quantity.Counted(1.0, CountUnit.BUNCH), quantityOf("1 bunch asparagus"))
        assertEquals(Quantity.Counted(2.0, CountUnit.BUNCH), quantityOf("2 bunches asparagus"))
        assertEquals(Quantity.Counted(1.0, CountUnit.CAN), quantityOf("1 can beans"))
    }

    @Test
    fun `a stated size followed by a container word is measured`() {
        assertEquals(Quantity.Measured(400.0, MeasureUnit.G), quantityOf("400g tin chopped tomatoes"))
        assertEquals(Quantity.Measured(400.0, MeasureUnit.G), quantityOf("400g can chickpeas, drained"))
    }

    @Test
    fun `zero zero denominator and malformed amounts are unquantified`() {
        assertEquals(Quantity.Unquantified, quantityOf("0 g sugar"))
        assertEquals(Quantity.Unquantified, quantityOf("1/0 g"))
        assertEquals(Quantity.Unquantified, quantityOf("1e999 g"))
        assertEquals(Quantity.Unquantified, quantityOf("½½ tsp"))
        assertEquals(Quantity.Unquantified, quantityOf("99999999999999999999 g"))
    }

    @Test
    fun `six digit amounts are read and seven digit amounts are not`() {
        assertEquals(Quantity.Measured(123456.0, MeasureUnit.G), quantityOf("123456 g"))
        assertEquals(Quantity.Unquantified, quantityOf("1234567 g"))
    }

    // ---- T9: remaining grammar rows (G2-G7, G10-G12) ----

    @Test
    fun `a multiplier multiplies two confident numbers`() {
        assertEquals(Quantity.Measured(800.0, MeasureUnit.G), quantityOf("2 x 400g tins chopped tomatoes"))
    }

    @Test
    fun `a parenthetical size takes precedence over its container count`() {
        assertEquals(Quantity.Measured(400.0, MeasureUnit.G), quantityOf("1 (400 g) tin chickpeas"))
        assertEquals(Quantity.Measured(400.0, MeasureUnit.G), quantityOf("1 tin (400g) chickpeas"))
    }

    @Test
    fun `the R4 unquantified lines all give the unquantified value`() {
        val lines = listOf(
            "salt, to taste",
            "a pinch of salt",
            "a handful of basil",
            "some tomatoes",
            "2-3 carrots",
            "1,5 kg flour",
            "0 g sugar",
            "a knob of butter",
            "3 splodges of ketchup",
        )
        for (line in lines) {
            assertEquals(Quantity.Unquantified, quantityOf(line), "expected unquantified for: $line")
        }
    }

    @Test
    fun `lead word amounts are never read as one`() {
        assertEquals(Quantity.Unquantified, quantityOf("a tin of chickpeas"))
        assertEquals(Quantity.Unquantified, quantityOf("an onion"))
    }

    @Test
    fun `negative ranged and decimal comma amounts are unquantified`() {
        assertEquals(Quantity.Unquantified, quantityOf("-2 eggs"))
        assertEquals(Quantity.Unquantified, quantityOf("2 to 3 carrots"))
        assertEquals(Quantity.Unquantified, quantityOf("1,5 kg flour"))
    }

    @Test
    fun `vague and unknown units are unquantified`() {
        assertEquals(Quantity.Unquantified, quantityOf("2 pinches salt"))
        assertEquals(Quantity.Unquantified, quantityOf("3 splodges of ketchup"))
    }

    @Test
    fun `two numbers that are not a mixed number are unquantified`() {
        assertEquals(Quantity.Unquantified, quantityOf("2 400g tins tomatoes"))
    }
}
