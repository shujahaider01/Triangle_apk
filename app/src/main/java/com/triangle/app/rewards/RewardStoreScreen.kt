package com.triangle.app.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.Reward

private val StoreCategoryFilters = listOf("all", "digital", "learning", "merch", "office", "physical")

/** Native port of renderInternRewards()'s Store tab (script.js:13778-13875). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardStoreScreen(
    viewModel: RewardsViewModel,
    onOpenDetail: (String) -> Unit,
    onOpenWallet: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenManage: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val palette = rewardsPalette()
    var category by remember { mutableStateOf("all") }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenWallet) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Redeemed") }, onClick = { menuOpen = false; onOpenHistory() })
                        DropdownMenuItem(text = { Text("Manage") }, onClick = { menuOpen = false; onOpenManage() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpenSettings() })
                    }
                }
            )
        }
    ) { padding ->
        val shown = if (category == "all") state.rewards else state.rewards.filter { it.category == category }
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                StoreHero(balance = state.balance)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StoreCategoryFilters.forEach { key ->
                        val label = if (key == "all") "All" else rewardCategory(key).label
                        val active = category == key
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else palette.surface2)
                                .clickable { category = key }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) MaterialTheme.colorScheme.onPrimary else palette.text2)
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛍️", fontSize = 44.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("No rewards yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap ⋮ → Manage to add one", fontSize = 12.5.sp, color = palette.text3)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown, key = { it.id }) { reward ->
                        RewardRow(reward = reward, balance = state.balance, palette = palette, onClick = { onOpenDetail(reward.id) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StoreHero(balance: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(RewardsColors.StoreHeroGradient))
            .padding(20.dp, 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("BALANCE", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🪙", fontSize = 26.sp)
                Spacer(Modifier.width(8.dp))
                Text(balance.toLocaleString(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text("🎁", fontSize = 50.sp)
    }
}

@Composable
private fun RewardRow(reward: Reward, balance: Int, palette: RewardsPalette, onClick: () -> Unit) {
    val cat = rewardCategory(reward.category)
    val afford = balance >= reward.coinCost
    val oos = reward.stock != null && reward.stock <= 0

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .clickable(enabled = true) { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(cat.bg), contentAlignment = Alignment.Center) {
            Text(cat.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(reward.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(cat.bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(cat.label, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = cat.color)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${reward.coinCost} 🪙",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (afford) RewardsColors.Afford else RewardsColors.CantAfford
            )
        }
        if (oos) {
            Text("Out of Stock", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.text3)
        }
    }
}

private fun Int.toLocaleString(): String = "%,d".format(this)
