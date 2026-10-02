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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
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
import com.triangle.app.ui.theme.triangleDarkTheme

/** Which top-level section's bottom-nav tab is currently active. */
enum class BottomNavTab { HOME, HABITS, TASKS, PROFILE }

/** What each bottom-nav control does — provided by AppNavHost so every screen's bar behaves the same. */
class BottomNavActions(
    val onHome: () -> Unit,
    val onHabits: () -> Unit,
    val onTasks: () -> Unit,
    val onProfile: () -> Unit,
    val onQuickAdd: () -> Unit
)

val LocalBottomNavActions = staticCompositionLocalOf<BottomNavActions?> { null }

/**
 * Floating bottom bar: a rounded pill with Home, Habits, Tasks and Profile (the active one sits in a tinted
 * capsule), and a separate round "+" button beside it that opens Quick add. The active tab isn't clickable.
 */
@Composable
fun AppBottomNav(active: BottomNavTab) {
    val actions = LocalBottomNavActions.current ?: return
    val dark = triangleDarkTheme()
    val pill = if (dark) Color(0xFF2A2A30) else Color.White
    val idle = if (dark) Color(0xFFB8B8C2) else Color(0xFF6B6B76)

    data class NavItem(val tab: BottomNavTab, val label: String, val icon: ImageVector, val activeIcon: ImageVector, val onClick: () -> Unit)

    val items = listOf(
        NavItem(BottomNavTab.HOME, "Home", Icons.Outlined.Home, Icons.Rounded.Home, actions.onHome),
        NavItem(BottomNavTab.HABITS, "Habits", Icons.Outlined.Repeat, Icons.Outlined.Repeat, actions.onHabits),
        NavItem(BottomNavTab.TASKS, "Tasks", Icons.Outlined.TaskAlt, Icons.Outlined.TaskAlt, actions.onTasks),
        NavItem(BottomNavTab.PROFILE, "Profile", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle, actions.onProfile)
    )

    Column(Modifier.fillMaxWidth()) {
        // Non-prod builds: the environment strip sits directly above the bar (nothing in prod).
        EnvironmentStrip()
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .shadow(8.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                    .clip(RoundedCornerShape(32.dp))
                    .background(pill)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEach { item ->
                    val isActive = item.tab == active
                    val color = if (isActive) TriangleBrandPurple else idle
                    Column(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(if (isActive) TriangleBrandPurple.copy(alpha = if (dark) 0.28f else 0.14f) else Color.Transparent)
                            .clickable(enabled = !isActive, onClick = item.onClick),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(if (isActive) item.activeIcon else item.icon, contentDescription = item.label, tint = color, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x44000000), spotColor = Color(0x44000000))
                    .clip(RoundedCornerShape(24.dp))
                    .background(TriangleBrandPurple.copy(alpha = 0.82f))
                    .clickable(onClick = actions.onQuickAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Quick add", tint = Color.White, modifier = Modifier.size(30.dp))
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
