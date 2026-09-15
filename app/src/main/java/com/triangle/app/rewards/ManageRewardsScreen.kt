package com.triangle.app.rewards

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.Reward

/** Native port of the catalog-management part of renderAdminRewards() (script.js:15755-16468) — Manage tab only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageRewardsScreen(
    viewModel: RewardsViewModel,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val palette = rewardsPalette()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Rewards", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) { Icon(Icons.Default.Add, contentDescription = "Add Reward") }
        }
    ) { padding ->
        if (state.rewards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛍️", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No rewards yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + to add one", fontSize = 12.5.sp, color = palette.text3)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.rewards, key = { it.id }) { reward ->
                    ManageRewardRow(reward, palette, onEdit = { onEdit(reward.id) }, onDelete = { pendingDeleteId = reward.id })
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete this reward?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReward(deleteId) {}
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ManageRewardRow(reward: Reward, palette: RewardsPalette, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cat = rewardCategory(reward.category)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .clickable { onEdit() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(cat.bg), contentAlignment = Alignment.Center) {
            Text(cat.emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(reward.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text("${reward.coinCost} 🪙 · ${reward.stock?.let { "$it in stock" } ?: "Unlimited"}", fontSize = 11.5.sp, color = palette.text3)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
    }
}
