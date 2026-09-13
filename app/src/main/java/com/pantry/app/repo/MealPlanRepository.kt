package com.pantry.app.repo

import com.pantry.app.data.db.MealPlanDao
import com.pantry.app.data.db.MealPlanEntryEntity
import com.pantry.app.data.db.MealSlot
import com.pantry.app.data.db.PlannedMeal
import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.seasonality.SeasonStatus
import com.pantry.app.seasonality.Seasonality
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class MealPlanRepository(private val dao: MealPlanDao) {

    fun observeWeek(weekStart: LocalDate): Flow<List<PlannedMeal>> =
        dao.observeRange(weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay())

    suspend fun getWeek(weekStart: LocalDate): List<PlannedMeal> =
        dao.getRange(weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay())

    suspend fun add(date: LocalDate, slot: MealSlot, recipeId: String, servings: Int) {
        dao.insert(MealPlanEntryEntity(epochDay = date.toEpochDay(), slot = slot, recipeId = recipeId, servings = servings))
    }

    suspend fun remove(entryId: Long) = dao.delete(entryId)

    suspend fun setServings(entryId: Long, servings: Int) = dao.setServings(entryId, servings.coerceIn(1, 50))

    suspend fun clearWeek(weekStart: LocalDate) =
        dao.clearRange(weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay())

    /**
     * Feature 6 + 7: fill the week's dinners from the catalogue, favouring
     * recipes whose ingredients are in season and avoiding the same recipe
     * twice running.
     */
    suspend fun autoFillDinners(
        weekStart: LocalDate,
        catalogue: List<RecipeWithIngredients>,
        defaultServings: Int,
        replaceExisting: Boolean
    ): Int {
        if (catalogue.isEmpty()) return 0
        val existing = getWeek(weekStart)
        if (replaceExisting) clearWeek(weekStart)

        val taken = if (replaceExisting) emptySet() else
            existing.filter { it.entry.slot == MealSlot.DINNER }.map { it.entry.epochDay }.toSet()

        var placed = 0
        var lastId: String? = null
        val usedThisWeek = mutableSetOf<String>()

        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())
            if (date.toEpochDay() in taken) continue

            val ranked = catalogue
                .filter { it.recipe.id != lastId }
                .sortedByDescending { score(it, date, usedThisWeek) }

            val pick = ranked.firstOrNull() ?: continue
            add(date, MealSlot.DINNER, pick.recipe.id, defaultServings)
            usedThisWeek += pick.recipe.id
            lastId = pick.recipe.id
            placed++
        }
        return placed
    }

    private fun score(recipe: RecipeWithIngredients, date: LocalDate, usedThisWeek: Set<String>): Double {
        var score = if (recipe.recipe.id in usedThisWeek) -50.0 else 0.0
        if (recipe.recipe.favourite) score += 8.0

        val advice = Seasonality.adviseAll(recipe.ingredients.map { it.name }, date)
        score += advice.count { it.status == SeasonStatus.IN_SEASON } * 6.0
        score -= advice.count { it.status == SeasonStatus.OUT_OF_SEASON } * 5.0

        // Weeknights lean quick; the weekend can take a longer cook.
        val minutes = recipe.recipe.displayMinutes ?: 45
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        score += if (weekend) minutes / 30.0 else -(minutes / 20.0)

        score += Math.random() * 3.0 // keeps repeat generations from being identical
        return score
    }

    companion object {
        fun weekStartFor(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
