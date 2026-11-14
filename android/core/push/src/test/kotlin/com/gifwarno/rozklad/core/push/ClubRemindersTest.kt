package com.gifwarno.rozklad.core.push

import com.gifwarno.rozklad.core.model.Club
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.testing.TestData
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Нагадування про заняття: батькам — для всіх дітей, а не лише вибраної (аудит 14.11.2026, В9). */
class ClubRemindersTest {
    private val football = Club("Футбол", "⚽", setOf(DayOfWeek.MONDAY), LocalTime.of(16, 0), LocalTime.of(17, 30), place = "Стадіон")
    private val chess = Club("Шахи", "♟️", setOf(DayOfWeek.TUESDAY), LocalTime.of(15, 0), LocalTime.of(16, 0))
    private val second = Member("k2", Role.CHILD, "Оля", "", 0, "🐼", "")

    private fun schedule(vararg clubs: Club) =
        SchoolSchedule(bells = emptyList(), week = emptyList(), holidays = emptyList(), clubs = clubs.toList())

    @Test
    fun `батьки отримують заняття всіх дітей з іменами`() {
        val data = TestData.familyData().let { d ->
            d.copy(
                members = d.members + second,
                timetables = mapOf(TestData.child.userId to schedule(football), second.userId to schedule(chess)),
            )
        }
        // На екрані вибрано першу дитину — заняття другої все одно мають нагадуватись.
        val kids = ClubReminders.reminderClubs(TestData.parentContext(data))

        assertEquals(listOf(KidClubs(listOf(football), "Сашко"), KidClubs(listOf(chess), "Оля")), kids)
    }

    @Test
    fun `одна дитина — без імені в тексті`() {
        val data = TestData.familyData().copy(timetables = mapOf(TestData.child.userId to schedule(football)))

        assertEquals(listOf(KidClubs(listOf(football), null)), ClubReminders.reminderClubs(TestData.parentContext(data)))
    }

    @Test
    fun `текст нагадування з іменем дитини`() {
        val (title, body) = ClubReminders.reminderText(football, 30, who = "Оля")

        assertEquals("⚽ Оля: Футбол о 16:00", title)
        assertEquals("Через 30 хв · 📍 Стадіон", body)
    }
}
