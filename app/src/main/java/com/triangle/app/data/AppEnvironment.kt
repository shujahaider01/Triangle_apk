package com.triangle.app.data

import com.triangle.app.BuildConfig

/**
 * Which of the three isolated environments (see the Environment & Release
 * Management PRD) this build was compiled as — driven by the flavor's
 * ENVIRONMENT BuildConfig field (app/build.gradle.kts), not by BuildConfig.DEBUG.
 */
enum class Environment { DEV, QA, PROD }

object AppEnvironment {
    val current: Environment = when (BuildConfig.ENVIRONMENT) {
        "dev" -> Environment.DEV
        "qa" -> Environment.QA
        else -> Environment.PROD
    }

    val isProd: Boolean get() = current == Environment.PROD
}
