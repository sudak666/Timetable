package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Bell
import com.gifwarno.rozklad.core.model.Holiday
import com.gifwarno.rozklad.core.model.Lesson
import com.gifwarno.rozklad.core.model.SchoolDay
import com.gifwarno.rozklad.core.model.SchoolSchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Що відбувається в школі в конкретний момент. */
sealed interface SchoolStatus {
    data class InLesson(val index: Int, val bell: Bell, val lessons: List<Lesson>) : SchoolStatus

    data class OnBreak(val nextIndex: Int, val previous: Bell, val next: Bell, val lessons: List<Lesson>) : SchoolStatus

    data class BeforeSchool(val first: Bell, val lessons: List<Lesson>) : SchoolStatus

    data object Done : SchoolStatus
}

/** Розрахунки за розкладом конкретної дитини. Чиста логіка — тестується без Android. */
class SchoolCalendar(private val schedule: SchoolSchedule) {
    private val bells get() = schedule.bells

    fun holidayOf(date: LocalDate): Holiday? = schedule.holidays.firstOrNull { date in it }

    fun dayOf(date: LocalDate): SchoolDay? {
        val i = date.dayOfWeek.value - 1
        val lessons = schedule.week.getOrNull(i) ?: return null
        return SchoolDay(Timetable.DAY_NAMES[i], lessons)
    }

    /** Уроки на дату; null — вихідний, канікули або порожній день. */
    fun lessonsOn(date: LocalDate): List<Lesson>? {
        if (holidayOf(date) != null) return null
        // Захист від розкладу, де уроків більше, ніж дзвінків (сервер це забороняє, але дані могли прийти старі).
        return dayOf(date)?.lessons?.take(bells.size)?.takeIf { it.isNotEmpty() }
    }

    fun bell(index: Int): Bell = bells[index]

    fun statusAt(now: LocalDateTime): SchoolStatus {
        val lessons = lessonsOn(now.toLocalDate()) ?: return SchoolStatus.Done
        val t = now.toLocalTime()
        lessons.indices.forEach { i ->
            val b = bells[i]
            if (!t.isBefore(b.start) && t.isBefore(b.end)) return SchoolStatus.InLesson(i, b, lessons)
            if (t.isBefore(b.start)) {
                return if (i == 0) SchoolStatus.BeforeSchool(b, lessons) else SchoolStatus.OnBreak(i, bells[i - 1], b, lessons)
            }
        }
        return SchoolStatus.Done
    }

    /** Наступна навчальна дата після [from] з урахуванням канікул; null — у розкладі немає жодного уроку. */
    fun nextSchoolDate(from: LocalDate): LocalDate? = (1L..MAX_LOOKAHEAD_DAYS).map(from::plusDays).firstOrNull { lessonsOn(it) != null }

    /** Найближча дата після [from], коли є урок предмета (для «здати домашку до…»). */
    fun nextLessonDate(subject: String, from: LocalDate): LocalDate = (1L..MAX_LOOKAHEAD_DAYS).map(from::plusDays)
        .firstOrNull { d -> lessonsOn(d)?.any { it.subject == subject } == true } ?: from.plusDays(1)

    /** Частка навчального дня, що минула (0..1); null — сьогодні не вчимося. */
    fun dayProgress(now: LocalDateTime): Float? {
        val lessons = lessonsOn(now.toLocalDate()) ?: return null
        val start = bells.first().start
        val end = bells[lessons.lastIndex].end
        val total = Duration.between(start, end).seconds.toFloat()
        if (total <= 0f) return null
        val passed = Duration.between(start, now.toLocalTime()).seconds.toFloat()
        return (passed / total).coerceIn(0f, 1f)
    }

    /** Предмети, які зустрічаються в розкладі (для вибору в оцінках і домашці). */
    fun subjects(): List<String> = schedule.week.flatten().map { it.subject }.distinct()

    companion object {
        private const val MAX_LOOKAHEAD_DAYS = 366L

        fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
