package com.triangle.app.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.SentimentSatisfied
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TasksHabitsUiPrefs
import com.triangle.app.navigation.AppBottomNav
import com.triangle.app.navigation.BottomNavTab
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.TriangleBrandPurpleLight
import com.triangle.app.ui.theme.TriangleCardBgDark
import com.triangle.app.ui.theme.TriangleText2Dark
import com.triangle.app.ui.theme.TriangleText2Light
import com.triangle.app.ui.theme.TrianglePageBgDark
import com.triangle.app.ui.theme.TrianglePageGradientLight
import com.triangle.app.ui.theme.triangleDarkTheme

// ── Design tokens ported 1:1 from style.css (see the same hex values in
// :root's --brand/--brand-light, .points-big, .idash-analytics-box's inline
// gradients in renderInternDashboard()'s analyticsBoxes array, and the
// [data-page="internDashboard"] .content-area page background) — the goal
// of this screen is to look like the same app, not a redesign; only the
// implementation language changed.
private val BrandPurple = TriangleBrandPurple
private val BrandPurpleLight = TriangleBrandPurpleLight
private val PageGradientLight = TrianglePageGradientLight
private val PageBgDark = TrianglePageBgDark
private val CardBgDark = TriangleCardBgDark
private val Text2Light = TriangleText2Light
private val Text2Dark = TriangleText2Dark

private data class Category(
    val name: String,
    val enabled: Boolean,
    val bg: Color,
    val tint: Color,
    val icon: ImageVector,
    val onClick: (() -> Unit)? = null
)

private data class AnalyticsCard(val label: String, val value: String, val gradient: List<Color>, val tint: Color)

/**
 * Native port of script.js's renderInternDashboard() — same layout, colors
 * and copy as the WebView version (minimal header, greeting, purple points
 * card with a progress bar, horizontal category shortcuts, 2-column
 * Analytics grid, bottom nav) — see DashboardViewModel for the ported data
 * computations behind these numbers.
 */
