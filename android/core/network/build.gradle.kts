plugins {
    alias(libs.plugins.rozklad.android.library)
    alias(libs.plugins.rozklad.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gifwarno.rozklad.core.network"
    buildFeatures.buildConfig = true
    defaultConfig {
        // Публічні параметри (publishable key); перевизначаються -Prozklad.supabaseUrl=…
        val url = providers.gradleProperty("rozklad.supabaseUrl").get()
        val key = providers.gradleProperty("rozklad.supabaseKey").get()
        buildConfigField("String", "SUPABASE_URL", "\"$url\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$key\"")
    }
}

dependencies {
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
