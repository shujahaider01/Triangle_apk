package com.triangle.app.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Single-series vertical bar chart — matches script.js's Weekly Productivity
 * card (`.np2-wp-bar-track`/`.np2-wp-bar-fill`, top-rounded bars). Built with
 * plain Row/Box + fillMaxHeight(fraction) rather than Canvas, since bars are
 * just rectangles — simpler and gets free Compose layout/click-target
 * behavior for free.
 */
@Composable
fun BarChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValueOverride: Float? = null
) {
    val maxV = (maxValueOverride ?: values.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it }
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            val fraction = (v / maxV).coerceIn(0f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 5.dp)
                    .fillMaxHeight(fraction.coerceAtLeast(0.015f))
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(color)
            )
        }
    }
}
