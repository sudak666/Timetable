package com.gifwarno.rozklad.feature.widget

import com.gifwarno.rozklad.core.domain.Timetable
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    private val s = Timetable.EXAMPLE

    // 2026-11-09 — понеділок
    private fun at(time: String) = LocalDateTime.parse("2026-11-09T$time")

    private fun ready(time: String) = buildWidgetState(at(time), "Сашко", s) as WidgetState.Ready

    @Test
    fun `під час уроку — предмет, кінець і наступний`() {
        val r = ready("08:40")
        assertEquals("Математика", r.headline)
        assertEquals("до 9:15 · далі Всесв. історія", r.detail)
        assertEquals(WidgetLessonState.CURRENT, r.lessons.first().state)
        assertEquals("Сьогодні", r.dayTitle)
    }

    @Test
    fun `на перерві — коли й що далі`() {
        val r = ready("09:20")
        assertEquals("Перерва", r.headline)
        assertEquals("2 урок о${Char(0xA0)}9:25 · Всесв. історія", r.detail)
        assertEquals(WidgetLessonState.PAST, r.lessons.first().state)
    }

    @Test
    fun `після уроків — розклад на завтра`() {
        val r = ready("17:00")
        assertEquals("Уроки закінчились", r.headline)
        assertEquals("Завтра", r.dayTitle)
        assertEquals("Література", r.lessons.first().subject)
        assertTrue(r.lessons.all { it.state == WidgetLessonState.UPCOMING })
    }

    @Test
    fun `на канікулах показує їх кінець`() {
        val r = buildWidgetState(LocalDateTime.parse("2026-10-28T10:00"), "Сашко", s) as WidgetState.Ready
        assertTrue(r.headline.startsWith("Канікули до"))
    }

    @Test
    fun `у суботу — вихідні`() {
        val r = buildWidgetState(LocalDateTime.parse("2026-11-14T10:00"), "Сашко", s) as WidgetState.Ready
        assertEquals("Вихідні", r.headline)
    }

    @Test
    fun `без розкладу або порожній розклад — запрошення заповнити`() {
        assertEquals(WidgetState.NoSchedule("Сашко"), buildWidgetState(at("10:00"), "Сашко", null))
        assertEquals(WidgetState.NoSchedule("Сашко"), buildWidgetState(at("10:00"), "Сашко", Timetable.BLANK))
    }

    @Test
    fun `наступне оновлення — найближча межа уроку, інакше північ`() {
        assertEquals(at("09:15"), nextRefreshAt(at("08:40"), s))
        assertEquals(at("09:25"), nextRefreshAt(at("09:15"), s))
        assertEquals(at("08:30"), nextRefreshAt(at("07:00"), s))
        assertEquals(LocalDateTime.parse("2026-11-10T00:00"), nextRefreshAt(at("17:00"), s))
        assertEquals(LocalDateTime.parse("2026-11-10T00:00"), nextRefreshAt(at("10:00"), null))
    }
}
