package com.gifwarno.rozklad.core.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolCalendarTest {
    private val cal = SchoolCalendar(Timetable.EXAMPLE)

    // 2026-11-09 — понеділок, не канікули
    private fun at(time: String) = LocalDateTime.parse("2026-11-09T$time")

    @Test
    fun `до першого дзвінка`() {
        val s = cal.statusAt(at("08:00"))
        assertTrue(s is SchoolStatus.BeforeSchool)
    }

    @Test
    fun `під час уроку повертає його індекс`() {
        val s = cal.statusAt(at("09:30")) as SchoolStatus.InLesson
        assertEquals(1, s.index)
        assertEquals("Всесв. історія", s.lessons[s.index].subject)
    }

    @Test
    fun `на перерві показує наступний урок`() {
        val s = cal.statusAt(at("10:15")) as SchoolStatus.OnBreak
        assertEquals(2, s.nextIndex)
    }

    @Test
    fun `після уроків — Done`() {
        assertEquals(SchoolStatus.Done, cal.statusAt(at("16:00")))
    }

    @Test
    fun `канікули й вихідні без уроків`() {
        assertNull(cal.lessonsOn(LocalDate.parse("2026-10-28")))
        assertNull(cal.lessonsOn(LocalDate.parse("2026-11-08")))
        assertEquals(SchoolStatus.Done, cal.statusAt(LocalDateTime.parse("2026-10-28T10:00")))
    }

    @Test
    fun `наступний навчальний день пропускає канікули`() {
        assertEquals(LocalDate.parse("2026-11-02"), cal.nextSchoolDate(LocalDate.parse("2026-10-25")))
        assertEquals(LocalDate.parse("2026-11-09"), cal.nextSchoolDate(LocalDate.parse("2026-11-06")))
    }

    @Test
    fun `наступний урок предмета`() {
        // Інформатика лише у вівторок
        assertEquals(LocalDate.parse("2026-11-10"), cal.nextLessonDate("Інформатика", LocalDate.parse("2026-11-09")))
    }

    @Test
    fun `понеділок тижня`() {
        assertEquals(LocalDate.parse("2026-11-09"), SchoolCalendar.mondayOf(LocalDate.parse("2026-11-13")))
    }

    @Test
    fun `порожній розклад — жодного навчального дня`() {
        val empty = SchoolCalendar(Timetable.BLANK)
        assertNull(empty.nextSchoolDate(LocalDate.parse("2026-11-09")))
        assertEquals(SchoolStatus.Done, empty.statusAt(at("10:00")))
    }
}
