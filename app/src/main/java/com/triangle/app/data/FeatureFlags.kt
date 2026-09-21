package com.triangle.app.data

import androidx.compose.runtime.mutableStateMapOf
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Feature-flag registry (Environment & Release Management PRD, §8-9). Every
 * flag carries the metadata the PRD requires — description, owner, created
 * date, status, and a per-environment default — so a feature's code can ship
 * to PROD while staying OFF for real users until explicitly approved.
 *
 * No flags are wired to real UI yet; this is the mechanism only. Add a new
 * flag as an entry below (copy the commented example's shape) and gate the
 * feature's UI with FeatureFlags.isEnabled(FeatureFlag.X) — don't read
 * AppEnvironment/BuildConfig directly from a screen.
 */
enum class FlagStatus { IN_DEVELOPMENT, TESTING, APPROVED_FOR_RELEASE, RELEASED }

enum class FeatureFlag(
    val description: String,
    val owner: String,
    val createdDate: String,
    val status: FlagStatus,
    val devDefault: Boolean,
    val qaDefault: Boolean,
    val prodDefault: Boolean,
) {
    DM_ENABLED(
        description = "Direct Messages (inbox/thread/new-message) between connected accounts",
        owner = "Product",
        createdDate = "2026-09-25",
        status = FlagStatus.TESTING,
        devDefault = true, qaDefault = true, prodDefault = false,
    ),
    TASK_HABIT_NOTES_ENABLED(
        description = "Add Note / Add Photo on Task and Habit detail screens (text notes + cropped photo attachments)",
        owner = "Product",
        createdDate = "2026-09-25",
        status = FlagStatus.TESTING,
        devDefault = true, qaDefault = true, prodDefault = false,
    ),
    NOTIFICATION_BADGE_ENABLED(
        description = "Unread-count badge on the Dashboard's notification bell icon",
        owner = "Product",
        createdDate = "2026-09-22",
        status = FlagStatus.APPROVED_FOR_RELEASE,
        devDefault = true, qaDefault = true, prodDefault = true,
    )
    ;

    // Same key across all three Firebase projects (triangle-apk/-qa/-prod)
    // — each project's own Remote Config console is what actually differs
    // per environment, since dev/qa/prod are already isolated projects.
    val remoteKey: String get() = name.lowercase()

    val localDefault: Boolean
        get() = when (AppEnvironment.current) {
            Environment.DEV -> devDefault
            Environment.QA -> qaDefault
            Environment.PROD -> prodDefault
        }
}

/**
 * Backed by Firebase Remote Config so a flag can be turned on/off from that
 * environment's Firebase console without shipping a new build (PRD §18's
 * "Feature ON -> critical issue -> Feature OFF" rollback mitigation).
 *
 * `state` always starts at each flag's compiled localDefault and is only
 * ever overwritten once refreshFromRemote() confirms a value was actually
 * published in that project's console (source == VALUE_SOURCE_REMOTE) — so
 * offline, first-launch-before-fetch, and "nothing configured remotely yet"
 * all behave identically to today (no remote wiring), never silently OFF.
 */
object FeatureFlags {
    private val state = mutableStateMapOf<FeatureFlag, Boolean>().apply {
        FeatureFlag.entries.forEach { put(it, it.localDefault) }
    }

    fun isEnabled(flag: FeatureFlag): Boolean = state[flag] ?: flag.localDefault

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(
                remoteConfigSettings {
                    // Dev/qa fetch fresh every launch for easy testing; prod
                    // caches for an hour so an emergency flag flip reaches
                    // running installs within that window without hammering
                    // Remote Config's fetch quota.
                    minimumFetchIntervalInSeconds = if (AppEnvironment.isProd) 3600 else 0
                }
            )
        }
    }

    /** Fire-and-forget from MainActivity.onCreate(); failures are swallowed
     * since `state` is already correct from local defaults. */
    suspend fun refreshFromRemote() {
        runCatching {
            remoteConfig.fetchAndActivate().await()
            FeatureFlag.entries.forEach { flag ->
                val value = remoteConfig.getValue(flag.remoteKey)
                if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
                    state[flag] = value.asBoolean()
                }
            }
        }
    }
}
