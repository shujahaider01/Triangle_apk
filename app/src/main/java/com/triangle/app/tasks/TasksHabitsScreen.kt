package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import com.triangle.app.ui.components.ConfettiOverlay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.triangle.app.data.AssignmentRepository
import com.triangle.app.data.HighlightBus
import com.triangle.app.data.HighlightKind
import com.triangle.app.ui.components.deepLinkHighlight
import kotlinx.coroutines.delay
import com.triangle.app.navigation.AppBottomNav
import com.triangle.app.navigation.BottomNavTab
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.TrianglePageBgDark
import com.triangle.app.ui.theme.TrianglePageGradientLight
import com.triangle.app.ui.theme.triangleDarkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Native port of script.js's renderInternTasks() — a swipeable Tasks |
 * Habits two-pane page (HorizontalPager, matching the WebView's
 * scroll-snap two-pane layout) with a shared date/category-filter header.
 * See TasksHabitsViewModel for the shared state both panes read from.
 */
@Composable
fun TasksHabitsScreen(
    viewModel: TasksHabitsViewModel,
    onOpenTaskDetail: (String) -> Unit,
    onOpenHabitDetail: (String) -> Unit,
    onOpenHabitAnalytics: (String) -> Unit,
    onCreateTask: (Boolean) -> Unit,
    onCreateHabit: (Boolean) -> Unit,
    onOpenHome: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val dark = triangleDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var confettiTrigger by remember { mutableIntStateOf(0) }
    fun showTaskCompletedUndo(task: com.triangle.app.data.models.Task) {
        confettiTrigger++
        com.triangle.app.ui.components.CompletionSounds.playTask()
        snackbarScope.launch {
            delay(1700)
            val result = snackbarHostState.showSnackbar("Task completed", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoComplete(task)
        }
    }
    fun showHabitCompletedUndo(habit: com.triangle.app.data.models.Habit) {
        confettiTrigger++
        com.triangle.app.ui.components.CompletionSounds.playHabit()
        snackbarScope.launch {
            delay(1700)
            val result = snackbarHostState.showSnackbar("Habit completed", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoHabitComplete(habit)
        }
    }

    // Scroll state of whichever pane's LazyColumn is currently active —
    // drives the filter/body divider below, which should only appear once
    // the list has actually scrolled away from its top (see that divider's
    // own comment for why).
    val tasksListState = rememberLazyListState()
    val habitsListState = rememberLazyListState()

    // Which pane (Tasks vs Habits) to open on is resolved asynchronously
    // (see TasksHabitsViewModel.initialPane's doc comment — it depends on a
    // DataStore read plus checking for unseen assigned habits), so the pager
    // itself can't be created until that resolves; otherwise it'd always
    // start on Tasks and then visibly jump to Habits a moment later.
    val initialPane by viewModel.initialPane.collectAsState()
    val resolvedPane = initialPane
    if (resolvedPane == null) {
        // Renders before the Scaffold/page-gradient below exists, so without
        // an explicit background this fell back to the raw window background
        // (dark) regardless of the app's own light/dark theme setting.
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }
    val pagerState = rememberPagerState(initialPage = resolvedPane) { 2 }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.setActivePane(it) }
    }

    // A notification tap asked to reveal + highlight one task/habit (see HighlightBus).
    // Phase 1 puts the list in a state where the item is visible (its date/mode, no
    // narrowing filters) — retried as data loads; phase 2 scrolls to the row and lights it.
    val highlightReq by HighlightBus.request.collectAsState()
    val loadedTasks by viewModel.tasks.collectAsState()
    val loadedHabits by viewModel.habits.collectAsState()
    val itemReq = highlightReq?.takeIf { it.kind == HighlightKind.TASK || it.kind == HighlightKind.HABIT }
    var targetId by remember(itemReq) { mutableStateOf<String?>(null) }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(itemReq, loadedTasks, loadedHabits) {
        val req = itemReq ?: return@LaunchedEffect
        if (targetId != null) return@LaunchedEffect
        // Old notifications carry no item id — fall back to the newest item with that name.
        val id = req.itemId.ifBlank {
            if (req.kind == HighlightKind.TASK) loadedTasks.filter { it.title == req.title }.maxByOrNull { it.createdAt }?.id
            else loadedHabits.filter { it.name == req.title }.maxByOrNull { it.createdAt }?.id
        } ?: return@LaunchedEffect
        val ok = if (req.kind == HighlightKind.TASK) viewModel.revealTask(id) else viewModel.revealHabit(id)
        if (ok) targetId = id
    }
    LaunchedEffect(itemReq) {
        val req = itemReq ?: return@LaunchedEffect
        delay(8000)
        if (targetId == null) HighlightBus.clear(req) // never appeared (deleted/archived) — stop waiting
    }
    LaunchedEffect(itemReq, targetId, state.visibleTasks, state.visibleHabits) {
        val req = itemReq ?: return@LaunchedEffect
        val id = targetId ?: return@LaunchedEffect
        val isTask = req.kind == HighlightKind.TASK
        val index = if (isTask) {
            state.visibleTasks.indexOfFirst { (it is TaskRow.Own && it.task.id == id) || (it is TaskRow.Assigned && it.group.itemId == id) }
        } else {
            state.visibleHabits.indexOfFirst { (it is HabitRow.Own && it.habit.id == id) || (it is HabitRow.Assigned && it.group.itemId == id) }
        }
        if (index < 0) return@LaunchedEffect
        pagerState.scrollToPage(if (isTask) 0 else 1)
        (if (isTask) tasksListState else habitsListState).animateScrollToItem(index)
        highlightedId = id
        delay(3000)
        highlightedId = null
        HighlightBus.clear(req)
    }

    // Assigned-out rows are a one-shot join against every recipient's org
    // (see AssignmentRepository.readAssignedGroups) rather than a live
    // subscription, so a recipient completing their copy only shows up
    // once this refires — on resume approximates "reopening the screen",
    // which is how the old standalone Assigned-by-Me screen refreshed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAssignedGroups() }

    // Opening an assigned-out item's own (read-only) detail page is a local
    // full-screen swap, not a nav route — same reasoning as CreateEditTaskScreen's
    // AssignMembersScreen: the group carries a whole Task/Habit + member list
    // graph that a nav argument can't cheaply express. Because it's NOT a real
    // nav destination, the NavController's back stack still thinks we're
    // sitting on the Tasks list underneath — without a BackHandler here,
    // pressing system back (button or gesture) would fall through to
    // MainActivity's onNativeBackPressed and pop the Tasks list itself off
    // the stack, landing on Home instead of just closing this overlay.
    var assignedTaskDetail by remember { mutableStateOf<AssignmentRepository.AssignedTaskGroup?>(null) }
    var assignedHabitDetail by remember { mutableStateOf<AssignmentRepository.AssignedHabitGroup?>(null) }
    var assignedHabitAnalytics by remember { mutableStateOf<AssignmentRepository.AssignedHabitGroup?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    BackHandler(enabled = assignedTaskDetail != null) { assignedTaskDetail = null }
    BackHandler(enabled = assignedHabitDetail != null) { assignedHabitDetail = null }
    BackHandler(enabled = assignedHabitAnalytics != null) { assignedHabitAnalytics = null }
    BackHandler(enabled = showFilterSheet) { showFilterSheet = false }
    assignedTaskDetail?.let { group ->
        AssignedTaskDetailScreen(
            group = group,
            onDelete = { viewModel.deleteTaskAssignment(group.itemId); assignedTaskDetail = null },
            onBack = { assignedTaskDetail = null }
        )
        return
    }
    assignedHabitDetail?.let { group ->
        AssignedHabitDetailScreen(
            group = group,
            dateStr = state.selectedDate.toString(),
            onDelete = { viewModel.deleteHabitAssignment(group.itemId); assignedHabitDetail = null },
            onBack = { assignedHabitDetail = null }
        )
        return
    }
    assignedHabitAnalytics?.let { group ->
        val merged = remember(group) {
            group.members.fold(emptyMap<String, com.triangle.app.data.models.HabitCompletionEntry>()) { acc, member -> acc + member.completions }
        }
        HabitAnalyticsScreen(habit = group.habit, completions = merged, onBack = { assignedHabitAnalytics = null })
        return
    }
    // A full page rather than a bottom sheet, per the user's explicit request
    // — same local-overlay pattern as the other full-screen swaps above.
    if (showFilterSheet) {
        FilterScreen(
            initialFilters = state.filters,
            isHabitsPane = pagerState.currentPage == 1,
            onApply = { viewModel.setFilters(it) },
            onBack = { showFilterSheet = false }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val isSolo = state.itemMode == ItemMode.SOLO
                if (pagerState.currentPage == 0) onCreateTask(isSolo) else onCreateHabit(isSolo)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Create")
            }
        },
        bottomBar = {
            AppBottomNav(
                active = BottomNavTab.TASKS,
                tasksLabel = if (pagerState.currentPage == 0) "Tasks" else "Habits",
                onHome = onOpenHome,
                onTasks = {},
                onProfile = onOpenProfile
            )
        }
    ) { padding ->
        // Same orange->pink->purple->blue page gradient as Dashboard (the
        // user asked for Tasks/Habits and Profile to match it too, rather
        // than staying on a plain background).
        val bg = if (dark) Modifier.background(TrianglePageBgDark) else Modifier.background(Brush.linearGradient(TrianglePageGradientLight))
        // The gradient fills the whole screen edge-to-edge (unpadded) — only
        // the bottom inset (nav bar) is applied to the content Column below;
        // the top (status bar) inset is applied to the white card itself, so
        // the gradient truly extends behind the status bar instead of the
        // page's Scaffold background color peeking through above the card.
        Box(Modifier.fillMaxSize().then(bg)) {
            Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                // The date-header (label + DateStrip) is a white, rounded-bottom
                // card — matches both the WebView's .hd-wrap and the reference's
                // header card. It's now the true top of the page: no back button,
                // per the user's explicit request (system back/gesture still works
                // via MainActivity.onNativeBackPressed).
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (dark) MaterialTheme.colorScheme.surface else Color.White, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    DateHeader(
                        state = state,
                        viewModel = viewModel,
                        activePage = pagerState.currentPage,
                        onOpenFilters = { showFilterSheet = true }
                    )
                }

                Spacer(Modifier.height(8.dp))

                FilterBar(
                    state = state,
                    viewModel = viewModel,
                    activePage = pagerState.currentPage
                )

                // Separates the fixed filter chips above from the scrollable
                // list below — sits outside the HorizontalPager/LazyColumn so
                // it never scrolls away, and shared by both Tasks and Habits
                // since it's above the pager rather than inside either pane.
                // Invisible at rest and only fades in once the active pane's
                // list has actually scrolled away from its top — otherwise
                // it just adds a visible line between two things (filter
                // chips, first list item) that already read as separate.
                val isPaneScrolled by remember {
                    derivedStateOf {
                        val activeState = if (pagerState.currentPage == 0) tasksListState else habitsListState
                        activeState.firstVisibleItemIndex > 0 || activeState.firstVisibleItemScrollOffset > 0
                    }
                }
                val dividerAlpha by animateFloatAsState(
                    targetValue = if (isPaneScrolled) (if (dark) 0.14f else 0.09f) else 0f,
                    label = "taskHabitDividerAlpha"
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dividerAlpha),
                    thickness = 1.dp
                )

                // Tasks and Habits are switched purely by swiping (matches
                // the source's own scroll-snap two-pane layout) — no tap
                // target at the top for it. beyondViewportPageCount = 1 keeps
                // the OTHER page composed at all times instead of only
                // mounting it once the swipe gesture starts — without this,
                // the destination page's whole LazyColumn (icons, heatmaps,
                // etc.) had to inflate mid-gesture, which is what made the
                // Tasks<->Habits swipe itself feel less smooth than scrolling
                // within either page.
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    if (page == 0) {
                        TasksPane(
                            state = state,
                            viewModel = viewModel,
                            listState = tasksListState,
                            onOpenDetail = onOpenTaskDetail,
                            onOpenAssignedDetail = { assignedTaskDetail = it },
                            onTaskCompleted = ::showTaskCompletedUndo,
                            highlightedId = highlightedId
                        )
                    } else {
                        HabitsPane(
                            state = state,
                            viewModel = viewModel,
                            listState = habitsListState,
                            onOpenDetail = onOpenHabitDetail,
                            onOpenAnalytics = onOpenHabitAnalytics,
                            onOpenAssignedDetail = { assignedHabitDetail = it },
                            onOpenAssignedAnalytics = { assignedHabitAnalytics = it },
                            onHabitCompleted = ::showHabitCompletedUndo,
                            highlightedId = highlightedId
                        )
                    }
                }
            }
            ConfettiOverlay(confettiTrigger)
        }
    }
    // Above the Scaffold (so the nav bar can't cover it), low over the environment strip.
    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp))
    }
}

