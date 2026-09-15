package com.triangle.app.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ring/donut chart — matches script.js's Tasks-by-Category ring (concentric
 * stroked circles via stroke-dasharray, 34px stroke on an r=80 circle in a
 * 200x200 box, i.e. inner radius ≈63/outer ≈97) ported to Canvas arcs.
 */
@Composable
fun DonutChart(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 28.dp,
    centerContent: @Composable BoxScope.() -> Unit = {}
) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = strokeWidth.toPx()
            val diameter = minOf(size.width, size.height) - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            if (total <= 0f) {
                drawArc(
                    Color.Gray.copy(alpha = 0.15f), 0f, 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(strokePx, cap = StrokeCap.Butt)
                )
            } else {
                var startAngle = -90f
                segments.forEach { (value, color) ->
                    val sweep = value / total * 360f
                    drawArc(
                        color, startAngle, sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(strokePx, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }
        centerContent()
    }
}
