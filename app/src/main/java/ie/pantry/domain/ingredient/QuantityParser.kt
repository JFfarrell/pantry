package ie.pantry.domain.ingredient

private const val MAX_INT_DIGITS = 6
private const val MAX_FRAC_DIGITS = 6

private val VULGAR_FRACTIONS: Map<Char, Double> = mapOf(
    '¼' to 0.25,
    '½' to 0.5,
    '¾' to 0.75,
    '⅓' to 1.0 / 3.0,
    '⅔' to 2.0 / 3.0,
    '⅛' to 0.125,
)

/** The result of reading a [ScannedLine]'s leading quantity segment: the [quantity] itself, and
 * [nameStart], the index in [ScannedLine.text] where the name phrase begins. */
internal data class QuantityRead(val quantity: Quantity, val nameStart: Int)

/** Reads the leading quantity segment of a [ScannedLine] into a [Quantity] (R4; FC3). Grammar
 * rows G1-G13 are checked in table order; the first rule that matches wins. */
internal object QuantityParser {

    private val LEAD_WORDS = setOf("a", "an", "some", "few", "several")
    private val VAGUE_MEASURES = setOf(
        "pinch", "pinches", "handful", "handfuls", "knob", "knobs", "dash", "dashes", "splash", "splashes",
    )

    fun read(line: ScannedLine): QuantityRead {
        val text = line.text
        val words = splitWords(text)
        if (words.isEmpty()) return QuantityRead(Quantity.Unquantified, 0)

        val first = words[0].text
        val firstChar = first.firstOrNull()
        val firstIsNumeric = firstChar != null && (firstChar.isDigit() || firstChar in VULGAR_FRACTIONS)
        val firstIsNegative = first.length > 1 && first[0] == '-' && first[1].isDigit()
        val firstIsLeadWord = first in LEAD_WORDS

        // G2: one or more lead words.
        if (firstIsLeadWord) return readLeadWordForm(words, text)

        // G3: '-' directly followed by a digit.
        if (firstIsNegative) return readNegativeForm(words, text)

        // G1: no digit/fraction in the first word, and it is not a lead word.
        if (!firstIsNumeric) return QuantityRead(Quantity.Unquantified, 0)

        val firstNumeric = parseNumericWord(first)
        if (firstNumeric is NumericWordResult.Malformed) {
            return readMalformedForm(words, text)
        }
        firstNumeric as NumericWordResult.Amount

        val gluedUnit = firstNumeric.gluedUnit
        if (gluedUnit != null) {
            // "500g", "400g": the amount and its unit are fused in one word (G8/G9).
            return readGluedAmount(words, firstNumeric.value, gluedUnit, text)
        }

        // Bare amount in its own word. Read it plus any mixed-number continuation
        // ("1 1/2", "1 ½"), then continue with the rules that need a second element.
        var value = firstNumeric.value
        var idx = 1
        val mixedFraction = if (idx < words.size) tryReadBareFractionWord(words[idx].text) else null
        if (mixedFraction != null) {
            value += mixedFraction
            idx += 1
        }

        // G4: range ("2-3", "2 to 3").
        if (idx < words.size && (words[idx].text == "-" || words[idx].text == "to")) {
            val second = readPlainAmountAt(words, idx + 1)
            if (second != null) {
                var next = second.second
                val measureMatch = matchMeasureUnitAt(words, next)
                if (measureMatch != null) {
                    next += measureMatch.wordsConsumed
                } else if (next < words.size && matchCountUnit(words[next].text) != null) {
                    next++
                }
                if (next < words.size && words[next].text == "of") next++
                return QuantityRead(Quantity.Unquantified, wordStart(words, next, text.length))
            }
        }

        // G6: AMOUNT x AMOUNT MEASURE_UNIT.
        if (idx < words.size && words[idx].text == "x") {
            val secondIdx = idx + 1
            if (secondIdx < words.size) {
                val secondWord = words[secondIdx].text
                val c = secondWord.firstOrNull()
                if (c != null && (c.isDigit() || c in VULGAR_FRACTIONS)) {
                    val secondResult = parseNumericWord(secondWord)
                    if (secondResult is NumericWordResult.Amount && secondResult.gluedUnit is MeasureUnit) {
                        var next = secondIdx + 1
                        if (next < words.size && matchCountUnit(words[next].text) != null) next++
                        if (next < words.size && words[next].text == "of") next++
                        val product = value * secondResult.value
                        val nameStart = wordStart(words, next, text.length)
                        return QuantityRead(toQuantity(product, secondResult.gluedUnit), nameStart)
                    }
                }
            }
        }

        // G7: AMOUNT [count unit] ( AMOUNT MEASURE_UNIT [each] ) [count unit]. The inner amount
        // may have its unit glued (e.g. "(400g)") or separate (e.g. "(400 g)").
        run {
            var innerStart = idx
            if (innerStart < words.size && matchCountUnit(words[innerStart].text) != null) innerStart++
            val paren = tryReadParenthetical(words, innerStart)
            if (paren != null && paren.innerWords.isNotEmpty()) {
                val innerFirst = paren.innerWords[0].text
                val innerFirstChar = innerFirst.firstOrNull()
                if (innerFirstChar != null && (innerFirstChar.isDigit() || innerFirstChar in VULGAR_FRACTIONS)) {
                    val innerParsed = parseNumericWord(innerFirst)
                    if (innerParsed is NumericWordResult.Amount) {
                        var innerUnit: MeasureUnit? = null
                        if (innerParsed.gluedUnit is MeasureUnit) {
                            innerUnit = innerParsed.gluedUnit
                        } else if (innerParsed.gluedUnit == null) {
                            val measureMatch = matchMeasureUnitAt(paren.innerWords, 1)
                            if (measureMatch != null) innerUnit = measureMatch.unit
                        }
                        if (innerUnit != null) {
                            var outerNext = paren.closeWordIdx + 1
                            if (outerNext < words.size && matchCountUnit(words[outerNext].text) != null) outerNext++
                            if (outerNext < words.size && words[outerNext].text == "of") outerNext++
                            val product = value * innerParsed.value
                            val nameStart = wordStart(words, outerNext, text.length)
                            return QuantityRead(toQuantity(product, innerUnit), nameStart)
                        }
                    }
                }
            }
        }

        return dispatchAfterAmount(words, idx, value, text)
    }

