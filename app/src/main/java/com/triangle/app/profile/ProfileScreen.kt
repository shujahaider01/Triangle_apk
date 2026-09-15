package com.triangle.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.triangle.app.data.SessionStore
import com.triangle.app.navigation.AppBottomNav
import com.triangle.app.navigation.BottomNavTab

/**
 * Native port of renderInternProfile() — sticky purple hero + Overview/
 * Analytics/Achievement tab switcher (see the Milestone 3 plan: the V/S tab
 * and viewing another intern's profile are both out of scope for this
 * milestone — every native account is a one-person org, see
 * [[project-individual-only-scope]]).
 */
@Composable
fun ProfileScreen(
    session: SessionStore.Session,
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenRewards: () -> Unit
) {
    val viewModel: ProfileViewModel = viewModel(factory = viewModelFactory { initializer { ProfileViewModel(session) } })
    val state by viewModel.uiState.collectAsState()
    val palette = profilePalette()

    Scaffold(
        bottomBar = {
            AppBottomNav(active = BottomNavTab.PROFILE, onHome = onOpenHome, onTasks = onOpenTasks, onRewards = onOpenRewards, onProfile = {})
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProfileColors.Purple)
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Something went wrong", color = MaterialTheme.colorScheme.error)
                }
                else -> Column(Modifier.fillMaxSize().padding(padding)) {
                    ProfileHero(state, palette, onBack)
                    ProfileTabBar(state.tab, palette, onSelect = viewModel::selectTab)
                    when (state.tab) {
                        ProfileTab.Overview -> OverviewTab(state, palette)
                        ProfileTab.Analytics -> AnalyticsTab(state, palette, onMonthChange = viewModel::changeAnalyticsMonth, onYearChange = viewModel::changeAnalyticsYear)
                        ProfileTab.Achievement -> AchievementTab(state, palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(state: ProfileUiState, palette: ProfilePalette, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ProfileColors.Purple)
            .padding(20.dp, 28.dp, 20.dp, 20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            IconButton(onClick = { /* Settings not yet ported */ }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(state.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(state.name.ifBlank { "You" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                Text(state.handle, color = Color.White.copy(alpha = 0.75f), fontSize = 12.5.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${state.level} · ${state.tier}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Rank #${state.rank}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(state.xpIntoLevel / 500f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
        Spacer(Modifier.height(4.dp))
        Text("${state.xpIntoLevel} / 500 XP to next level", color = Color.White.copy(alpha = 0.75f), fontSize = 10.5.sp)
    }
}

@Composable
private fun ProfileTabBar(current: ProfileTab, palette: ProfilePalette, onSelect: (ProfileTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .background(palette.surface2, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ProfileTab.entries.forEach { tab ->
            val active = tab == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) ProfileColors.Purple.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tab.name,
                    color = if (active) ProfileColors.Purple else palette.text2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