@Composable
fun DashboardScreen(
    session: SessionStore.Session,
    onOpenTasks: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenDm: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenCircle: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = viewModelFactory { initializer { DashboardViewModel(session) } }
    )
    val state by viewModel.uiState.collectAsState()
    val dark = triangleDarkTheme()
    val context = LocalContext.current
    val tasksNavPane by TasksHabitsUiPrefs.lastActivePaneFlow(context.applicationContext, session.uid).collectAsState(initial = 0)

    Scaffold(
        bottomBar = {
            AppBottomNav(
                active = BottomNavTab.HOME,
                tasksLabel = if (tasksNavPane == 0) "Tasks" else "Habits",
                onHome = {},
                onTasks = onOpenTasks,
                onProfile = onOpenProfile
            )
        }
    ) { padding ->
        val bg = if (dark) Modifier.background(PageBgDark) else Modifier.background(Brush.linearGradient(PageGradientLight))
        Box(Modifier.fillMaxSize().then(bg)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandPurple)
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Something went wrong", color = MaterialTheme.colorScheme.error)
                }
                else -> DashboardContent(state, padding, dark, onOpenNotifications, onOpenDm, onOpenSettings, onOpenLeaderboard, onOpenCircle)
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    padding: PaddingValues,
    dark: Boolean,
    onOpenNotifications: () -> Unit,
    onOpenDm: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenCircle: () -> Unit
) {
    val text2 = if (dark) Text2Dark else Text2Light

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Minimal header: hamburger + Circle + notif bell + DM icon ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenCircle) {
                Icon(Icons.Default.People, contentDescription = "My Circle")
            }
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
            }
            IconButton(onClick = onOpenDm) {
                Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Messages")
            }
        }

        Column(Modifier.padding(horizontal = 14.dp)) {
            // ── Greeting ── "Hello, <first name>" per the user's requested
            // reference design, replacing the old time-of-day salutation.
            Text(
                "Hello, ${state.firstName} 👋",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(3.dp))
            Text("Let's make today productive.", fontSize = 13.5.sp, color = text2)
            Spacer(Modifier.height(14.dp))

            // ── Points card ── slightly more compact than before per the
            // user's requested reference design (smaller padding/number/trophy).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(BrandPurple, BrandPurpleLight)))
                    .padding(16.dp, 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TOTAL POINTS",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        state.points.toLocaleString(),
                        color = Color.White,
                        fontSize = 33.sp,
                        fontWeight = FontWeight.Black
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Performance", color = Color.White.copy(alpha = 0.85f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Text("${state.obediencePercent}%", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .width(90.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(state.obediencePercent / 100f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.9f))
                        )
                    }
                }
                Box(
                    Modifier.size(66.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏆", fontSize = 48.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Category shortcuts (horizontal scroll) ──
            val categories = listOf(
                Category("Leaderboard", true, Color(0xFFFEF3C7), Color(0xFFD97706), icon = Icons.Outlined.EmojiEvents, onClick = onOpenLeaderboard),
                Category("Moods", false, Color(0xFFEDE9FE), Color(0xFF8B5CF6), icon = Icons.Outlined.SentimentSatisfied),
                Category("Reviews", false, Color(0xFFDCFCE7), Color(0xFF22C55E), icon = Icons.Outlined.RateReview),
                Category("Attendance", false, Color(0xFFFDE2E2), Color(0xFFEF4444), icon = Icons.Outlined.EventAvailable)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                categories.forEach { cat ->
                    val alpha = if (cat.enabled) 1f else 0.45f
                    Column(
                        modifier = Modifier
                            .width(74.dp)
                            .clickable(enabled = cat.enabled) { cat.onClick?.invoke() },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(cat.bg.copy(alpha = alpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(cat.icon, contentDescription = cat.name, tint = cat.tint.copy(alpha = alpha), modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            cat.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = text2.copy(alpha = alpha),
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Analytics", fontSize = 17.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(10.dp))

            val cards = listOf(
                AnalyticsCard("Total Tasks", state.totalTasksAll.toLocaleString(), listOf(Color(0xFFEFEAFD), Color(0xFFF8F7FE)), Color(0xFF6D5EF5)),
                AnalyticsCard("Tasks Completed", state.tasksCompleted.toLocaleString(), listOf(Color(0xFFE0F7FA), Color(0xFFF2FCFD)), Color(0xFF06B6D4)),
                AnalyticsCard("XP This Week", state.weeklyXP.toLocaleString(), listOf(Color(0xFFFDEEEE), Color(0xFFFEF8F8)), Color(0xFFEF4444)),
                AnalyticsCard("Current Streak", state.currentStreak.toString(), listOf(Color(0xFFFFF1E2), Color(0xFFFFFAF3)), Color(0xFFF97316)),
                AnalyticsCard("Longest Streak", state.longestStreak.toString(), listOf(Color(0xFFE9FBEF), Color(0xFFF6FDF8)), Color(0xFF22C55E)),
                AnalyticsCard("Current Rank", "#${state.rank}", listOf(Color(0xFFEAF2FE), Color(0xFFF6FAFE)), Color(0xFF3B82F6))
            )
            // Hand-rolled 2-column grid (not LazyVerticalGrid) — a fixed
            // small list of cards self-sizing from their own aspect ratio,
            // so it can never under-measure its own height and clip the
            // last row the way a LazyVerticalGrid with a guessed fixed
            // height did.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.chunked(2).forEach { rowCards ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowCards.forEach { card ->
                            val cardBg = if (dark) CardBgDark else null
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(2.2f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (cardBg != null) Brush.linearGradient(listOf(cardBg, cardBg)) else Brush.linearGradient(card.gradient))
                                    .padding(14.dp, 12.dp, 14.dp, 12.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(card.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = text2, modifier = Modifier.padding(bottom = 6.dp))
                                Text(card.value, fontSize = 19.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        if (rowCards.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun Int.toLocaleString(): String = "%,d".format(this)
