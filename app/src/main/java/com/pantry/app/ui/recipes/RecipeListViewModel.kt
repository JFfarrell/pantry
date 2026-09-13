package com.pantry.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.nutrition.NutritionRepository
import com.pantry.app.nutrition.RecipeMacros
import com.pantry.app.repo.RecipeRepository
import com.pantry.app.repo.ShoppingRepository
import com.pantry.app.seasonality.SeasonStatus
import com.pantry.app.seasonality.Seasonality
import com.pantry.app.shopping.ShoppingSource
import com.pantry.app.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class RecipeFilter(val label: String) {
    ALL("All"), FAVOURITES("Favourites"), QUICK("Under 30 min"), IN_SEASON("In season")
}

data class RecipeListUiState(
    val recipes: List<RecipeWithIngredients> = emptyList(),
    val query: String = "",
    val filter: RecipeFilter = RecipeFilter.ALL,
    val selected: Set<String> = emptySet(),
    val seasonalNow: List<String> = emptyList()
) {
    val selecting: Boolean get() = selected.isNotEmpty()
}

class RecipeListViewModel(
    private val recipeRepo: RecipeRepository,
    private val shoppingRepo: ShoppingRepository,
    private val nutrition: NutritionRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(RecipeFilter.ALL)
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    private val _macros = MutableStateFlow<Map<String, RecipeMacros>>(emptyMap())
    val macros: StateFlow<Map<String, RecipeMacros>> = _macros.asStateFlow()

    val uiState: StateFlow<RecipeListUiState> =
        combine(recipeRepo.observeAll(), query, filter, selected) { all, q, f, sel ->
            RecipeListUiState(
                recipes = all.filter { it.matches(q) && it.matches(f) },
                query = q,
                filter = f,
                selected = sel,
                seasonalNow = Seasonality.inSeasonNow()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

    init {
        // Macro figures are cached in the database after the first pass, so this
        // only costs network on genuinely new ingredients.
        viewModelScope.launch {
            recipeRepo.observeAll().collect { recipes ->
                recipes.forEach { recipe ->
                    if (_macros.value.containsKey(recipe.recipe.id)) return@forEach
                    val computed = nutrition.compute(recipe)
                    _macros.value = _macros.value + (recipe.recipe.id to computed)
                }
            }
        }
    }

    private fun RecipeWithIngredients.matches(q: String): Boolean {
        if (q.isBlank()) return true
        val needle = q.trim().lowercase()
        return recipe.title.lowercase().contains(needle) ||
            recipe.tags.any { it.lowercase().contains(needle) } ||
            ingredients.any { it.name.lowercase().contains(needle) }
    }

    private fun RecipeWithIngredients.matches(f: RecipeFilter): Boolean = when (f) {
        RecipeFilter.ALL -> true
        RecipeFilter.FAVOURITES -> recipe.favourite
        RecipeFilter.QUICK -> (recipe.displayMinutes ?: Int.MAX_VALUE) <= 30
        RecipeFilter.IN_SEASON -> Seasonality
            .adviseAll(ingredients.map { it.name }, LocalDate.now())
            .let { advice ->
                advice.any { it.status == SeasonStatus.IN_SEASON } &&
                    advice.none { it.status == SeasonStatus.OUT_OF_SEASON }
            }
    }

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: RecipeFilter) { filter.value = value }

    fun toggleSelected(id: String) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
    }

    fun clearSelection() { selected.value = emptySet() }

    fun toggleFavourite(recipe: RecipeWithIngredients) = viewModelScope.launch {
        recipeRepo.setFavourite(recipe.recipe, !recipe.recipe.favourite)
    }

    fun delete(id: String) = viewModelScope.launch {
        recipeRepo.delete(id)
        selected.value = selected.value - id
    }

    /** Feature 4: one list from however many recipes are ticked. */
    fun buildShoppingList(onDone: (String) -> Unit) = viewModelScope.launch {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return@launch
        val recipes = recipeRepo.getByIds(ids)
        val name = if (recipes.size == 1) recipes.first().recipe.title
        else "${recipes.size} recipes"
        val listId = shoppingRepo.generate(name, recipes.map { ShoppingSource(it) })
        selected.value = emptySet()
        onDone(listId)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                RecipeListViewModel(c.recipes, c.shopping, c.nutrition)
            }
        }
    }
}

