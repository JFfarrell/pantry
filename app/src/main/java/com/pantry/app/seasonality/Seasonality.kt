package com.pantry.app.seasonality

import com.pantry.app.domain.IngredientParser
import com.pantry.app.domain.TextMatch
import java.time.LocalDate
import java.time.Month

enum class SeasonStatus { IN_SEASON, SHOULDER, OUT_OF_SEASON, YEAR_ROUND, UNKNOWN }

data class SeasonAdvice(
    val ingredientName: String,
    val status: SeasonStatus,
    val peakMonths: List<Month>,
    val message: String
)

/**
 * Feature 7. UK growing seasons for produce that has one worth caring about.
 * Anything not listed -- store cupboard, meat, dairy, imported staples -- is
 * reported as UNKNOWN and simply not flagged, rather than guessed at.
 */
object Seasonality {

    private val JAN = 1; private val FEB = 2; private val MAR = 3; private val APR = 4
    private val MAY = 5; private val JUN = 6; private val JUL = 7; private val AUG = 8
    private val SEP = 9; private val OCT = 10; private val NOV = 11; private val DEC = 12

    /** Ingredient key to the months it is at its UK best. */
    private val calendar: Map<String, Set<Int>> = mapOf(
        "asparagus" to setOf(APR, MAY, JUN),
        "aubergine" to setOf(JUL, AUG, SEP, OCT),
        "beetroot" to setOf(JUN, JUL, AUG, SEP, OCT, NOV),
        "blackberry" to setOf(AUG, SEP, OCT),
        "blackcurrant" to setOf(JUL, AUG),
        "broad bean" to setOf(JUN, JUL, AUG),
        "broccoli" to setOf(JUL, AUG, SEP, OCT),
        "brussels sprout" to setOf(OCT, NOV, DEC, JAN, FEB),
        "cabbage" to setOf(SEP, OCT, NOV, DEC, JAN, FEB, MAR),
        "cauliflower" to setOf(SEP, OCT, NOV, DEC, JAN, FEB, MAR, APR),
        "celeriac" to setOf(OCT, NOV, DEC, JAN, FEB, MAR),
        "celery" to setOf(JUL, AUG, SEP, OCT),
        "cherry" to setOf(JUN, JUL, AUG),
        "chicory" to setOf(NOV, DEC, JAN, FEB),
        "courgette" to setOf(JUN, JUL, AUG, SEP),
        "cucumber" to setOf(MAY, JUN, JUL, AUG, SEP),
        "damson" to setOf(SEP, OCT),
        "fennel" to setOf(JUN, JUL, AUG, SEP),
        "gooseberry" to setOf(JUN, JUL, AUG),
        "green bean" to setOf(JUL, AUG, SEP),
        "jerusalem artichoke" to setOf(NOV, DEC, JAN, FEB),
        "kale" to setOf(OCT, NOV, DEC, JAN, FEB, MAR),
        "leek" to setOf(SEP, OCT, NOV, DEC, JAN, FEB, MAR, APR),
        "lettuce" to setOf(MAY, JUN, JUL, AUG, SEP),
        "marrow" to setOf(AUG, SEP, OCT),
        "new potato" to setOf(MAY, JUN, JUL, AUG),
        "parsnip" to setOf(SEP, OCT, NOV, DEC, JAN, FEB),
        "pea" to setOf(JUN, JUL, AUG),
        "pear" to setOf(SEP, OCT, NOV, DEC),
        "plum" to setOf(AUG, SEP, OCT),
        "pumpkin" to setOf(SEP, OCT, NOV),
        "purple sprouting broccoli" to setOf(FEB, MAR, APR),
        "radish" to setOf(APR, MAY, JUN, JUL, AUG, SEP),
        "raspberry" to setOf(JUN, JUL, AUG, SEP),
        "redcurrant" to setOf(JUL, AUG),
        "rhubarb" to setOf(JAN, FEB, MAR, APR, MAY, JUN),
        "rocket" to setOf(MAY, JUN, JUL, AUG, SEP),
        "runner bean" to setOf(JUL, AUG, SEP, OCT),
        "samphire" to setOf(JUN, JUL, AUG),
        "spinach" to setOf(APR, MAY, JUN, JUL, AUG, SEP),
        "spring green" to setOf(FEB, MAR, APR, MAY),
        "spring onion" to setOf(APR, MAY, JUN, JUL, AUG, SEP),
        "squash" to setOf(SEP, OCT, NOV, DEC),
        "strawberry" to setOf(JUN, JUL, AUG, SEP),
        "swede" to setOf(OCT, NOV, DEC, JAN, FEB, MAR),
        "sweetcorn" to setOf(AUG, SEP, OCT),
        "tomato" to setOf(JUL, AUG, SEP, OCT),
        "turnip" to setOf(OCT, NOV, DEC, JAN, FEB, MAR),
        "watercress" to setOf(APR, MAY, JUN, JUL, AUG, SEP),
        "wild garlic" to setOf(MAR, APR, MAY),
        // Reliably available all year in UK supermarkets; listed so they are
        // affirmatively "fine" rather than unknown.
        "carrot" to (1..12).toSet(),
        "onion" to (1..12).toSet(),
        "potato" to (1..12).toSet(),
        "garlic" to (1..12).toSet(),
        "mushroom" to (1..12).toSet(),
        "apple" to setOf(SEP, OCT, NOV, DEC, JAN)
    )

