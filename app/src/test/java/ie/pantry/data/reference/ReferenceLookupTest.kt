package ie.pantry.data.reference

import ie.pantry.testutil.FixtureAssetSource
import ie.pantry.testutil.Sentinels
import kotlinx.coroutines.test.runTest
import ie.pantry.data.db.entity.NutritionBasis
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R2: a lookup is found or explicitly absent, and R7: tables copy and freeze their input. */
@RunWith(RobolectricTestRunner::class)
class ReferenceLookupTest {

    private fun staple(key: String, value: Double = 0.0) = StaplesEntry(
        key = key,
        basis = NutritionBasis.PER_100G,
        energyKcal = value,
        proteinG = value,
        fatG = value,
        carbohydrateG = value,
    )

    private val failure = LoadFailure(
        dataset = ReferenceDataset.STAPLES,
        category = LoadFailure.Category.INVALID_JSON,
        array = null,
        entryIndex = null,
        field = null,
    )

    // ---- types (T3) ----

    @Test
    fun `absent is never equal to a found value of empty string or zero-valued entry`() {
        assertNotEquals<Any>(Lookup.Absent, Lookup.Found(""))
        assertNotEquals<Any>(Lookup.Absent, Lookup.Found(staple("water")))
        assertNotEquals<Any>(Lookup.Found(""), Lookup.Found(staple("water")))
    }

    @Test
    fun `load failed is both a load result and a lookup result`() {
        val failed = LoadFailed(failure)

        assertTrue(failed is LoadResult<*>)
        assertTrue(failed is LookupResult<*>)
        assertEquals(failure, failed.failure)
    }

    @Test
    fun `load failure categories are exactly the ten design categories`() {
        val expected = setOf(
            "ASSET_ABSENT", "ASSET_UNREADABLE", "INVALID_JSON", "MISSING_FIELD", "UNEXPECTED_FIELD",
            "WRONG_TYPE", "INVALID_ENUM_VALUE", "INVALID_VALUE", "DUPLICATE_KEY", "INTERNAL_ERROR",
        )

        assertEquals(expected, LoadFailure.Category.entries.map { it.name }.toSet())
        assertEquals(10, LoadFailure.Category.entries.size)
    }

    @Test
    fun `reference dataset asset paths are the four reference json paths`() {
        assertEquals(
            mapOf(
                "STAPLES" to "reference/staples.json",
                "ALIASES" to "reference/aliases.json",
                "SEASONALITY" to "reference/seasonality.json",
                "SECTION_ORDER" to "reference/section_order.json",
            ),
            ReferenceDataset.entries.associate { it.name to it.assetPath },
        )
    }

    // ---- tables (T4) ----

    @Test
    fun `staples miss is absent and distinct from zero-valued water`() {
        val table = StaplesTable(listOf(staple("water")))

        val hit = table.lookup("water")
        val miss = table.lookup("bread")

        assertEquals(Lookup.Found(staple("water")), hit)
        assertEquals(Lookup.Absent, miss)
        assertNotEquals<Any>(hit, miss)
    }

    @Test
    fun `no nutrient value is readable from an absent staples result`() {
        val result: Lookup<StaplesEntry> = StaplesTable(listOf(staple("water"))).lookup("nothing")

        // An exhaustive when: only Found carries a value, so Absent has no nutrient to read.
        val readable: Double? = when (result) {
            is Lookup.Found -> result.value.energyKcal
            Lookup.Absent -> null
        }

        assertNull(readable)
    }

    @Test
    fun `alias hit returns the mapped canonical key and a miss is absent`() {
        val table = AliasTable(listOf(AliasEntry(variant = "courgettes", canonicalKey = "courgette")))

        assertEquals(Lookup.Found("courgette"), table.lookup("courgettes"))
        assertEquals(Lookup.Absent, table.lookup("aubergines"))
    }

    @Test
    fun `alias absent is distinct from an empty key and from the echoed variant`() {
        val table = AliasTable(listOf(AliasEntry(variant = "courgettes", canonicalKey = "courgette")))

        val miss = table.lookup("aubergines")

        assertNotEquals<Any>(miss, Lookup.Found(""))
        assertNotEquals<Any>(miss, Lookup.Found("aubergines"))
    }

    @Test
    fun `seasonality entry with no substitutions is found and distinct from absent`() {
        val entry = SeasonalityEntry(key = "strawberry", inSeasonMonths = setOf(6, 7, 8), substitutions = emptyList())
        val table = SeasonalityTable(listOf(entry))

        val hit = table.lookup("strawberry")
        val miss = table.lookup("mango")

        assertEquals(Lookup.Found(entry), hit)
        assertEquals(emptyList(), (hit as Lookup.Found).value.substitutions)
        assertEquals(Lookup.Absent, miss)
        assertNotEquals<Any>(hit, miss)
    }

