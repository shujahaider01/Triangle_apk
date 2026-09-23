package com.triangle.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import com.triangle.app.data.AppEnvironment
import com.triangle.app.data.Environment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
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
import com.triangle.app.ui.theme.triangleDarkTheme

/** Which top-level section's bottom-nav tab is currently active. */
enum class BottomNavTab { HOME, TASKS, PROFILE }

/**
 * The "Home / Tasks / Profile" flat bar (.duo-nav/.duo-nav-btn/
 * .duo-nav-pill in style.css — brand purple active tint, not the global
 * accent) — originally Dashboard-only; extracted here so Tasks/Habits and
 * Profile show the same bar too, letting the user jump directly between
 * these three top-level sections instead of always routing back through
 * Dashboard first. The active tab's own callback is never invoked (its item
 * is simply not clickable), matching how Dashboard always treated its own
 * "Home" item before this was shared.
 */
@Composable
fun AppBottomNav(
    active: BottomNavTab,
    onHome: () -> Unit,
    onTasks: () -> Unit,
    onProfile: () -> Unit,
    tasksLabel: String = "Tasks"
) {
    val dark = triangleDarkTheme()
    val barBg = if (dark) TriangleCardBgDark else Color.White
    val text2 = if (dark) TriangleText2Dark else TriangleText2Light

    data class NavItem(val tab: BottomNavTab, val label: String, val icon: ImageVector, val onClick: () -> Unit)

    val items = listOf(
        NavItem(BottomNavTab.HOME, "Home", Icons.Outlined.Home, onHome),
        // TasksHabitsScreen passes "Tasks" or "Habits" here depending on
        // which pane of its two-pane pager is currently showing, so this
        // tab's label follows you as you swipe instead of always reading
        // "Tasks" even while looking at Habits.
        NavItem(BottomNavTab.TASKS, tasksLabel, Icons.Outlined.GridView, onTasks),
        NavItem(BottomNavTab.PROFILE, "Profile", Icons.Outlined.Person, onProfile)
    )

    Column(Modifier.fillMaxWidth()) {
        // Non-prod builds: the environment strip sits directly above the tabs (nothing in prod).
        EnvironmentStrip()
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
                    Icon(item.icon, contentDescription = item.label, tint = color, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(item.label, fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold, color = color)
                }
            }
        }
    }
}

/**
 * Thin strip identifying a non-prod build (Environment & Release Management PRD §13) so it's
 * obvious at a glance which environment's data a test is touching. Shown above the bottom nav
 * on the screens that have one (see [AppBottomNav]); AppNavHost shows it at the very bottom
 * ([padForNavigationBar]) on screens that don't. Renders nothing in prod.
 */
@Composable
fun EnvironmentStrip(modifier: Modifier = Modifier, padForNavigationBar: Boolean = false) {
    val (label, color) = when (AppEnvironment.current) {
        Environment.DEV -> "DEV ENVIRONMENT" to Color(0xFFFF7A1A)
        Environment.QA -> "QA ENVIRONMENT" to Color(0xFF1A73E8)
        Environment.PROD -> return
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(color)
            .then(if (padForNavigationBar) Modifier.navigationBarsPadding() else Modifier)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
