package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.Clubs
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Club
import java.time.LocalDate
import java.time.LocalDateTime

/** Рядок картки «Після школи». */
data class ClubRow(val emoji: String, val title: String, val time: String, val place: String, val bring: String, val state: LessonState)

/** Картка «Після школи»: сьогоднішні заняття, а коли вони вже минули — завтрашні. */
data class AfterSchool(val title: String, val isToday: Boolean, val rows: List<ClubRow>)

/** Що взяти на заняття — для рюкзака. [day] — короткий день, якщо заняття не того дня, на який збираємо рюкзак. */
data class ClubBagItem(val emoji: String, val title: String, val bring: String, val day: String?)

/**
 * Сьогодні показуємо, поки не закінчилось останнє заняття; потім — завтрашні (якщо є).
 * Нічого немає ні сьогодні, ні завтра — картки немає.
 */
fun buildAfterSchool(now: LocalDateTime, clubs: List<Club>): AfterSchool? {
    val today = now.toLocalDate()
    val t = now.toLocalTime()
    val todays = Clubs.on(today, clubs)
    if (todays.any { t.isBefore(it.end) }) {
        return AfterSchool(
            title = "Після школи",
            isToday = true,
            rows = todays.map { c ->
                val state = when {
                    !t.isBefore(c.end) -> LessonState.PAST
                    !t.isBefore(c.start) -> LessonState.CURRENT
                    else -> LessonState.UPCOMING
                }
                c.row(state)
            },
        )
    }
    val tomorrow = Clubs.on(today.plusDays(1), clubs).ifEmpty { return null }
    return AfterSchool("Завтра після школи", isToday = false, rows = tomorrow.map { it.row(LessonState.UPCOMING) })
}

private fun Club.row(state: LessonState) = ClubRow(emoji, title, Clubs.timeText(this), place, bring, state)

/**
 * Речі для занять: на сьогодні, поки шкільний день не скінчився ([schoolDone] = false), інакше на завтра.
 * Позначаємо день, якщо він не збігається з [bagDate] — днем, на який рюкзак зі шкільними предметами.
 */
fun clubBag(now: LocalDateTime, clubs: List<Club>, schoolDone: Boolean, bagDate: LocalDate): List<ClubBagItem> {
    val today = now.toLocalDate()
    val date = if (schoolDone) today.plusDays(1) else today
    val t = now.toLocalTime()
    return Clubs.on(date, clubs)
        .filter { it.bring.isNotBlank() && (date != today || t.isBefore(it.end)) }
        .map { ClubBagItem(it.emoji, it.title, it.bring, Timetable.DAY_SHORT[date.dayOfWeek.value - 1].takeIf { date != bagDate }) }
}
