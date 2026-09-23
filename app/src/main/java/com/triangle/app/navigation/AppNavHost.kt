package com.triangle.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.AppEnvironment
import com.triangle.app.announcements.AnnouncementComposeScreen
import com.triangle.app.announcements.AnnouncementViewModel
import com.triangle.app.data.Environment
import com.triangle.app.data.HighlightBus
import com.triangle.app.data.HighlightKind
import com.triangle.app.data.NotificationRepository
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.TriangleOrange
import com.triangle.app.ui.theme.TriangleBrandPurpleLight
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.triangle.app.MainActivity
import com.triangle.app.auth.LoginScreen
import com.triangle.app.auth.SignupScreen
import com.triangle.app.auth.UsernameSetupScreen
import com.triangle.app.circle.CircleScreen
import com.triangle.app.circle.CircleSearchScreen
import com.triangle.app.circle.CircleViewModel
import com.triangle.app.circle.ConnectEmailScreen
import com.triangle.app.circle.ConnectScreen
import com.triangle.app.circle.ConnectionRequestsScreen
import com.triangle.app.dashboard.DashboardScreen
import com.triangle.app.TokenStore
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import com.triangle.app.dm.DmInboxScreen
import com.triangle.app.dm.DmNewMessageScreen
import com.triangle.app.dm.DmThreadScreen
import com.triangle.app.dm.DmViewModel
import com.triangle.app.leaderboard.LeaderboardScreen
import com.triangle.app.leaderboard.LeaderboardViewModel
import com.triangle.app.notifications.NotificationsScreen
import com.triangle.app.notifications.NotificationsViewModel
import com.triangle.app.profile.ProfileScreen
import com.triangle.app.profile.PublicProfileScreen
import com.triangle.app.tasks.AddNoteScreen
import com.triangle.app.tasks.CreateEditHabitScreen
import com.triangle.app.tasks.CreateEditTaskScreen
import com.triangle.app.tasks.HabitAnalyticsScreen
import com.triangle.app.tasks.HabitDetailScreen
import com.triangle.app.tasks.TaskDetailScreen
import com.triangle.app.tasks.TasksHabitsScreen
import com.triangle.app.tasks.TasksHabitsViewModel
import com.triangle.app.settings.ArchivedItemsScreen
import com.triangle.app.settings.BackupRestoreScreen
import com.triangle.app.settings.BackupRestoreViewModel
import com.triangle.app.settings.ChangePasswordScreen
import com.triangle.app.settings.SettingsScreen

private const val ROUTE_LOGIN = "login"
private const val ROUTE_SIGNUP = "signup"
private const val ROUTE_USERNAME_SETUP = "usernameSetup"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CHANGE_PASSWORD = "changePassword"
private const val ROUTE_BACKUP_RESTORE = "backupRestore"
private const val ROUTE_ARCHIVED_ITEMS = "archivedItems"
private const val ROUTE_COMPOSE_ANNOUNCEMENT = "composeAnnouncement"
private const val ROUTE_LEADERBOARD = "leaderboard"
private const val ROUTE_DM_GRAPH = "dmGraph"
private const val ROUTE_DM_INBOX = "dmInbox"
private const val ROUTE_DM_NEW_MESSAGE = "dmNewMessage"
private const val ROUTE_DM_THREAD = "dmThread/{otherUserId}"
private const val ROUTE_CIRCLE_GRAPH = "circleGraph"
private const val ROUTE_CIRCLE = "circle"
private const val ROUTE_CIRCLE_SEARCH = "circleSearch"
private const val ROUTE_CONNECTION_REQUESTS = "connectionRequests"
private const val ROUTE_CONNECT = "connect"
private const val ROUTE_CONNECT_EMAIL = "connectEmail"
private const val ROUTE_TASKS_GRAPH = "tasksGraph"
private const val ROUTE_TASKS_LIST = "tasksList"
private const val ROUTE_TASK_DETAIL = "taskDetail/{taskId}?highlight={highlight}"
private const val ROUTE_CREATE_TASK = "createTask/{isSolo}"
private const val ROUTE_EDIT_TASK = "editTask/{taskId}"
private const val ROUTE_HABIT_DETAIL = "habitDetail/{habitId}?highlight={highlight}"
private const val ROUTE_CREATE_HABIT = "createHabit/{isSolo}"
private const val ROUTE_EDIT_HABIT = "editHabit/{habitId}"
private const val ROUTE_ADD_TASK_NOTE = "addTaskNote/{taskId}"
private const val ROUTE_HABIT_ANALYTICS = "habitAnalytics/{habitId}"

