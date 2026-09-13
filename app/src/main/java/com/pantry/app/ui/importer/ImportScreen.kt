package com.pantry.app.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ImportScreen(
    sharedUrl: String? = null,
    onSaved: (String) -> Unit,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) viewModel.startWith(sharedUrl)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Import a recipe", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Paste a link, or share a recipe page to Pantry from your browser. " +
                "Most recipe sites publish structured data that Pantry reads directly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.url,
            onValueChange = viewModel::setUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Recipe URL") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } }
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::fetch, enabled = !state.loading) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(0.dp))
                } else {
                    Icon(Icons.Default.Download, null)
                }
                Text("  Fetch recipe")
            }
            if (state.draft != null) {
                OutlinedButton(onClick = viewModel::reset) { Text("Start over") }
            }
        }

        state.warning?.let { warning ->
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    warning,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        state.duplicateOfId?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "You already saved this link. Saving again will update the existing recipe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val draft = state.draft ?: return@Column

        Spacer(Modifier.height(20.dp))
        state.strategy?.let {
            Text("Read via: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = draft.title,
            onValueChange = { v -> viewModel.updateDraft { it.copy(title = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Title") }
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Servings", draft.servings, Modifier.weight(1f)) { v ->
                viewModel.updateDraft { it.copy(servings = v) }
            }
            NumberField("Prep (min)", draft.prepMinutes, Modifier.weight(1f)) { v ->
                viewModel.updateDraft { it.copy(prepMinutes = v) }
            }
            NumberField("Cook (min)", draft.cookMinutes, Modifier.weight(1f)) { v ->
                viewModel.updateDraft { it.copy(cookMinutes = v) }
            }
        }
        Spacer(Modifier.height(8.dp))

        NumberField("Total time (min), if different", draft.totalMinutes, Modifier.fillMaxWidth()) { v ->
            viewModel.updateDraft { it.copy(totalMinutes = v) }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = draft.ingredientsText,
            onValueChange = { v -> viewModel.updateDraft { it.copy(ingredientsText = v) } },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            label = { Text("Ingredients, one per line") },
            supportingText = { Text("Quantities are parsed from these lines for shopping lists and macros.") }
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = draft.stepsText,
            onValueChange = { v -> viewModel.updateDraft { it.copy(stepsText = v) } },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            label = { Text("Method, one step per line") }
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.save(onSaved) },
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.title.isNotBlank()
        ) { Text(if (state.duplicateOfId != null) "Update saved recipe" else "Save to catalogue") }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