    // ---- G2, G3, malformed-first-word forms ----

    private fun readLeadWordForm(words: List<Word>, text: String): QuantityRead {
        var idx = 0
        while (idx < words.size && words[idx].text in LEAD_WORDS) idx++
        if (idx < words.size) {
            val stripped = stripDot(words[idx].text)
            if (stripped in VAGUE_MEASURES) {
                idx++
            } else {
                val measureMatch = matchMeasureUnitAt(words, idx)
                if (measureMatch != null) {
                    idx += measureMatch.wordsConsumed
                } else if (matchCountUnit(words[idx].text) != null) {
                    idx++
                }
            }
        }
        if (idx < words.size && words[idx].text == "of") idx++
        return QuantityRead(Quantity.Unquantified, wordStart(words, idx, text.length))
    }

    private fun readNegativeForm(words: List<Word>, text: String): QuantityRead {
        var idx = 1
        val measureMatch = matchMeasureUnitAt(words, idx)
        if (measureMatch != null) {
            idx += measureMatch.wordsConsumed
        } else if (idx < words.size && matchCountUnit(words[idx].text) != null) {
            idx++
        }
        if (idx < words.size && words[idx].text == "of") idx++
        return QuantityRead(Quantity.Unquantified, wordStart(words, idx, text.length))
    }

