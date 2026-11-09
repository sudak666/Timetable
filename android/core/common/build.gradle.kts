plugins {
    alias(libs.plugins.rozklad.android.library)
    alias(libs.plugins.rozklad.hilt)
}

android {
    namespace = "com.gifwarno.rozklad.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