/**
 * Bottom-nav "switch tab" navigation, shared by the three top-level screens
 * that now show AppBottomNav (Dashboard, Tasks/Habits, Profile) — pops
 * everything above Dashboard before pushing the new section,
 * so tapping between tabs never stacks screens indefinitely (back from any
 * of them goes straight to Dashboard, not back through every tab visited).
 */
/**
 * `saveState`/`restoreState` here (Navigation's own "multiple back stacks"
 * mechanism, the same one BottomNavigationView samples use) is what makes
 * switching tabs feel like switching TO an already-running tab instead of
 * relaunching it: without it, popping back to Dashboard on every Home tap
 * (see goHome() below) tore down the Tasks graph's NavBackStackEntry —
 * and with it TasksHabitsViewModel, which had already fully loaded both
 * the live own-tasks listener and the one-shot assigned-groups join — so
 * returning to Tasks re-ran that whole load from zero every single time,
 * not just the first. saveState on the pop keeps that entry (and its
 * ViewModelStore) cached instead of destroyed; restoreState on the way
 * back in reuses it rather than creating a fresh one.
 */
private fun switchTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(ROUTE_DASHBOARD) { inclusive = false; saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Routes a tapped notification to the specific task / habit / chat message and asks
 * the destination screen to highlight it (see HighlightBus). A push only carries a
 * notifId, so its type and target are looked up from the notification record;
 * anything unresolvable falls back to the Notifications list.
 */
private suspend fun routeDeepLink(navController: NavController, session: SessionStore.Session, link: MainActivity.PendingDeepLink) {
    fun openTasks(kind: HighlightKind, id: String, title: String? = null) {
        HighlightBus.post(kind, id, title)
        switchTab(navController, ROUTE_TASKS_GRAPH)
    }

    if (link.reminderKind != null && link.reminderItemId != null) {
        openTasks(if (link.reminderKind == "habit") HighlightKind.HABIT else HighlightKind.TASK, link.reminderItemId)
        return
    }
    val notifId = link.notifId ?: return
    val notification = runCatching { NotificationRepository.getNotification(session.orgId, session.uid, notifId) }.getOrNull()
    if (notification == null) {
        navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true }
        return
    }
    runCatching { NotificationRepository.markRead(session.orgId, session.uid, notifId) }
    val itemId = notification.itemId
    when {
        notification.type == "chat_message" && notification.otherUserId != null -> {
            itemId?.let { HighlightBus.post(HighlightKind.MESSAGE, it) }
            navController.navigate("dmThread/${notification.otherUserId}") { launchSingleTop = true }
        }
        // Announcements render inline in the Notifications list — open it and highlight the card.
        notification.type == "announcement" && itemId != null -> {
            HighlightBus.post(HighlightKind.ANNOUNCEMENT, itemId)
            navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true }
        }
        notification.type == "task_assigned" -> openTasks(HighlightKind.TASK, itemId ?: "", HighlightBus.titleFromAssignedBody(notification.body))
        notification.type == "habit_assigned" -> openTasks(HighlightKind.HABIT, itemId ?: "", HighlightBus.titleFromAssignedBody(notification.body))
        else -> navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true }
    }
}

/** Home tap from within a tab — pop back to the already-live Dashboard instance, saving the tab's own state so switchTab() can restore it later instead of recreating it. */
private fun goHome(navController: NavController) {
    navController.popBackStack(ROUTE_DASHBOARD, inclusive = false, saveState = true)
}

// Same gold used for the "Reward" dot in ic_launcher_foreground.xml/splash_icon.xml.
private val TriangleGoldDot = Color(0xFFF5B942)

/** Guaranteed minimum time SplashGradientScreen stays on screen (see AppNavHost) —
 * long enough for the dot-scatter animation below to fully settle plus a brief
 * hold, short enough not to feel sluggish on a fast session-restore. */
private const val MIN_SPLASH_MILLIS = 1900L

