plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.triangle.app.benchmark"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // Baseline Profile generation needs API 28+ (root-free profile
        // capture landed properly around API 33 — our TriangleTest AVD is
        // API 34, so no root/managed device juggling needed).
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // This module's whole purpose is to drive the real :app APK through its
    // cold-start path and record the resulting profile — not to be its own
    // shippable app.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Drives the connected TriangleTest emulator (already set up for this
// project's own manual smoke-testing — see the native-rewrite roadmap)
// instead of spinning up a separate Gradle Managed Device.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
