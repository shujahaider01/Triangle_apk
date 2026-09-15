package com.triangle.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.TriangleCardBgDark
import com.triangle.app.ui.theme.TriangleText2Dark
import com.triangle.app.ui.theme.TriangleText2Light

/** Which top-level section's bottom-nav tab is currently active. */
enum class BottomNavTab { HOME, TASKS, REWARDS, PROFILE }

/**
 * The "Home / Tasks / Rewards / Profile" flat bar (.duo-nav/.duo-nav-btn/
 * .duo-nav-pill in style.css — brand purple active tint, not the global
 * accent) — originally Dashboard-only; extracted here so Tasks/Habits,
 * Profile, and the Rewards Store show the same bar too, letting the user
 * jump directly between these four top-level sections instead of always
 * routing back through Dashboard first. The active tab's own callback is
 * never invoked (its item is simply not clickable), matching how Dashboard
 * always treated its own "Home" item before this was shared.
 */
@Composable
fun AppBottomNav(
    active: BottomNavTab,
    onHome: () -> Unit,
    onTasks: () -> Unit,
    onRewards: () -> Unit,
    onProfile: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val barBg = if (dark) TriangleCardBgDark else Color.White
    val text2 = if (dark) TriangleText2Dark else TriangleText2Light

    data class NavItem(val tab: BottomNavTab, val label: String, val icon: ImageVector, val onClick: () -> Unit)

    val items = listOf(
        NavItem(BottomNavTab.HOME, "Home", Icons.Default.Home, onHome),
        NavItem(BottomNavTab.TASKS, "Tasks", Icons.AutoMirrored.Filled.List, onTasks),
        NavItem(BottomNavTab.REWARDS, "Rewards", Icons.Default.CardGiftcard, onRewards),
        NavItem(BottomNavTab.PROFILE, "Profile", Icons.Default.Person, onProfile)
    )

    Row(
        modifier = Modifier.fillMaxWidth().background(barBg).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEach { item ->
            val isActive = item.tab == active
            val color = if (isActive) TriangleBrandPurple else text2
            Column(
                modifier = Modifier
                    .clickable(enabled = !isActive, onClick = item.onClick)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(item.icon, contentDescription = item.label, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(item.label, fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold, color = color)
            }
        }
    }
}
