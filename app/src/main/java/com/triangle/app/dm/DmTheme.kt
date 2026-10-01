package com.triangle.app.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.triangle.app.ui.theme.triangleDarkTheme

/**
 * Design tokens for the DM screens — brand-purple accent (was teal, script.js NOTIF_META.chat_message color `#14b8a6`)
 * NOTIF_META.chat_message color (`#14b8a6`, script.js:4692), same
 * "hardcoded tokens per screen" pattern as ProfileTheme.kt.
 */
object DmColors {
    val Accent = Color(0xFF6D5EF5) // Triangle brand purple
    /** Soft translucent purple (not solid), so my messages read as tinted rather than filled. */
    val MyBubble = Color(0xFF6D5EF5).copy(alpha = 0.18f)
    val TheirBubbleDark = Color(0xFF2A3242)
    val TheirBubbleLight = Color(0xFFE5E7EB)

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

data class DmPalette(
    val surface2: Color,
    val border: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val theirBubble: Color
)

@Composable
@ReadOnlyComposable
fun dmPalette(): DmPalette {
    val dark = triangleDarkTheme()
    return if (dark) {
        DmPalette(DmColors.SurfaceDark, DmColors.BorderDark, DmColors.TextDark, DmColors.Text2Dark, DmColors.Text3Dark, DmColors.TheirBubbleDark)
    } else {
        DmPalette(DmColors.SurfaceLight, DmColors.BorderLight, DmColors.TextLight, DmColors.Text2Light, DmColors.Text3Light, DmColors.TheirBubbleLight)
    }
}
