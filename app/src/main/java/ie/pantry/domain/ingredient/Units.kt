package ie.pantry.domain.ingredient

import ie.pantry.data.db.entity.QuantityDimension

/** A unit of amount: either a [MeasureUnit] (MASS/VOLUME) or a [CountUnit]. Each carries a
 * stable, unique column [token] (R4 AC6). */
sealed interface AmountUnit {
    val token: String
}

/** The bare-count column token, used when a [ie.pantry.domain.ingredient.Quantity.Counted] has
 * no [CountUnit] (a plain "3 eggs"). */
const val BARE_COUNT_TOKEN = "each"

/** Recognised mass and volume units and their conversion factor to their dimension's base unit
 * (`G` for MASS, `ML` for VOLUME) — DM4, spec Q4. */
enum class MeasureUnit(
    override val token: String,
    val dimension: QuantityDimension,
    val factorToBase: Double,
    internal val spellings: List<String>,
) : AmountUnit {
    G("g", QuantityDimension.MASS, 1.0, listOf("g", "gram", "grams")),
    KG("kg", QuantityDimension.MASS, 1000.0, listOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms")),
    MG("mg", QuantityDimension.MASS, 0.001, listOf("mg", "milligram", "milligrams")),
    OZ("oz", QuantityDimension.MASS, 28.349523125, listOf("oz", "ounce", "ounces")),
    LB("lb", QuantityDimension.MASS, 453.59237, listOf("lb", "lbs", "pound", "pounds")),
    ML("ml", QuantityDimension.VOLUME, 1.0, listOf("ml", "mls", "millilitre", "millilitres", "milliliter", "milliliters")),
    L("l", QuantityDimension.VOLUME, 1000.0, listOf("l", "litre", "litres", "liter", "liters")),
    CL("cl", QuantityDimension.VOLUME, 10.0, listOf("cl", "centilitre", "centilitres", "centiliter", "centiliters")),
    TSP("tsp", QuantityDimension.VOLUME, 5.0, listOf("tsp", "tsps", "teaspoon", "teaspoons")),
    TBSP("tbsp", QuantityDimension.VOLUME, 15.0, listOf("tbsp", "tbsps", "tablespoon", "tablespoons")),
    CUP("cup", QuantityDimension.VOLUME, 250.0, listOf("cup", "cups")),
    FL_OZ("fl_oz", QuantityDimension.VOLUME, 28.4130625, listOf("fl oz", "fluid ounce", "fluid ounces")),
    PINT("pint", QuantityDimension.VOLUME, 568.26125, listOf("pint", "pints")),
    ;
}

/** Recognised count-unit nouns (DM5, spec Q4). A bare count (no noun) uses [BARE_COUNT_TOKEN]
 * instead of a [CountUnit]. */
enum class CountUnit(
    override val token: String,
    internal val spellings: List<String>,
) : AmountUnit {
    CLOVE("clove", listOf("clove", "cloves")),
    TIN("tin", listOf("tin", "tins")),
    CAN("can", listOf("can", "cans")),
    BUNCH("bunch", listOf("bunch", "bunches")),
    BULB("bulb", listOf("bulb", "bulbs")),
    SLICE("slice", listOf("slice", "slices")),
    SPRIG("sprig", listOf("sprig", "sprigs")),
    STICK("stick", listOf("stick", "sticks")),
    SHEET("sheet", listOf("sheet", "sheets")),
    PACKET("packet", listOf("packet", "packets")),
    ;
}
