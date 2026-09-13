package com.pantry.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pantry.app.data.db.MealSlot
import com.pantry.app.data.db.PlannedMeal
import com.pantry.app.importer.Iso8601Duration
import com.pantry.app.ui.common.EmptyState
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onOpenRecipe: (String) -> Unit,
    onShoppingListCreated: (String) -> Unit,
    viewModel: MealPlanViewModel = viewModel(factory = MealPlanViewModel.Factory)
) {
    val weekStart by viewModel.weekStart.collectAsState()
    val days by viewModel.days.collectAsState()
    val catalogue by viewModel.catalogue.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var picking by remember { mutableStateOf<Pair<LocalDate, MealSlot>?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Week navigator
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::previousWeek) { Icon(Icons.Default.ChevronLeft, "Previous week") }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Week of ${weekStart.dayOfMonth} ${weekStart.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val weekKcal = days.sumOf { it.macros.kcal }
                    if (weekKcal > 0) {
                        Text(
                            "${weekKcal.roundToInt()} kcal planned this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = viewModel::nextWeek) { Icon(Icons.Default.ChevronRight, "Next week") }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { viewModel.autoFill(replaceExisting = false, servings = 2) }, enabled = !busy) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Text("  Plan my week")
                }
                OutlinedButton(onClick = { viewModel.shopForWeek(onShoppingListCreated) }) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp))
                    Text("  Shop for week")
                }
                TextButton(onClick = viewModel::thisWeek) { Text("Today") }
                TextButton(onClick = viewModel::clearWeek) { Text("Clear") }
            }

            // Feature 7 on the planning screen.
            val seasonal = viewModel.inSeasonNow
            if (seasonal.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("At its best this month", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            seasonal.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (catalogue.isEmpty()) {
                EmptyState(
                    "Nothing to plan with yet",
                    "Import a few recipes and Pantry can lay out a week for you, favouring what is in season."
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(days, key = { it.date.toEpochDay() }) { day ->
                    DayCard(
                        day = day,
                        onOpenRecipe = onOpenRecipe,
                        onRemove = viewModel::remove,
                        onAdd = { slot -> picking = day.date to slot }
                    )
                }
            }
        }
    }

    picking?.let { (date, slot) ->
        RecipePickerDialog(
            title = "${slot.name.lowercase().replaceFirstChar { it.uppercase() }} on ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}",
            recipes = catalogue.map { it.recipe.id to it.recipe.title },
            onDismiss = { picking = null },
            onPick = { id ->
                viewModel.add(date, slot, id, 2)
                picking = null
            }
        )
    }
}

@Composable
private fun DayCard(
    day: DaySummary,
    onOpenRecipe: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onAdd: (MealSlot) -> Unit
) {
    val isToday = day.date == LocalDate.now()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${day.date.dayOfMonth}/${day.date.monthValue}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (day.macros.kcal > 0) {
                    Text(
                        "${day.macros.kcal.roundToInt()} kcal · ${Iso8601Duration.format(day.totalMinutes)} cooking",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            MealSlot.entries.forEach { slot ->
                val mealsInSlot = day.meals.filter { it.entry.slot == slot }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        slot.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(72.dp)
                    )
                    if (mealsInSlot.isEmpty()) {
                        Text(
                            "+ add",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onAdd(slot) }.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(Modifier.weight(1f)) {
                            mealsInSlot.forEach { meal -> MealRow(meal, onOpenRecipe, onRemove) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealRow(meal: PlannedMeal, onOpenRecipe: (String) -> Unit, onRemove: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable { onOpenRecipe(meal.recipe.id) }
                .padding(vertical = 2.dp)
        ) {
            Text(meal.recipe.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "${meal.entry.servings} servings · ${Iso8601Duration.format(meal.recipe.displayMinutes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onRemove(meal.entry.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RecipePickerDialog(
    title: String,
    recipes: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (recipes.isEmpty()) {
                    Text("No recipes saved yet.")
                }
                recipes.forEach { (id, name) ->
                    Text(
                        name,
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
