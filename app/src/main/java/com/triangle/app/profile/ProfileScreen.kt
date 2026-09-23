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
import androidx.compose.material.icons.filled.EmojiEvents
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
    val tasksNavPane by TasksHabitsUiPrefs.lastActivePaneFlow(context.applicationContext, session.uid).collectAsState(initial = 0)
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
        topBar = { state ->
            TopAppBar(
                title = { Text(state.name.ifBlank { name }, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    )
}

@Composable
private fun ProfileScreenContent(
    viewModel: ProfileViewModel,
    topBar: @Composable (ProfileUiState) -> Unit = {},
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
        topBar = { topBar(state) },
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
                else -> Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                    ProfileHero(
                        topInset = padding.calculateTopPadding(),
                        state = state,
                        palette = palette,
                        editable = !viewModel.isReadOnly,
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
    state: ProfileUiState,
    palette: ProfilePalette,
    editable: Boolean,
    uploadingPhoto: Boolean,
    onAvatarClick: () -> Unit
) {
    val gold = Color(0xFFFFD86B)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF4A2FBF), Color(0xFF7B3FE4), Color(0xFF9D6BFF))))
            .drawBehind {
                // Soft decorative circles for depth.
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.38f, center = Offset(size.width * 0.95f, size.height * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.28f, center = Offset(size.width * 0.05f, size.height * 1.0f))
            }
            .padding(20.dp, 28.dp + topInset, 20.dp, 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(76.dp)
                        .border(2.5.dp, Brush.linearGradient(listOf(gold, Color.White.copy(alpha = 0.85f))), CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
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
                        Text(state.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                    if (uploadingPhoto) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                }
                // Camera hint only until a photo exists (the avatar itself stays tappable after that).
                if (editable && state.photoUrl == null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(ProfileColors.Purple)
                            .clickable(onClick = onAvatarClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Change profile photo", tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(state.name.ifBlank { "You" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                Text(state.handle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Level ${state.level} · ${state.tier}",
                        color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(gold).padding(horizontal = 10.dp, vertical = 5.dp).align(Alignment.Top),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFF5A3A00), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("#${state.rank}", color = Color(0xFF3B2494), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.22f))) {
            Box(
                Modifier
                    .fillMaxWidth((state.xpIntoLevel / 500f).coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(Color.White, gold)))
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.xpIntoLevel} / 500 XP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("to next level", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        state.photoError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
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
