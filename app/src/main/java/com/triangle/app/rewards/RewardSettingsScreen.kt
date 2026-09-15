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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RoundingPolicies = listOf("floor" to "Round Down", "round" to "Nearest", "ceil" to "Round Up")

/** Native port of rwSaveSettings() (script.js:16482-16489). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardSettingsScreen(viewModel: RewardsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var xpPerCoin by remember(state.xpPerCoin) { mutableStateOf(state.xpPerCoin.toString()) }
    var roundingPolicy by remember(state.roundingPolicy) { mutableStateOf(state.roundingPolicy) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text("XP per Coin", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("How much XP converts into 1 coin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = xpPerCoin,
                    onValueChange = { xpPerCoin = it.filter(Char::isDigit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column {
                Text("Rounding Policy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundingPolicies.forEach { (key, label) ->
                        val isActive = roundingPolicy == key
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { roundingPolicy = key }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(label, fontSize = 13.sp, color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val rate = xpPerCoin.toIntOrNull()?.coerceIn(1, 10000) ?: return@Button
                    saving = true
                    viewModel.saveSettings(rate, roundingPolicy) { saving = false; onBack() }
                },
                enabled = !saving && (xpPerCoin.toIntOrNull()?.let { it in 1..10000 } == true),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save")
            }
        }
    }
}
