plugins {
    alias(libs.plugins.rozklad.android.library)
    alias(libs.plugins.rozklad.hilt)
}

android {
    namespace = "com.gifwarno.rozklad.core.datastore"
}

dependencies {
    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
