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

            // Вимоги Supabase до пароля (мінімальна довжина, «слабкий» чи злитий пароль) — зрозумілою мовою.
            PASSWORD_LENGTH.find(m) != null -> "Пароль має бути від ${PASSWORD_LENGTH.find(m)!!.groupValues[1]} символів"

            Regex(
                "weak|pwned|leaked|compromised",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(m) && m.contains("password", ignoreCase = true) ->
                "Цей пароль занадто простий або вже витікав — придумай інший"

            Regex("JWT|expired|invalid claim", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Сесія застаріла — увійди ще раз"

            Regex("row-level security|permission denied|violates", RegexOption.IGNORE_CASE).containsMatchIn(m) ->
                "Недостатньо прав для цієї дії"

            Regex("duplicate key", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Такий запис уже існує"

            m.isBlank() -> "Щось пішло не так"

            else -> m.lineSequence().first().take(MAX_LEN)
        }
    }

    private val PASSWORD_LENGTH = Regex("password should be at least (\\d+)", RegexOption.IGNORE_CASE)
    private val NETWORK = Regex("Unable to resolve host|timeout|timed out|Failed to connect|connection", RegexOption.IGNORE_CASE)
    private const val MAX_LEN = 200
}
