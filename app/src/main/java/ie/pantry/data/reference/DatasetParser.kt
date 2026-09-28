package ie.pantry.data.reference

import ie.pantry.data.db.entity.NutritionBasis
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Turns one asset's bytes into a [LoadResult] of its table under strict rules: only `has` and `get` followed by
 * an explicit type check (never an `opt*` accessor or a coercing typed getter), no defaults, no content in any
 * failure. Every failure is a [LoadFailure] built from constants and positions; a `JSONException` message, which
 * can embed the whole asset, is never kept.
 *
 * Within one entry the order is: missing fields, then an unexpected field, then wrong types in schema order,
 * then invalid values, then a duplicate key against earlier entries.
 */
internal object DatasetParser {

    private const val ENTRIES = "entries"
    private const val SECTIONS = "sections"
    private const val MAPPINGS = "mappings"

    fun parseStaples(bytes: ByteArray): LoadResult<StaplesTable> = parse(ReferenceDataset.STAPLES) { reader ->
        val root = reader.readRoot(bytes)
        reader.requireFields(root, listOf(ENTRIES), array = null, entryIndex = null)
        val array = reader.array(root, ENTRIES)
        val seenKeys = HashSet<String>()
        val entries = ArrayList<StaplesEntry>(array.length())
        for (i in 0 until array.length()) {
            val entry = reader.entry(array, ENTRIES, i)
            reader.requireFields(
                entry,
                listOf("key", "basis", "energyKcal", "proteinG", "fatG", "carbohydrateG"),
                ENTRIES,
                i,
            )
            val key = reader.string(entry, "key", ENTRIES, i)
            val basisName = reader.string(entry, "basis", ENTRIES, i)
            val energy = reader.number(entry, "energyKcal", ENTRIES, i)
            val protein = reader.number(entry, "proteinG", ENTRIES, i)
            val fat = reader.number(entry, "fatG", ENTRIES, i)
            val carbohydrate = reader.number(entry, "carbohydrateG", ENTRIES, i)
            val basis = NutritionBasis.entries.firstOrNull { it.name == basisName }
                ?: reader.fail(LoadFailure.Category.INVALID_ENUM_VALUE, ENTRIES, i, "basis")
            reader.requireNutrient(energy, "energyKcal", ENTRIES, i)
            reader.requireNutrient(protein, "proteinG", ENTRIES, i)
            reader.requireNutrient(fat, "fatG", ENTRIES, i)
            reader.requireNutrient(carbohydrate, "carbohydrateG", ENTRIES, i)
            if (!seenKeys.add(key)) reader.fail(LoadFailure.Category.DUPLICATE_KEY, ENTRIES, i, "key")
            entries += StaplesEntry(key, basis, energy, protein, fat, carbohydrate)
        }
        StaplesTable(entries)
    }

    fun parseAliases(bytes: ByteArray): LoadResult<AliasTable> = parse(ReferenceDataset.ALIASES) { reader ->
        val root = reader.readRoot(bytes)
        reader.requireFields(root, listOf(ENTRIES), array = null, entryIndex = null)
        val array = reader.array(root, ENTRIES)
        val seenVariants = HashSet<String>()
        val entries = ArrayList<AliasEntry>(array.length())
        for (i in 0 until array.length()) {
            val entry = reader.entry(array, ENTRIES, i)
            reader.requireFields(entry, listOf("variant", "canonicalKey"), ENTRIES, i)
            val variant = reader.string(entry, "variant", ENTRIES, i)
            val canonicalKey = reader.string(entry, "canonicalKey", ENTRIES, i)
            if (!seenVariants.add(variant)) reader.fail(LoadFailure.Category.DUPLICATE_KEY, ENTRIES, i, "variant")
            entries += AliasEntry(variant, canonicalKey)
        }
        AliasTable(entries)
    }

