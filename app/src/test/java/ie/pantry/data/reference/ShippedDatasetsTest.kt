package ie.pantry.data.reference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ie.pantry.data.db.entity.NutritionBasis
import ie.pantry.testutil.RepoPaths
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// ---------------------------------------------------------------------------------------------
// Curation-policy helpers. Each returns the offending keys or lines (empty when the rule holds), so a
// shipped-content test can assert `emptyList()` and a negative-control test can assert what it reports.
// ---------------------------------------------------------------------------------------------

/** The canonical-key surface form (R3 AC3, Q3): non-empty, lowercase, trimmed, no two consecutive spaces. */
internal fun surfaceFormViolations(keys: Collection<String>): List<String> = keys.filter { key ->
    key.isEmpty() || key != key.lowercase(Locale.ROOT) || key != key.trim() || key.contains("  ")
}

/** Variants mapped to themselves. */
internal fun aliasSelfMaps(entries: List<AliasEntry>): List<String> =
    entries.filter { it.variant == it.canonicalKey }.map { it.variant }

/** Variants whose target is itself another entry's variant (an alias chain). */
internal fun aliasChains(entries: List<AliasEntry>): List<String> {
    val variants = entries.map { it.variant }.toSet()
    return entries.filter { it.canonicalKey in variants }.map { it.variant }
}

/** Seasonality entries that name their own key as a substitute. */
internal fun selfSubstitutions(entries: List<SeasonalityEntry>): List<String> =
    entries.filter { it.key in it.substitutions }.map { it.key }

/** Alias targets that are not in [known] (AD11). */
internal fun unresolvedAliasTargets(entries: List<AliasEntry>, known: Set<String>): List<String> =
    entries.map { it.canonicalKey }.filter { it !in known }

/** Substitutions that are not in [known] (AD11). */
internal fun unresolvedSubstitutions(entries: List<SeasonalityEntry>, known: Set<String>): List<String> =
    entries.flatMap { it.substitutions }.filter { it !in known }

/** Staples keys with no section mapping (R4 AC4). */
internal fun unmappedStaplesKeys(staples: List<StaplesEntry>, sections: SectionOrderTable): List<String> =
    staples.map { it.key }.filter { sections.sectionFor(it) == Lookup.Absent }

/** Keys whose energy exceeds 1000 kcal or any macro exceeds 100 g per 100 g (AD10 policy, not a load rule). */
internal fun nutrientBoundViolations(staples: List<StaplesEntry>): List<String> = staples.filter {
    it.energyKcal > 1000.0 || it.proteinG > 100.0 || it.fatG > 100.0 || it.carbohydrateG > 100.0
}.map { it.key }

/** Lines present in exactly one of [assetSections] and the [expectedText] snapshot (blank lines skipped). */
internal fun sectionSetDifference(assetSections: List<String>, expectedText: String): List<String> {
    val expected = expectedText.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val actual = assetSections.toSet()
    return ((expected - actual) + (actual - expected)).sorted()
}

/** True when at least one entry names at least one substitution (Q10). */
internal fun hasSubstitution(entries: List<SeasonalityEntry>): Boolean = entries.any { it.substitutions.isNotEmpty() }

/** Deepest nesting of JSON objects and arrays, the top-level object at depth 1; brackets inside strings are not counted. */
internal fun jsonDepth(text: String): Int {
    var depth = 0
    var deepest = 0
    var inString = false
    var escaped = false
    for (ch in text) {
        when {
            inString && escaped -> escaped = false
            inString && ch == '\\' -> escaped = true
            ch == '"' -> inString = !inString
            !inString && (ch == '{' || ch == '[') -> {
                depth++
                if (depth > deepest) deepest = depth
            }
            !inString && (ch == '}' || ch == ']') -> depth--
        }
    }
    return deepest
}

internal const val MAX_ASSET_BYTES = 1_048_576
internal const val MAX_ASSET_DEPTH = 4

internal fun exceedsSizeCap(bytes: ByteArray): Boolean = bytes.size > MAX_ASSET_BYTES

/** Policy helper tests: each rule is shown able to fail on one violating fixture and to pass a control. */
@RunWith(RobolectricTestRunner::class)
class ShippedDatasetsTest {

    private fun staple(key: String, energy: Double = 10.0, protein: Double = 1.0, fat: Double = 1.0, carbs: Double = 1.0) =
        StaplesEntry(key, NutritionBasis.PER_100G, energy, protein, fat, carbs)

    private fun season(key: String, subs: List<String> = emptyList()) = SeasonalityEntry(key, setOf(6), subs)

