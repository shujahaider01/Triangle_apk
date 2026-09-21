package com.triangle.app.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.triangle.app.DriveAuthStore
import com.triangle.app.DriveImageHelper
import com.triangle.app.data.Badges
import com.triangle.app.data.DashboardStats
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.HabitStats
import com.triangle.app.data.OrgDataRepository
import com.triangle.app.data.ProfileStats
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProfileTab { Overview, Analytics, Achievement }

data class ProfileUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val tab: ProfileTab = ProfileTab.Overview,
    val name: String = "",
    val handle: String = "",
    val photoUrl: String? = null,
    val photoUploading: Boolean = false,
    val photoError: String? = null,
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
    val overdueTimeline: List<ProfileStats.OverdueWeek> = emptyList(),
    val monthlyPerformance: ProfileStats.MonthlyPerformance? = null,

    // Achievement
    val bestStreak: Int = 0,
    val weeklyRankRecord: ProfileStats.WeeklyRankRecord? = null,
    val mostXpInADay: Int = 0,
    val totalTasksDone: Int = 0,
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
 *
 * Keyed off a bare uid/orgId rather than the whole SessionStore.Session so
 * the same view model can back either the current user's own Profile tab
 * (the `session` constructor, [isReadOnly] false) or a read-only peek at
 * someone else's profile tapped from the Leaderboard (the uid/orgId
 * constructor, [isReadOnly] true — no photo upload, see uploadPhoto()).
 */
