package com.triangle.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.triangle.app.MainActivity
import com.triangle.app.auth.LoginScreen
import com.triangle.app.auth.SignupScreen
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
import com.triangle.app.rewards.CreateEditRewardScreen
import com.triangle.app.rewards.ManageRewardsScreen
import com.triangle.app.rewards.RewardDetailScreen
import com.triangle.app.rewards.RewardHistoryScreen
import com.triangle.app.rewards.RewardSettingsScreen
import com.triangle.app.rewards.RewardStoreScreen
import com.triangle.app.rewards.RewardWalletScreen
import com.triangle.app.rewards.RewardsViewModel
import com.triangle.app.tasks.AddNoteScreen
import com.triangle.app.tasks.CreateEditHabitScreen
import com.triangle.app.tasks.CreateEditTaskScreen
import com.triangle.app.tasks.HabitAnalyticsScreen
import com.triangle.app.tasks.HabitDetailScreen
import com.triangle.app.tasks.TaskDetailScreen
import com.triangle.app.tasks.TasksHabitsScreen
import com.triangle.app.tasks.TasksHabitsViewModel
import com.triangle.app.settings.BackupRestoreScreen
import com.triangle.app.settings.BackupRestoreViewModel
import com.triangle.app.settings.ChangePasswordScreen
import com.triangle.app.settings.SettingsScreen

