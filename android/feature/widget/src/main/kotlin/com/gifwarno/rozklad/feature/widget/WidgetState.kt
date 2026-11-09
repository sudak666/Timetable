package com.gifwarno.rozklad.feature.widget

import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.SchoolStatus
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.SchoolSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class WidgetLessonState { PAST, CURRENT, UPCOMING }

data class WidgetLesson(val number: Int, val subject: String, val emoji: String, val start: String, val state: WidgetLessonState)

/**
 * Що показує віджет. Лише абсолютний час («до 9:15»), без зворотного відліку:
 * віджет оновлюється на межах уроків, і навіть із затримкою текст лишається правдивим.
 */
sealed interface WidgetState {
    data object SignedOut : WidgetState

    data class NoSchedule(val childName: String) : WidgetState

    data class Ready(
        val childName: String,
        val emoji: String,
        val headline: String,
        val detail: String,
        val dayTitle: String,
        val lessons: List<WidgetLesson>,
    ) : WidgetState
}

private val HM = DateTimeFormatter.ofPattern("H:mm")
private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.forLanguageTag("uk-UA"))

private fun hm(t: LocalTime) = HM.format(t)

/** Чиста функція: час + розклад → вміст віджета. */
fun buildWidgetState(now: LocalDateTime, childName: String, schedule: SchoolSchedule?): WidgetState {
    if (schedule == null) return WidgetState.NoSchedule(childName)
    val cal = SchoolCalendar(schedule)
    val today = now.toLocalDate()
    val t = now.toLocalTime()
    val status = cal.statusAt(now)

    // Після уроків (і у вихідні) — розклад наступного навчального дня: «що збирати в рюкзак».
    val shownDate: LocalDate = if (status is SchoolStatus.Done) {
        cal.nextSchoolDate(
            today,
        ) ?: return WidgetState.NoSchedule(childName)
    } else {
        today
    }
    val lessons = cal.lessonsOn(shownDate).orEmpty()
    val isToday = shownDate == today

    val (emoji, headline, detail) = when (status) {
        is SchoolStatus.InLesson -> {
            val subject = status.lessons[status.index].subject
            val next = status.lessons.getOrNull(status.index + 1)
            Triple(
                Timetable.subject(subject).emoji,
                subject,
                "до ${hm(status.bell.end)}" + (next?.let { " · далі ${it.subject}" } ?: " · останній урок"),
            )
        }

        is SchoolStatus.OnBreak -> {
            val next = status.lessons[status.nextIndex].subject
            Triple("☕", "Перерва", "${status.nextIndex + 1} урок о ${hm(status.next.start)} · $next")
        }

        is SchoolStatus.BeforeSchool -> {
            val first = status.lessons.first().subject
            Triple(Timetable.subject(first).emoji, first, "перший урок о ${hm(status.first.start)}")
        }

        SchoolStatus.Done -> {
            val first = lessons.firstOrNull()?.subject.orEmpty()
            val holiday = cal.holidayOf(today)
            val headline = when {
                holiday != null -> "Канікули до ${DAY.format(holiday.end)}"
                today.dayOfWeek.value > lessonDays(schedule) -> "Вихідні"
                else -> "Уроки закінчились"
            }
            Triple(if (holiday != null) "🏖️" else "🎉", headline, "далі: $first о ${hm(cal.bell(0).start)}")
        }
    }

    val rows = lessons.mapIndexed { i, l ->
        val bell = cal.bell(i)
        val state = when {
            !isToday -> WidgetLessonState.UPCOMING
            !t.isBefore(bell.end) -> WidgetLessonState.PAST
            !t.isBefore(bell.start) -> WidgetLessonState.CURRENT
            else -> WidgetLessonState.UPCOMING
        }
        WidgetLesson(i + 1, l.subject, Timetable.subject(l.subject).emoji, hm(bell.start), state)
    }
    val dayTitle = when (shownDate) {
        today -> "Сьогодні"
        today.plusDays(1) -> "Завтра"
        else -> DAY.format(shownDate).replaceFirstChar { it.titlecase(Locale.forLanguageTag("uk-UA")) }
    }
    return WidgetState.Ready(childName, emoji, headline, detail, dayTitle, rows)
}

private fun lessonDays(s: SchoolSchedule) = s.week.size

/**
 * Найближчий момент, коли вміст віджета зміниться: початок або кінець уроку сьогодні,
 * інакше — північ (новий день). Віджет оновиться саме тоді.
 */
fun nextRefreshAt(now: LocalDateTime, schedule: SchoolSchedule?): LocalDateTime {
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
    if (schedule == null) return midnight
    val cal = SchoolCalendar(schedule)
    val lessons = cal.lessonsOn(now.toLocalDate()) ?: return midnight
    val t = now.toLocalTime()
    return lessons.indices
        .flatMap { listOf(cal.bell(it).start, cal.bell(it).end) }
        .firstOrNull { it.isAfter(t) }
        ?.let { now.toLocalDate().atTime(it) }
        ?: midnight
}
