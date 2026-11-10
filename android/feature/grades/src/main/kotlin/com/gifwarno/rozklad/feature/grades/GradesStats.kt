package com.gifwarno.rozklad.feature.grades

import com.gifwarno.rozklad.core.domain.AutoReward
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.LedgerEntry
import com.gifwarno.rozklad.core.model.LedgerKind
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Рядок історії: підтверджена оцінка, бонус/штраф/виплата або автонагорода (серія, челендж). */
sealed interface HistoryEntry {
    val key: String
    val date: LocalDate
    val createdAt: Instant

    data class GradeRow(val item: GradeItem) : HistoryEntry {
        override val key get() = "g" + item.grade.id
        override val date: LocalDate get() = item.grade.date
        override val createdAt: Instant get() = item.grade.createdAt
    }

    data class Money(val entry: LedgerEntry, val canDelete: Boolean) : HistoryEntry {
        override val key get() = "l" + entry.id
        override val date: LocalDate get() = entry.date
        override val createdAt: Instant get() = entry.createdAt
    }

    data class Auto(val reward: AutoReward) : HistoryEntry {
        override val key get() = "a" + reward.id
        override val date: LocalDate get() = reward.date
        override val createdAt: Instant get() = reward.createdAt
    }
}

/**
 * «Я проти минулого місяця» (одна дитина) або рейтинг місяця (кілька дітей) — як у вебі.
 * [earnedPrev] — за ті самі дні минулого місяця (1…[upToDay]), інакше на початку місяця
 * завжди виходило б «гірше». [upToDay] == null — місяць уже закінчився, порівнюємо цілі місяці.
 */
data class MonthCompare(val avgNow: Double?, val avgPrev: Double?, val earnedNow: Int, val earnedPrev: Int, val upToDay: Int? = null)

data class RankRow(val userId: String, val name: String, val avatar: String, val earned: Int)

data class SubjectAvg(val subject: String, val avg: Double, val count: Int)

internal object GradesStats {
    const val HISTORY_LIMIT = 60

    /** Свіжі зверху: за датою, потім за часом створення. */
    fun history(grades: List<GradeItem>, ledger: List<LedgerEntry>, auto: List<AutoReward>, isParent: Boolean): List<HistoryEntry> =
        (grades.map { HistoryEntry.GradeRow(it) } + ledger.map { HistoryEntry.Money(it, isParent) } + auto.map { HistoryEntry.Auto(it) })
            .sortedWith(compareByDescending<HistoryEntry> { it.date }.thenByDescending { it.createdAt })
            .take(HISTORY_LIMIT)

    /** Зароблено дитиною за місяць: підтверджені оцінки + бонуси (зі штрафами) + автонагороди. */
    fun earnedIn(childId: String, data: FamilyData, month: YearMonth, upToDay: Int = 31): Int {
        val approved = data.grades.filter { it.childId == childId && it.status == GradeStatus.APPROVED }
        val auto = Rewards.autoRewards(approved, data.challenges.filter { it.childId == childId }, data.settings.streak)
        val items = approved.map { it.date to Rewards.gradeValue(it, data.settings.rates) } +
            data.ledger.filter { it.childId == childId && it.kind == LedgerKind.BONUS }.map { it.date to it.amount } +
            auto.map { it.date to it.amount }
        return items.sumOf { (d, v) -> if (YearMonth.from(d) == month && d.dayOfMonth <= upToDay) v else 0 }
    }

    fun avgIn(approved: List<Grade>, month: YearMonth): Double? =
        approved.filter { Rewards.isScored(it) && YearMonth.from(it.date) == month }.map { it.value }.takeIf { it.isNotEmpty() }?.average()

    fun compare(childId: String, approved: List<Grade>, data: FamilyData, today: LocalDate): MonthCompare {
        val now = YearMonth.from(today)
        val prev = now.minusMonths(1)
        // Гроші — накопичувальні: порівнюємо з тими самими днями минулого місяця. Середній бал — і так середній.
        val day = today.dayOfMonth.takeIf { it < now.lengthOfMonth() }
        return MonthCompare(
            avgNow = avgIn(approved, now),
            avgPrev = avgIn(approved, prev),
            earnedNow = earnedIn(childId, data, now),
            earnedPrev = earnedIn(childId, data, prev, day ?: 31),
            upToDay = day,
        )
    }

    fun rank(data: FamilyData, today: LocalDate): List<RankRow> {
        val month = YearMonth.from(today)
        return data.children
            .map { RankRow(it.userId, it.name.ifBlank { "Дитина" }, it.avatar.ifBlank { "🙂" }, earnedIn(it.userId, data, month)) }
            .sortedByDescending { it.earned }
    }

    fun subjectAverages(approved: List<Grade>): List<SubjectAvg> = approved
        .filter(Rewards::isScored)
        .groupBy { it.subject }
        .map { (s, g) -> SubjectAvg(s, g.map { it.value }.average(), g.size) }
        .sortedByDescending { it.avg }
}
