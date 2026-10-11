package ie.pantry.recipeimport

/** An `http(s)` URL that passed [ImportUrl.check]. Only [ImportUrl] can build one. */
@JvmInline
value class ValidatedUrl internal constructor(val value: String) {
    override fun toString(): String = "ValidatedUrl(length=${value.length})"
}

/** The first link found in a text. [text] is the full run, never truncated; [overLimit] says it reached the scan limit. */
class PickedUrl(val text: String, val overLimit: Boolean) {
    override fun toString(): String = "PickedUrl(length=${text.length}, overLimit=$overLimit)"
}

enum class InvalidReason { NO_URL, OVER_LIMIT, USERINFO, NO_HOST }

sealed interface UrlCheck {
    class Valid internal constructor(val url: ValidatedUrl) : UrlCheck {
        override fun toString(): String = "Valid(length=${url.value.length})"
    }

    data class Invalid(val reason: InvalidReason) : UrlCheck
}

/** URL intake: picks the first link out of shared text and validates it before any fetch. Pure Kotlin. */
object ImportUrl {
    const val SCAN_LIMIT: Int = 2048

    private const val OPENERS = "([<\"'"
    private const val TRAILING_PUNCTUATION = ".,;:!?"

    /** The share side: the first `http(s)` URL starting inside the first [SCAN_LIMIT] characters, or null. */
    fun pick(text: String): PickedUrl? {
        val start = findStart(text) ?: return null
        val quote = if (start > 0) text[start - 1] else ' '
        var end = start
        while (end < text.length) {
            val c = text[end]
            if (c.isWhitespace() || c == '"' || c == '>' || (c == '\'' && quote == '\'')) break
            end++
        }
        val overLimit = end >= SCAN_LIMIT
        var open = 0
        var close = 0
        var openSquare = 0
        var closeSquare = 0
        for (i in start until end) {
            when (text[i]) {
                '(' -> open++
                ')' -> close++
                '[' -> openSquare++
                ']' -> closeSquare++
            }
        }
        var last = end
        while (last > start) {
            val c = text[last - 1]
            if (c in TRAILING_PUNCTUATION) {
                last--
            } else if (c == ')' && close > open) {
                close--
                last--
            } else if (c == ']' && closeSquare > openSquare) {
                closeSquare--
                last--
            } else {
                break
            }
        }
        return PickedUrl(text.substring(start, last), overLimit)
    }

    /** The import side: picks, then validates. Never throws. */
    fun check(text: String): UrlCheck {
        val picked = pick(text) ?: return UrlCheck.Invalid(InvalidReason.NO_URL)
        if (picked.overLimit) return UrlCheck.Invalid(InvalidReason.OVER_LIMIT)
        val url = picked.text
        val authorityStart = url.indexOf("://") + 3
        var authorityEnd = authorityStart
        while (authorityEnd < url.length && url[authorityEnd] !in "/?#\\") authorityEnd++
        val authority = url.substring(authorityStart, authorityEnd)
        if (authority.contains('@')) return UrlCheck.Invalid(InvalidReason.USERINFO)
        val host = if (authority.startsWith("[")) authority else authority.substringBefore(':')
        if (host.isEmpty()) return UrlCheck.Invalid(InvalidReason.NO_HOST)
        return UrlCheck.Valid(ValidatedUrl(url))
    }

    private fun findStart(text: String): Int? {
        val limit = minOf(text.length, SCAN_LIMIT)
        for (i in 0 until limit) {
            val c = text[i]
            if (c != 'h' && c != 'H') continue
            if (i > 0 && !text[i - 1].isWhitespace() && text[i - 1] !in OPENERS) continue
            if (text.regionMatches(i, "https://", 0, 8, ignoreCase = true) ||
                text.regionMatches(i, "http://", 0, 7, ignoreCase = true)
            ) {
                return i
            }
        }
        return null
    }
}