    @Test
    fun `surface form check rejects uppercase padded and double-spaced keys`() {
        val bad = listOf("Onion", " onion", "onion ", "red  onion", "")

        assertEquals(bad, surfaceFormViolations(bad))
        assertEquals(emptyList(), surfaceFormViolations(listOf("onion", "red onion")))
    }

    @Test
    fun `alias self-map check rejects a variant mapped to itself`() {
        assertEquals(listOf("tomato"), aliasSelfMaps(listOf(AliasEntry("tomato", "tomato"), AliasEntry("tomatoes", "tomato"))))
        assertEquals(emptyList(), aliasSelfMaps(listOf(AliasEntry("tomatoes", "tomato"))))
    }

    @Test
    fun `alias chain check rejects a target that is also a variant`() {
        val chained = listOf(AliasEntry("scallions", "spring onions"), AliasEntry("spring onions", "spring onion"))

        assertEquals(listOf("scallions"), aliasChains(chained))
        assertEquals(emptyList(), aliasChains(listOf(AliasEntry("scallions", "spring onion"))))
    }

    @Test
    fun `self-substitution check rejects an entry substituting its own key`() {
        assertEquals(listOf("kale"), selfSubstitutions(listOf(season("kale", listOf("kale")))))
        assertEquals(emptyList(), selfSubstitutions(listOf(season("kale", listOf("cabbage")))))
    }

    @Test
    fun `cross-resolution check rejects an alias target outside the known keys`() {
        val known = setOf("onion")

        assertEquals(listOf("nothing"), unresolvedAliasTargets(listOf(AliasEntry("x", "nothing")), known))
        assertEquals(emptyList(), unresolvedAliasTargets(listOf(AliasEntry("x", "onion")), known))
    }

    @Test
    fun `cross-resolution check rejects a substitution outside staples and seasonality keys`() {
        val known = setOf("onion", "kale")

        assertEquals(listOf("mango"), unresolvedSubstitutions(listOf(season("kale", listOf("onion", "mango"))), known))
        assertEquals(emptyList(), unresolvedSubstitutions(listOf(season("kale", listOf("onion"))), known))
    }

    @Test
    fun `unmapped key check rejects a staples key with no section mapping`() {
        val sections = SectionOrderTable(
            sections = listOf(SectionOrderEntry("Fresh Food", 0)),
            mappings = listOf(SectionMapping("onion", "Fresh Food")),
        )

        assertEquals(listOf("flour"), unmappedStaplesKeys(listOf(staple("onion"), staple("flour")), sections))
        assertEquals(emptyList(), unmappedStaplesKeys(listOf(staple("onion")), sections))
    }

    @Test
    fun `nutrient upper bound check rejects energy above 1000 kcal`() {
        assertEquals(listOf("oil"), nutrientBoundViolations(listOf(staple("oil", energy = 1000.5), staple("onion"))))
        assertEquals(emptyList(), nutrientBoundViolations(listOf(staple("oil", energy = 1000.0))))
    }

    @Test
    fun `nutrient upper bound check rejects a macro above 100 g`() {
        assertEquals(listOf("a"), nutrientBoundViolations(listOf(staple("a", protein = 100.5))))
        assertEquals(listOf("b"), nutrientBoundViolations(listOf(staple("b", fat = 100.5))))
        assertEquals(listOf("c"), nutrientBoundViolations(listOf(staple("c", carbs = 100.5))))
        assertEquals(emptyList(), nutrientBoundViolations(listOf(staple("d", protein = 100.0, fat = 100.0, carbs = 100.0))))
    }

    @Test
    fun `section set comparison fails on a stale or an extra snapshot line`() {
        val sections = listOf("Fresh Food", "Bakery")

        assertEquals(emptyList(), sectionSetDifference(sections, "Fresh Food\n\nBakery\n"))
        assertEquals(listOf("Frozen"), sectionSetDifference(sections, "Fresh Food\nBakery\nFrozen"))
        assertEquals(listOf("Bakery"), sectionSetDifference(sections, "Fresh Food"))
    }

    @Test
    fun `substitution presence check rejects a slice with no substitutions`() {
        assertFalse(hasSubstitution(listOf(season("kale"), season("leek"))))
        assertTrue(hasSubstitution(listOf(season("kale"), season("leek", listOf("onion")))))
    }

