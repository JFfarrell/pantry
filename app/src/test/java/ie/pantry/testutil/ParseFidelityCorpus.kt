package ie.pantry.testutil

import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.domain.ingredient.CanonicalKey
import ie.pantry.domain.ingredient.QuantityColumns
import java.time.LocalDate

/** Which route supplied a corpus entry (R8; CFC-1). */
enum class CorpusOrigin { HAND_TRANSCRIBED, F5_HARVESTED }

/** Whether a corpus line is a real ingredient line or a non-ingredient line such as a section
 * header (spec Q10). */
enum class LineKind { INGREDIENT, NON_INGREDIENT }

/** One row of the shared parse-fidelity corpus (DM8). */
data class CorpusEntry(
    val origin: CorpusOrigin,
    val sourceUrl: String,
    val retrieved: LocalDate,
    val lineKind: LineKind,
    val line: String,
    val expectedKey: CanonicalKey,
    val expectedColumns: QuantityColumns,
    val row: Int,
)

/** Loads the shared parse-fidelity corpus (R8; CFC-1 Enforcement) — the fixture F3, F6 and F11
 * all drive their own routes with to prove byte-identical results. Plain Kotlin, no JSON library,
 * and no dependency on any `ie.pantry.domain.ingredient` test class (R8 AC3). */
object ParseFidelityCorpus {
    const val DEFAULT_RESOURCE = "ingredient/parse_fidelity_corpus.tsv"

    /** Reads [resource] from the test classpath, in file order.
     * @throws IllegalStateException naming only the offending row number. */
    fun load(resource: String = DEFAULT_RESOURCE): List<CorpusEntry> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/$resource")) {
            "parse_fidelity_corpus: resource not found"
        }
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines()

        val entries = mutableListOf<CorpusEntry>()
        var headerSeen = false
        var row = 0
        for (rawLine in lines) {
            if (rawLine.startsWith("#")) continue
            if (!headerSeen) {
                headerSeen = true
                continue
            }
            row++
            entries.add(parseRow(rawLine, row))
        }
        return entries
    }

    private fun parseRow(rawLine: String, row: Int): CorpusEntry {
        try {
            val fields = rawLine.split("\t")
            check(fields.size == 9)

            val origin = CorpusOrigin.valueOf(fields[0])
            val sourceUrl = fields[1]
            val retrieved = LocalDate.parse(fields[2])
            val lineKind = LineKind.valueOf(fields[3])
            val line = fields[4]
            val key = if (fields[5] == "<absent>") CanonicalKey.Absent else CanonicalKey.Derived(fields[5])
            val dimension = QuantityDimension.valueOf(fields[6])
            val isUnquantified = dimension == QuantityDimension.UNQUANTIFIED
            val amountField = fields[7]
            val unitField = fields[8]
            check((amountField == "-") == isUnquantified)
            check((unitField == "-") == isUnquantified)
            val amount = if (amountField == "-") null else parseAmount(amountField)
            val unit = if (unitField == "-") null else unitField

            return CorpusEntry(origin, sourceUrl, retrieved, lineKind, line, key, QuantityColumns(amount, unit, dimension), row)
        } catch (_: Exception) {
            throw IllegalStateException("parse_fidelity_corpus: malformed row $row")
        }
    }

    private fun parseAmount(raw: String): Double {
        val slash = raw.indexOf('/')
        return if (slash >= 0) {
            raw.substring(0, slash).toDouble() / raw.substring(slash + 1).toDouble()
        } else {
            raw.toDouble()
        }
    }
}
