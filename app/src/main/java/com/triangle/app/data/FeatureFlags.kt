package com.triangle.app.data

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
    // Example — copy this shape for the first real flag:
    // TASK_LEVELS_ENABLED(
    //     description = "Task Levels progression system",
    //     owner = "Engineering",
    //     createdDate = "2026-09-21",
    //     status = FlagStatus.IN_DEVELOPMENT,
    //     devDefault = true, qaDefault = true, prodDefault = false,
    // )
}

object FeatureFlags {
    fun isEnabled(flag: FeatureFlag): Boolean = when (AppEnvironment.current) {
        Environment.DEV -> flag.devDefault
        Environment.QA -> flag.qaDefault
        Environment.PROD -> flag.prodDefault
    }
}
