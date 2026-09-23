package com.triangle.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

private val ConfettiColors = listOf(
    Color(0xFFFF6B9D), Color(0xFFFFD23F), Color(0xFF4FC3F7), Color(0xFFB388FF),
    Color(0xFF69F0AE), Color(0xFFFF9E40), Color(0xFF5C6BC0)
)

private class Piece(
    val x: Float, val delay: Float, val speed: Float, val size: Float,
    val color: Color, val round: Boolean, val spin: Float, val sway: Float, val phase: Float
)

/** Small falling confetti dots/flakes across the whole screen; replays each time [trigger] changes (0 = idle). */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val pieces = remember(trigger) {
        val r = Random(trigger * 7919L + System.nanoTime())
        List(70) {
            Piece(
                x = r.nextFloat(), delay = r.nextFloat() * 0.35f, speed = 0.75f + r.nextFloat() * 0.5f,
                size = 4f + r.nextFloat() * 6f, color = ConfettiColors[r.nextInt(ConfettiColors.size)],
                round = r.nextBoolean(), spin = (r.nextFloat() - 0.5f) * 720f,
                sway = 10f + r.nextFloat() * 24f, phase = r.nextFloat() * 6.28f
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1700, easing = LinearEasing))
    }
    if (trigger == 0 || progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        pieces.forEach { c ->
            val t = ((p - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val y = -20f + (size.height * 0.85f) * (t * c.speed)
            val x = c.x * size.width + sin(t * 9f + c.phase) * c.sway
            val alpha = if (t > 0.7f) (1f - t) / 0.3f else 1f
            val color = c.color.copy(alpha = alpha.coerceIn(0f, 1f))
            if (c.round) drawCircle(color, c.size / 2f * density.coerceAtLeast(1f), Offset(x, y))
            else rotate(c.spin * t, Offset(x, y)) {
                val w = c.size * density.coerceAtLeast(1f)
                drawRect(color, Offset(x - w / 2f, y - w / 4f), Size(w, w / 2f))
            }
        }
    }
}
