package com.triangle.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import android.graphics.Path as AndroidPath

/**
 * Renders a task/habit's stored `iconSvg` field (a full `<svg>...</svg>`
 * markup string — see HabitPalette.kt) by parsing its `<path d="...">` /
 * `<circle cx cy r>` elements and drawing them with Compose's Canvas,
 * tinted with the task/habit's own color. This works for ANY icon the
 * app has ever stored — including ones picked in the WebView from outside
 * HabitPalette's curated native picker subset — since it parses whatever
 * SVG is actually there rather than looking it up in a fixed table.
 *
 * Uses androidx.core.graphics.PathParser (part of core-ktx, already a
 * dependency) to convert SVG path data into a real android.graphics.Path.
 */
@Composable
fun SvgPathIcon(svg: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val parsed = remember(svg) { parseSvg(svg) }
    Canvas(modifier = modifier.size(size)) {
        val scaleX = this.size.width / parsed.viewBoxW
        val scaleY = this.size.height / parsed.viewBoxH
        scale(scaleX, scaleY, pivot = Offset.Zero) {
            parsed.paths.forEach { drawPath(it.asComposePath(), color = tint) }
            parsed.circles.forEach { (cx, cy, r) -> drawCircle(color = tint, radius = r, center = Offset(cx, cy)) }
        }
    }
}

private data class ParsedSvg(
    val viewBoxW: Float,
    val viewBoxH: Float,
    val paths: List<AndroidPath>,
    val circles: List<Triple<Float, Float, Float>>
)

private val svgCache = HashMap<String, ParsedSvg>()
private val viewBoxRegex = Regex("""viewBox="([\d.\s-]+)"""")
private val pathDataRegex = Regex("""<path[^>]*\sd="([^"]+)"""")
private val circleTagRegex = Regex("""<circle([^>]*)/?>""")
private val cxRegex = Regex("""cx="([\d.-]+)"""")
private val cyRegex = Regex("""cy="([\d.-]+)"""")
private val rRegex = Regex("""\br="([\d.-]+)"""")

private fun parseSvg(svg: String): ParsedSvg = svgCache.getOrPut(svg) {
    val vb = viewBoxRegex.find(svg)?.groupValues?.get(1)?.trim()?.split(Regex("\\s+"))
    val w = vb?.getOrNull(2)?.toFloatOrNull() ?: 24f
    val h = vb?.getOrNull(3)?.toFloatOrNull() ?: 24f

    val paths = pathDataRegex.findAll(svg).mapNotNull { m ->
        runCatching { PathParser.createPathFromPathData(m.groupValues[1]) }.getOrNull()
    }.toList()

    val circles = circleTagRegex.findAll(svg).mapNotNull { m ->
        val attrs = m.groupValues[1]
        val cx = cxRegex.find(attrs)?.groupValues?.get(1)?.toFloatOrNull()
        val cy = cyRegex.find(attrs)?.groupValues?.get(1)?.toFloatOrNull()
        val r = rRegex.find(attrs)?.groupValues?.get(1)?.toFloatOrNull()
        if (cx != null && cy != null && r != null) Triple(cx, cy, r) else null
    }.toList()

    ParsedSvg(w, h, paths, circles)
}
