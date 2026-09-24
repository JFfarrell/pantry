package ie.pantry.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** A cached nutrition lookup. The four nutrient columns are expressed per [basisUnit]. */
@Entity(tableName = "nutrition_cache_entry")
data class NutritionCacheEntry(
    @PrimaryKey val canonicalKey: String,
    val basisUnit: NutritionBasis,
    val energyKcal: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val carbohydrateG: Double?,
    val sourceAttribution: String,
    val licenceTag: String,
    val fetchedAt: Instant,
    val estimatedConversion: Boolean,
)
