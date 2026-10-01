package com.triangle.app.reviews

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.triangle.app.data.models.Review
import com.triangle.app.tasks.drawTrianglePill
import com.triangle.app.tasks.shareBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** Renders one review as a picture (reviewer, stars, comment, Triangle logo) and opens the share sheet. */
suspend fun shareReview(context: Context, review: Review) {
    val avatar = review.reviewerPhotoUrl?.takeIf { it.isNotBlank() }?.let { url ->
        runCatching {
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(300).build()
            (ImageLoader(context).execute(request) as? SuccessResult)?.drawable?.toBitmap(300, 300)
        }.getOrNull()
    }
    val bitmap = withContext(Dispatchers.Default) { renderReviewCard(context, review, avatar) }
    shareBitmap(context, bitmap, "review_card.png")
}

private fun ratingArgb(stars: Int): Int = when {
    stars >= 5 -> 0xFF0E9F6E.toInt()
    stars == 4 -> 0xFF8BC34A.toInt()
    stars == 3 -> 0xFFF5C518.toInt()
    else -> 0xFFE53935.toInt()
}

private fun starPath(cx: Float, cy: Float, outer: Float): Path {
    val inner = outer * 0.42f
    val p = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -Math.PI / 2 + i * Math.PI / 5
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun renderReviewCard(context: Context, r: Review, avatar: Bitmap?): Bitmap {
    val w = 900
    val pad = 70f
    val textW = (w - 2 * pad).toInt()

    // Measure the comment first so the card is exactly as tall as it needs to be.
    val commentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF55555C.toInt(); textSize = 44f }
    val commentLayout = r.comment.takeIf { it.isNotBlank() }?.let {
        StaticLayout.Builder.obtain(it, 0, it.length, commentPaint, textW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(8f, 1f).setMaxLines(8)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
    }
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF55555C.toInt(); textSize = 40f }
    val titleText = if (r.taskTitle.isNotBlank()) "for “${r.taskTitle}”" else ""
    val titleLayout = titleText.takeIf { it.isNotEmpty() }?.let {
        StaticLayout.Builder.obtain(it, 0, it.length, titlePaint, textW)
            .setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
    }

    val headerH = 14f
    val avatarTop = headerH + 60f
    val avatarR = 70f
    var y = avatarTop + 2 * avatarR + 50f
    val titleTop = y
    if (titleLayout != null) y += titleLayout.height + 30f
    val commentTop = y
    if (commentLayout != null) y += commentLayout.height + 40f
    val pillTop = y + 30f
    val h = (pillTop + 96f + 60f).toInt()

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)

    // Card with a hairline border, thin rating-coloured line across the top (same as the on-screen card).
    val rect = RectF(2f, 2f, w - 2f, h - 2f)
    c.drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    c.save()
    c.clipPath(Path().apply { addRoundRect(rect, 48f, 48f, Path.Direction.CW) })
    c.drawRect(0f, 0f, w.toFloat(), headerH, Paint().apply { color = ratingArgb(r.stars) })
    c.restore()
    c.drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD5D5D9.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
    })

    // Avatar (photo or initial).
    val acx = pad + avatarR
    val acy = avatarTop + avatarR
    if (avatar != null) {
        val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val s = (2 * avatarR) / avatar.width
        shader.setLocalMatrix(Matrix().apply { setScale(s, s); postTranslate(acx - avatarR, acy - avatarR) })
        c.drawCircle(acx, acy, avatarR, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    } else {
        c.drawCircle(acx, acy, avatarR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9CFFF.toInt() })
        c.drawText(
            (r.reviewerName.firstOrNull() ?: '?').uppercase(), acx, acy + 24f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF3B2494.toInt(); textSize = 70f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
        )
    }

    // Name, stars and score.
    val textX = pad + 2 * avatarR + 36f
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1F.toInt(); textSize = 56f; typeface = Typeface.DEFAULT_BOLD }
    var name = r.reviewerName.ifBlank { "Someone" }
    while (namePaint.measureText(name) > w - textX - pad && namePaint.textSize > 30f) namePaint.textSize -= 2f
    c.drawText(name, textX, acy - 14f, namePaint)
    val starR = 24f
    val starY = acy + 44f
    for (i in 0 until Review.MAX_STARS) {
        val cx = textX + starR + i * (2 * starR + 10f)
        c.drawPath(starPath(cx, starY, starR), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (i < r.stars) 0xFFF5B301.toInt() else 0xFFD9D9DE.toInt()
        })
    }
    c.drawText(
        "${r.stars}.0", textX + Review.MAX_STARS * (2 * starR + 10f) + 8f, starY + 14f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1F.toInt(); textSize = 40f; typeface = Typeface.DEFAULT_BOLD }
    )

    titleLayout?.let { c.save(); c.translate(pad, titleTop); it.draw(c); c.restore() }
    commentLayout?.let { c.save(); c.translate(pad, commentTop); it.draw(c); c.restore() }

    drawTrianglePill(context, c, w, pillTop)
    return out
}
