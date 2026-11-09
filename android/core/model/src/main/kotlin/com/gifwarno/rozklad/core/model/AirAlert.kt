package com.gifwarno.rozklad.core.model

import java.time.Instant

/** Стан повітряної тривоги для обраного місця (дані alerts.in.ua через наш сервер). */
enum class AlertStatus {
    /** Тривоги немає. */
    NONE,

    /** Тривога лише в частині області чи району. */
    PARTIAL,

    /** Тривога. */
    ACTIVE,
    ;

    companion object {
        /** Код сервера: A / P / N. Невідомий код — «немає» (не лякаємо через збій формату). */
        fun fromCode(code: String?): AlertStatus = when (code) {
            "A" -> ACTIVE
            "P" -> PARTIAL
            else -> NONE
        }
    }
}

/**
 * Місце, яке сім'я обрала для тривог, і його стан.
 * [status] == null — стан ще не відомий (місце щойно обрали, сервер перевірить протягом хвилини).
 */
data class AirAlert(
    val regionUid: Int,
    val regionName: String,
    val status: AlertStatus?,
    /** Коли почався поточний стан. */
    val since: Instant?,
    /** Остання перевірка — показуємо «станом на», щоб не видавати застарілі дані за поточні. */
    val checkedAt: Instant?,
) {
    val isActive: Boolean get() = status == AlertStatus.ACTIVE
}

enum class AlertLocationKind { OBLAST, CITY, RAION, HROMADA }

/** Запис довідника місць для вибору: область → район → громада. */
data class AlertLocation(val uid: Int, val name: String, val kind: AlertLocationKind, val oblastUid: Int?, val raionUid: Int?)
