package ie.pantry.domain.ingredient

import ie.pantry.data.reference.AliasEntry
import ie.pantry.data.reference.AliasTable
import ie.pantry.data.reference.Lookup
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

/** R2: canonical-key rule, alias-table interaction and construction guards. */
class CanonicalKeyRuleTest {

    @Test
    fun `derived key rejects a blank value`() {
        assertFailsWith<IllegalArgumentException> { CanonicalKey.Derived("") }
        assertFailsWith<IllegalArgumentException> { CanonicalKey.Derived("   ") }
    }

    @Test
    fun `absent key has no string field and equals no derived key`() {
        val fieldTypes = CanonicalKey.Absent::class.java.declaredFields.map { it.type }
        assertTrue(
            fieldTypes.none { it == String::class.java },
            "Absent must carry no String field, found: $fieldTypes",
        )

        assertNotEquals<CanonicalKey>(CanonicalKey.Absent, CanonicalKey.Derived("onion"))
        assertNotEquals<CanonicalKey>(CanonicalKey.Absent, CanonicalKey.Derived("tomato puree"))
    }

    // ---- T10: canonical-key rule K1-K8 (quantity-free lines; the spec's exact quantity-bearing
    // lines are asserted end to end through parse in T11) ----

    private val emptyTable = AliasTable(emptyList())

    private fun keyOf(line: String, table: AliasTable = emptyTable): CanonicalKey =
        IngredientEngine(table).parse(line).key

    @Test
    fun `case whitespace and diacritics are folded`() {
        assertEquals(CanonicalKey.Derived("red onion"), keyOf("  Red  ONION "))
        assertEquals(CanonicalKey.Derived("tomato puree"), keyOf("Tomato purée"))
    }

    @Test
    fun `preparation and size words are removed`() {
        assertEquals(CanonicalKey.Derived("onion"), keyOf("large onions, finely chopped"))
        assertEquals(CanonicalKey.Derived("tomato"), keyOf("ripe tomatoes"))
        assertEquals(CanonicalKey.Derived("cherry"), keyOf("cherries"))
    }

    @Test
    fun `the last word is singularised by the Q2 suffix rules`() {
        assertEquals(CanonicalKey.Derived("couscous"), keyOf("couscous"))
        assertEquals(CanonicalKey.Derived("asparagus"), keyOf("asparagus"))
        assertEquals(CanonicalKey.Derived("tomato"), keyOf("tomatoes"))
    }

    @Test
    fun `hyphens become spaces`() {
        assertEquals(CanonicalKey.Derived("self raising flour"), keyOf("self-raising flour"))
    }

    @Test
    fun `comma tails and parenthesised text are dropped`() {
        assertEquals(CanonicalKey.Derived("garlic"), keyOf("garlic, crushed"))
        assertEquals(CanonicalKey.Derived("coriander"), keyOf("coriander (leaves and stalks)"))
    }

    @Test
    fun `the alias table overrides only the irregular plural and the brand name`() {
        var hits = 0
        val table = AliasTable(
            listOf(
                AliasEntry("bay leaves", "bay leaf"),
                AliasEntry("philadelphia", "cream cheese"),
            ),
        )
        val countingEngine = IngredientEngine { variant ->
            table.lookup(variant).also { if (it is Lookup.Found) hits++ }
        }

        assertEquals(CanonicalKey.Derived("bay leaf"), countingEngine.parse("bay leaves").key)
        assertEquals(CanonicalKey.Derived("cream cheese"), countingEngine.parse("Philadelphia").key)
        assertEquals(CanonicalKey.Derived("carrot"), countingEngine.parse("carrots").key)
        assertEquals(2, hits)
    }

    @Test
    fun `a stage one hit wins over the rule and the protected phrase`() {
        val table = AliasTable(listOf(AliasEntry("chopped tomato", "tinned tomato")))
        assertEquals(CanonicalKey.Derived("tinned tomato"), keyOf("chopped tomato", table))
    }

    @Test
    fun `chopped tomatoes is a protected phrase`() {
        assertEquals(CanonicalKey.Derived("chopped tomato"), keyOf("chopped tomato"))
        assertEquals(CanonicalKey.Derived("chopped tomato"), keyOf("chopped tomatoes"))
    }

    @Test
    fun `no remaining name text gives the absent key`() {
        assertEquals(CanonicalKey.Absent, keyOf(""))
        assertEquals(CanonicalKey.Absent, keyOf(", chopped"))
        assertEquals(CanonicalKey.Absent, keyOf("finely chopped"))
        assertEquals(CanonicalKey.Absent, keyOf("to taste"))
    }

    @Test
    fun `a product is never folded into its base ingredient`() {
        assertEquals(CanonicalKey.Derived("tomato"), keyOf("tomatoes"))
        assertEquals(CanonicalKey.Derived("tomato puree"), keyOf("tomato puree"))
    }

    // ---- T11: the spec's exact R2 lines, end to end through parse ----

    @Test
    fun `R2 rule lines give their spec keys with an empty table`() {
        val cases = mapOf(
            "  Red  ONION " to "red onion",
            "2 large onions, finely chopped" to "onion",
            "500g carrots" to "carrot",
            "4 ripe tomatoes" to "tomato",
            "a handful of cherries" to "cherry",
            "Tomato purée" to "tomato puree",
            "self-raising flour" to "self raising flour",
            "200g couscous" to "couscous",
            "1 bunch asparagus" to "asparagus",
        )
        for ((line, expectedKey) in cases) {
            assertEquals(CanonicalKey.Derived(expectedKey), keyOf(line), "line: $line")
        }
    }

    @Test
    fun `R2 alias lines hit the table only for the irregular plural and the brand name`() {
        var hits = 0
        val table = AliasTable(
            listOf(
                AliasEntry("bay leaves", "bay leaf"),
                AliasEntry("philadelphia", "cream cheese"),
            ),
        )
        val countingEngine = IngredientEngine { variant ->
            table.lookup(variant).also { if (it is Lookup.Found) hits++ }
        }

        assertEquals(CanonicalKey.Derived("bay leaf"), countingEngine.parse("2 bay leaves").key)
        assertEquals(CanonicalKey.Derived("cream cheese"), countingEngine.parse("200g Philadelphia").key)
        assertEquals(CanonicalKey.Derived("carrot"), countingEngine.parse("3 carrots").key)
        assertEquals(2, hits)
    }

    @Test
    fun `quantity only lines give the absent key`() {
        assertEquals(CanonicalKey.Absent, keyOf("2 tbsp"))
        assertEquals(CanonicalKey.Absent, keyOf("500 g"))
        assertEquals(CanonicalKey.Absent, keyOf(""))
        assertEquals(CanonicalKey.Absent, keyOf(", chopped"))
    }

    @Test
    fun `tomatoes and tomato puree lines keep different keys`() {
        assertEquals(CanonicalKey.Derived("tomato"), keyOf("400 g tomatoes"))
        assertEquals(CanonicalKey.Derived("tomato puree"), keyOf("2 tbsp tomato purée"))
    }

    @Test
    fun `no quantity segment word leaks into the key`() {
        val cases = mapOf(
            "0 g sugar" to "sugar",
            "3 splodges of ketchup" to "ketchup",
            "a tin of chickpeas" to "chickpea",
            "2 x 400g tins chopped tomatoes" to "chopped tomato",
            "2-3 carrots" to "carrot",
            "1,5 kg flour" to "flour",
            "2 400g tins tomatoes" to "tomato",
        )
        for ((line, expectedKey) in cases) {
            assertEquals(CanonicalKey.Derived(expectedKey), keyOf(line), "line: $line")
        }
    }
}
