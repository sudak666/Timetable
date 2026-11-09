plugins {
    alias(libs.plugins.rozklad.android.feature)
}

android {
    namespace = "com.gifwarno.rozklad.feature.widget"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.ktx)

    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.robolectric)
}
