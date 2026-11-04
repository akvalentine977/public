// AGP 9 has built-in Kotlin support, so no org.jetbrains.kotlin.android plugin is applied.
// The Compose compiler plugin version in gradle/libs.versions.toml matches the Kotlin
// Gradle plugin version that this AGP release bundles.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
