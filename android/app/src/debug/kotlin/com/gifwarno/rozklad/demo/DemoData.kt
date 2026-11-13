package com.gifwarno.rozklad.demo

import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.AirAlert
import com.gifwarno.rozklad.core.model.AlertStatus
import com.gifwarno.rozklad.core.model.Challenge
import com.gifwarno.rozklad.core.model.Family
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.model.LedgerEntry
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.model.UserAccount
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Вигадана сім'я для демо: шестикласник Тарас і мама Оксана. Оцінки за ~6 тижнів за прикладом
 * розкладу, кілька на підтвердження, челенджі, домашка, ціль. Усі дати — відносно [today].
 * Випадковість із фіксованим зерном — скриншоти щоразу однакові.
 */
internal class DemoData(
    private val today: LocalDate,
    role: Role,
    noKids: Boolean = false,
    noFamily: Boolean = false,
    alert: AlertStatus = AlertStatus.NONE,
    now: Instant = Instant.EPOCH,
) {
    private val parent = Member(PARENT_ID, Role.PARENT, "Оксана", "", 0, "🦉", "")
    private val schedule = Timetable.EXAMPLE
    private val cal = SchoolCalendar(schedule)

    val session: SessionState = when {
        noFamily -> SessionState.NoFamily(UserAccount(PARENT_ID, "mama@example.com", "Оксана"))
        role == Role.PARENT -> SessionState.Ready(UserAccount(PARENT_ID, "mama@example.com", "Оксана"), FAMILY_ID, Role.PARENT)
        else -> SessionState.Ready(UserAccount(CHILD_ID, "taras@example.com", "Тарас"), FAMILY_ID, Role.CHILD)
    }

    val family: FamilyData = run {
        val grades = grades()
        val earned = grades.filter { it.status == GradeStatus.APPROVED }.sumOf { it.amount ?: 0 }
        val ledger = listOf(
            entry("l1", LedgerKind.BONUS, 100, "За тиждень без сімок", today.minusDays(9)),
            entry("l2", LedgerKind.PAYOUT, 2500, "Кросівки", today.minusDays(27)),
            entry("l3", LedgerKind.PAYOUT, 400, "Кіно з друзями", today.minusDays(20)),
            entry("l4", LedgerKind.PAYOUT, 200, "Поповнення телефону", today.minusDays(13)),
            entry("l5", LedgerKind.PAYOUT, 450, "Книжка «Гаррі Поттер»", today.minusDays(6)),
        )
        val balance = earned + ledger.sumOf { if (it.kind == LedgerKind.BONUS) it.amount else -it.amount }
        // Ціль трохи більша за гаманець — смужка заповнена приблизно на дві третини.
        val goal = ((balance * 3 / 2 + 499) / 500 * 500).coerceAtLeast(1000)
        val child = Member(CHILD_ID, Role.CHILD, "Тарас", "Велосипед", goal, "🐯", "#6c5ce7")
        FamilyData(
            family = Family(FAMILY_ID, "Сім’я Тараса", "DEMO12"),
            members = if (noKids) listOf(parent) else listOf(parent, child),
            grades = grades,
            ledger = ledger,
            challenges = listOf(
                Challenge("c1", CHILD_ID, "Три десятки з математики", "Математика", 10, 3, 150, today.minusDays(4), today.plusDays(3)),
                Challenge("c2", CHILD_ID, "П’ять оцінок від 9", null, 9, 5, 100, today.minusDays(1), today.plusDays(6)),
            ),
            homework = homework(),
            settings = Rewards.DEFAULT_SETTINGS,
            timetables = mapOf(CHILD_ID to schedule),
            airAlert = if (alert == AlertStatus.NONE) {
                AirAlert(ALERT_UID, "м. Київ", AlertStatus.NONE, null, null)
            } else {
                AirAlert(ALERT_UID, "м. Київ", alert, now.minus(ALERT_AGO), now)
            },
        )
    }

    private fun entry(id: String, kind: LedgerKind, amount: Int, note: String, date: LocalDate) =
        LedgerEntry(id, CHILD_ID, kind, amount, note, date, date.atTime(19, 0).toInstant(ZoneOffset.UTC))

    private fun grades(): List<Grade> {
        val rnd = Random(SEED)
        val out = mutableListOf<Grade>()
        // Навчальні дні від 6 тижнів тому до вчора; канікули пропускаємо.
        var d = today.minusDays(HISTORY_DAYS)
        while (d.isBefore(today)) {
            val lessons = cal.lessonsOn(d).orEmpty()
            if (lessons.isNotEmpty() && cal.holidayOf(d) == null) {
                repeat(if (rnd.nextInt(3) == 0) 1 else 2) {
                    val subject = lessons[rnd.nextInt(lessons.size)].subject
                    out += grade("g${out.size}", subject, value(rnd), d, GradeStatus.APPROVED)
                }
            }
            d = d.plusDays(1)
        }
        // Свіжі: дві оцінки за сьогоднішні уроки й три — чекають підтвердження батьків.
        val todayLessons = cal.lessonsOn(today).orEmpty().map { it.subject }
        todayLessons.getOrNull(0)?.let { out += grade("t1", it, 12, today, GradeStatus.APPROVED) }
        todayLessons.getOrNull(1)?.let { out += grade("t2", it, 11, today, GradeStatus.PENDING) }
        out += grade("p1", "Англ. мова", 10, today.minusDays(1), GradeStatus.PENDING)
        out += grade("p2", "Укр. мова", 9, today.minusDays(1), GradeStatus.PENDING)
        return out.sortedByDescending { it.date }
    }

    /** Переважно 8–12, зрідка 6–7 — як у звичайного старанного учня. */
    private fun value(rnd: Random): Int = when (rnd.nextInt(20)) {
        0 -> 6
        1, 2 -> 7
        in 3..5 -> 8
        in 6..9 -> 9
        in 10..13 -> 10
        in 14..16 -> 11
        else -> 12
    }

    private fun grade(id: String, subject: String, value: Int, date: LocalDate, status: GradeStatus) = Grade(
        id = id,
        childId = CHILD_ID,
        subject = subject,
        value = value,
        date = date,
        status = status,
        amount = if (status == GradeStatus.APPROVED) Rewards.rateOf(value, Rewards.DEFAULT_RATES) else null,
        createdAt = date.atTime(15, 0).toInstant(ZoneOffset.UTC),
    )

    private fun homework(): List<Homework> {
        fun next(subject: String) = cal.nextLessonDate(subject, today)

        // Минулий урок з предмета — щоб «здати до» завжди припадало на справжній урок, а не на вихідний.
        fun prev(subject: String) = generateSequence(today.minusDays(1)) { it.minusDays(1) }
            .take(LOOKBACK_DAYS)
            .filter { d -> cal.holidayOf(d) == null && cal.lessonsOn(d).orEmpty().any { it.subject == subject } }
            .firstOrNull() ?: today.minusDays(1)
        return listOf(
            Homework("h1", CHILD_ID, "Математика", next("Математика"), "№ 245, 247 (с. 62)", false),
            Homework("h2", CHILD_ID, "Англ. мова", next("Англ. мова"), "Вивчити слова Unit 4, впр. 3 письмово", false),
            Homework("h3", CHILD_ID, "Укр. мова", next("Укр. мова"), "Вправа 118, правило на с. 54", false),
            Homework("h4", CHILD_ID, "Довкілля", prev("Довкілля"), "Спостереження за погодою: заповнити таблицю", false),
            Homework("h5", CHILD_ID, "Література", prev("Література"), "Прочитати «Захар Беркут», розділи 1–3", true),
            Homework("h6", CHILD_ID, "Інформатика", prev("Інформатика"), "Презентація «Мій улюблений алгоритм»", true),
        )
    }

    private companion object {
        const val FAMILY_ID = "demo-family"
        const val PARENT_ID = "demo-parent"
        const val CHILD_ID = "demo-child"
        const val LOOKBACK_DAYS = 21
        const val ALERT_UID = 31

        /** Демо-тривога «триває» вже стільки. */
        val ALERT_AGO: Duration = Duration.ofMinutes(25)
        const val SEED = 12
        const val HISTORY_DAYS = 42L
    }
}
