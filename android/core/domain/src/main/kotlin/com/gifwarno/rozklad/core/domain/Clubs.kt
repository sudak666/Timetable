package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Club
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Шаблон заняття для швидкого додавання: емодзі + типова назва. */
data class ClubKind(val emoji: String, val title: String)

/** Позашкільні заняття: що в який день, коли нагадати, перевірка. Чиста логіка — тестується без Android. */
object Clubs {
    /** Найпоширеніші заняття. Свою назву й емодзі теж можна ввести. */
    val KINDS: List<ClubKind> = listOf(
        ClubKind("⚽", "Футбол"),
        ClubKind("🏀", "Баскетбол"),
        ClubKind("🏐", "Волейбол"),
        ClubKind("🏊", "Плавання"),
        ClubKind("🚴", "Велоспорт"),
        ClubKind("🥋", "Бойові мистецтва"),
        ClubKind("🤸", "Гімнастика"),
        ClubKind("🎾", "Теніс"),
        ClubKind("🏋️", "Тренування"),
        ClubKind("💻", "Програмування"),
        ClubKind("🤖", "Робототехніка"),
        ClubKind("♟️", "Шахи"),
        ClubKind("🎨", "Малювання"),
        ClubKind("🎵", "Музика"),
        ClubKind("💃", "Танці"),
        ClubKind("🇬🇧", "Англійська"),
        ClubKind("📚", "Репетитор"),
    )

    const val DEFAULT_EMOJI = "⭐"

    /** Заняття на дату, за часом початку. Канікули не враховуються — тренування зазвичай тривають. */
    fun on(date: LocalDate, clubs: List<Club>): List<Club> = clubs.filter { it.isOn(date.dayOfWeek) }.sortedBy { it.start }

    /** Найближче нагадування строго після [now]: (момент нагадування, заняття). null — занять немає або [leadMinutes] ≤ 0. */
    fun nextReminder(now: LocalDateTime, clubs: List<Club>, leadMinutes: Int): Pair<LocalDateTime, Club>? {
        if (leadMinutes <= 0) return null
        return (0L..DAYS_IN_WEEK).asSequence()
            .map { now.toLocalDate().plusDays(it) }
            .flatMap { d -> on(d, clubs).map { d.atTime(it.start).minusMinutes(leadMinutes.toLong()) to it } }
            .filter { (at, _) -> at.isAfter(now) }
            .minByOrNull { (at, _) -> at }
    }

    /** «Пн, Ср, Пт»; усі будні — «Пн–Пт», увесь тиждень — «щодня». */
    fun daysText(days: Set<DayOfWeek>): String = when (days) {
        DayOfWeek.entries.toSet() -> "щодня"
        WEEKDAYS -> "Пн–Пт"
        else -> days.sorted().joinToString(", ") { Timetable.DAY_SHORT[it.value - 1] }
    }

    /** «16:00–17:30». */
    fun timeText(c: Club): String = "${HM.format(c.start)}–${HM.format(c.end)}"

    private val HM = DateTimeFormatter.ofPattern("H:mm")
    private val WEEKDAYS = (1..5).map(DayOfWeek::of).toSet()

    /** Ті самі правила, що й school_activities_valid() на сервері. Помилки — людською мовою. */
    fun validate(clubs: List<Club>): List<String> = buildList {
        if (clubs.size > Club.MAX) add("Не більше ${Club.MAX} занять")
        clubs.forEach { c ->
            val name = c.title.trim().ifEmpty { "Заняття без назви" }
            if (c.title.isBlank() || c.title.trim().length > Club.MAX_TITLE) add("$name: порожня або задовга назва")
            if (c.emoji.isBlank() || c.emoji.length > Club.MAX_EMOJI) add("$name: обери емодзі")
            if (c.days.isEmpty()) add("$name: обери хоча б один день")
            if (!c.end.isAfter(c.start)) add("$name: кінець має бути пізніше за початок")
            if (c.place.length > Club.MAX_PLACE) add("$name: задовга назва місця (максимум ${Club.MAX_PLACE})")
            if (c.bring.length > Club.MAX_BRING) add("$name: задовгий список речей (максимум ${Club.MAX_BRING})")
        }
    }

    private const val DAYS_IN_WEEK = 7L
}
