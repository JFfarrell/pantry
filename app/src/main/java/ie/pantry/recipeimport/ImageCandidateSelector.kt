package ie.pantry.recipeimport

import java.net.URI
import java.net.URISyntaxException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** The one picture the page offers, chosen by the first tier that has one. */
internal sealed interface Candidate {
    /** An absolute `http` or `https` URL to fetch. */
    class Url(val value: String) : Candidate {
        override fun toString(): String = "Candidate.Url(length=${value.length})"
    }

    /** A candidate whose scheme is not `http` or `https`, such as `data:`. */
    data object NotHttp : Candidate

    /** A JSON-LD image string that is not a valid URI. */
    data object BadUrl : Candidate

    /** The page offers no picture. */
    data object None : Candidate
}

/** Image candidate selection (AD12): JSON-LD, then `og:image`, then inline `img`; it never falls through. */
internal object ImageCandidateSelector {

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

    fun select(document: Document, scan: JsonLdScan): Candidate {
        scan.recipes.firstNotNullOfOrNull { it.image }?.let { return fromJsonLd(it, document) }
        val og = document.select("meta[property~=(?i)^og:image$]").firstOrNull { it.attr("content").isNotBlank() }
        if (og != null) return fromResolved(og.absUrl("content"))
        val images = document.select("img[src]").filter { it.attr("src").isNotBlank() }
        if (images.isEmpty()) return Candidate.None
        return fromResolved(largestDeclared(images).absUrl("src"))
    }

    /** A JSON-LD string resolves inside one guard; an absolute value is used as is, never joined with the page URL. */
    private fun fromJsonLd(raw: String, document: Document): Candidate {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Candidate.None
        return try {
            val parsed = URI(trimmed)
            val resolved = if (parsed.isAbsolute) parsed else URI(document.baseUri()).resolve(parsed)
            fromResolved(resolved.toString())
        } catch (e: URISyntaxException) {
            Candidate.BadUrl
        } catch (e: IllegalArgumentException) {
            Candidate.BadUrl
        }
    }

    private fun fromResolved(resolved: String): Candidate {
        if (resolved.isBlank()) return Candidate.None
        val scheme = SCHEME.find(resolved)?.groupValues?.get(1)?.lowercase()
        return if (scheme == "http" || scheme == "https") Candidate.Url(resolved) else Candidate.NotHttp
    }

    /** The largest `width` x `height` where both are positive integers, first on a tie; else the first image. */
    private fun largestDeclared(images: List<Element>): Element {
        var best: Element? = null
        var bestArea = 0L
        for (image in images) {
            val width = image.attr("width").trim().toIntOrNull()?.takeIf { it > 0 } ?: continue
            val height = image.attr("height").trim().toIntOrNull()?.takeIf { it > 0 } ?: continue
            val area = width.toLong() * height
            if (area > bestArea) {
                best = image
                bestArea = area
            }
        }
        return best ?: images.first()
    }
}
