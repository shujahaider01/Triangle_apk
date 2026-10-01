package com.triangle.app.circle

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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.TriangleBrandPurple

private val AssignGreen = Color(0xFF22C55E)
private val AssignRed = Color(0xFFEF4444)

/**
 * Entry point for the Connect->Assign->Complete->Earn social graph — the
 * people a user is connected with, and can therefore assign Tasks/Habits
 * to (see the approved plan at .claude/plans/enchanted-brewing-beacon.md).
 * Incoming connection requests are handled from the Notifications screen,
 * not from here — they arrive as ordinary notifications like everything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleScreen(
    viewModel: CircleViewModel,
    onOpenConnect: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.circle.collectAsState()
    var sheetMember by remember { mutableStateOf<CircleMember?>(null) }
    var query by remember { mutableStateOf("") }
    val filteredMembers = remember(state.members, query) {
        if (query.isBlank()) state.members
        else state.members.filter {
            it.name.contains(query, ignoreCase = true) || it.username?.contains(query, ignoreCase = true) == true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Members", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = onOpenConnect) {
                        Icon(Icons.Default.PersonAddAlt1, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Invite")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.members.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤝", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Your Circle is empty", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Connect with someone to start assigning tasks and habits to each other.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenConnect) { Text("Connect") }
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search members...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Team Members (${state.members.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredMembers, key = { it.uid }) { m -> CircleMemberRow(m, onOpenPermissions = { sheetMember = m }) }
                }
            }
        }

        sheetMember?.let { member ->
            MemberPermissionsSheet(
                member = member,
                onSave = { canAssign, canAnnounce ->
                    viewModel.setAssignPermission(member.uid, canAssign)
                    if (canAnnounce != member.canAnnounce) viewModel.setAnnouncePermission(member.uid, canAnnounce)
                    sheetMember = null
                },
                onRemove = { viewModel.removeMember(member.uid); sheetMember = null },
                onDismiss = { sheetMember = null }
            )
        }
    }
}

@Composable
private fun CircleMemberRow(m: CircleMember, onOpenPermissions: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(m.name, m.photoUrl, size = 42.dp, backgroundColor = TriangleBrandPurple.copy(alpha = 0.18f), textColor = TriangleBrandPurple, fontSize = 16.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (m.username != null) {
                Text("@${m.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!m.canAnnounce) {
                Text("Announcements off", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AssignRed)
            }
        }
        Spacer(Modifier.width(8.dp))
        AssignPermissionPill(canAssign = m.canAssign)
        IconButton(onClick = onOpenPermissions) { Icon(Icons.Default.MoreVert, contentDescription = "Member options") }
    }
}

@Composable
private fun AssignPermissionPill(canAssign: Boolean) {
    val color = if (canAssign) AssignGreen else AssignRed
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (canAssign) Icons.Default.CheckCircle else Icons.Default.Block,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(if (canAssign) "Can assign" else "Cannot assign", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
