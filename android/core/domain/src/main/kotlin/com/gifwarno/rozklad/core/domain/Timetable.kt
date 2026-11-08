package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Bell
import com.gifwarno.rozklad.core.model.Lesson
import com.gifwarno.rozklad.core.model.SchoolDay
import com.gifwarno.rozklad.core.model.SubjectInfo
import java.time.LocalDate
import java.time.LocalTime

/**
 * Розклад, дзвінки, канікули й предмети. Поки що збігається з веб-версією (src/data/schedule.ts).
 * Наступний крок — перенести в Supabase, щоб кожна сім'я мала свій розклад.
 */
object Timetable {
    val BELLS: List<Bell> = listOf(
        "8:30" to "9:15",
        "9:25" to "10:10",
        "10:30" to "11:15",
        "11:25" to "12:10",
        "12:30" to "13:15",
        "13:35" to "14:20",
        "14:30" to "15:15",
    ).map { (a, b) -> Bell(time(a), time(b)) }

    private fun time(s: String): LocalTime = s.split(":").let { (h, m) -> LocalTime.of(h.toInt(), m.toInt()) }

    private fun day(name: String, vararg subjects: String) = SchoolDay(name, subjects.map { Lesson(it) })

    /** Понеділок … п'ятниця. */
    val WEEK: List<SchoolDay> = listOf(
        SchoolDay(
            "Понеділок",
            listOf("Математика", "Всесв. історія", "Література", "Англ. мова", "Укр. мова", "STEM").map { Lesson(it) } +
                Lesson("Математика", optional = true),
        ),
        day("Вівторок", "Література", "Інформатика", "Укр. мова", "Географія", "Математика", "Фіз-ра"),
        day("Середа", "Англ. мова", "Математика", "Технології", "Укр. мова", "Громад. освіта", "Довкілля", "STEM"),
        day("Четвер", "Математика", "Укр. мова", "Географія", "Фіз-ра", "Англ. мова", "Мистецтво", "ЗБД"),
        day("П’ятниця", "Всесв. історія", "Англ. мова", "Література", "Фіз-ра", "Довкілля", "Математика"),
    )

    /** Канікули: перший і останній день включно. Київ 2026/2027. */
    val HOLIDAYS: List<ClosedRange<LocalDate>> = listOf(
        "2026-10-26" to "2026-11-01",
        "2026-12-24" to "2027-01-10",
        "2027-03-22" to "2027-03-28",
        "2027-05-29" to "2027-08-31",
    ).map { (a, b) -> LocalDate.parse(a)..LocalDate.parse(b) }

    val SUBJECTS: List<SubjectInfo> = listOf(
        SubjectInfo("Математика", 0xFF6C5CE7, "🧮"),
        SubjectInfo("Всесв. історія", 0xFFB84A00, "🏛️"),
        SubjectInfo("Література", 0xFFC2185B, "📚"),
        SubjectInfo("Англ. мова", 0xFF0869B5, "🇬🇧"),
        SubjectInfo("Укр. мова", 0xFF8A5C00, "✍️"),
        SubjectInfo("STEM", 0xFF00866B, "🤖"),
        SubjectInfo("Інформатика", 0xFF00797A, "💻"),
        SubjectInfo("Географія", 0xFF12806A, "🌍"),
        SubjectInfo("Фіз-ра", 0xFFC62828, "⚽"),
        SubjectInfo("Технології", 0xFF6D5BD0, "🛠️"),
        SubjectInfo("Громад. освіта", 0xFFAD1F76, "🤝"),
        SubjectInfo("Довкілля", 0xFF1E7D43, "🌱"),
        SubjectInfo("Мистецтво", 0xFFB35C00, "🎨"),
        SubjectInfo("ЗБД", 0xFF4B5563, "🦺"),
    )

    private val bySubject = SUBJECTS.associateBy { it.name }

    fun subject(name: String): SubjectInfo = bySubject[name] ?: SubjectInfo(name, 0xFF4B5563, "📘")
}
