package com.gifwarno.rozklad.core.data

import java.io.IOException

/** Людські повідомлення замість технічних помилок мережі/БД (як errText у вебі). */
object UserMessage {
    fun of(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is IOException || NETWORK.containsMatchIn(m) || e.javaClass.simpleName.contains("HttpRequest") ->
                "Немає інтернету — спробуй ще раз, коли зʼявиться звʼязок"

            Regex("invalid login credentials", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Неправильна пошта або пароль"

            Regex("email not confirmed", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Підтвердь пошту — лист уже надіслано"

            Regex("already registered|user already exists", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Такий акаунт уже існує — увійди"

            Regex("JWT|expired|invalid claim", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Сесія застаріла — увійди ще раз"

            Regex("row-level security|permission denied|violates", RegexOption.IGNORE_CASE).containsMatchIn(m) ->
                "Недостатньо прав для цієї дії"

            Regex("duplicate key", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Такий запис уже існує"

            m.isBlank() -> "Щось пішло не так"

            else -> m.lineSequence().first().take(MAX_LEN)
        }
    }

    private val NETWORK = Regex("Unable to resolve host|timeout|timed out|Failed to connect|connection", RegexOption.IGNORE_CASE)
    private const val MAX_LEN = 200
}
