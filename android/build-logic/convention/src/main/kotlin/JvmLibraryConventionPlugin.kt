import com.gifwarno.rozklad.configureJvm
import com.gifwarno.rozklad.lib
import com.gifwarno.rozklad.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Чистий Kotlin без Android: доменна логіка, тести без емулятора. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureJvm()
            dependencies {
                "testImplementation"(libs.lib("junit4"))
            }
        }
    }
}
