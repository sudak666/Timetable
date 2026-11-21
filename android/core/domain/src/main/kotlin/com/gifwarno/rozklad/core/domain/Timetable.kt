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

    /**
     * Предмети НУШ і старшої школи. Свій предмет теж можна ввести — отримає нейтральну іконку.
     * Кольори — як обкладинки зошитів за групами: мови теплі (теракота, бордо), точні науки сині,
     * природа зелена, мистецтво й спорт — помаранчево-червоні. Біла цифра на кожному ≥ 5:1 (WCAG AA).
     * Ті самі кольори — у вебі (src/data/schedule.ts).
     */
    val SUBJECTS: List<SubjectInfo> = listOf(
        SubjectInfo("Математика", 0xFF1E56A0, "🧮"),
        SubjectInfo("Алгебра", 0xFF2A4A96, "➗"),
        SubjectInfo("Геометрія", 0xFF3B4290, "📐"),
        SubjectInfo("Укр. мова", 0xFFB03A24, "✍️"),
        SubjectInfo("Укр. література", 0xFF8F3A28, "📖"),
        SubjectInfo("Література", 0xFFA1364F, "📚"),
        SubjectInfo("Зарубіжна література", 0xFF88406A, "🌐"),
        SubjectInfo("Англ. мова", 0xFF1B6A86, "🇬🇧"),
        SubjectInfo("Нім. мова", 0xFF2D5A6B, "🇩🇪"),
        SubjectInfo("Історія України", 0xFF8A6200, "🇺🇦"),
        SubjectInfo("Всесв. історія", 0xFFA0561C, "🏛️"),
        SubjectInfo("Громад. освіта", 0xFF76502E, "🤝"),
        SubjectInfo("Я досліджую світ", 0xFF2E7A3B, "🔍"),
        SubjectInfo("Природознавство", 0xFF3E6B22, "🍃"),
        SubjectInfo("Довкілля", 0xFF2A7552, "🌱"),
        SubjectInfo("Біологія", 0xFF2B7340, "🧬"),
        SubjectInfo("Географія", 0xFF1D7066, "🌍"),
        SubjectInfo("Фізика", 0xFF2E4777, "⚛️"),
        SubjectInfo("Хімія", 0xFF7A3E78, "🧪"),
        SubjectInfo("Астрономія", 0xFF343F6C, "🔭"),
        SubjectInfo("Інформатика", 0xFF0E6A70, "💻"),
        SubjectInfo("STEM", 0xFF1B735E, "🤖"),
        SubjectInfo("Технології", 0xFF5C6626, "🛠️"),
        SubjectInfo("Мистецтво", 0xFFAD5418, "🎨"),
        SubjectInfo("Музика", 0xFF963A66, "🎵"),
        SubjectInfo("Фіз-ра", 0xFFBC3326, "⚽"),
        SubjectInfo("Здоров’я", 0xFFAE3F27, "❤️"),
        SubjectInfo("ЗБД", 0xFF4B5563, "🦺"),
        SubjectInfo("Захист України", 0xFF3E4A38, "🛡️"),
    )

    private val bySubject = SUBJECTS.associateBy { it.name }

    fun subject(name: String): SubjectInfo = bySubject[name] ?: SubjectInfo(name, 0xFF4B5563, "📘")
}
