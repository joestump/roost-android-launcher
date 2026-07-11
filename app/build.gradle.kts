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
        versionCode = 21
        versionName = "0.7.6"
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
