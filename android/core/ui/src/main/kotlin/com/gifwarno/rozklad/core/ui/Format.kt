package com.gifwarno.rozklad.core.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Форматування для українського інтерфейсу. */
object Format {
    private val uk: Locale = Locale.forLanguageTag("uk-UA")
    private val dayShort = DateTimeFormatter.ofPattern("EEE, d MMM", uk)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, d MMMM", uk)

    fun money(v: Int): String = (if (v > 0) "+" else "") + "$v ₴"

    fun balance(v: Int): String = "$v ₴"

    fun dayShort(d: LocalDate): String = dayShort.format(d)

    fun dayLong(d: LocalDate): String = dayLong.format(d).replaceFirstChar { it.titlecase(uk) }

    /** Секунди → «h:mm:ss» або «m:ss». */
    fun timer(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** Відміна «урок/уроки/уроків». */
    fun lessons(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        val word = when {
            mod10 == 1 && mod100 != 11 -> "урок"
            mod10 in 2..4 && mod100 !in 12..14 -> "уроки"
            else -> "уроків"
        }
        return "$n $word"
    }
}
