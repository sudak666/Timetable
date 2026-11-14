package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Club
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubsTest {
    private fun club(title: String, vararg days: DayOfWeek, start: String = "16:00", end: String = "17:30") =
        Club(title, "⚽", days.toSet(), LocalTime.parse(start), LocalTime.parse(end))

    private val football = club("Футбол", TUESDAY, WEDNESDAY)
    private val code = club("Програмування", MONDAY, WEDNESDAY, start = "18:00", end = "19:00")
    private val swim = club("Плавання", WEDNESDAY, start = "07:00", end = "08:00")
    private val all = listOf(football, code, swim)

    // 2026-11-09 — понеділок.
    private val monday = LocalDate.parse("2026-11-09")

    @Test
    fun `заняття дня за часом початку`() {
        assertEquals(listOf("Плавання", "Футбол", "Програмування"), Clubs.on(monday.plusDays(2), all).map { it.title })
        assertEquals(emptyList<Club>(), Clubs.on(monday.plusDays(6), all))
    }

    @Test
    fun `найближче нагадування — строго після зараз, переходить на інші дні`() {
        val (at, c) = checkNotNull(Clubs.nextReminder(monday.atTime(9, 0), all, 30))
        assertEquals(monday.atTime(17, 30), at)
        assertEquals("Програмування", c.title)

        // Нагадування про програмування вже минуло — наступне у вівторок (футбол).
        val next = checkNotNull(Clubs.nextReminder(monday.atTime(17, 30), all, 30))
        assertEquals(monday.plusDays(1).atTime(15, 30), next.first)
        // Ранкове плавання в середу: нагадування за годину — о 6:00.
        assertEquals(monday.plusDays(2).atTime(6, 0), Clubs.nextReminder(monday.plusDays(1).atTime(16, 0), all, 60)?.first)
    }

    @Test
    fun `нагадування через тиждень і вимкнені нагадування`() {
        val only = listOf(club("Шахи", MONDAY))
        assertEquals(LocalDateTime.of(monday.plusDays(7), LocalTime.of(15, 45)), Clubs.nextReminder(monday.atTime(16, 0), only, 15)?.first)
        assertNull(Clubs.nextReminder(monday.atTime(9, 0), only, 0))
        assertNull(Clubs.nextReminder(monday.atTime(9, 0), emptyList(), 30))
    }

    @Test
    fun `текст днів`() {
        assertEquals("Пн, Ср", Clubs.daysText(setOf(WEDNESDAY, MONDAY)))
        assertEquals("Пн–Пт", Clubs.daysText((1..5).map(DayOfWeek::of).toSet()))
        assertEquals("щодня", Clubs.daysText(DayOfWeek.entries.toSet()))
        assertEquals("16:00–17:30", Clubs.timeText(football))
    }

    @Test
    fun `перевірка — як на сервері`() {
        assertEquals(emptyList<String>(), Clubs.validate(all))
        assertTrue(Clubs.validate(listOf(club(" ", MONDAY))).single().contains("назва"))
        assertTrue(Clubs.validate(listOf(club("Х"))).single().contains("день"))
        assertTrue(Clubs.validate(listOf(club("Х", MONDAY, start = "18:00", end = "17:00"))).single().contains("кінець"))
        assertTrue(Clubs.validate(List(Club.MAX + 1) { football }).first().contains("Не більше"))
        assertTrue(Clubs.validate(listOf(football.copy(bring = "а".repeat(Club.MAX_BRING + 1)))).single().contains("речей"))
        // Заняття входять у загальну перевірку розкладу.
        assertTrue(ScheduleValidator.validate(Timetable.EXAMPLE.copy(clubs = listOf(club("Х")))).isNotEmpty())
    }
}
