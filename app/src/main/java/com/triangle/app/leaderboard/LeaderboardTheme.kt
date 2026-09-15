package com.triangle.app.leaderboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** Rank-medal colors ported verbatim from script.js's inline hex (script.js:13331-13397). */
object LeaderboardColors {
    val Gold = Color(0xFFFFD700)
    val Silver = Color(0xFFC0C0C0)
    val Bronze = Color(0xFFCD7F32)
    val Accent = Color(0xFF7C3AED)
}

data class LeaderboardPalette(val surface2: Color, val text: Color, val text2: Color, val text3: Color)

@Composable
@ReadOnlyComposable
fun leaderboardPalette(): LeaderboardPalette {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        LeaderboardPalette(Color(0xFF1E2738), Color(0xFFFFFFFF), Color(0xFFA1A1AA), Color(0xFF6B7280))
    } else {
        LeaderboardPalette(Color(0xFFF3F4F6), Color(0xFF111827), Color(0xFF6B7280), Color(0xFF9CA3AF))
    }
}

fun medalColor(rank: Int): Color? = when (rank) {
    1 -> LeaderboardColors.Gold
    2 -> LeaderboardColors.Silver
    3 -> LeaderboardColors.Bronze
    else -> null
}
