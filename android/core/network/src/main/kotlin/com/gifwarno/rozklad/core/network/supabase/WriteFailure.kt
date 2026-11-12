package com.gifwarno.rozklad.core.network.supabase

/** Що робити із записом з офлайн-черги, на який сервер відповів помилкою. */
internal enum class WriteFailure {
    /** Сервер свідомо відмовив (RLS, CHECK, некоректні дані) — повтор не допоможе, зміну відкидаємо. */
    REJECTED,

    /** Збій сервера, шлюзу чи ліміт запитів — лишаємо в черзі й повторюємо пізніше. */
    TRANSIENT,

    /** Токен протермінований — оновлюємо сесію й повторюємо. */
    AUTH,
}

/** Класифікація за HTTP-статусом відповіді PostgREST. */
internal fun writeFailureOf(status: Int): WriteFailure = when {
    status == HTTP_UNAUTHORIZED -> WriteFailure.AUTH
    status >= HTTP_SERVER_ERROR || status in TRANSIENT_4XX -> WriteFailure.TRANSIENT
    else -> WriteFailure.REJECTED
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_SERVER_ERROR = 500

/** 408 Request Timeout, 425 Too Early, 429 Too Many Requests. */
private val TRANSIENT_4XX = setOf(408, 425, 429)
