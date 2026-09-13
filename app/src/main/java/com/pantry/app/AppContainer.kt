package com.pantry.app

import android.content.Context
import com.pantry.app.data.db.AppDatabase
import com.pantry.app.importer.RecipeUrlImporter
import com.pantry.app.nutrition.NutritionRepository
import com.pantry.app.nutrition.OpenFoodFactsClient
import com.pantry.app.nutrition.StaplesTable
import com.pantry.app.repo.MealPlanRepository
import com.pantry.app.repo.RecipeRepository
import com.pantry.app.repo.ShoppingRepository

/**
 * Hand-rolled dependency graph. The app is small enough that a container beats
 * a DI framework: everything is constructed once, lazily, and is easy to follow.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.build(appContext) }

    val recipes by lazy { RecipeRepository(database.recipeDao()) }
    val mealPlan by lazy { MealPlanRepository(database.mealPlanDao()) }
    val shopping by lazy { ShoppingRepository(database.shoppingDao()) }
    val importer by lazy { RecipeUrlImporter() }
    val nutrition by lazy {
        NutritionRepository(
            dao = database.nutritionDao(),
            staples = StaplesTable(appContext),
            remote = OpenFoodFactsClient()
        )
    }
}
