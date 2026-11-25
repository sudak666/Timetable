package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.ui.Format
import java.time.LocalDateTime

internal val FACTS: List<String> = listOf(
    "Восьминіг має три серця і блакитну кров 🐙",
    "Мед ніколи не псується — його знаходили в єгипетських пірамідах 🍯",
    "Равлик може спати до 3 років 🐌",
    "У космосі сльози не падають — збираються кулькою біля ока 🚀",
    "Банани — це ягоди, а полуниця — ні 🍌",
    "Серце кита розміром з автомобіль 🐋",
    "Блискавка гарячіша за поверхню Сонця ⚡",
    "Мураха може підняти вагу в 10–50 разів більшу за себе 🐜",
    "Жирафи сплять лише 30 хвилин на добу 🦒",
    "На Венері день довший за рік 🪐",
    "Коти сплять близько 16 годин на день 🐱",
    "Дельфіни дають одне одному імена 🐬",
    "Метелики відчувають смак ногами 🦋",
    "В Україні понад 70 000 річок 🌊",
)

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
        "Ще ${leftText(left / MIN_PER_DAY, (left % MIN_PER_DAY) / MIN_PER_HOUR)} — і вихідні! 🏖️"
    }
}

/** «3 дні 3 год», «1 день», «45 хв» — без «0 д» і з відмінками. */
internal fun leftText(days: Int, hours: Int): String {
    val d = when {
        days == 0 -> null
        days % 10 == 1 && days % 100 != 11 -> "$days день"
        days % 10 in 2..4 && days % 100 !in 12..14 -> "$days дні"
        else -> "$days днів"
    }
    val h = when {
        hours > 0 -> "$hours год"
        d == null -> "менше години"
        else -> null
    }
    return listOfNotNull(d, h).joinToString(" ")
}

private const val FRIDAY = 4
private const val MIN_PER_DAY = 1440
private const val MIN_PER_HOUR = 60
private const val SEC_PER_MIN = 60
