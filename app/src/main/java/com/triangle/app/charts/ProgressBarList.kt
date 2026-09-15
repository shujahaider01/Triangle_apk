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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ProgressBarItem(val label: String, val trailingText: String, val fraction: Float, val color: Color)

/**
 * Labeled horizontal progress-bar rows — matches Task Status Distribution
 * and Top Strengths (both are "label + fill bar", not a chart per se, in
 * the source).
 */
@Composable
fun ProgressBarList(items: List<ProgressBarItem>, modifier: Modifier = Modifier, barHeight: androidx.compose.ui.unit.Dp = 8.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { item ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(item.trailingText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(barHeight / 2))
                        .background(item.color.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(barHeight / 2))
                            .background(item.color)
                    )
                }
            }
        }
    }
}
