package com.pantry.app.domain

/**
 * One ingredient line, broken into the parts the shopping list and the macro
 * calculator need. [raw] is always kept so the recipe screen can show the
 * original wording even when parsing was imperfect.
 */
data class ParsedIngredient(
    val raw: String,
    val quantity: Quantity?,
    val name: String,
    val note: String? = null,
    val optional: Boolean = false
) {
    /** Lower-cased, de-pluralised key used to merge lines across recipes. */
    val matchKey: String get() = IngredientParser.normaliseName(name)
}

/**
 * Turns free-text recipe lines ("2 1/2 tbsp olive oil, plus extra for drizzling")
 * into structured quantities. Deliberately forgiving: anything it cannot read
 * falls through as a name-only ingredient rather than being dropped.
 */
object IngredientParser {

    private val unicodeFractions = mapOf(
        '½' to 0.5, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 0.25,
        '¾' to 0.75, '⅕' to 0.2, '⅙' to 1.0 / 6, '⅛' to 0.125,
        '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875
    )

    private val optionalMarkers = listOf("optional", "to taste", "to serve", "if you like")

    private val descriptors = setOf(
        "fresh", "freshly", "large", "medium", "small", "ripe", "free-range", "free", "range",
        "organic", "finely", "roughly", "coarsely", "thinly", "chopped", "sliced", "diced",
        "minced", "grated", "crushed", "peeled", "trimmed", "washed", "drained", "rinsed",
        "cooked", "raw", "good", "quality", "extra", "plus", "approx", "about", "level",
        "heaped", "heaping", "packed", "plain", "vine", "of"
    )

    private val pluralExceptions = mapOf(
        "tomatoes" to "tomato", "potatoes" to "potato", "leaves" to "leaf",
        "loaves" to "loaf", "knives" to "knife", "chillies" to "chilli",
        "berries" to "berry", "cherries" to "cherry", "anchovies" to "anchovy"
    )

    fun parse(line: String): ParsedIngredient {
        val raw = line.trim().removePrefix("•").removePrefix("-").trim()
        if (raw.isEmpty()) return ParsedIngredient(line, null, line.trim())

        var work = raw
        val lower = raw.lowercase()
        val optional = optionalMarkers.any { lower.contains(it) }

        // Pull a trailing preparation note off the end: "onion, finely chopped".
        var note: String? = null
        val commaIdx = work.indexOf(',')
        if (commaIdx > 0) {
            note = work.substring(commaIdx + 1).trim().ifEmpty { null }
            work = work.substring(0, commaIdx)
        }
        // Parenthesised asides get folded into the note too: "1 tin (400g) tomatoes".
        val paren = Regex("\\(([^)]*)\\)").find(work)
        if (paren != null) {
            note = listOfNotNull(paren.groupValues[1].trim().ifEmpty { null }, note)
                .joinToString("; ").ifEmpty { null }
            work = work.removeRange(paren.range).replace("  ", " ").trim()
        }

        val (amount, afterAmount) = readAmount(work)
        val (unit, afterUnit) = readUnit(afterAmount)

        var name = afterUnit.trim().trim('.', ',', ';').trim()
        if (name.startsWith("of ", ignoreCase = true)) name = name.substring(3).trim()
        if (name.isEmpty()) name = afterAmount.trim().ifEmpty { raw }

        // "2 garlic cloves" puts the unit after the ingredient rather than before it.
        var resolvedUnit = unit
        if (amount != null && resolvedUnit == null) {
            trailingCountUnit(name)?.let { (u, shortened) ->
                resolvedUnit = u
                name = shortened
            }
        }

        val quantity = when {
            amount == null -> null
            resolvedUnit != null -> Quantity(amount, resolvedUnit!!)
            // "3 eggs" — a bare number is a count of the thing itself.
            else -> Quantity(amount, MeasureUnit.PIECE)
        }

        return ParsedIngredient(raw = raw, quantity = quantity, name = name, note = note, optional = optional)
    }


    /**
     * Splits a trailing count word off a name: "garlic cloves" becomes CLOVE plus
     * "garlic". Only count units qualify; a trailing mass or volume word is far
     * more likely to be part of the ingredient's actual name.
     */
    private fun trailingCountUnit(name: String): Pair<MeasureUnit, String>? {
        val words = name.trim().split(Regex("\\s+"))
        if (words.size < 2) return null
        val unit = MeasureUnit.fromAlias(words.last()) ?: return null
        if (unit.kind != MeasureKind.COUNT || unit == MeasureUnit.PIECE) return null
        return unit to words.dropLast(1).joinToString(" ")
    }

    /** Reads a leading amount, handling "1", "1.5", "1/2", "1 1/2", "1½", "2-3". */
    private fun readAmount(input: String): Pair<Double?, String> {
        var s = input.trimStart()
        if (s.isEmpty()) return null to input

        var total = 0.0
        var consumed = 0
        var found = false

        // A range ("2-3 onions") is averaged up to the larger side, which is the
        // safer error to make when you are shopping.
        val range = Regex("^(\\d+(?:\\.\\d+)?)\\s*(?:-|–|to)\\s*(\\d+(?:\\.\\d+)?)").find(s)
        if (range != null) {
            return range.groupValues[2].toDouble() to s.substring(range.value.length)
        }

        while (consumed < s.length) {
            val rest = s.substring(consumed).trimStart()
            val skipped = s.length - consumed - rest.length

            val frac = Regex("^(\\d+)\\s*/\\s*(\\d+)").find(rest)
            val whole = Regex("^\\d+(?:\\.\\d+)?").find(rest)
            val uni = rest.firstOrNull()?.let { unicodeFractions[it] }

            when {
                frac != null -> {
                    total += frac.groupValues[1].toDouble() / frac.groupValues[2].toDouble()
                    consumed += skipped + frac.value.length; found = true
                }
                whole != null -> {
                    if (found) break // "2 onions 3" — stop, the number belongs elsewhere
                    total += whole.value.toDouble()
                    consumed += skipped + whole.value.length; found = true
                }
                uni != null -> {
                    total += uni
                    consumed += skipped + 1; found = true
                }
                else -> break
            }
        }
        return if (found) total to s.substring(consumed) else null to input
    }

    private fun readUnit(input: String): Pair<MeasureUnit?, String> {
        val s = input.trimStart()
        val lower = s.lowercase()
        for (alias in MeasureUnit.aliasesLongestFirst) {
            if (!lower.startsWith(alias)) continue
            val after = s.substring(alias.length)
            // Must be a whole word: "gram" should not match inside "granola".
            if (after.isNotEmpty() && (after[0].isLetterOrDigit() || after[0] == '/')) continue
            return MeasureUnit.fromAlias(alias) to after.trimStart('.', ' ')
        }
        return null to input
    }

    /** Collapses wording differences so the same ingredient merges across recipes. */
    fun normaliseName(name: String): String {
        val words = name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in descriptors }
            .map { singularise(it) }
        return words.joinToString(" ").trim().ifEmpty { name.lowercase().trim() }
    }

    private fun singularise(word: String): String {
        pluralExceptions[word]?.let { return it }
        return when {
            word.length > 3 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 3 && word.endsWith("ses") -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") -> word.dropLast(1)
            else -> word
        }
    }
}
