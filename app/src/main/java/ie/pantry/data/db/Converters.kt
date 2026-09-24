package ie.pantry.data.db

import androidx.room.TypeConverter
import ie.pantry.data.db.entity.NutritionBasis
import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.db.entity.SelectionSnapshot
import java.time.Instant

/** Room column converters. An absent value (null, empty list) is preserved, never defaulted. */
class Converters {

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun quantityDimensionToString(value: QuantityDimension): String = value.name

    @TypeConverter
    fun stringToQuantityDimension(value: String): QuantityDimension = QuantityDimension.valueOf(value)

    @TypeConverter
    fun nutritionBasisToString(value: NutritionBasis): String = value.name

    @TypeConverter
    fun stringToNutritionBasis(value: String): NutritionBasis = NutritionBasis.valueOf(value)

    /** Comma-joined; the empty string means an empty list. */
    @TypeConverter
    fun longListToString(value: List<Long>): String = value.joinToString(",")

    @TypeConverter
    fun stringToLongList(value: String): List<Long> =
        if (value.isEmpty()) emptyList() else value.split(",").map(String::toLong)

    /** `recipeId:servings` pairs joined by `|`; servings is empty when null. */
    @TypeConverter
    fun snapshotsToString(value: List<SelectionSnapshot>): String =
        value.joinToString("|") { "${it.recipeId}:${it.servings ?: ""}" }

    @TypeConverter
    fun stringToSnapshots(value: String): List<SelectionSnapshot> =
        if (value.isEmpty()) {
            emptyList()
        } else {
            value.split("|").map { pair ->
                val (recipeId, servings) = pair.split(":", limit = 2)
                SelectionSnapshot(recipeId.toLong(), servings.ifEmpty { null }?.toInt())
            }
        }
}
