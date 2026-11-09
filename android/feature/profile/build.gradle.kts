plugins {
    alias(libs.plugins.rozklad.android.feature)
}

android {
    namespace = "com.gifwarno.rozklad.feature.profile"
}

dependencies {
    implementation(projects.core.datastore)
    implementation(projects.core.push)
    implementation(libs.androidx.activity.compose)
}
