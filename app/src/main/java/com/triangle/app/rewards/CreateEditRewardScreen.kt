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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.Reward

/** Native port of _rwForm()'s save handler (script.js:16799-16820) — no image picker this cut, see the Milestone 4 plan's scope trims. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditRewardScreen(
    viewModel: RewardsViewModel,
    existingReward: Reward?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val isNew = existingReward == null
    var name by remember { mutableStateOf(existingReward?.name ?: "") }
    var description by remember { mutableStateOf(existingReward?.description ?: "") }
    var terms by remember { mutableStateOf(existingReward?.terms ?: "") }
    var category by remember { mutableStateOf(existingReward?.category ?: "physical") }
    var coinCost by remember { mutableStateOf(existingReward?.coinCost ?: 50) }
    var unlimited by remember { mutableStateOf(existingReward?.stock == null) }
    var stockText by remember { mutableStateOf(existingReward?.stock?.toString() ?: "10") }
    var active by remember { mutableStateOf(existingReward?.active ?: true) }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        if (name.isBlank() || saving) return
        saving = true
        val reward = (existingReward ?: Reward(
            id = "rw-${System.currentTimeMillis()}",
            name = "",
            coinCost = 0,
            createdAt = System.currentTimeMillis()
        )).copy(
            name = name.trim(),
            description = description,
            terms = terms,
            category = category,
            coinCost = coinCost.coerceIn(1, 999999),
            stock = if (unlimited) null else stockText.toIntOrNull()?.coerceIn(0, 999999),
            active = active
        )
        viewModel.saveReward(reward) { saving = false; onSaved() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add Reward" else "Edit Reward", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = { save() }, enabled = name.isNotBlank() && !saving) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Reward name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = terms,
                onValueChange = { terms = it },
                label = { Text("Terms (one per line)") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text("Category", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RewardCategories.forEach { (key, info) ->
                        val isActive = category == key
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { category = key }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("${info.emoji} ${info.label}", fontSize = 13.sp, color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Column {
                Text("Coin cost", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { coinCost = (coinCost - 10).coerceAtLeast(1) }) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
                    Text("$coinCost 🪙", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { coinCost = (coinCost + 10).coerceAtMost(999999) }) { Icon(Icons.Default.Add, contentDescription = "Increase") }
                }
            }

            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Unlimited stock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = unlimited, onCheckedChange = { unlimited = it })
                }
                if (!unlimited) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { stockText = it.filter(Char::isDigit) },
                        label = { Text("Stock quantity") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Active", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = active, onCheckedChange = { active = it })
            }

            if (!isNew) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    viewModel.deleteReward(existingReward!!.id) { onSaved() }
                }) {
                    Text("Delete reward", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
