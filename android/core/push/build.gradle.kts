plugins {
    alias(libs.plugins.rozklad.android.library)
    alias(libs.plugins.rozklad.hilt)
}

/*
 * Параметри Firebase — публічні (вони все одно є в APK), але залежать від твого проєкту Firebase.
 * Задаються в android/gradle.properties (rozklad.firebase.*). Порожні → push вимкнено, збірка працює.
 */
fun firebase(name: String): String = providers.gradleProperty("rozklad.firebase.$name").getOrElse("")

android {
    namespace = "com.gifwarno.rozklad.core.push"
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebase("apiKey")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebase("projectId")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${firebase("senderId")}\"")
    }
    buildTypes {
        // Debug-збірка має інший пакет (.debug) → окремий застосунок у Firebase.
        debug { buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase("appIdDebug")}\"") }
        release { buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase("appId")}\"") }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.model)
    implementation(projects.core.network)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
}
