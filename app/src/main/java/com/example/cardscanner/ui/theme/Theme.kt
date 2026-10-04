package com.example.cardscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Financial-app palette: deep indigo + gold accents on neutral surfaces.
private val Indigo900 = Color(0xFF1A237E)
private val Indigo700 = Color(0xFF303F9F)
private val Indigo500 = Color(0xFF3F51B5)
private val Gold = Color(0xFFFFD54F)
private val SurfaceLight = Color(0xFFF7F8FC)
private val SurfaceDark = Color(0xFF12142A)

private val LightColors = lightColorScheme(
    primary = Indigo700,
    onPrimary = Color.White,
    primaryContainer = Indigo500,
    onPrimaryContainer = Color.White,
    secondary = Gold,
    onSecondary = Color(0xFF1C1B1F),
    background = SurfaceLight,
    onBackground = Color(0xFF1C1B1F),
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8EAF6),
    onSurfaceVariant = Color(0xFF44475A),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8DA),
    onPrimary = Color(0xFF10122B),
    primaryContainer = Indigo900,
    onPrimaryContainer = Color(0xFFE8EAF6),
    secondary = Gold,
    onSecondary = Color(0xFF1C1B1F),
    background = SurfaceDark,
    onBackground = Color(0xFFE4E1EC),
    surface = SurfaceDark,
    onSurface = Color(0xFFE4E1EC),
    surfaceVariant = Color(0xFF2A2C4A),
    onSurfaceVariant = Color(0xFFC5C8DC),
    error = Color(0xFFF2B8B5),
)

@Composable
fun CardScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
