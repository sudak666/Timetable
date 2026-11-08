package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Bell
import com.gifwarno.rozklad.core.model.Lesson
import com.gifwarno.rozklad.core.model.SchoolDay
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

/** Розрахунки за розкладом; розклад передається явно — легко тестувати й замінити на розклад із сервера. */
class SchoolCalendar(
    private val week: List<SchoolDay> = Timetable.WEEK,
    private val bells: List<Bell> = Timetable.BELLS,
    private val holidays: List<ClosedRange<LocalDate>> = Timetable.HOLIDAYS,
) {
    fun holidayOf(date: LocalDate): ClosedRange<LocalDate>? = holidays.firstOrNull { date in it }

    fun dayOf(date: LocalDate): SchoolDay? = week.getOrNull(date.dayOfWeek.value - 1)

    /** Уроки на дату; null — вихідний або канікули. */
    fun lessonsOn(date: LocalDate): List<Lesson>? = if (holidayOf(date) != null) null else dayOf(date)?.lessons

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

    /** Наступна навчальна дата після [from] з урахуванням канікул. */
    fun nextSchoolDate(from: LocalDate): LocalDate =
        (1L..MAX_LOOKAHEAD_DAYS).map(from::plusDays).firstOrNull { lessonsOn(it) != null } ?: from.plusDays(1)

    /** Найближча дата після [from], коли є урок предмета (для «здати домашку до…»). */
    fun nextLessonDate(subject: String, from: LocalDate): LocalDate = (1L..MAX_LOOKAHEAD_DAYS).map(from::plusDays)
        .firstOrNull { d -> lessonsOn(d)?.any { it.subject == subject } == true } ?: from.plusDays(1)

    /** Частка навчального дня, що минула (0..1); null — сьогодні не вчимося. */
    fun dayProgress(now: LocalDateTime): Float? {
        val lessons = lessonsOn(now.toLocalDate()) ?: return null
        val start = bells.first().start
        val end = bells[lessons.lastIndex].end
        val total = Duration.between(start, end).seconds.toFloat()
        val passed = Duration.between(start, now.toLocalTime()).seconds.toFloat()
        return (passed / total).coerceIn(0f, 1f)
    }

    companion object {
        private const val MAX_LOOKAHEAD_DAYS = 366L

        fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
