import androidx.room.gradle.RoomExtension
import com.gifwarno.rozklad.lib
import com.gifwarno.rozklad.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room зі схемами в git: міграції перевіряються, а не вгадуються. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            dependencies {
                "implementation"(libs.lib("androidx-room-runtime"))
                "implementation"(libs.lib("androidx-room-ktx"))
                "ksp"(libs.lib("androidx-room-compiler"))
                "testImplementation"(libs.lib("androidx-room-testing"))
            }
        }
    }
}
