package com.pantry.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Herb = Color(0xFF2F5D3A)
private val HerbLight = Color(0xFF5B8C63)
private val Terracotta = Color(0xFFB4562F)
private val Parchment = Color(0xFFFBF8F3)

private val LightScheme = lightColorScheme(
    primary = Herb,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE6D2),
    onPrimaryContainer = Color(0xFF0C2214),
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCD),
    onSecondaryContainer = Color(0xFF3A1204),
    background = Parchment,
    onBackground = Color(0xFF1B1C19),
    surface = Parchment,
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFE0E5DC),
    onSurfaceVariant = Color(0xFF444842)
)

private val DarkScheme = darkColorScheme(
    primary = HerbLight,
    onPrimary = Color(0xFF12351D),
    primaryContainer = Color(0xFF1F4A2B),
    onPrimaryContainer = Color(0xFFCDE6D2),
    secondary = Color(0xFFFFB59A),
    onSecondary = Color(0xFF5A230D),
    background = Color(0xFF12140F),
    onBackground = Color(0xFFE3E3DC),
    surface = Color(0xFF12140F),
    onSurface = Color(0xFFE3E3DC),
    surfaceVariant = Color(0xFF444842),
    onSurfaceVariant = Color(0xFFC4C9BF)
)

@Composable
fun PantryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
