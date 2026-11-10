package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.SchoolSchedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class WeekLesson(val number: Int, val time: String, val subject: String, val optional: Boolean)

data class WeekDay(val index: Int, val name: String, val short: String, val lessons: List<WeekLesson>, val isToday: Boolean)

/** Скільки уроків предмета на тиждень — як «Уроків на тиждень» у вебі. */
data class SubjectCount(val subject: String, val count: Int)

data class WeekSchedule(val days: List<WeekDay>, val counts: List<SubjectCount>)

private val HM = DateTimeFormatter.ofPattern("H:mm")

/** Чиста функція: розклад → тиждень по днях і лічильник предметів (найчастіші зверху). */
fun buildWeek(schedule: SchoolSchedule, today: LocalDate): WeekSchedule {
    val todayIndex = today.dayOfWeek.value - 1
    val days = schedule.week.mapIndexed { i, lessons ->
        WeekDay(
            index = i,
            name = Timetable.DAY_NAMES[i],
            short = Timetable.DAY_SHORT[i],
            lessons = lessons.take(schedule.bells.size).mapIndexed { n, l ->
                val b = schedule.bells[n]
                WeekLesson(n + 1, "${HM.format(b.start)}–${HM.format(b.end)}", l.subject, l.optional)
            },
            isToday = i == todayIndex,
        )
    }
    val counts = days.flatMap { d -> d.lessons.map { it.subject } }
        .groupingBy { it }
        .eachCount()
        .map { (s, n) -> SubjectCount(s, n) }
        .sortedWith(compareByDescending<SubjectCount> { it.count }.thenBy { it.subject })
    return WeekSchedule(days, counts)
}
