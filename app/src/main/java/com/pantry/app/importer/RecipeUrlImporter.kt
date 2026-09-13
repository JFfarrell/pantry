package com.pantry.app.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Feature 2: pull a recipe out of a web page.
 *
 * Strategy order, best first:
 *   1. JSON-LD schema.org/Recipe -- what nearly every recipe site emits for Google.
 *   2. Microdata (itemtype=schema.org/Recipe) -- older sites.
 *   3. Microformats h-recipe / heuristic list scraping -- last resort, marked Partial.
 */
class RecipeUrlImporter(
    private val client: OkHttpClient = defaultClient()
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun import(url: String): ImportResult = withContext(Dispatchers.IO) {
        val normalised = normaliseUrl(url) ?: return@withContext ImportResult.Failure("That does not look like a web address.")

        val html = try {
            fetch(normalised)
        } catch (e: Exception) {
            return@withContext ImportResult.Failure("Could not load the page: ${e.message ?: "network error"}")
        }

        val doc = Jsoup.parse(html, normalised)
        val site = siteName(doc, normalised)

        fromJsonLd(doc, normalised, site)?.let { return@withContext ImportResult.Success(it) }
        fromMicrodata(doc, normalised, site)?.let { return@withContext ImportResult.Success(it) }

        val guess = fromHeuristics(doc, normalised, site)
        return@withContext if (guess.looksComplete) {
            ImportResult.Partial(guess, "No structured recipe data on this page, so the ingredients were guessed from the page layout. Worth a quick check.")
        } else {
            ImportResult.Partial(
                guess,
                "This page has no machine-readable recipe. The title and text were kept so you can fill in the rest by hand."
            )
        }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            // Some publishers serve a stub to unknown agents; a normal browser UA gets the real page.
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-GB,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    // ---------- strategy 1: JSON-LD ----------

    private fun fromJsonLd(doc: Document, url: String, site: String?): ImportedRecipe? {
        for (script in doc.select("script[type='application/ld+json']")) {
            val payload = script.data().trim()
            if (payload.isEmpty()) continue
            val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: continue
            val recipeNode = findRecipeNode(root) ?: continue
            val parsed = parseSchemaRecipe(recipeNode, url, site, "JSON-LD")
            if (parsed.looksComplete) return parsed
        }
        return null
    }

    /** Recipe objects hide inside arrays, @graph lists, and nested mainEntity fields. */
    private fun findRecipeNode(element: JsonElement, depth: Int = 0): JsonObject? {
        if (depth > 6) return null
        when (element) {
            is JsonArray -> element.forEach { child -> findRecipeNode(child, depth + 1)?.let { return it } }
            is JsonObject -> {
                if (hasType(element, "Recipe")) return element
                listOf("@graph", "mainEntity", "mainEntityOfPage", "itemListElement").forEach { key ->
                    element[key]?.let { child -> findRecipeNode(child, depth + 1)?.let { return it } }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun hasType(obj: JsonObject, type: String): Boolean {
        val node = obj["@type"] ?: return false
        return stringsOf(node).any { it.equals(type, ignoreCase = true) }
    }

    private fun parseSchemaRecipe(node: JsonObject, url: String, site: String?, strategy: String): ImportedRecipe {
        val ingredients = (stringsOf(node["recipeIngredient"]) + stringsOf(node["ingredients"]))
            .map { it.cleanWhitespace() }
            .filter { it.isNotBlank() }
            .distinct()

        return ImportedRecipe(
            title = firstString(node["name"])?.cleanWhitespace().orEmpty(),
            description = firstString(node["description"])?.cleanWhitespace(),
            imageUrl = imageFrom(node["image"]),
            sourceUrl = url,
            sourceName = site,
            servings = servingsFrom(node["recipeYield"]),
            prepMinutes = Iso8601Duration.toMinutes(firstString(node["prepTime"])),
            cookMinutes = Iso8601Duration.toMinutes(firstString(node["cookTime"])),
            totalMinutes = Iso8601Duration.toMinutes(firstString(node["totalTime"])),
            ingredientLines = ingredients,
            steps = instructionsFrom(node["recipeInstructions"]),
            tags = (stringsOf(node["recipeCategory"]) + stringsOf(node["keywords"]).flatMap { it.split(",") })
                .map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8),
            cuisine = firstString(node["recipeCuisine"]),
            strategy = strategy
        )
    }

    private fun instructionsFrom(node: JsonElement?): List<String> {
        if (node == null) return emptyList()
        val out = mutableListOf<String>()

        fun walk(el: JsonElement, depth: Int = 0) {
            if (depth > 5) return
            when (el) {
                is JsonArray -> el.forEach { walk(it, depth + 1) }
                is JsonObject -> when {
                    // HowToSection groups steps under itemListElement.
                    el["itemListElement"] != null -> walk(el["itemListElement"]!!, depth + 1)
                    else -> firstString(el["text"] ?: el["name"])?.let { out += it }
                }
                is JsonPrimitive -> if (el.isString) out += el.content
                else -> Unit
            }
        }
        walk(node)

        return out
            .map { Jsoup.parse(it).text().cleanWhitespace() }
            .filter { it.isNotBlank() }
            // A single blob of prose is more useful split into sentences-per-step.
            .let { steps -> if (steps.size == 1 && steps[0].length > 400) splitProse(steps[0]) else steps }
    }

    private fun splitProse(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+(?=[A-Z0-9])"))
            .map { it.trim() }
            .filter { it.length > 3 }

    private fun imageFrom(node: JsonElement?): String? {
        if (node == null) return null
        return when (node) {
            is JsonPrimitive -> node.contentOrNullSafe()
            is JsonArray -> node.firstNotNullOfOrNull { imageFrom(it) }
            is JsonObject -> firstString(node["url"]) ?: firstString(node["contentUrl"])
            else -> null
        }
    }

    private fun servingsFrom(node: JsonElement?): Int? {
        val text = firstString(node) ?: return null
        return Regex("\\d+").find(text)?.value?.toIntOrNull()?.takeIf { it in 1..50 }
    }

    // ---------- strategy 2: microdata ----------

    private fun fromMicrodata(doc: Document, url: String, site: String?): ImportedRecipe? {
        val scope = doc.selectFirst("[itemtype~=(?i)schema.org/Recipe]") ?: return null

        fun prop(name: String) = scope.select("[itemprop=$name]")
        fun propText(name: String): String? = prop(name).firstOrNull()?.let {
            (it.attr("content").ifBlank { it.attr("datetime") }.ifBlank { it.text() }).cleanWhitespace()
        }?.ifBlank { null }

        val ingredients = prop("recipeIngredient").ifEmpty { prop("ingredients") }
            .map { it.text().cleanWhitespace() }.filter { it.isNotBlank() }

        val recipe = ImportedRecipe(
            title = propText("name") ?: doc.title().cleanWhitespace(),
            description = propText("description"),
            imageUrl = prop("image").firstOrNull()?.absUrl("src")?.ifBlank { null }
                ?: prop("image").firstOrNull()?.attr("content")?.ifBlank { null },
            sourceUrl = url,
            sourceName = site,
            servings = servingsFromText(propText("recipeYield")),
            prepMinutes = Iso8601Duration.toMinutes(propText("prepTime")),
            cookMinutes = Iso8601Duration.toMinutes(propText("cookTime")),
            totalMinutes = Iso8601Duration.toMinutes(propText("totalTime")),
            ingredientLines = ingredients,
            steps = prop("recipeInstructions").flatMap { el ->
                val items = el.select("li")
                if (items.isNotEmpty()) items.map { it.text() } else listOf(el.text())
            }.map { it.cleanWhitespace() }.filter { it.isNotBlank() },
            cuisine = propText("recipeCuisine"),
            strategy = "Microdata"
        )
        return recipe.takeIf { it.looksComplete }
    }

    private fun servingsFromText(text: String?): Int? =
        text?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }?.takeIf { it in 1..50 }

    // ---------- strategy 3: heuristics ----------

    private fun fromHeuristics(doc: Document, url: String, site: String?): ImportedRecipe {
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.ifBlank { null }
            ?: doc.selectFirst("h1")?.text()
            ?: doc.title()

        // Microformats first, then any list whose items read like ingredient lines.
        val hRecipe = doc.select(".p-ingredient, .ingredient, [class*=ingredient] li, li[class*=ingredient]")
            .map { it.text().cleanWhitespace() }
            .filter { it.isNotBlank() && it.length < 200 }
            .distinct()

        val guessed = hRecipe.ifEmpty {
            doc.select("ul").maxByOrNull { list ->
                list.select("li").count { li -> looksLikeIngredient(li.text()) }
            }?.select("li")?.map { it.text().cleanWhitespace() }
                ?.filter { looksLikeIngredient(it) }
                .orEmpty()
        }

        val steps = doc.select("[class*=instruction] li, [class*=method] li, [class*=direction] li, ol li")
            .map { it.text().cleanWhitespace() }
            .filter { it.length in 15..2000 }
            .distinct()
            .take(40)

        return ImportedRecipe(
            title = title.cleanWhitespace(),
            description = doc.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null },
            imageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null },
            sourceUrl = url,
            sourceName = site,
            ingredientLines = guessed,
            steps = steps,
            strategy = "Page layout (guessed)"
        )
    }

    private fun looksLikeIngredient(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 3..160) return false
        val startsWithAmount = Regex("^\\d|^[0-9\\u00BC-\\u00BE\\u2150-\\u215E]").containsMatchIn(t)
        val hasUnit = Regex("\\b(g|kg|ml|l|tsp|tbsp|cup|cups|oz|lb|clove|cloves|pinch|handful)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(t)
        return startsWithAmount || hasUnit
    }

    // ---------- shared helpers ----------

    private fun stringsOf(node: JsonElement?): List<String> = when (node) {
        null -> emptyList()
        is JsonPrimitive -> listOfNotNull(node.contentOrNullSafe())
        is JsonArray -> node.flatMap { stringsOf(it) }
        is JsonObject -> listOfNotNull(firstString(node["name"]) ?: firstString(node["text"]))
        else -> emptyList()
    }

    private fun firstString(node: JsonElement?): String? = stringsOf(node).firstOrNull()

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this.content == "null") null else this.content.ifBlank { null }

    private fun String.cleanWhitespace(): String =
        replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

    private fun siteName(doc: Document, url: String): String? =
        doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.ifBlank { null }
            ?: runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()

    private fun normaliseUrl(input: String): String? {
        // Shared text is often "Look at this https://... " rather than a bare URL.
        val candidate = Regex("https?://\\S+").find(input)?.value
            ?: input.trim().takeIf { it.isNotBlank() && !it.contains(' ') }?.let { "https://$it" }
            ?: return null
        return runCatching {
            val uri = URI(candidate.trimEnd('.', ',', ')'))
            if (uri.host.isNullOrBlank()) null else uri.toString()
        }.getOrNull()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
