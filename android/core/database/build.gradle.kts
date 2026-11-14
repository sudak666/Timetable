plugins {
    alias(libs.plugins.rozklad.android.library)
    alias(libs.plugins.rozklad.android.room)
    alias(libs.plugins.rozklad.hilt)
}

android {
    namespace = "com.gifwarno.rozklad.core.database"
    // Схеми Room — ресурси unit-тестів міграцій (MigrationTestHelper під Robolectric).
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
