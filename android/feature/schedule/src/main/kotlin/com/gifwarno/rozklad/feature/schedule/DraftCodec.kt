package com.gifwarno.rozklad.feature.schedule

import com.gifwarno.rozklad.core.model.Bell
import com.gifwarno.rozklad.core.model.Club
import com.gifwarno.rozklad.core.model.Holiday
import com.gifwarno.rozklad.core.model.Lesson
import com.gifwarno.rozklad.core.model.SchoolSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Незбережена чернетка розкладу в SavedStateHandle: якщо система вб'є процес (батьки пішли в месенджер
 * глянути фото розкладу), після повернення редактор відкриється з тими самими змінами.
 * Розмір — кілька КБ (сервер і так обмежує розклад 32 КБ), Bundle це витримує.
 */
internal object DraftCodec {
    data class Draft(val childId: String, val day: Int, val schedule: SchoolSchedule)

    @Serializable
    private data class Dto(
        val childId: String,
        val day: Int,
        /** «08:30-09:15». */
        val bells: List<String>,
        val week: List<List<LessonDto>>,
        /** «2026-12-24/2027-01-07». */
        val holidays: List<String>,
        val clubs: List<ClubDto> = emptyList(),
    )

    /** Заняття: дні 1..7, час «16:00». */
    @Serializable
    private data class ClubDto(
        val t: String,
        val e: String,
        val d: List<Int>,
        val a: String,
        val b: String,
        val p: String = "",
        val n: String = "",
    )

    @Serializable
    private data class LessonDto(val s: String, val o: Boolean = false)

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(childId: String, day: Int, s: SchoolSchedule): String = json.encodeToString(
        Dto.serializer(),
        Dto(
            childId = childId,
            day = day,
            bells = s.bells.map { "${it.start}-${it.end}" },
            week = s.week.map { day -> day.map { LessonDto(it.subject, it.optional) } },
            holidays = s.holidays.map { "${it.start}/${it.end}" },
            clubs = s.clubs.map { c ->
                ClubDto(c.title, c.emoji, c.days.map { it.value }.sorted(), "${c.start}", "${c.end}", c.place, c.bring)
            },
        ),
    )

    /** null — немає чернетки або вона зіпсована (тоді просто відкриваємо збережений розклад). */
    fun decode(raw: String?): Draft? = raw?.let {
        runCatching {
            val d = json.decodeFromString(Dto.serializer(), it)
            Draft(
                childId = d.childId,
                day = d.day,
                schedule = SchoolSchedule(
                    bells = d.bells.map { b ->
                        val (start, end) = b.split('-', limit = 2)
                        Bell(LocalTime.parse(start), LocalTime.parse(end))
                    },
                    week = d.week.map { day -> day.map { l -> Lesson(l.s, l.o) } },
                    holidays = d.holidays.map { h ->
                        val (start, end) = h.split('/', limit = 2)
                        Holiday(LocalDate.parse(start), LocalDate.parse(end))
                    },
                    clubs = d.clubs.map { c ->
                        Club(c.t, c.e, c.d.map(DayOfWeek::of).toSet(), LocalTime.parse(c.a), LocalTime.parse(c.b), c.p, c.n)
                    },
                ),
            )
        }.getOrNull()
    }
}
