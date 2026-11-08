package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.SchoolSchedule

/**
 * Ті самі правила, що й у school_timetable_valid() на сервері, плюс логічні
 * (дзвінки по порядку). Помилки — людською мовою, щоб показати в редакторі.
 */
object ScheduleValidator {
    fun validate(s: SchoolSchedule): List<String> = buildList {
        if (s.bells.isEmpty()) add("Додай хоча б один дзвінок")
        if (s.bells.size > SchoolSchedule.MAX_LESSONS) add("Не більше ${SchoolSchedule.MAX_LESSONS} уроків на день")
        s.bells.forEachIndexed { i, b ->
            if (!b.end.isAfter(b.start)) add("${i + 1} урок: кінець має бути пізніше за початок")
            val prev = s.bells.getOrNull(i - 1)
            if (prev != null && b.start.isBefore(prev.end)) add("${i + 1} урок починається раніше, ніж закінчується $i урок")
        }
        if (s.week.size !in SchoolSchedule.MIN_DAYS..SchoolSchedule.MAX_DAYS) add("У тижні має бути від 5 до 7 днів")
        s.week.forEachIndexed { d, lessons ->
            if (lessons.size > s.bells.size) {
                add("${Timetable.DAY_NAMES.getOrElse(d) { "День ${d + 1}" }}: уроків більше, ніж дзвінків (${s.bells.size})")
            }
            if (lessons.any { it.subject.isBlank() || it.subject.length > SchoolSchedule.MAX_SUBJECT }) {
                add("${Timetable.DAY_NAMES.getOrElse(d) { "День ${d + 1}" }}: порожня або задовга назва предмета")
            }
        }
        if (s.holidays.size > SchoolSchedule.MAX_HOLIDAYS) add("Забагато канікул (максимум ${SchoolSchedule.MAX_HOLIDAYS})")
        if (s.holidays.any { it.end.isBefore(it.start) }) add("Канікули: кінець раніше за початок")
    }
}
