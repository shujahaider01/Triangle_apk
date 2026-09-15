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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.Reward
import kotlin.math.max

/** Native port of rwOpenDetail() + rwRedeemNow() (script.js:16855-17033). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardDetailScreen(
    viewModel: RewardsViewModel,
    reward: Reward,
    onRedeemed: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val redeemEvent by viewModel.redeemEvent.collectAsState()
    val palette = rewardsPalette()
    val cat = rewardCategory(reward.category)
    val afford = state.balance >= reward.coinCost
    val oos = reward.stock != null && reward.stock <= 0
    val need = max(0, reward.coinCost - state.balance)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(redeemEvent) {
        when (val event = redeemEvent) {
            is RedeemUiEvent.Success -> { viewModel.consumeRedeemEvent(); onRedeemed() }
            RedeemUiEvent.NotEnoughCoins -> { snackbarHostState.showSnackbar("⚠️ Not enough coins"); viewModel.consumeRedeemEvent() }
            RedeemUiEvent.OutOfStock -> { snackbarHostState.showSnackbar("❌ Out of stock"); viewModel.consumeRedeemEvent() }
            RedeemUiEvent.Failed -> { snackbarHostState.showSnackbar("❌ Redemption failed. Try again."); viewModel.consumeRedeemEvent() }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reward Details", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(cat.bg), contentAlignment = Alignment.Center) {
                Text(cat.emoji, fontSize = 56.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(reward.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = palette.text)
            Spacer(Modifier.height(6.dp))
            Text("${reward.coinCost} 🪙 Coins", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RewardsColors.Afford)

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoCell("Type", cat.label, palette)
                InfoCell("Stock", reward.stock?.toString() ?: "Unlimited", palette)
                InfoCell("Validity", validityText(reward), palette)
            }

            Spacer(Modifier.height(20.dp))
            SectionCard("About this reward", reward.description.ifBlank { "None" }, palette)
            Spacer(Modifier.height(14.dp))
            SectionCard("Terms and Conditions", reward.terms.ifBlank { "None" }, palette)

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("You have", fontSize = 12.sp, color = palette.text3)
                    Text("${state.balance} 🪙", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RewardsColors.Afford)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Need", fontSize = 12.sp, color = palette.text3)
                    Text("$need 🪙", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (need > 0) RewardsColors.CantAfford else RewardsColors.Afford)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { viewModel.redeem(reward) },
                enabled = afford && !oos,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (oos) "Out of Stock" else if (!afford) "⚠️ Not enough coins" else "Redeem Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, palette: RewardsPalette) {
    Column {
        Text(label, fontSize = 11.sp, color = palette.text3)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
    }
}

@Composable
private fun SectionCard(title: String, body: String, palette: RewardsPalette) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface2).padding(14.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.text)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 13.sp, color = palette.text2)
    }
}

private fun validityText(reward: Reward): String {
    val start = reward.validityStart
    val end = reward.validityEnd
    return when {
        start != null && end != null -> "$start – $end"
        end != null -> "Until $end"
        else -> "—"
    }
}
