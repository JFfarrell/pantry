package ie.pantry.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A stored recipe. Optional fields are nullable with no column default: absent means absent
 * (an unstated yield or cooking time, no image, a manual entry with no source).
 */
@Entity(tableName = "recipe")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val method: String,
    val yieldServings: Int?,
    val cookingTimeMinutes: Int?,
    /** Path relative to `filesDir`, e.g. `thumbnails/<uuid>.jpg`; absent means an explicit no-image. */
    val thumbnailPath: String?,
    val sourceUrl: String?,
    val fetchedAt: Instant?,
    /** Overwritten by every `RecipeDao` write; there is no column default. */
    val updatedAt: Instant = Instant.EPOCH,
    /** Soft-delete marker; a non-null value hides the row from every recipe read. */
    val pendingDeletionAt: Instant? = null,
)
