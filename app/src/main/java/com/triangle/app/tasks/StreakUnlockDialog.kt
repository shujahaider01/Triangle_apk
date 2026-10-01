package com.triangle.app.tasks

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.triangle.app.R
import com.triangle.app.ui.theme.triangleDarkTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A just-reached (or re-opened) streak milestone to celebrate. */
data class StreakUnlock(
    val habitName: String,
    val milestone: Int,
    val unlockedOn: LocalDate
)

private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun daysLabel(n: Int) = "$n day${if (n == 1) "" else "s"}"

/** The "Well done!" card shown when a streak milestone is unlocked, with a Share action. */
@Composable
fun StreakUnlockDialog(unlock: StreakUnlock, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = streakAccent(unlock.milestone)
    val dark = triangleDarkTheme()
    val card = if (dark) Color(0xFF1E1E26) else Color.White
    val rule = if (dark) Color(0xFF3A3A46) else Color(0xFFD5D5D9)
    val muted = if (dark) Color(0xFFC9C9D1) else Color(0xFF55555C)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(card),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Well done!",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = muted,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = rule)
            Spacer(Modifier.height(28.dp))
            StreakBadge(unlock.milestone, 150.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                unlock.habitName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text("Streak unlocked", fontSize = 15.sp, color = muted)
            Text(daysLabel(unlock.milestone), fontSize = 30.sp, fontWeight = FontWeight.Black, color = accent)
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = rule)
            Text(
                "Unlocked on: ${unlock.unlockedOn.format(DATE_FMT)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = muted,
                modifier = Modifier.padding(vertical = 14.dp)
            )
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = rule)
            Row(
                Modifier.fillMaxWidth().clickable { shareStreakBadge(context, unlock) }.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("SHARE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

/** Saves [bitmap] as a PNG in the app cache and opens the system share sheet for it. */
fun shareBitmap(context: Context, bitmap: Bitmap, fileName: String) {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, fileName)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Renders the shareable card (badge, habit, "Streak unlocked", day count, Triangle logo + name) to a PNG and opens the share sheet. */
fun shareStreakBadge(context: Context, unlock: StreakUnlock) {
    shareBitmap(context, renderStreakShareCard(context, unlock), "streak_badge.png")
}

/** The grey "logo + Triangle" pill that closes every shared card, centred horizontally on a card [w] px wide with its top at [top]. */
fun drawTrianglePill(context: Context, c: android.graphics.Canvas, w: Int, top: Float) {
    val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF222222.toInt(); textSize = 46f; typeface = Typeface.DEFAULT_BOLD
    }
    val logoSize = 72
    val gap = 18f
    val contentW = logoSize + gap + name.measureText("Triangle")
    val pillW = contentW + 2 * 40f
    val pillH = 96f
    val pill = RectF(w / 2f - pillW / 2f, top, w / 2f + pillW / 2f, top + pillH)
    c.drawRoundRect(pill, pillH / 2f, pillH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEFEFF2.toInt() })
    val startX = pill.centerX() - contentW / 2f
    ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(logoSize, logoSize)?.let {
        c.drawBitmap(it, startX, pill.centerY() - logoSize / 2f, null)
    }
    val fm = name.fontMetrics
    c.drawText("Triangle", startX + logoSize + gap, pill.centerY() - (fm.ascent + fm.descent) / 2f, name)
}

private fun renderStreakShareCard(context: Context, unlock: StreakUnlock): Bitmap {
    val w = 900
    val h = 1100
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val accent = streakAccent(unlock.milestone).toArgb()

    // White rounded card with a hairline border.
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD5D5D9.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    val rect = RectF(2f, 2f, w - 2f, h - 2f)
    c.drawRoundRect(rect, 56f, 56f, bg)
    c.drawRoundRect(rect, 56f, 56f, border)

    // Badge, drawn with the same code as the on-screen one.
    val badgeSize = 520
    val badge = ImageBitmap(badgeSize, badgeSize)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(badge), Size(badgeSize.toFloat(), badgeSize.toFloat())) {
        drawStreakBadge(unlock.milestone, locked = false)
    }
    c.drawBitmap(badge.asAndroidBitmap(), (w - badgeSize) / 2f, 90f, null)

    fun text(value: String, size: Float, color: Int, bold: Boolean, baseline: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        // Shrink long habit names to fit the card.
        while (p.measureText(value) > w - 120 && p.textSize > 28f) p.textSize -= 2f
        c.drawText(value, w / 2f, baseline, p)
    }
    text(unlock.habitName, 76f, accent, true, 700f)
    text("Streak unlocked", 44f, 0xFF333333.toInt(), false, 800f)
    text(daysLabel(unlock.milestone), 92f, accent, true, 920f)

    drawTrianglePill(context, c, w, 970f)
    return out
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)
