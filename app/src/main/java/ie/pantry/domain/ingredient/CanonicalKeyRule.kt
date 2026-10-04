package ie.pantry.domain.ingredient

import ie.pantry.data.reference.Lookup

/** Turns the name text of a [ScannedLine] into a [CanonicalKey] by AD10's steps K1-K8 (R2). */
internal object CanonicalKeyRule {

    private val PREPARATION_PHRASES = listOf("to taste", "to serve", "for garnish")
    private val PREPARATION_WORDS = setOf(
        "chopped", "finely", "roughly", "thinly", "diced", "sliced", "minced", "crushed",
        "grated", "peeled", "halved", "trimmed", "beaten", "softened", "melted", "fresh",
        "freshly", "ground", "large", "medium", "small", "ripe",
    )
    private val PROTECTED_PHRASES = mapOf(
        "chopped tomato" to "chopped tomato",
        "chopped tomatoes" to "chopped tomato",
    )

    fun resolve(line: ScannedLine, nameStart: Int, aliasLookup: (String) -> Lookup<String>): CanonicalKey {
        val text = line.text
        val raw = text.substring(nameStart.coerceIn(0, text.length))

        // K1: drop parenthesised spans; an unbalanced '(' drops to the end of the line, and a
        // stray ')' becomes a space.
        val noParens = dropParens(raw)

        // K2: cut at the first comma.
        val commaIdx = noParens.indexOf(',')
        val beforeComma = if (commaIdx >= 0) noParens.substring(0, commaIdx) else noParens

        // K3: remaining '-' become spaces, then collapse and trim.
        val namePhrase = collapseWhitespace(beforeComma.replace('-', ' ')).trim()
        if (namePhrase.isEmpty()) return CanonicalKey.Absent

        // K4: stage-1 lookup of the name phrase.
        when (val hit = aliasLookup(namePhrase)) {
            is Lookup.Found -> return CanonicalKey.Derived(hit.value)
            Lookup.Absent -> Unit
        }

        // K5: protected phrase, returned directly with no further processing.
        PROTECTED_PHRASES[namePhrase]?.let { return CanonicalKey.Derived(it) }

        // K6: remove preparation phrases, then the preparation/size words, at word boundaries.
        var stripped = namePhrase
        for (phrase in PREPARATION_PHRASES) {
            stripped = removePhrase(stripped, phrase)
        }
        stripped = removeWords(collapseWhitespace(stripped).trim(), PREPARATION_WORDS)
        stripped = collapseWhitespace(stripped).trim()
        if (stripped.isEmpty()) return CanonicalKey.Absent

        // K7: singularise the last word.
        val ruleOutput = singulariseLastWord(stripped)

        // K8: stage-2 lookup of the rule output.
        return when (val hit = aliasLookup(ruleOutput)) {
            is Lookup.Found -> CanonicalKey.Derived(hit.value)
            Lookup.Absent -> CanonicalKey.Derived(ruleOutput)
        }
    }

    private fun dropParens(text: String): String {
        val sb = StringBuilder()
        var depth = 0
        for (c in text) {
            when {
                c == '(' -> depth++
                c == ')' && depth > 0 -> depth--
                c == ')' -> sb.append(' ')
                depth == 0 -> sb.append(c)
                else -> Unit
            }
        }
        return sb.toString()
    }

    private fun collapseWhitespace(text: String): String {
        val sb = StringBuilder()
        var lastWasSpace = false
        for (c in text) {
            if (c == ' ') {
                if (!lastWasSpace) sb.append(' ')
                lastWasSpace = true
            } else {
                sb.append(c)
                lastWasSpace = false
            }
        }
        return sb.toString()
    }

    private fun removeWords(text: String, words: Set<String>): String {
        if (text.isEmpty()) return text
        return text.split(' ').filter { it !in words }.joinToString(" ")
    }

    private fun removePhrase(text: String, phrase: String): String {
        if (text.isEmpty()) return text
        val phraseWords = phrase.split(' ')
        val tokens = text.split(' ')
        val result = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            if (i + phraseWords.size <= tokens.size && tokens.subList(i, i + phraseWords.size) == phraseWords) {
                i += phraseWords.size
            } else {
                result.add(tokens[i])
                i++
            }
        }
        return result.joinToString(" ")
    }

    private fun singulariseLastWord(text: String): String {
        val lastSpace = text.lastIndexOf(' ')
        val prefix = if (lastSpace >= 0) text.substring(0, lastSpace + 1) else ""
        val lastWord = if (lastSpace >= 0) text.substring(lastSpace + 1) else text
        return prefix + singularise(lastWord)
    }

    private fun singularise(word: String): String = when {
        word.endsWith("ies") -> word.dropLast(3) + "y"
        word.endsWith("oes") -> word.dropLast(2)
        word.endsWith("ches") || word.endsWith("shes") || word.endsWith("sses") || word.endsWith("xes") -> word.dropLast(2)
        word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
        word.endsWith("s") -> word.dropLast(1)
        else -> word
    }
}
