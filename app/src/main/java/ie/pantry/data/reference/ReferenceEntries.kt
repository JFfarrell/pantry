package ie.pantry.data.reference

import ie.pantry.data.db.entity.NutritionBasis

/** A staples nutrition row: nutrients per [basis], each present, finite and non-negative. */
data class StaplesEntry(
    val key: String,
    val basis: NutritionBasis,
    val energyKcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbohydrateG: Double,
)

/** An already-normalised raw [variant] and the canonical key it maps to. */
data class AliasEntry(val variant: String, val canonicalKey: String)

/** The Republic-of-Ireland in-season [inSeasonMonths] (1 to 12) for a key, and its out-of-season [substitutions]. */
data class SeasonalityEntry(
    val key: String,
    val inSeasonMonths: Set<Int>,
    val substitutions: List<String>,
)

/** A supermarket section and its display position for a click-and-collect list (not a physical store walk); a lower [walkIndex] comes earlier. */
data class SectionOrderEntry(val name: String, val walkIndex: Int)

/** The one section a canonical [key] belongs to. */
data class SectionMapping(val key: String, val section: String)