/**
 * The white "hd-wrap" card's contents — date label + filter button and
 * the month-wide DateStrip (see DateStrip.kt). Accent is brand purple,
 * matching the WebView source and the user's own follow-up correction
 * (a gold accent was tried first per an external reference app, but the
 * user asked to go back to purple). The performance-percentage badge that
 * used to sit where the filter button is now replaced it per the user's
 * explicit request when the Filters feature was planned.
 */
@Composable
private fun DateHeader(state: TasksHabitsUiState, viewModel: TasksHabitsViewModel, activePage: Int, onOpenFilters: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = remember(state.selectedDate) { ordinalDateLabel(state.selectedDate) }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)) {
                        append(label.first)
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))) {
                        append(", ${label.second}")
                    }
                },
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            BadgedBox(
                badge = {
                    if (state.filters.activeCount > 0) {
                        Badge(containerColor = TriangleBrandPurple, contentColor = Color.White) {
                            Text(state.filters.activeCount.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            ) {
                IconButton(onClick = onOpenFilters, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filters",
                        tint = if (state.filters.isActive) TriangleBrandPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))
        DateStrip(
            selectedDate = state.selectedDate,
            brandColor = TriangleBrandPurple,
            pctForDate = { d -> if (activePage == 0) viewModel.tasksPctForDate(d) else viewModel.habitsPctForDate(d) },
            onSelect = viewModel::selectDate,
            highlightRange = state.filters.dateRangeHighlight
        )
    }
}

/**
 * "Today"/"Yesterday"/"Tomorrow"/day-name (bold half) + ordinal date like
 * "15th Sep" (lighter half) — always shown per the user's request to
 * "Display full date today, 15th Sep" rather than just "Today" alone.
 */
private fun ordinalDateLabel(date: LocalDate): Pair<String, String> {
    val today = LocalDate.now()
    val dayWord = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE"))
    }
    val day = date.dayOfMonth
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    val ordinal = "$day$suffix ${date.format(DateTimeFormatter.ofPattern("MMM"))}"
    return dayWord to ordinal
}

/**
 * The Shared/Solo mode toggle and its mode-dependent sub-filter pill row —
 * sits transparent on the page gradient, below the white date-header card.
 * Shown on BOTH panes (the sub-filter concept is relationship/category
 * based, not a Task-only stored field, so it works the same for Habits).
 * Which chips appear in the second row depends entirely on the active mode:
 * Shared shows the assignment-relationship chips (All/Assigned to Me/
 * Assigned by Me); Solo shows the old fixed category chips (All/Office/
 * Academic/Personal), since Solo items are the ones that field actually
 * describes now.
 */
@Composable
private fun FilterBar(state: TasksHabitsUiState, viewModel: TasksHabitsViewModel, activePage: Int) {
    val sharedCount = if (activePage == 0) state.sharedTaskCount else state.sharedHabitCount
    val soloCount = if (activePage == 0) state.soloTaskCount else state.soloHabitCount

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Wider gap than the 8.dp between individual chips within each
        // group below — this is the boundary between two DIFFERENT kinds
        // of control (mode toggle vs. sub-filter), not just another chip
        // in the same row, so it reads better with more separation.
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ModeSegment(sharedCount = sharedCount, soloCount = soloCount, active = state.itemMode, onSelect = viewModel::setItemMode)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            when (state.itemMode) {
                ItemMode.SHARED -> SHARED_SUB_FILTER_ICONS.forEach { (filter, icon) ->
                    CategoryPill(filter.label, icon, state.sharedSubFilter == filter) { viewModel.setSharedSubFilter(filter) }
                }
                ItemMode.SOLO -> SOLO_CATEGORY_ICONS.forEach { (label, icon) ->
                    CategoryPill(label, icon, state.soloCategoryFilter == label) { viewModel.setSoloCategoryFilter(label) }
                }
            }
        }
    }
}

