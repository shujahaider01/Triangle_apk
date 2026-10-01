package com.triangle.app.data

import android.content.Context
import kotlinx.coroutines.flow.first

/**
 * Tells the user when their place on the leaderboard (them + their connections) changes at the top: they
 * enter the top 3, drop out of it, or move between 1st/2nd/3rd. Watched for the Weekly, Monthly and All Time
 * boards. There is no server-side ranking, so this runs on the user's own device — when the app opens, when the
 * leaderboard is opened, and when any push arrives — and compares against the last rank it saw. The first
 * check only records the starting ranks (no notification). Changes are delivered as one in-app + push
 * notification of type `rank_change`.
 */
object RankWatcher {
    private const val PREFS = "rank_watch"
    private const val MIN_GAP_MS = 10 * 60 * 1000L
    private const val UNRANKED = 99
    private val boards = listOf("week" to "weekly", "month" to "monthly", "all" to "all-time")

    /** Rank of [me] among [entries] for one board: ties share a rank, and someone with 0 points isn't ranked at all. */
    private fun rankOf(entries: List<LeaderboardRepository.LeaderboardEntry>, me: String, pts: (LeaderboardRepository.LeaderboardEntry) -> Int): Int {
        val mine = entries.firstOrNull { it.uid == me } ?: return UNRANKED
        val myPts = pts(mine)
        if (myPts <= 0) return UNRANKED
        return 1 + entries.count { pts(it) > myPts }
    }

    suspend fun check(context: Context, session: SessionStore.Session, force: Boolean = false) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val gapKey = "${session.uid}_checkedAt"
            if (!force && now - prefs.getLong(gapKey, 0L) < MIN_GAP_MS) return
            prefs.edit().putLong(gapKey, now).apply()

            val circle = ConnectionRepository.circleFlow(session.uid).first().toSet()
            val entries = LeaderboardRepository.fetchRanking(session.uid, circle)
            val ranks = mapOf(
                "week" to rankOf(entries, session.uid) { it.weekPoints },
                "month" to rankOf(entries, session.uid) { it.monthPoints },
                "all" to rankOf(entries, session.uid) { it.points }
            )

            val messages = mutableListOf<String>()
            val edit = prefs.edit()
            boards.forEach { (key, label) ->
                val new = ranks.getValue(key)
                val prev = prefs.getInt("${session.uid}_$key", -1)
                edit.putInt("${session.uid}_$key", new)
                if (prev == -1 || prev == new) return@forEach
                val nowTop = new <= 3
                val wasTop = prev <= 3
                when {
                    nowTop && !wasTop -> messages += if (new == 1) "You're #1 on the $label leaderboard 👑" else "You're now #$new on the $label leaderboard 🎉"
                    !nowTop && wasTop -> messages += "You dropped out of the top 3 on the $label leaderboard" + (if (new != UNRANKED) " (now #$new)" else "")
                    nowTop && new < prev -> messages += "You moved up to #$new on the $label leaderboard"
                    nowTop && new > prev -> messages += "You slipped to #$new on the $label leaderboard"
                }
            }
            edit.apply()
            if (messages.isNotEmpty()) NotificationRepository.notifyRankChange(session.uid, messages.joinToString(" · "))
        }
    }
}

