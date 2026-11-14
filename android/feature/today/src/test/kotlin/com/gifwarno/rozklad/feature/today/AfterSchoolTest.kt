package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Club
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AfterSchoolTest {
    private fun club(title: String, vararg days: DayOfWeek, start: String, end: String, bring: String = "") =
        Club(title, "⚽", days.toSet(), LocalTime.parse(start), LocalTime.parse(end), bring = bring)

    private val football = club("Футбол", MONDAY, SATURDAY, start = "16:00", end = "17:30", bring = "форма")
    private val code = club("Програмування", MONDAY, start = "18:00", end = "19:00")
    private val chess = club("Шахи", TUESDAY, start = "15:00", end = "16:00", bring = "блокнот")
    private val all = listOf(code, football, chess)

    // 2026-11-09 — понеділок.
    private fun at(s: String) = LocalDateTime.parse("2026-11-09T$s")

    @Test
    fun `сьогодні — за часом, з поточним і минулими`() {
        val a = checkNotNull(buildAfterSchool(at("16:30"), all))
        assertTrue(a.isToday)
        assertEquals(listOf("Футбол", "Програмування"), a.rows.map { it.title })
        assertEquals(listOf(LessonState.CURRENT, LessonState.UPCOMING), a.rows.map { it.state })
        assertEquals("16:00–17:30", a.rows.first().time)
        assertEquals(listOf(LessonState.PAST, LessonState.CURRENT), checkNotNull(buildAfterSchool(at("18:10"), all)).rows.map { it.state })
    }

    @Test
    fun `після останнього заняття — завтрашні, а якщо завтра нічого — картки немає`() {
        val a = checkNotNull(buildAfterSchool(at("19:00"), all))
        assertFalse(a.isToday)
        assertEquals(listOf("Шахи"), a.rows.map { it.title })
        // Середа: завтра (четвер) занять немає.
        assertNull(buildAfterSchool(LocalDateTime.parse("2026-11-11T12:00"), all))
    }

    @Test
    fun `заняття показуються й на канікулах`() {
        // 2026-10-26 — понеділок осінніх канікул у прикладі.
        val s = checkNotNull(buildTodayState(LocalDateTime.parse("2026-10-26T12:00"), SchoolCalendar(Timetable.EXAMPLE), clubs = all))
        assertEquals(listOf("Футбол", "Програмування"), s.afterSchool?.rows?.map { it.title })
    }

    @Test
    fun `рюкзак — речі на сьогодні, після уроків — на завтра з позначкою дня`() {
        val today = LocalDate.parse("2026-11-09")
        // Уроки ще йдуть: футбол сьогодні, день збігається з рюкзаком — без позначки.
        assertEquals(listOf(ClubBagItem("⚽", "Футбол", "форма", null)), clubBag(at("10:00"), all, schoolDone = false, bagDate = today))
        // Уроки скінчились, рюкзак на вівторок — шахи завтра.
        assertEquals(listOf("Шахи"), clubBag(at("19:30"), all, schoolDone = true, bagDate = today.plusDays(1)).map { it.title })
        // П'ятниця ввечері: рюкзак на понеділок, а футбол у суботу — з позначкою «Сб».
        val fri = LocalDateTime.parse("2026-11-13T18:00")
        assertEquals("Сб", clubBag(fri, all, schoolDone = true, bagDate = LocalDate.parse("2026-11-16")).single().day)
    }

    @Test
    fun `стан екрана містить картку й речі`() {
        val s = checkNotNull(buildTodayState(at("10:00"), SchoolCalendar(Timetable.EXAMPLE), clubs = all))
        assertEquals(2, s.afterSchool?.rows?.size)
        assertEquals("форма", s.clubBag.single().bring)
    }
}
