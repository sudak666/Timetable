package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Member
import java.time.LocalDate

/** Смужка «гравця» зверху «Сьогодні»: ті самі рівень, XP і гаманець, що й на «Оцінках». */
data class PlayerStrip(val name: String, val avatar: String, val level: Int, val xpInLevel: Int, val balance: Int)

/** Завдання дня — лише показ прогресу з наявних даних, без окремих нагород. */
data class Quest(val emoji: String, val title: String, val progress: Int, val target: Int) {
    val done: Boolean get() = progress >= target
}

/** Чиста функція: дані сім'ї → смужка гравця. Рахує так само, як GradesViewModel. */
fun buildPlayer(child: Member, data: FamilyData, today: LocalDate): PlayerStrip {
    val approved = data.grades.filter { it.childId == child.userId && it.status == GradeStatus.APPROVED }
    val ledger = data.ledger.filter { it.childId == child.userId }
    val challenges = data.challenges.filter { it.childId == child.userId }
    val auto = Rewards.autoRewards(approved, challenges, data.settings.streak)
    val xp = Rewards.xpOf(approved)
    val totals = Rewards.totals(approved, ledger, auto, data.settings.rates, today, SchoolCalendar.mondayOf(today))
    return PlayerStrip(
        name = child.name,
        avatar = child.avatar.ifBlank { "🙂" },
        level = Rewards.levelOf(xp),
        xpInLevel = xp % Rewards.XP_PER_LEVEL,
        balance = totals.balance,
    )
}

/**
 * Завдання дня:
 * - домашка на наступний навчальний день (якщо її задали);
 * - оцінка сьогодні (лише в день з уроками);
 * - поточна серія хороших оцінок.
 */
fun buildQuests(childId: String, data: FamilyData, cal: SchoolCalendar, today: LocalDate): List<Quest> {
    val out = mutableListOf<Quest>()
    val next = cal.nextSchoolDate(today)
    if (next != null) {
        val due = data.homework.filter { it.childId == childId && it.due == next }
        if (due.isNotEmpty()) {
            val label = if (next == today.plusDays(1)) "завтра" else "наступний урок"
            out += Quest("📒", "Домашка на $label", due.count { it.done }, due.size)
        }
    }
    if (!cal.lessonsOn(today).isNullOrEmpty()) {
        val gotGrade = data.grades.any { it.childId == childId && it.date == today }
        out += Quest("⭐", "Отримати оцінку сьогодні", if (gotGrade) 1 else 0, 1)
    }
    val streak = data.settings.streak
    val approved = data.grades.filter { it.childId == childId && it.status == GradeStatus.APPROVED }
    out += Quest(
        "🔥",
        "Серія: ${streak.len} оцінок від ${streak.min}" + if (streak.bonus > 0) " (+${streak.bonus} ₴)" else "",
        Rewards.currentRun(approved, streak),
        streak.len,
    )
    return out
}
