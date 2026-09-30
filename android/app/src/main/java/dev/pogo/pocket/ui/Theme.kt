package dev.pogo.pocket.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF6B5E00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF176),
    onPrimaryContainer = Color(0xFF3E3500),
    secondary = Color(0xFF8A6A2A),
    background = Color(0xFFFFFBF0),
    surface = Color(0xFFFFFBF0),
    surfaceContainer = Color(0xFFF6F0DE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE3D34A),
    onPrimary = Color(0xFF383000),
    primaryContainer = Color(0xFF524700),
    onPrimaryContainer = Color(0xFFFFF176),
    secondary = Color(0xFFD9C08A),
    background = Color(0xFF1C1B18),
    surface = Color(0xFF1C1B18),
    surfaceContainer = Color(0xFF282620),
)

@Composable
fun PogoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
