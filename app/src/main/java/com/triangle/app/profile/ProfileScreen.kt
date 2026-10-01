package com.triangle.app.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TasksHabitsUiPrefs
import com.triangle.app.navigation.AppBottomNav
import com.triangle.app.navigation.BottomNavTab
import com.triangle.app.tasks.PhotoCropView
import com.triangle.app.ui.theme.TrianglePageBgDark
import com.triangle.app.ui.theme.TrianglePageGradientLight
import com.triangle.app.ui.theme.triangleDarkTheme
import com.triangle.app.util.decodeImageUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native port of renderInternProfile() — sticky purple hero + Overview/
 * Analytics/Achievement tab switcher. This is the CURRENT user's own
 * profile, reached from the bottom nav — editable (photo upload) and with
 * the Home/Tasks/Profile bar instead of a back button. See
 * PublicProfileScreen below for the read-only "someone else's profile"
 * variant tapped from the Leaderboard.
 */
@Composable
fun ProfileScreen(
    session: SessionStore.Session,
    onOpenHome: () -> Unit,
    onOpenTasks: () -> Unit
) {
    val viewModel: ProfileViewModel = viewModel(factory = viewModelFactory { initializer { ProfileViewModel(session) } })
    val context = LocalContext.current
    val tasksNavPane by TasksHabitsUiPrefs.lastActivePaneFlow(context.applicationContext, session.uid).collectAsState(initial = 1)
    ProfileScreenContent(
        viewModel = viewModel,
        bottomBar = {
            AppBottomNav(
                active = BottomNavTab.PROFILE,
                tasksLabel = if (tasksNavPane == 0) "Tasks" else "Habits",
                onHome = onOpenHome,
                onTasks = onOpenTasks,
                onProfile = {}
            )
        }
    )
}

