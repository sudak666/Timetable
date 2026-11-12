import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties

plugins {
    alias(libs.plugins.rozklad.android.application)
    alias(libs.plugins.rozklad.compose.application)
    alias(libs.plugins.rozklad.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.firebase.crashlytics)
}

// Ключ підпису — лише локально або з секретів CI, ніколи в git (див. android/README.md).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use(::load)
}

android {
    namespace = "com.gifwarno.rozklad"

    defaultConfig {
        // Публічний ID у Google Play — після першої публікації змінити неможливо.
        applicationId = "ua.dvanadtsiatka"
        versionCode = 4
        versionName = "0.2.2"
        androidResources.localeFilters += listOf("uk")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // Плагін Crashlytics лише вписує ідентифікатор збірки (без нього SDK не працює).
            // mapping не завантажуємо: обфускацію вимкнено (proguard-rules.pro), стеки читабельні й так.
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
        }
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.push)
    implementation(projects.core.ui)

    implementation(projects.feature.auth)
    implementation(projects.feature.today)
    implementation(projects.feature.grades)
    implementation(projects.feature.homework)
    implementation(projects.feature.profile)
    implementation(projects.feature.schedule)
    implementation(projects.feature.widget)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.work.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
