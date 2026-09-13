package com.pantry.app.importer

/** Whatever the importer could pull off a page, before it becomes a saved recipe. */
data class ImportedRecipe(
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val sourceUrl: String,
    val sourceName: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredientLines: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val cuisine: String? = null,
    /** Which extraction strategy produced this, shown to the user so a thin result is explicable. */
    val strategy: String = "unknown"
) {
    val looksComplete: Boolean get() = title.isNotBlank() && ingredientLines.isNotEmpty()
}

sealed interface ImportResult {
    data class Success(val recipe: ImportedRecipe) : ImportResult
    /** Page fetched but no recipe found: hand the user what we have for manual tidy-up. */
    data class Partial(val recipe: ImportedRecipe, val reason: String) : ImportResult
    data class Failure(val reason: String) : ImportResult
}
