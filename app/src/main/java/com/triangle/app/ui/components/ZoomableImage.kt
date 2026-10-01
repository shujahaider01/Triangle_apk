package com.triangle.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full-size image viewer content: pinch to zoom in/out (1x to 5x), drag to pan while zoomed in,
 * double-tap to jump between fit-to-screen and 2.5x. Zooming out below 1x snaps back to fit.
 */
@Composable
fun ZoomableImage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // Keeps the picture from being dragged past its own edges at the current zoom.
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = box.width * (s - 1f) / 2f
        val maxY = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    scale = newScale
                    offset = if (newScale <= MIN_ZOOM) Offset.Zero else clamp(offset + pan, newScale)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scope.launch {
                        val target = if (scale > 1.05f) 1f else DOUBLE_TAP_ZOOM
                        val startScale = scale
                        val startOffset = offset
                        Animatable(0f).animateTo(1f) {
                            val s = startScale + (target - startScale) * value
                            scale = s
                            offset = if (s <= MIN_ZOOM) Offset.Zero else clamp(startOffset * (1f - value), s)
                        }
                    }
                })
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
