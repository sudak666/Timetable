package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrophiesTest {
    private fun g(id: String, value: Int, date: String) =
        Grade(id, "k1", "Математика", value, LocalDate.parse(date), GradeStatus.APPROVED, null, Instant.parse("${date}T10:00:00Z"))

    @Test
    fun `шлях — рівні з нагородами як у вебі`() {
        val road = Trophies.road(level = 6)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 10, 12, 15), road.map { it.level })
        assertEquals(TrophyReward.Avatar("🐙"), road.first().reward)
        assertTrue(road.first { it.level == 6 }.got)
        assertTrue(road.first { it.level == 7 }.next)
        assertFalse(road.first { it.level == 8 }.next)
        assertEquals(300, road.first { it.level == 7 }.xpNeeded)
    }

    @Test
    fun `значки — серія десяток, перша 12, заробіток і тиждень без мінусів`() {
        val monday = LocalDate.parse("2026-11-09")
        val grades = listOf(g("1", 10, "2026-11-02"), g("2", 12, "2026-11-03"), g("3", 11, "2026-11-04"), g("4", 8, "2026-11-09"))
        val b = Trophies.badges(grades, earned = 1200, rates = Rewards.DEFAULT_RATES, weekStart = monday).associate { it.title to it.got }
        assertEquals(true, b["Перша оцінка"])
        assertEquals(true, b["Перша 12"])
        assertEquals(true, b["3 десятки поспіль"])
        assertEquals(false, b["5 поспіль ≥10"])
        assertEquals(true, b["Заробив 1000 ₴"])
        // На цьому тижні лише одна оцінка — замало для «тижня без мінусів».
        assertEquals(false, b["Тиждень без мінусів"])
    }

    @Test
    fun `колір з рядка #rrggbb`() {
        assertEquals(0xFF6C5CE7L, Trophies.parseColor("#6c5ce7"))
        assertNull(Trophies.parseColor("red"))
        assertNull(Trophies.parseColor(null))
    }
}
