package com.pantry.app.domain

/** The three physically distinct ways a recipe can quantify an ingredient. */
enum class MeasureKind { MASS, VOLUME, COUNT }

/**
 * A unit of measure. [toBase] converts into the kind's base unit:
 * grams for MASS, millilitres for VOLUME, and "one of them" for COUNT.
 */
enum class MeasureUnit(
    val label: String,
    val kind: MeasureKind,
    val toBase: Double,
    val aliases: List<String>
) {
    GRAM("g", MeasureKind.MASS, 1.0, listOf("g", "gr", "gram", "grams", "gramme", "grammes")),
    KILOGRAM("kg", MeasureKind.MASS, 1000.0, listOf("kg", "kilo", "kilos", "kilogram", "kilograms")),
    OUNCE("oz", MeasureKind.MASS, 28.3495, listOf("oz", "ounce", "ounces")),
    POUND("lb", MeasureKind.MASS, 453.592, listOf("lb", "lbs", "pound", "pounds")),

    MILLILITRE("ml", MeasureKind.VOLUME, 1.0, listOf("ml", "mls", "millilitre", "millilitres", "milliliter", "milliliters", "cc")),
    LITRE("l", MeasureKind.VOLUME, 1000.0, listOf("l", "lt", "litre", "litres", "liter", "liters")),
    TEASPOON("tsp", MeasureKind.VOLUME, 4.92892, listOf("tsp", "tsps", "teaspoon", "teaspoons")),
    TABLESPOON("tbsp", MeasureKind.VOLUME, 14.7868, listOf("tbsp", "tbsps", "tbs", "tblsp", "tablespoon", "tablespoons")),
    CUP("cup", MeasureKind.VOLUME, 236.588, listOf("cup", "cups")),
    FLUID_OUNCE("fl oz", MeasureKind.VOLUME, 28.4131, listOf("fl oz", "floz", "fl.oz", "fluid ounce", "fluid ounces")),
    PINT("pint", MeasureKind.VOLUME, 568.261, listOf("pt", "pint", "pints")),

    PIECE("", MeasureKind.COUNT, 1.0, listOf("piece", "pieces", "whole")),
    CLOVE("clove", MeasureKind.COUNT, 1.0, listOf("clove", "cloves")),
    SLICE("slice", MeasureKind.COUNT, 1.0, listOf("slice", "slices", "rasher", "rashers")),
    CAN("can", MeasureKind.COUNT, 1.0, listOf("can", "cans", "tin", "tins")),
    PACK("pack", MeasureKind.COUNT, 1.0, listOf("pack", "packs", "packet", "packets", "punnet", "punnets")),
    BUNCH("bunch", MeasureKind.COUNT, 1.0, listOf("bunch", "bunches", "sprig", "sprigs", "stick", "sticks", "stalk", "stalks")),
    HANDFUL("handful", MeasureKind.COUNT, 1.0, listOf("handful", "handfuls")),
    PINCH("pinch", MeasureKind.COUNT, 1.0, listOf("pinch", "pinches", "dash", "dashes"));

    companion object {
        private val byAlias: Map<String, MeasureUnit> = buildMap {
            entries.forEach { unit -> unit.aliases.forEach { put(it, unit) } }
        }

        /** Longest alias first, so "fl oz" beats "oz" when scanning a line. */
        val aliasesLongestFirst: List<String> = byAlias.keys.sortedByDescending { it.length }

        fun fromAlias(token: String): MeasureUnit? = byAlias[token.trim().lowercase().removeSuffix(".")]
    }
}

/** A quantity of something, or a bare amount when the recipe gave no unit. */
data class Quantity(val amount: Double, val unit: MeasureUnit) {
    val kind: MeasureKind get() = unit.kind
    val base: Double get() = amount * unit.toBase

    /** Same-kind quantities add; COUNT only adds when the unit word matches. */
    fun canCombineWith(other: Quantity): Boolean =
        if (kind == MeasureKind.COUNT) unit == other.unit else kind == other.kind

    operator fun plus(other: Quantity): Quantity {
        require(canCombineWith(other)) { "Cannot add $other to $this" }
        return Quantity((base + other.base) / unit.toBase, unit)
    }

    /** Re-expresses in the friendliest unit of the same kind, e.g. 1500 g -> 1.5 kg. */
    fun humanised(): Quantity = when {
        unit.kind == MeasureKind.MASS && base >= 1000 -> Quantity(base / 1000.0, MeasureUnit.KILOGRAM)
        unit.kind == MeasureKind.MASS && unit != MeasureUnit.GRAM -> Quantity(base, MeasureUnit.GRAM)
        unit.kind == MeasureKind.VOLUME && base >= 1000 -> Quantity(base / 1000.0, MeasureUnit.LITRE)
        // Spoons stay spoons while the number is still spoon-sized; once a
        // merged total runs past a cupful, millilitres read better.
        unit.kind == MeasureKind.VOLUME &&
            (base >= 250 || unit !in setOf(MeasureUnit.MILLILITRE, MeasureUnit.TEASPOON, MeasureUnit.TABLESPOON)) ->
            Quantity(base, MeasureUnit.MILLILITRE)
        else -> this
    }

    override fun toString(): String {
        val h = humanised()
        val n = formatAmount(h.amount)
        return if (h.unit.label.isEmpty()) n else "$n ${h.unit.label}"
    }

    companion object {
        fun formatAmount(value: Double): String {
            val rounded = when {
                value >= 100 -> Math.round(value).toDouble()
                value >= 10 -> Math.round(value * 10) / 10.0
                else -> Math.round(value * 100) / 100.0
            }
            return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        }
    }
}

fun Double.scaled(factor: Double) = this * factor
