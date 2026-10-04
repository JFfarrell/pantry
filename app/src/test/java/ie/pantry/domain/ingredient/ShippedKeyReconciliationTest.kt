package ie.pantry.domain.ingredient

import ie.pantry.data.reference.Lookup
import ie.pantry.testutil.ShippedReferenceTables
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R2 AC4, R3: every shipped key is a fixed point of the engine, and every shipped alias
 * variant is reachable. Robolectric, since it loads the real shipped tables. */
@RunWith(RobolectricTestRunner::class)
class ShippedKeyReconciliationTest {

    @Test
    fun `shipped alias variants give their curated keys`() {
        val engine = IngredientEngine(ShippedReferenceTables.aliasTable())
        val cases = mapOf(
            "1 eggplant" to "aubergine",
            "2 zucchini" to "courgette",
            "a handful of cilantro" to "coriander",
            "200g shrimp" to "prawn",
            "3 scallions" to "spring onion",
            "300g brussel sprouts" to "brussels sprout",
        )
        for ((line, expected) in cases) {
            assertEquals(CanonicalKey.Derived(expected), engine.parse(line).key, "line: $line")
        }
    }

    @Test
    fun `every shipped staples and seasonality key and substitution is a fixed point`() {
        val engine = IngredientEngine(ShippedReferenceTables.aliasTable())
        val staples = ShippedReferenceTables.staplesTable()
        val seasonality = ShippedReferenceTables.seasonalityTable()

        val keys = mutableSetOf<String>()
        keys += staples.entries.map { it.key }
        keys += seasonality.entries.map { it.key }
        keys += seasonality.entries.flatMap { it.substitutions }

        val offenders = keys.filter { engine.parse(it).key != CanonicalKey.Derived(it) }
        assertTrue(offenders.isEmpty(), "keys that are not fixed points of the engine: $offenders")
    }

    @Test
    fun `every section mapping key and alias target is a fixed point`() {
        val engine = IngredientEngine(ShippedReferenceTables.aliasTable())
        val sections = ShippedReferenceTables.sectionOrderTable()
        val aliases = ShippedReferenceTables.aliasTable()

        val keys = mutableSetOf<String>()
        keys += sections.mappings.map { it.key }
        keys += aliases.entries.map { it.canonicalKey }

        val offenders = keys.filter { engine.parse(it).key != CanonicalKey.Derived(it) }
        assertTrue(offenders.isEmpty(), "keys that are not fixed points of the engine: $offenders")
    }

    @Test
    fun `every shipped alias variant is reachable at a lookup stage`() {
        val aliases = ShippedReferenceTables.aliasTable()
        val queried = mutableListOf<Pair<String, Lookup<String>>>()
        val recordingEngine = IngredientEngine { variant ->
            aliases.lookup(variant).also { queried.add(variant to it) }
        }

        for (entry in aliases.entries) {
            queried.clear()
            recordingEngine.parse(entry.variant)
            val reached = queried.any { (queriedVariant, result) -> queriedVariant == entry.variant && result is Lookup.Found }
            assertTrue(reached, "alias variant never reached the table: ${entry.variant}")
        }
    }
}