private val SHARED_SUB_FILTER_ICONS: List<Pair<SharedSubFilter, ImageVector>> = listOf(
    SharedSubFilter.ALL to Icons.Default.Apps,
    SharedSubFilter.ASSIGNED_TO_ME to Icons.AutoMirrored.Filled.Assignment,
    SharedSubFilter.ASSIGNED_BY_ME to Icons.AutoMirrored.Filled.Send
)

private val SOLO_CATEGORY_ICONS: List<Pair<String, ImageVector>> = listOf(
    "All" to Icons.Default.Apps,
    "Office" to Icons.Default.Work,
    "Academic" to Icons.Default.School,
    "Personal" to Icons.Default.Person
)

/**
 * One joined pill split into "Shared (N)"/"Solo (N)" halves — same visual
 * shape as the old "All/Due" status segment it replaces, just switching a
 * relationship mode instead of a date-status filter now.
 */
@Composable
private fun ModeSegment(sharedCount: Int, soloCount: Int, active: ItemMode, onSelect: (ItemMode) -> Unit) {
    val dark = triangleDarkTheme()
    val border = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.13f)
    Row(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, border, RoundedCornerShape(7.dp))
    ) {
        // IntrinsicSize.Max (not weight — this Row isn't width-constrained
        // by its own parent, so weight alone wouldn't have anything to
        // distribute) makes both halves match whichever label is wider,
        // instead of each sizing to its own text ("Shared (12)" vs. "Solo
        // (3)" previously ending up visibly different widths).
        StatusSegmentItem("Shared ($sharedCount)", active == ItemMode.SHARED, modifier = Modifier.width(IntrinsicSize.Max)) { onSelect(ItemMode.SHARED) }
        StatusSegmentItem("Solo ($soloCount)", active == ItemMode.SOLO, modifier = Modifier.width(IntrinsicSize.Max)) { onSelect(ItemMode.SOLO) }
    }
}

