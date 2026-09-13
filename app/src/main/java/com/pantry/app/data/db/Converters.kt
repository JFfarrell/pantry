package com.pantry.app.data.db

import androidx.room.TypeConverter

class Converters {
    /**
     * Steps and tags live in one delimited column. The delimiter is the ASCII
     * unit separator, which will never appear in recipe prose.
     */
    private val sep = "\u001F"

    @TypeConverter
    fun stringListToDb(value: List<String>?): String = value.orEmpty().joinToString(sep)

    @TypeConverter
    fun dbToStringList(value: String?): List<String> =
        value?.takeIf { it.isNotEmpty() }?.split(sep) ?: emptyList()

    @TypeConverter
    fun slotToDb(slot: MealSlot): String = slot.name

    @TypeConverter
    fun dbToSlot(value: String): MealSlot = MealSlot.valueOf(value)
}
