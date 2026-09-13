package com.pantry.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pantry.app.data.db.MealSlot
import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.nutrition.NutritionRepository
import com.pantry.app.nutrition.RecipeMacros
import com.pantry.app.repo.MealPlanRepository
import com.pantry.app.repo.RecipeRepository
import com.pantry.app.repo.ShoppingRepository
import com.pantry.app.seasonality.SeasonAdvice
import com.pantry.app.seasonality.Seasonality
import com.pantry.app.shopping.ShoppingSource
import com.pantry.app.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecipeDetailViewModel(
    private val recipeId: String,
    private val recipes: RecipeRepository,
    private val nutrition: NutritionRepository,
    private val shopping: ShoppingRepository,
    private val plan: MealPlanRepository
) : ViewModel() {

    val recipe: StateFlow<RecipeWithIngredients?> =
        recipes.observeById(recipeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _servings = MutableStateFlow<Int?>(null)
    /** null until the recipe loads, then the user's chosen scale. */
    val servings: StateFlow<Int?> = _servings.asStateFlow()

    private val _macros = MutableStateFlow(RecipeMacros.EMPTY)
    val macros: StateFlow<RecipeMacros> = _macros.asStateFlow()

    private val _seasonality = MutableStateFlow<List<SeasonAdvice>>(emptyList())
    val seasonality: StateFlow<List<SeasonAdvice>> = _seasonality.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            recipe.collect { loaded ->
                if (loaded == null) return@collect
                if (_servings.value == null) _servings.value = loaded.recipe.servings
                _macros.value = nutrition.compute(loaded, _servings.value)
                _seasonality.value = Seasonality.adviseAll(loaded.ingredients.map { it.name }, LocalDate.now())
            }
        }
    }

    fun setServings(value: Int) {
        val clamped = value.coerceIn(1, 50)
        _servings.value = clamped
        val loaded = recipe.value ?: return
        viewModelScope.launch { _macros.value = nutrition.compute(loaded, clamped) }
    }

    /** Ingredient quantities rescaled to the servings currently on screen. */
    fun scale(): Double {
        val loaded = recipe.value ?: return 1.0
        val chosen = _servings.value ?: loaded.recipe.servings
        return chosen.toDouble() / loaded.recipe.servings.coerceAtLeast(1)
    }

    fun toggleFavourite() = viewModelScope.launch {
        recipe.value?.let { recipes.setFavourite(it.recipe, !it.recipe.favourite) }
    }

    fun addToShoppingList(onDone: (String) -> Unit) = viewModelScope.launch {
        val loaded = recipe.value ?: return@launch
        val id = shopping.generate(
            loaded.recipe.title,
            listOf(ShoppingSource(loaded, _servings.value ?: loaded.recipe.servings))
        )
        onDone(id)
    }

    fun addToPlan(date: LocalDate, slot: MealSlot) = viewModelScope.launch {
        val loaded = recipe.value ?: return@launch
        plan.add(date, slot, loaded.recipe.id, _servings.value ?: loaded.recipe.servings)
        _message.value = "Added to ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}"
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        recipes.delete(recipeId)
        onDone()
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(recipeId: String) = viewModelFactory {
            initializer {
                val c = container
                RecipeDetailViewModel(recipeId, c.recipes, c.nutrition, c.shopping, c.mealPlan)
            }
        }
    }
}
