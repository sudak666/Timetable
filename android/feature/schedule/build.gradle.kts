plugins {
    alias(libs.plugins.rozklad.android.feature)
}

android {
    namespace = "com.gifwarno.rozklad.feature.schedule"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