/**
 * Shown while SessionStore's first value is still loading (see AppNavHost
 * below) — the same orange-to-purple diagonal gradient and triangle mark as
 * the launcher icon (ic_launcher_background/foreground), since the system
 * splash screen API can only ever paint a flat color (windowSplashScreenBackground
 * is typed as a @color, not a drawable) and can't reproduce the gradient
 * itself; this takes over the instant that flat-color frame is gone and
 * covers the actual loading wait instead.
 */
@Composable
private fun SplashGradientScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(TriangleBrandPurple, TriangleBrandPurpleLight))),
        contentAlignment = Alignment.Center
    ) {
        SplashLogo(modifier = Modifier.size(360.dp))
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TechDive", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

/**
 * Redraws the same rounded-triangle mark as ic_launcher_foreground.xml, but
 * the three pillar dots (Tasks/Habits/Reward) enter animated: they start
 * merged into one point at the triangle's own center — drawn
 * orange-then-gold-then-purple so the merged blob reads as purple, since
 * purple lands on top — then scatter outward into their resting corners
 * inside the triangle, one smooth ease
 * with no overshoot (a bouncy spring here visibly overshoots past the rest
 * positions and swings back toward merged before resettling — confirmed via
 * a frame-by-frame screen recording, not just a hunch — which reads as a
 * glitch rather than a deliberate effect for a one-shot entrance).
 */
@Composable
private fun SplashLogo(modifier: Modifier = Modifier) {
    val scatter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scatter.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }
    Canvas(modifier = modifier) {
        val s = size.width / 108f

        // Same rounded-triangle path as ic_launcher_foreground.xml's 108-unit viewport.
        val trianglePath = Path().apply {
            moveTo(49f * s, 32.66f * s)
            lineTo(33.02f * s, 60.34f * s)
            quadraticTo(28.02f * s, 69f * s, 38.02f * s, 69f * s)
            lineTo(69.98f * s, 69f * s)
            quadraticTo(79.98f * s, 69f * s, 74.98f * s, 60.34f * s)
            lineTo(59f * s, 32.66f * s)
            quadraticTo(54f * s, 24f * s, 49f * s, 32.66f * s)
            close()
        }
        drawPath(trianglePath, color = Color.White)

        // Same dot rest positions as ic_launcher_foreground.xml (scale 0.42 from centroid).
        val orangeRest = Offset(54f * s, 41.4f * s)
        val purpleRest = Offset(43.09f * s, 60.3f * s)
        val goldRest = Offset(64.91f * s, 60.3f * s)
        // Triangle's own centroid (same (54,54) center ic_launcher_foreground.xml's
        // dot positions are all offset from) — not any one dot's own resting spot.
        val mergedPoint = Offset(54f * s, 54f * s)
        val dotRadius = 7f * s

        fun lerp(rest: Offset) = Offset(
            mergedPoint.x + (rest.x - mergedPoint.x) * scatter.value,
            mergedPoint.y + (rest.y - mergedPoint.y) * scatter.value
        )

        drawCircle(TriangleOrange, dotRadius, lerp(orangeRest))
        drawCircle(TriangleGoldDot, dotRadius, lerp(goldRest))
        drawCircle(TriangleBrandPurple, dotRadius, lerp(purpleRest))
    }
}

/**
 * App-wide navigation shell — every screen is now a native Compose
 * destination (the WebView was retired in Milestone 7). Session data is
 * passed via plain remembered Compose state rather than NavController route
 * arguments — simpler for what is a small, fixed set of screens (not a
 * deep-linkable URL space).
 */
