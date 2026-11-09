plugins {
    alias(libs.plugins.rozklad.android.feature)
}

android {
    namespace = "com.gifwarno.rozklad.feature.grades"
}

dependencies {
    implementation(projects.core.common)
}
