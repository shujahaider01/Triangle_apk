plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.triangle.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.triangle.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Placeholder until Triangle's own Firebase project exists — see
        // Firebase Console > Authentication > Sign-in method > Google for
        // the real "Web client" OAuth ID once that project is created.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"859625465910-5fu54tsk2ifgn1fhcheaai44skgm2ieq.apps.googleusercontent.com\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // BuildConfig generation is off by default since AGP 8 — needed for the
    // BuildConfig.DEBUG check that gates WebView debugging (chrome://inspect)
    // to debug builds only in MainActivity.kt.
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase Cloud Messaging (Phase 1 — push notifications)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")

    // Google Identity Services — Authorization API (Settings > Integrations
    // Google Drive backup/restore). NOT the deprecated GoogleSignInClient;
    // this is Identity.getAuthorizationClient(), used purely to obtain a
    // drive.file-scoped OAuth token, no separate "sign-in" concept needed.
    implementation("com.google.android.gms:play-services-auth:21.6.0")
}