private const val ROUTE_LOGIN = "login"
private const val ROUTE_SIGNUP = "signup"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CHANGE_PASSWORD = "changePassword"
private const val ROUTE_BACKUP_RESTORE = "backupRestore"
private const val ROUTE_LEADERBOARD = "leaderboard"
private const val ROUTE_DM_GRAPH = "dmGraph"
private const val ROUTE_DM_INBOX = "dmInbox"
private const val ROUTE_DM_NEW_MESSAGE = "dmNewMessage"
private const val ROUTE_DM_THREAD = "dmThread/{otherUserId}"
private const val ROUTE_REWARDS_GRAPH = "rewardsGraph"
private const val ROUTE_REWARDS_STORE = "rewardsStore"
private const val ROUTE_REWARD_DETAIL = "rewardDetail/{rewardId}"
private const val ROUTE_REWARDS_WALLET = "rewardsWallet"
private const val ROUTE_REWARDS_HISTORY = "rewardsHistory"
private const val ROUTE_REWARDS_MANAGE = "rewardsManage"
private const val ROUTE_CREATE_REWARD = "createReward"
private const val ROUTE_EDIT_REWARD = "editReward/{rewardId}"
private const val ROUTE_REWARDS_SETTINGS = "rewardsSettings"
private const val ROUTE_TASKS_GRAPH = "tasksGraph"
private const val ROUTE_TASKS_LIST = "tasksList"
private const val ROUTE_TASK_DETAIL = "taskDetail/{taskId}"
private const val ROUTE_CREATE_TASK = "createTask"
private const val ROUTE_EDIT_TASK = "editTask/{taskId}"
private const val ROUTE_HABIT_DETAIL = "habitDetail/{habitId}"
private const val ROUTE_CREATE_HABIT = "createHabit"
private const val ROUTE_EDIT_HABIT = "editHabit/{habitId}"
private const val ROUTE_ADD_TASK_NOTE = "addTaskNote/{taskId}"
private const val ROUTE_HABIT_ANALYTICS = "habitAnalytics/{habitId}"

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

    if (!sessionLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val startDestination = if (session == null) ROUTE_LOGIN else ROUTE_DASHBOARD

    fun handleLoggedIn(newSession: SessionStore.Session) {
        session = newSession
        navController.navigate(ROUTE_DASHBOARD) { popUpTo(0) }
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
        composable(ROUTE_DASHBOARD) {
            val currentSession = session
            if (currentSession == null) {
                LaunchedEffect(Unit) { navController.navigate(ROUTE_LOGIN) { popUpTo(0) } }
            } else {
                // A notification was tapped — route to the native
                // Notifications screen. Keyed on both the session uid AND
                // MainActivity's pendingDeepLinkGeneration counter so a
                // warm-start tap (user already sitting on Dashboard) also
                // re-triggers this check — keying on uid alone would only
                // ever fire once per login, silently dropping a second tap.
                LaunchedEffect(currentSession.uid, activity.pendingDeepLinkGeneration) {
                    if (activity.hasPendingDeepLink()) {
                        activity.consumePendingDeepLink()
                        navController.navigate(ROUTE_NOTIFICATIONS)
                    }
                }
                DashboardScreen(
                    session = currentSession,
                    onOpenTasks = { navController.navigate(ROUTE_TASKS_GRAPH) },
                    onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
                    onOpenRewards = { navController.navigate(ROUTE_REWARDS_GRAPH) },
                    onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
                    onOpenDm = { navController.navigate(ROUTE_DM_GRAPH) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onOpenLeaderboard = { navController.navigate(ROUTE_LEADERBOARD) }
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
                ProfileScreen(session = currentSession, onBack = { navController.popBackStack() })
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
                    onOpenDmThread = { otherUserId -> navController.navigate("dmThread/$otherUserId") },
                    onOpenTasks = { navController.navigate(ROUTE_TASKS_GRAPH) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── Settings (Milestone 6) — Sign Out is the critical row here: no
        // native logout existed before this milestone. Clearing session is
        // handled inside SettingsScreen itself (SessionStore.clear() +
        // FirebaseAuth.signOut()); this route just handles the resulting
        // navigation back to a clean login stack.
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
                onOpenChangePassword = { navController.navigate(ROUTE_CHANGE_PASSWORD) },
                onOpenBackupRestore = { navController.navigate(ROUTE_BACKUP_RESTORE) },
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
                LeaderboardScreen(viewModel = leaderboardViewModel, onBack = { navController.popBackStack() })
            }
        }

        // ── DM (Milestone 5) — one DmViewModel shared across every screen in
        // this nested graph (inbox/new-message/thread), scoped to the
        // graph's own NavBackStackEntry, same pattern as Tasks/Rewards.
        // dmThread is ALSO reachable directly from outside this graph (a
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

        // ── Rewards (Milestone 4) — one RewardsViewModel shared across every
        // screen in this nested graph (store/detail/wallet/history/manage/
        // create/edit/settings), scoped to the graph's own NavBackStackEntry,
        // same pattern as the Tasks graph below.
        navigation(startDestination = ROUTE_REWARDS_STORE, route = ROUTE_REWARDS_GRAPH) {
            composable(ROUTE_REWARDS_STORE) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_REWARDS_GRAPH) }
                val rewardsViewModel: RewardsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { RewardsViewModel(currentSession) } }
                )
                RewardStoreScreen(
                    viewModel = rewardsViewModel,
                    onOpenDetail = { id -> navController.navigate("rewardDetail/$id") },
                    onOpenWallet = { navController.navigate(ROUTE_REWARDS_WALLET) },
                    onOpenHistory = { navController.navigate(ROUTE_REWARDS_HISTORY) },
                    onOpenManage = { navController.navigate(ROUTE_REWARDS_MANAGE) },
                    onOpenSettings = { navController.navigate(ROUTE_REWARDS_SETTINGS) }
                )
            }
            composable(ROUTE_REWARD_DETAIL, arguments = listOf(navArgument("rewardId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_REWARDS_GRAPH) }
                val rewardsViewModel: RewardsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { RewardsViewModel(currentSession) } }
                )
                val rewardId = backStackEntry.arguments?.getString("rewardId")
                val reward = rewardsViewModel.rewardById(rewardId ?: "")
                if (reward == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    RewardDetailScreen(
                        viewModel = rewardsViewModel,
                        reward = reward,
                        onRedeemed = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(ROUTE_REWARDS_WALLET) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_REWARDS_GRAPH) }
                val rewardsViewModel: RewardsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { RewardsViewModel(currentSession) } }
                )
                RewardWalletScreen(viewModel = rewardsViewModel, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_REWARDS_HISTORY) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_REWARDS_GRAPH) }
                val rewardsViewModel: RewardsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { RewardsViewModel(currentSession) } }
                )
                RewardHistoryScreen(viewModel = rewardsViewModel, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_REWARDS_MANAGE) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_REWARDS_GRAPH) }
                val rewardsViewModel: RewardsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { RewardsViewModel(currentSession) } }
                )
                ManageRewardsScreen(
                    viewModel = rewardsViewModel,
                    onCreate = { navController.navigate(ROUTE_CREATE_REWARD) },
                    onEdit = { id -> navController.navigate("editReward/$id") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CREATE_REWARD) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_REWARDS_GRAPH) }
                val rewardsViewModel: RewardsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { RewardsViewModel(currentSession) } }
                )
                CreateEditRewardScreen(
                    viewModel = rewardsViewModel,
                    existingReward = null,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_EDIT_REWARD, arguments = listOf(navArgument("rewardId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_REWARDS_GRAPH) }
                val rewardsViewModel: RewardsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { RewardsViewModel(currentSession) } }
                )
                val rewardId = backStackEntry.arguments?.getString("rewardId")
                val existingReward = rewardsViewModel.rewardById(rewardId ?: "")
                CreateEditRewardScreen(
                    viewModel = rewardsViewModel,
                    existingReward = existingReward,
                    onSaved = { navController.popBackStack(ROUTE_REWARDS_MANAGE, inclusive = false) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_REWARDS_SETTINGS) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_REWARDS_GRAPH) }
                val rewardsViewModel: RewardsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { RewardsViewModel(currentSession) } }
                )
                RewardSettingsScreen(viewModel = rewardsViewModel, onBack = { navController.popBackStack() })
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
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession) } }
                )
                TasksHabitsScreen(
                    viewModel = tasksViewModel,
                    onOpenTaskDetail = { id -> navController.navigate("taskDetail/$id") },
                    onOpenHabitDetail = { id -> navController.navigate("habitDetail/$id") },
                    onCreateTask = { navController.navigate(ROUTE_CREATE_TASK) },
                    onCreateHabit = { navController.navigate(ROUTE_CREATE_HABIT) }
                )
            }
            composable(ROUTE_TASK_DETAIL, arguments = listOf(navArgument("taskId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession) } }
                )
                val taskId = backStackEntry.arguments?.getString("taskId")
                val tasks by tasksViewModel.tasks.collectAsState()
                val task = tasks.firstOrNull { it.id == taskId }
                if (task == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    TaskDetailScreen(
                        session = currentSession,
                        viewModel = tasksViewModel,
                        task = task,
                        canEdit = task.isPersonal && task.createdBy == currentSession.uid,
                        onEdit = { navController.navigate("editTask/${task.id}") },
                        onAddNote = { navController.navigate("addTaskNote/${task.id}") },
                        onBack = { navController.popBackStack() }
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
            composable(ROUTE_CREATE_TASK) {
                val currentSession = session ?: return@composable
                CreateEditTaskScreen(
                    session = currentSession,
                    existingTask = null,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_EDIT_TASK, arguments = listOf(navArgument("taskId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession) } }
                )
                val taskId = backStackEntry.arguments?.getString("taskId")
                val tasks by tasksViewModel.tasks.collectAsState()
                val task = tasks.firstOrNull { it.id == taskId }
                CreateEditTaskScreen(
                    session = currentSession,
                    existingTask = task,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack(ROUTE_TASKS_LIST, inclusive = false) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_HABIT_DETAIL, arguments = listOf(navArgument("habitId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession) } }
                )
                val habitId = backStackEntry.arguments?.getString("habitId")
                val habits by tasksViewModel.habits.collectAsState()
                val habit = habits.firstOrNull { it.id == habitId }
                if (habit == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    HabitDetailScreen(
                        session = currentSession,
                        viewModel = tasksViewModel,
                        habit = habit,
                        onEdit = { navController.navigate("editHabit/${habit.id}") },
                        onOpenAnalytics = { navController.navigate("habitAnalytics/${habit.id}") },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(ROUTE_HABIT_ANALYTICS, arguments = listOf(navArgument("habitId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession) } }
                )
                val habitId = backStackEntry.arguments?.getString("habitId")
                val habits by tasksViewModel.habits.collectAsState()
                val habit = habits.firstOrNull { it.id == habitId }
                if (habit == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    HabitAnalyticsScreen(
                        session = currentSession,
                        viewModel = tasksViewModel,
                        habit = habit,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(ROUTE_CREATE_HABIT) {
                val currentSession = session ?: return@composable
                CreateEditHabitScreen(
                    session = currentSession,
                    existingHabit = null,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_EDIT_HABIT, arguments = listOf(navArgument("habitId") { type = NavType.StringType })) { backStackEntry ->
                val currentSession = session ?: return@composable
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(ROUTE_TASKS_GRAPH) }
                val tasksViewModel: TasksHabitsViewModel = viewModel(
                    graphEntry,
                    factory = viewModelFactory { initializer { TasksHabitsViewModel(currentSession) } }
                )
                val habitId = backStackEntry.arguments?.getString("habitId")
                val habits by tasksViewModel.habits.collectAsState()
                val habit = habits.firstOrNull { it.id == habitId }
                CreateEditHabitScreen(
                    session = currentSession,
                    existingHabit = habit,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack(ROUTE_TASKS_LIST, inclusive = false) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
