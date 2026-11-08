import com.gifwarno.rozklad.lib
import com.gifwarno.rozklad.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature-модуль: екран + ViewModel. Бачить лише core-модулі, ніколи інші feature.
 * Навігацію знає тільки :app — feature віддає @Composable Route і колбеки.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("rozklad.android.library.compose")
            pluginManager.apply("rozklad.hilt")
            dependencies {
                "implementation"(project(":core:data"))
                "implementation"(project(":core:domain"))
                "implementation"(project(":core:model"))
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:ui"))

                "implementation"(libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
                "implementation"(libs.lib("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.lib("androidx-lifecycle-viewmodel-compose"))

                "testImplementation"(project(":core:testing"))
                "testImplementation"(libs.lib("turbine"))
            }
        }
    }
}
