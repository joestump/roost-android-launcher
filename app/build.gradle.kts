plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "rocks.stump.roost"
    compileSdk = 34

    defaultConfig {
        applicationId = "rocks.stump.roost"
        minSdk = 26
        targetSdk = 34
        versionCode = 24
        versionName = "0.7.9"
    }

    // Generate BuildConfig so the app can surface its own versionName (Settings device strip).
    // A build feature toggle, not a runtime dependency — ADR-0001's dependency-free rule is intact.
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Intentionally dependency-free: pure Android framework, no AndroidX.
}
