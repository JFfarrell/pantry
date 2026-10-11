package ie.pantry.recipeimport

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Cooking time and yield rules (AD10). Anything the rules do not accept is [DraftValue.Absent], never zero. */
internal object RecipeValues {

    private val DURATION = Regex(
        """^PT(?:(\d{1,4})H)?(?:(\d{1,5})M)?(?:(\d{1,6}(?:[.,]\d{1,9})?)S)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val BARE_INTEGER = Regex("""^(\d+)$""")
    private val LABEL_FIRST = Regex("""^(?:serves|servings|portions):?\s*(\d+)$""", RegexOption.IGNORE_CASE)
    private val COUNT_FIRST = Regex("""^(\d+)\s*(?:servings|portions)$""", RegexOption.IGNORE_CASE)
    private val SIXTY = BigDecimal(60)

    /** Whole minutes of an ISO 8601 time-only duration, rounded half up; zero or anything else is absent. */
    fun minutes(iso: String?): DraftValue<Int> {
        val match = DURATION.matchEntire(iso?.trim() ?: return DraftValue.Absent) ?: return DraftValue.Absent
        val (hours, minutes, seconds) = match.destructured
        if (hours.isEmpty() && minutes.isEmpty() && seconds.isEmpty()) return DraftValue.Absent
        var total = BigDecimal.ZERO
        if (hours.isNotEmpty()) total += BigDecimal(hours).multiply(BigDecimal(3600))
        if (minutes.isNotEmpty()) total += BigDecimal(minutes).multiply(SIXTY)
        if (seconds.isNotEmpty()) total += BigDecimal(seconds.replace(',', '.'))
        val whole = total.divide(SIXTY, 0, RoundingMode.HALF_UP).toInt()
        return if (whole >= 1) DraftValue.Stated(whole) else DraftValue.Absent
    }

    /** `totalTime` if it gives a stated value, else `cookTime`. */
    fun cookingMinutes(total: String?, cook: String?): DraftValue<Int> {
        val fromTotal = minutes(total)
        return if (fromTotal is DraftValue.Stated) fromTotal else minutes(cook)
    }

    /** The yield from a JSON value, or from the first element of an array. */
    fun servings(value: JsonElement?): DraftValue<Int> {
        val element = if (value is JsonArray) value.firstOrNull() else value
        val primitive = element as? JsonPrimitive ?: return DraftValue.Absent
        if (primitive.isString) return servings(primitive.content)
        return positive(primitive.content.toIntOrNull())
    }

    fun servings(text: String?): DraftValue<Int> {
        val trimmed = text?.trim() ?: return DraftValue.Absent
        val match = BARE_INTEGER.matchEntire(trimmed)
            ?: LABEL_FIRST.matchEntire(trimmed)
            ?: COUNT_FIRST.matchEntire(trimmed)
            ?: return DraftValue.Absent
        return positive(match.groupValues[1].toIntOrNull())
    }

    private fun positive(value: Int?): DraftValue<Int> =
        if (value != null && value >= 1) DraftValue.Stated(value) else DraftValue.Absent
}
