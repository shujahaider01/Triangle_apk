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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.circle.CircleMember
import com.triangle.app.data.PriorityXp
import com.triangle.app.ui.theme.TriangleBrandPurple

/**
 * Shared by CreateEditTaskScreen/CreateEditHabitScreen (Milestone 3's
 * Connect->Assign->Complete->Earn redesign, see the approved plan) — the
 * Priority segmented picker that XP/points are derived from
 * (data/PriorityXp.kt), rather than freely entered.
 */

/**
 * Collapsed "Assign to" row shown on the Create form — tapping it opens
 * AssignMembersScreen (the full-screen search+multi-select picker) rather
 * than picking recipients inline.
 */
@Composable
fun AssignSummaryRow(members: List<CircleMember>, selectedUids: Set<String>, onClick: () -> Unit) {
    val selected = members.filter { it.uid in selectedUids }
    Column {
        Text("Assign to", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected.isEmpty()) {
                Text(
                    "Choose from your Circle",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Row {
                        selected.take(4).forEach { m ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(TriangleBrandPurple.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text((m.name.firstOrNull() ?: '?').uppercase(), color = TriangleBrandPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (selected.size == 1) selected.first().name else "${selected.size} people selected",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PriorityPicker(selected: String, onSelect: (String) -> Unit) {
    Column {
        Text("Priority", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriorityXp.LABELS.keys.forEach { key ->
                val active = selected == key
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelect(key) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        PriorityXp.labelFor(key),
                        fontSize = 12.5.sp,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Awards ${PriorityXp.xpFor(selected)} XP on completion",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyCircleState(modifier: Modifier = Modifier, onFindPeople: () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🤝", fontSize = 44.sp)
            Spacer(Modifier.height(10.dp))
            Text("Your Circle is empty", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Connect with someone before you can assign them a task or habit.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFindPeople) { Text("Find people") }
        }
    }
}
