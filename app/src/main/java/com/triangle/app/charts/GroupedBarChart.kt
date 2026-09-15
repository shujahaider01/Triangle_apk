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

/** Paired 2-series vertical bar chart — matches Coins Earned vs Spent. */
@Composable
fun GroupedBarChart(
    groups: List<Pair<Float, Float>>,
    color1: Color,
    color2: Color,
    modifier: Modifier = Modifier,
    maxValueOverride: Float? = null
) {
    val maxV = (maxValueOverride ?: groups.flatMap { listOf(it.first, it.second) }.maxOrNull() ?: 0f)
        .let { if (it <= 0f) 1f else it }
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        groups.forEach { (a, b) ->
            Row(
                Modifier.weight(1f).padding(horizontal = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((a / maxV).coerceIn(0f, 1f).coerceAtLeast(0.015f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(color1)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((b / maxV).coerceIn(0f, 1f).coerceAtLeast(0.015f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(color2)
                )
            }
        }
    }
}
