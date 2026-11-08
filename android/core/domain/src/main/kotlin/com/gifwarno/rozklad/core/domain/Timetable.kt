package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Bell
import com.gifwarno.rozklad.core.model.Holiday
import com.gifwarno.rozklad.core.model.Lesson
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.model.SubjectInfo
import java.time.LocalDate
import java.time.LocalTime

/** Довідники: предмети, типові дзвінки, назви днів. Сам розклад — у кожної дитини свій (SchoolSchedule). */
object Timetable {
    val DAY_NAMES = listOf("Понеділок", "Вівторок", "Середа", "Четвер", "П’ятниця", "Субота", "Неділя")
    val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")

    private fun time(s: String): LocalTime = s.split(":").let { (h, m) -> LocalTime.of(h.toInt(), m.toInt()) }

    private fun bells(vararg pairs: Pair<String, String>) = pairs.map { (a, b) -> Bell(time(a), time(b)) }

    /** Найпоширеніший шкільний дзвінок: 45 хв уроки, перерви 10/20 хв. Стартова точка для нового розкладу. */
    val TYPICAL_BELLS: List<Bell> = bells(
        "8:30" to "9:15",
        "9:25" to "10:10",
        "10:30" to "11:15",
        "11:25" to "12:10",
        "12:30" to "13:15",
        "13:35" to "14:20",
        "14:30" to "15:15",
    )

    /** Порожній розклад на 5 днів із типовими дзвінками — з цього починає нова сім'я. */
    val BLANK = SchoolSchedule(TYPICAL_BELLS, List(SchoolSchedule.MIN_DAYS) { emptyList() }, emptyList())

    /** Приклад (розклад, з якого почався проєкт) — для тестів і прев'ю. */
    val EXAMPLE = SchoolSchedule(
        bells = TYPICAL_BELLS,
        week = listOf(
            listOf("Математика", "Всесв. історія", "Література", "Англ. мова", "Укр. мова", "STEM").map { Lesson(it) } +
                Lesson("Математика", optional = true),
            listOf("Література", "Інформатика", "Укр. мова", "Географія", "Математика", "Фіз-ра").map { Lesson(it) },
            listOf("Англ. мова", "Математика", "Технології", "Укр. мова", "Громад. освіта", "Довкілля", "STEM").map { Lesson(it) },
            listOf("Математика", "Укр. мова", "Географія", "Фіз-ра", "Англ. мова", "Мистецтво", "ЗБД").map { Lesson(it) },
            listOf("Всесв. історія", "Англ. мова", "Література", "Фіз-ра", "Довкілля", "Математика").map { Lesson(it) },
        ),
        holidays = listOf(
            "2026-10-26" to "2026-11-01",
            "2026-12-24" to "2027-01-10",
            "2027-03-22" to "2027-03-28",
            "2027-05-29" to "2027-08-31",
        ).map { (a, b) -> Holiday(LocalDate.parse(a), LocalDate.parse(b)) },
    )

    /** Предмети НУШ і старшої школи. Свій предмет теж можна ввести — отримає нейтральну іконку. */
    val SUBJECTS: List<SubjectInfo> = listOf(
        SubjectInfo("Математика", 0xFF6C5CE7, "🧮"),
        SubjectInfo("Алгебра", 0xFF5B4BD6, "➗"),
        SubjectInfo("Геометрія", 0xFF4A3AC4, "📐"),
        SubjectInfo("Укр. мова", 0xFF8A5C00, "✍️"),
        SubjectInfo("Укр. література", 0xFFA0522D, "📖"),
        SubjectInfo("Література", 0xFFC2185B, "📚"),
        SubjectInfo("Зарубіжна література", 0xFFAD1457, "🌐"),
        SubjectInfo("Англ. мова", 0xFF0869B5, "🇬🇧"),
        SubjectInfo("Нім. мова", 0xFF1A5276, "🇩🇪"),
        SubjectInfo("Історія України", 0xFF1F5FAD, "🇺🇦"),
        SubjectInfo("Всесв. історія", 0xFFB84A00, "🏛️"),
        SubjectInfo("Громад. освіта", 0xFFAD1F76, "🤝"),
        SubjectInfo("Я досліджую світ", 0xFF2E7D32, "🔍"),
        SubjectInfo("Природознавство", 0xFF33691E, "🍃"),
        SubjectInfo("Довкілля", 0xFF1E7D43, "🌱"),
        SubjectInfo("Біологія", 0xFF2E7D32, "🧬"),
        SubjectInfo("Географія", 0xFF12806A, "🌍"),
        SubjectInfo("Фізика", 0xFF283593, "⚛️"),
        SubjectInfo("Хімія", 0xFF6A1B9A, "🧪"),
        SubjectInfo("Астрономія", 0xFF303F9F, "🔭"),
        SubjectInfo("Інформатика", 0xFF00797A, "💻"),
        SubjectInfo("STEM", 0xFF00866B, "🤖"),
        SubjectInfo("Технології", 0xFF6D5BD0, "🛠️"),
        SubjectInfo("Мистецтво", 0xFFB35C00, "🎨"),
        SubjectInfo("Музика", 0xFFC2185B, "🎵"),
        SubjectInfo("Фіз-ра", 0xFFC62828, "⚽"),
        SubjectInfo("Здоров’я", 0xFFBF360C, "❤️"),
        SubjectInfo("ЗБД", 0xFF4B5563, "🦺"),
        SubjectInfo("Захист України", 0xFF37474F, "🛡️"),
    )

    private val bySubject = SUBJECTS.associateBy { it.name }

    fun subject(name: String): SubjectInfo = bySubject[name] ?: SubjectInfo(name, 0xFF4B5563, "📘")
}
