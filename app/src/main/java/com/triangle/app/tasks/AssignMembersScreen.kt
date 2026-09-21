package com.triangle.app.tasks

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.circle.CircleMember
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.TriangleBrandPurple

private enum class AssignTab { ALL, RECENT }

/**
 * Full-screen "Assign to" picker, replacing the inline single-row list that
 * used to live directly on Create/Edit Task/Habit. Lets the assigner search
 * their Circle and pick multiple recipients — each selected member gets
 * their own independent copy of the task/habit (AssignmentRepository writes
 * one copy per recipient into that recipient's own org tree; there's no
 * shared "team" or "org" concept in this app, so unlike a generic reference
 * design there is no Team tab — Recent is derived from AssignmentRepository's
 * assignedByMe history instead).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignMembersScreen(
    itemLabel: String,
    members: List<CircleMember>,
    recentUids: List<String>,
    initiallySelected: Set<String>,
    onClose: () -> Unit,
    onDone: (Set<String>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(AssignTab.ALL) }
    var selected by remember { mutableStateOf(initiallySelected) }

    val recentOrder = recentUids.withIndex().associate { (i, uid) -> uid to i }
    val base = when (tab) {
        AssignTab.ALL -> members
        AssignTab.RECENT -> members.filter { it.uid in recentOrder }.sortedBy { recentOrder.getValue(it.uid) }
    }
    val visible = if (query.isBlank()) {
        base
    } else {
        base.filter {
            it.name.contains(query, ignoreCase = true) || (it.username?.contains(query, ignoreCase = true) == true)
        }
    }
    val selectedMembers = members.filter { it.uid in selected }

    fun toggle(uid: String) {
        selected = if (uid in selected) selected - uid else selected + uid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assign $itemLabel", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                actions = {
                    TextButton(onClick = { onDone(selected) }, enabled = selected.isNotEmpty()) {
                        Text("Next")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Assign to", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${selected.size} selected",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search members...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssignTabChip("All", tab == AssignTab.ALL) { tab = AssignTab.ALL }
                    AssignTabChip("Recent", tab == AssignTab.RECENT) { tab = AssignTab.RECENT }
                }
            }
            HorizontalDivider()

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "No one here yet." else "No matches for \"$query\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    items(visible, key = { it.uid }) { member ->
                        MemberRow(member = member, checked = member.uid in selected, onClick = { toggle(member.uid) })
                    }
                }
            }

            if (selectedMembers.isNotEmpty()) {
                Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Selected Members (${selectedMembers.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { selected = emptySet() }) { Text("Clear all") }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(selectedMembers, key = { it.uid }) { member ->
                                SelectedChip(member = member, onRemove = { toggle(member.uid) })
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { onDone(selected) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignTabChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemberRow(member: CircleMember, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(member.name, member.photoUrl, size = 42.dp, backgroundColor = TriangleBrandPurple.copy(alpha = 0.18f), textColor = TriangleBrandPurple, fontSize = 16.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(member.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (member.username != null) Text("@${member.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) TriangleBrandPurple else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SelectedChip(member: CircleMember, onRemove: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box {
            Avatar(member.name, member.photoUrl, size = 48.dp, backgroundColor = TriangleBrandPurple.copy(alpha = 0.18f), textColor = TriangleBrandPurple, fontSize = 16.sp)
            Box(
                Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            member.name.substringBefore(" "),
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