@Composable
private fun StatusSegmentItem(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .background(if (active) TriangleBrandPurple else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

/** Matches .hd-cat-pill/.hd-cat-active — icon + label, bordered pill; active = brand-purple border/text, not filled. */
@Composable
private fun CategoryPill(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val dark = triangleDarkTheme()
    val border = if (active) TriangleBrandPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.13f)
    Row(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active && dark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, border, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (active) TriangleBrandPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = if (active) TriangleBrandPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}

@Composable
private fun TasksPane(
    state: TasksHabitsUiState,
    viewModel: TasksHabitsViewModel,
    listState: LazyListState,
    onOpenDetail: (String) -> Unit,
    onOpenAssignedDetail: (AssignmentRepository.AssignedTaskGroup) -> Unit,
    onTaskCompleted: (com.triangle.app.data.models.Task) -> Unit,
    highlightedId: String?
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.visibleTasks.isEmpty()) {
        EmptyState(emoji = "🎉", message = "All clear for this day")
        return
    }
    var expandedGroup by remember { mutableStateOf<AssignmentRepository.AssignedTaskGroup?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.visibleTasks, key = { row -> when (row) { is TaskRow.Own -> row.task.id; is TaskRow.Assigned -> "assigned-${row.group.itemId}" } }) { row ->
            val rowId = when (row) { is TaskRow.Own -> row.task.id; is TaskRow.Assigned -> row.group.itemId }
            Box(Modifier.deepLinkHighlight(highlightedId == rowId)) {
                when (row) {
                    is TaskRow.Own -> {
                        val done = viewModel.isDone(row.task, state.completions)
                        TaskRowCard(
                            task = row.task,
                            done = done,
                            onClick = { onOpenDetail(row.task.id) },
                            onToggleDone = { viewModel.completeTaskWithUndo(row.task); onTaskCompleted(row.task) }
                        )
                    }
                    is TaskRow.Assigned -> AssignedTaskRowCard(
                        group = row.group,
                        onClick = { onOpenAssignedDetail(row.group) },
                        onShowProgress = { expandedGroup = row.group }
                    )
                }
            }
        }
    }
    expandedGroup?.let { group ->
        AssignedGroupMembersSheet(
            title = group.task.title,
            members = group.members.map { AssignedMemberStatus(it.uid, it.name, it.photoUrl, it.done) },
            onDismiss = { expandedGroup = null }
        )
    }
}

