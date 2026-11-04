// AGP 9 has built-in Kotlin support and bundles its own Kotlin Gradle plugin.
// The Compose compiler plugin version must match the Kotlin compiler version,
// so we pin KGP explicitly (documented approach: "Upgrade to a higher KGP version").
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
