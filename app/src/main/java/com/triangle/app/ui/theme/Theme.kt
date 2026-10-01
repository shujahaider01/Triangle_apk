package com.triangle.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Same accent/wordmark colors as the splash screen + auth header in
// script.js/style.css (the orange bolt badge, "Triangle" wordmark).
val TriangleOrange = Color(0xFFE85D26)
val TriangleOrangeDark = Color(0xFFC24710)

// style.css overrides `--accent` to `--brand` (this purple) specifically on
// the individual/intern Tasks page (`body[data-page="internTasks"] { --accent:
// var(--brand); }`) — every accent element there (status pills, category
// pills, date-strip ring) reads brand purple, not the global orange. Shared
// here so Dashboard and Tasks/Habits reference the same constant instead of
// duplicating the hex literal.
val TriangleBrandPurple = Color(0xFF6D5EF5)
val TriangleBrandPurpleLight = Color(0xFF8B7CF6)
val TriangleBrandPurpleDark = Color(0xFF5648D6)

// The same [data-page="internDashboard"]/[data-page="internTasks"] page-background
// gradient (orange -> pink -> purple -> blue) both Dashboard and Tasks/Habits sit
// on in the source — shared here rather than each screen keeping its own copy.
val TrianglePageGradientLight = listOf(Color(0xFFFBEEE9), Color(0xFFF3E6EE), Color(0xFFE9E6F5), Color(0xFFE1E4F5))
val TrianglePageBgDark = Color(0xFF141414)

// Shared "secondary text" / dark-mode card colors — originally Dashboard-
// only, now also backing the bottom nav bar (shared across Dashboard/
// Tasks/Profile, see navigation/AppBottomNav.kt) so every screen
// that shows it renders identical colors instead of each keeping its own copy.
val TriangleCardBgDark = Color(0xFF1C1C1E)
val TriangleText2Light = Color(0xFF6B7280)
val TriangleText2Dark = Color(0xFF8B92A5)

private val LightColors = lightColorScheme(
    primary = TriangleBrandPurple,
    onPrimary = Color.White,
    secondary = TriangleBrandPurpleDark,
    background = Color(0xFFFAFAFA),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = TriangleBrandPurple,
    onPrimary = Color.White,
    secondary = TriangleBrandPurpleDark,
    background = Color(0xFF1A1D23),
    surface = Color(0xFF20242C)
)

// Resolved light/dark state for the whole app — set once at the root by
// TriangleTheme, based on the user's Settings > Appearance choice (System/
// Light/Dark, see data/ThemeStore.kt), not always the raw OS setting. Every
// screen that needs to branch its own custom colors (most don't go through
// MaterialTheme.colorScheme directly — see the many local "val dark = ..."
// call sites) reads this via triangleDarkTheme() instead of calling
// isSystemInDarkTheme() itself, so a manual override actually takes effect
// everywhere instead of only re-tinting MaterialTheme's own colorScheme.
private val LocalTriangleDarkTheme = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun triangleDarkTheme(): Boolean = LocalTriangleDarkTheme.current

@Composable
fun TriangleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTriangleDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
