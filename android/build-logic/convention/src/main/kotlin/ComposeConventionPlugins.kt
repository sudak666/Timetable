import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.gifwarno.rozklad.lib
import com.gifwarno.rozklad.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

private fun Project.configureCompose() {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // ./gradlew assembleRelease -PcomposeReports=true → звіти стабільності в build/compose
        if (providers.gradleProperty("composeReports").isPresent) {
            reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
        }
        stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose_stability.conf"))
    }
    dependencies {
        val bom = platform(libs.lib("androidx-compose-bom"))
        "implementation"(bom)
        "androidTestImplementation"(bom)
        "implementation"(libs.lib("androidx-compose-ui-tooling-preview"))
        "debugImplementation"(libs.lib("androidx-compose-ui-tooling"))
        "lintChecks"(libs.lib("compose-lint-checks"))
    }
}

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("rozklad.android.application")
            extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
            configureCompose()
        }
    }
}

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("rozklad.android.library")
            extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            configureCompose()
        }
    }
}
