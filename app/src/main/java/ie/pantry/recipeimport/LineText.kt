package ie.pantry.recipeimport

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

/** Text normalisation for ingredient lines, steps and titles (AD9). */
internal object LineText {

    /** The longest raw text one element may collect before its line is dropped, not truncated. */
    const val MAX_RAW_CHARS: Int = 4096

    private const val NBSP = ' '

    /**
     * Folds U+00A0 to a space, collapses runs of ASCII whitespace to one space and trims the ends. Nothing else
     * changes: list markers and other Unicode spaces such as U+2009 and U+202F are kept.
     */
    fun normalise(raw: String): String {
        val out = StringBuilder(raw.length)
        var pendingSpace = false
        for (c in raw) {
            if (c == NBSP || isAsciiSpace(c)) {
                pendingSpace = true
            } else {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(c)
            }
        }
        return out.toString()
    }

    private fun isAsciiSpace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

    /**
     * The normalised text of [element]: every text node's whole text, with a space at each `<br>` and block
     * boundary. Returns null once the raw text (the text nodes' characters, not the separators) passes [MAX_RAW_CHARS].
     */
    fun of(element: Element): String? {
        val raw = StringBuilder()
        var textLength = 0
        var tooLong = false
        NodeTraversor.filter(
            object : NodeFilter {
                override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
                    when (node) {
                        is TextNode -> {
                            raw.append(node.wholeText)
                            textLength += node.wholeText.length
                        }
                        is Element -> if (node.nameIs("br") || node.isBlock) raw.append(' ')
                    }
                    if (textLength > MAX_RAW_CHARS) {
                        tooLong = true
                        return NodeFilter.FilterResult.STOP
                    }
                    return NodeFilter.FilterResult.CONTINUE
                }

                override fun tail(node: Node, depth: Int): NodeFilter.FilterResult {
                    if (node is Element && node.isBlock) raw.append(' ')
                    return NodeFilter.FilterResult.CONTINUE
                }
            },
            element,
        )
        return if (tooLong) null else normalise(raw.toString())
    }
}

/**
 * The one iterative walk every extractor DOM traversal uses. It never recurses, so a deep chain cannot overflow
 * the stack. It skips any element deeper than [MAX_DEPTH] (the `html` element is depth 1) with its subtree, and
 * checks [Job] cancellation every [CHECK_INTERVAL] visited nodes.
 */
internal object BoundedDomWalk {
    const val MAX_DEPTH: Int = 512
    const val CHECK_INTERVAL: Int = 1024

    /**
     * Walks [root] in document order. [enter] is called with each node and its depth (the root's own depth is 0)
     * and returns false to skip that node's children; [leave] is called after the children of an entered node.
     */
    fun walk(
        root: Node,
        job: Job,
        enter: (node: Node, depth: Int) -> Boolean,
        leave: (node: Node, depth: Int) -> Unit = { _, _ -> },
    ) {
        var visited = 0
        var node: Node? = root
        var depth = 0
        while (node != null) {
            val skipped = node is Element && depth > MAX_DEPTH
            var descend = false
            if (!skipped) {
                visited++
                if (visited % CHECK_INTERVAL == 0) job.ensureActive()
                descend = enter(node, depth)
            }
            if (descend && node.childNodeSize() > 0) {
                node = node.childNode(0)
                depth++
                continue
            }
            // No children to enter: leave this node, then climb to the next sibling.
            var current: Node = node
            if (!skipped) leave(current, depth)
            while (true) {
                if (current === root) return
                val next = current.nextSibling()
                if (next != null) {
                    node = next
                    break
                }
                current = current.parent() ?: return
                depth--
                if (current === root) {
                    leave(current, depth)
                    return
                }
                leave(current, depth)
            }
        }
    }
}
