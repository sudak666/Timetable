package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Bell
import com.gifwarno.rozklad.core.model.Holiday
import com.gifwarno.rozklad.core.model.Lesson
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleValidatorTest {
    @Test
    fun `приклад і порожній розклад коректні`() {
        assertEquals(emptyList<String>(), ScheduleValidator.validate(Timetable.EXAMPLE))
        assertEquals(emptyList<String>(), ScheduleValidator.validate(Timetable.BLANK))
    }

    @Test
    fun `уроків більше, ніж дзвінків`() {
        val s = Timetable.BLANK.copy(
            bells = Timetable.TYPICAL_BELLS.take(1),
            week =
                listOf(listOf(Lesson("А"), Lesson("Б"))) + List(4) { emptyList() },
        )
        assertTrue(ScheduleValidator.validate(s).single().startsWith("Понеділок"))
    }

    @Test
    fun `дзвінки не по порядку`() {
        val s = Timetable.BLANK.copy(
            bells = listOf(Bell(LocalTime.of(9, 0), LocalTime.of(9, 45)), Bell(LocalTime.of(9, 30), LocalTime.of(10, 15))),
        )
        assertEquals(1, ScheduleValidator.validate(s).size)
    }

    @Test
    fun `кінець уроку раніше за початок і перевернуті канікули`() {
        val s = Timetable.BLANK.copy(
            bells = listOf(Bell(LocalTime.of(9, 0), LocalTime.of(8, 0))),
            holidays = listOf(Holiday(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-01"))),
        )
        assertEquals(2, ScheduleValidator.validate(s).size)
    }

    @Test
    fun `чотири дні — помилка`() {
        assertEquals(1, ScheduleValidator.validate(Timetable.BLANK.copy(week = List(4) { emptyList() })).size)
    }
}
