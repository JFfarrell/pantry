package com.pantry.app.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pantry.app.data.db.MealSlot
import com.pantry.app.domain.Quantity
import com.pantry.app.importer.Iso8601Duration
import com.pantry.app.nutrition.IngredientMacros
import com.pantry.app.nutrition.toQuantity
import com.pantry.app.seasonality.SeasonStatus
import com.pantry.app.ui.common.MacroHeadline
import com.pantry.app.ui.common.SeasonChip
import com.pantry.app.ui.common.TimeChip
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onBack: () -> Unit,
    onShoppingListCreated: (String) -> Unit,
    viewModel: RecipeDetailViewModel = viewModel(factory = RecipeDetailViewModel.factory(recipeId))
) {
    val recipe by viewModel.recipe.collectAsState()
    val macros by viewModel.macros.collectAsState()
    val seasonality by viewModel.seasonality.collectAsState()
    val servings by viewModel.servings.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var showPlanDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMacroDetail by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val loaded = recipe

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(loaded?.recipe?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavourite) {
                        Icon(
                            if (loaded?.recipe?.favourite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favourite"
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            )
        }
    ) { padding ->

        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("Loading...") }
            return@Scaffold
        }

        val scale = viewModel.scale()

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            loaded.recipe.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }

            Column(Modifier.padding(16.dp)) {

                // --- Feature 8: time, spelled out ---
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeChip(loaded.recipe.displayMinutes)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        loaded.recipe.prepMinutes?.let { append("Prep ${Iso8601Duration.format(it)}") }
                        loaded.recipe.cookMinutes?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append("Cook ${Iso8601Duration.format(it)}")
                        }
                        loaded.recipe.sourceName?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                loaded.recipe.description?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(16.dp))
                ServingsStepper(servings ?: loaded.recipe.servings, viewModel::setServings)

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.addToShoppingList(onShoppingListCreated) }) {
                        Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp))
                        Text("  Shopping list")
                    }
                    OutlinedButton(onClick = { showPlanDialog = true }) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                        Text("  Add to plan")
                    }
                }

                // --- Feature 5: macros ---
                Spacer(Modifier.height(24.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        MacroHeadline(
                            macros = macros.perServing,
                            caption = "per serving"
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Whole recipe: ${macros.perRecipe.kcal.roundToInt()} kcal  ·  " +
                                "fibre ${macros.perServing.fibreG.roundToInt()} g  ·  " +
                                "sugar ${macros.perServing.sugarG.roundToInt()} g  ·  " +
                                "salt ${"%.1f".format(macros.perServing.saltG)} g",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (macros.coverage < 1f) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { macros.coverage },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Based on ${macros.covered} of ${macros.lines.size} ingredients. " +
                                    "Anything without a match is left out rather than guessed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showMacroDetail = !showMacroDetail }) {
                            Text(if (showMacroDetail) "Hide breakdown" else "Per-ingredient breakdown")
                        }

                        if (showMacroDetail) {
                            HorizontalDivider()
                            macros.lines.forEach { line -> MacroLine(line) }
                        }
                    }
                }

                // --- Ingredients, with feature 7 seasonality ---
                Spacer(Modifier.height(24.dp))
                Text("Ingredients", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                loaded.ingredients.sortedBy { it.position }.forEach { ingredient ->
                    val advice = seasonality.firstOrNull { it.ingredientName == ingredient.name }
                    val quantity = ingredient.toQuantity()?.let { Quantity(it.amount * scale, it.unit) }

                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            text = quantity?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(88.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(ingredient.name, style = MaterialTheme.typography.bodyMedium)
                            ingredient.note?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (advice != null && advice.status != SeasonStatus.YEAR_ROUND) {
                                Spacer(Modifier.height(2.dp))
                                SeasonChip(advice)
                            }
                        }
                    }
                }

                val outOfSeason = seasonality.filter { it.status == SeasonStatus.OUT_OF_SEASON }
                if (outOfSeason.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Seasonal notes", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            outOfSeason.forEach {
                                Text(
                                    "${it.ingredientName}: ${it.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // --- Method ---
                if (loaded.recipe.steps.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("Method", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    loaded.recipe.steps.forEachIndexed { index, step ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                loaded.recipe.sourceUrl?.let {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Source: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (showPlanDialog) {
        AddToPlanDialog(
            onDismiss = { showPlanDialog = false },
            onPick = { date, slot ->
                viewModel.addToPlan(date, slot)
                showPlanDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this recipe?") },
            text = { Text("It will also disappear from any meal plan it is in. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete(onBack) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun ServingsStepper(servings: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Servings", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = { onChange(servings - 1) }) { Icon(Icons.Default.Remove, "Fewer") }
        Text("$servings", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(servings + 1) }) { Icon(Icons.Default.Add, "More") }
        Spacer(Modifier.weight(1f))
        Text(
            "Quantities and macros rescale",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MacroLine(line: IngredientMacros) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(line.ingredient.name, style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    line.macros == null && line.grams == null -> "No quantity given, so not counted"
                    line.macros == null -> "No nutrition data found"
                    !line.confidentWeight -> "${line.grams?.roundToInt()} g (estimated weight)"
                    else -> "${line.grams?.roundToInt()} g"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            line.macros?.let { "${it.kcal.roundToInt()} kcal" } ?: "--",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AddToPlanDialog(onDismiss: () -> Unit, onPick: (LocalDate, MealSlot) -> Unit) {
    var slot by remember { mutableStateOf(MealSlot.DINNER) }
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to meal plan") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlot.entries.forEach { option ->
                        val selected = slot == option
                        Text(
                            option.name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { slot = option }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Which day?", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                (0..13).forEach { offset ->
                    val date = today.plusDays(offset.toLong())
                    Text(
                        text = when (offset) {
                            0 -> "Today"
                            1 -> "Tomorrow"
                            else -> "${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}/${date.monthValue}"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(date, slot) }
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
