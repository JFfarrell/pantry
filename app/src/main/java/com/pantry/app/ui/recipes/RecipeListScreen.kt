@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pantry.app.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pantry.app.data.db.RecipeWithIngredients
import com.pantry.app.nutrition.RecipeMacros
import com.pantry.app.ui.common.EmptyState
import com.pantry.app.ui.common.TimeChip
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    onOpenRecipe: (String) -> Unit,
    onShoppingListCreated: (String) -> Unit,
    viewModel: RecipeListViewModel = viewModel(factory = RecipeListViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val macros by viewModel.macros.collectAsState()

    Column(Modifier.fillMaxSize()) {

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search recipes and ingredients") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecipeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        if (state.selecting) {
            SelectionBar(
                count = state.selected.size,
                onClear = viewModel::clearSelection,
                onBuild = { viewModel.buildShoppingList(onShoppingListCreated) }
            )
        }

        if (state.recipes.isEmpty()) {
            EmptyState(
                title = if (state.query.isBlank()) "No recipes yet" else "Nothing matches that",
                body = if (state.query.isBlank())
                    "Tap Import and paste a recipe link, or share a page to Pantry from your browser."
                else
                    "Try a different word, or clear the filter."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.recipes, key = { it.recipe.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        macros = macros[recipe.recipe.id],
                        selected = recipe.recipe.id in state.selected,
                        selecting = state.selecting,
                        onClick = {
                            if (state.selecting) viewModel.toggleSelected(recipe.recipe.id)
                            else onOpenRecipe(recipe.recipe.id)
                        },
                        onLongClick = { viewModel.toggleSelected(recipe.recipe.id) },
                        onFavourite = { viewModel.toggleFavourite(recipe) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onBuild: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$count selected",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Clear",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.combinedClickable(onClick = onClear).padding(8.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Make one list",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = onBuild)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeWithIngredients,
    macros: RecipeMacros?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavourite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {

            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onLongClick() })
                Spacer(Modifier.width(4.dp))
            } else if (recipe.recipe.imageUrl != null) {
                AsyncImage(
                    model = recipe.recipe.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    recipe.recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append("${recipe.recipe.servings} servings")
                        append(" · ${recipe.ingredients.size} ingredients")
                        recipe.recipe.sourceName?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeChip(recipe.recipe.displayMinutes)
                    if (macros != null && macros.perServing.kcal > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${macros.perServing.kcal.roundToInt()} kcal / serving",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onFavourite) {
                Icon(
                    if (recipe.recipe.favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favourite",
                    tint = if (recipe.recipe.favourite) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
