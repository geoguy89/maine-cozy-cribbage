plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.geoguy89.cribbage"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geoguy89.cribbage"
        minSdk = 26
        targetSdk = 35
        // CI sets this to the run number, so every release installs over the last (the update check relies on it too).
        versionCode = System.getenv("CRIBBAGE_BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.0"
    }

    // One permanent key, so each release installs over the last. CI supplies it from repository secrets; local
    // builds without it fall back to the debug key.
    val keystore = System.getenv("CRIBBAGE_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("CRIBBAGE_KEYSTORE_PASSWORD")
                keyAlias = "cribbage"
                keyPassword = System.getenv("CRIBBAGE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Lint's Kotlin analysis has crashed on release builds; it isn't a gate for this project.
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.window:window:1.4.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
