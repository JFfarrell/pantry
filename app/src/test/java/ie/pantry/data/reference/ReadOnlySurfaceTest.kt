package ie.pantry.data.reference

import ie.pantry.data.db.entity.NutritionBasis
import ie.pantry.testutil.RepoPaths
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * R7: the reference package exposes no way to mutate loaded data. Checked by reflection, by attempted
 * downcast writes on real returned collections, and by inspecting the package's own source.
 */
@RunWith(RobolectricTestRunner::class)
class ReadOnlySurfaceTest {

    private val publicTypes = listOf(
        Lookup::class.java, Lookup.Found::class.java, Lookup.Absent::class.java,
        LoadResult::class.java, LoadResult.Ready::class.java, LoadFailed::class.java,
        LoadFailure::class.java, LoadFailure.Category::class.java, ReferenceDataset::class.java,
        StaplesEntry::class.java, AliasEntry::class.java, SeasonalityEntry::class.java,
        SectionOrderEntry::class.java, SectionMapping::class.java,
        StaplesTable::class.java, AliasTable::class.java, SeasonalityTable::class.java, SectionOrderTable::class.java,
        ReferenceDataStore::class.java,
    )

    private val mutatorPrefixes = listOf("set", "add", "remove", "put", "clear", "replace")

    @Test
    fun `repo root is the nearest directory holding settings gradle kts`() {
        val root = RepoPaths.repoRoot()

        assertTrue(java.io.File(root, "settings.gradle.kts").isFile)
    }

    @Test
    fun `every declared field of reference types is final`() {
        for (type in publicTypes) {
            for (field in type.declaredFields) {
                if (field.isSynthetic) continue
                assertTrue(Modifier.isFinal(field.modifiers), "${type.name}.${field.name} must be final")
            }
        }
    }

    @Test
    fun `no reference type declares a mutator method`() {
        for (type in publicTypes) {
            for (method in type.declaredMethods) {
                if (method.isSynthetic || method.isBridge) continue
                if (!Modifier.isPublic(method.modifiers)) continue
                val name = method.name
                assertFalse(
                    mutatorPrefixes.any { name.startsWith(it) && name.length > it.length && name[it.length].isUpperCase() },
                    "${type.name}.$name looks like a mutator",
                )
            }
        }
    }

    @Test
    fun `downcast writes to every returned collection throw and leave lookups unchanged`() {
        val staples = StaplesTable(listOf(StaplesEntry("water", NutritionBasis.PER_100G, 0.0, 0.0, 0.0, 0.0)))
        val season = SeasonalityTable(listOf(SeasonalityEntry("kale", setOf(1), listOf("cabbage"))))
        val sections = SectionOrderTable(
            sections = listOf(SectionOrderEntry("Fresh Food", 0)),
            mappings = listOf(SectionMapping("water", "Fresh Food")),
        )

        assertMutationRejected(staples.entries) { entries -> entries.add(entries.first()) }
        assertMutationRejected(season.entries) { entries -> entries.add(entries.first()) }
        assertMutationRejected(season.entries.first().inSeasonMonths) { months -> months.add(99) }
        assertMutationRejected(season.entries.first().substitutions) { subs -> subs.add("x") }
        assertMutationRejected(sections.sections) { list -> list.add(list.first()) }
        assertMutationRejected(sections.mappings) { list -> list.add(list.first()) }

        assertEquals(Lookup.Found(staples.entries.single()), staples.lookup("water"))
    }

    private fun <T> assertMutationRejected(collection: List<T>, mutate: (MutableList<T>) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val mutable = collection as MutableList<T>
        assertFailsWith<UnsupportedOperationException> { mutate(mutable) }
    }

    private fun <T> assertMutationRejected(collection: Set<T>, mutate: (MutableSet<T>) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val mutable = collection as MutableSet<T>
        assertFailsWith<UnsupportedOperationException> { mutate(mutable) }
    }

    @Test
    fun `table built from caller-owned mutable lists is unaffected by later mutation`() {
        val ownedMonths = mutableSetOf(1, 2)
        val ownedSubs = mutableListOf("cabbage")
        val entry = SeasonalityEntry("kale", ownedMonths, ownedSubs)
        val table = SeasonalityTable(listOf(entry))

        ownedMonths.add(99)
        ownedSubs.add("mutated")

        val stored = (table.lookup("kale") as Lookup.Found).value
        assertEquals(setOf(1, 2), stored.inSeasonMonths)
        assertEquals(listOf("cabbage"), stored.substitutions)
    }

    @Test
    fun `reference package source writes nothing`() {
        val forbidden = listOf(
            "FileOutputStream", "openFileOutput", "SharedPreferences", ".edit(",
            "writeText", "writeBytes", "@Insert", "@Update", "Dao",
        )
        for (file in referenceSourceFiles()) {
            val text = file.readText()
            for (needle in forbidden) {
                assertFalse(text.contains(needle), "${file.name} must not contain '$needle'")
            }
        }
    }

    @Test
    fun `pure reference files import neither android nor org json nor other db types`() {
        val pureFiles = setOf(
            "Lookup.kt", "LoadFailure.kt", "ReferenceDataset.kt", "ReferenceEntries.kt",
            "ReferenceTables.kt", "AssetSource.kt",
        )
        for (file in referenceSourceFiles().filter { it.name in pureFiles }) {
            for (line in file.readLines()) {
                if (!line.trimStart().startsWith("import ")) continue
                assertFalse(line.contains("import android."), "${file.name}: $line")
                assertFalse(line.contains("import org.json"), "${file.name}: $line")
                if (line.contains("import ie.pantry.data.db")) {
                    assertTrue(
                        line.contains("import ie.pantry.data.db.entity.NutritionBasis"),
                        "${file.name}: only NutritionBasis may be imported from data.db, found: $line",
                    )
                }
            }
        }
    }

    private fun referenceSourceFiles(): List<java.io.File> {
        val dir = java.io.File(RepoPaths.repoRoot(), "app/src/main/java/ie/pantry/data/reference")
        return dir.listFiles { f -> f.extension == "kt" }?.toList().orEmpty()
    }
}
