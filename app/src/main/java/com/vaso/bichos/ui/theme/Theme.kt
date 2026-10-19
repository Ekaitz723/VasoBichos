package com.vaso.bichos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Tema oscuro fijo, tipo menú de RPG. */
private val GameColorScheme = darkColorScheme(
    primary = Color(0xFFFFC857),
    onPrimary = Color(0xFF2A1B00),
    secondary = Color(0xFF7FD1FF),
    tertiary = Color(0xFFFF7A90),
    background = Color(0xFF14121F),
    onBackground = Color(0xFFEDE7FF),
    surface = Color(0xFF1E1B2E),
    onSurface = Color(0xFFEDE7FF),
    surfaceVariant = Color(0xFF2A2640),
    onSurfaceVariant = Color(0xFFB9B2D6),
    outline = Color(0xFF5B5480),
    error = Color(0xFFFF6B6B),
)

@Composable
fun VasoBichosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GameColorScheme, typography = Typography, content = content)
}
