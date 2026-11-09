plugins {
    alias(libs.plugins.rozklad.android.library)
}

android {
    namespace = "com.gifwarno.rozklad.core.testing"
}

dependencies {
    api(projects.core.data)
    api(projects.core.model)
    implementation(projects.core.domain)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    implementation(libs.androidx.dataStore.preferences)
}
