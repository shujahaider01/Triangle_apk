package com.triangle.app.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Hand-built line/area chart (no charting library — matches script.js's own
 * "raw SVG polyline + linearGradient polygon fill" approach, ported to
 * Compose Canvas). Covers Performance Trend, Overdue Tasks Timeline, and
 * Yearly Performance from renderInternProfile()'s Analytics tab.
 */
@Composable
fun LineAreaChart(
    points: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    maxValueOverride: Float? = null,
    showDots: Boolean = true,
    showGridLines: Boolean = false
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val maxV = (maxValueOverride ?: points.max()).let { if (it <= 0f) 1f else it }
        val n = points.size
        val stepX = size.width / (n - 1)
        fun toY(v: Float): Float = size.height - (v.coerceAtLeast(0f) / maxV) * size.height

        if (showGridLines) {
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                val y = size.height * (1 - f)
                drawLine(
                    Color.Gray.copy(alpha = 0.15f),
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.5f
                )
            }
        }

        val linePath = Path()
        val areaPath = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = toY(v)
            if (i == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, size.height)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo((n - 1) * stepX, size.height)
        areaPath.close()

        drawPath(
            areaPath,
            brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0f)))
        )
        drawPath(
            linePath,
            color = lineColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        if (showDots) {
            points.forEachIndexed { i, v ->
                val center = Offset(i * stepX, toY(v))
                drawCircle(lineColor, radius = 6f, center = center)
                drawCircle(Color.White, radius = 3f, center = center)
            }
        }
    }
}
