plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.spotless)
}

// Форматування для всього репо: ./gradlew spotlessApply
// fileTree з exclude не заходить у build/ і .gradle/ взагалі — інакше паралельна збірка ламає обхід файлів.
val sourceTree = { pattern: String ->
    fileTree(rootDir) {
        include(pattern)
        exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }
}
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target(sourceTree("**/src/**/*.kt"))
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target(sourceTree("**/*.gradle.kts"))
        ktlint(ktlintVersion)
    }
}
