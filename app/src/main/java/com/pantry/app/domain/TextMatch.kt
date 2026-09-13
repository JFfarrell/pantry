package com.pantry.app.domain

/**
 * Lookup tables here are keyed on ingredient words, and plain `contains` gets
 * that wrong in ways that matter: "peanut butter" would match the seasonal
 * entry for "pea", and "pearl barley" the entry for "pear". Every table lookup
 * goes through whole-word matching instead.
 */
object TextMatch {

    private val cache = HashMap<String, Regex>()

    fun containsWord(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        val pattern = cache.getOrPut(needle) { Regex("(?<![a-z0-9])" + Regex.escape(needle) + "(?![a-z0-9])") }
        return pattern.containsMatchIn(haystack)
    }

    /**
     * The most specific table entry that appears in [name], so
     * "extra virgin olive oil" resolves to "olive oil" rather than "oil".
     */
    fun <T> bestMatch(table: Map<String, T>, name: String): Map.Entry<String, T>? {
        table.entries.firstOrNull { it.key == name }?.let { return it }
        return table.entries
            .filter { containsWord(name, it.key) }
            .maxByOrNull { it.key.length }
    }
}