/**
 * Read-only peek at someone else's Overview/Analytics/Achievement tabs —
 * tapped from a Leaderboard podium card or row. Same ProfileViewModel and
 * the same three tab composables as the owner's own Profile screen (they
 * already only ever read `state`, never call back into anything
 * write-specific), just backed by the read-only uid/orgId constructor and
 * a back button instead of the bottom nav.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    uid: String,
    orgId: String,
    name: String,
    username: String?,
    photoUrl: String?,
    onBack: () -> Unit
) {
    val viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory { initializer { ProfileViewModel(uid, orgId, name, username, photoUrl) } }
    )
    ProfileScreenContent(
        viewModel = viewModel,
        onBack = onBack
    )
}

@Composable
private fun ProfileScreenContent(
    viewModel: ProfileViewModel,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val palette = profilePalette()
    val dark = triangleDarkTheme()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedRawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decodingPhoto by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        decodingPhoto = true
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeImageUri(context, uri) }
            decodingPhoto = false
            if (bmp != null) pickedRawBitmap = bmp
        }
    }
    // Google sign-in/consent screen for the Drive upload — only actually
    // shown when the account hasn't already granted drive.file access (see
    // ProfileViewModel.requestPhotoUpload's hasResolution() check).
    val driveAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onPhotoAuthorizationResult(context, result.data)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(

        bottomBar = bottomBar
    ) { padding ->
        // Same orange->pink->purple->blue page gradient as Dashboard/Tasks.
        val bg = if (dark) Modifier.background(TrianglePageBgDark) else Modifier.background(Brush.linearGradient(TrianglePageGradientLight))
        Box(Modifier.fillMaxSize().then(bg)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProfileColors.Purple)
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Something went wrong", color = MaterialTheme.colorScheme.error)
                }
                // Full padding here (top included) — for the own-profile case
                // (no real topBar) that's just the status-bar safe-area inset,
                // and ProfileHero's purple background still extends edge-to-edge
                // behind the status bar since the Box behind this Column isn't
                // padded, only the Column's content is. For the read-only
                // PublicProfileScreen case, this is the real TopAppBar's height.
                else -> {
                    // The whole page scrolls as one; for your own profile a thin sticky bar
                    // (name fades in, share stays put, hairline appears) rides over the top.
                    val scroll = rememberScrollState()
                    val readOnly = viewModel.isReadOnly
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = padding.calculateBottomPadding())) {
                            ProfileHero(
                                topInset = padding.calculateTopPadding(),
                                barSpace = StickyBarHeight,
                                state = state,
                                editable = !readOnly,
                                uploadingPhoto = state.photoUploading || decodingPhoto,
                                onAvatarClick = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            )
                            ProfileTabBar(state.tab, palette, onSelect = viewModel::selectTab)
                            when (state.tab) {
                                ProfileTab.Overview -> OverviewTab(state, palette)
                                ProfileTab.Analytics -> AnalyticsTab(state, palette, onMonthChange = viewModel::changeAnalyticsMonth, onYearChange = viewModel::changeAnalyticsYear)
                                ProfileTab.Achievement -> AchievementTab(state, palette)
                            }
                        }
                        ProfileStickyBar(
                            name = state.name.ifBlank { "You" },
                            scrollPx = scroll.value,
                            topInset = padding.calculateTopPadding(),
                            onBack = if (readOnly) onBack else null,
                            onShare = { scope.launch { runCatching { shareProfile(context, state) } } }
                        )
                    }
                }
            }
        }
    }

    pickedRawBitmap?.let { raw ->
        PhotoCropView(
            sourceBitmap = raw,
            onConfirm = { cropped ->
                viewModel.requestPhotoUpload(context, cropped) { request -> driveAuthLauncher.launch(request) }
                pickedRawBitmap = null
            },
            onCancel = { pickedRawBitmap = null },
            circularFrame = true
        )
    }
    }
}

@Composable
private fun ProfileHero(
    topInset: androidx.compose.ui.unit.Dp,
    barSpace: androidx.compose.ui.unit.Dp,
    state: ProfileUiState,
    editable: Boolean,
    uploadingPhoto: Boolean,
    onAvatarClick: () -> Unit
) {
    val dark = triangleDarkTheme()
    val statColor = if (dark) Color(0xFFB7ACFF) else Color(0xFF5B3FD6)
    val chipBg = if (dark) Color(0xFF2E2A45) else Color.White
    val editBg = if (dark) Color(0xFF4A3D8C) else Color(0xFFD9CFFF)
    val editFg = if (dark) Color(0xFFEDE9FF) else Color(0xFF3B2494)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            // Room for the sticky top bar that sits over the header (see ProfileStickyBar).
            .padding(top = topInset + barSpace + 4.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(editBg)
                .then(if (editable) Modifier.clickable(onClick = onAvatarClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (state.photoUrl != null) {
                AsyncImage(
                    model = state.photoUrl,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(state.name.firstOrNull()?.uppercase() ?: "?", color = editFg, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            if (uploadingPhoto) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            state.name.ifBlank { "You" },
            fontSize = 26.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(state.handle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))

        // Three equal-width columns (like the shared picture), so the gaps between them are even.
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            HeroStat("${state.performancePct}%", "performance", statColor, Modifier.weight(1f))
            HeroStat(state.points.toString(), "points", statColor, Modifier.weight(1f))
            HeroStat(state.ratingAverage?.let { "%.1f".format(it) } ?: "–", "rating", statColor, Modifier.weight(1f))
        }

        // Level progress: bar with a star at the end, and how far the next level is.
        Spacer(Modifier.height(24.dp))
        val trackColor = if (dark) Color(0xFF45397F) else Color(0xFFD3C9F7)
        val fillColor = if (dark) Color(0xFFA99DFF) else Color(0xFF6D4DE0)
        val dotColor = if (dark) Color(0xFF1A1440) else Color(0xFF2A1B6B)
        val levelSize = 500f
        val fraction = (state.xpIntoLevel / levelSize).coerceIn(0.07f, 1f)
        val pointsAway = (levelSize.toInt() - state.xpIntoLevel).coerceAtLeast(0)
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(22.dp)) {
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(trackColor))
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(6.dp).clip(CircleShape).background(dotColor.copy(alpha = 0.7f)))
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(fraction).height(12.dp).clip(RoundedCornerShape(6.dp)).background(fillColor)) {
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = 5.dp).size(6.dp).clip(CircleShape).background(dotColor))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).clip(CircleShape).background(trackColor), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(pointsAway.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(" points away from Level ${state.level + 1}", fontSize = 16.sp)
            }
        }
        state.photoError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 14.sp)
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

private val StickyBarHeight = 56.dp

/**
 * Own-profile top bar: transparent over the header until the page scrolls, then
 * it fills in, shows a hairline underneath and fades the name in at the left
 * (as the big name scrolls away). The share button stays at the right throughout.
 */
@Composable
private fun ProfileStickyBar(name: String, scrollPx: Int, topInset: androidx.compose.ui.unit.Dp, onBack: (() -> Unit)? = null, onShare: () -> Unit) {
    val dark = triangleDarkTheme()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val barColor = if (dark) TrianglePageBgDark else Color(0xFFF3E6EE) // matches the page gradient behind the header
    val chipBg = if (dark) Color(0xFF2E2A45) else Color.White
    // Bar fills in over the first 24dp of scroll; the name fades in as the avatar passes under it.
    val barAlpha = (scrollPx / with(density) { 24.dp.toPx() }).coerceIn(0f, 1f)
    val nameAlpha = ((scrollPx - with(density) { 90.dp.toPx() }) / with(density) { 60.dp.toPx() }).coerceIn(0f, 1f)

    Box(Modifier.fillMaxWidth().height(topInset + StickyBarHeight)) {
        Box(Modifier.matchParentSize().background(barColor.copy(alpha = barAlpha)))
        androidx.compose.material3.HorizontalDivider(
            Modifier.align(Alignment.BottomCenter),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = barAlpha)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = topInset).height(StickyBarHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(chipBg).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                name,
                fontSize = 22.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = nameAlpha }
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(chipBg).clickable(onClick = onShare),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share profile", modifier = Modifier.size(20.dp))
            }
        }
    }
}
