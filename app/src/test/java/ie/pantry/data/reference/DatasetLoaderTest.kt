package ie.pantry.data.reference

import ie.pantry.data.reference.DatasetLoader
import ie.pantry.testutil.FailingMidReadInputStream
import ie.pantry.testutil.FixtureAssetSource
import ie.pantry.testutil.RecordingAssetSource
import ie.pantry.testutil.Sentinels
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * R1: strict parsing. Each row is one fixture and one named test asserting the exact
 * (category, array, entryIndex, field) tuple through one shared helper that also checks the failure's
 * `toString()` carries no sentinel. The rows call [DatasetParser] on bytes: no loader exists yet.
 */
@RunWith(RobolectricTestRunner::class)
class DatasetLoaderTest {

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/reference/fixtures/$name")) { "missing fixture $name" }.readBytes()

    private fun assertFailure(
        result: LoadResult<*>,
        category: LoadFailure.Category,
        array: String? = null,
        entryIndex: Int? = null,
        field: String? = null,
        dataset: ReferenceDataset = ReferenceDataset.STAPLES,
    ) {
        val failed = assertIs<LoadFailed>(result, "expected a load failure but got $result")
        assertEquals(LoadFailure(dataset, category, array, entryIndex, field), failed.failure)
        val text = failed.failure.toString()
        for (sentinel in Sentinels.all) assertFalse(text.contains(sentinel), "sentinel leaked into toString")
    }

    private fun ready(result: LoadResult<StaplesTable>): StaplesTable =
        assertIs<LoadResult.Ready<StaplesTable>>(result, "expected Ready but got $result").table

    private fun staples(name: String) = DatasetParser.parseStaples(fixture(name))

    // ---- T5: staples entry-level rows ----

    @Test
    fun `staples valid fixture loads Ready with count equal to array length`() {
        assertEquals(3, ready(staples("staples_valid.json")).entries.size)
    }

    @Test
    fun `staples empty entries array loads Ready with zero entries`() {
        assertEquals(0, ready(staples("staples_empty.json")).entries.size)
    }

    @Test
    fun `staples entry missing energyKcal fails MISSING_FIELD at entries 1 energyKcal`() =
        assertFailure(staples("staples_missing_energy.json"), LoadFailure.Category.MISSING_FIELD, "entries", 1, "energyKcal")

    @Test
    fun `staples repeated key fails DUPLICATE_KEY at entries 2 key`() =
        assertFailure(staples("staples_duplicate_key.json"), LoadFailure.Category.DUPLICATE_KEY, "entries", 2, "key")

    @Test
    fun `staples energyKcal as string fails WRONG_TYPE at entries 0 energyKcal`() =
        assertFailure(staples("staples_energy_as_string.json"), LoadFailure.Category.WRONG_TYPE, "entries", 0, "energyKcal")

    @Test
    fun `staples key as number fails WRONG_TYPE at entries 0 key`() =
        assertFailure(staples("staples_key_as_number.json"), LoadFailure.Category.WRONG_TYPE, "entries", 0, "key")

    @Test
    fun `staples proteinG null fails WRONG_TYPE at entries 1 proteinG`() =
        assertFailure(staples("staples_protein_null.json"), LoadFailure.Category.WRONG_TYPE, "entries", 1, "proteinG")

    @Test
    fun `staples lowercase basis fails INVALID_ENUM_VALUE at entries 0 basis`() =
        assertFailure(staples("staples_bad_basis.json"), LoadFailure.Category.INVALID_ENUM_VALUE, "entries", 0, "basis")

    @Test
    fun `staples negative fatG fails INVALID_VALUE at entries 0 fatG`() =
        assertFailure(staples("staples_negative_fat.json"), LoadFailure.Category.INVALID_VALUE, "entries", 0, "fatG")

    @Test
    fun `staples fibreG field fails UNEXPECTED_FIELD at entries 1 with no field name`() =
        assertFailure(staples("staples_unexpected_field.json"), LoadFailure.Category.UNEXPECTED_FIELD, "entries", 1, null)

