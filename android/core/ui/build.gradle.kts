plugins {
    alias(libs.plugins.rozklad.compose.library)
}

android {
    namespace = "com.gifwarno.rozklad.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(projects.core.domain)
}
