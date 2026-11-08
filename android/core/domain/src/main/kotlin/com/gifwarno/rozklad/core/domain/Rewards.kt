package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Challenge
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.LedgerEntry
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.RewardSettings
import com.gifwarno.rozklad.core.model.Streak
import java.time.Instant
import java.time.LocalDate

/**
 * Доменна логіка нагород — 1:1 з веб-версією (src/features/rewards.ts),
 * щоб веб і Android рахували однаково. Без Android і мережі, покрита тестами.
 */
object Rewards {
    val DEFAULT_RATES: Map<Int, Int> = mapOf(
        12 to 300, 11 to 250, 10 to 200, 9 to 150, 8 to 100, 7 to 70,
        6 to 0, 5 to -70, 4 to -100, 3 to -150, 2 to -200, 1 to -300,
    )
    val DEFAULT_STREAK = Streak(len = 5, min = 7, bonus = 50)
    const val XP_PER_LEVEL = 50

    /** «Зараховано» платиться як 7, але не входить у середній бал, XP, серії та челенджі. */
    const val PASS_AS = 7

    /**
     * Налаштування сім'ї з JSON-колонки `school_families.rates`: ключі «1».. «12» — курс,
     * `streak_*` — серії. Сміття ігнорується, межі затискаються (як у вебі).
     * Значення передаються рядками: data-шар не тягне JSON у домен.
     */
    fun parseSettings(raw: Map<String, String?>): RewardSettings {
        val rates = DEFAULT_RATES.toMutableMap()
        raw.forEach { (k, v) ->
            val n = v?.toDoubleOrNull()
            if (k.all(Char::isDigit) && k.isNotEmpty() && n != null && n.isFinite()) rates[k.toInt()] = n.toInt()
        }

        fun num(key: String, default: Int) = raw[key]?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt() ?: default
        return RewardSettings(
            rates = rates,
            streak = Streak(
                len = maxOf(2, num("streak_len", DEFAULT_STREAK.len)),
                min = num("streak_min", DEFAULT_STREAK.min).coerceIn(1, 12),
                bonus = maxOf(0, num("streak_bonus", DEFAULT_STREAK.bonus)),
            ),
        )
    }

    val DEFAULT_SETTINGS = RewardSettings(DEFAULT_RATES, DEFAULT_STREAK)

    fun isScored(g: Grade): Boolean = g.value != Grade.PASS

    fun rateOf(value: Int, rates: Map<Int, Int>): Int = rates[if (value == Grade.PASS) PASS_AS else value] ?: 0

    fun gradeLabel(value: Int): String = if (value == Grade.PASS) "Зар." else value.toString()

    /** Сума за оцінку: зафіксована при підтвердженні, інакше — поточний курс. */
    fun gradeValue(g: Grade, rates: Map<Int, Int>): Int = g.amount ?: rateOf(g.value, rates)

    val byTime: Comparator<Grade> = compareBy<Grade>({ it.date }, { it.createdAt })

    fun challengeHits(c: Challenge, approvedSorted: List<Grade>): List<Grade> = approvedSorted.filter {
        isScored(it) &&
            it.date in c.startDate..c.endDate &&
            it.value >= c.minGrade &&
            (c.subject == null || it.subject == c.subject)
    }

    /** Автоматичні нагороди за серії та виконані челенджі (детерміновано з підтверджених оцінок). */
    fun autoRewards(approved: List<Grade>, challenges: List<Challenge>, streak: Streak): List<AutoReward> {
        val sorted = approved.sortedWith(byTime)
        val out = mutableListOf<AutoReward>()
        if (streak.bonus > 0) {
            var run = 0
            for (g in sorted.filter(::isScored)) {
                run = if (g.value >= streak.min) run + 1 else 0
                if (run == streak.len) {
                    out += AutoReward(
                        id = "s${g.id}",
                        date = g.date,
                        createdAt = g.createdAt,
                        amount = streak.bonus,
                        note = "Серія: ${streak.len} оцінок поспіль ≥ ${streak.min}",
                        icon = "🔥",
                    )
                    run = 0
                }
            }
        }
        for (c in challenges) {
            val hit = challengeHits(c, sorted).getOrNull(c.need - 1) ?: continue
            out += AutoReward(
                id = "c${c.id}",
                date = hit.date,
                createdAt = hit.createdAt,
                amount = c.reward,
                note = "Челендж: ${c.title}",
                icon = "🏅",
            )
        }
        return out
    }

    /** Поточна серія (скидається після нарахування бонусу). */
    fun currentRun(approved: List<Grade>, streak: Streak): Int {
        var run = 0
        for (g in approved.filter(::isScored).sortedWith(byTime)) {
            run = if (g.value >= streak.min) run + 1 else 0
            if (run == streak.len) run = 0
        }
        return run
    }

    fun bestTenRun(approved: List<Grade>): Int {
        var run = 0
        var best = 0
        for (g in approved.filter(::isScored).sortedWith(byTime)) {
            run = if (g.value >= 10) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    fun totals(
        approved: List<Grade>,
        ledger: List<LedgerEntry>,
        auto: List<AutoReward>,
        rates: Map<Int, Int>,
        today: LocalDate,
        weekStart: LocalDate,
    ): Totals {
        val items = approved.map { it.date to gradeValue(it, rates) } +
            ledger.filter { it.kind == LedgerKind.BONUS }.map { it.date to it.amount } +
            auto.map { it.date to it.amount }

        fun sum(pred: (LocalDate) -> Boolean) = items.sumOf { (d, v) -> if (pred(d)) v else 0 }
        val earned = sum { true }
        val paid = ledger.filter { it.kind == LedgerKind.PAYOUT }.sumOf { it.amount }
        return Totals(
            today = sum { it == today },
            week = sum { it in weekStart..today },
            month = sum { it.year == today.year && it.month == today.month },
            earned = earned,
            paid = paid,
            balance = earned - paid,
        )
    }

    fun xpOf(approved: List<Grade>): Int = approved.sumOf { it.value }

    fun levelOf(xp: Int): Int = xp / XP_PER_LEVEL + 1
}

data class AutoReward(val id: String, val date: LocalDate, val createdAt: Instant, val amount: Int, val note: String, val icon: String)

data class Totals(val today: Int, val week: Int, val month: Int, val earned: Int, val paid: Int, val balance: Int)
