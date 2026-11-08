package com.gifwarno.rozklad.core.model

import java.time.LocalDate
import java.time.LocalTime

data class Lesson(val subject: String, val optional: Boolean = false)

data class SchoolDay(val name: String, val lessons: List<Lesson>)

data class Bell(val start: LocalTime, val end: LocalTime)

/** Канікули: перший і останній день включно. */
data class Holiday(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Розклад конкретної дитини (таблиця school_timetables).
 * [week] — з понеділка, 5..7 днів; i-й урок дня йде за i-м дзвінком.
 */
data class SchoolSchedule(val bells: List<Bell>, val week: List<List<Lesson>>, val holidays: List<Holiday>) {
    val isEmpty: Boolean get() = week.all { it.isEmpty() }

    companion object {
        const val MIN_DAYS = 5
        const val MAX_DAYS = 7
        const val MAX_LESSONS = 12
        const val MAX_HOLIDAYS = 30
        const val MAX_SUBJECT = 60
    }
}

/** Предмет: колір (ARGB) та емодзі для інтерфейсу. */
data class SubjectInfo(val name: String, val color: Long, val emoji: String)
