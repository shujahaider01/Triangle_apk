package com.triangle.app.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single-series vertical bar chart — matches script.js's Weekly Productivity
 * card (`.np2-wp-bar-track`/`.np2-wp-bar-fill`, top-rounded bars). Built with
 * plain Row/Box + fillMaxHeight(fraction) rather than Canvas, since bars are
 * just rectangles — simpler and gets free Compose layout/click-target
 * behavior for free.
 *
 * [labels] (e.g. "W1"/"W2"/…) render under each bar and [valueLabels] (e.g.
 * "40%") render above it — both optional so callers with unlabeled data
 * aren't forced to pass anything, but every bar chart in the app should
 * supply them: an unlabeled bar tells you nothing about which bucket or
 * value it represents.
 */
@Composable
fun BarChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValueOverride: Float? = null,
    labels: List<String>? = null,
    valueLabels: List<String>? = null
) {
    val maxV = (maxValueOverride ?: values.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { i, v ->
                val fraction = (v / maxV).coerceIn(0f, 1f)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    valueLabels?.getOrNull(i)?.let {
                        Text(it, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 5.dp)
                            .weight(1f, fill = false)
                            .fillMaxHeight(fraction.coerceAtLeast(0.015f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(color)
                    )
                }
            }
        }
        if (labels != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                labels.forEach { label ->
                    Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
