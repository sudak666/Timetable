package com.gifwarno.rozklad

import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Порожній сервіс-замок: одночасно ним користується лише одне завдання KSP. */
abstract class KspLock : BuildService<BuildServiceParameters.None>

/**
 * Завдання KSP — по одному на всю збірку. KSP2 працює всередині демона Gradle, і кілька модулів
 * водночас заважають одне одному: то «X could not be resolved», то процесор мовчки не бачить
 * анотацій і нічого не генерує (так екран «Оцінки» випав із графа Hilt у версії 4).
 */
fun Project.serializeKsp() {
    val lock = gradle.sharedServices.registerIfAbsent("kspLock", KspLock::class.java) {
        maxParallelUsages.set(1)
    }
    tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
        usesService(lock)
    }
}
