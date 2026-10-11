package ie.pantry.recipeimport

import kotlinx.coroutines.Job
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Microdata, then RDFa, as ARCHITECTURE C1's one `MICRODATA` tier. */
internal object MicrodataExtractor {

    private val WHITESPACE = Regex("\\s+")

    /** The first microdata `Recipe`, else the first RDFa `Recipe`, mapped; null when the page has neither. */
    fun recipe(document: Document, job: Job = Job()): ExtractedRecipe? {
        findMicrodataRoot(document, job)?.let { return read(it, job, scopeAttribute = "itemscope", propertyAttribute = "itemprop") }
        findRdfaRoot(document, job)?.let { return read(it, job, scopeAttribute = "typeof", propertyAttribute = "property") }
        return null
    }

    private fun tokens(value: String): List<String> = value.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    private fun findMicrodataRoot(document: Document, job: Job): Element? {
        var found: Element? = null
        BoundedDomWalk.walk(document, job, enter = { node, _ ->
            if (found == null && node is Element && node.hasAttr("itemscope") &&
                tokens(node.attr("itemtype")).any(::isSchemaRecipe)
            ) {
                found = node
            }
            found == null
        })
        return found
    }

    private fun isSchemaRecipe(token: String): Boolean {
        val lower = token.lowercase()
        return (lower.startsWith("http://") || lower.startsWith("https://")) && lower.endsWith("schema.org/recipe")
    }

    /** The first element whose `typeof` is `Recipe` under a `vocab` naming `schema.org`, or is `schema:Recipe`. */
    private fun findRdfaRoot(document: Document, job: Job): Element? {
        var found: Element? = null
        val vocabs = ArrayList<Pair<Element, String>>()
        BoundedDomWalk.walk(
            document,
            job,
            enter = { node, _ ->
                if (found != null || node !is Element) return@walk found == null
                if (node.hasAttr("vocab")) vocabs += node to node.attr("vocab")
                val types = tokens(node.attr("typeof"))
                val inSchemaVocab = vocabs.lastOrNull()?.second?.contains("schema.org", ignoreCase = true) == true
                if (types.any { it == "schema:Recipe" || (it == "Recipe" && inSchemaVocab) }) found = node
                found == null
            },
            leave = { node, _ ->
                if (vocabs.lastOrNull()?.first === node) vocabs.removeAt(vocabs.size - 1)
            },
        )
        return found
    }

    private fun read(root: Element, job: Job, scopeAttribute: String, propertyAttribute: String): ExtractedRecipe {
        val owned = ArrayList<Pair<String, Element>>()
        val scopes = ArrayList<Element>()
        BoundedDomWalk.walk(
            root,
            job,
            enter = { node, _ ->
                if (node is Element) {
                    if (node === root) {
                        scopes += node
                    } else {
                        if (scopes.lastOrNull() === root) {
                            for (name in tokens(node.attr(propertyAttribute))) owned += name.removePrefix("schema:") to node
                        }
                        if (node.hasAttr(scopeAttribute)) scopes += node
                    }
                }
                true
            },
            leave = { node, _ ->
                if (scopes.lastOrNull() === node) scopes.removeAt(scopes.size - 1)
            },
        )
        fun elements(name: String): List<Element> = owned.filter { it.first == name }.map { it.second }
        fun first(name: String): String? = elements(name).firstNotNullOfOrNull(::valueOf)

        val ingredients = elements("recipeIngredient").ifEmpty { elements("ingredients") }
        return ExtractedRecipe(
            title = first("name")?.let { DraftValue.Stated(it) } ?: DraftValue.Absent,
            lines = ingredients.mapNotNull(::valueOf).take(JsonLdExtractor.MAX_OUTPUT_ITEMS),
            steps = elements("recipeInstructions").flatMap(::stepsOf).take(JsonLdExtractor.MAX_OUTPUT_ITEMS),
            cookingTime = RecipeValues.cookingMinutes(first("totalTime"), first("cookTime")),
            servings = RecipeValues.servings(first("recipeYield")),
        )
    }

    /** `meta` gives `content`, `time` gives `datetime` else its text, `link` and `a` give `href`, others their text. */
    private fun valueOf(element: Element): String? {
        val raw = when {
            element.nameIs("meta") -> element.attr("content")
            element.nameIs("time") -> if (element.hasAttr("datetime")) element.attr("datetime") else LineText.of(element)
            element.nameIs("link") || element.nameIs("a") -> element.attr("href")
            else -> LineText.of(element)
        } ?: return null
        if (raw.length > LineText.MAX_RAW_CHARS) return null
        return LineText.normalise(raw).ifEmpty { null }
    }

    /** Each `li`, else each `p`, else the whole text as one step. */
    private fun stepsOf(element: Element): List<String> {
        val items = element.getElementsByTag("li").ifEmpty { element.getElementsByTag("p") }
        if (items.isEmpty()) return listOfNotNull(valueOf(element))
        return items.mapNotNull { LineText.of(it)?.ifEmpty { null } }
    }
}
