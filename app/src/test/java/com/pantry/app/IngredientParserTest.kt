package com.pantry.app

import com.pantry.app.domain.IngredientParser
import com.pantry.app.domain.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientParserTest {

    @Test
    fun `reads a simple metric quantity`() {
        val parsed = IngredientParser.parse("250 g plain flour")
        assertEquals(250.0, parsed.quantity!!.amount, 0.001)
        assertEquals(MeasureUnit.GRAM, parsed.quantity!!.unit)
        assertEquals("plain flour", parsed.name)
    }

    @Test
    fun `reads a mixed number fraction`() {
        val parsed = IngredientParser.parse("1 1/2 tbsp olive oil")
        assertEquals(1.5, parsed.quantity!!.amount, 0.001)
        assertEquals(MeasureUnit.TABLESPOON, parsed.quantity!!.unit)
    }

    @Test
    fun `reads a unicode fraction`() {
        val parsed = IngredientParser.parse("½ tsp salt")
        assertEquals(0.5, parsed.quantity!!.amount, 0.001)
        assertEquals(MeasureUnit.TEASPOON, parsed.quantity!!.unit)
    }

    @Test
    fun `a range rounds up to the larger end`() {
        val parsed = IngredientParser.parse("2-3 garlic cloves, crushed")
        assertEquals(3.0, parsed.quantity!!.amount, 0.001)
        assertEquals(MeasureUnit.CLOVE, parsed.quantity!!.unit)
        assertEquals("crushed", parsed.note)
    }

    @Test
    fun `a bare number is a count`() {
        val parsed = IngredientParser.parse("3 eggs")
        assertEquals(3.0, parsed.quantity!!.amount, 0.001)
        assertEquals(MeasureUnit.PIECE, parsed.quantity!!.unit)
    }

    @Test
    fun `parenthetical detail becomes a note`() {
        val parsed = IngredientParser.parse("1 tin (400g) chopped tomatoes")
        assertEquals(MeasureUnit.CAN, parsed.quantity!!.unit)
        assertEquals("400g", parsed.note)
        assertEquals("chopped tomatoes", parsed.name)
    }

    @Test
    fun `an unquantified line still parses`() {
        val parsed = IngredientParser.parse("salt and pepper, to taste")
        assertNull(parsed.quantity)
        assertTrue(parsed.optional)
    }

    @Test
    fun `unit words are not matched inside other words`() {
        val parsed = IngredientParser.parse("100 g granola")
        assertEquals("granola", parsed.name)
    }

    @Test
    fun `normalising collapses plurals and descriptors`() {
        assertEquals(
            IngredientParser.normaliseName("ripe vine tomatoes"),
            IngredientParser.normaliseName("Tomato")
        )
        assertEquals("chicken breast", IngredientParser.normaliseName("free-range chicken breasts, sliced"))
    }
}
