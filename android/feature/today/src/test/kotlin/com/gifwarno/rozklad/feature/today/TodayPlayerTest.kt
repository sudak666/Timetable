package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.testing.TestData
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayPlayerTest {
    private val cal = SchoolCalendar(Timetable.EXAMPLE)
    private val monday = LocalDate.parse("2026-11-09")

    private val data = TestData.familyData(
        grades = listOf(
            TestData.grade("a", 10, "2026-11-05"),
            TestData.grade("b", 12, "2026-11-06"),
            TestData.grade("c", 8, "2026-11-09", GradeStatus.PENDING),
        ),
    ).copy(
        homework = listOf(
            Homework("h1", TestData.child.userId, "Математика", monday.plusDays(1), "№ 5", done = true),
            Homework("h2", TestData.child.userId, "Фізика", monday.plusDays(1), "§ 3", done = false),
            Homework("h3", TestData.child.userId, "Фізика", monday.plusDays(3), "§ 4", done = false),
        ),
    )

    @Test
    fun `гравець — XP і гаманець лише з підтверджених оцінок`() {
        val p = buildPlayer(TestData.child, data, monday)
        assertEquals(1, p.level)
        assertEquals(22, p.xpInLevel)
        assertEquals(500, p.balance)
        assertEquals("🦊", p.avatar)
    }

    @Test
    fun `завдання дня в понеділок — домашка на завтра, оцінка сьогодні, серія`() {
        val q = buildQuests(TestData.child.userId, data, cal, monday)
        assertEquals(listOf("📒", "⭐", "🔥"), q.map { it.emoji })
        assertEquals(1 to 2, q[0].progress to q[0].target)
        assertEquals("Домашка на завтра", q[0].title)
        assertTrue(q[1].done)
        assertEquals(2 to 5, q[2].progress to q[2].target)
    }

    @Test
    fun `у неділю без уроків немає завдання «оцінка сьогодні»`() {
        val q = buildQuests(TestData.child.userId, data, cal, monday.minusDays(1))
        assertFalse(q.any { it.emoji == "⭐" })
    }

    @Test
    fun `салют — лише після уроків у навчальний день`() {
        assertTrue(checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-09T18:00"), cal)).finishedToday)
        assertFalse(checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-09T09:00"), cal)).finishedToday)
        assertFalse(checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-14T18:00"), cal)).finishedToday)
    }
}