    /** Swaps to offer when something is out of season this month. */
    private val substitutes: Map<String, List<String>> = mapOf(
        "asparagus" to listOf("purple sprouting broccoli", "green beans", "tenderstem broccoli"),
        "tomato" to listOf("tinned tomatoes", "roasted peppers", "passata"),
        "strawberry" to listOf("frozen berries", "pear", "apple"),
        "raspberry" to listOf("frozen raspberries", "blackberries"),
        "courgette" to listOf("leek", "celeriac", "squash"),
        "aubergine" to listOf("mushroom", "squash"),
        "pea" to listOf("frozen peas", "broad beans"),
        "green bean" to listOf("frozen green beans", "kale", "cabbage"),
        "new potato" to listOf("waxy potatoes", "celeriac"),
        "spinach" to listOf("kale", "chard", "cabbage"),
        "rhubarb" to listOf("cooking apple", "plum"),
        "sweetcorn" to listOf("frozen sweetcorn", "peas")
    )

    fun advise(ingredientName: String, on: LocalDate = LocalDate.now()): SeasonAdvice {
        val key = IngredientParser.normaliseName(ingredientName)
        val entry = TextMatch.bestMatch(calendar, key)
            ?: return SeasonAdvice(ingredientName, SeasonStatus.UNKNOWN, emptyList(), "")

        val months = entry.value
        val peak = months.sorted().map { Month.of(it) }
        val month = on.monthValue

        if (months.size >= 12) {
            return SeasonAdvice(ingredientName, SeasonStatus.YEAR_ROUND, peak, "Available all year.")
        }

        val prev = if (month == 1) 12 else month - 1
        val next = if (month == 12) 1 else month + 1

        return when {
            month in months -> SeasonAdvice(
                ingredientName, SeasonStatus.IN_SEASON, peak,
                "In season now -- ${entry.key} is at its best."
            )
            prev in months || next in months -> SeasonAdvice(
                ingredientName, SeasonStatus.SHOULDER, peak,
                "Just outside its season. Still around, but past or before its best."
            )
            else -> {
                val swaps = substitutes[entry.key]
                val tail = if (swaps.isNullOrEmpty()) "" else " Try ${swaps.joinToString(", ")} instead."
                SeasonAdvice(
                    ingredientName, SeasonStatus.OUT_OF_SEASON, peak,
                    "Out of season -- best ${describe(peak)}.$tail"
                )
            }
        }
    }

    fun adviseAll(names: List<String>, on: LocalDate = LocalDate.now()): List<SeasonAdvice> =
        names.map { advise(it, on) }.filter { it.status != SeasonStatus.UNKNOWN }

    /** What is at its peak this month, to suggest on the meal-plan screen. */
    fun inSeasonNow(on: LocalDate = LocalDate.now(), limit: Int = 12): List<String> =
        calendar.entries
            .filter { it.value.size < 12 && on.monthValue in it.value }
            .map { it.key }
            .sorted()
            .take(limit)

    private fun describe(months: List<Month>): String {
        if (months.isEmpty()) return "at another time of year"
        val names = months.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3) }
        return if (names.size <= 2) names.joinToString(" and ") else "${names.first()} to ${names.last()}"
    }
}
