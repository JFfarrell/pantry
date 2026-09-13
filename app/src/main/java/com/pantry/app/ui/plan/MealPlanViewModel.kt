package com.pantry.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pantry.app.data.db.MealSlot
import com.pantry.app.data.db.PlannedMeal
import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.domain.Macros
import com.pantry.app.nutrition.NutritionRepository
import com.pantry.app.repo.MealPlanRepository
import com.pantry.app.repo.RecipeRepository
import com.pantry.app.repo.ShoppingRepository
import com.pantry.app.seasonality.Seasonality
import com.pantry.app.shopping.ShoppingSource
import com.pantry.app.ui.container
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DaySummary(
    val date: LocalDate,
    val meals: List<PlannedMeal>,
    val macros: Macros,
    val totalMinutes: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanViewModel(
    private val plan: MealPlanRepository,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val nutrition: NutritionRepository
) : ViewModel() {

    private val _weekStart = MutableStateFlow(MealPlanRepository.weekStartFor(LocalDate.now()))
    val weekStart: StateFlow<LocalDate> = _weekStart.asStateFlow()

    private val _days = MutableStateFlow<List<DaySummary>>(emptyList())
    val days: StateFlow<List<DaySummary>> = _days.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val catalogue: StateFlow<List<RecipeWithIngredients>> =
        recipes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Feature 7 on the planning screen: what is worth cooking this month. */
    val inSeasonNow: List<String> get() = Seasonality.inSeasonNow(LocalDate.now())

    init {
        viewModelScope.launch {
            _weekStart
                .flatMapLatest { start -> plan.observeWeek(start) }
                .collect { meals -> rebuild(meals) }
        }
    }

    private suspend fun rebuild(meals: List<PlannedMeal>) {
        val start = _weekStart.value
        val byDay = meals.groupBy { it.entry.epochDay }

        // Macros are per planned serving count, not per the recipe's own default.
        val macroCache = mutableMapOf<String, Macros>()
        _days.value = (0..6).map { offset ->
            val date = start.plusDays(offset.toLong())
            val dayMeals = byDay[date.toEpochDay()].orEmpty()

            var total = Macros.ZERO
            var minutes = 0
            for (meal in dayMeals) {
                val perServing = macroCache.getOrPut(meal.recipe.id) {
                    val full = recipes.getByIds(listOf(meal.recipe.id)).firstOrNull()
                    if (full == null) Macros.ZERO else nutrition.compute(full).perServing
                }
                total += perServing * meal.entry.servings.toDouble()
                minutes += meal.recipe.displayMinutes ?: 0
            }
            DaySummary(date, dayMeals, total, minutes)
        }
    }

    fun nextWeek() { _weekStart.value = _weekStart.value.plusWeeks(1) }
    fun previousWeek() { _weekStart.value = _weekStart.value.minusWeeks(1) }
    fun thisWeek() { _weekStart.value = MealPlanRepository.weekStartFor(LocalDate.now()) }

    fun add(date: LocalDate, slot: MealSlot, recipeId: String, servings: Int) = viewModelScope.launch {
        plan.add(date, slot, recipeId, servings)
    }

    fun remove(entryId: Long) = viewModelScope.launch { plan.remove(entryId) }

    fun setServings(entryId: Long, servings: Int) = viewModelScope.launch {
        plan.setServings(entryId, servings)
    }

    fun clearWeek() = viewModelScope.launch { plan.clearWeek(_weekStart.value) }

    /** Feature 6: fill the week automatically, weighted by season and cooking time. */
    fun autoFill(replaceExisting: Boolean, servings: Int) = viewModelScope.launch {
        _busy.value = true
        val placed = plan.autoFillDinners(_weekStart.value, catalogue.value, servings, replaceExisting)
        _busy.value = false
        _message.value = when {
            catalogue.value.isEmpty() -> "Import some recipes first."
            placed == 0 -> "Every dinner this week is already planned."
            else -> "Planned $placed dinners, leaning on what is in season."
        }
    }

    /** Feature 4: the whole week becomes one shopping list. */
    fun shopForWeek(onDone: (String) -> Unit) = viewModelScope.launch {
        val meals = plan.getWeek(_weekStart.value)
        if (meals.isEmpty()) {
            _message.value = "Nothing planned this week yet."
            return@launch
        }
        val full = recipes.getByIds(meals.map { it.entry.recipeId }.distinct())
        val sources = meals.mapNotNull { meal ->
            full.firstOrNull { it.recipe.id == meal.entry.recipeId }
                ?.let { ShoppingSource(it, meal.entry.servings) }
        }
        val start = _weekStart.value
        val listId = shopping.generate(
            "Week of ${start.dayOfMonth}/${start.monthValue}",
            sources
        )
        onDone(listId)
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                MealPlanViewModel(c.mealPlan, c.recipes, c.shopping, c.nutrition)
            }
        }
    }
}
