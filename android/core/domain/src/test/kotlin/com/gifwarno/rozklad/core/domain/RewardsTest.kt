package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.domain.Rewards.DEFAULT_RATES
import com.gifwarno.rozklad.core.model.Challenge
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.LedgerEntry
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.Streak
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ті самі сценарії, що й у вебі (tests/unit/rewards.test.ts) — веб і Android рахують однаково. */
class RewardsTest {
    private var n = 0

    private fun g(value: Int, date: String, subject: String = "Математика", amount: Int? = null): Grade {
        n++
        return Grade(
            id = "g$n",
            childId = "k",
            subject = subject,
            value = value,
            date = LocalDate.parse(date),
            status = GradeStatus.APPROVED,
            amount = amount,
            createdAt = Instant.parse("${date}T10:%02d:00Z".format(n)),
        )
    }

    @Test
    fun `gradeValue використовує зафіксовану суму, інакше курс`() {
        assertEquals(999, Rewards.gradeValue(g(12, "2026-09-01", amount = 999), DEFAULT_RATES))
        assertEquals(300, Rewards.gradeValue(g(12, "2026-09-01"), DEFAULT_RATES))
        assertEquals(0, Rewards.gradeValue(g(13, "2026-09-01"), DEFAULT_RATES))
    }

    @Test
    fun `parseSettings ігнорує сміття і затискає межі`() {
        val s = Rewards.parseSettings(mapOf("12" to "200", "x" to "5", "streak_len" to "1", "streak_min" to "99", "streak_bonus" to "-5"))
        assertEquals(200, s.rates[12])
        assertEquals(-300, s.rates[1])
        assertEquals(Streak(len = 2, min = 12, bonus = 0), s.streak)
    }

    @Test
    fun `parseSettings повертає дефолти для порожніх даних`() {
        assertEquals(Streak(5, 7, 50), Rewards.parseSettings(emptyMap()).streak)
    }

    @Test
    fun `серія нараховує бонус кожні N поспіль і скидає лічильник`() {
        val streak = Streak(len = 3, min = 7, bonus = 50)
        val a = listOf(g(8, "2026-09-01"), g(9, "2026-09-02"), g(10, "2026-09-03"), g(7, "2026-09-04"))
        val r = Rewards.autoRewards(a, emptyList(), streak)
        assertEquals(1, r.size)
        assertEquals(LocalDate.parse("2026-09-03"), r.single().date)
        assertEquals(1, Rewards.currentRun(a, streak))
    }

    @Test
    fun `низька оцінка обриває серію`() {
        val streak = Streak(len = 3, min = 7, bonus = 50)
        val a = listOf(g(8, "2026-09-01"), g(3, "2026-09-02"), g(10, "2026-09-03"), g(10, "2026-09-04"))
        assertEquals(0, Rewards.autoRewards(a, emptyList(), streak).size)
        assertEquals(2, Rewards.currentRun(a, streak))
    }

    private val challenge = Challenge(
        id = "c1",
        childId = "k",
        title = "2 десятки з математики",
        subject = "Математика",
        minGrade = 10,
        need = 2,
        reward = 100,
        startDate = LocalDate.parse("2026-09-01"),
        endDate = LocalDate.parse("2026-09-07"),
    )

    @Test
    fun `челендж зараховує лише відповідні оцінки в межах дат`() {
        val a = listOf(g(10, "2026-08-31"), g(11, "2026-09-02", "Англ. мова"), g(12, "2026-09-03"), g(10, "2026-09-05"))
        val r = Rewards.autoRewards(a, listOf(challenge), Streak(99, 7, 0)).single()
        assertEquals(100, r.amount)
        assertEquals(LocalDate.parse("2026-09-05"), r.date)
    }

    @Test
    fun `невиконаний челендж не зараховується`() {
        assertEquals(0, Rewards.autoRewards(listOf(g(12, "2026-09-03")), listOf(challenge), Streak(99, 7, 0)).size)
    }

    @Test
    fun `totals рахує періоди й баланс з виплатами`() {
        val a = listOf(g(12, "2026-09-21"), g(4, "2026-09-25"), g(10, "2026-08-30"))
        val day = LocalDate.parse("2026-09-25")
        val l = listOf(
            LedgerEntry("l1", "k", LedgerKind.BONUS, 50, "", day, Instant.EPOCH),
            LedgerEntry("l2", "k", LedgerKind.PAYOUT, 100, "", day, Instant.EPOCH),
        )
        val t = Rewards.totals(a, l, emptyList(), DEFAULT_RATES, day, LocalDate.parse("2026-09-21"))
        assertEquals(-100 + 50, t.today)
        assertEquals(300 - 100 + 50, t.week)
        assertEquals(300 - 100 + 50, t.month)
        assertEquals(300 - 100 + 50 + 200, t.earned)
        assertEquals(t.earned - 100, t.balance)
    }

    @Test
    fun `рівні по 50 XP`() {
        assertEquals(1, Rewards.levelOf(0))
        assertEquals(1, Rewards.levelOf(49))
        assertEquals(2, Rewards.levelOf(50))
    }

    @Test
    fun `зараховано платиться як 7 і не рве серію`() {
        val s = Streak(len = 3, min = 10, bonus = 50)
        val a = listOf(g(10, "2026-10-01"), g(Grade.PASS, "2026-10-02"), g(11, "2026-10-03"), g(12, "2026-10-04"))
        assertEquals(DEFAULT_RATES[7], Rewards.gradeValue(g(Grade.PASS, "2026-10-02"), DEFAULT_RATES))
        assertEquals(1, Rewards.autoRewards(a, emptyList(), s).size)
        assertEquals(0, Rewards.currentRun(a, s))
        assertEquals(3, Rewards.bestTenRun(a))
    }
}
