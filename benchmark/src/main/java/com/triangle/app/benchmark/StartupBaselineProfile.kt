package com.triangle.app.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * Records which classes/methods get touched on a cold launch — from process
 * start through MainActivity.onCreate, Compose's first frame (the gradient
 * SplashGradientScreen in AppNavHost), into whichever screen the session
 * lands on — so ART can compile that path ahead of time instead of
 * JIT-warming it live. This is what was actually behind the multi-second
 * flat-color splash phase measured via logcat (Davey/Choreographer skipped
 * frames) and via `adb shell am start -W` (4+ seconds cold, real device).
 */
class StartupBaselineProfile {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = "com.triangle.app",
        includeInStartupProfile = true
    ) {
        // Plain startActivityAndWait() — the POST_NOTIFICATIONS runtime
        // permission MainActivity requests on API 33+ was already resolved
        // (granted/denied) from earlier manual smoke-testing on this same
        // device/emulator, so no system permission dialog interferes here.
        startActivityAndWait()
    }
}
