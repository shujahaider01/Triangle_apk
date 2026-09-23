package com.triangle.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val HighlightBlue = Color(0xFF3B82F6)

/**
 * The translucent blue band (with a solid edge bar at each end) that marks the row a
 * notification tap brought you to. Drawn behind the content and bled [bleed] past each side
 * so it reads as a full-width strip even though the row itself sits inside list padding;
 * fades in quickly and out slowly when [active] flips false.
 */
fun Modifier.deepLinkHighlight(active: Boolean, bleed: Dp = 12.dp): Modifier = composed {
    val alpha by animateFloatAsState(if (active) 1f else 0f, animationSpec = tween(if (active) 250 else 900), label = "deepLinkHighlight")
    drawBehind {
        if (alpha > 0.01f) {
            val b = bleed.toPx()
            val v = 4.dp.toPx()
            val bar = 3.dp.toPx()
            val top = -v
            val height = size.height + 2 * v
            drawRect(HighlightBlue.copy(alpha = 0.22f * alpha), Offset(-b, top), Size(size.width + 2 * b, height))
            drawRect(HighlightBlue.copy(alpha = 0.9f * alpha), Offset(-b, top), Size(bar, height))
            drawRect(HighlightBlue.copy(alpha = 0.9f * alpha), Offset(size.width + b - bar, top), Size(bar, height))
        }
    }
}