    private fun readMalformedForm(words: List<Word>, text: String): QuantityRead {
        var idx = 1
        val measureMatch = matchMeasureUnitAt(words, idx)
        if (measureMatch != null) {
            idx += measureMatch.wordsConsumed
        } else if (idx < words.size && matchCountUnit(words[idx].text) != null) {
            idx += 1
        }
        return QuantityRead(Quantity.Unquantified, wordStart(words, idx, text.length))
    }

    private fun readGluedAmount(words: List<Word>, value: Double, gluedUnit: AmountUnit, text: String): QuantityRead {
        var idx = 1
        if (gluedUnit is MeasureUnit && idx < words.size && matchCountUnit(words[idx].text) != null) idx++
        if (idx < words.size && words[idx].text == "of") idx++
        val nameStart = wordStart(words, idx, text.length)
        return QuantityRead(toQuantity(value, gluedUnit), nameStart)
    }

    /** G8 (measure unit), G9 (count unit), G10 (vague measure), G11 (unknown word + "of"), G12
     * (ambiguous second numeric word), or G13 (amount on its own). */
    private fun dispatchAfterAmount(words: List<Word>, startIdx: Int, value: Double, text: String): QuantityRead {
        val idx = startIdx

        // G8: AMOUNT MEASURE_UNIT, with an optional trailing count unit and "of".
        val measureMatch = matchMeasureUnitAt(words, idx)
        if (measureMatch != null) {
            var next = idx + measureMatch.wordsConsumed
            if (next < words.size && matchCountUnit(words[next].text) != null) next++
            if (next < words.size && words[next].text == "of") next++
            val nameStart = wordStart(words, next, text.length)
            return QuantityRead(toQuantity(value, measureMatch.unit), nameStart)
        }

        // G9: AMOUNT COUNT_UNIT, with an optional trailing "of".
        if (idx < words.size) {
            val countUnit = matchCountUnit(words[idx].text)
            if (countUnit != null) {
                var next = idx + 1
                if (next < words.size && words[next].text == "of") next++
                val nameStart = wordStart(words, next, text.length)
                return QuantityRead(toQuantity(value, countUnit), nameStart)
            }
        }

        // G10: AMOUNT VAGUE_MEASURE.
        if (idx < words.size && stripDot(words[idx].text) in VAGUE_MEASURES) {
            var next = idx + 1
            if (next < words.size && words[next].text == "of") next++
            return QuantityRead(Quantity.Unquantified, wordStart(words, next, text.length))
        }

        // G11: AMOUNT WORD "of", where WORD is not a known unit (already ruled out above).
        if (idx + 1 < words.size && words[idx + 1].text == "of") {
            return QuantityRead(Quantity.Unquantified, wordStart(words, idx + 2, text.length))
        }

        // G12: a second numeric word that does not complete a mixed number (already tried).
        if (idx < words.size) {
            val c = words[idx].text.firstOrNull()
            if (c != null && (c.isDigit() || c in VULGAR_FRACTIONS)) {
                var next = idx + 1
                val measureMatch2 = matchMeasureUnitAt(words, next)
                if (measureMatch2 != null) {
                    next += measureMatch2.wordsConsumed
                } else if (next < words.size && matchCountUnit(words[next].text) != null) {
                    next++
                }
                if (next < words.size && words[next].text == "of") next++
                return QuantityRead(Quantity.Unquantified, wordStart(words, next, text.length))
            }
        }

        // G13: AMOUNT on its own.
        val nameStart = wordStart(words, idx, text.length)
        return QuantityRead(toQuantity(value, null), nameStart)
    }

    private fun toQuantity(value: Double, unit: AmountUnit?): Quantity {
        if (!value.isFinite() || value <= 0.0) return Quantity.Unquantified
        return when (unit) {
            is MeasureUnit -> Quantity.Measured(value, unit)
            is CountUnit -> Quantity.Counted(value, unit)
            null -> Quantity.Counted(value, null)
        }
    }

    // ---- word cursor ----

    private data class Word(val text: String, val start: Int)

