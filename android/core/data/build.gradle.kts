plugins {
    alias(libs.plugins.rozklad.android.library)
    alias(libs.plugins.rozklad.hilt)
}

android {
    namespace = "com.gifwarno.rozklad.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.datastore)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.network)

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.turbine)
}
