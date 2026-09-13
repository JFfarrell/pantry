package com.pantry.app.nutrition

import com.pantry.app.domain.Macros
import com.pantry.app.domain.NutritionFacts
import com.pantry.app.domain.NutritionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Open Food Facts search. No API key and no sign-up, but their terms ask for an
 * identifying User-Agent, which is set below.
 */
class OpenFoodFactsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(term: String): NutritionFacts? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/cgi/search.pl".toHttpUrl().newBuilder()
            .addQueryParameter("search_terms", term)
            .addQueryParameter("search_simple", "1")
            .addQueryParameter("action", "process")
            .addQueryParameter("json", "1")
            .addQueryParameter("page_size", "10")
            .addQueryParameter("fields", "product_name,nutriments,nutrition_grades,countries_tags")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Pantry/1.0 (Android; recipe macro lookup)")
            .build()

        val body = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        val products = runCatching {
            json.parseToJsonElement(body).jsonObject["products"] as? JsonArray
        }.getOrNull() ?: return@withContext null

        products.asSequence()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { toFacts(it, term) }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Returns the facts plus a match score, so the best of ten results wins. */
    private fun toFacts(product: JsonObject, term: String): Pair<NutritionFacts, Int>? {
        val name = (product["product_name"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (name.isBlank()) return null
        val nutriments = product["nutriments"] as? JsonObject ?: return null

        val kcal = nutriments.number("energy-kcal_100g")
            ?: nutriments.number("energy_100g")?.let { it / 4.184 } // kJ fallback
            ?: return null
        if (kcal <= 0.0 || kcal > 950.0) return null // implausible per-100g energy

        val macros = Macros(
            kcal = kcal,
            proteinG = nutriments.number("proteins_100g") ?: 0.0,
            carbsG = nutriments.number("carbohydrates_100g") ?: 0.0,
            fatG = nutriments.number("fat_100g") ?: 0.0,
            fibreG = nutriments.number("fiber_100g") ?: 0.0,
            sugarG = nutriments.number("sugars_100g") ?: 0.0,
            saltG = nutriments.number("salt_100g") ?: 0.0
        )

        val termWords = term.lowercase().split(" ").filter { it.length > 2 }.toSet()
        val nameWords = name.lowercase().split(Regex("[^a-z]+")).toSet()
        var score = termWords.count { it in nameWords } * 10
        if (name.length < 40) score += 3                                  // prefer plain names over marketing copy
        if (nameWords.size <= termWords.size + 2) score += 3              // prefer close matches
        if (macros.proteinG + macros.carbsG + macros.fatG > 0) score += 5 // complete records
        val ukOrUnknown = (product["countries_tags"] as? JsonArray)?.any {
            (it as? JsonPrimitive)?.content?.contains("united-kingdom") == true
        } ?: false
        if (ukOrUnknown) score += 2

        return NutritionFacts(macros, name, NutritionSource.OPEN_FOOD_FACTS) to score
    }

    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
}
