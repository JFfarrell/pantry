package com.pantry.app.nutrition

import android.content.Context
import com.pantry.app.domain.IngredientParser
import com.pantry.app.domain.Macros
import com.pantry.app.domain.NutritionFacts
import com.pantry.app.domain.NutritionSource
import com.pantry.app.domain.TextMatch

/**
 * The bundled per-100g table from assets/staples.csv. It exists because Open
 * Food Facts is a database of *packaged products* and is thin on raw
 * ingredients -- "2 carrots" needs a composition table, not a barcode.
 */
class StaplesTable(private val context: Context) {

    private val table: Map<String, Macros> by lazy { load() }

    private fun load(): Map<String, Macros> = runCatching {
        context.assets.open("staples.csv").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
                val parts = line.split(",")
                if (parts.size < 8) return@mapNotNull null
                val key = IngredientParser.normaliseName(parts[0])
                val macros = Macros(
                    kcal = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
                    proteinG = parts[2].toDoubleOrNull() ?: 0.0,
                    carbsG = parts[3].toDoubleOrNull() ?: 0.0,
                    fatG = parts[4].toDoubleOrNull() ?: 0.0,
                    fibreG = parts[5].toDoubleOrNull() ?: 0.0,
                    sugarG = parts[6].toDoubleOrNull() ?: 0.0,
                    saltG = parts[7].toDoubleOrNull() ?: 0.0
                )
                key to macros
            }.toMap()
        }
    }.getOrElse { emptyMap() }

    /**
     * Exact key first, then the longest table entry contained in the name, so
     * "free-range chicken breast fillets" resolves to "chicken breast" rather
     * than the shorter "chicken".
     */
    fun lookup(matchKey: String): NutritionFacts? {
        val best = TextMatch.bestMatch(table, matchKey)
            ?: table.entries.firstOrNull { TextMatch.containsWord(it.key, matchKey) }
            ?: return null
        return NutritionFacts(best.value, best.key, NutritionSource.BUNDLED_TABLE)
    }
}