    @Test
    fun `depth measure gives 3 and 4 for the design layouts and rejects depth 5`() {
        val staples = """{"entries": [{"key": "water"}]}"""
        val aliases = """{"entries": [{"variant": "a", "canonicalKey": "b"}]}"""
        val sectionOrder = """{"sections": [{"name": "F", "walkIndex": 0}], "mappings": [{"key": "k", "section": "F"}]}"""
        val seasonality = """{"entries": [{"key": "k", "inSeasonMonths": [6], "substitutions": []}]}"""

        assertEquals(3, jsonDepth(staples))
        assertEquals(3, jsonDepth(aliases))
        assertEquals(3, jsonDepth(sectionOrder))
        assertEquals(4, jsonDepth(seasonality))
        assertEquals(5, jsonDepth("[[[[[]]]]]"))
        assertTrue(jsonDepth("[[[[[]]]]]") > MAX_ASSET_DEPTH)
        assertEquals(1, jsonDepth("""{"note": "[[[ brackets inside a string ]]]"}"""))
    }

    @Test
    fun `size cap rejects an asset over 1 MB`() {
        assertFalse(exceedsSizeCap(ByteArray(1_048_576)))
        assertTrue(exceedsSizeCap(ByteArray(1_048_577)))
    }

    // -----------------------------------------------------------------------------------------
    // T22: the tests above prove each helper against fixtures; these run them against the real
    // shipped assets (R1 AC1, AC3; R3 AC2, AC3; R4 AC1–AC5; R5 AC1–AC4; AD11).
    // -----------------------------------------------------------------------------------------

    private fun assetSource() = AndroidAssetSource(ApplicationProvider.getApplicationContext<Context>().assets)

    private fun assetBytes(dataset: ReferenceDataset): ByteArray =
        ApplicationProvider.getApplicationContext<Context>().assets.open(dataset.assetPath).use { it.readBytes() }

    private fun assetFile(dataset: ReferenceDataset): File = File(RepoPaths.repoRoot(), "app/src/main/assets/${dataset.assetPath}")

