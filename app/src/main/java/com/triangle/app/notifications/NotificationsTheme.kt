package com.triangle.app.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.triangle.app.ui.theme.triangleDarkTheme

data class NotificationsPalette(
    val surface2: Color,
    val text: Color,
    val text2: Color,
    val text3: Color
)

@Composable
@ReadOnlyComposable
fun notificationsPalette(): NotificationsPalette {
    val dark = triangleDarkTheme()
    return if (dark) {
        NotificationsPalette(Color(0xFF1E2738), Color(0xFFFFFFFF), Color(0xFFA1A1AA), Color(0xFF6B7280))
    } else {
        NotificationsPalette(Color(0xFFF3F4F6), Color(0xFF111827), Color(0xFF6B7280), Color(0xFF9CA3AF))
    }
}
