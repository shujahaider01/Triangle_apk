package com.triangle.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.triangle.app.ui.theme.triangleDarkTheme

/**
 * Design tokens ported 1:1 from style.css's `--np2-*` custom properties
 * (renderInternProfile's own dark/light palette — see style.css:10990-11025)
 * — same "hardcoded tokens per screen" pattern DashboardScreen.kt already
 * uses, not MaterialTheme colors, so this looks like the same app.
 */
object ProfileColors {
    val Purple = Color(0xFF7C3AED)

    val SurfaceDark = Color(0xFF1E2738)
    val BorderDark = Color(0xFF2A3242)
    val TextDark = Color(0xFFFFFFFF)
    val Text2Dark = Color(0xFFA1A1AA)
    val Text3Dark = Color(0xFF6B7280)

    val SurfaceLight = Color(0xFFF3F4F6)
    val BorderLight = Color(0xFFE5E7EB)
    val TextLight = Color(0xFF111827)
    val Text2Light = Color(0xFF6B7280)
    val Text3Light = Color(0xFF9CA3AF)
}

data class ProfilePalette(
    val surface2: Color,
    val border: Color,
    val text: Color,
    val text2: Color,
    val text3: Color
)

@Composable
@ReadOnlyComposable
fun profilePalette(): ProfilePalette {
    val dark = triangleDarkTheme()
    return if (dark) {
        ProfilePalette(ProfileColors.SurfaceDark, ProfileColors.BorderDark, ProfileColors.TextDark, ProfileColors.Text2Dark, ProfileColors.Text3Dark)
    } else {
        ProfilePalette(ProfileColors.SurfaceLight, ProfileColors.BorderLight, ProfileColors.TextLight, ProfileColors.Text2Light, ProfileColors.Text3Light)
    }
}
