package com.triangle.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.Badges
import com.triangle.app.data.DashboardStats
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.HabitStats
import com.triangle.app.data.OrgDataRepository
import com.triangle.app.data.ProfileStats
import com.triangle.app.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class ProfileTab { Overview, Analytics, Achievement }

data class ProfileUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val tab: ProfileTab = ProfileTab.Overview,
    val name: String = "",
    val handle: String = "",
    val level: Int = 1,
    val tier: String = "Beginner",
    val points: Int = 0,
    val xpIntoLevel: Int = 0,
    val rank: Int = 1,

    // Overview
    val performancePct: Int = 0,
    val performanceDelta: Int? = null,
    val monthXp: Int = 0,
    val xpDeltaPct: Int? = null,
    val monthCoins: Int = 0,
    val trendWeeks: List<ProfileStats.WeeklyProductivity>? = null,
    val trendPct: List<Int> = emptyList(),
    val taskCompletionPct: Int = 0,
    val habitStrengthPct: Int = 0,

    // Analytics (current-month/current-year snapshots — refreshed by changeAnalyticsMonth/Year)
    val analyticsRef: ProfileStats.MonthRef = ProfileStats.currentMonthRef(),
    val analyticsYear: Int = ProfileStats.currentMonthRef().year,
    val weeklyProductivity: ProfileStats.WeeklyProductivity? = null,
    val tasksByCategory: ProfileStats.TasksByCategory? = null,
    val taskStatusDistribution: ProfileStats.TaskStatusDistribution? = null,
    val coinsEarnedVsSpent: ProfileStats.CoinsEarnedVsSpent? = null,
    val overdueTimeline: List<ProfileStats.OverdueWeek> = emptyList(),
    val monthlyPerformance: ProfileStats.MonthlyPerformance? = null,

    // Achievement
    val bestStreak: Int = 0,
    val weeklyRankRecord: ProfileStats.WeeklyRankRecord? = null,
    val mostXpInADay: Int = 0,
    val totalTasksDone: Int = 0,
    val totalCoinsEarned: Int = 0,
    val badges: List<Badges.Badge> = emptyList()
)

/**
 * Native port of the DATA half of renderInternProfile()'s Overview/Analytics/
 * Achievement tabs — see ProfileStats/Badges for the ported pure
 * computations. One-shot OrgDataRepository.fetchProfileData() read (same
 * pattern as Milestone 1's Dashboard), refreshed on init and whenever the
 * Analytics tab's month/year picker changes — no realtime listener here
 * since nothing on this screen writes data (read-only, see the Milestone 3
 * plan's "no new write paths" note).
 */
class ProfileViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(name = session.name, handle = handleOf(session)))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var snapshot: OrgDataRepository.ProfileSnapshot? = null

    init {
        load()
        observeHabitStrength()
    }

    fun selectTab(tab: ProfileTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val snap = OrgDataRepository.fetchProfileData(session.orgId, session.uid)
                snapshot = snap
                applySnapshot(snap, _uiState.value.analyticsRef, _uiState.value.analyticsYear)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load your profile")
            }
        }
    }

    fun changeAnalyticsMonth(ref: ProfileStats.MonthRef) {
        val snap = snapshot ?: return
        _uiState.value = _uiState.value.copy(analyticsRef = ref)
        recomputeAnalytics(snap, ref, _uiState.value.analyticsYear)
    }

    fun changeAnalyticsYear(year: Int) {
        val snap = snapshot ?: return
        _uiState.value = _uiState.value.copy(analyticsYear = year)
        recomputeAnalytics(snap, _uiState.value.analyticsRef, year)
    }

    private fun applySnapshot(snap: OrgDataRepository.ProfileSnapshot, analyticsRef: ProfileStats.MonthRef, analyticsYear: Int) {
        val level = ProfileStats.level(snap.points)
        val tier = ProfileStats.tier(snap.points)
        val rank = DashboardStats.getRank(snap.memberPoints, session.uid)
        val bestStreak = DashboardStats.getStreak(taskMapList(snap), completionsAsMap(snap), session.uid)

        val curRef = ProfileStats.currentMonthRef()
        val prevRef = if (curRef.month == 0) ProfileStats.MonthRef(curRef.year - 1, 11) else ProfileStats.MonthRef(curRef.year, curRef.month - 1)
        val weeklyProdForOverview = ProfileStats.getWeeklyProductivity(snap.tasks, snap.completions, session.uid, curRef)
        val obNow = weeklyProdForOverview.score
        val performanceDelta = if (hasTasksIn(snap, prevRef)) weeklyProdForOverview.delta else null

        val monthXp = ProfileStats.monthXp(snap.pointHistory, curRef)
        val prevMonthXp = ProfileStats.monthXp(snap.pointHistory, prevRef)
        val xpDeltaPct = if (prevMonthXp > 0) ((monthXp - prevMonthXp) * 100.0 / prevMonthXp).let { Math.round(it).toInt() } else if (monthXp > 0) 100 else null
        val monthCoins = monthXp / 10 // xpPerCoin default; matches rewardSettings.xpPerCoin fallback of 10

        val taskStatus = ProfileStats.getTaskStatusDistribution(snap.tasks, snap.completions, session.uid, curRef)
        val taskCompletionPct = taskStatus.let { d -> if (d.total > 0) (d.statuses.first { it.label == "Completed" }.count * 100 / d.total) else 0 }
        val habitStrengthPct = minOf(bestStreak * 7, 100)

        val weeklyRankRecord = ProfileStats.getWeeklyRankRecord(snap.pointHistory)
        val mostXpInADay = ProfileStats.getMostXpInADay(snap.pointHistory)
        val totalTasksDone = snap.tasks.count { it.countsForStats(session.uid) && snap.completions.contains("${it.id}-${session.uid}") }
        val totalCoinsEarned = snap.walletTransactions.filter { it["type"] == "earned" }.sumOf { (it["coinsChange"] as? Number)?.toInt() ?: 0 }
        val badges = Badges.compute(snap.tasks, snap.completions, session.uid, snap.points, bestStreak, rank, weeklyRankRecord, snap.pointHistory)

        _uiState.value = _uiState.value.copy(
            isLoading = false, error = null,
            level = level, tier = tier, points = snap.points, xpIntoLevel = snap.points % 500, rank = rank,
            performancePct = obNow, performanceDelta = performanceDelta,
            monthXp = monthXp, xpDeltaPct = xpDeltaPct, monthCoins = monthCoins,
            trendPct = weeklyProdForOverview.weeks,
            taskCompletionPct = taskCompletionPct, habitStrengthPct = habitStrengthPct,
            bestStreak = bestStreak, weeklyRankRecord = weeklyRankRecord, mostXpInADay = mostXpInADay,
            totalTasksDone = totalTasksDone, totalCoinsEarned = totalCoinsEarned, badges = badges
        )
        recomputeAnalytics(snap, analyticsRef, analyticsYear)
    }

    private fun recomputeAnalytics(snap: OrgDataRepository.ProfileSnapshot, ref: ProfileStats.MonthRef, year: Int) {
        _uiState.value = _uiState.value.copy(
            weeklyProductivity = ProfileStats.getWeeklyProductivity(snap.tasks, snap.completions, session.uid, ref),
            tasksByCategory = ProfileStats.getTasksByCategory(snap.tasks, session.uid, ref),
            taskStatusDistribution = ProfileStats.getTaskStatusDistribution(snap.tasks, snap.completions, session.uid, ref),
            coinsEarnedVsSpent = ProfileStats.getCoinsEarnedVsSpent(snap.walletTransactions, ref),
            overdueTimeline = ProfileStats.getOverdueTasksTimeline(snap.tasks, snap.completions, session.uid, ref),
            monthlyPerformance = ProfileStats.getMonthlyPerformance(snap.tasks, snap.completions, session.uid, ProfileStats.YearRef(year))
        )
    }

    private fun hasTasksIn(snap: OrgDataRepository.ProfileSnapshot, ref: ProfileStats.MonthRef): Boolean {
        val ds = java.time.LocalDate.of(ref.year, ref.month + 1, 1)
        val de = ds.plusMonths(1).minusDays(1)
        return snap.tasks.any { t ->
            t.countsForStats(session.uid) && (t.instanceDate ?: t.dueDate ?: t.createdDate)?.let { it >= ds.toString() && it <= de.toString() } == true
        }
    }

    private fun taskMapList(snap: OrgDataRepository.ProfileSnapshot): List<Map<String, Any?>> = snap.tasks.map { it.toMap() }
    private fun completionsAsMap(snap: OrgDataRepository.ProfileSnapshot): Map<String, Any?> =
        snap.completions.associateWith { mapOf("done" to true) }

    /** Habit-based "Top Strengths" bar — same `min(streak*7, 100)` proxy script.js uses (s.habitStreak||streak)*7. */
    private fun observeHabitStrength() {
        combine(HabitRepository.habitsFlow(session.orgId), HabitRepository.habitCompletionsFlow(session.orgId, session.uid)) { habits, completions ->
            habits.filter { it.assignedTo.contains(session.uid) }
                .maxOfOrNull { h -> HabitStats.calcStreak(h, completions[h.id] ?: emptyMap()).current } ?: 0
        }.onEach { habitStreak ->
            if (habitStreak > 0) {
                _uiState.value = _uiState.value.copy(habitStrengthPct = minOf(habitStreak * 7, 100))
            }
        }.launchIn(viewModelScope)
    }

    private fun handleOf(session: SessionStore.Session): String {
        val base = session.email.substringBefore("@").ifBlank { session.name.trim().substringBefore(" ") }
        return "@" + base.lowercase().ifBlank { session.uid.take(6) }
    }
}
