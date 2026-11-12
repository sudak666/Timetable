package com.gifwarno.rozklad.demo

import android.content.Context
import com.gifwarno.rozklad.core.common.ClockOverride
import com.gifwarno.rozklad.core.data.di.RepositoryOverride
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.model.Role
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Демо-режим (лише debug-збірка): вигадана сім'я, усе в пам'яті, жодного запиту на сервер.
 * Для огляду екранів і скриншотів магазину — без реальних імен, пошти й кодів.
 *
 * Увімкнути: файл `files/demo` у даних застосунку (див. android/README.md, розділ «Демо-режим»):
 * ```
 * role=child      # або parent
 * time=10:40      # який зараз час у демо (далі годинник іде)
 * kids=0          # сім'я без дитини (порожні стани з запрошенням)
 * family=none     # увійшли, але сім'ї ще немає (екран «Майже готово!»)
 * ```
 * Вимкнути — видалити файл і перезапустити застосунок.
 */
class DemoMode(val role: Role, start: LocalTime, noKids: Boolean = false, noFamily: Boolean = false) {
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Найближчий навчальний день (сьогодні або останній будній) — щоб у вихідні теж був «шкільний» день. */
    val today: LocalDate = generateSequence(LocalDate.now(zone)) { it.minusDays(1) }
        .first { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }

    /** Годинник іде від [start] обраного дня — таймери й «до дзвінка» живі. */
    val clock: Clock = run {
        val real = Clock.system(zone)
        Clock.offset(real, Duration.between(real.instant(), today.atTime(start).atZone(zone).toInstant()))
    }

    private val data = DemoData(today, role, noKids, noFamily)
    val session: SessionRepository = DemoSessionRepository(data.session)
    val family: FamilyRepository = DemoFamilyRepository(data.family, clock)

    companion object {
        fun read(context: Context): DemoMode? {
            val f = File(context.filesDir, "demo")
            if (!f.exists()) return null
            val kv = f.readLines().mapNotNull { line ->
                line.substringBefore('#').split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
            }.toMap()
            val role = if (kv["role"] == "parent") Role.PARENT else Role.CHILD
            val time = kv["time"]?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() } ?: LocalTime.of(10, 40)
            return DemoMode(role, time, noKids = kv["kids"] == "0", noFamily = kv["family"] == "none")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object DemoModule {
    @Provides
    @Singleton
    fun demo(@ApplicationContext context: Context): Holder = Holder(DemoMode.read(context))

    @Provides
    fun clockOverride(h: Holder): ClockOverride = object : ClockOverride {
        override fun clock(): Clock? = h.mode?.clock
    }

    @Provides
    fun repositoryOverride(h: Holder): RepositoryOverride = object : RepositoryOverride {
        override fun session(): SessionRepository? = h.mode?.session

        override fun family(): FamilyRepository? = h.mode?.family
    }

    /** Обгортка: Dagger не вміє null-синглтони. */
    class Holder(val mode: DemoMode?)
}
