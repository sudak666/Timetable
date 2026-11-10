package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.ui.Format
import java.time.LocalDateTime
import kotlin.random.Random

/** Питання вікторини: перша відповідь — правильна (як у вебі, src/views/quiz.ts). */
internal val QUIZ: List<Pair<String, List<String>>> = listOf(
    "Скільки буде 7 × 8?" to listOf("56", "54", "64", "48"),
    "Столиця Австралії?" to listOf("Канберра", "Сідней", "Мельбурн", "Перт"),
    "Хто написав «Кобзар»?" to listOf("Тарас Шевченко", "Іван Франко", "Леся Українка", "Григорій Сковорода"),
    "Яка планета найбільша?" to listOf("Юпітер", "Сатурн", "Земля", "Нептун"),
    "H₂O — це…" to listOf("вода", "сіль", "кисень", "водень"),
    "Past Simple від «go»?" to listOf("went", "goed", "gone", "goes"),
    "Скільки градусів у прямому куті?" to listOf("90", "180", "45", "100"),
    "Найдовша річка України?" to listOf("Дніпро", "Дністер", "Південний Буг", "Десна"),
    "Скільки сторін у шестикутника?" to listOf("6", "5", "8", "7"),
    "Хто автор «Лісової пісні»?" to listOf("Леся Українка", "Ольга Кобилянська", "Марко Вовчок", "Ліна Костенко"),
    "Що таке «apple»?" to listOf("яблуко", "груша", "слива", "апельсин"),
    "√81 = ?" to listOf("9", "8", "7", "18"),
    "Найвища гора України?" to listOf("Говерла", "Піп Іван", "Петрос", "Бребенескул"),
    "Який газ ми видихаємо?" to listOf("вуглекислий", "кисень", "азот", "гелій"),
    "Скільки хвилин у 2,5 годинах?" to listOf("150", "120", "250", "130"),
    "У якому році Україна проголосила незалежність?" to listOf("1991", "1990", "1996", "2001"),
    "Множина від «child»?" to listOf("children", "childs", "childes", "childrens"),
    "Що більше: 0,5 чи 0,45?" to listOf("0,5", "0,45", "однакові", "не можна порівняти"),
    "Хто намалював «Мону Лізу»?" to listOf("Леонардо да Вінчі", "Мікеланджело", "Рафаель", "Пікассо"),
    "Яка частина мови «швидко»?" to listOf("прислівник", "прикметник", "дієслово", "іменник"),
    "Скільки материків на Землі?" to listOf("6", "5", "7", "4"),
    "Що вимірюють у ватах?" to listOf("потужність", "масу", "довжину", "час"),
    "Перший президент України?" to listOf("Леонід Кравчук", "Леонід Кучма", "Віктор Ющенко", "Михайло Грушевський"),
    "1 кілобайт ≈ скільки байтів?" to listOf("1024", "1000000", "100", "512"),
    "Як англійською «вівторок»?" to listOf("Tuesday", "Thursday", "Monday", "Wednesday"),
    "Площа квадрата зі стороною 5?" to listOf("25", "20", "10", "15"),
    "Яке море омиває Україну на півдні?" to listOf("Чорне", "Балтійське", "Середземне", "Червоне"),
    "Найшвидша наземна тварина?" to listOf("гепард", "лев", "антилопа", "кінь"),
    "Скільки кісток у дорослої людини?" to listOf("206", "306", "106", "256"),
    "Хто написав «Захар Беркут»?" to listOf("Іван Франко", "Панас Мирний", "Іван Нечуй-Левицький", "Михайло Коцюбинський"),
)

internal val FACTS: List<String> = listOf(
    "Восьминіг має три серця і блакитну кров 🐙",
    "Мед ніколи не псується — його знаходили в єгипетських пірамідах 🍯",
    "Равлик може спати до 3 років 🐌",
    "У космосі неможливо заплакати — сльози не падають 🚀",
    "Банани — це ягоди, а полуниця — ні 🍌",
    "Серце кита розміром з автомобіль 🐋",
    "Блискавка гарячіша за поверхню Сонця ⚡",
    "Мурахи ніколи не сплять по-справжньому 🐜",
    "Жирафи сплять лише 30 хвилин на добу 🦒",
    "На Венері день довший за рік 🪐",
    "Коти сплять близько 16 годин на день 🐱",
    "Дельфіни дають одне одному імена 🐬",
    "Метелики відчувають смак ногами 🦋",
    "В Україні понад 70 000 річок 🌊",
)

data class QuizQuestion(val question: String, val right: String, val options: List<String>)

/** Три випадкові питання з перемішаними відповідями. [seed] — щоб вікторина пережила поворот екрана. */
fun quizRound(seed: Long, size: Int = QUIZ_SIZE): List<QuizQuestion> {
    val r = Random(seed)
    return QUIZ.shuffled(r).take(size).map { (q, a) -> QuizQuestion(q, a.first(), a.shuffled(r)) }
}

const val QUIZ_SIZE = 3
const val MAX_STARS = 10

/** Скільки лишилось до вихідних — 1:1 з вебом (weekendText у src/views/schedule.ts). */
fun weekendText(now: LocalDateTime, cal: SchoolCalendar, fridayLessons: Int): String {
    val date = now.toLocalDate()
    cal.holidayOf(date)?.let { return "Канікули до ${Format.dayShort(it.end)}! Відпочивай 🏖️" }
    val weekday = date.dayOfWeek.value - 1
    if (weekday > FRIDAY) return "Ура, вихідні! Відпочивай 🎉"
    if (fridayLessons == 0) return "Скоро вихідні! 🏖️"
    val fridayEnd = cal.bell(fridayLessons - 1).end
    val left =
        (FRIDAY - weekday) * MIN_PER_DAY + (fridayEnd.toSecondOfDay() / SEC_PER_MIN - now.toLocalTime().toSecondOfDay() / SEC_PER_MIN)
    return if (left <= 0) {
        "Вихідні вже тут! 🥳"
    } else {
        "Ще ${left / MIN_PER_DAY} д ${(left % MIN_PER_DAY) / MIN_PER_HOUR} год — і вихідні! 🏖️"
    }
}

private const val FRIDAY = 4
private const val MIN_PER_DAY = 1440
private const val MIN_PER_HOUR = 60
private const val SEC_PER_MIN = 60
