package ie.pantry.recipeimport

import kotlinx.coroutines.Job
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** A JSON-LD block longer than this many characters is skipped before it is scanned or parsed. */
internal const val JSON_LD_BLOCK_CAP_CHARS: Int = 512 * 1024

/** The deepest container nesting a JSON-LD block may have. The outermost value is depth 1. */
internal const val JSON_LD_DEPTH_BOUND: Int = 64

/** A string-aware container-depth pre-scan: linear, allocation-free and never recursive. */
internal object JsonDepth {

    /**
     * True when no `{` or `[` outside a string nests deeper than [bound], with the outermost value at depth 1.
     * It stops at the first character that would reach `bound + 1`.
     */
    fun withinBound(text: String, bound: Int = JSON_LD_DEPTH_BOUND): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    if (depth > bound) return false
                }
                '}', ']' -> if (depth > 0) depth--
            }
        }
        return true
    }
}

/** A mapped `Recipe` node and the raw image string found beside it, held after its block's tree is dropped. */
internal class JsonLdRecipe(val recipe: ExtractedRecipe, val image: String?) {
    override fun toString(): String = "JsonLdRecipe(recipe=$recipe, hasImage=${image != null})"
}

/** The `Recipe` nodes of a page's JSON-LD blocks, in walk order. */
internal class JsonLdScan(val recipes: List<JsonLdRecipe>) {
    override fun toString(): String = "JsonLdScan(recipes=${recipes.size})"
}

internal object JsonLdExtractor {

    /** At most this many `application/ld+json` blocks are examined, valid or not. */
    const val MAX_BLOCKS: Int = 20

    /** At most this many elements of any array are examined. */
    const val MAX_ARRAY_ELEMENTS: Int = 1000

    private const val LD_JSON = "application/ld+json"

    /**
     * Scans the first [MAX_BLOCKS] JSON-LD blocks in document order. A block that is too long, too deep or not
     * valid JSON is skipped and still counted. Each block's tree is dropped as soon as its recipes are mapped.
     */
    fun recipes(document: Document, job: Job = Job()): JsonLdScan {
        val blocks = ArrayList<String>(MAX_BLOCKS)
        BoundedDomWalk.walk(document, job, enter = { node, _ ->
            if (blocks.size < MAX_BLOCKS && node is Element && node.nameIs("script") &&
                node.attr("type").trim().equals(LD_JSON, ignoreCase = true)
            ) {
                blocks += node.data()
            }
            true
        })
        val found = ArrayList<JsonLdRecipe>()
        for (text in blocks) {
            if (text.length > JSON_LD_BLOCK_CAP_CHARS || !JsonDepth.withinBound(text)) continue
            val root = try {
                Json.parseToJsonElement(text)
            } catch (e: SerializationException) {
                continue
            } catch (e: IllegalArgumentException) {
                continue
            }
            val nodes = recipeNodes(root)
            if (nodes.isEmpty()) continue
            val index = idIndex(root)
            for (node in nodes) found += JsonLdRecipe(toRecipe(node), imageString(node["image"], index))
        }
        return JsonLdScan(found)
    }

    /** The `Recipe` objects of a parsed tree in walk order, through arrays and `@graph`, never past depth 64. */
    fun recipeNodes(root: JsonElement): List<JsonObject> {
        val found = ArrayList<JsonObject>()
        collect(root, 1, found)
        return found
    }

    private fun collect(element: JsonElement, depth: Int, found: MutableList<JsonObject>) {
        if (depth > JSON_LD_DEPTH_BOUND) return
        when (element) {
            is JsonArray -> for (child in element.take(MAX_ARRAY_ELEMENTS)) collect(child, depth + 1, found)
            is JsonObject -> if (isRecipe(element)) {
                found += element
            } else {
                element["@graph"]?.let { collect(it, depth + 1, found) }
            }
            else -> Unit
        }
    }

    private fun isRecipe(node: JsonObject): Boolean = when (val type = node["@type"]) {
        is JsonPrimitive -> type.isString && type.content == "Recipe"
        is JsonArray -> type.any { it is JsonPrimitive && it.isString && it.content == "Recipe" }
        else -> false
    }

    /** At most this many ingredient lines, and this many steps, are kept per recipe. */
    const val MAX_OUTPUT_ITEMS: Int = 200

