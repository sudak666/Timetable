import com.android.build.api.dsl.LibraryExtension
import com.gifwarno.rozklad.configureAndroidLibrary
import com.gifwarno.rozklad.lib
import com.gifwarno.rozklad.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroidLibrary(this)
            }
            dependencies {
                "testImplementation"(libs.lib("junit4"))
                "testImplementation"(libs.lib("kotlinx-coroutines-test"))
            }
        }
    }
}
