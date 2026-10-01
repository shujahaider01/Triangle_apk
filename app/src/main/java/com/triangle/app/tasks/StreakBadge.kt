package com.triangle.app.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Streak lengths that unlock a badge, in order. */
val STREAK_MILESTONES = listOf(1, 7, 14, 30, 50, 100, 200, 365, 730)

private class BadgePalette(
    val blob: Color,
    val outer: Color,
    val inner: Color,
    val emblem: Color,
    val ribbonA: Color,
    val ribbonB: Color,
    val accent: Color
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

// One palette per milestone, in STREAK_MILESTONES order.
private val PALETTES = listOf(
    BadgePalette(c(0xF6C343), c(0xE8913A), c(0xF2B15A), c(0xFFFFFF), c(0xE8582B), c(0xE8582B), c(0xD9822B)),
    BadgePalette(c(0x9BC53D), c(0x8A97B5), c(0xB9C3DA), c(0xFFFFFF), c(0x2E5C9A), c(0x2E5C9A), c(0x5C7A29)),
    BadgePalette(c(0x4DD0A0), c(0xE87722), c(0xF9A24A), c(0xFFFFFF), c(0x1C9CAE), c(0x1C9CAE), c(0x7B4B3A)),
    BadgePalette(c(0x4CB8F0), c(0xA9CFDB), c(0xDDF0F4), c(0xFFFFFF), c(0x7C4DFF), c(0x7C4DFF), c(0x2B8CB8)),
    BadgePalette(c(0x3C8CF0), c(0xF2B01E), c(0xFFD25A), c(0xFFFFFF), c(0x2EBD85), c(0xE8563C), c(0x2F6FD6)),
    BadgePalette(c(0x6A4DD6), c(0xF2B01E), c(0xFFD25A), c(0xFFFFFF), c(0xE8563C), c(0x5B7CF0), c(0x5B3FC2)),
    BadgePalette(c(0x8E44D6), c(0xF2B01E), c(0xFFD25A), c(0x4FD6F0), c(0xE8563C), c(0xE8563C), c(0x7B2FC4)),
    BadgePalette(c(0xF06292), c(0x455A8A), c(0x6C86C8), c(0xFFD54F), c(0xFFC107), c(0xFFC107), c(0xC2185B)),
    BadgePalette(c(0x1ECBC1), c(0xD4A017), c(0xFFE082), c(0xC62828), c(0xC62828), c(0xC62828), c(0x0E8F87))
)

private fun paletteFor(milestone: Int): BadgePalette {
    val i = STREAK_MILESTONES.indexOf(milestone).let { if (it < 0) 0 else it }
    return PALETTES[i]
}

/** Accent colour used for the habit name / day count text of a milestone's unlock card. */
fun streakAccent(milestone: Int): Color = paletteFor(milestone).accent

/** Medal outline: sides = 0 means a circle, otherwise a regular polygon with a vertex pointing up. */
private fun medalSides(index: Int): Int = when (index) {
    0, 1 -> 0
    2, 3 -> 5
    4, 5, 6 -> 6
    7 -> 8
    else -> 12
}

private fun polygon(cx: Float, cy: Float, r: Float, sides: Int): Path {
    val p = Path()
    for (i in 0 until sides) {
        val a = -PI / 2 + i * 2 * PI / sides
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun star(cx: Float, cy: Float, outer: Float, inner: Float): Path {
    val p = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/**
 * Draws a streak badge into a 100x100 unit space scaled to the draw area:
 * a splash-shaped backdrop, a medal (shape and colours differ per milestone)
 * with an emblem, and two ribbon tails. [locked] draws a faded outline of the
 * same badge with a padlock instead.
 */
fun DrawScope.drawStreakBadge(milestone: Int, locked: Boolean) {
    val index = STREAK_MILESTONES.indexOf(milestone).let { if (it < 0) 0 else it }
    val pal = PALETTES[index]
    val fade = if (locked) 0.14f else 1f
    scale(size.minDimension / 100f, pivot = Offset.Zero) {
        // Backdrop: a disc with an even scalloped edge (a soft "flower" outline).
        val blob = pal.blob.copy(alpha = fade)
        drawCircle(blob, radius = 37f, center = Offset(50f, 46f))
        for (i in 0 until 14) {
            val a = i * 2 * PI / 14
            drawCircle(
                blob, radius = 6.2f,
                center = Offset(50f + (37f * cos(a)).toFloat(), 46f + (37f * sin(a)).toFloat())
            )
        }

        // Ribbon tails behind the medal.
        val ribbonA = pal.ribbonA.copy(alpha = fade)
        val ribbonB = pal.ribbonB.copy(alpha = fade)
        fun tail(x: Float, color: Color) {
            val t = Path().apply {
                moveTo(x, 66f); lineTo(x + 9f, 66f); lineTo(x + 9f, 96f)
                lineTo(x + 4.5f, 90f); lineTo(x, 96f); close()
            }
            drawPath(t, color)
        }
        tail(38f, ribbonA)
        tail(53f, ribbonB)

        // Medal.
        val cx = 50f
        val cy = 46f
        val sides = medalSides(index)
        val join = Stroke(width = 7f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        if (sides == 0) {
            drawCircle(pal.outer.copy(alpha = fade), radius = 27f, center = Offset(cx, cy))
            drawCircle(pal.inner.copy(alpha = fade), radius = 20f, center = Offset(cx, cy))
        } else {
            val outerPath = polygon(cx, cy, 28f, sides)
            drawPath(outerPath, pal.outer.copy(alpha = fade))
            drawPath(outerPath, pal.outer.copy(alpha = fade), style = join)
            val innerPath = polygon(cx, cy, 20f, sides)
            drawPath(innerPath, pal.inner.copy(alpha = fade))
            drawPath(innerPath, pal.inner.copy(alpha = fade), style = Stroke(width = 4f, join = StrokeJoin.Round))
        }

        // Emblem: a diamond for the 7- and 200-day tiers, a face ring for the first, a star for the rest.
        val emblem = pal.emblem.copy(alpha = fade)
        when (index) {
            0 -> {
                drawCircle(emblem, radius = 9f, center = Offset(cx, cy), style = Stroke(width = 3.5f))
                drawCircle(emblem, radius = 1.8f, center = Offset(cx - 3.2f, cy - 1.5f))
                drawCircle(emblem, radius = 1.8f, center = Offset(cx + 3.2f, cy - 1.5f))
            }
            1, 6 -> {
                val d = Path().apply {
                    moveTo(cx, cy - 12f); lineTo(cx + 9f, cy); lineTo(cx, cy + 12f); lineTo(cx - 9f, cy); close()
                }
                drawPath(d, emblem)
                drawPath(d, emblem, style = Stroke(width = 2f, join = StrokeJoin.Round))
            }
            else -> {
                val s = star(cx, cy + 1f, 12f, 5.2f)
                drawPath(s, emblem, style = Stroke(width = 4.2f, join = StrokeJoin.Round))
            }
        }

        if (locked) {
            // Padlock: shackle arc, body, keyhole.
            val dark = Color(0xFF111111)
            drawArc(
                color = dark, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(cx - 8f, cy - 19f), size = Size(16f, 20f),
                style = Stroke(width = 4.5f, cap = StrokeCap.Butt)
            )
            drawRoundRect(dark, topLeft = Offset(cx - 13f, cy - 8f), size = Size(26f, 22f), cornerRadius = CornerRadius(5f, 5f))
            drawCircle(Color.White, radius = 2.8f, center = Offset(cx, cy + 3f))
        }
    }
}

/** A streak badge as a composable; see [drawStreakBadge]. */
@Composable
fun StreakBadge(milestone: Int, size: Dp, modifier: Modifier = Modifier, locked: Boolean = false) {
    Canvas(modifier.size(size)) { drawStreakBadge(milestone, locked) }
}
