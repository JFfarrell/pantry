package ie.pantry.domain.ingredient

import ie.pantry.data.reference.AliasTable
import ie.pantry.data.reference.Lookup
import java.text.Normalizer
import java.util.Locale

/** The input-length cap (AD7, spec RK7). A longer line is treated as unreadable without being
 * scanned. */
internal const val MAX_LINE_CHARS = 1_000

private val UNREADABLE = ParsedLine(CanonicalKey.Absent, Quantity.Unquantified)

/** Package-internal capability gate (AD4). [ScannedLine] and its only implementer are visible
 * only inside this module's `ie.pantry.domain.ingredient` package, so [QuantityParser] and
 * [CanonicalKeyRule] — which take a [ScannedLine], never a raw [String] — can never be reached
 * with unscanned text from outside [IngredientEngine.parse]. */
internal sealed interface ScannedLine {
    val text: String
}

private class Scanned(override val text: String) : ScannedLine

/** Normalises [line] per AD8 steps N1-N6. Returns null only for a line over [MAX_LINE_CHARS];
 * that null never leaves this file. */
private fun scan(line: String): ScannedLine? {
    // N1: length cap.
    if (line.length > MAX_LINE_CHARS) return null

    // N2: NFD-normalise, then drop combining marks (diacritic folding: e -> e).
    val nfd = Normalizer.normalize(line, Normalizer.Form.NFD)
    val foldedDiacritics = buildString {
        for (c in nfd) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) append(c)
        }
    }

    // N3: lowercase, root locale.
    val lowered = foldedDiacritics.lowercase(Locale.ROOT)

    // N4: map each character. Letters, digits, `, ( ) / .` and vulgar fractions are kept;
    // `-`, en dash and em dash become `-`; `x` (multiplication sign) becomes ` x `; apostrophes
    // are removed; everything else becomes a space.
    val mapped = buildString {
        for (c in lowered) {
            when {
                c.isLetterOrDigit() -> append(c)
                c == ',' || c == '(' || c == ')' || c == '/' || c == '.' -> append(c)
                c == '¼' || c == '½' || c == '¾' ||
                    c == '⅓' || c == '⅔' || c == '⅛' -> append(c)
                c == '-' || c == '–' || c == '—' -> append('-')
                c == '×' -> append(" x ")
                c == '\'' || c == '’' -> Unit
                else -> append(' ')
            }
        }
    }

    // N5: a `-` with a letter on both sides becomes a space; any other `-` is kept (ranges and
    // signs, handled by the quantity parser).
    val hyphenFolded = buildString {
        for (i in mapped.indices) {
            val c = mapped[i]
            if (c == '-') {
                val prev = mapped.getOrNull(i - 1)
                val next = mapped.getOrNull(i + 1)
                if (prev != null && prev.isLetter() && next != null && next.isLetter()) {
                    append(' ')
                } else {
                    append(c)
                }
            } else {
                append(c)
            }
        }
    }

    // N6: collapse runs of whitespace and trim, with a manual character pass (AD7: no pattern-matching library).
    val trimmed = hyphenFolded.trim()
    val collapsed = buildString {
        var lastWasSpace = false
        for (c in trimmed) {
            if (c == ' ') {
                if (!lastWasSpace) append(' ')
                lastWasSpace = true
            } else {
                append(c)
                lastWasSpace = false
            }
        }
    }

    return Scanned(collapsed)
}

/** Parses one free-text ingredient line into a canonical key and a quantity (R1; CFC-1). The
 * key rule and quantity parser can be reached only through this function — no other
 * non-private declaration in this package accepts raw line text (R1 AC1; AD4). */
class IngredientEngine internal constructor(private val aliasLookup: (String) -> Lookup<String>) {

    /** Engines receive tables only (F2 AD2): the caller unwraps `ReferenceDataStore.aliases()`. */
    constructor(aliases: AliasTable) : this(aliases::lookup)

    /**
     * Parses one free-text ingredient line into a canonical key and a quantity.
     *
     * @param line raw line text, from any route (import confirm, manual entry, post-save edit)
     * @return a [ParsedLine]; `CanonicalKey.Absent` when no name text remains, and
     *   `Quantity.Unquantified` when no leading amount is confidently read
     */
    fun parse(line: String): ParsedLine {
        return try {
            val scanned = scan(line) ?: return UNREADABLE
            val read = QuantityParser.read(scanned)
            val key = CanonicalKeyRule.resolve(scanned, read.nameStart, aliasLookup)
            ParsedLine(key, read.quantity)
        } catch (_: Exception) {
            UNREADABLE
        }
    }
}