class ProfileViewModel private constructor(
    private val uid: String,
    private val orgId: String,
    initialName: String,
    initialHandle: String,
    initialPhotoUrl: String?,
    val isReadOnly: Boolean
) : ViewModel() {

    constructor(session: SessionStore.Session) : this(
        uid = session.uid,
        orgId = session.orgId,
        initialName = session.name,
        initialHandle = "@" + (session.username ?: session.uid.take(6)),
        initialPhotoUrl = session.photoUrl,
        isReadOnly = false
    )

    /** Read-only variant for viewing someone else's profile — see the class doc comment. */
    constructor(uid: String, orgId: String, name: String, username: String?, photoUrl: String?) : this(
        uid = uid,
        orgId = orgId,
        initialName = name,
        initialHandle = "@" + (username ?: uid.take(6)),
        initialPhotoUrl = photoUrl,
        isReadOnly = true
    )

    private val _uiState = MutableStateFlow(ProfileUiState(name = initialName, handle = initialHandle, photoUrl = initialPhotoUrl))
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
                val snap = OrgDataRepository.fetchProfileData(orgId, uid)
                snapshot = snap
                applySnapshot(snap, _uiState.value.analyticsRef, _uiState.value.analyticsYear)
                // users/{uid} lives outside the org data blob fetchProfileData reads,
                // and the cached session's own photoUrl can be stale (set at login,
                // never refreshed) — re-fetch it fresh so a photo uploaded in a
                // previous session still shows up here. Also refreshes `name`/
                // `handle`, which the read-only (someone-else's-profile)
                // constructor doesn't necessarily have (PublicProfileScreen is
                // reached via a uid/orgId-only nav route, no name in the URL).
                val record = UserRepository.fetchUserRecord(uid)
                if (record != null) {
                    _uiState.value = _uiState.value.copy(
                        name = record.name,
                        handle = "@" + (record.username ?: uid.take(6)),
                        photoUrl = record.photoUrl
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load your profile")
            }
        }
    }

    private val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
    private var pendingPhoto: Bitmap? = null

    /**
     * Uploads a newly-cropped profile photo to Google Drive (see
     * DriveImageHelper's doc comment for why Drive instead of Firebase
     * Storage) and points users/{uid}/photoUrl at the result. No-ops on a
     * read-only (someone-else's) profile. Reuses the same drive.file OAuth
     * flow as Settings > Backup & Restore — [launchIntent] is how the
     * caller (ProfileScreen) plugs in its Activity Result launcher when
     * Google needs to show a consent screen; [onPhotoAuthorizationResult]
     * feeds the result back in once that finishes.
     */
    fun requestPhotoUpload(context: Context, bitmap: Bitmap, launchIntent: (IntentSenderRequest) -> Unit) {
        if (isReadOnly) return
        pendingPhoto = bitmap
        _uiState.value = _uiState.value.copy(photoUploading = true, photoError = null)
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(driveScope)).build()
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    try {
                        launchIntent(IntentSenderRequest.Builder(authResult.pendingIntent!!.intentSender).build())
                    } catch (e: Exception) {
                        pendingPhoto = null
                        _uiState.value = _uiState.value.copy(photoUploading = false, photoError = "Couldn't open Google sign-in: ${e.message}")
                    }
                } else {
                    finishPhotoUpload(context, authResult.accessToken)
                }
            }
            .addOnFailureListener { e ->
                pendingPhoto = null
                _uiState.value = _uiState.value.copy(photoUploading = false, photoError = e.message ?: "Google authorization failed")
            }
    }

    /** Called from the launcher's result callback once the account-picker/consent intent finishes. */
    fun onPhotoAuthorizationResult(context: Context, resultData: Intent?) {
        try {
            val authResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(resultData)
            finishPhotoUpload(context, authResult.accessToken)
        } catch (e: Exception) {
            pendingPhoto = null
            _uiState.value = _uiState.value.copy(photoUploading = false, photoError = e.message ?: "Google sign-in was cancelled")
        }
    }

    private fun finishPhotoUpload(context: Context, token: String?) {
        val bitmap = pendingPhoto
        pendingPhoto = null
        if (token.isNullOrEmpty() || bitmap == null) {
            _uiState.value = _uiState.value.copy(photoUploading = false, photoError = "No access token returned by Google")
            return
        }
        DriveAuthStore.saveAccessToken(context, token)
        DriveAuthStore.setConnected(context, true)
        viewModelScope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    DriveImageHelper.uploadImage(token, "TriangleProfilePhoto_${uid}_${System.currentTimeMillis()}.jpg", bitmap)
                }
                UserRepository.updateProfilePhoto(uid, url)
                _uiState.value = _uiState.value.copy(photoUploading = false, photoUrl = url)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(photoUploading = false, photoError = e.message ?: "Couldn't upload photo")
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
        val rank = DashboardStats.getRank(snap.memberPoints, uid)
        val bestStreak = DashboardStats.getStreak(taskMapList(snap), completionsAsMap(snap), uid)

        val curRef = ProfileStats.currentMonthRef()
        val prevRef = if (curRef.month == 0) ProfileStats.MonthRef(curRef.year - 1, 11) else ProfileStats.MonthRef(curRef.year, curRef.month - 1)
        val weeklyProdForOverview = ProfileStats.getWeeklyProductivity(snap.tasks, snap.completions, uid, curRef)
        val obNow = weeklyProdForOverview.score
        val performanceDelta = if (hasTasksIn(snap, prevRef)) weeklyProdForOverview.delta else null

        val monthXp = ProfileStats.monthXp(snap.pointHistory, curRef)
        val prevMonthXp = ProfileStats.monthXp(snap.pointHistory, prevRef)
        val xpDeltaPct = if (prevMonthXp > 0) ((monthXp - prevMonthXp) * 100.0 / prevMonthXp).let { Math.round(it).toInt() } else if (monthXp > 0) 100 else null

        val taskStatus = ProfileStats.getTaskStatusDistribution(snap.tasks, snap.completions, uid, curRef)
        val taskCompletionPct = taskStatus.let { d -> if (d.total > 0) (d.statuses.first { it.label == "Completed" }.count * 100 / d.total) else 0 }
        val habitStrengthPct = minOf(bestStreak * 7, 100)

        val weeklyRankRecord = ProfileStats.getWeeklyRankRecord(snap.pointHistory)
        val mostXpInADay = ProfileStats.getMostXpInADay(snap.pointHistory)
        val totalTasksDone = snap.tasks.count { it.countsForStats(uid) && snap.completions.contains("${it.id}-$uid") }
        val badges = Badges.compute(snap.tasks, snap.completions, uid, snap.points, bestStreak, rank, weeklyRankRecord, snap.pointHistory)

        _uiState.value = _uiState.value.copy(
            isLoading = false, error = null,
            level = level, tier = tier, points = snap.points, xpIntoLevel = snap.points % 500, rank = rank,
            performancePct = obNow, performanceDelta = performanceDelta,
            monthXp = monthXp, xpDeltaPct = xpDeltaPct,
            trendPct = weeklyProdForOverview.weeks,
            taskCompletionPct = taskCompletionPct, habitStrengthPct = habitStrengthPct,
            bestStreak = bestStreak, weeklyRankRecord = weeklyRankRecord, mostXpInADay = mostXpInADay,
            totalTasksDone = totalTasksDone, badges = badges
        )
        recomputeAnalytics(snap, analyticsRef, analyticsYear)
    }

    private fun recomputeAnalytics(snap: OrgDataRepository.ProfileSnapshot, ref: ProfileStats.MonthRef, year: Int) {
        _uiState.value = _uiState.value.copy(
            weeklyProductivity = ProfileStats.getWeeklyProductivity(snap.tasks, snap.completions, uid, ref),
            tasksByCategory = ProfileStats.getTasksByCategory(snap.tasks, uid, ref),
            taskStatusDistribution = ProfileStats.getTaskStatusDistribution(snap.tasks, snap.completions, uid, ref),
            overdueTimeline = ProfileStats.getOverdueTasksTimeline(snap.tasks, snap.completions, uid, ref),
            monthlyPerformance = ProfileStats.getMonthlyPerformance(snap.tasks, snap.completions, uid, ProfileStats.YearRef(year))
        )
    }

    private fun hasTasksIn(snap: OrgDataRepository.ProfileSnapshot, ref: ProfileStats.MonthRef): Boolean {
        val ds = java.time.LocalDate.of(ref.year, ref.month + 1, 1)
        val de = ds.plusMonths(1).minusDays(1)
        return snap.tasks.any { t ->
            t.countsForStats(uid) && (t.instanceDate ?: t.dueDate ?: t.createdDate)?.let { it >= ds.toString() && it <= de.toString() } == true
        }
    }

    private fun taskMapList(snap: OrgDataRepository.ProfileSnapshot): List<Map<String, Any?>> = snap.tasks.map { it.toMap() }
    private fun completionsAsMap(snap: OrgDataRepository.ProfileSnapshot): Map<String, Any?> =
        snap.completions.associateWith { mapOf("done" to true) }

    /** Habit-based "Top Strengths" bar — same `min(streak*7, 100)` proxy script.js uses (s.habitStreak||streak)*7. */
    private fun observeHabitStrength() {
        combine(HabitRepository.habitsFlow(orgId), HabitRepository.habitCompletionsFlow(orgId, uid)) { habits, completions ->
            habits.filter { it.assignedTo.contains(uid) }
                .maxOfOrNull { h -> HabitStats.calcStreak(h, completions[h.id] ?: emptyMap()).current } ?: 0
        }.onEach { habitStreak ->
            if (habitStreak > 0) {
                _uiState.value = _uiState.value.copy(habitStrengthPct = minOf(habitStreak * 7, 100))
            }
        }.launchIn(viewModelScope)
    }
}