    // ---- T6: document-level and top-level structure rows ----

    @Test
    fun `truncated staples document fails INVALID_JSON with no position and no sentinel`() =
        assertFailure(staples("invalid_json.json"), LoadFailure.Category.INVALID_JSON)

    @Test
    fun `malformed UTF-8 bytes fail INVALID_JSON with no position`() {
        val malformed = byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D)

        assertFailure(DatasetParser.parseStaples(malformed), LoadFailure.Category.INVALID_JSON)
    }

    @Test
    fun `zero-byte asset fails INVALID_JSON with no position`() =
        assertFailure(DatasetParser.parseStaples(ByteArray(0)), LoadFailure.Category.INVALID_JSON)

    @Test
    fun `whitespace-only asset fails INVALID_JSON with no position`() =
        assertFailure(DatasetParser.parseStaples("  \n\t ".toByteArray()), LoadFailure.Category.INVALID_JSON)

    @Test
    fun `content after the closing brace fails INVALID_JSON with no position`() =
        assertFailure(staples("staples_trailing_content.json"), LoadFailure.Category.INVALID_JSON)

    @Test
    fun `trailing comma array hole fails INVALID_JSON at entries 1 with no field`() =
        assertFailure(staples("staples_array_hole.json"), LoadFailure.Category.INVALID_JSON, "entries", 1, null)

    @Test
    fun `top-level null fails WRONG_TYPE with no position`() =
        assertFailure(staples("top_level_null.json"), LoadFailure.Category.WRONG_TYPE)

    @Test
    fun `top-level scalar fails WRONG_TYPE with no position`() =
        assertFailure(staples("top_level_scalar.json"), LoadFailure.Category.WRONG_TYPE)

    @Test
    fun `top-level array fails WRONG_TYPE with no position`() =
        assertFailure(staples("top_level_array.json"), LoadFailure.Category.WRONG_TYPE)

    @Test
    fun `missing entries array fails MISSING_FIELD with field entries and no entry position`() =
        assertFailure(staples("staples_missing_entries.json"), LoadFailure.Category.MISSING_FIELD, null, null, "entries")

    @Test
    fun `unexpected top-level field fails UNEXPECTED_FIELD with no position`() =
        assertFailure(staples("staples_unexpected_top_level.json"), LoadFailure.Category.UNEXPECTED_FIELD)

    @Test
    fun `entries as an object fails WRONG_TYPE with field entries and no entry position`() =
        assertFailure(staples("staples_entries_not_array.json"), LoadFailure.Category.WRONG_TYPE, null, null, "entries")

    @Test
    fun `entry that is not an object fails WRONG_TYPE at entries 0 with no field`() =
        assertFailure(staples("staples_entry_not_object.json"), LoadFailure.Category.WRONG_TYPE, "entries", 0, null)

    // ---- T7: boundary and org.json characterisation rows ----

    @Test
    fun `negative zero fatG loads Ready`() {
        ready(staples("staples_negative_zero_fat.json"))
    }

    @Test
    fun `energyKcal 1e308 loads Ready at load time`() {
        ready(staples("staples_energy_1e308.json"))
    }

    @Test
    fun `NaN nutrient in an object fails INVALID_JSON with no position`() =
        assertFailure(staples("staples_nan_energy.json"), LoadFailure.Category.INVALID_JSON)

    @Test
    fun `energyKcal 1e999 fails INVALID_JSON with no position`() =
        assertFailure(staples("staples_energy_1e999.json"), LoadFailure.Category.INVALID_JSON)

    @Test
    fun `top-level NaN fails WRONG_TYPE with no position`() =
        assertFailure(staples("top_level_nan.json"), LoadFailure.Category.WRONG_TYPE)

    @Test
    fun `line comment inside a staples document is accepted`() {
        ready(staples("lenient_comment.json"))
    }

    @Test
    fun `single-quoted strings are accepted`() {
        ready(staples("lenient_single_quotes.json"))
    }

    @Test
    fun `unquoted field names are accepted`() {
        ready(staples("lenient_unquoted_names.json"))
    }

    @Test
    fun `leading-zero octal 010 is accepted as 8`() {
        val table = ready(staples("lenient_octal.json"))

        assertEquals(8.0, table.entries.single().energyKcal)
    }

    @Test
    fun `NUL after the closing brace is accepted`() {
        val valid = fixture("staples_valid.json")

        ready(DatasetParser.parseStaples(valid + byteArrayOf(0)))
    }

    // ---- T8: aliases ----

    private fun aliases(name: String) = DatasetParser.parseAliases(fixture(name))

    private fun assertAliasFailure(name: String, category: LoadFailure.Category, array: String? = null, index: Int? = null, field: String? = null) =
        assertFailure(aliases(name), category, array, index, field, ReferenceDataset.ALIASES)

    @Test
    fun `aliases valid fixture loads Ready with count equal to array length`() {
        assertEquals(2, assertIs<LoadResult.Ready<AliasTable>>(aliases("aliases_valid.json")).table.entries.size)
    }

    @Test
    fun `aliases repeated variant fails DUPLICATE_KEY at entries 1 variant`() =
        assertAliasFailure("aliases_duplicate_variant.json", LoadFailure.Category.DUPLICATE_KEY, "entries", 1, "variant")

    @Test
    fun `aliases entry missing canonicalKey fails MISSING_FIELD at entries 0 canonicalKey`() =
        assertAliasFailure("aliases_missing_canonical_key.json", LoadFailure.Category.MISSING_FIELD, "entries", 0, "canonicalKey")

    @Test
    fun `aliases variant as number fails WRONG_TYPE at entries 0 variant`() =
        assertAliasFailure("aliases_variant_as_number.json", LoadFailure.Category.WRONG_TYPE, "entries", 0, "variant")

    @Test
    fun `aliases canonicalKey null fails WRONG_TYPE at entries 1 canonicalKey`() =
        assertAliasFailure("aliases_canonical_key_null.json", LoadFailure.Category.WRONG_TYPE, "entries", 1, "canonicalKey")

    @Test
    fun `aliases extra field fails UNEXPECTED_FIELD at entries 0 with no field name`() =
        assertAliasFailure("aliases_unexpected_field.json", LoadFailure.Category.UNEXPECTED_FIELD, "entries", 0, null)

    @Test
    fun `aliases trailing content fails INVALID_JSON with no position`() =
        assertAliasFailure("aliases_trailing_content.json", LoadFailure.Category.INVALID_JSON)

    // ---- T9: seasonality ----

    private fun seasonality(name: String) = DatasetParser.parseSeasonality(fixture(name))

    private fun assertSeasonFailure(name: String, category: LoadFailure.Category, array: String? = null, index: Int? = null, field: String? = null) =
        assertFailure(seasonality(name), category, array, index, field, ReferenceDataset.SEASONALITY)

    private fun seasonTable(name: String) =
        assertIs<LoadResult.Ready<SeasonalityTable>>(seasonality(name), "expected Ready for $name").table

    @Test
    fun `seasonality valid fixture loads Ready with count equal to array length`() {
        assertEquals(3, seasonTable("seasonality_valid.json").entries.size)
    }

    @Test
    fun `seasonality months 1 and 12 load Ready`() {
        assertEquals(setOf(1, 12), seasonTable("seasonality_months_1_and_12.json").entries.single().inSeasonMonths)
    }

    @Test
    fun `seasonality month three point zero fails WRONG_TYPE at entries 0 inSeasonMonths`() =
        assertSeasonFailure("seasonality_fractional_month.json", LoadFailure.Category.WRONG_TYPE, "entries", 0, "inSeasonMonths")

    @Test
    fun `seasonality month as string fails WRONG_TYPE at entries 0 inSeasonMonths`() =
        assertSeasonFailure("seasonality_month_as_string.json", LoadFailure.Category.WRONG_TYPE, "entries", 0, "inSeasonMonths")

    @Test
    fun `seasonality Long-range month fails WRONG_TYPE at entries 0 inSeasonMonths`() =
        assertSeasonFailure("seasonality_long_month.json", LoadFailure.Category.WRONG_TYPE, "entries", 0, "inSeasonMonths")

    @Test
    fun `seasonality NaN month element fails WRONG_TYPE at entries 0 inSeasonMonths`() =
        assertSeasonFailure("seasonality_nan_month.json", LoadFailure.Category.WRONG_TYPE, "entries", 0, "inSeasonMonths")

    @Test
    fun `seasonality month 0 fails INVALID_VALUE at entries 0 inSeasonMonths`() =
        assertSeasonFailure("seasonality_month_0.json", LoadFailure.Category.INVALID_VALUE, "entries", 0, "inSeasonMonths")

    @Test
    fun `seasonality month 13 fails INVALID_VALUE at entries 1 inSeasonMonths`() =
        assertSeasonFailure("seasonality_month_13.json", LoadFailure.Category.INVALID_VALUE, "entries", 1, "inSeasonMonths")

    @Test
    fun `seasonality repeated month fails INVALID_VALUE at entries 0 inSeasonMonths`() =
        assertSeasonFailure("seasonality_repeated_month.json", LoadFailure.Category.INVALID_VALUE, "entries", 0, "inSeasonMonths")

    @Test
    fun `seasonality empty months fails INVALID_VALUE at entries 0 inSeasonMonths`() =
        assertSeasonFailure("seasonality_empty_months.json", LoadFailure.Category.INVALID_VALUE, "entries", 0, "inSeasonMonths")

    @Test
    fun `seasonality non-string substitution fails WRONG_TYPE at entries 0 substitutions`() =
        assertSeasonFailure("seasonality_substitution_not_string.json", LoadFailure.Category.WRONG_TYPE, "entries", 0, "substitutions")

    @Test
    fun `seasonality entry missing substitutions fails MISSING_FIELD at entries 0 substitutions`() =
        assertSeasonFailure("seasonality_missing_substitutions.json", LoadFailure.Category.MISSING_FIELD, "entries", 0, "substitutions")

    @Test
    fun `seasonality repeated key fails DUPLICATE_KEY at entries 1 key`() =
        assertSeasonFailure("seasonality_duplicate_key.json", LoadFailure.Category.DUPLICATE_KEY, "entries", 1, "key")

    @Test
    fun `seasonality extra field fails UNEXPECTED_FIELD at entries 0 with no field name`() =
        assertSeasonFailure("seasonality_unexpected_field.json", LoadFailure.Category.UNEXPECTED_FIELD, "entries", 0, null)

    @Test
    fun `seasonality truncated document fails INVALID_JSON with no position`() =
        assertSeasonFailure("seasonality_truncated.json", LoadFailure.Category.INVALID_JSON)

    @Test
    fun `extra seasonality entry added by JSON edit alone is absent before and found after`() {
        val before = seasonTable("seasonality_one.json")
        val after = seasonTable("seasonality_one_plus_extra.json")

        assertEquals(Lookup.Absent, before.lookup("fig"))
        assertIs<Lookup.Found<SeasonalityEntry>>(after.lookup("fig"))
    }

    // ---- T10: section order ----

    private fun sectionOrder(name: String) = DatasetParser.parseSectionOrder(fixture(name))

    private fun assertSectionFailure(name: String, category: LoadFailure.Category, array: String? = null, index: Int? = null, field: String? = null) =
        assertFailure(sectionOrder(name), category, array, index, field, ReferenceDataset.SECTION_ORDER)

    @Test
    fun `section order valid fixture loads Ready with counts equal to both array lengths`() {
        val table = assertIs<LoadResult.Ready<SectionOrderTable>>(sectionOrder("section_order_valid.json")).table

        assertEquals(3, table.sections.size)
        assertEquals(2, table.mappings.size)
        assertEquals(listOf(0, 10, 20), table.sections.map { it.walkIndex })
    }

    @Test
    fun `section order fractional walkIndex fails WRONG_TYPE at sections 1 walkIndex`() =
        assertSectionFailure("section_order_fractional_index.json", LoadFailure.Category.WRONG_TYPE, "sections", 1, "walkIndex")

    @Test
    fun `section order walkIndex three point zero fails WRONG_TYPE at sections 0 walkIndex`() =
        assertSectionFailure("section_order_whole_double_index.json", LoadFailure.Category.WRONG_TYPE, "sections", 0, "walkIndex")

    @Test
    fun `section order negative walkIndex fails INVALID_VALUE at sections 0 walkIndex`() =
        assertSectionFailure("section_order_negative_index.json", LoadFailure.Category.INVALID_VALUE, "sections", 0, "walkIndex")

    @Test
    fun `section order repeated walkIndex fails INVALID_VALUE at sections 2 walkIndex`() =
        assertSectionFailure("section_order_index_collision.json", LoadFailure.Category.INVALID_VALUE, "sections", 2, "walkIndex")

    @Test
    fun `section order mapping to unlisted section fails INVALID_VALUE at mappings 1 section`() =
        assertSectionFailure("section_order_unlisted_section.json", LoadFailure.Category.INVALID_VALUE, "mappings", 1, "section")

    @Test
    fun `section order repeated name fails DUPLICATE_KEY at sections 1 name`() =
        assertSectionFailure("section_order_duplicate_name.json", LoadFailure.Category.DUPLICATE_KEY, "sections", 1, "name")

    @Test
    fun `section order repeated mapping key fails DUPLICATE_KEY at mappings 1 key`() =
        assertSectionFailure("section_order_duplicate_mapping.json", LoadFailure.Category.DUPLICATE_KEY, "mappings", 1, "key")

    @Test
    fun `section order missing walkIndex fails MISSING_FIELD at sections 0 walkIndex`() =
        assertSectionFailure("section_order_missing_walk_index.json", LoadFailure.Category.MISSING_FIELD, "sections", 0, "walkIndex")

    @Test
    fun `section order missing mappings array fails MISSING_FIELD with field mappings and no entry position`() =
        assertSectionFailure("section_order_missing_mappings.json", LoadFailure.Category.MISSING_FIELD, null, null, "mappings")

    @Test
    fun `section order mapping extra field fails UNEXPECTED_FIELD at mappings 0 with no field name`() =
        assertSectionFailure("section_order_mapping_unexpected_field.json", LoadFailure.Category.UNEXPECTED_FIELD, "mappings", 0, null)

    // ---- T11: precedence and parity ----

    @Test
    fun `earlier failing entry wins over a later one`() =
        assertFailure(staples("staples_multi_fault_entries.json"), LoadFailure.Category.WRONG_TYPE, "entries", 1, "energyKcal")

    @Test
    fun `missing field wins over unexpected type and value faults in one entry`() =
        assertFailure(staples("staples_multi_fault_one_entry.json"), LoadFailure.Category.MISSING_FIELD, "entries", 0, "carbohydrateG")

    @Test
    fun `type fault on a later field wins over a value fault on an earlier field`() =
        assertFailure(staples("staples_type_before_value.json"), LoadFailure.Category.WRONG_TYPE, "entries", 0, "fatG")

    @Test
    fun `entry field fault wins over its duplicate key`() =
        assertFailure(staples("staples_field_fault_before_duplicate.json"), LoadFailure.Category.INVALID_VALUE, "entries", 1, "fatG")

    @Test
    fun `sections are checked before mappings whatever their file order`() =
        assertSectionFailure("section_order_sections_before_mappings.json", LoadFailure.Category.DUPLICATE_KEY, "sections", 1, "name")

    @Test
    fun `parser rejects every table invariant with its own category before the table constructor`() {
        val cases = listOf<Pair<LoadResult<*>, LoadFailure.Category>>(
            staples("staples_duplicate_key.json") to LoadFailure.Category.DUPLICATE_KEY,
            seasonality("seasonality_month_13.json") to LoadFailure.Category.INVALID_VALUE,
            sectionOrder("section_order_index_collision.json") to LoadFailure.Category.INVALID_VALUE,
            sectionOrder("section_order_unlisted_section.json") to LoadFailure.Category.INVALID_VALUE,
        )

        for ((result, expected) in cases) {
            val category = assertIs<LoadFailed>(result).failure.category
            assertEquals(expected, category)
            assertFalse(category == LoadFailure.Category.INTERNAL_ERROR, "a table invariant must be caught by the parser first")
        }
    }

    // ---- T12: AssetSource seam, loadDataset ----

    private fun loadStaples(source: AssetSource) = DatasetLoader.loadDataset(source, ReferenceDataset.STAPLES, DatasetParser::parseStaples)

    @Test
    fun `unmapped fixture path loads as ASSET_ABSENT with no position`() {
        val failed = assertIs<LoadFailed>(loadStaples(FixtureAssetSource(emptyMap())))

        assertEquals(LoadFailure(ReferenceDataset.STAPLES, LoadFailure.Category.ASSET_ABSENT, null, null, null), failed.failure)
    }

    @Test
    fun `unavailable source loads as ASSET_ABSENT and does not throw`() {
        val failed = assertIs<LoadFailed>(loadStaples(AssetSource.UNAVAILABLE))

        assertEquals(LoadFailure.Category.ASSET_ABSENT, failed.failure.category)
    }

    @Test
    fun `stream failing mid-read loads as ASSET_UNREADABLE`() {
        val source = AssetSource { FailingMidReadInputStream(goodBytes = 2) }

        val failed = assertIs<LoadFailed>(loadStaples(source))

        assertEquals(LoadFailure.Category.ASSET_UNREADABLE, failed.failure.category)
    }

    @Test
    fun `parse lambda throwing loads as INTERNAL_ERROR with no sentinel`() {
        val source = FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to ByteArray(0)))
        val throwingParse: (ByteArray) -> LoadResult<StaplesTable> = { error(Sentinels.INGREDIENT) }

        val failed = assertIs<LoadFailed>(DatasetLoader.loadDataset(source, ReferenceDataset.STAPLES, throwingParse))

        assertEquals(LoadFailure.Category.INTERNAL_ERROR, failed.failure.category)
        assertFalse(failed.failure.toString().contains(Sentinels.INGREDIENT))
    }

    @Test
    fun `valid fixture through the loader is Ready with one open and a closed stream`() {
        val bytes = fixture("staples_valid.json")
        val recording = RecordingAssetSource(FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to bytes)))

        val result = loadStaples(recording)

        assertEquals(3, ready(result).entries.size)
        assertEquals(1, recording.openCount)
        assertEquals(listOf(ReferenceDataset.STAPLES.assetPath), recording.openedPaths)
    }

    @Test
    fun `truncated fixture through the loader is INVALID_JSON with no sentinel`() {
        val bytes = fixture("invalid_json.json")
        val source = FixtureAssetSource(mapOf(ReferenceDataset.STAPLES.assetPath to bytes))

        assertFailure(loadStaples(source), LoadFailure.Category.INVALID_JSON)
    }

    @Test
    fun `android asset source throws FileNotFoundException for a missing asset`() {
        // Deliberately does not use any of the four ReferenceDataset asset paths: every one of them is
        // shipped as a real app/src/main/assets/reference/*.json file once its curation task lands, so a
        // path that starts out absent would silently stop testing this contract as curation completes.
        // AndroidAssetSource's FileNotFoundException-on-miss contract is what loadDataset's own
        // exception-to-category mapping (exhaustively covered elsewhere in this file via
        // FixtureAssetSource) relies on; this test is the one place that checks it against the real
        // Android AssetManager rather than a fixture.
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = ie.pantry.data.reference.AndroidAssetSource(context.assets)

        assertFailsWith<java.io.FileNotFoundException> { source.open("reference/does_not_exist.json") }
    }
}
