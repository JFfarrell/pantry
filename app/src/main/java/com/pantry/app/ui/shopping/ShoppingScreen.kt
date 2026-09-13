package com.pantry.app.ui.shopping

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pantry.app.tesco.TescoBasketActivity
import com.pantry.app.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    listId: String? = null,
    viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val tescoQueue by viewModel.tescoQueue.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var newItem by remember { mutableStateOf("") }

    // A finished run reports back what you marked as added, and those get ticked
    // off here. Nothing is ticked unless you asked for it on the summary sheet.
    val tescoRun = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val added = result.data
            ?.getStringArrayListExtra(TescoBasketActivity.EXTRA_ADDED)
            .orEmpty()
        if (added.isNotEmpty()) viewModel.markAdded(added)
    }

    LaunchedEffect(listId) { if (listId != null) viewModel.showList(listId) }

    LaunchedEffect(tescoQueue) {
        val queue = tescoQueue ?: return@LaunchedEffect
        viewModel.consumeTescoQueue()
        if (queue.size == 0) {
            snackbar.showSnackbar("Everything on this list is already ticked off.")
        } else {
            tescoRun.launch(
                Intent(context, TescoBasketActivity::class.java)
                    .putStringArrayListExtra(TescoBasketActivity.EXTRA_ITEMS, ArrayList(queue.names))
                    .putStringArrayListExtra(TescoBasketActivity.EXTRA_QUANTITIES, ArrayList(queue.quantities))
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (state.lists.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.lists.forEach { list ->
                        FilterChip(
                            selected = list.id == state.activeList?.id,
                            onClick = { viewModel.showList(list.id) },
                            label = { Text(list.name, maxLines = 1) }
                        )
                    }
                }
            }

            val active = state.activeList
            if (active == null || state.total == 0) {
                EmptyState(
                    "No shopping list yet",
                    "Open a recipe and tap Shopping list, tick several recipes in the catalogue to combine them, " +
                        "or use Shop for week on the plan."
                )
                return@Column
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(active.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${state.remaining} of ${state.total} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (state.total == 0) 0f else (state.total - state.remaining).toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = viewModel::prepareTescoRun) {
                    Icon(Icons.Default.ShoppingBasket, null, Modifier.size(18.dp))
                    Text("  Fill Tesco basket")
                }
                OutlinedButton(onClick = {
                    copyToClipboard(context, viewModel.asPlainText(state))
                }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                    Text("  Copy")
                }
                TextButton(onClick = viewModel::deleteActiveList) { Text("Delete list") }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add something else") },
                    singleLine = true
                )
                IconButton(onClick = {
                    viewModel.addManual(newItem)
                    newItem = ""
                }) { Icon(Icons.Default.Add, "Add item") }
            }

            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                state.itemsByAisle.forEach { (aisle, rows) ->
                    item(key = "header-$aisle") {
                        Text(
                            aisle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(rows.size, key = { index -> rows[index].id }) { index ->
                        val row = rows[index]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.checked,
                                onCheckedChange = { viewModel.setChecked(row.id, it) }
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setChecked(row.id, !row.checked) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    row.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (row.checked) FontWeight.Normal else FontWeight.Medium,
                                    textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                                    color = if (row.checked) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                val detail = listOfNotNull(
                                    row.displayQuantity.ifBlank { null },
                                    row.fromRecipes.ifBlank { null }
                                ).joinToString("  ·  ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteItem(row.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Remove", Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Shopping list", text))
}
