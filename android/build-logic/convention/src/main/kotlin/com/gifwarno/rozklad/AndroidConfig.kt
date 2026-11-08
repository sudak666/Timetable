package com.gifwarno.rozklad

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Спільні параметри для всіх модулів — змінюються в одному місці. */
object Sdk {
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
    val JAVA = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

internal fun Project.configureAndroidApplication(ext: ApplicationExtension) {
    with(ext) {
        compileSdk = Sdk.COMPILE
        defaultConfig {
            minSdk = Sdk.MIN
            targetSdk = Sdk.TARGET
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = Sdk.JAVA
            targetCompatibility = Sdk.JAVA
        }
        testOptions.unitTests.isIncludeAndroidResources = true
        lint { configureLint(this, checkDeps = true) }
    }
    configureKotlin()
}

internal fun Project.configureAndroidLibrary(ext: LibraryExtension) {
    with(ext) {
        compileSdk = Sdk.COMPILE
        defaultConfig {
            minSdk = Sdk.MIN
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = Sdk.JAVA
            targetCompatibility = Sdk.JAVA
        }
        testOptions.unitTests.isIncludeAndroidResources = true
        lint { configureLint(this, checkDeps = false) }
    }
    configureKotlin()
}

private fun Project.configureLint(lint: Lint, checkDeps: Boolean) {
    with(lint) {
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = checkDeps
        lintConfig = rootProject.file("config/lint.xml")
    }
}

/** Kotlin: у CI попередження = помилки (-PwarningsAsErrors=true). */
internal fun Project.configureKotlin() {
    // Модулі без тестів (поки що) не мають валити збірку.
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false)
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(Sdk.JVM_TARGET)
            allWarningsAsErrors.set(warningsAsErrors)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}

internal fun Project.configureJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = Sdk.JAVA
        targetCompatibility = Sdk.JAVA
    }
    configureKotlin()
}
