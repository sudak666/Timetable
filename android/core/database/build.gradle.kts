plugins {
    alias(libs.plugins.rozklad.android.library)
    alias(libs.plugins.rozklad.android.room)
    alias(libs.plugins.rozklad.hilt)
}

android {
    namespace = "com.gifwarno.rozklad.core.database"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
