package com.triangle.app.circle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ConnectGreen = Color(0xFF22C55E)
private val ConnectBlue = Color(0xFF3B82F6)
private val ConnectPurple = Color(0xFF8B5CF6)

/** "Connect" picker — the single entry point for starting a connection (Email/Link/Username). See the approved plan at .claude/plans/expressive-munching-karp.md. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onOpenEmail: () -> Unit,
    onOpenUsername: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            ConnectTriangleMark(Modifier.size(84.dp))
            Spacer(Modifier.height(16.dp))
            Text("Connect with someone", fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Choose how you want to connect with a person on Triangle.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            ConnectOptionRow(
                icon = Icons.Default.Email,
                iconTint = ConnectBlue,
                title = "Email",
                subtitle = "Send an invite by email or connect if they're already a user.",
                onClick = onOpenEmail
            )
            Spacer(Modifier.height(10.dp))
            ConnectOptionRow(
                icon = Icons.Default.Link,
                iconTint = ConnectPurple,
                title = "Link",
                subtitle = "Share a unique link (coming soon).",
                enabled = false,
                onClick = {}
            )
            Spacer(Modifier.height(10.dp))
            ConnectOptionRow(
                icon = Icons.Default.AlternateEmail,
                iconTint = ConnectGreen,
                title = "Username",
                subtitle = "Invite by their Triangle username.",
                onClick = onOpenUsername
            )
        }
    }
}

@Composable
private fun ConnectOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.55f)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Small static version of the app's triangle-of-three-dots mark (see navigation/AppNavHost.kt's SplashLogo) — used as this screen's hero graphic. */
@Composable
private fun ConnectTriangleMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val top = Offset(size.width / 2f, size.height * 0.12f)
        val bottomLeft = Offset(size.width * 0.14f, size.height * 0.88f)
        val bottomRight = Offset(size.width * 0.86f, size.height * 0.88f)
        val lineColor = Color(0xFFD1D5DB)
        val strokeWidth = size.minDimension * 0.03f
        drawLine(lineColor, top, bottomLeft, strokeWidth, cap = StrokeCap.Round)
        drawLine(lineColor, top, bottomRight, strokeWidth, cap = StrokeCap.Round)
        drawLine(lineColor, bottomLeft, bottomRight, strokeWidth, cap = StrokeCap.Round)
        val dotRadius = size.minDimension * 0.14f
        drawCircle(ConnectGreen, dotRadius, top)
        drawCircle(ConnectBlue, dotRadius, bottomLeft)
        drawCircle(ConnectPurple, dotRadius, bottomRight)
    }
}