    fun parseSeasonality(bytes: ByteArray): LoadResult<SeasonalityTable> = parse(ReferenceDataset.SEASONALITY) { reader ->
        val root = reader.readRoot(bytes)
        reader.requireFields(root, listOf(ENTRIES), array = null, entryIndex = null)
        val array = reader.array(root, ENTRIES)
        val seenKeys = HashSet<String>()
        val entries = ArrayList<SeasonalityEntry>(array.length())
        for (i in 0 until array.length()) {
            val entry = reader.entry(array, ENTRIES, i)
            reader.requireFields(entry, listOf("key", "inSeasonMonths", "substitutions"), ENTRIES, i)
            val key = reader.string(entry, "key", ENTRIES, i)
            val months = reader.wholeNumbers(entry, "inSeasonMonths", ENTRIES, i)
            val substitutions = reader.strings(entry, "substitutions", ENTRIES, i)
            if (months.isEmpty() || months.any { it !in 1..12 } || months.toSet().size != months.size) {
                reader.fail(LoadFailure.Category.INVALID_VALUE, ENTRIES, i, "inSeasonMonths")
            }
            if (!seenKeys.add(key)) reader.fail(LoadFailure.Category.DUPLICATE_KEY, ENTRIES, i, "key")
            entries += SeasonalityEntry(key, LinkedHashSet(months), substitutions)
        }
        SeasonalityTable(entries)
    }

    /** `sections` are parsed before `mappings`, whatever their order in the file. */
    fun parseSectionOrder(bytes: ByteArray): LoadResult<SectionOrderTable> = parse(ReferenceDataset.SECTION_ORDER) { reader ->
        val root = reader.readRoot(bytes)
        reader.requireFields(root, listOf(SECTIONS, MAPPINGS), array = null, entryIndex = null)

        val sectionArray = reader.array(root, SECTIONS)
        val names = HashSet<String>()
        val walkIndexes = HashSet<Int>()
        val sections = ArrayList<SectionOrderEntry>(sectionArray.length())
        for (i in 0 until sectionArray.length()) {
            val entry = reader.entry(sectionArray, SECTIONS, i)
            reader.requireFields(entry, listOf("name", "walkIndex"), SECTIONS, i)
            val name = reader.string(entry, "name", SECTIONS, i)
            val walkIndex = reader.wholeNumber(entry, "walkIndex", SECTIONS, i)
            if (walkIndex < 0 || !walkIndexes.add(walkIndex)) {
                reader.fail(LoadFailure.Category.INVALID_VALUE, SECTIONS, i, "walkIndex")
            }
            if (!names.add(name)) reader.fail(LoadFailure.Category.DUPLICATE_KEY, SECTIONS, i, "name")
            sections += SectionOrderEntry(name, walkIndex)
        }

        val mappingArray = reader.array(root, MAPPINGS)
        val mappedKeys = HashSet<String>()
        val mappings = ArrayList<SectionMapping>(mappingArray.length())
        for (i in 0 until mappingArray.length()) {
            val entry = reader.entry(mappingArray, MAPPINGS, i)
            reader.requireFields(entry, listOf("key", "section"), MAPPINGS, i)
            val key = reader.string(entry, "key", MAPPINGS, i)
            val section = reader.string(entry, "section", MAPPINGS, i)
            if (section !in names) reader.fail(LoadFailure.Category.INVALID_VALUE, MAPPINGS, i, "section")
            if (!mappedKeys.add(key)) reader.fail(LoadFailure.Category.DUPLICATE_KEY, MAPPINGS, i, "key")
            mappings += SectionMapping(key, section)
        }
        SectionOrderTable(sections, mappings)
    }

    private inline fun <T> parse(dataset: ReferenceDataset, build: (Reader) -> T): LoadResult<T> =
        try {
            LoadResult.Ready(build(Reader(dataset)))
        } catch (abort: ParseAbort) {
            LoadFailed(abort.failure)
        } catch (_: JSONException) {
            // The tokener reports no position, and its message can embed the asset: keep neither.
            LoadFailed(LoadFailure(dataset, LoadFailure.Category.INVALID_JSON, null, null, null))
        } catch (_: CharacterCodingException) {
            LoadFailed(LoadFailure(dataset, LoadFailure.Category.INVALID_JSON, null, null, null))
        }

    /** A stackless unwind carrier: caught inside the parser and never leaves it. */
    private class ParseAbort(val failure: LoadFailure) : Exception(null, null, false, false)

