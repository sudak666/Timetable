import com.gifwarno.rozklad.lib
import com.gifwarno.rozklad.libs
import com.gifwarno.rozklad.serializeKsp
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            dependencies {
                "implementation"(libs.lib("hilt-android"))
                "ksp"(libs.lib("hilt-compiler"))
            }
            failOnEmptyKspOutput()
            serializeKsp()
        }
    }
}

private val HILT_ANNOTATION =
    Regex("@(HiltViewModel|HiltWorker|HiltAndroidApp|AndroidEntryPoint|EntryPoint|Module|Inject)\\b")

/**
 * KSP2 інколи «успішно» завершується, нічого не згенерувавши (12.11: release-збірка модуля оцінок —
 * у версії 4 екран «Оцінки» падав, бо GradesViewModel не потрапив у граф Hilt). Gradle запам'ятовує
 * порожній результат як актуальний, тож помилка тихо доходить до магазину. Тому: якщо в модулі є
 * анотації Hilt, а KSP не створив жодного файлу — збірка падає.
 */
private fun Project.failOnEmptyKspOutput() {
    tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") && !it.name.contains("Test") }.configureEach {
        val variant = name.removePrefix("ksp").removeSuffix("Kotlin").replaceFirstChar { it.lowercase() }
        val sources = fileTree("src/main") { include("**/*.kt") }
        val generated = layout.buildDirectory.dir("generated/ksp/$variant")
        val taskPath = path
        doLast {
            val usesHilt = sources.files.any { HILT_ANNOTATION.containsMatchIn(it.readText()) }
            val empty = generated.get().asFile.walkTopDown().none { it.isFile }
            if (usesHilt && empty) {
                throw GradleException(
                    "$taskPath: у модулі є анотації Hilt, але KSP нічого не згенерував. " +
                        "Запустіть ./gradlew --stop і зберіть ще раз.",
                )
            }
        }
    }
}
