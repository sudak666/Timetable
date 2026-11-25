package com.gifwarno.rozklad.core.model

import java.time.Instant
import java.time.LocalDate

enum class Role { PARENT, CHILD }

data class UserAccount(val id: String, val email: String, val fullName: String)

data class Family(val id: String, val name: String, val inviteCode: String)

data class Member(
    val userId: String,
    val role: Role,
    val name: String,
    val goalTitle: String,
    val goalAmount: Int,
    val avatar: String,
    val theme: String,
)

enum class GradeStatus { PENDING, APPROVED }

data class Grade(
    val id: String,
    val childId: String,
    val subject: String,
    /** 1..12, або [PASS] = «зараховано». */
    val value: Int,
    val date: LocalDate,
    val status: GradeStatus,
    /** Сума, зафіксована при підтвердженні; null — ще за поточним курсом. */
    val amount: Int?,
    val createdAt: Instant,
) {
    companion object {
        const val PASS = 0
        val VALUES: List<Int> = (12 downTo 1).toList() + PASS
    }
}

enum class LedgerKind { BONUS, PAYOUT }

/** Звідки бонус: зірка за поведінку від батьків або гроші за вікторину (нараховує сервер); null — звичайний. */
enum class LedgerSource { STAR, QUIZ }

data class LedgerEntry(
    val id: String,
    val childId: String,
    val kind: LedgerKind,
    val amount: Int,
    val note: String,
    val date: LocalDate,
    val createdAt: Instant,
    val source: LedgerSource? = null,
)

data class Challenge(
    val id: String,
    val childId: String,
    val title: String,
    val subject: String?,
    val minGrade: Int,
    val need: Int,
    val reward: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

/** [createdBy] — хто задав (null — невідомо, старі записи): дитина не видаляє домашку, яку задали батьки. */
data class Homework(
    val id: String,
    val childId: String,
    val subject: String,
    val due: LocalDate,
    val text: String,
    val done: Boolean,
    val createdBy: String? = null,
)

data class Streak(val len: Int, val min: Int, val bonus: Int)

/** Ціни зірки за поведінку й правильної відповіді у вікторині (ключі star_price / quiz_price у school_families.rates). */
data class Prices(val star: Int = 50, val quiz: Int = 20)

/** Курс «оцінка → гривні», правила серій і ціни зірки й вікторини. */
data class RewardSettings(val rates: Map<Int, Int>, val streak: Streak, val prices: Prices = Prices())

/** Питання вікторини з сервера: правильної відповіді клієнт не знає, варіанти вже перемішані. */
data class QuizQuestion(val id: Int, val question: String, val options: List<String>)

/** Відповідь сервера: [paid] — скільки нараховано (0 — без грошей), [left] — скільки оплачених відповідей лишилось на сьогодні. */
data class QuizAnswer(val correct: Boolean, val right: String, val paid: Int, val left: Int)

/** Усе, що бачить сім'я, — локальна копія з Room (працює офлайн). */
data class FamilyData(
    val family: Family,
    val members: List<Member>,
    val grades: List<Grade>,
    val ledger: List<LedgerEntry>,
    val challenges: List<Challenge>,
    val homework: List<Homework>,
    val settings: RewardSettings,
    /** Розклад кожної дитини: childId → розклад. Немає ключа — розклад ще не заповнено. */
    val timetables: Map<String, SchoolSchedule> = emptyMap(),
    /** Повітряні тривоги для обраного сім'єю місця; null — місце не обрано. */
    val airAlert: AirAlert? = null,
) {
    val children: List<Member> get() = members.filter { it.role == Role.CHILD }
}