    private fun sectionTable() = SectionOrderTable(
        sections = listOf(SectionOrderEntry("Fresh Food", 0), SectionOrderEntry("Bakery", 10)),
        mappings = listOf(SectionMapping(key = "water", section = "Fresh Food")),
    )

    @Test
    fun `section lookup returns the first section with its index and a miss is absent`() {
        val table = sectionTable()

        assertEquals(Lookup.Found(SectionOrderEntry("Fresh Food", 0)), table.sectionFor("water"))
        assertEquals(Lookup.Absent, table.sectionFor("unmapped"))
    }

    @Test
    fun `section absent is distinct from the first section and its index`() {
        val miss = sectionTable().sectionFor("unmapped")

        assertNotEquals<Any>(miss, Lookup.Found(SectionOrderEntry("Fresh Food", 0)))
        assertNotEquals<Any>(miss, Lookup.Found(SectionOrderEntry("", 0)))
    }

    @Test
    fun `lookup of an empty string is absent and does not throw`() {
        assertEquals(Lookup.Absent, StaplesTable(listOf(staple("water"))).lookup(""))
        assertEquals(Lookup.Absent, AliasTable(emptyList()).lookup(""))
        assertEquals(Lookup.Absent, SeasonalityTable(emptyList()).lookup(""))
        assertEquals(Lookup.Absent, sectionTable().sectionFor(""))
    }

    @Test
    fun `staples table constructor rejects a repeated key with a constant message`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            StaplesTable(listOf(staple("repeated-key-text"), staple("repeated-key-text")))
        }

        val message = assertNotNull(failure.message)
        assertTrue(!message.contains("repeated-key-text"), "the message must not echo the key: $message")
    }

    @Test
    fun `seasonality table constructor rejects a month outside 1 to 12`() {
        val entry = SeasonalityEntry(key = "bad-month-key", inSeasonMonths = setOf(13), substitutions = emptyList())

        val failure = assertFailsWith<IllegalArgumentException> { SeasonalityTable(listOf(entry)) }

        assertTrue(!assertNotNull(failure.message).contains("bad-month-key"))
    }

    @Test
    fun `section order table constructor rejects a repeated walk index`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            SectionOrderTable(
                sections = listOf(SectionOrderEntry("First-name-text", 5), SectionOrderEntry("Second-name-text", 5)),
                mappings = emptyList(),
            )
        }

        val message = assertNotNull(failure.message)
        assertTrue(!message.contains("First-name-text") && !message.contains("Second-name-text"))
    }

    @Test
    fun `section order table constructor rejects a mapping to an unlisted section`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            SectionOrderTable(
                sections = listOf(SectionOrderEntry("Fresh Food", 0)),
                mappings = listOf(SectionMapping(key = "mapped-key-text", section = "No Such Section")),
            )
        }

        val message = assertNotNull(failure.message)
        assertTrue(!message.contains("mapped-key-text") && !message.contains("No Such Section"))
    }

    // ---- T13: store lookups ----

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/reference/fixtures/$name")) { "missing fixture $name" }.readBytes()

    @Test
    fun `store lookup on a broken asset is LoadFailed never Absent`() = runTest {
        val store = ReferenceDataStore(FixtureAssetSource(emptyMap()))

        val result = store.stapleFor("water")

        assertIs<LoadFailed>(result)
    }

    @Test
    fun `store shortcuts return found and absent for each dataset`() = runTest {
        val store = ReferenceDataStore(
            FixtureAssetSource(
                mapOf(
                    ReferenceDataset.STAPLES.assetPath to fixture("staples_valid.json"),
                    ReferenceDataset.ALIASES.assetPath to fixture("aliases_valid.json"),
                    ReferenceDataset.SEASONALITY.assetPath to fixture("seasonality_valid.json"),
                    ReferenceDataset.SECTION_ORDER.assetPath to fixture("section_order_valid.json"),
                ),
            ),
        )

        assertIs<Lookup.Found<StaplesEntry>>(store.stapleFor("water"))
        assertEquals(Lookup.Absent, store.stapleFor("nothing"))
        assertIs<Lookup.Found<String>>(store.canonicalKeyForVariant("courgettes"))
        assertEquals(Lookup.Absent, store.canonicalKeyForVariant("nothing"))
        assertIs<Lookup.Found<SeasonalityEntry>>(store.seasonalityFor("strawberry"))
        assertEquals(Lookup.Absent, store.seasonalityFor("nothing"))
        assertIs<Lookup.Found<SectionOrderEntry>>(store.sectionFor("water"))
        assertEquals(Lookup.Absent, store.sectionFor("nothing"))
    }

    @Test
    fun `store accessor returns the same Ready table instance on every call`() = runTest {
        val store = ReferenceDataStore(FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to fixture("staples_valid.json"))))

        val first = assertIs<LoadResult.Ready<StaplesTable>>(store.staples())
        val second = assertIs<LoadResult.Ready<StaplesTable>>(store.staples())

        assertTrue(first.table === second.table)
    }
}
