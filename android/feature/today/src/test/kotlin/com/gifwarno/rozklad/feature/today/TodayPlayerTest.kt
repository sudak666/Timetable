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

    @Test
    fun `тиждень — дні з часом уроків і лічильник предметів`() {
        val w = buildWeek(Timetable.EXAMPLE, monday)
        assertTrue(w.days.first().isToday)
        assertEquals("8:30–9:15", w.days.first().lessons.first().time)
        assertEquals(w.days.sumOf { it.lessons.size }, w.counts.sumOf { it.count })
        assertTrue(w.counts.zipWithNext().all { (a, b) -> a.count >= b.count })
    }

    @Test
    fun `до вихідних — як у вебі`() {
        // Пт у прикладі — 6 уроків, кінець о 14:20. Понеділок 18:00 → 3 д 20 год 20 хв.
        assertEquals("Ще 3 д 20 год — і вихідні! 🏖️", weekendText(LocalDateTime.parse("2026-11-09T18:00"), cal, 6))
        assertEquals("Ура, вихідні! Відпочивай 🎉", weekendText(LocalDateTime.parse("2026-11-14T10:00"), cal, 6))
        assertEquals("Вихідні вже тут! 🥳", weekendText(LocalDateTime.parse("2026-11-13T15:00"), cal, 6))
    }

    @Test
    fun `вікторина — три різні питання, правильна відповідь серед варіантів`() {
        val r = quizRound(42)
        assertEquals(3, r.map { it.question }.distinct().size)
        assertTrue(r.all { it.right in it.options && it.options.size == 4 })
    }
}