    /** Strict UTF-8 decode, the same contract `DatasetParser.readRoot` relies on; throws on malformed input. */
    private fun decodeStrictUtf8(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private fun rawArrayLength(bytes: ByteArray, field: String): Int =
        (JSONTokener(decodeStrictUtf8(bytes)).nextValue() as JSONObject).getJSONArray(field).length()

    private fun expectedSectionsText(): String =
        checkNotNull(javaClass.getResourceAsStream("/reference/expected_sections.txt")) { "missing expected_sections.txt" }
            .readBytes().toString(Charsets.UTF_8)

    private fun staplesTable(): StaplesTable =
        assertIs<LoadResult.Ready<StaplesTable>>(DatasetLoader.loadDataset(assetSource(), ReferenceDataset.STAPLES, DatasetParser::parseStaples)).table

    private fun aliasTable(): AliasTable =
        assertIs<LoadResult.Ready<AliasTable>>(DatasetLoader.loadDataset(assetSource(), ReferenceDataset.ALIASES, DatasetParser::parseAliases)).table

    private fun seasonalityTable(): SeasonalityTable =
        assertIs<LoadResult.Ready<SeasonalityTable>>(DatasetLoader.loadDataset(assetSource(), ReferenceDataset.SEASONALITY, DatasetParser::parseSeasonality)).table

    private fun sectionOrderTable(): SectionOrderTable =
        assertIs<LoadResult.Ready<SectionOrderTable>>(DatasetLoader.loadDataset(assetSource(), ReferenceDataset.SECTION_ORDER, DatasetParser::parseSectionOrder)).table

    @Test
    fun `shipped staples count is within 160 to 180 inclusive`() {
        val count = staplesTable().entries.size

        assertTrue(count in 160..180, "expected 160..180 staples entries, was $count")
    }

    @Test
    fun `four dataset assets exist under main assets reference`() {
        for (dataset in ReferenceDataset.entries) {
            assertTrue(assetFile(dataset).isFile, "expected ${assetFile(dataset)} to exist")
        }
    }

    @Test
    fun `every shipped asset decodes as strict UTF-8 within the size and depth caps`() {
        for (dataset in ReferenceDataset.entries) {
            val bytes = assetBytes(dataset)
            assertFalse(exceedsSizeCap(bytes), "${dataset.name} exceeds $MAX_ASSET_BYTES bytes")
            val text = decodeStrictUtf8(bytes)
            assertTrue(jsonDepth(text) <= MAX_ASSET_DEPTH, "${dataset.name} exceeds depth $MAX_ASSET_DEPTH")
        }
    }

    @Test
    fun `every shipped asset loads Ready with count equal to its raw array length`() {
        assertEquals(rawArrayLength(assetBytes(ReferenceDataset.STAPLES), "entries"), staplesTable().entries.size)
        assertEquals(rawArrayLength(assetBytes(ReferenceDataset.ALIASES), "entries"), aliasTable().entries.size)
        assertEquals(rawArrayLength(assetBytes(ReferenceDataset.SEASONALITY), "entries"), seasonalityTable().entries.size)
        val rawSections = rawArrayLength(assetBytes(ReferenceDataset.SECTION_ORDER), "sections")
        val rawMappings = rawArrayLength(assetBytes(ReferenceDataset.SECTION_ORDER), "mappings")
        val table = sectionOrderTable()
        assertEquals(rawSections, table.sections.size)
        assertEquals(rawMappings, table.mappings.size)
    }

    @Test
    fun `every shipped staples entry is PER_100G with finite non-negative bounded nutrients`() {
        val entries = staplesTable().entries

        for (entry in entries) {
            assertEquals(NutritionBasis.PER_100G, entry.basis, "${entry.key}: basis")
            for ((label, value) in listOf("energyKcal" to entry.energyKcal, "proteinG" to entry.proteinG, "fatG" to entry.fatG, "carbohydrateG" to entry.carbohydrateG)) {
                assertTrue(value.isFinite() && value >= 0.0, "${entry.key}: $label must be finite and non-negative, was $value")
            }
        }
        assertEquals(emptyList(), nutrientBoundViolations(entries))
    }

    @Test
    fun `every shipped staples key is in canonical surface form and unique`() {
        val keys = staplesTable().entries.map { it.key }

        assertEquals(emptyList(), surfaceFormViolations(keys))
        assertEquals(keys.size, keys.toSet().size, "duplicate staples key")
    }

    @Test
    fun `every shipped section has one unique walk index`() {
        val sections = sectionOrderTable().sections
        val indexes = sections.map { it.walkIndex }

        assertTrue(indexes.all { it >= 0 }, "a walkIndex is negative")
        assertEquals(indexes.size, indexes.toSet().size, "a walkIndex is repeated")
    }

    @Test
    fun `every shipped mapping names a listed indexed section`() {
        val table = sectionOrderTable()
        val sectionNames = table.sections.map { it.name }.toSet()

        for (mapping in table.mappings) {
            assertTrue(mapping.section in sectionNames, "mapping key '${mapping.key}' names unlisted section '${mapping.section}'")
        }
    }

    @Test
    fun `every shipped staples key resolves to a listed section`() {
        assertEquals(emptyList(), unmappedStaplesKeys(staplesTable().entries, sectionOrderTable()))
    }

    @Test
    fun `shipped section names equal the expected sections snapshot`() {
        val sectionNames = sectionOrderTable().sections.map { it.name }

        assertEquals(emptyList(), sectionSetDifference(sectionNames, expectedSectionsText()))
    }

    @Test
    fun `shipped seasonality months are non-empty distinct values from 1 to 12`() {
        for (entry in seasonalityTable().entries) {
            assertTrue(entry.inSeasonMonths.isNotEmpty(), "${entry.key}: inSeasonMonths must not be empty")
            assertTrue(entry.inSeasonMonths.all { it in 1..12 }, "${entry.key}: inSeasonMonths must be 1..12")
        }
    }

    @Test
    fun `shipped seasonality names at least one substitution and none of its own key`() {
        val entries = seasonalityTable().entries

        assertTrue(hasSubstitution(entries), "no seasonality entry names a substitution")
        assertEquals(emptyList(), selfSubstitutions(entries))
    }

    @Test
    fun `shipped aliases are surface form never self-mapped and never chained`() {
        val entries = aliasTable().entries
        val allKeys = entries.flatMap { listOf(it.variant, it.canonicalKey) }

        assertEquals(emptyList(), surfaceFormViolations(allKeys))
        assertEquals(emptyList(), aliasSelfMaps(entries))
        assertEquals(emptyList(), aliasChains(entries))
    }

    @Test
    fun `shipped alias targets and substitutions resolve to known keys`() {
        // AD11: known = staplesKeys ∪ seasonalityKeys ∪ sectionMappingKeys for alias targets; substitutions
        // resolve against the narrower staplesKeys ∪ seasonalityKeys only (no section-mapping-only keys).
        val staplesKeys = staplesTable().entries.map { it.key }.toSet()
        val seasonalityEntries = seasonalityTable().entries
        val seasonalityKeys = seasonalityEntries.map { it.key }.toSet()
        val sectionMappingKeys = sectionOrderTable().mappings.map { it.key }.toSet()
        val aliasKnown = staplesKeys + seasonalityKeys + sectionMappingKeys
        val substitutionKnown = staplesKeys + seasonalityKeys

        assertEquals(emptyList(), unresolvedAliasTargets(aliasTable().entries, aliasKnown))
        assertEquals(emptyList(), unresolvedSubstitutions(seasonalityEntries, substitutionKnown))
    }
}
