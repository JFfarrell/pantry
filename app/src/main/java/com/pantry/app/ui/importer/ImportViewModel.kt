package com.pantry.app.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pantry.app.importer.ImportResult
import com.pantry.app.importer.ImportedRecipe
import com.pantry.app.importer.RecipeUrlImporter
import com.pantry.app.repo.RecipeRepository
import com.pantry.app.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Editable copy of an import, so the user can fix anything the parser got wrong. */
data class ImportDraft(
    val title: String = "",
    val servings: String = "2",
    val prepMinutes: String = "",
    val cookMinutes: String = "",
    val totalMinutes: String = "",
    val ingredientsText: String = "",
    val stepsText: String = "",
    val imageUrl: String? = null,
    val sourceUrl: String = "",
    val sourceName: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val cuisine: String? = null
) {
    fun toImported() = ImportedRecipe(
        title = title,
        description = description,
        imageUrl = imageUrl,
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        servings = servings.toIntOrNull() ?: 2,
        prepMinutes = prepMinutes.toIntOrNull(),
        cookMinutes = cookMinutes.toIntOrNull(),
        totalMinutes = totalMinutes.toIntOrNull(),
        ingredientLines = ingredientsText.lines().map { it.trim() }.filter { it.isNotBlank() },
        steps = stepsText.split("\n\n", "\n").map { it.trim() }.filter { it.isNotBlank() },
        tags = tags,
        cuisine = cuisine
    )

    companion object {
        fun from(recipe: ImportedRecipe) = ImportDraft(
            title = recipe.title,
            servings = (recipe.servings ?: 2).toString(),
            prepMinutes = recipe.prepMinutes?.toString().orEmpty(),
            cookMinutes = recipe.cookMinutes?.toString().orEmpty(),
            totalMinutes = recipe.totalMinutes?.toString().orEmpty(),
            ingredientsText = recipe.ingredientLines.joinToString("\n"),
            stepsText = recipe.steps.joinToString("\n\n"),
            imageUrl = recipe.imageUrl,
            sourceUrl = recipe.sourceUrl,
            sourceName = recipe.sourceName,
            description = recipe.description,
            tags = recipe.tags,
            cuisine = recipe.cuisine
        )
    }
}

data class ImportUiState(
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
    val strategy: String? = null,
    val draft: ImportDraft? = null,
    val duplicateOfId: String? = null,
    val savedId: String? = null
)

class ImportViewModel(
    private val importer: RecipeUrlImporter,
    private val recipes: RecipeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun setUrl(value: String) {
        _state.value = _state.value.copy(url = value, error = null)
    }

    /** Called when a link arrives via the share sheet. */
    fun startWith(url: String) {
        if (url.isBlank() || _state.value.loading) return
        _state.value = _state.value.copy(url = url)
        fetch()
    }

    fun fetch() {
        val url = _state.value.url.trim()
        if (url.isBlank()) {
            _state.value = _state.value.copy(error = "Paste a recipe link first.")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null, warning = null, draft = null)

        viewModelScope.launch {
            when (val result = importer.import(url)) {
                is ImportResult.Success -> present(result.recipe, null)
                is ImportResult.Partial -> present(result.recipe, result.reason)
                is ImportResult.Failure ->
                    _state.value = _state.value.copy(loading = false, error = result.reason)
            }
        }
    }

    private suspend fun present(recipe: ImportedRecipe, warning: String?) {
        _state.value = _state.value.copy(
            loading = false,
            draft = ImportDraft.from(recipe),
            warning = warning,
            strategy = recipe.strategy,
            duplicateOfId = recipes.existingIdFor(recipe.sourceUrl)
        )
    }

    fun updateDraft(transform: (ImportDraft) -> ImportDraft) {
        _state.value.draft?.let { _state.value = _state.value.copy(draft = transform(it)) }
    }

    fun save(onSaved: (String) -> Unit) {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            val id = recipes.save(draft.toImported(), existingId = _state.value.duplicateOfId)
            _state.value = ImportUiState()
            onSaved(id)
        }
    }

    fun reset() { _state.value = ImportUiState() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                ImportViewModel(c.importer, c.recipes)
            }
        }
    }
}
