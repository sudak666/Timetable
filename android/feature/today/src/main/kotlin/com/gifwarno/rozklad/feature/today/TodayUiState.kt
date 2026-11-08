package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.SchoolStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Головна картка «що зараз». */
sealed interface NowCard {
    data class Lesson(
        val subject: String,
        val number: Int,
        val total: Int,
        val secondsLeft: Long,
        val progress: Float,
        val next: String?,
    ) : NowCard

    data class Break(
        val nextSubject: String,
        val nextNumber: Int,
        val breakMinutes: Long,
        val secondsLeft: Long,
        val progress: Float,
        val startsAt: String,
    ) : NowCard

    data class Before(val firstSubject: String, val lessonsCount: Int, val secondsLeft: Long, val startsAt: String) : NowCard

    data class Done(
        val nextDayName: String,
        val nextDate: LocalDate,
        val firstSubject: String,
        val startsAt: String,
        /** Останній день канікул, якщо зараз канікули. */
        val holidayUntil: LocalDate?,
    ) : NowCard
}

enum class LessonState { PAST, CURRENT, UPCOMING }

data class LessonRow(val number: Int, val subject: String, val time: String, val optional: Boolean, val state: LessonState)

data class TodayUiState(
    val now: NowCard,
    val dayTitle: String,
    val isToday: Boolean,
    val lessons: List<LessonRow>,
    val backpack: List<String>,
    /** null — сьогодні не вчимося. */
    val dayProgress: Float?,
)

private val HM = DateTimeFormatter.ofPattern("H:mm")

private fun secondsBetween(a: LocalTime, b: LocalTime) = Duration.between(a, b).seconds

private fun progress(start: LocalTime, end: LocalTime, now: LocalTime): Float =
    (secondsBetween(start, now).toFloat() / secondsBetween(start, end)).coerceIn(0f, 1f)

/**
 * Чиста функція: час + розклад → стан екрана. Тестується без Android.
 * null — у розкладі немає жодного уроку (ще не заповнено).
 */
fun buildTodayState(now: LocalDateTime, cal: SchoolCalendar): TodayUiState? {
    val t = now.toLocalTime()
    val today = now.toLocalDate()
    val status = cal.statusAt(now)

    val card = when (status) {
        is SchoolStatus.InLesson -> NowCard.Lesson(
            subject = status.lessons[status.index].subject,
            number = status.index + 1,
            total = status.lessons.size,
            secondsLeft = secondsBetween(t, status.bell.end),
            progress = progress(status.bell.start, status.bell.end, t),
            next = status.lessons.getOrNull(status.index + 1)?.subject,
        )

        is SchoolStatus.OnBreak -> NowCard.Break(
            nextSubject = status.lessons[status.nextIndex].subject,
            nextNumber = status.nextIndex + 1,
            breakMinutes = Duration.between(status.previous.end, status.next.start).toMinutes(),
            secondsLeft = secondsBetween(t, status.next.start),
            progress = progress(status.previous.end, status.next.start, t),
            startsAt = HM.format(status.next.start),
        )

        is SchoolStatus.BeforeSchool -> NowCard.Before(
            firstSubject = status.lessons.first().subject,
            lessonsCount = status.lessons.size,
            secondsLeft = secondsBetween(t, status.first.start),
            startsAt = HM.format(status.first.start),
        )

        SchoolStatus.Done -> {
            val next = cal.nextSchoolDate(today) ?: return null
            val day = cal.dayOf(next)
            NowCard.Done(
                nextDayName = day?.name.orEmpty(),
                nextDate = next,
                firstSubject = day?.lessons?.firstOrNull()?.subject.orEmpty(),
                startsAt = HM.format(cal.bell(0).start),
                holidayUntil = cal.holidayOf(today)?.end,
            )
        }
    }

    // Після уроків показуємо розклад наступного навчального дня — «що збирати в рюкзак».
    val showToday = status !is SchoolStatus.Done
    val shownDate = if (showToday) today else cal.nextSchoolDate(today) ?: return null
    val day = cal.dayOf(shownDate)
    val lessons = cal.lessonsOn(shownDate).orEmpty()
    val rows = lessons.mapIndexed { i, l ->
        val bell = cal.bell(i)
        val state = when {
            !showToday -> LessonState.UPCOMING
            !t.isBefore(bell.end) -> LessonState.PAST
            !t.isBefore(bell.start) -> LessonState.CURRENT
            else -> LessonState.UPCOMING
        }
        LessonRow(i + 1, l.subject, "${HM.format(bell.start)}–${HM.format(bell.end)}", l.optional, state)
    }
    return TodayUiState(
        now = card,
        dayTitle = when {
            showToday -> "Сьогодні · ${day?.name.orEmpty()}"
            shownDate == today.plusDays(1) -> "Завтра · ${day?.name.orEmpty()}"
            else -> day?.name.orEmpty()
        },
        isToday = showToday,
        lessons = rows,
        backpack = lessons.map { it.subject }.distinct(),
        dayProgress = cal.dayProgress(now),
    )
}
