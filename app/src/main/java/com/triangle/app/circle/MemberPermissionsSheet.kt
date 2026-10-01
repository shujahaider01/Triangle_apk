package com.triangle.app.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.TriangleBrandPurple

private val PermissionGreen = Color(0xFF22C55E)
private val PermissionRed = Color(0xFFEF4444)

private enum class PermissionChoice { CAN_ASSIGN, CANNOT_ASSIGN, REMOVE }

/** "…" menu on a Members row — matches the mockup's "Set Permissions" sheet. See CircleViewModel.setAssignPermission()/removeMember(). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberPermissionsSheet(
    member: CircleMember,
    onSave: (canAssign: Boolean, canAnnounce: Boolean) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var choice by remember(member.uid) {
        mutableStateOf(if (member.canAssign) PermissionChoice.CAN_ASSIGN else PermissionChoice.CANNOT_ASSIGN)
    }
    var allowAnnouncements by remember(member.uid) { mutableStateOf(member.canAnnounce) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(member.name, member.photoUrl, size = 42.dp, backgroundColor = TriangleBrandPurple.copy(alpha = 0.18f), textColor = TriangleBrandPurple, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(member.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (member.username != null) {
                        Text("@${member.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(16.dp))
            Text("Set Permissions", fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))

            PermissionOptionRow(
                icon = Icons.Default.PersonAddAlt1,
                iconTint = PermissionGreen,
                title = "Can assign tasks or habits",
                subtitle = "Can create and assign tasks and habits to other members.",
                selected = choice == PermissionChoice.CAN_ASSIGN,
                accentColor = PermissionGreen,
                onClick = { choice = PermissionChoice.CAN_ASSIGN }
            )
            Spacer(Modifier.height(10.dp))
            PermissionOptionRow(
                icon = Icons.Default.Block,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "Cannot assign tasks or habits",
                subtitle = "Can view tasks and habits, but cannot assign them to others.",
                selected = choice == PermissionChoice.CANNOT_ASSIGN,
                accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { choice = PermissionChoice.CANNOT_ASSIGN }
            )
            Spacer(Modifier.height(10.dp))
            PermissionOptionRow(
                icon = Icons.Default.DeleteOutline,
                iconTint = PermissionRed,
                title = "Remove Member",
                subtitle = "Remove this member from your Circle.",
                selected = choice == PermissionChoice.REMOVE,
                accentColor = PermissionRed,
                onClick = { choice = PermissionChoice.REMOVE }
            )
            if (choice != PermissionChoice.REMOVE) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow announcements", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Let ${member.name.substringBefore(' ')} send you announcements. Turn off to stop receiving them.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = allowAnnouncements, onCheckedChange = { allowAnnouncements = it })
                }
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    when (choice) {
                        PermissionChoice.CAN_ASSIGN -> onSave(true, allowAnnouncements)
                        PermissionChoice.CANNOT_ASSIGN -> onSave(false, allowAnnouncements)
                        PermissionChoice.REMOVE -> onRemove()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = if (choice == PermissionChoice.REMOVE) ButtonDefaults.buttonColors(containerColor = PermissionRed) else ButtonDefaults.buttonColors()
            ) {
                Text(if (choice == PermissionChoice.REMOVE) "Remove Member" else "Save Changes", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PermissionOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (selected) accentColor.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = accentColor)
        )
    }
}
