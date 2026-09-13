package com.pantry.app

import com.pantry.app.shopping.Aisles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AislesTest {

    @Test
    fun `olive oil is a cupboard item, not a jar of olives`() {
        assertEquals(Aisles.CUPBOARD, Aisles.classify("olive oil"))
        assertEquals(Aisles.CUPBOARD, Aisles.classify("extra virgin olive oil"))
    }

    @Test
    fun `actual olives still go in the jars aisle`() {
        assertEquals(Aisles.TINS, Aisles.classify("pitted black olives"))
    }

    @Test
    fun `the form of an item beats what it is made of`() {
        assertEquals(Aisles.FROZEN, Aisles.classify("frozen peas"))
        assertEquals(Aisles.TINS, Aisles.classify("tinned tomatoes"))
        assertEquals(Aisles.PRODUCE, Aisles.classify("fresh peas"))
    }

    @Test
    fun `ordinary items land where you would expect`() {
        assertEquals(Aisles.PRODUCE, Aisles.classify("garlic"))
        assertEquals(Aisles.MEAT_FISH, Aisles.classify("free-range chicken breasts"))
        assertEquals(Aisles.DAIRY, Aisles.classify("greek yoghurt"))
        assertEquals(Aisles.OTHER, Aisles.classify("kitchen roll"))
    }

    @Test
    fun `walking order is the layout of a shop, not the alphabet`() {
        // The bug this guards: ordering on the aisle name put Bakery before
        // Fruit & veg, so the Tesco queue disagreed with the visible list.
        assertTrue(Aisles.walkingOrder(Aisles.PRODUCE) < Aisles.walkingOrder(Aisles.BAKERY))
        assertTrue(Aisles.walkingOrder(Aisles.MEAT_FISH) < Aisles.walkingOrder(Aisles.FROZEN))
        assertEquals(Aisles.order.size, Aisles.walkingOrder("Somewhere unknown"))
    }
}
