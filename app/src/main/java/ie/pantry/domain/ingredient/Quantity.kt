package ie.pantry.domain.ingredient

import ie.pantry.data.db.entity.QuantityDimension

private const val BLANK_KEY_MESSAGE = "CanonicalKey.Derived requires a non-blank value"
private const val NON_POSITIVE_AMOUNT_MESSAGE = "quantity amount must be finite and greater than zero"

/** The canonical-key result of parsing a line's name phrase (R2). */
sealed interface CanonicalKey {
    /** A key the rule or alias table produced, in F2 surface form (lowercase, trimmed,
     * single-spaced). */
    data class Derived(val value: String) : CanonicalKey {
        init {
            require(value.isNotBlank()) { BLANK_KEY_MESSAGE }
        }
    }

    /** No name text remained after quantity and preparation words were removed (R2 AC5;
     * CFC-2). Carries no string field, so it cannot be confused with a key of `""`. */
    data object Absent : CanonicalKey
}

/** The quantity result of parsing a line's leading amount (R4). */
sealed interface Quantity {
    val dimension: QuantityDimension

    /** A mass or volume amount, in the unit the line stated. */
    data class Measured(val amount: Double, val unit: MeasureUnit) : Quantity {
        override val dimension: QuantityDimension get() = unit.dimension

        init {
            require(amount.isFinite() && amount > 0.0) { NON_POSITIVE_AMOUNT_MESSAGE }
        }
    }

    /** A count amount. [countUnit] is null only for a bare count (`3 eggs`). */
    data class Counted(val amount: Double, val countUnit: CountUnit?) : Quantity {
        override val dimension: QuantityDimension get() = QuantityDimension.COUNT

        init {
            require(amount.isFinite() && amount > 0.0) { NON_POSITIVE_AMOUNT_MESSAGE }
        }
    }

    /** No leading amount was confidently recognised (R4 AC5; CFC-2). Carries no numeric
     * property or unit, so a number cannot be put in it. */
    data object Unquantified : Quantity {
        override val dimension: QuantityDimension get() = QuantityDimension.UNQUANTIFIED
    }
}

/** One parsed line: its canonical key and its quantity (R1). */
data class ParsedLine(val key: CanonicalKey, val quantity: Quantity)

/** The typed outcome of [Quantity.convertTo] (R5). */
sealed interface Conversion {
    data class Converted(val quantity: Quantity) : Conversion
    data object NotConvertible : Conversion
}

/** The typed outcome of [Quantity.scaledBy] (R6). */
sealed interface Scaling {
    data class Scaled(val quantity: Quantity) : Scaling
    data object InvalidRatio : Scaling
}

/** The typed outcome of [ParsedLine.mergeWith] (R7). */
sealed interface MergeResult {
    data class Merged(val line: ParsedLine) : MergeResult
    data object NotMergeable : MergeResult
}

/** The value image of a [Quantity] that F6 persists through F1's `RecipeIngredient` columns
 * (R4 AC6; AD11). Null iff [dimension] is `UNQUANTIFIED`. */
data class QuantityColumns(val amount: Double?, val unit: String?, val dimension: QuantityDimension)

/** The typed read-back of a stored [QuantityColumns] triple (AD11). */
sealed interface ColumnDecode {
    data class Decoded(val quantity: Quantity) : ColumnDecode
    data object Inconsistent : ColumnDecode
}

/** Maps a quantity to its column triple. A bare count uses [BARE_COUNT_TOKEN]. */
fun Quantity.toColumns(): QuantityColumns = when (this) {
    is Quantity.Measured -> QuantityColumns(amount, unit.token, dimension)
    is Quantity.Counted -> QuantityColumns(amount, countUnit?.token ?: BARE_COUNT_TOKEN, dimension)
    Quantity.Unquantified -> QuantityColumns(null, null, dimension)
}

/** Reads a stored column triple back. Returns [ColumnDecode.Inconsistent] for any triple the
 * engine could not have produced: an unknown token, a token whose dimension does not match, a
 * null or non-positive amount on a measured dimension, or a non-null amount or unit on
 * `UNQUANTIFIED`. */
fun QuantityColumns.decode(): ColumnDecode {
    if (dimension == QuantityDimension.UNQUANTIFIED) {
        return if (amount == null && unit == null) {
            ColumnDecode.Decoded(Quantity.Unquantified)
        } else {
            ColumnDecode.Inconsistent
        }
    }
    if (amount == null || !amount.isFinite() || amount <= 0.0 || unit == null) {
        return ColumnDecode.Inconsistent
    }
    return when (dimension) {
        QuantityDimension.MASS, QuantityDimension.VOLUME -> {
            val measureUnit = MeasureUnit.entries.find { it.token == unit }
                ?: return ColumnDecode.Inconsistent
            if (measureUnit.dimension != dimension) return ColumnDecode.Inconsistent
            ColumnDecode.Decoded(Quantity.Measured(amount, measureUnit))
        }
        QuantityDimension.COUNT -> {
            if (unit == BARE_COUNT_TOKEN) {
                ColumnDecode.Decoded(Quantity.Counted(amount, null))
            } else {
                val countUnit = CountUnit.entries.find { it.token == unit }
                    ?: return ColumnDecode.Inconsistent
                ColumnDecode.Decoded(Quantity.Counted(amount, countUnit))
            }
        }
        QuantityDimension.UNQUANTIFIED -> ColumnDecode.Inconsistent
    }
}
