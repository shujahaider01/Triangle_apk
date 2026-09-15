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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TxnSourceInfo(val emoji: String, val label: String, val color: Color, val bg: Color)

private val TxnSources = mapOf(
    "task" to TxnSourceInfo("✅", "Task Completed", Color(0xFF3B82F6), Color(0x1F3B82F6)),
    "habit" to TxnSourceInfo("🔁", "Habit Completed", Color(0xFFF59E0B), Color(0x1FF59E0B)),
    "redemption" to TxnSourceInfo("🎁", "Reward Redeemed", Color(0xFFEF4444), Color(0x1FEF4444))
)
private val DefaultTxnSource = TxnSourceInfo("🪙", "Coin Activity", Color(0xFF6C5CE7), Color(0x1F6C5CE7))

/** Native port of renderInternRewards()'s Wallet + History tabs (script.js:13718-13925). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardWalletScreen(viewModel: RewardsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val palette = rewardsPalette()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (state.transactions.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                BalanceHero(state.balance, state.totalEarned, state.totalSpent)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 44.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("No transactions yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
                        Spacer(Modifier.height(4.dp))
                        Text("Complete tasks & habits to start earning", fontSize = 12.5.sp, color = palette.text3)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { BalanceHero(state.balance, state.totalEarned, state.totalSpent) }
                item { Spacer(Modifier.height(6.dp)) }
                items(state.transactions.sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }) { txn ->
                    TransactionRow(txn, palette)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun BalanceHero(balance: Int, totalEarned: Int, totalSpent: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(RewardsColors.BalanceHeroGradient))
            .padding(20.dp)
    ) {
        Text("AVAILABLE BALANCE", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🪙", fontSize = 32.sp)
            Spacer(Modifier.width(10.dp))
            Text(balance.toString(), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WalletStat("🪙 $totalEarned", "Earned")
            WalletStat("🪙 $totalSpent", "Spent")
        }
    }
}

@Composable
private fun WalletStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TransactionRow(txn: Map<String, Any?>, palette: RewardsPalette) {
    val source = TxnSources[txn["source"] as? String] ?: DefaultTxnSource
    val coinsChange = (txn["coinsChange"] as? Number)?.toInt() ?: 0
    val isPositive = coinsChange >= 0
    val sourceTitle = (txn["sourceTitle"] as? String)?.takeIf { it.isNotBlank() } ?: source.label
    val timestamp = (txn["timestamp"] as? Number)?.toLong() ?: 0L
    val xpEarned = (txn["xpEarned"] as? Number)?.toInt() ?: 0
    val balanceAfter = (txn["balanceAfter"] as? Number)?.toInt() ?: 0

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface2).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(source.bg), contentAlignment = Alignment.Center) {
            Text(source.emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sourceTitle, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
                if (xpEarned > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(source.color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("+$xpEarned XP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = source.color)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text("${source.label} · ${formatTime(timestamp)}", fontSize = 11.sp, color = palette.text3)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (isPositive) "+" else "−"}${kotlin.math.abs(coinsChange)} 🪙",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) Color(0xFF22C55E) else Color(0xFFEF4444)
            )
            Spacer(Modifier.height(2.dp))
            Text("Bal: $balanceAfter", fontSize = 10.5.sp, color = palette.text3)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(timestamp))
}
