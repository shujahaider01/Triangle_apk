package com.triangle.app.tasks

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.theme.TriangleBrandPurple
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class CropRatio(val label: String, val value: Float?) // null = "Original" (source bitmap's own aspect)

private val CROP_RATIOS = listOf(
    CropRatio("Original", null),
    CropRatio("Square", 1f),
    CropRatio("4:3", 4f / 3f),
    CropRatio("16:9", 16f / 9f)
)

/**
 * Full-screen pan/pinch-zoom crop tool — a deliberately simplified native
 * equivalent of the WebView's rte-crop-page (no rotate slider, no freehand
 * mode; see the Task Notes plan's "explicitly out of scope" section). Lives
 * as local state inside AddNoteScreen rather than its own nav destination,
 * since passing a Bitmap through NavController args isn't natural and this
 * is conceptually one step of "add a photo," not a screen a user backs into.
 *
 * [circularFrame] is profile-picture mode: the frame is locked to a circle
 * (no aspect-ratio chips, since the avatar is always displayed circular —
 * see ProfileScreen's ProfileHero) instead of the rectangular multi-ratio
 * frame task/habit note photos use. The underlying frameRect/cropBitmap()
 * math is unchanged either way — a circle is just drawn as the visual
 * boundary of the same square frameRect a "Square" ratio would produce, so
 * what's confirmed is exactly what the circular mask showed.
 */