    /** Maps one `Recipe` node. */
    fun toRecipe(node: JsonObject): ExtractedRecipe =
        ExtractedRecipe(
            title = text(node["name"])?.let { DraftValue.Stated(it) } ?: DraftValue.Absent,
            lines = lines(node["recipeIngredient"] ?: node["ingredients"]),
            steps = steps(node["recipeInstructions"]),
            cookingTime = RecipeValues.cookingMinutes(string(node["totalTime"]), string(node["cookTime"])),
            servings = RecipeValues.servings(node["recipeYield"]),
        )

    private fun string(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * One title, line or step: dropped when its raw string is longer than [LineText.MAX_RAW_CHARS]; passed through
     * `parseBodyFragment` only when it holds `&` or `<`; then normalised. Null when nothing is left.
     */
    private fun text(element: JsonElement?): String? = text(string(element))

    private fun text(raw: String?): String? {
        if (raw == null || raw.length > LineText.MAX_RAW_CHARS) return null
        val plain = if (raw.contains('&') || raw.contains('<')) {
            LineText.of(Jsoup.parseBodyFragment(raw).body()) ?: return null
        } else {
            LineText.normalise(raw)
        }
        return plain.ifEmpty { null }
    }

    private fun lines(element: JsonElement?): List<String> {
        val items = when (element) {
            is JsonArray -> element.take(MAX_ARRAY_ELEMENTS)
            is JsonPrimitive -> listOf(element)
            else -> return emptyList()
        }
        val lines = ArrayList<String>()
        for (item in items) {
            if (lines.size >= MAX_OUTPUT_ITEMS) break
            text(item)?.let(lines::add)
        }
        return lines
    }

    private fun steps(element: JsonElement?): List<String> {
        val steps = ArrayList<String>()
        collectSteps(element, 1, steps)
        return steps
    }

    private fun collectSteps(element: JsonElement?, depth: Int, steps: MutableList<String>) {
        if (steps.size >= MAX_OUTPUT_ITEMS || depth > JSON_LD_DEPTH_BOUND) return
        when (element) {
            is JsonPrimitive -> {
                if (!element.isString) return
                for (piece in element.content.split('\n', '\r')) {
                    if (steps.size >= MAX_OUTPUT_ITEMS) return
                    text(piece)?.let(steps::add)
                }
            }
            is JsonArray -> for (child in element.take(MAX_ARRAY_ELEMENTS)) collectSteps(child, depth + 1, steps)
            is JsonObject -> {
                val types = typesOf(element)
                when {
                    "HowToTip" in types -> Unit
                    "HowToSection" in types || "ItemList" in types || element.containsKey("itemListElement") ->
                        collectSteps(element["itemListElement"], depth + 1, steps)
                    else -> text(element["text"] ?: element["name"])?.let(steps::add)
                }
            }
            else -> Unit
        }
    }

    private fun typesOf(node: JsonObject): Set<String> = when (val type = node["@type"]) {
        is JsonPrimitive -> if (type.isString) setOf(type.content) else emptySet()
        is JsonArray -> type.mapNotNull { string(it) }.toSet()
        else -> emptySet()
    }

    /**
     * The raw image string of a `Recipe` node: a string, the first array element, an `ImageObject`'s `url` else
     * `contentUrl`, or an `{"@id": ...}` reference resolved one hop in [index]. A blank value is no image.
     */
    private fun imageString(image: JsonElement?, index: Map<String, JsonObject>): String? {
        val element = if (image is JsonArray) image.firstOrNull() else image
        val direct = when (element) {
            is JsonPrimitive -> return string(element)?.takeIf { it.isNotBlank() }
            is JsonObject -> urlOf(element)
            else -> return null
        }
        if (direct != null) return direct
        val id = string((element as JsonObject)["@id"]) ?: return null
        val target = index[id]?.takeIf { it !== element } ?: return null
        return urlOf(target)
    }

    private fun urlOf(node: JsonObject): String? =
        (string(node["url"]) ?: string(node["contentUrl"]))?.takeIf { it.isNotBlank() }

    /** Every object in the tree with a string `@id`, first one winning; the tree is at most 64 deep. */
    private fun idIndex(root: JsonElement): Map<String, JsonObject> {
        val index = HashMap<String, JsonObject>()
        fun visit(element: JsonElement, depth: Int) {
            if (depth > JSON_LD_DEPTH_BOUND) return
            when (element) {
                is JsonArray -> element.take(MAX_ARRAY_ELEMENTS).forEach { visit(it, depth + 1) }
                is JsonObject -> {
                    string(element["@id"])?.let { index.putIfAbsent(it, element) }
                    element.values.forEach { visit(it, depth + 1) }
                }
                else -> Unit
            }
        }
        visit(root, 1)
        return index
    }
}