@Composable
fun AppNavHost(activity: MainActivity) {
    val context = LocalContext.current
    val navController = rememberNavController()

    var session by remember { mutableStateOf<SessionStore.Session?>(null) }
    var sessionLoaded by remember { mutableStateOf(false) }
    var minSplashElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(MIN_SPLASH_MILLIS)
        minSplashElapsed = true
    }

    LaunchedEffect(Unit) {
        SessionStore.sessionFlow(context).collect { restored ->
            session = restored
            sessionLoaded = true
            // Milestone 5: native FCM token registration (script.js's own
            // registerDeviceToken(), script.js:928-941) — previously this
            // only ever happened from WebView JS, so a native-only session
            // (never opening a WebView page) was invisible to the push
            // pipeline. TokenStore already caches the token locally
            // (TxpMessagingService.onNewToken()); this just also writes it
            // to Firebase once a session is known.
            if (restored != null) {
                val token = TokenStore.get(context)
                if (token.isNotBlank()) {
                    runCatching { UserRepository.registerDeviceToken(restored.uid, restored.role, token) }
                }
            }
        }
    }

    DisposableEffect(navController) {
        activity.onNativeBackPressed = { navController.popBackStack() }
        onDispose { activity.onNativeBackPressed = null }
    }

    // Screens with the bottom nav show the environment strip inside it (above the tabs); the
    // others get it at the very bottom of the screen.

    Column(Modifier.fillMaxSize()) {
    // With the banner below, this content area ends above the system navigation bar's inset —
    // consume it so screens' own bottom-inset padding doesn't leave a gap above the banner.
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
    ) {
    if (!sessionLoaded || !minSplashElapsed) {
        SplashGradientScreen()
    } else {
    val startDestination = when {
        session == null -> ROUTE_LOGIN
        session?.username.isNullOrBlank() -> ROUTE_USERNAME_SETUP
        else -> ROUTE_DASHBOARD
    }

    // Usernames are mandatory (Connect->Assign->Complete->Earn needs them to
    // find/connect people) — route through setup first instead of straight
    // to Dashboard whenever the freshly-logged-in session doesn't have one
    // yet (new signups, and pre-this-feature accounts on their next login).
    fun handleLoggedIn(newSession: SessionStore.Session) {
        session = newSession
        val target = if (newSession.username.isNullOrBlank()) ROUTE_USERNAME_SETUP else ROUTE_DASHBOARD
        navController.navigate(target) { popUpTo(0) }
    }

    // A notification (push or reminder) was tapped. Handled here, above the NavHost,
    // rather than inside the Dashboard destination: on a warm start the user may be
    // deep in another screen, where Dashboard isn't composed and the tap would
    // silently wait. Keyed on MainActivity's pendingDeepLinkGeneration so a second
    // tap re-triggers it, and on the session so a cold start waits for login/setup.
    val readyForDeepLink = session != null && !session?.username.isNullOrBlank()
    LaunchedEffect(session?.uid, readyForDeepLink, activity.pendingDeepLinkGeneration) {
        val currentSession = session
        if (!readyForDeepLink || currentSession == null || !activity.hasPendingDeepLink()) return@LaunchedEffect
        val link = activity.consumePendingDeepLink() ?: return@LaunchedEffect
        routeDeepLink(navController, currentSession, link)
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                onLoggedIn = ::handleLoggedIn,
                onGoToSignup = { navController.navigate(ROUTE_SIGNUP) }
            )
        }
        composable(ROUTE_SIGNUP) {
            SignupScreen(
                onLoggedIn = ::handleLoggedIn,
                onGoToLogin = { navController.popBackStack() }
            )
        }
        composable(ROUTE_USERNAME_SETUP) {
            val currentSession = session
            if (currentSession == null) {
                LaunchedEffect(Unit) { navController.navigate(ROUTE_LOGIN) { popUpTo(0) } }
            } else {
                UsernameSetupScreen(
                    session = currentSession,
                    onUsernameSet = { updated ->
                        session = updated
                        navController.navigate(ROUTE_DASHBOARD) { popUpTo(0) }
                    },
                    onSignedOut = {
                        session = null
                        navController.navigate(ROUTE_LOGIN) { popUpTo(0) }
                    }
                )
            }
        }
        composable(ROUTE_DASHBOARD) {
            val currentSession = session
            if (currentSession == null) {
                LaunchedEffect(Unit) { navController.navigate(ROUTE_LOGIN) { popUpTo(0) } }
            } else {
                DashboardScreen(
                    session = currentSession,
                    onOpenTasks = { switchTab(navController, ROUTE_TASKS_GRAPH) },
                    onOpenProfile = { switchTab(navController, ROUTE_PROFILE) },
                    onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
                    onOpenDm = { navController.navigate(ROUTE_DM_GRAPH) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onOpenLeaderboard = { navController.navigate(ROUTE_LEADERBOARD) },
                    onOpenCircle = { navController.navigate(ROUTE_CIRCLE_GRAPH) },
                    onOpenArchive = { navController.navigate(ROUTE_ARCHIVED_ITEMS) }
                )
            }
        }

        // ── Profile (Milestone 3) — Overview/Analytics/Achievement tabs, own
        // profile only. Viewing another intern's profile isn't reachable yet
        // (no Leaderboard tap-through ported), so no userId route argument
        // is needed here.
        composable(ROUTE_PROFILE) {
            val currentSession = session
            if (currentSession == null) {
                LaunchedEffect(Unit) { navController.navigate(ROUTE_LOGIN) { popUpTo(0) } }
            } else {
                ProfileScreen(
                    session = currentSession,
                    onOpenHome = { goHome(navController) },
                    onOpenTasks = { switchTab(navController, ROUTE_TASKS_GRAPH) }
                )
            }
        }

        // ── Notifications (Milestone 5) — own destination, not part of the
        // DM graph, since it can be reached both from the Dashboard bell and
        // from an FCM tap deep-link (see the pending-deep-link block above).
        composable(ROUTE_NOTIFICATIONS) {
            val currentSession = session
            if (currentSession == null) {
                LaunchedEffect(Unit) { navController.navigate(ROUTE_LOGIN) { popUpTo(0) } }
            } else {
                val notifViewModel: NotificationsViewModel = viewModel(
                    factory = viewModelFactory { initializer { NotificationsViewModel(currentSession) } }
                )
                NotificationsScreen(
                    viewModel = notifViewModel,
                    onOpenDmThread = { otherUserId, messageId ->
                        messageId?.let { HighlightBus.post(HighlightKind.MESSAGE, it) }
                        navController.navigate("dmThread/$otherUserId")
                    },
                    // Same reveal-and-highlight-in-the-list behavior as tapping the phone
                    // notification itself (see routeDeepLink), not the item's detail page.
                    onOpenTaskDetail = { taskId, body ->
                        HighlightBus.post(HighlightKind.TASK, taskId ?: "", HighlightBus.titleFromAssignedBody(body))
                        switchTab(navController, ROUTE_TASKS_GRAPH)
                    },
                    onOpenHabitDetail = { habitId, body ->
                        HighlightBus.post(HighlightKind.HABIT, habitId ?: "", HighlightBus.titleFromAssignedBody(body))
                        switchTab(navController, ROUTE_TASKS_GRAPH)
                    },
                    onOpenTasks = { navController.navigate(ROUTE_TASKS_GRAPH) },
                    onOpenCircle = { navController.navigate(ROUTE_CIRCLE_GRAPH) },
                    onComposeAnnouncement = { navController.navigate(ROUTE_COMPOSE_ANNOUNCEMENT) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── Announcements — composer, saved groups, and the read-only detail
        // (also the sender's own view, with Edit/Delete). Reached from the
        // Notifications tab's + button / Sent list / a tapped announcement push.
        composable(ROUTE_COMPOSE_ANNOUNCEMENT) {
            val currentSession = session ?: return@composable
            val announcementViewModel: AnnouncementViewModel = viewModel(
                factory = viewModelFactory { initializer { AnnouncementViewModel(currentSession) } }
            )
            AnnouncementComposeScreen(
                viewModel = announcementViewModel,
                onSent = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Settings (Milestone 6) — Sign Out is the critical row here: no
        // native logout existed before this milestone. Clearing session is
        // handled inside SettingsScreen itself (SessionStore.clear() +
        // FirebaseAuth.signOut()); this route just handles the resulting
        // navigation back to a clean login stack.
        composable(ROUTE_SETTINGS) {
            val currentSession = session ?: return@composable
            SettingsScreen(
                session = currentSession,
                onSessionUpdated = { updated -> session = updated },
                onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
                onOpenChangePassword = { navController.navigate(ROUTE_CHANGE_PASSWORD) },
                onOpenBackupRestore = { navController.navigate(ROUTE_BACKUP_RESTORE) },
                onOpenArchivedItems = { navController.navigate(ROUTE_ARCHIVED_ITEMS) },
                onSignedOut = {
                    session = null
                    navController.navigate(ROUTE_LOGIN) { popUpTo(0) }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(ROUTE_CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Backup & Restore (restored, see the Task Notes / Drive OAuth
        // plan) — Drive OAuth is scoped to this feature only; task-note
        // photos upload to Firebase Storage instead, unrelated to whether
        // Drive is connected.
        composable(ROUTE_BACKUP_RESTORE) {
            val currentSession = session
            if (currentSession == null) {
                LaunchedEffect(Unit) { navController.navigate(ROUTE_LOGIN) { popUpTo(0) } }
            } else {
                val backupViewModel: BackupRestoreViewModel = viewModel(
                    factory = viewModelFactory { initializer { BackupRestoreViewModel(currentSession) } }
                )
                BackupRestoreScreen(viewModel = backupViewModel, onBack = { navController.popBackStack() })
            }
        }
        composable(ROUTE_ARCHIVED_ITEMS) {
            val currentSession = session
            if (currentSession == null) {
                LaunchedEffect(Unit) { navController.navigate(ROUTE_LOGIN) { popUpTo(0) } }
            } else {
                ArchivedItemsScreen(session = currentSession, onBack = { navController.popBackStack() })
            }
        }

        // ── Leaderboard (Milestone 6) — one-shot global ranking fetch, no
        // realtime subscription (matches source's own plain fetch()).
        composable(ROUTE_LEADERBOARD) {
            val currentSession = session
            if (currentSession == null) {
                LaunchedEffect(Unit) { navController.navigate(ROUTE_LOGIN) { popUpTo(0) } }
            } else {
                val leaderboardViewModel: LeaderboardViewModel = viewModel(
                    factory = viewModelFactory { initializer { LeaderboardViewModel(currentSession) } }
                )
                LeaderboardScreen(
                    viewModel = leaderboardViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { uid, orgId -> navController.navigate("profileView/$uid/$orgId") }
                )
            }
        }

        // ── Public profile peek (Leaderboard tap-through) — read-only
        // Overview/Analytics/Achievement view of someone else's profile, see
        // ProfileViewModel's uid/orgId constructor and PublicProfileScreen.
        composable(
            "profileView/{uid}/{orgId}",
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType },
                navArgument("orgId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid") ?: return@composable
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            PublicProfileScreen(
                uid = uid,
                orgId = orgId,
                name = "",
                username = null,
                photoUrl = null,
                onBack = { navController.popBackStack() }
            )
        }

        // ── DM (Milestone 5) — one DmViewModel shared across every screen in
        // this nested graph (inbox/new-message/thread), scoped to the
        // graph's own NavBackStackEntry, same pattern as the Tasks graph
        // below. dmThread is ALSO reachable directly from outside this graph (a
        // Notifications tap navigates straight to "dmThread/{otherUserId}"
        // without first visiting the inbox) — NavController resolves it the
        // same way either way since it's a normal (non-nested) route string.
        navigation(startDestination = ROUTE_DM_INBOX, route = ROUTE_DM_GRAPH) {
            composable(ROUTE_DM_INBOX) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_DM_GRAPH) }
                val dmViewModel: DmViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { DmViewModel(currentSession) } }
                )
                DmInboxScreen(
                    viewModel = dmViewModel,
                    onOpenThread = { otherUserId -> navController.navigate("dmThread/$otherUserId") },
                    onNewMessage = { navController.navigate(ROUTE_DM_NEW_MESSAGE) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_DM_NEW_MESSAGE) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_DM_GRAPH) }
                val dmViewModel: DmViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { DmViewModel(currentSession) } }
                )
                DmNewMessageScreen(
                    viewModel = dmViewModel,
                    onSelectUser = { otherUserId ->
                        navController.navigate("dmThread/$otherUserId") { popUpTo(ROUTE_DM_INBOX) }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            // Nested inside this graph (not a top-level sibling composable)
            // so that navigating straight here from Notifications — without
            // ever visiting the inbox — still resolves ROUTE_DM_GRAPH's own
            // back stack entry correctly; Navigation-Compose builds the
            // parent-graph entry automatically when entering any nested
            // destination, wherever the navigate() call originates from.
            composable(ROUTE_DM_THREAD, arguments = listOf(navArgument("otherUserId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_DM_GRAPH) }
                val dmViewModel: DmViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { DmViewModel(currentSession) } }
                )
                val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
                DmThreadScreen(
                    viewModel = dmViewModel,
                    session = currentSession,
                    otherUserId = otherUserId,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── Circle & Connections — one CircleViewModel shared across every
        // screen in this nested graph (list/search/requests), scoped to the
        // graph's own NavBackStackEntry, same pattern as the DM graph above.
        // Part of the Connect->Assign->Complete->Earn model — see
        // .claude/plans/enchanted-brewing-beacon.md. ROUTE_CONNECTION_REQUESTS
        // is ALSO reachable directly from outside this graph (a Notifications
        // tap on a connection_request navigates straight there), same as
        // ROUTE_DM_THREAD above.
        navigation(startDestination = ROUTE_CIRCLE, route = ROUTE_CIRCLE_GRAPH) {
            composable(ROUTE_CIRCLE) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_CIRCLE_GRAPH) }
                val circleViewModel: CircleViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { CircleViewModel(currentSession) } }
                )
                CircleScreen(
                    viewModel = circleViewModel,
                    onOpenConnect = { navController.navigate(ROUTE_CONNECT) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CONNECT) {
                ConnectScreen(
                    onOpenEmail = { navController.navigate(ROUTE_CONNECT_EMAIL) },
                    onOpenUsername = { navController.navigate(ROUTE_CIRCLE_SEARCH) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CONNECT_EMAIL) { backStackEntry ->
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_CIRCLE_GRAPH) }
                val currentSession = session ?: return@composable
                val circleViewModel: CircleViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { CircleViewModel(currentSession) } }
                )
                ConnectEmailScreen(viewModel = circleViewModel, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_CIRCLE_SEARCH) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_CIRCLE_GRAPH) }
                val circleViewModel: CircleViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { CircleViewModel(currentSession) } }
                )
                CircleSearchScreen(viewModel = circleViewModel, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_CONNECTION_REQUESTS) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_CIRCLE_GRAPH) }
                val circleViewModel: CircleViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { CircleViewModel(currentSession) } }
                )
                ConnectionRequestsScreen(viewModel = circleViewModel, onBack = { navController.popBackStack() })
            }
        }

        // ── Tasks + Habits (Milestone 2) — one TasksHabitsViewModel shared
        // across every screen in this nested graph (list/detail/create/edit),
        // scoped to the graph's own NavBackStackEntry so navigating between
        // them keeps seeing the same live tasks/habits/completions state
        // instead of each screen spinning up its own realtime listeners.
        navigation(startDestination = ROUTE_TASKS_LIST, route = ROUTE_TASKS_GRAPH) {
            composable(ROUTE_TASKS_LIST) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession, context.applicationContext) } }
                )
                TasksHabitsScreen(
                    viewModel = tasksViewModel,
                    onOpenTaskDetail = { id -> navController.navigate("taskDetail/$id") },
                    onOpenHabitDetail = { id -> navController.navigate("habitDetail/$id") },
                    onOpenHabitAnalytics = { id -> navController.navigate("habitAnalytics/$id") },
                    onCreateTask = { isSolo -> navController.navigate("createTask/$isSolo") },
                    onCreateHabit = { isSolo -> navController.navigate("createHabit/$isSolo") },
                    onOpenHome = { goHome(navController) },
                    onOpenProfile = { switchTab(navController, ROUTE_PROFILE) }
                )
            }
            composable(
                ROUTE_TASK_DETAIL,
                arguments = listOf(
                    navArgument("taskId") { type = NavType.StringType },
                    navArgument("highlight") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession, context.applicationContext) } }
                )
                val taskId = backStackEntry.arguments?.getString("taskId")
                val highlight = backStackEntry.arguments?.getBoolean("highlight") ?: false
                val tasks by tasksViewModel.tasks.collectAsState()
                val task = tasks.firstOrNull { it.id == taskId }
                if (task == null) {
                    // Only pop if this screen is still the current one — after an
                    // in-screen Delete/Archive confirm already called onBack(), the
                    // item vanishing from the list would otherwise fire a SECOND
                    // pop mid-exit-animation and take the Tasks list with it.
                    LaunchedEffect(Unit) { if (navController.currentBackStackEntry == backStackEntry) navController.popBackStack() }
                } else {
                    TaskDetailScreen(
                        session = currentSession,
                        viewModel = tasksViewModel,
                        task = task,
                        canEdit = task.isPersonal && task.createdBy == currentSession.uid,
                        onEdit = { navController.navigate("editTask/${task.id}") },
                        onDelete = { tasksViewModel.deleteTask(task.id) },
                        onArchive = { tasksViewModel.archiveTask(task.id) },
                        onAddNote = { navController.navigate("addTaskNote/${task.id}") },
                        onBack = { navController.popBackStack() },
                        highlightOnOpen = highlight
                    )
                }
            }
            composable(ROUTE_ADD_TASK_NOTE, arguments = listOf(navArgument("taskId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
                AddNoteScreen(
                    session = currentSession,
                    taskId = taskId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CREATE_TASK, arguments = listOf(navArgument("isSolo") { type = NavType.BoolType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession, context.applicationContext) } }
                )
                val isSolo = backStackEntry.arguments?.getBoolean("isSolo") ?: false
                CreateEditTaskScreen(
                    session = currentSession,
                    viewModel = tasksViewModel,
                    existingTask = null,
                    isSolo = isSolo,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onFindPeople = { navController.navigate(ROUTE_CIRCLE_SEARCH) }
                )
            }
            composable(ROUTE_EDIT_TASK, arguments = listOf(navArgument("taskId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession, context.applicationContext) } }
                )
                val taskId = backStackEntry.arguments?.getString("taskId")
                val tasks by tasksViewModel.tasks.collectAsState()
                val task = tasks.firstOrNull { it.id == taskId }
                CreateEditTaskScreen(
                    session = currentSession,
                    viewModel = tasksViewModel,
                    existingTask = task,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack(ROUTE_TASKS_LIST, inclusive = false) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                ROUTE_HABIT_DETAIL,
                arguments = listOf(
                    navArgument("habitId") { type = NavType.StringType },
                    navArgument("highlight") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession, context.applicationContext) } }
                )
                val habitId = backStackEntry.arguments?.getString("habitId")
                val highlight = backStackEntry.arguments?.getBoolean("highlight") ?: false
                val habits by tasksViewModel.habits.collectAsState()
                val habit = habits.firstOrNull { it.id == habitId }
                if (habit == null) {
                    // See the identical guard on the task-detail route above.
                    LaunchedEffect(Unit) { if (navController.currentBackStackEntry == backStackEntry) navController.popBackStack() }
                } else {
                    HabitDetailScreen(
                        session = currentSession,
                        viewModel = tasksViewModel,
                        habit = habit,
                        canEdit = habit.createdBy == null || habit.createdBy == currentSession.uid,
                        onEdit = { navController.navigate("editHabit/${habit.id}") },
                        onDelete = { tasksViewModel.deleteHabit(habit.id) },
                        onArchive = { tasksViewModel.archiveHabit(habit.id) },
                        onOpenAnalytics = { navController.navigate("habitAnalytics/${habit.id}") },
                        onBack = { navController.popBackStack() },
                        highlightOnOpen = highlight
                    )
                }
            }
            composable(ROUTE_HABIT_ANALYTICS, arguments = listOf(navArgument("habitId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession, context.applicationContext) } }
                )
                val habitId = backStackEntry.arguments?.getString("habitId")
                val habits by tasksViewModel.habits.collectAsState()
                val habit = habits.firstOrNull { it.id == habitId }
                if (habit == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val tasksState by tasksViewModel.uiState.collectAsState()
                    HabitAnalyticsScreen(
                        habit = habit,
                        completions = tasksState.habitCompletions[habit.id] ?: emptyMap(),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(ROUTE_CREATE_HABIT, arguments = listOf(navArgument("isSolo") { type = NavType.BoolType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession, context.applicationContext) } }
                )
                val isSolo = backStackEntry.arguments?.getBoolean("isSolo") ?: false
                CreateEditHabitScreen(
                    session = currentSession,
                    viewModel = tasksViewModel,
                    existingHabit = null,
                    isSolo = isSolo,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onFindPeople = { navController.navigate(ROUTE_CIRCLE_SEARCH) }
                )
            }
            composable(ROUTE_EDIT_HABIT, arguments = listOf(navArgument("habitId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession, context.applicationContext) } }
                )
                val habitId = backStackEntry.arguments?.getString("habitId")
                val habits by tasksViewModel.habits.collectAsState()
                val habit = habits.firstOrNull { it.id == habitId }
                CreateEditHabitScreen(
                    session = currentSession,
                    viewModel = tasksViewModel,
                    existingHabit = habit,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack(ROUTE_TASKS_LIST, inclusive = false) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
    }

    }
    }
}
