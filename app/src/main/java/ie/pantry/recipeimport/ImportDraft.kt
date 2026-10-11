package ie.pantry.recipeimport

import java.time.Instant

/** A draft field that is either stated by the page or explicitly absent. Never `null`. */
sealed interface DraftValue<out T> {
    /** Prints no value, so a title or a step never reaches a log through this method. */
    class Stated<out T>(val value: T) : DraftValue<T> {
        override fun toString(): String = "Stated"
    }

    data object Absent : DraftValue<Nothing>
}

enum class NoImageCause { NO_CANDIDATE, NOT_HTTP, BAD_URL, FETCH_FAILED, UNREADABLE, NOT_AN_IMAGE }

sealed interface DraftImage {
    /** The original fetched (decompressed) bytes, with the dimensions read from the image bounds. */
    class Validated(val bytes: ByteArray, val widthPx: Int, val heightPx: Int) : DraftImage {
        override fun toString(): String = "Validated(size=${bytes.size}, widthPx=$widthPx, heightPx=$heightPx)"
    }

    data class NoImage(val cause: NoImageCause) : DraftImage
}

/** Which tier supplied the draft. [MICRODATA] covers microdata and RDFa. */
enum class ExtractionTier { JSON_LD, MICRODATA, HEURISTIC }

/** Whether the draft found a title, ingredients and a method. */
sealed interface Completeness {
    data object Complete : Completeness

    data class Partial(val foundTitle: Boolean, val foundIngredients: Boolean, val foundMethod: Boolean) : Completeness
}

/**
 * The in-memory result of an import, which the recipe form takes as its initial state. Every optional field is a
 * [DraftValue] or an empty list, so an absence is a value the compiler makes callers handle.
 */
class ImportDraft(
    val title: DraftValue<String>,
    val ingredientLines: List<String>,
    val method: DraftValue<List<String>>,
    val cookingTime: DraftValue<Int>,
    val servings: DraftValue<Int>,
    val image: DraftImage,
    val tier: ExtractionTier,
    val sourceUrl: String,
    val fetchedAt: Instant,
) {
    val completeness: Completeness
        get() {
            val foundTitle = title is DraftValue.Stated
            val foundIngredients = ingredientLines.isNotEmpty()
            val foundMethod = method is DraftValue.Stated
            return if (foundTitle && foundIngredients && foundMethod) {
                Completeness.Complete
            } else {
                Completeness.Partial(foundTitle, foundIngredients, foundMethod)
            }
        }

    /** Counts, the tier and enum names only. */
    override fun toString(): String =
        "ImportDraft(tier=$tier, titleStated=${title is DraftValue.Stated}, ingredients=${ingredientLines.size}, " +
            "methodStated=${method is DraftValue.Stated}, image=${image::class.java.simpleName})"
}
