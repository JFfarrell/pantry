package ie.pantry.domain.ingredient

import ie.pantry.data.db.entity.QuantityDimension

private fun baseUnitFor(dimension: QuantityDimension): MeasureUnit? =
    MeasureUnit.entries.firstOrNull { it.dimension == dimension && it.factorToBase == 1.0 }

/** Within-dimension conversion (R5). A [Quantity.Measured] value converts to a [MeasureUnit] of
 * the same dimension; a [Quantity.Counted] value converts only to its own, equal [CountUnit].
 * Every other combination — across dimensions, across count units, [Quantity.Unquantified], or
 * a non-finite result — gives [Conversion.NotConvertible]. Never throws. */
fun Quantity.convertTo(target: AmountUnit): Conversion {
    return when (this) {
        is Quantity.Measured -> {
            if (target !is MeasureUnit || target.dimension != unit.dimension) return Conversion.NotConvertible
            val converted = amount * unit.factorToBase / target.factorToBase
            if (!converted.isFinite() || converted <= 0.0) return Conversion.NotConvertible
            Conversion.Converted(Quantity.Measured(converted, target))
        }
        is Quantity.Counted -> {
            if (target !is CountUnit || target != countUnit) return Conversion.NotConvertible
            Conversion.Converted(this)
        }
        Quantity.Unquantified -> Conversion.NotConvertible
    }
}

/** Multiplies by [ratio] (R6). Returns [Scaling.InvalidRatio] when the ratio is not finite or
 * not greater than zero, or when the product is not finite or not greater than zero.
 * [Quantity.Unquantified] scales to itself. No rounding is applied. Never throws. */
fun Quantity.scaledBy(ratio: Double): Scaling {
    if (!ratio.isFinite() || ratio <= 0.0) return Scaling.InvalidRatio
    return when (this) {
        is Quantity.Measured -> {
            val scaled = amount * ratio
            if (!scaled.isFinite() || scaled <= 0.0) return Scaling.InvalidRatio
            Scaling.Scaled(Quantity.Measured(scaled, unit))
        }
        is Quantity.Counted -> {
            val scaled = amount * ratio
            if (!scaled.isFinite() || scaled <= 0.0) return Scaling.InvalidRatio
            Scaling.Scaled(Quantity.Counted(scaled, countUnit))
        }
        Quantity.Unquantified -> Scaling.Scaled(Quantity.Unquantified)
    }
}

/** Merges two lines with the same [CanonicalKey.Derived] key and comparable quantities (R7;
 * AD3). Returns [MergeResult.NotMergeable] when either key is absent, the keys differ, or the
 * quantities are not comparable (different dimension, different count unit, or one measured and
 * one unquantified). Same-unit measured quantities keep their unit; mixed-unit measured
 * quantities are added in their dimension's base unit. Symmetric. Never throws. */
fun ParsedLine.mergeWith(other: ParsedLine): MergeResult {
    val sharedKey = key
    if (sharedKey !is CanonicalKey.Derived || other.key !is CanonicalKey.Derived || sharedKey != other.key) {
        return MergeResult.NotMergeable
    }

    val a = quantity
    val b = other.quantity
    val mergedQuantity: Quantity = when {
        a is Quantity.Unquantified && b is Quantity.Unquantified -> Quantity.Unquantified

        a is Quantity.Measured && b is Quantity.Measured && a.dimension == b.dimension -> {
            val sum: Double
            val resultUnit: MeasureUnit
            if (a.unit == b.unit) {
                sum = a.amount + b.amount
                resultUnit = a.unit
            } else {
                val base = baseUnitFor(a.dimension) ?: return MergeResult.NotMergeable
                sum = (a.amount * a.unit.factorToBase) + (b.amount * b.unit.factorToBase)
                resultUnit = base
            }
            if (!sum.isFinite() || sum <= 0.0) return MergeResult.NotMergeable
            Quantity.Measured(sum, resultUnit)
        }

        a is Quantity.Counted && b is Quantity.Counted && a.countUnit == b.countUnit -> {
            val sum = a.amount + b.amount
            if (!sum.isFinite() || sum <= 0.0) return MergeResult.NotMergeable
            Quantity.Counted(sum, a.countUnit)
        }

        else -> return MergeResult.NotMergeable
    }

    return MergeResult.Merged(ParsedLine(sharedKey, mergedQuantity))
}

/** R7's predicate; true iff [mergeWith] returns [MergeResult.Merged]. Defined in terms of
 * [mergeWith] so it can never disagree with the merge. */
fun ParsedLine.canMergeWith(other: ParsedLine): Boolean = mergeWith(other) is MergeResult.Merged
