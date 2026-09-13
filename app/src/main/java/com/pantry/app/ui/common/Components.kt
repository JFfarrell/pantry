package com.pantry.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantry.app.domain.Macros
import com.pantry.app.importer.Iso8601Duration
import com.pantry.app.seasonality.SeasonAdvice
import com.pantry.app.seasonality.SeasonStatus
import kotlin.math.roundToInt

val ProteinColour = Color(0xFF3E6C9B)
val CarbColour = Color(0xFFD79A3C)
val FatColour = Color(0xFFB4562F)

/** Feature 8: cooking time, shown wherever a recipe is. */
@Composable
fun TimeChip(minutes: Int?, modifier: Modifier = Modifier) {
    if (minutes == null || minutes <= 0) return
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = { Text(Iso8601Duration.format(minutes), fontSize = 12.sp) },
        leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
fun SeasonChip(advice: SeasonAdvice, modifier: Modifier = Modifier) {
    val (label, colour) = when (advice.status) {
        SeasonStatus.IN_SEASON -> "In season" to Color(0xFF2E7D32)
        SeasonStatus.SHOULDER -> "Edge of season" to Color(0xFF9A6B00)
        SeasonStatus.OUT_OF_SEASON -> "Out of season" to MaterialTheme.colorScheme.error
        else -> return
    }
    Text(
        text = label,
        color = colour,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colour.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Proportional bar of energy from protein / carbs / fat. */
@Composable
fun MacroSplitBar(macros: Macros, modifier: Modifier = Modifier) {
    val (p, c, f) = macros.energySplit()
    if (p + c + f <= 0.0) return
    Row(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (p > 0) Box(Modifier.weight(p.toFloat()).fillMaxWidth().background(ProteinColour))
        if (c > 0) Box(Modifier.weight(c.toFloat()).fillMaxWidth().background(CarbColour))
        if (f > 0) Box(Modifier.weight(f.toFloat()).fillMaxWidth().background(FatColour))
    }
}

@Composable
fun MacroLegend(macros: Macros, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendEntry("Protein", macros.proteinG, ProteinColour)
        LegendEntry("Carbs", macros.carbsG, CarbColour)
        LegendEntry("Fat", macros.fatG, FatColour)
    }
}

@Composable
private fun LegendEntry(label: String, grams: Double, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colour))
        Spacer(Modifier.width(6.dp))
        Text("$label ${grams.roundToInt()} g", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MacroHeadline(macros: Macros, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${macros.kcal.roundToInt()}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(" kcal", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        MacroSplitBar(macros)
        Spacer(Modifier.height(8.dp))
        MacroLegend(macros)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
