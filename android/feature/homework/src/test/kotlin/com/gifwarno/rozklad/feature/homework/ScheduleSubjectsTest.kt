package com.gifwarno.rozklad.feature.homework

import com.gifwarno.rozklad.core.model.Lesson
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleSubjectsTest {
    private val week = listOf(
        listOf(Lesson("Математика"), Lesson("Історія")),
        listOf(Lesson("Англ. мова"), Lesson("Математика")),
        listOf(Lesson("STEM")),
        emptyList(),
        listOf(Lesson("Фіз-ра")),
    )

    @Test
    fun `у вівторок спершу вівторкові предмети, далі решта тижня по колу`() {
        val tuesday = LocalDate.parse("2026-11-10")
        assertEquals(listOf("Англ. мова", "Математика", "STEM", "Фіз-ра", "Історія"), scheduleSubjects(week, tuesday))
    }

    @Test
    fun `у неділю — з понеділка`() {
        val sunday = LocalDate.parse("2026-11-15")
        assertEquals(listOf("Математика", "Історія", "Англ. мова", "STEM", "Фіз-ра"), scheduleSubjects(week, sunday))
    }
}
