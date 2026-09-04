package com.airsoft.tracker.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color.White,
    secondary = Color(0xFF66BB6A),
    tertiary = Color(0xFF81C784),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceVariant = Color(0xFF263238),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    secondary = Color(0xFF388E3C),
    tertiary = Color(0xFF43A047),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    onSurfaceVariant = Color(0xFF616161),
)

@Composable
fun AirsoftTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}