@Composable
fun PhotoCropView(
    sourceBitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    squareByDefault: Boolean = false,
    circularFrame: Boolean = false
) {
    var ratio by remember { mutableStateOf(if (circularFrame || squareByDefault) CROP_RATIOS[1] else CROP_RATIOS[0]) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it }
    ) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            val viewportW = viewportSize.width.toFloat()
            val viewportH = viewportSize.height.toFloat()
            val bmpW = sourceBitmap.width.toFloat()
            val bmpH = sourceBitmap.height.toFloat()

            // Crop frame: target aspect ratio, sized to 86% of the viewport's limiting dimension, centered.
            val targetAspect = ratio.value ?: (bmpW / bmpH)
            val maxFrameW = viewportW * 0.86f
            val maxFrameH = viewportH * 0.72f // leaves room for the top bar + bottom chip row
            var frameW = maxFrameW
            var frameH = frameW / targetAspect
            if (frameH > maxFrameH) {
                frameH = maxFrameH
                frameW = frameH * targetAspect
            }
            val frameLeft = (viewportW - frameW) / 2f
            val frameTop = (viewportH - frameH) / 2f
            val frameRect = Rect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH)

            // "Cover" base scale against the crop FRAME, not the whole
            // viewport — the only real constraint is that the frame itself
            // must never show non-image content. Basing this on the (much
            // larger) viewport instead, as before, forced scale=1 to already
            // be zoomed in past what's needed to cover the frame (why the
            // default view was more cropped than it needed to be — "full
            // image" complaint) AND made it impossible to pinch out beyond
            // that same point (the "can't zoom out" complaint) — one wrong
            // reference size caused both.
            val baseScale = max(frameW / bmpW, frameH / bmpH)
            val effectiveScale = baseScale * scale

            // How far scale is allowed to go below 1 (frame-cover) — down to
            // HALF of wherever the whole image fits inside the viewport
            // (min, not max, of the two axis ratios — "fit" rather than
            // "cover" — then halved again for extra headroom beyond even
            // that). Below frame-cover the image no longer fills the frame
            // on its own, but that's fine: cropBitmap() already clamps its
            // crop rect to the bitmap's real bounds, so zooming out this far
            // just means "crop everything available" rather than corrupting
            // or crashing — letting people zoom out well past the image's
            // own edges while deciding where to crop is worth that tradeoff.
            val minScale = (min(viewportW / bmpW, viewportH / bmpH) / baseScale) * 0.5f

            // Bitmap pixel (px,py) is drawn (before transform) at local Image
            // position (px,py) — ContentScale.None means 1:1, top-left
            // anchored. Scaling from transformOrigin (0,0) then translating
            // by (translationX,translationY) places screen position =
            // (translationX,translationY) + (px,py)*effectiveScale — solved
            // below so the bitmap's own center lands at (viewport center +
            // pan offset), matching cropBitmap()'s inverse math exactly.
            val imageCenterOnScreen = Offset(viewportW / 2f, viewportH / 2f) + offset
            val imgTranslationX = imageCenterOnScreen.x - (bmpW / 2f) * effectiveScale
            val imgTranslationY = imageCenterOnScreen.y - (bmpH / 2f) * effectiveScale

            Image(
                bitmap = sourceBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.None,
                // Alignment.TopStart is load-bearing, not cosmetic — Image's
                // default alignment (Center) would internally center the
                // natural-size bitmap within this fillMaxSize() box BEFORE
                // the graphicsLayer transform below runs, silently
                // introducing a (viewportW-bmpW)/2, (viewportH-bmpH)/2
                // offset that imgTranslationX/Y and cropBitmap()'s inverse
                // math don't account for — exactly the "correct at default
                // position, wrong once panned/zoomed" symptom this caused.
                alignment = Alignment.TopStart,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(minScale, 5f)
                            // Anchor the zoom to the pinch centroid instead
                            // of the image's own center — without this, the
                            // point under your fingers doesn't stay under
                            // your fingers as scale changes, which reads as
                            // the picture suddenly jumping/snapping instead
                            // of zooming smoothly in place.
                            val actualZoom = newScale / scale
                            val centroidFromCenter = centroid - Offset(viewportW / 2f, viewportH / 2f)
                            offset = (offset - centroidFromCenter) * actualZoom + centroidFromCenter + pan
                            scale = newScale
                        }
                    }
                    .graphicsLayer {
                        scaleX = effectiveScale
                        scaleY = effectiveScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        translationX = imgTranslationX
                        translationY = imgTranslationY
                    }
            )

            // Dim mask around the frame — circular punch-hole for profile
            // pictures (matches the circular avatar it'll actually become),
            // four translucent strips for the rectangular multi-ratio frame
            // otherwise (avoids needing an offscreen punch-hole composite
            // for the common case).
            val maskColor = Color.Black.copy(alpha = 0.55f)
            if (circularFrame) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = min(frameRect.width, frameRect.height) / 2f
                    drawIntoCanvas { canvas ->
                        val layerPaint = Paint().apply { color = maskColor }
                        val bounds = Rect(Offset.Zero, size)
                        canvas.saveLayer(bounds, layerPaint)
                        canvas.drawRect(bounds, layerPaint)
                        canvas.drawCircle(frameRect.center, radius, Paint().apply { blendMode = BlendMode.Clear })
                        canvas.restore()
                    }
                    drawCircle(Color.White, radius, frameRect.center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                }
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(maskColor, topLeft = Offset(0f, 0f), size = Size(size.width, frameRect.top))
                    drawRect(maskColor, topLeft = Offset(0f, frameRect.bottom), size = Size(size.width, size.height - frameRect.bottom))
                    drawRect(maskColor, topLeft = Offset(0f, frameRect.top), size = Size(frameRect.left, frameRect.height))
                    drawRect(maskColor, topLeft = Offset(frameRect.right, frameRect.top), size = Size(size.width - frameRect.right, frameRect.height))
                    drawRect(Color.White, topLeft = frameRect.topLeft, size = frameRect.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                }
            }

            // Top bar — inset below the (transparent, edge-to-edge) status bar; without this
            // the row draws under it and its buttons don't reliably receive touches there.
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White) }
                Text("Crop Photo", color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterVertically))
                IconButton(onClick = {
                    val cropped = cropBitmap(sourceBitmap, frameRect, viewportW, viewportH, effectiveScale, offset)
                    onConfirm(cropped)
                }) { Icon(Icons.Default.Check, contentDescription = "Confirm", tint = TriangleBrandPurple) }
            }

            // Aspect-ratio chip row — hidden for profile pictures, which are
            // always circular, so there's nothing to choose between.
            if (!circularFrame) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CROP_RATIOS.forEach { r ->
                        val active = r == ratio
                        Box(
                            Modifier
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) TriangleBrandPurple else Color.White.copy(alpha = 0.15f))
                                .clickable { ratio = r; scale = 1f; offset = Offset.Zero }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(r.label, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Maps the crop frame (viewport space) back into source-bitmap pixel space
 * and crops. The image is drawn top-start at its natural size, scaled by
 * `effectiveScale` around its own center, then the whole thing is
 * translated so that center lands at (viewport center + offset) — i.e. the
 * same "cover, then pan/zoom from center" model a typical native photo
 * cropper uses.
 */
private fun cropBitmap(
    source: Bitmap,
    frameRect: Rect,
    viewportW: Float,
    viewportH: Float,
    effectiveScale: Float,
    offset: Offset
): Bitmap {
    val bmpW = source.width.toFloat()
    val bmpH = source.height.toFloat()
    val viewportCenter = Offset(viewportW / 2f, viewportH / 2f)
    val imageCenterOnScreen = viewportCenter + offset

    fun screenToBitmap(p: Offset): Offset {
        val dx = (p.x - imageCenterOnScreen.x) / effectiveScale
        val dy = (p.y - imageCenterOnScreen.y) / effectiveScale
        return Offset(bmpW / 2f + dx, bmpH / 2f + dy)
    }

    val topLeft = screenToBitmap(frameRect.topLeft)
    val bottomRight = screenToBitmap(frameRect.bottomRight)

    val left = topLeft.x.coerceIn(0f, bmpW)
    val top = topLeft.y.coerceIn(0f, bmpH)
    val right = bottomRight.x.coerceIn(0f, bmpW)
    val bottom = bottomRight.y.coerceIn(0f, bmpH)
    val width = (right - left).coerceAtLeast(1f)
    val height = (bottom - top).coerceAtLeast(1f)

    return Bitmap.createBitmap(
        source,
        left.roundToInt(),
        top.roundToInt(),
        min(width.roundToInt(), source.width - left.roundToInt()),
        min(height.roundToInt(), source.height - top.roundToInt())
    )
}
