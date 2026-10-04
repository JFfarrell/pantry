package ie.pantry.domain.ingredient

import ie.pantry.data.db.entity.QuantityDimension
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** R5, R6, R7: conversion, scaling and merge. */
class ConversionScalingMergeTest {

    @Test
    fun `operation failure values are field free data objects`() {
        assertTrue(Conversion.NotConvertible.instanceFields().isEmpty())
        assertTrue(Scaling.InvalidRatio.instanceFields().isEmpty())
        assertTrue(MergeResult.NotMergeable.instanceFields().isEmpty())

        assertEquals("NotConvertible", Conversion.NotConvertible.toString())
        assertEquals("InvalidRatio", Scaling.InvalidRatio.toString())
        assertEquals("NotMergeable", MergeResult.NotMergeable.toString())
    }

    private fun Any.instanceFields() =
        this::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }

    // ---- R5: conversion ----

    @Test
    fun `within dimension conversion to the base unit uses the Q4 factors`() {
        val cases = listOf(
            Quantity.Measured(1.0, MeasureUnit.KG) to 1000.0,
            Quantity.Measured(1.0, MeasureUnit.LB) to 453.59237,
            Quantity.Measured(1.0, MeasureUnit.OZ) to 28.349523125,
            Quantity.Measured(1.0, MeasureUnit.L) to 1000.0,
            Quantity.Measured(1.0, MeasureUnit.TBSP) to 15.0,
            Quantity.Measured(1.0, MeasureUnit.TSP) to 5.0,
            Quantity.Measured(1.0, MeasureUnit.CUP) to 250.0,
        )
        for ((quantity, expectedAmount) in cases) {
            val target = if (quantity.dimension == QuantityDimension.MASS) MeasureUnit.G else MeasureUnit.ML
            val result = quantity.convertTo(target)
            assertTrue(result is Conversion.Converted, "expected Converted for $quantity -> $target")
            val converted = (result as Conversion.Converted).quantity as Quantity.Measured
            assertEquals(expectedAmount, converted.amount, 1e-9)
            assertEquals(target, converted.unit)
        }
    }

    @Test
    fun `conversion across dimensions or count units is not convertible`() {
        assertEquals(Conversion.NotConvertible, Quantity.Measured(1.0, MeasureUnit.G).convertTo(MeasureUnit.ML))
        assertEquals(Conversion.NotConvertible, Quantity.Counted(1.0, null).convertTo(MeasureUnit.G))
        assertEquals(Conversion.NotConvertible, Quantity.Counted(3.0, CountUnit.CLOVE).convertTo(CountUnit.BULB))
    }

    @Test
    fun `a count converts only to its own count unit`() {
        val quantity = Quantity.Counted(3.0, CountUnit.CLOVE)
        assertEquals(Conversion.Converted(quantity), quantity.convertTo(CountUnit.CLOVE))
    }

    @Test
    fun `unquantified and overflowing conversions are not convertible`() {
        assertEquals(Conversion.NotConvertible, Quantity.Unquantified.convertTo(MeasureUnit.G))
        val huge = Quantity.Measured(Double.MAX_VALUE, MeasureUnit.KG)
        assertEquals(Conversion.NotConvertible, huge.convertTo(MeasureUnit.MG))
    }

    // ---- R6: scaling ----

    @Test
    fun `scaling multiplies amounts and keeps the unit`() {
        assertEquals(
            Scaling.Scaled(Quantity.Measured(600.0, MeasureUnit.G)),
            Quantity.Measured(400.0, MeasureUnit.G).scaledBy(1.5),
        )
        assertEquals(
            Scaling.Scaled(Quantity.Measured(3.0, MeasureUnit.TBSP)),
            Quantity.Measured(2.0, MeasureUnit.TBSP).scaledBy(1.5),
        )
        assertEquals(
            Scaling.Scaled(Quantity.Counted(3.0, null)),
            Quantity.Counted(2.0, null).scaledBy(1.5),
        )
    }

    @Test
    fun `scaling leaves unquantified unchanged`() {
        assertEquals(Scaling.Scaled(Quantity.Unquantified), Quantity.Unquantified.scaledBy(2.0))
    }

    @Test
    fun `zero negative NaN infinite and overflowing ratios are invalid`() {
        val quantity = Quantity.Measured(400.0, MeasureUnit.G)
        assertEquals(Scaling.InvalidRatio, quantity.scaledBy(0.0))
        assertEquals(Scaling.InvalidRatio, quantity.scaledBy(-1.0))
        assertEquals(Scaling.InvalidRatio, quantity.scaledBy(Double.NaN))
        assertEquals(Scaling.InvalidRatio, quantity.scaledBy(Double.POSITIVE_INFINITY))
        assertEquals(Scaling.InvalidRatio, Quantity.Measured(1e308, MeasureUnit.G).scaledBy(1e10))
    }

    // ---- R7: merge ----

    @Test
    fun `one kilogram and five hundred grams of one key merge to fifteen hundred grams`() {
        val a = ParsedLine(CanonicalKey.Derived("potato"), Quantity.Measured(1.0, MeasureUnit.KG))
        val b = ParsedLine(CanonicalKey.Derived("potato"), Quantity.Measured(500.0, MeasureUnit.G))

        val result = a.mergeWith(b)
        assertTrue(result is MergeResult.Merged)
        val merged = (result as MergeResult.Merged).line.quantity as Quantity.Measured
        assertEquals(1500.0, merged.amount, 1e-9)
        assertEquals(MeasureUnit.G, merged.unit)
        assertTrue(a.canMergeWith(b))
    }

    @Test
    fun `same unit merges keep the unit`() {
        val a = ParsedLine(CanonicalKey.Derived("oil"), Quantity.Measured(2.0, MeasureUnit.TBSP))
        val b = ParsedLine(CanonicalKey.Derived("oil"), Quantity.Measured(1.0, MeasureUnit.TBSP))

        val result = a.mergeWith(b)
        assertTrue(result is MergeResult.Merged)
        val merged = (result as MergeResult.Merged).line.quantity as Quantity.Measured
        assertEquals(3.0, merged.amount, 1e-9)
        assertEquals(MeasureUnit.TBSP, merged.unit)
    }

    @Test
    fun `different key dimension or count unit never merges`() {
        val tomato400g = ParsedLine(CanonicalKey.Derived("tomato"), Quantity.Measured(400.0, MeasureUnit.G))
        val puree2tbsp = ParsedLine(CanonicalKey.Derived("tomato puree"), Quantity.Measured(2.0, MeasureUnit.TBSP))
        val puree100g = ParsedLine(CanonicalKey.Derived("tomato puree"), Quantity.Measured(100.0, MeasureUnit.G))
        val onion200g = ParsedLine(CanonicalKey.Derived("onion"), Quantity.Measured(200.0, MeasureUnit.G))
        val onion2each = ParsedLine(CanonicalKey.Derived("onion"), Quantity.Counted(2.0, null))
        val garlic3cloves = ParsedLine(CanonicalKey.Derived("garlic"), Quantity.Counted(3.0, CountUnit.CLOVE))
        val garlic1bulb = ParsedLine(CanonicalKey.Derived("garlic"), Quantity.Counted(1.0, CountUnit.BULB))

        assertEquals(MergeResult.NotMergeable, tomato400g.mergeWith(puree2tbsp))
        assertFalse(tomato400g.canMergeWith(puree2tbsp))
        assertEquals(MergeResult.NotMergeable, tomato400g.mergeWith(puree100g))
        assertEquals(MergeResult.NotMergeable, onion200g.mergeWith(onion2each))
        assertEquals(MergeResult.NotMergeable, garlic3cloves.mergeWith(garlic1bulb))
    }

    @Test
    fun `two unquantified lines of one key merge to unquantified`() {
        val a = ParsedLine(CanonicalKey.Derived("salt"), Quantity.Unquantified)
        val b = ParsedLine(CanonicalKey.Derived("salt"), Quantity.Unquantified)

        val result = a.mergeWith(b)
        assertEquals(MergeResult.Merged(ParsedLine(CanonicalKey.Derived("salt"), Quantity.Unquantified)), result)
        assertTrue(a.canMergeWith(b))
    }

    @Test
    fun `measured and unquantified lines never merge`() {
        val measured = ParsedLine(CanonicalKey.Derived("salt"), Quantity.Measured(200.0, MeasureUnit.G))
        val unquantified = ParsedLine(CanonicalKey.Derived("salt"), Quantity.Unquantified)

        assertEquals(MergeResult.NotMergeable, measured.mergeWith(unquantified))
        assertFalse(measured.canMergeWith(unquantified))
    }

    @Test
    fun `absent keys never merge`() {
        val absent1 = ParsedLine(CanonicalKey.Absent, Quantity.Unquantified)
        val absent2 = ParsedLine(CanonicalKey.Absent, Quantity.Unquantified)
        val derived = ParsedLine(CanonicalKey.Derived("salt"), Quantity.Unquantified)

        assertEquals(MergeResult.NotMergeable, absent1.mergeWith(absent2))
        assertEquals(MergeResult.NotMergeable, absent1.mergeWith(derived))
        assertEquals(MergeResult.NotMergeable, derived.mergeWith(absent1))
    }

    @Test
    fun `merge is symmetric for every mergeable pair`() {
        val pairs = listOf(
            ParsedLine(CanonicalKey.Derived("potato"), Quantity.Measured(1.0, MeasureUnit.KG)) to
                ParsedLine(CanonicalKey.Derived("potato"), Quantity.Measured(500.0, MeasureUnit.G)),
            ParsedLine(CanonicalKey.Derived("oil"), Quantity.Measured(2.0, MeasureUnit.TBSP)) to
                ParsedLine(CanonicalKey.Derived("oil"), Quantity.Measured(1.0, MeasureUnit.TBSP)),
            ParsedLine(CanonicalKey.Derived("salt"), Quantity.Unquantified) to
                ParsedLine(CanonicalKey.Derived("salt"), Quantity.Unquantified),
            ParsedLine(CanonicalKey.Derived("garlic"), Quantity.Counted(3.0, CountUnit.CLOVE)) to
                ParsedLine(CanonicalKey.Derived("garlic"), Quantity.Counted(2.0, CountUnit.CLOVE)),
            ParsedLine(CanonicalKey.Derived("onion"), Quantity.Counted(2.0, null)) to
                ParsedLine(CanonicalKey.Derived("onion"), Quantity.Counted(1.0, null)),
            ParsedLine(CanonicalKey.Derived("stock"), Quantity.Measured(1.0, MeasureUnit.L)) to
                ParsedLine(CanonicalKey.Derived("stock"), Quantity.Measured(200.0, MeasureUnit.ML)),
        )
        for ((a, b) in pairs) {
            assertEquals(a.mergeWith(b), b.mergeWith(a))
        }
    }

    @Test
    fun `the predicate agrees with merge on every pair`() {
        val pairs = listOf(
            ParsedLine(CanonicalKey.Derived("potato"), Quantity.Measured(1.0, MeasureUnit.KG)) to
                ParsedLine(CanonicalKey.Derived("potato"), Quantity.Measured(500.0, MeasureUnit.G)),
            ParsedLine(CanonicalKey.Derived("tomato"), Quantity.Measured(400.0, MeasureUnit.G)) to
                ParsedLine(CanonicalKey.Derived("tomato puree"), Quantity.Measured(2.0, MeasureUnit.TBSP)),
            ParsedLine(CanonicalKey.Absent, Quantity.Unquantified) to
                ParsedLine(CanonicalKey.Absent, Quantity.Unquantified),
        )
        for ((a, b) in pairs) {
            assertEquals(a.mergeWith(b) is MergeResult.Merged, a.canMergeWith(b))
        }
    }

    // ---- T11: R7's exact lines, parsed through the full engine ----

    private val mergeEngine = IngredientEngine(ie.pantry.data.reference.AliasTable(emptyList()))

    private fun parsedLine(line: String) = mergeEngine.parse(line)

    @Test
    fun `parsed R7 lines merge exactly as the spec states`() {
        val potatoKg = parsedLine("1 kg potatoes")
        val potatoG = parsedLine("500 g potatoes")
        val result = potatoKg.mergeWith(potatoG)
        assertTrue(result is MergeResult.Merged)
        val merged = (result as MergeResult.Merged).line.quantity as Quantity.Measured
        assertEquals(MeasureUnit.G, merged.unit)
        assertEquals(1500.0, merged.amount, 1e-9)
        assertTrue(potatoKg.canMergeWith(potatoG))

        val tomato400g = parsedLine("400 g tomatoes")
        val puree2tbsp = parsedLine("2 tbsp tomato purée")
        val puree100g = parsedLine("100 g tomato purée")
        val onion200g = parsedLine("200 g onions")
        val onion2 = parsedLine("2 onions")
        assertFalse(tomato400g.canMergeWith(puree2tbsp))
        assertFalse(tomato400g.canMergeWith(puree100g))
        assertFalse(onion200g.canMergeWith(onion2))

        val garlic3cloves = parsedLine("3 cloves garlic")
        val garlic1bulb = parsedLine("1 bulb garlic")
        assertFalse(garlic3cloves.canMergeWith(garlic1bulb))

        val saltToTaste = parsedLine("salt, to taste")
        val pinchOfSalt = parsedLine("a pinch of salt")
        val saltMergeResult = saltToTaste.mergeWith(pinchOfSalt)
        assertEquals(MergeResult.Merged(ParsedLine(CanonicalKey.Derived("salt"), Quantity.Unquantified)), saltMergeResult)
        assertTrue(saltToTaste.canMergeWith(pinchOfSalt))

        val salt200g = parsedLine("200 g salt")
        assertFalse(salt200g.canMergeWith(saltToTaste))
    }
}
