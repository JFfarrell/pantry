package ie.pantry.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ie.pantry.data.db.entity.NutritionCacheEntry

/** One-shot reads by key: no screen observes the cache, so there is no `Flow`. */
@Dao
interface NutritionCacheDao {

    /** @return the cached entry, or null when the key is unknown. */
    @Query("SELECT * FROM nutrition_cache_entry WHERE canonicalKey = :key")
    suspend fun find(key: String): NutritionCacheEntry?

    /** Inserts the entry, or replaces the existing entry for the same key. */
    @Upsert
    suspend fun upsert(entry: NutritionCacheEntry)
}
