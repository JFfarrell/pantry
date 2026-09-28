package ie.pantry.data.reference

import java.util.Collections

/*
 * Read-only tables over the parsed datasets. Each constructor copies its input and wraps every exposed
 * collection as unmodifiable, so a caller that downcasts a returned collection cannot change the shared
 * data. Invariant checks use constant messages: a message never carries a key or any asset text.
 */

class StaplesTable internal constructor(entries: List<StaplesEntry>) {

    /** Unmodifiable, in file order. */
    val entries: List<StaplesEntry> = Collections.unmodifiableList(ArrayList(entries))

    private val index: Map<String, StaplesEntry> = HashMap<String, StaplesEntry>().also { map ->
        for (entry in this.entries) require(map.put(entry.key, entry) == null) { "staples table: duplicate key" }
    }

    /** Exact match on [key]; never throws; a miss is [Lookup.Absent]. */
    fun lookup(key: String): Lookup<StaplesEntry> = index[key]?.let { Lookup.Found(it) } ?: Lookup.Absent
}

class AliasTable internal constructor(entries: List<AliasEntry>) {

    val entries: List<AliasEntry> = Collections.unmodifiableList(ArrayList(entries))

    private val index: Map<String, String> = HashMap<String, String>().also { map ->
        for (entry in this.entries) require(map.put(entry.variant, entry.canonicalKey) == null) { "alias table: duplicate variant" }
    }

    /** Exact match on the already-normalised [variant]; a hit carries the mapped canonical key. */
    fun lookup(variant: String): Lookup<String> = index[variant]?.let { Lookup.Found(it) } ?: Lookup.Absent
}

class SeasonalityTable internal constructor(entries: List<SeasonalityEntry>) {

    val entries: List<SeasonalityEntry> = Collections.unmodifiableList(
        ArrayList(
            entries.map { entry ->
                entry.copy(
                    inSeasonMonths = Collections.unmodifiableSet(LinkedHashSet(entry.inSeasonMonths)),
                    substitutions = Collections.unmodifiableList(ArrayList(entry.substitutions)),
                )
            },
        ),
    )

    private val index: Map<String, SeasonalityEntry> = HashMap<String, SeasonalityEntry>().also { map ->
        for (entry in this.entries) {
            require(entry.inSeasonMonths.isNotEmpty() && entry.inSeasonMonths.all { it in 1..12 }) {
                "seasonality table: months must be a non-empty set within 1 to 12"
            }
            require(map.put(entry.key, entry) == null) { "seasonality table: duplicate key" }
        }
    }

    fun lookup(key: String): Lookup<SeasonalityEntry> = index[key]?.let { Lookup.Found(it) } ?: Lookup.Absent
}

class SectionOrderTable internal constructor(sections: List<SectionOrderEntry>, mappings: List<SectionMapping>) {

    /** Unmodifiable, in file order (not re-sorted). */
    val sections: List<SectionOrderEntry> = Collections.unmodifiableList(ArrayList(sections))
    val mappings: List<SectionMapping> = Collections.unmodifiableList(ArrayList(mappings))

    private val sectionsByName: Map<String, SectionOrderEntry> = HashMap<String, SectionOrderEntry>().also { map ->
        val indexes = HashSet<Int>()
        for (section in this.sections) {
            require(section.walkIndex >= 0) { "section order table: walk index must not be negative" }
            require(indexes.add(section.walkIndex)) { "section order table: repeated walk index" }
            require(map.put(section.name, section) == null) { "section order table: duplicate section name" }
        }
    }

    private val mappedSection: Map<String, SectionOrderEntry> = HashMap<String, SectionOrderEntry>().also { map ->
        for (mapping in this.mappings) {
            val section = requireNotNull(sectionsByName[mapping.section]) { "section order table: mapping to an unlisted section" }
            require(map.put(mapping.key, section) == null) { "section order table: duplicate mapping key" }
        }
    }

    /** The section [key] maps to, with its walk index; [Lookup.Absent] when unmapped. */
    fun sectionFor(key: String): Lookup<SectionOrderEntry> = mappedSection[key]?.let { Lookup.Found(it) } ?: Lookup.Absent
}
