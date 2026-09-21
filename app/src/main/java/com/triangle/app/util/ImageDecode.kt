package com.triangle.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlin.math.max

// Cap decoded images to this on their longest side — well under the ~4096px
// GPU texture size limit that caused PhotoCropView's rendering corruption
// ("bleeding") when full camera-resolution bitmaps (commonly 4000x3000, up
// to 8000x6000 on newer phones) were decoded unmodified and then drawn
// through a graphicsLayer scale transform.
private const val MAX_DECODED_DIMENSION = 2048

/** Decodes a content Uri (from the system Photo Picker) into a mutable Bitmap, downsampled to at
 * most MAX_DECODED_DIMENSION on its longest side, or null if it couldn't be read. */
fun decodeImageUri(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.isMutableRequired = true
            val (w, h) = targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(w, h)
        }
    } else {
        decodeDownsampledLegacy(context, uri)
    }
} catch (e: Exception) {
    null
}

private fun targetSize(width: Int, height: Int): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= MAX_DECODED_DIMENSION) return width to height
    val scale = MAX_DECODED_DIMENSION.toFloat() / longest
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}

/** Pre-API 28 path — no ImageDecoder.setTargetSize(), so this does its own two-pass
 * bounds-then-inSampleSize decode instead of MediaStore.Images.Media.getBitmap()'s
 * unbounded full-resolution decode. */
@Suppress("DEPRECATION")
private fun decodeDownsampledLegacy(context: Context, uri: Uri): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
        ?: return MediaStore.Images.Media.getBitmap(context.contentResolver, uri) // couldn't open twice; fall back

    var sampleSize = 1
    val longest = max(boundsOptions.outWidth, boundsOptions.outHeight)
    while (longest / sampleSize > MAX_DECODED_DIMENSION * 2) sampleSize *= 2

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize; inMutable = true }
    val sampled = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        ?: return null

    // inSampleSize only halves at a time, so finish with a precise scale to the exact target.
    val (targetW, targetH) = targetSize(sampled.width, sampled.height)
    return if (targetW == sampled.width && targetH == sampled.height) sampled
    else Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
}
