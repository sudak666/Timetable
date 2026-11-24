package com.gifwarno.rozklad.core.network.supabase

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * «Забули пароль?» — запит листа до Supabase Auth напряму, без PKCE.
 *
 * Клієнт застосунку працює в PKCE (для входу через Google), і `resetPasswordForEmail` з нього додає
 * code_challenge: посилання з листа тоді несе `?code=`, який обміняти може лише цей застосунок. А веде
 * посилання на сайт — і той код мовчки ігнорував, новий пароль задати було ніде (24.11.2026).
 * Без code_challenge посилання несе сесію у фрагменті адреси: сайт відкриває «Задай новий пароль»
 * на будь-якому пристрої, а далі з цим паролем входять у застосунку (так само, як «Забули пароль?» на сайті).
 */
internal object Recover {
    fun url(base: String, redirect: String): String =
        base.trimEnd('/') + "/auth/v1/recover?redirect_to=" + URLEncoder.encode(redirect, Charsets.UTF_8.name())

    fun body(email: String): String = buildJsonObject { put("email", email) }.toString()

    /** Текст помилки з відповіді Auth (людською мовою його робить UserMessage). */
    fun error(status: Int, body: String): String {
        val obj = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        val msg = obj?.let { o -> FIELDS.firstNotNullOfOrNull { o[it]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) } }
        return msg ?: "Не вдалося надіслати лист (код $status)"
    }

    private val FIELDS = listOf("msg", "message", "error_description", "error")
}
