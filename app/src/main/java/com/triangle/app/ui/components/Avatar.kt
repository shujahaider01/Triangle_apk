package com.triangle.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * The one avatar circle every screen should use — profile photo when
 * [photoUrl] is set, else a colored initial. Before this, every screen that
 * showed "who" (Leaderboard, Circle, Assign picker, DM inbox/thread, task
 * detail's Assigned By/To, connection notifications) had its own hand-rolled
 * initials-only circle that ignored users/{uid}/photoUrl entirely, so a
 * profile photo never showed up anywhere except the Profile tab itself.
 */
@Composable
fun Avatar(
    name: String,
    photoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF7C3AED).copy(alpha = 0.18f),
    textColor: Color = Color(0xFF7C3AED),
    fontSize: androidx.compose.ui.unit.TextUnit = (size.value * 0.4f).sp
) {
    Box(
        modifier.size(size).clip(CircleShape).background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "$name's profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text((name.firstOrNull() ?: '?').uppercase(), color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}
