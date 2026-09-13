package com.pantry.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        MealPlanEntryEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        NutritionCacheEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pantry.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
