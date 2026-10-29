import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.arina.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.arina.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.4"

        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("GEMINI_API_KEY", "").trim()}\"")
        buildConfigField("String", "CARTESIA_API_KEY", "\"${localProps.getProperty("CARTESIA_API_KEY", "").trim()}\"")
        buildConfigField("String", "TAVILY_API_KEY", "\"${localProps.getProperty("TAVILY_API_KEY", "").trim()}\"")
        buildConfigField("String", "CARTESIA_VOICE_ID", "\"${localProps.getProperty("CARTESIA_VOICE_ID", "").trim()}\"")
    }

    // One fixed key for every build, so a new APK installs over the old one and keeps its data and permissions.
    // (A debug-only key kept in the repo on purpose; it protects nothing secret.)
    signingConfigs {
        getByName("debug") {
            storeFile = file("ariauri-debug.keystore")
            storePassword = "android"
            keyAlias = "ariauri"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
