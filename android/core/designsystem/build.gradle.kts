plugins {
    alias(libs.plugins.rozklad.compose.library)
}

android {
    namespace = "com.gifwarno.rozklad.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui)
}