    private class Reader(private val dataset: ReferenceDataset) {

        fun fail(category: LoadFailure.Category, array: String? = null, entryIndex: Int? = null, field: String? = null): Nothing =
            throw ParseAbort(LoadFailure(dataset, category, array, entryIndex, field))

        /** Strict UTF-8, then one JSON value with nothing after it, which must be an object. */
        fun readRoot(bytes: ByteArray): JSONObject {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            val tokener = JSONTokener(text)
            val value = tokener.nextValue()
            if (tokener.nextClean() != '\u0000') fail(LoadFailure.Category.INVALID_JSON)
            return value as? JSONObject ?: fail(LoadFailure.Category.WRONG_TYPE)
        }

        /** Reports a missing required field first, then any field outside the allowed set. */
        fun requireFields(obj: JSONObject, required: List<String>, array: String?, entryIndex: Int?) {
            for (field in required) {
                if (!obj.has(field)) fail(LoadFailure.Category.MISSING_FIELD, array, entryIndex, field)
            }
            val allowed = required.toSet()
            val names = obj.keys()
            while (names.hasNext()) {
                if (names.next() !in allowed) fail(LoadFailure.Category.UNEXPECTED_FIELD, array, entryIndex, null)
            }
        }

        fun array(obj: JSONObject, field: String): JSONArray =
            obj.get(field) as? JSONArray ?: fail(LoadFailure.Category.WRONG_TYPE, null, null, field)

        /** One array element as an object. A hole (a stored null) is `INVALID_JSON` at its position. */
        fun entry(array: JSONArray, arrayName: String, index: Int): JSONObject {
            val value = try {
                array.get(index)
            } catch (_: JSONException) {
                fail(LoadFailure.Category.INVALID_JSON, arrayName, index, null)
            }
            return value as? JSONObject ?: fail(LoadFailure.Category.WRONG_TYPE, arrayName, index, null)
        }

        fun string(obj: JSONObject, field: String, array: String, index: Int): String =
            obj.get(field) as? String ?: fail(LoadFailure.Category.WRONG_TYPE, array, index, field)

        /** A JSON number as a double; a string, `null`, boolean, array or object is `WRONG_TYPE`. */
        fun number(obj: JSONObject, field: String, array: String, index: Int): Double =
            when (val value = obj.get(field)) {
                is Int -> value.toDouble()
                is Long -> value.toDouble()
                is Double -> value
                else -> fail(LoadFailure.Category.WRONG_TYPE, array, index, field)
            }

        /** A whole number is an `Int` only: `3.0`, a `Long`-range value, a string and `null` are `WRONG_TYPE`. */
        fun wholeNumber(obj: JSONObject, field: String, array: String, index: Int): Int =
            obj.get(field) as? Int ?: fail(LoadFailure.Category.WRONG_TYPE, array, index, field)

        /** A JSON array of `Int`; any other element type, or a non-array field, is `WRONG_TYPE` at the field. */
        fun wholeNumbers(obj: JSONObject, field: String, array: String, index: Int): List<Int> {
            val values = obj.get(field) as? JSONArray ?: fail(LoadFailure.Category.WRONG_TYPE, array, index, field)
            return List(values.length()) { values.get(it) as? Int ?: fail(LoadFailure.Category.WRONG_TYPE, array, index, field) }
        }

        /** A JSON array of strings (possibly empty); any other element type, or a non-array field, is `WRONG_TYPE`. */
        fun strings(obj: JSONObject, field: String, array: String, index: Int): List<String> {
            val values = obj.get(field) as? JSONArray ?: fail(LoadFailure.Category.WRONG_TYPE, array, index, field)
            return List(values.length()) { values.get(it) as? String ?: fail(LoadFailure.Category.WRONG_TYPE, array, index, field) }
        }

        /** A nutrient must be finite and not below zero; `-0.0` equals `0.0` and is accepted. */
        fun requireNutrient(value: Double, field: String, array: String, index: Int) {
            if (!value.isFinite() || value < 0.0) fail(LoadFailure.Category.INVALID_VALUE, array, index, field)
        }
    }
}
