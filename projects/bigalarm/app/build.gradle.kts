plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.akvalentine.bigalarm"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.akvalentine.bigalarm"
        // Wear OS 5 = API 34 (needed for the systemExempted foreground service type).
        // Galaxy Watch5 on One UI 8 Watch is Wear OS 6 = API 36.
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // The Kotlin/Compose-compiler version is deliberately pinned to what AGP bundles.
        disable += "NewerVersionAvailable"
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.input)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}
