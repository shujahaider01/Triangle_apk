package com.triangle.app.dm

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.triangle.app.data.UserRepository

/** Native port of openDmNewMessagePage()/_dmnRenderSearch() (script.js:1453-1537). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmNewMessageScreen(
    viewModel: DmViewModel,
    onSelectUser: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.search.collectAsState()
    val palette = dmPalette()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Message", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.searchUsers(it) },
                placeholder = { Text("Search by name or email") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Search for someone to message", fontSize = 13.sp, color = palette.text3)
                }
                state.results.isEmpty() && !state.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No one found", fontSize = 13.sp, color = palette.text3)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results, key = { it.uid }) { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.surface2)
                                .clickable { onSelectUser(user.uid) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(DmColors.Accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Text((user.name.firstOrNull() ?: '?').uppercase(), color = DmColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(user.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                if (user.email.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(user.email, fontSize = 11.5.sp, color = palette.text3)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
