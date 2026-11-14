plugins {
    alias(libs.plugins.rozklad.android.feature)
    // Чернетка розкладу в SavedStateHandle (DraftCodec).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gifwarno.rozklad.feature.schedule"
}

dependencies {
    implementation(projects.core.datastore)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
}