    private fun splitWords(text: String): List<Word> {
        val words = mutableListOf<Word>()
        var i = 0
        while (i < text.length) {
            while (i < text.length && text[i] == ' ') i++
            if (i >= text.length) break
            val start = i
            while (i < text.length && text[i] != ' ') i++
            words.add(Word(text.substring(start, i), start))
        }
        return words
    }

    private fun wordStart(words: List<Word>, index: Int, textLength: Int): Int =
        if (index < words.size) words[index].start else textLength

    private data class ParenResult(val innerWords: List<Word>, val closeWordIdx: Int)

    /** Reads a `( ... )` span starting at [openIdx], which may spread across several
     * space-separated words (`(400 g)`) or sit inside one glued word (`(400g)`). Returns null
     * if [openIdx] does not start with `(`, or the span never closes. */
    private fun tryReadParenthetical(words: List<Word>, openIdx: Int): ParenResult? {
        if (openIdx >= words.size) return null
        val openWord = words[openIdx].text
        if (!openWord.startsWith("(")) return null

        val inner = mutableListOf<Word>()
        var i = openIdx
        var text = openWord.removePrefix("(")
        var offset = words[openIdx].start + 1
        while (true) {
            if (text.endsWith(")")) {
                val stripped = text.dropLast(1)
                if (stripped.isNotEmpty()) inner.add(Word(stripped, offset))
                return ParenResult(inner, i)
            }
            if (text.isNotEmpty()) inner.add(Word(text, offset))
            i++
            if (i >= words.size) return null
            text = words[i].text
            offset = words[i].start
        }
    }

    // ---- AMOUNT grammar ----

    private sealed interface NumericWordResult {
        data class Amount(val value: Double, val gluedUnit: AmountUnit?) : NumericWordResult
        data object Malformed : NumericWordResult
    }

    /** Parses one raw word that starts with a digit or a vulgar fraction into an AMOUNT, plus
     * whatever unit spelling (if any) is glued directly to it (e.g. "500g", "400g" -> `g`). If
     * the word does not resolve to one of the accepted AMOUNT shapes, or a non-empty glued
     * remainder does not match a real unit spelling, the whole word is malformed. */
    private fun parseNumericWord(word: String): NumericWordResult {
        val n = word.length

        if (word[0] in VULGAR_FRACTIONS) {
            return checkGluedTrailing(VULGAR_FRACTIONS.getValue(word[0]), word, 1)
        }

        var j = 0
        while (j < n && word[j].isDigit()) j++
        val intDigits = word.substring(0, j)
        if (intDigits.isEmpty() || intDigits.length > MAX_INT_DIGITS) return NumericWordResult.Malformed
        val intValue = intDigits.toDouble()

        // DIGITS + vulgar fraction fused, e.g. "1½".
        if (j < n && word[j] in VULGAR_FRACTIONS) {
            return checkGluedTrailing(intValue + VULGAR_FRACTIONS.getValue(word[j]), word, j + 1)
        }

        // DIGITS . DIGITS
        if (j < n && word[j] == '.') {
            var k = j + 1
            while (k < n && word[k].isDigit()) k++
            val fracDigits = word.substring(j + 1, k)
            if (fracDigits.isNotEmpty() && fracDigits.length <= MAX_FRAC_DIGITS) {
                return checkGluedTrailing((intDigits + "." + fracDigits).toDouble(), word, k)
            }
            // A bare trailing '.' with no digits after it (e.g. "500." before a unit) is not
            // part of the number; fall through and treat it as glued trailing text.
        }

        // DIGITS / DIGITS
        if (j < n && word[j] == '/') {
            var k = j + 1
            while (k < n && word[k].isDigit()) k++
            val denDigits = word.substring(j + 1, k)
            if (denDigits.isNotEmpty() && denDigits.length <= MAX_INT_DIGITS) {
                return checkGluedTrailing(intValue / denDigits.toDouble(), word, k)
            }
            return NumericWordResult.Malformed
        }

        return checkGluedTrailing(intValue, word, j)
    }

