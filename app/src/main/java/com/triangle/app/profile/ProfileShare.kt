package com.triangle.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.triangle.app.tasks.drawTrianglePill
import com.triangle.app.tasks.shareBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders the profile card (photo, name, @handle, connections / points / performance, Triangle pill) and opens the share sheet. */
suspend fun shareProfile(context: Context, state: ProfileUiState) {
    val avatar = state.photoUrl?.let { url ->
        runCatching {
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(400).build()
            (ImageLoader(context).execute(request) as? SuccessResult)?.drawable?.toBitmap(400, 400)
        }.getOrNull()
    }
    val bitmap = withContext(Dispatchers.Default) { renderProfileCard(context, state, avatar) }
    shareBitmap(context, bitmap, "profile_card.png")
}

private fun renderProfileCard(context: Context, state: ProfileUiState, avatar: Bitmap?): Bitmap {
    val w = 900
    val h = 1260
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val teal = 0xFF5B3FD6.toInt()

    val rect = RectF(2f, 2f, w - 2f, h - 2f)
    c.drawRoundRect(rect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    c.drawRoundRect(rect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD5D5D9.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
    })

    // Soft aqua wash across the top, like the on-screen header.
    c.save()
    c.clipPath(Path().apply { addRoundRect(rect, 56f, 56f, Path.Direction.CW) })
    c.drawRect(0f, 0f, w.toFloat(), 620f, Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, 620f, intArrayOf(0xFFFBEEE9.toInt(), 0xFFF3E6EE.toInt(), 0xFFE1E4F5.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
    })
    c.restore()

    // Avatar: the photo, or the name's first letter on a tinted disc.
    val cx = w / 2f
    val cy = 230f
    val r = 130f
    if (avatar != null) {
        val shader = android.graphics.BitmapShader(avatar, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val m = android.graphics.Matrix().apply {
            val s = (2 * r) / avatar.width
            setScale(s, s); postTranslate(cx - r, cy - r)
        }
        shader.setLocalMatrix(m)
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    } else {
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9CFFF.toInt() })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal; textSize = 130f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText((state.name.firstOrNull() ?: '?').uppercase(), cx, cy + 45f, p)
    }

    fun centered(text: String, size: Float, color: Int, bold: Boolean, baseline: Float, maxW: Float = w - 120f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        while (p.measureText(text) > maxW && p.textSize > 28f) p.textSize -= 2f
        c.drawText(text, w / 2f, baseline, p)
    }
    centered(state.name.ifBlank { "Triangle user" }, 78f, 0xFF1B1B1F.toInt(), false, 480f)
    centered(state.handle, 52f, 0xFF1B1B1F.toInt(), true, 560f)

    // Three stat columns.
    val stats = listOf(
        "${state.performancePct}%" to "performance",
        state.points.toString() to "points",
        (state.ratingAverage?.let { "%.1f".format(it) } ?: "–") to "rating"
    )
    val colW = (w - 160f) / 3f
    stats.forEachIndexed { i, (value, label) ->
        val x = 80f + colW * i + colW / 2f
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal; textSize = 82f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B1B1F.toInt(); textSize = 42f; textAlign = Paint.Align.CENTER
        }
        c.drawText(value, x, 740f, num)
        c.drawText(label, x, 800f, lab)
    }

    // Level progress, as on the profile header: bar + star badge, then "N points away from Level X".
    val barLeft = 80f
    val starR = 34f
    val barRight = w - 80f - 2 * starR - 16f
    val barCy = 900f
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD3C9F7.toInt() }
    c.drawRoundRect(RectF(barLeft, barCy - 14f, barRight, barCy + 14f), 14f, 14f, trackPaint)
    c.drawCircle(barRight - 12f, barCy, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB32A1B6B.toInt() })
    val fraction = (state.xpIntoLevel / 500f).coerceIn(0.07f, 1f)
    val fillRight = barLeft + (barRight - barLeft) * fraction
    c.drawRoundRect(RectF(barLeft, barCy - 14f, fillRight, barCy + 14f), 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6D4DE0.toInt() })
    c.drawCircle(fillRight - 12f, barCy, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A1B6B.toInt() })
    val starCx = w - 80f - starR
    c.drawCircle(starCx, barCy, starR, trackPaint)
    val star = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) 20f else 8.5f
        val a = -Math.PI / 2 + i * Math.PI / 5
        val x = starCx + (rr * Math.cos(a)).toFloat()
        val y = barCy + 1f + (rr * Math.sin(a)).toFloat()
        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    c.drawPath(star, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })

    val away = (500 - state.xpIntoLevel).coerceAtLeast(0).toString()
    val boldP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1F.toInt(); textSize = 56f; typeface = Typeface.DEFAULT_BOLD }
    val plainP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1F.toInt(); textSize = 44f }
    c.drawText(away, barLeft, 990f, boldP)
    c.drawText(" points away from Level ${state.level + 1}", barLeft + boldP.measureText(away), 990f, plainP)

    drawTrianglePill(context, c, w, 1090f)
    return out
}
