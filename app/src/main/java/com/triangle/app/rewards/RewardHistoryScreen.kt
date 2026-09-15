package com.triangle.app.rewards

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.Redemption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native port of renderInternRewards()'s "My Redemptions" tab (script.js:13927-13977), streamlined per the auto-approve decision — see [com.triangle.app.data.models.Redemption]'s class doc. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardHistoryScreen(viewModel: RewardsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val palette = rewardsPalette()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Redeemed", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (state.redemptions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎁", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No redemptions yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
                    Spacer(Modifier.height(4.dp))
                    Text("Visit the Store and redeem coins for rewards", fontSize = 12.5.sp, color = palette.text3)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.redemptions, key = { it.id }) { redemption ->
                    val reward = state.rewards.firstOrNull { it.id == redemption.rewardId }
                    RedemptionRow(redemption, reward?.name ?: "Reward", reward?.category ?: "physical", palette, onMarkDelivered = { viewModel.markDelivered(redemption.id) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun RedemptionRow(redemption: Redemption, rewardName: String, categoryKey: String, palette: RewardsPalette, onMarkDelivered: () -> Unit) {
    val cat = rewardCategory(categoryKey)
    val status = redemptionStatus(redemption.status)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface2).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cat.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(rewardName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text("${redemption.coinsCost} 🪙 · ${formatDate(redemption.createdAt)}", fontSize = 11.5.sp, color = palette.text3)
            }
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(status.bg).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(status.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = status.color)
            }
        }
        if (redemption.status == "approved") {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onMarkDelivered, modifier = Modifier.fillMaxWidth()) {
                Text("Mark as Delivered")
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestamp))
}