    private fun checkGluedTrailing(value: Double, word: String, consumedUpTo: Int): NumericWordResult {
        val trailing = word.substring(consumedUpTo)
        if (trailing.isEmpty()) return NumericWordResult.Amount(value, null)
        val stripped = stripDot(trailing)
        val measureUnit = MeasureUnit.entries.firstOrNull { stripped == it.token || stripped in it.spellings }
        if (measureUnit != null) return NumericWordResult.Amount(value, measureUnit)
        val countUnit = CountUnit.entries.firstOrNull { stripped in it.spellings }
        if (countUnit != null) return NumericWordResult.Amount(value, countUnit)
        return NumericWordResult.Malformed
    }

    /** Reads an amount at [idx] that stands alone as a "plain" amount: no glued unit, usable as
     * the second element of a range (G4) or a multiplier (G6 reads its own second amount
     * directly, since that one's glued unit *is* the result unit). Includes mixed-number
     * continuation. Returns null if [idx] is out of range, not numeric, or malformed. */
    private fun readPlainAmountAt(words: List<Word>, idx: Int): Pair<Double, Int>? {
        if (idx >= words.size) return null
        val word = words[idx].text
        val c = word.firstOrNull() ?: return null
        if (!(c.isDigit() || c in VULGAR_FRACTIONS)) return null
        val result = parseNumericWord(word)
        if (result is NumericWordResult.Malformed) return null
        result as NumericWordResult.Amount
        if (result.gluedUnit != null) return null
        var value = result.value
        var next = idx + 1
        if (next < words.size) {
            val frac = tryReadBareFractionWord(words[next].text)
            if (frac != null) {
                value += frac
                next += 1
            }
        }
        return value to next
    }

    /** Reads a fraction that is the *entire* word (a bare vulgar fraction, or a complete
     * `DIGITS/DIGITS`) for the "DIGITS space fraction" mixed-number form. Returns null for
     * anything else, including a plain integer (that is G12's concern). */
    private fun tryReadBareFractionWord(word: String): Double? {
        if (word.length == 1 && word[0] in VULGAR_FRACTIONS) return VULGAR_FRACTIONS.getValue(word[0])
        val slash = word.indexOf('/')
        if (slash > 0) {
            val numText = word.substring(0, slash)
            val denText = word.substring(slash + 1)
            if (numText.isNotEmpty() && numText.all { it.isDigit() } && numText.length <= MAX_INT_DIGITS &&
                denText.isNotEmpty() && denText.all { it.isDigit() } && denText.length <= MAX_INT_DIGITS
            ) {
                return numText.toDouble() / denText.toDouble()
            }
        }
        return null
    }

    // ---- unit matching ----

    private data class MeasureMatch(val unit: MeasureUnit, val wordsConsumed: Int)

    /** Multi-word spellings (`fl oz`, `fluid ounce`) are checked before single-word ones. */
    private fun matchMeasureUnitAt(words: List<Word>, idx: Int): MeasureMatch? {
        if (idx >= words.size) return null
        if (idx + 1 < words.size) {
            val two = stripDot(words[idx].text) + " " + stripDot(words[idx + 1].text)
            val unit = MeasureUnit.entries.firstOrNull { two in it.spellings }
            if (unit != null) return MeasureMatch(unit, 2)
        }
        val one = stripDot(words[idx].text)
        val unit = MeasureUnit.entries.firstOrNull { one in it.spellings }
        return if (unit != null) MeasureMatch(unit, 1) else null
    }

    private fun matchCountUnit(word: String): CountUnit? {
        val stripped = stripDot(word)
        return CountUnit.entries.firstOrNull { stripped in it.spellings }
    }

    private fun stripDot(word: String): String = if (word.endsWith(".")) word.dropLast(1) else word
}
