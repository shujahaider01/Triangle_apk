package com.triangle.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same accent/wordmark colors as the splash screen + auth header in
// script.js/style.css (the orange bolt badge, "Triangle" wordmark).
val TriangleOrange = Color(0xFFE85D26)
val TriangleOrangeDark = Color(0xFFC24710)

private val LightColors = lightColorScheme(
    primary = TriangleOrange,
    onPrimary = Color.White,
    secondary = TriangleOrangeDark,
    background = Color(0xFFFAFAFA),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = TriangleOrange,
    onPrimary = Color.White,
    secondary = TriangleOrangeDark,
    background = Color(0xFF1A1D23),
    surface = Color(0xFF20242C)
)

@Composable
fun TriangleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