@Composable
private fun HabitsPane(
    state: TasksHabitsUiState,
    viewModel: TasksHabitsViewModel,
    listState: LazyListState,
    onOpenDetail: (String) -> Unit,
    onOpenAnalytics: (String) -> Unit,
    onOpenAssignedDetail: (AssignmentRepository.AssignedHabitGroup) -> Unit,
    onOpenAssignedAnalytics: (AssignmentRepository.AssignedHabitGroup) -> Unit,
    onHabitCompleted: (com.triangle.app.data.models.Habit) -> Unit,
    highlightedId: String?
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.visibleHabits.isEmpty()) {
        EmptyState(emoji = "✨", message = "Nothing scheduled for this day")
        return
    }
    var expandedGroup by remember { mutableStateOf<AssignmentRepository.AssignedHabitGroup?>(null) }
    val dateStr = state.selectedDate.toString()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.visibleHabits, key = { row -> when (row) { is HabitRow.Own -> row.habit.id; is HabitRow.Assigned -> "assigned-${row.group.itemId}" } }) { row ->
            val rowId = when (row) { is HabitRow.Own -> row.habit.id; is HabitRow.Assigned -> row.group.itemId }
            Box(Modifier.deepLinkHighlight(highlightedId == rowId)) {
                when (row) {
                    is HabitRow.Own -> {
                        val completionsForHabit = state.habitCompletions[row.habit.id] ?: emptyMap()
                        HabitCard(
                            habit = row.habit,
                            completions = completionsForHabit,
                            streak = viewModel.streakFor(row.habit).current,
                            doneToday = completionsForHabit.containsKey(dateStr) || (state.selectedDate == java.time.LocalDate.now() && row.habit.id in state.optimisticHabitDone),
                            canComplete = state.selectedDate == java.time.LocalDate.now(),
                            onClick = { onOpenDetail(row.habit.id) },
                            onOpenAnalytics = { onOpenAnalytics(row.habit.id) },
                            onCompleteToday = { viewModel.completeHabitToday(row.habit); onHabitCompleted(row.habit) }
                        )
                    }
                    is HabitRow.Assigned -> AssignedHabitRowCard(
                        group = row.group,
                        dateStr = dateStr,
                        onClick = { onOpenAssignedDetail(row.group) },
                        onShowProgress = { expandedGroup = row.group },
                        onOpenAnalytics = { onOpenAssignedAnalytics(row.group) }
                    )
                }
            }
        }
    }
    expandedGroup?.let { group ->
        AssignedGroupMembersSheet(
            title = group.habit.name,
            members = group.members.map { AssignedMemberStatus(it.uid, it.name, it.photoUrl, it.completions.containsKey(dateStr)) },
            onDismiss = { expandedGroup = null }
        )
    }
}

@Composable
private fun EmptyState(emoji: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}
