package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.Timetable
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayStateTest {
    private val cal = SchoolCalendar(Timetable.EXAMPLE)

    @Test
    fun `під час уроку таймер рахує до дзвінка`() {
        val s = checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-09T09:15:00").minusMinutes(5), cal))
        val card = s.now as NowCard.Lesson
        assertEquals(1, card.number)
        assertEquals(300, card.secondsLeft)
        assertEquals("Всесв. історія", card.next)
        assertEquals(LessonState.CURRENT, s.lessons.first().state)
    }

    @Test
    fun `тривога з 9-50 — укриття для уроків від неї до зараз, майбутні не позначаємо`() {
        val s = checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-09T10:40"), cal, LocalDateTime.parse("2026-11-09T09:50")))
        assertEquals(listOf(false, true, true, false), s.lessons.take(4).map { it.inShelter })
    }

    @Test
    fun `тривога з учора — укриття для всіх сьогоднішніх уроків до зараз`() {
        val s = checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-09T09:30"), cal, LocalDateTime.parse("2026-11-08T23:00")))
        assertEquals(listOf(true, true, false), s.lessons.take(3).map { it.inShelter })
    }

    @Test
    fun `без тривоги позначок немає`() {
        val s = checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-09T10:40"), cal))
        assertTrue(s.lessons.none { it.inShelter })
    }

    @Test
    fun `після уроків показує розклад наступного дня`() {
        val s = checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-09T17:00"), cal))
        assertTrue(s.now is NowCard.Done)
        assertTrue(s.dayTitle.startsWith("Завтра"))
        assertTrue(s.lessons.all { it.state == LessonState.UPCOMING })
    }

    @Test
    fun `на канікулах показує їх кінець`() {
        val s = checkNotNull(buildTodayState(LocalDateTime.parse("2026-10-28T10:00"), cal))
        val card = s.now as NowCard.Done
        assertEquals(LocalDate.parse("2026-11-01"), card.holidayUntil)
        assertEquals(LocalDate.parse("2026-11-02"), card.nextDate)
    }

    @Test
    fun `порожній розклад — стану немає`() {
        assertEquals(null, buildTodayState(LocalDateTime.parse("2026-11-09T10:00"), SchoolCalendar(Timetable.BLANK)))
    }
}
