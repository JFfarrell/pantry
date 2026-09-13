package com.pantry.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pantry.app.ui.importer.ImportScreen
import com.pantry.app.ui.plan.MealPlanScreen
import com.pantry.app.ui.recipes.RecipeDetailScreen
import com.pantry.app.ui.recipes.RecipeListScreen
import com.pantry.app.ui.shopping.ShoppingScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("recipes", "Recipes", Icons.Default.MenuBook),
    Tab("plan", "Plan", Icons.Default.CalendarMonth),
    Tab("shopping", "Shopping", Icons.Default.ShoppingCart),
    Tab("import", "Import", Icons.Default.AddLink)
)

@Composable
fun PantryNav(sharedUrl: String? = null) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (tabs.any { currentRoute?.startsWith(it.route) == true }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute?.startsWith(tab.route) == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (sharedUrl.isNullOrBlank()) "recipes" else "import",
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable("recipes") {
                RecipeListScreen(
                    onOpenRecipe = { id -> navController.navigate("recipe/$id") },
                    onShoppingListCreated = { listId -> navController.navigate("shopping?listId=$listId") }
                )
            }

            composable("plan") {
                MealPlanScreen(
                    onOpenRecipe = { id -> navController.navigate("recipe/$id") },
                    onShoppingListCreated = { listId -> navController.navigate("shopping?listId=$listId") }
                )
            }

            composable(
                route = "shopping?listId={listId}",
                arguments = listOf(navArgument("listId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                ShoppingScreen(listId = entry.arguments?.getString("listId"))
            }

            composable("import") {
                ImportScreen(
                    sharedUrl = sharedUrl,
                    onSaved = { id ->
                        navController.navigate("recipe/$id") {
                            popUpTo("recipes")
                        }
                    }
                )
            }

            composable(
                route = "recipe/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                RecipeDetailScreen(
                    recipeId = id,
                    onBack = { navController.popBackStack() },
                    onShoppingListCreated = { listId ->
                        navController.navigate("shopping?listId=$listId")
                    }
                )
            }
        }
    }
}
