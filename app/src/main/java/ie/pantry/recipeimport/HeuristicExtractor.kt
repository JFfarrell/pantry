package ie.pantry.recipeimport

import kotlinx.coroutines.Job
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** The last-resort tier: ingredient and method lists found by their headings. It never states a time or a yield. */
internal object HeuristicExtractor {

    private const val MIN_ITEMS = 2
    private val METHOD_WORDS = Regex("\\b(method|directions|instructions|steps)\\b", RegexOption.IGNORE_CASE)
    private val HEADING_LEVEL = Regex("^h([1-6])$")

    /** A heuristic recipe, or null when no ingredient heading is followed by a list of at least two items. */
    fun recipe(document: Document, job: Job = Job()): ExtractedRecipe? {
        val headings = ArrayList<Element>()
        BoundedDomWalk.walk(document, job, enter = { node, _ ->
            if (node is Element && level(node) != null) headings += node
            true
        })
        val lines = headings.filter { text(it).contains("ingredient", ignoreCase = true) }
            .firstNotNullOfOrNull { itemsAfter(it).takeIf { items -> items.size >= MIN_ITEMS } } ?: return null
        val steps = headings.filter { METHOD_WORDS.containsMatchIn(text(it)) }
            .firstNotNullOfOrNull { itemsAfter(it).takeIf { items -> items.size >= MIN_ITEMS } }.orEmpty()
        return ExtractedRecipe(
            title = title(document, headings)?.let { DraftValue.Stated(it) } ?: DraftValue.Absent,
            lines = lines.take(JsonLdExtractor.MAX_OUTPUT_ITEMS),
            steps = steps.take(JsonLdExtractor.MAX_OUTPUT_ITEMS),
            cookingTime = DraftValue.Absent,
            servings = DraftValue.Absent,
        )
    }

    private fun level(element: Element): Int? = HEADING_LEVEL.matchEntire(element.normalName())?.groupValues?.get(1)?.toInt()

    private fun text(element: Element): String = LineText.of(element).orEmpty()

    /**
     * The items under [heading]: the direct `li` children of the first following `ul` or `ol` (or of the first list
     * inside a following sibling), else one item per sibling of the consecutive `p`, `div` and `li` run. The search
     * stops at the next heading of the same or a higher level.
     */
    private fun itemsAfter(heading: Element): List<String> {
        val headingLevel = level(heading) ?: return emptyList()
        val siblings = ArrayList<Element>()
        var next = heading.nextElementSibling()
        while (next != null) {
            val nextLevel = level(next)
            if (nextLevel != null && nextLevel <= headingLevel) break
            siblings += next
            next = next.nextElementSibling()
        }
        for (sibling in siblings) {
            val list = if (sibling.nameIs("ul") || sibling.nameIs("ol")) sibling else sibling.selectFirst("ul, ol")
            if (list != null) return list.children().filter { it.nameIs("li") }.mapNotNull { LineText.of(it)?.ifEmpty { null } }
        }
        val run = siblings.takeWhile { it.nameIs("p") || it.nameIs("div") || it.nameIs("li") }
        return run.mapNotNull { LineText.of(it)?.ifEmpty { null } }
    }

    private fun title(document: Document, headings: List<Element>): String? {
        headings.firstOrNull { level(it) == 1 }?.let { heading -> text(heading).ifEmpty { null }?.let { return it } }
        val og = document.select("meta[property~=(?i)^og:title$]").firstOrNull()?.attr("content")
        og?.let(LineText::normalise)?.ifEmpty { null }?.let { return it }
        return document.head().getElementsByTag("title").firstOrNull()?.let(::text)?.ifEmpty { null }
    }
}
