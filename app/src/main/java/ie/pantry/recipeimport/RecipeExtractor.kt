package ie.pantry.recipeimport

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.jsoup.nodes.Document

/** One recipe as a tier found it, before the draft is assembled. Never leaves `ie.pantry.recipeimport`. */
internal class ExtractedRecipe(
    val title: DraftValue<String>,
    val lines: List<String>,
    val steps: List<String>,
    val cookingTime: DraftValue<Int>,
    val servings: DraftValue<Int>,
) {
    /** Counts only, so a title or a line never reaches a log through this method. */
    override fun toString(): String =
        "ExtractedRecipe(titleStated=${title is DraftValue.Stated}, lines=${lines.size}, steps=${steps.size})"
}

/** What the winning tier found, with the page's JSON-LD scan (which the image selector reads). */
internal class Extraction(val tier: ExtractionTier, val recipe: ExtractedRecipe, val jsonLd: JsonLdScan) {
    override fun toString(): String = "Extraction(tier=$tier, recipe=$recipe, jsonLd=$jsonLd)"
}

/** Tier order and the qualify rule (AD3). The first tier with a qualifying recipe wins outright. */
internal object RecipeExtractor {

    /**
     * JSON-LD, then microdata and RDFa, then the heuristic tier. A structured recipe qualifies with at least one
     * ingredient line or step; a heuristic recipe qualifies with an ingredient list (two or more items). The one
     * cross-tier field is the title: the earliest structured recipe that stated one supplies it to a lower tier.
     * Returns null when nothing qualifies. Checks [job] between tiers.
     */
    fun extract(document: Document, job: Job): Extraction? {
        job.ensureActive()
        val scan = JsonLdExtractor.recipes(document, job)
        scan.recipes.firstOrNull { qualifies(it.recipe) }?.let { return Extraction(ExtractionTier.JSON_LD, it.recipe, scan) }
        var carried: DraftValue<String> = scan.recipes.map { it.recipe.title }.firstOrNull { it is DraftValue.Stated } ?: DraftValue.Absent

        job.ensureActive()
        val microdata = MicrodataExtractor.recipe(document, job)
        if (microdata != null && qualifies(microdata)) {
            return Extraction(ExtractionTier.MICRODATA, microdata.withTitle(carried), scan)
        }
        if (carried is DraftValue.Absent && microdata != null) carried = microdata.title

        job.ensureActive()
        val heuristic = HeuristicExtractor.recipe(document, job) ?: return null
        val title = if (carried is DraftValue.Stated) carried else heuristic.title
        return Extraction(ExtractionTier.HEURISTIC, heuristic.withTitle(title), scan)
    }

    private fun qualifies(recipe: ExtractedRecipe): Boolean = recipe.lines.isNotEmpty() || recipe.steps.isNotEmpty()

    /** The recipe with [carried] as its title when that is stated, else its own title. */
    private fun ExtractedRecipe.withTitle(carried: DraftValue<String>): ExtractedRecipe =
        if (carried is DraftValue.Stated) ExtractedRecipe(carried, lines, steps, cookingTime, servings) else this
}
