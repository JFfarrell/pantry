package ie.pantry.data.db

import ie.pantry.data.db.dao.NutritionCacheDao
import ie.pantry.data.db.entity.NutritionBasis
import ie.pantry.data.db.entity.NutritionCacheEntry
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The nutrition cache is read one-shot by key; absent nutrient values must stay absent. */
@RunWith(RobolectricTestRunner::class)
class NutritionCacheDaoTest {

    private lateinit var db: PantryDatabase
    private lateinit var cache: NutritionCacheDao

    @Before
    fun setUp() {
        db = TestDatabases.inMemory(MutableClock())
        cache = db.nutritionCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(
        key: String = "flour",
        basis: NutritionBasis = NutritionBasis.PER_100G,
        energy: Double? = 364.0,
        protein: Double? = 10.0,
        fat: Double? = 1.0,
        carbs: Double? = 76.0,
    ) = NutritionCacheEntry(
        canonicalKey = key,
        basisUnit = basis,
        energyKcal = energy,
        proteinG = protein,
        fatG = fat,
        carbohydrateG = carbs,
        sourceAttribution = "Example dataset",
        licenceTag = "ODbL-1.0",
        fetchedAt = Instant.parse("2026-02-01T00:00:00Z"),
        estimatedConversion = false,
    )

    @Test
    fun `find returns null for unknown key`() = runTest {
        assertNull(cache.find("nothing"))
    }

    @Test
    fun `upsert then find returns entry`() = runTest {
        val stored = entry()

        cache.upsert(stored)

        assertEquals(stored, cache.find("flour"))
    }

    @Test
    fun `upsert replaces existing entry for same key`() = runTest {
        cache.upsert(entry(energy = 364.0))

        cache.upsert(entry(energy = 350.0))

        assertEquals(350.0, assertNotNull(cache.find("flour")).energyKcal)
    }

    @Test
    fun `null nutrient fields round-trip as null`() = runTest {
        cache.upsert(entry(energy = null, protein = null, fat = null, carbs = null))

        val found = assertNotNull(cache.find("flour"))

        // Absent nutrient values must stay absent, never become 0.0 (R3 AC3).
        assertNull(found.energyKcal)
        assertNull(found.proteinG)
        assertNull(found.fatG)
        assertNull(found.carbohydrateG)
    }

    @Test
    fun `basisUnit round-trips for per 100 g and per 100 mL`() = runTest {
        cache.upsert(entry(key = "flour", basis = NutritionBasis.PER_100G))
        cache.upsert(entry(key = "milk", basis = NutritionBasis.PER_100ML))

        assertEquals(NutritionBasis.PER_100G, assertNotNull(cache.find("flour")).basisUnit)
        assertEquals(NutritionBasis.PER_100ML, assertNotNull(cache.find("milk")).basisUnit)
    }
}
