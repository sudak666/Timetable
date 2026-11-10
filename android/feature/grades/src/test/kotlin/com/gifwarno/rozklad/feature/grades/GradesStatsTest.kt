package com.gifwarno.rozklad.feature.grades

import com.gifwarno.rozklad.core.model.LedgerEntry
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.testing.TestData
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradesStatsTest {
    private val kid = TestData.child.userId
    private fun ledger(id: String, kind: LedgerKind, amount: Int, date: String) =
        LedgerEntry(id, kid, kind, amount, "", LocalDate.parse(date), Instant.parse("${date}T12:00:00Z"))

    private val data = TestData.familyData(
        grades = listOf(
            TestData.grade("a", 12, "2026-11-03"),
            TestData.grade("b", 8, "2026-11-05"),
            TestData.grade("c", 10, "2026-10-20"),
        ),
    ).copy(
        ledger = listOf(
            ledger("l1", LedgerKind.BONUS, 50, "2026-11-04"),
            ledger("l2", LedgerKind.BONUS, -100, "2026-11-06"),
            ledger("l3", LedgerKind.PAYOUT, 400, "2026-11-07"),
        ),
    )

    @Test
    fun `зароблено за місяць — оцінки, бонуси зі штрафами, без виплат`() {
        // 12 → 300, 8 → 100, бонус +50, штраф −100.
        assertEquals(350, GradesStats.earnedIn(kid, data, YearMonth.of(2026, 11)))
        assertEquals(200, GradesStats.earnedIn(kid, data, YearMonth.of(2026, 10)))
    }

    @Test
    fun `порівняння з минулим місяцем і середній за предметами`() {
        val c = GradesStats.compare(kid, data.grades, data, LocalDate.parse("2026-11-10"))
        assertEquals(10.0, c.avgNow!!, 0.001)
        assertEquals(10.0, c.avgPrev!!, 0.001)
        assertNull(GradesStats.avgIn(data.grades, YearMonth.of(2026, 9)))
        assertEquals(listOf("Математика"), GradesStats.subjectAverages(data.grades).map { it.subject })
    }

    @Test
    fun `історія — свіжі зверху, усі види записів`() {
        val items = data.grades.map { GradeItem(it, 0, canApprove = false, canDelete = true) }
        val h = GradesStats.history(items, data.ledger, emptyList(), isParent = true)
        assertEquals(listOf("ll3", "ll2", "gb", "ll1", "ga", "gc"), h.map { it.key })
    }
}
